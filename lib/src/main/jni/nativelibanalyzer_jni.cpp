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

#include <jni.h>
#include <stdexcept>
#include <fcntl.h>
#include <unistd.h>

#include "nativelibanalyzer.h"

#ifdef DEBUG
#warning sources are compiled with DEBUG set.
#endif

#ifdef DEBUG
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "nativelibsanalyzer", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "nativelibsanalyzer", __VA_ARGS__)
#else
#define LOGI(...)
#define LOGW(...)
#endif

#ifdef EXTRACT_SYMBOLS_TO_FILE
#include <iostream>
#include <fstream>
#endif

using namespace std;

template<typename T>
jobjectArray createJavaStringArray(JNIEnv *env, const T &stringsContainer) {
    jclass strCls = env->FindClass("java/lang/String");
    jobjectArray javaArray = env->NewObjectArray(stringsContainer.size(), strCls, nullptr);
    int i = 0;
    for (const string &element : stringsContainer) {
        jstring str = env->NewStringUTF(element.c_str());
        env->SetObjectArrayElement(javaArray, i, str);
        env->DeleteLocalRef(str);
        ++i;
    }
    return javaArray;
}

jobject createNativeLibObject(JNIEnv *env, jlong soFileLength, NativeLibAnalyzer::ABI abi,
                              const vector<string> &entryPoints, const set<string> &frameworks,
                              const vector<string> &dependencies) {

    jclass nativeLibObjectClass = env->FindClass("com/xh/nativelibsmonitor/lib/NativeLibrary");
    jmethodID nativeLibObjectConstructor = env->GetMethodID(nativeLibObjectClass, "<init>",
                                                            "(JI[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)V");

    jobjectArray entryPointsArray = createJavaStringArray(env, entryPoints);
    jobjectArray frameworksArray = createJavaStringArray(env, frameworks);
    jobjectArray dependenciesArray = createJavaStringArray(env, dependencies);

    jobject object = env->NewObject(nativeLibObjectClass, nativeLibObjectConstructor, soFileLength,
                                    abi, //WARNING: NativeLibAnalyzer::ABI has to match NativeLibrary ABI type.
                                    entryPointsArray, frameworksArray, dependenciesArray);

    env->DeleteLocalRef(entryPointsArray);
    env->DeleteLocalRef(frameworksArray);
    env->DeleteLocalRef(dependenciesArray);

    jthrowable ex = env->ExceptionOccurred();
    if (ex != nullptr) {
        env->ExceptionClear();
        throw new runtime_error("Couldn't create NativeLibrary object.");
    }

    return object;
}

jobject analyzeNativeLib(JNIEnv *env, jclass, jstring lib, char *soFileContent = nullptr,
                         jlong soFileLength = 0) {
    jobject result = nullptr;
    vector<string> entryPoints;
    set<string> frameworks;
    vector<string> dependencies;
    NativeLibAnalyzer::ABI abi = NativeLibAnalyzer::ABI::unknown;

#ifdef EXTRACT_SYMBOLS_TO_FILE
    ofstream myfile;
    myfile.open("/sdcard/entry_points.txt", ios::app);
#endif

    const char *libName = env->GetStringUTFChars(lib, nullptr);
    const string libNameStr = strrchr(libName, '/') + 1;

    bool knownSharedLib = false;
    bool analyzingFile = (soFileContent == nullptr);
    int fd = -1;

    {
        string framework = NativeLibAnalyzer::getFrameworkFromKnownSharedLibs(libNameStr);
        if (framework.size() > 0) {
            frameworks.insert(framework);
            knownSharedLib = true;
        }
    }

    if (analyzingFile) {
        fd = open(libName, O_RDONLY);
        if (fd > -1) {
            off_t length = lseek(fd, 0, SEEK_END);
            lseek(fd, 0, SEEK_SET);

            if (length < 0) soFileLength = 0;
            else soFileLength = length;
        }
    }

    env->ReleaseStringUTFChars(lib, libName);


    if (fd > -1 || !analyzingFile) {
        elf_version(EV_CURRENT);
        Elf *elf = nullptr;

        if (analyzingFile && fd > -1)
            elf = elf_begin(fd, ELF_C_READ_MMAP,
                            nullptr); //ELF_C_READ_MMAP caused crashes in regular libelf, had to fix: http://comments.gmane.org/gmane.comp.sysutils.elfutils.devel/4060
        else if (!analyzingFile)
            elf = elf_memory(soFileContent, (size_t) soFileLength);

        if (elf != nullptr) {
            abi = NativeLibAnalyzer::getAbi(elf);

            NativeLibAnalyzer::analyzeLibElfEntries(elf, entryPoints, frameworks, dependencies,
                                                    knownSharedLib);

            if (libNameStr.find("libunity") != string::npos) { // get Unity version
                const string unityVersion = NativeLibAnalyzer::getUnityVersion(elf, fd,
                                                                               soFileContent);
                if (unityVersion.size() > 0)
                    frameworks.insert("Unity " + unityVersion);
            }

            elf_end(elf);
        }
        else {
            if (analyzingFile)
                    LOGW("couldn't load elf file %s from disk.", libNameStr.c_str());
            else
                    LOGW("couldn't load elf file %s from apk.", libNameStr.c_str());
        }

        if (analyzingFile && fd > -1) {
            close(fd);
        }
    }

#ifdef EXTRACT_SYMBOLS_TO_FILE
    myfile.close();
#endif

    try {
        result = createNativeLibObject(env, soFileLength, abi, entryPoints, frameworks,
                                       dependencies);
    }
    catch (const runtime_error &e) {
        result = nullptr;
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
    }

    return result;
}

jobject analyzeNativeLibFromFile(JNIEnv *env, jclass clazz, jstring lib) {
    return analyzeNativeLib(env, clazz, lib);
}

jobject analyzeNativeLibFromJavaMemory(JNIEnv *env, jclass clazz, jstring lib,
                                       jbyteArray soFileContent, jlong numBytes) {
    char *bytes = reinterpret_cast<char *>(env->GetByteArrayElements(soFileContent, nullptr));
    jobject result = analyzeNativeLib(env, clazz, lib, bytes, numBytes);
    env->ReleaseByteArrayElements(soFileContent, reinterpret_cast<jbyte *>(bytes), JNI_ABORT);
    return result;
}

jobject analyzeNativeLibFromJavaInputStream(JNIEnv *env, jclass clazz, jstring lib,
                                            jobject soFileInputStream, jlong numBytes,
                                            jbyteArray buffer) {
    char *soFileContent = new char[numBytes];

    jclass inputStreamClass = env->FindClass("java/io/InputStream");
    jmethodID readMethod = env->GetMethodID(inputStreamClass, "read", "([B)I");

    jint bytesRead = 0;
    jint ret = 0;
    while (ret >= 0) {
        ret = env->CallIntMethod(soFileInputStream, readMethod, buffer);
        if (ret > 0) {
            if (bytesRead + ret <= numBytes) {
                env->GetByteArrayRegion(buffer, 0, ret,
                                        reinterpret_cast<jbyte *>(soFileContent) + bytesRead);
            }
            bytesRead += ret;
        }
    }

    jobject result = analyzeNativeLib(env, clazz, lib, soFileContent, numBytes);
    delete[] soFileContent;
    return result;
}

jstring getNativeBridgeVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF(NativeLibAnalyzer::getHoudiniVersion().c_str());
}

static JNINativeMethod exposedMethods[] = {
        {"analyzeNativeLib",       "(Ljava/lang/String;)Lcom/xh/nativelibsmonitor/lib/NativeLibrary;",                         (void *) analyzeNativeLibFromFile},
        {"analyzeNativeLib",       "(Ljava/lang/String;[BJ)Lcom/xh/nativelibsmonitor/lib/NativeLibrary;",                      (void *) analyzeNativeLibFromJavaMemory},
        {"analyzeNativeLib",       "(Ljava/lang/String;Ljava/io/InputStream;J[B)Lcom/xh/nativelibsmonitor/lib/NativeLibrary;", (void *) analyzeNativeLibFromJavaInputStream},
        {"getNativeBridgeVersion", "()Ljava/lang/String;",                                                                     (void *) getNativeBridgeVersion},
};

jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    jclass clazz = env->FindClass("com/xh/nativelibsmonitor/lib/AppAnalyzer");
    if (clazz == nullptr) return JNI_ERR;
    env->RegisterNatives(clazz, exposedMethods, sizeof(exposedMethods) / sizeof(JNINativeMethod));
    env->DeleteLocalRef(clazz);

    return JNI_VERSION_1_6;
}
