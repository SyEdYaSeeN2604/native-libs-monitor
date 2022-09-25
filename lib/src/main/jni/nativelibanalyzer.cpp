/**
 * Copyright (C) 2022 Intel Corporation
 *       
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       
 * http://www.apache.org/licenses/LICENSE-2.0
 *       
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <fcntl.h>
#include <limits>
#include <cassert>
#include <libelf.h>
#include <unistd.h>

// defines missing flags from current libelf:
#define EF_MIPS_ARCH_32R6 0x90000000
#define EF_MIPS_ARCH_64R6 0xa0000000

#include "nativelibanalyzer.h"

using namespace std;

void NativeLibAnalyzer::analyzeDynamicSymbols(Elf *elf, GElf_Shdr shdr, Elf_Data *edata,
                                              vector<string> &entryPoints,
                                              set<string> &frameworks, bool knownSharedLib) {
    GElf_Sym sym;
    int i = 0;

    while (gelf_getsym(edata, i++, &sym) != NULL) {
        if (sym.st_info == SHT_SYMTAB_SHNDX) {
            const char *name = elf_strptr(elf, shdr.sh_link, sym.st_name);
            if (name != nullptr) {

#ifdef EXTRACT_SYMBOLS_TO_FILE
                myfile << name << ";" << libName << "\n";
#endif

                if (sym.st_size < 2 || name[0] != '_' ||
                    name[1] == 'Z') { //skip internal symbols, but not C++ mangled ones.
                    assert(sym.st_size < numeric_limits<size_t>::max());

                    if (sym.st_size > 0) { // sym.st_size is correctly filled.
                        if ((sym.st_size >= 5 && strncmp("Java_", name, 5) == 0) //starts with Java_
                            || (sym.st_size > 0 &&
                               strncmp("JNI_OnLoad", name, (size_t) sym.st_size) ==
                               0) //exact comparison
                            || (sym.st_size > 0 &&
                               strncmp("android_main", name, (size_t) sym.st_size) == 0)) {
                            entryPoints.push_back(name);
                        }
                    }
                    else if (name[0] != '\0' &&
                             (name[0] != '_' || name[1] == 'Z')) { // sym.st_size is 0
                        if (strncmp("Java_", name, 5) == 0 //starts with Java_
                            || strncmp("JNI_OnLoad", name, 10) == 0 //exact comparison
                            || strncmp("android_main", name, 12) == 0) {
                            entryPoints.push_back(name);
                        }
                    }

                    if (!knownSharedLib) { //if this isn't a well-known shared lib, we do a static analysis of its non-internal symbols
                        const auto &lib = staticLibIdentifiers.find(name);
                        if (lib != staticLibIdentifiers.cend()) {
                            frameworks.insert(lib->second);
                        }
                    }
                }
            }
        }
    }
}


void NativeLibAnalyzer::analyzeSymbolsTable(Elf *elf, GElf_Shdr shdr, Elf_Data *edata,
                                            set<string> &frameworks) {
    GElf_Sym sym;
    size_t i = 0;
    while (gelf_getsym(edata, i++, &sym) != nullptr) {
        if (sym.st_info == SHT_SYMTAB) {
            const char *name = elf_strptr(elf, shdr.sh_link, sym.st_name);
            if (strncmp("__intel_cpu_features_init", name, 25) == 0) {
                frameworks.insert("Intel Compiler");
            }
        }
    }
}

void NativeLibAnalyzer::analyzeDynamicEntries(Elf *elf, GElf_Shdr shdr, Elf_Data *edata,
                                              vector<string> &dependencies) {
    GElf_Dyn dyn;
    size_t i = 0;

    while (gelf_getdyn(edata, i++, &dyn) != nullptr) {
        if (dyn.d_tag == DT_NEEDED) {
            assert(dyn.d_un.d_ptr < numeric_limits<size_t>::max());
            const char *name = elf_strptr(elf, shdr.sh_link, (size_t) dyn.d_un.d_ptr);
            if (name != nullptr)
                dependencies.push_back(name);
        }
    }
}

string NativeLibAnalyzer::getFrameworkFromKnownSharedLibs(string libNameStr) {
    const auto &sharedLib = sharedLibIdentifiers.find(libNameStr);
    if (sharedLib != sharedLibIdentifiers.cend()) {
        return sharedLib->second;
    }
    else {
        for (const auto &substring : sharedLibSubstringIdentifiers) {
            if (libNameStr.find(substring.first) != string::npos)
                return substring.second;
        }
    }
    return "";
}


pair<uint64_t, uint64_t> NativeLibAnalyzer::getRoDataStartAndSize(Elf *elf) {
    pair<uint64_t, uint64_t> result;
    Elf_Scn *scn = nullptr;

    while ((scn = elf_nextscn(elf, scn)) != nullptr) {
        GElf_Shdr shdr;
        gelf_getshdr(scn, &shdr);

        if (shdr.sh_type == SHT_PROGBITS) {
            size_t strndx;
            if (elf_getshdrstrndx(elf, &strndx) != -1) {
                const char *name = elf_strptr(elf, strndx, shdr.sh_name);
                if (strncmp(".rodata", name, 7) == 0) { // we're in .rodata section
                    result.first = shdr.sh_offset;
                    result.second = shdr.sh_size;
                }
            }
        }
    }
    return result;
}

string NativeLibAnalyzer::getHoudiniVersion() {
    uint64_t roDataSectionStart = 0;
    uint64_t roDataSectionSize = 0;
    int fd = open("/system/lib/libhoudini.so", O_RDONLY);

    if (fd > -1) {
        lseek64(fd, 0, SEEK_SET);

        elf_version(EV_CURRENT);
        Elf *elf = elf_begin(fd, ELF_C_READ_MMAP, nullptr); //ELF_C_READ_MMAP
        if (elf != nullptr) {
            auto roDataSectionStartAndSize = getRoDataStartAndSize(elf);
            roDataSectionStart = roDataSectionStartAndSize.first;
            roDataSectionSize = roDataSectionStartAndSize.second;
            elf_end(elf);
        }

        char buf[32] ,valid_buf[32];
        size_t stringLength = 0;
        size_t count = 0;
        lseek64(fd, roDataSectionStart, SEEK_SET);
        while (read(fd, buf, sizeof(buf)) != 0 && count < roDataSectionSize / sizeof(buf)) {
            strlcpy(valid_buf, buf, sizeof(valid_buf));
            if (strncmp("version: ", valid_buf, 9) == 0) {
                stringLength = strnlen(valid_buf, sizeof(valid_buf));
                break;
            }
            ++count;
        }

        close(fd);
        if (stringLength > 9)
            return string(buf + 9, stringLength - 9);
        else
            return "";
    }

    return "";
}


string NativeLibAnalyzer::getUnityVersion(Elf *elf, int fd, char *soFileContent) {

    auto roDataSectionStartAndSize = getRoDataStartAndSize(elf);
    auto roDataSectionStart = roDataSectionStartAndSize.first;
    auto roDataSectionSize = roDataSectionStartAndSize.second;

    char buf[1024];

    size_t count = 0;
    const char stringToLookFor[] = "Initialize engine version";
    //const size_t sizeOfStringToLookFor = 25;
    const size_t versionOffsetFromString = sizeof(stringToLookFor) + 7;
    const size_t versionStringMaxLength = 32;

    if (fd > -1 && soFileContent == nullptr) { //file from fd
        lseek64(fd, roDataSectionStart, SEEK_SET);
        while (read(fd, buf, sizeof(buf)) != 0 &&
               count * (sizeof(buf) - 2 * versionStringMaxLength) <
               roDataSectionSize) { // search windows is around 64 bytes less than bufer, to handle overlaps.

            const char *found = (char *) memmem(buf, sizeof(buf), stringToLookFor,
                                                sizeof(stringToLookFor));
            if (found != nullptr) {
                if (*(found + versionOffsetFromString - 1) >= '0' &&
                    *(found + versionOffsetFromString - 1) <= '9')
                    return string(found + versionOffsetFromString - 1,
                                  strnlen(found + versionOffsetFromString - 1,
                                          versionStringMaxLength));
                else
                    return string(found + versionOffsetFromString,
                                  strnlen(found + versionOffsetFromString, versionStringMaxLength));
            }

            ++count;
            lseek64(fd, roDataSectionStart + count * (sizeof(buf) - 2 * versionStringMaxLength),
                    SEEK_SET);
        }
    }
    else {//file from memory
        assert(roDataSectionSize < numeric_limits<size_t>::max());
        const char *found = (char *) memmem(soFileContent + roDataSectionStart,
                                            (size_t) roDataSectionSize,
                                            stringToLookFor, sizeof(stringToLookFor));
        if (found != nullptr) {
            if (*(found + versionOffsetFromString - 1) >= '0' &&
                *(found + versionOffsetFromString - 1) <= '9')
                return string(found + versionOffsetFromString - 1,
                              strnlen(found + versionOffsetFromString - 1, versionStringMaxLength));
            else
                return string(found + versionOffsetFromString,
                              strnlen(found + versionOffsetFromString, versionStringMaxLength));
        }
    }

    return "";
}

NativeLibAnalyzer::ABI NativeLibAnalyzer::getAbi(Elf *elf) {
    GElf_Ehdr ehdr;
    gelf_getehdr(elf, &ehdr);
    switch (ehdr.e_machine) {
        case EM_386:
            return ABI::x86;
        case EM_ARM:
            return ABI::arm; //armN
        case EM_MIPS:
            if ((ehdr.e_flags & EF_MIPS_ARCH) == EF_MIPS_ARCH_64
                || (ehdr.e_flags & EF_MIPS_ARCH) == EF_MIPS_ARCH_64R2
                || (ehdr.e_flags & EF_MIPS_ARCH) == EF_MIPS_ARCH_64R6
                    )
                return ABI::mips64;
            else
                return ABI::mips;
        case EM_X86_64:
            return ABI::x86_64;
        case EM_AARCH64:
            return ABI::arm64;
        default:
            return ABI::unknown;
    }
}

void NativeLibAnalyzer::analyzeLibElfEntries(Elf *elf, vector<string> &entryPoints,
                                             set<string> &frameworks,
                                             vector<string> &dependencies, bool knownSharedLib) {
    Elf_Scn *scn = nullptr;
    Elf_Data *edata = nullptr;

    while ((scn = elf_nextscn(elf, scn)) != nullptr) {
        GElf_Shdr shdr;
        if (gelf_getshdr(scn, &shdr) != NULL) {
            if (shdr.sh_type == SHT_DYNSYM) {
                edata = elf_getdata(scn, nullptr);
                if (edata != NULL) {
                    analyzeDynamicSymbols(elf, shdr, edata, entryPoints, frameworks,
                                          knownSharedLib);
                }
            }
            else if (shdr.sh_type == SHT_SYMTAB && !knownSharedLib) {
                edata = elf_getdata(scn, nullptr);
                if (edata != NULL)
                    analyzeSymbolsTable(elf, shdr, edata, frameworks);
            }
            else if (shdr.sh_type == SHT_DYNAMIC) {
                edata = elf_getdata(scn, nullptr);
                if (edata != NULL)
                    analyzeDynamicEntries(elf, shdr, edata, dependencies);
            }
            //  (ehdr.e_machine == EM_ARM && shdr.sh_type == 0x70000003U){ //SHT_ARM_ATTRIBUTES      = 0x70000003U, // ARMv5/v7 differentiation ?
        }
    }
}