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

#ifndef NATIVELIBSMONITOR_NATIVELIBANALYZER_H
#define NATIVELIBSMONITOR_NATIVELIBANALYZER_H

#include <string>
#include <set>
#include <vector>
#include <map>
#include <gelf.h>

namespace NativeLibAnalyzer {

    enum ABI {unknown, x86, armv5, armv7, mips, x86_64, arm64, mips64, arm};

    void analyzeDynamicSymbols(Elf *elf, GElf_Shdr shdr, Elf_Data *edata,
                        std::vector<std::string> &entryPoints,
                        std::set<std::string> &frameworks, bool knownSharedLib);

    void analyzeSymbolsTable(Elf *elf, GElf_Shdr shdr, Elf_Data *edata,
                      std::set<std::string> &frameworks);

    void analyzeDynamicEntries(Elf *elf, GElf_Shdr shdr, Elf_Data *edata,
                        std::vector<std::string> &dependencies);

    std::string getFrameworkFromKnownSharedLibs(std::string libNameStr);

    std::pair<uint64_t, uint64_t> getRoDataStartAndSize(Elf *elf);

    std::string getHoudiniVersion();

    std::string getUnityVersion(Elf *elf, int fd, char *soFileContent);

    ABI getAbi(Elf *elf);

    void analyzeLibElfEntries(Elf *elf, std::vector<std::string> &entryPoints,
                              std::set<std::string> &frameworks,
                              std::vector<std::string> &dependencies, bool knownSharedLib);

    static const std::map<const std::string, const std::string> staticLibIdentifiers = {
            {"zlibVersion",                                               "zlib"},
            {"png_flush",                                                 "libpng"},
            {"jpeg_input_complete",                                       "libjpeg"},
            {"lua_typename",                                              "Lua"},
            {"curl_global_init",                                          "libcurl"},
            {"FT_Render_Glyph",                                           "freetype"},
            {"libpd_init_audio",                                          "libpd"},
            {"objc_initWeak",                                             "clang-ObjC"},
            {"av_free",                                                   "libav"},
            {"FMOD_System_Init",                                          "FMOD"},
            {"ft_validator_init",                                         "freetype"},
            {"alcOpenDevice",                                             "OpenAL"},
            {"RSA_public_encrypt",                                        "OpenSSL"},
            {"RSA_public_decrypt",                                        "OpenSSL"},
            {"OpenSSLDie",                                                "OpenSSL"},
            {"xmlXPathInit",                                              "libxml"},
            {"Java_com_badlogic_gdx_graphics_g2d_Gdx2DPixmap_load",       "libgdx"},
            {"VisionEnterForegroundFunction",                             "Project Anarchy"},
            {"zzip_open",                                                 "zziplib"},
            {"vorbis_version_string",                                     "libvorbis"},
            {"ff_init_scantable",                                         "FFMpeg"},
            {"av_aes_init",                                               "FFMpeg"},
            {"cpSpaceInit",                                               "Chipmunk"},
            {"chromium_jinit_upsampler",                                  "chromium"},
            {"fz_write_document",                                         "mupdf"},
            {"clFinish",                                                  "OpenCL"},
            {"cJSON_Parse",                                               "cJSON"},
            {"Java_org_cocos2dx_lib_Cocos2dxRenderer_nativeInit",         "Cocos2dx"},
            {"cocos2dVersion",                                            "Cocos2d"},
            {"ImmVibeInitialize",                                         "Immersion Haptic SDK"},
            {"Java_com_metaio_sdk_jni_MetaioSDKJNI_equals",               "Metaio"},
            {"ImmVibeInitialize",                                         "Immersion Haptic SDK"},
            {"lame_init",                                                 "LAME"},
            {"mpg123_init",                                               "libmpg123"},
            {"TBB_runtime_interface_version",                             "TBB"},
            {"Java_com_apportable_MainThread_nativeRun",                  "Apportable"},
            {"Java_com_apportable_activity_VerdeActivity_nativeOnCreate", "Apportable"},
            {"Java_nagra_android_sdk_PRMHandler_start",                   "Nagra Media Player SDK"},
            {"_ZNK2cv3Mat6copyToERKNS_12_OutputArrayE",                   "OpenCV"},
            {"cvLoad",                                                    "OpenCV"},
            {"hkErrorMessage",                                            "Project Anarchy"},
            {"__intel_cpu_features_init",                                 "Intel Compiler"},
            {"_ZN7UEngine7PreExitEv",                                     "Unreal Engine"},
            {"_ZN7UEngine8IsEditorEv",                                    "Unreal Engine"},
            {"Java_pl_droidsonroids_gif_GifInfoHandle_renderFrame",       "android-gif-drawable"},
            {"Java_com_esri_android_map_MapSurface_nativeMapCreate",      "ArcGIS"},
            {"Java_com_insidesecure_drmagent_v2_internal"
                     "_DRMAgent_NativeBridge_nativeIsSecureDevice",       "Inside Secure"},
            {"GameServices_Builder_Create",                               "Google Play Game Services"},
    };

    static const std::map<const std::string, const std::string> sharedLibIdentifiers = {
            {"libaacdecoder.so",                     "aac-decoder"},
            {"libadlatte.sdk.0.5.so.so",             "AdLatte"},
            {"liballjoyn_java.so",                   "AllJoyn"},
            {"libAmazonGamesJni.so",                 "Amazon Games"},
            {"libandengine.so",                      "AndEngine"},
            {"libandenginephysicsbox2dextension.so", "AndEngine"},
            {"libAVEAndroid.so",                     "Adobe Player SDK"},
            {"libavcodec.so",                        "FFMpeg"},
            {"libavdevice.so",                       "FFMpeg"},
            {"libavfilter.so",                       "FFMpeg"},
            {"libavformat.so",                       "FFMpeg"},
            {"libavutil.so",                         "FFMpeg"},
            {"libffmpeg.so",                         "FFMpeg"},
            {"libpostproc.so",                       "FFMpeg"},
            {"libswresample.so",                     "FFMpeg"},
            {"libswscale.so",                        "FFMpeg"},
            {"libswscale.so",                        "FFMpeg"},
            {"libCocoonJSLib.so",                    "CocoonJS"},
            {"libconceal.so",                        "Conceal"},
            {"liblime.so",                           "OpenFL"},
            {"libnme.so",                            "OpenFL"},
            {"libApplicationMain.so",                "OpenFL"},
            {"libarchitect.so",                      "Wikitude"},
            {"libaviary_exif.so",                    "Aviary"},
            {"libaviary_moalite.so",                 "Aviary"},
            {"libaviary_native.so",                  "Aviary"},
            {"libbspatch.so",                        "Umeng"},
            {"libbox2d.so",                          "Box2D"},
            {"libcardioDecider.so",                  "card.io"},
            {"libcardioRecognizer_tegra2.so",        "card.io"},
            {"libcardioRecognizer.so",               "card.io"},
            {"libchipmunk.so",                       "Chipmunk"},
            {"libcocos2dcpp.so",                     "Cocos2dx"},
            {"libcorona.so",                         "Corona"},
            {"libDropboxSync.so",                    "Dropbox Sync"},
            {"libeveryplay.so",                      "EveryPlay"},
            {"libQCAR.so",                           "Vuforia"},
            {"libVuforia.so",                        "Vuforia"},
            {"libFoundation.so",                     "Apportable"},
            {"libfmodevent.so",                      "FMOD"},
            {"libfmodex.so",                         "FMOD"},
            {"freeglut-gles.so",                     "freeglut"},
            {"libgdx-freetype.so",                   "libGDX"},
            {"libgdx.so",                            "libGDX"},
            {"libgpuimage-library.so",               "GPUImage"},
            {"libandroidgl20.so",                    "GL2-android"},
            {"libImmEmulatorJ.so",                   "Immersion Haptic SDK"},
            {"libippresample.so",                    "IPP"},
            {"libjniARToolKitPlus.so",               "ARToolkitPlus"},
            {"libkamcord.so",                        "Kamcord"},
            {"libkamcordcore.so",                    "Kamcord"},
            {"libkroll-v8.so",                       "Appcelerator"},
            {"liblocSDK3.so",                        "Baidu GeoLocation"},
            {"liblocSDK4.so",                        "Baidu GeoLocation"},
            {"libmetaiosdk.so",                      "Metaio SDK"},
            {"libmupdf.so",                          "MuPDF"},
            {"libmoai.so",                           "Moai"},
            {"libmobileapptracker.so",               "MobileAppTracking"},
            {"libmonodroid.so",                      "Mono / Xamarin"},
            {"libmonosgen-2.0.so",                   "Monogame"},
            {"libmp3lame.so",                        "LAME"},
            {"libnativeinterface.so",                "Zirconia"},
            {"libnexplayerengine.so",                "NexPlayer SDK"},
            {"libnmpsdk_kk.so",                      "Nagra Media Player SDK"},
            {"libnmsp_speex.so",                     "Nuance Mobile SDK"},
            {"libogrekit.so",                        "OgreKit"},
            {"libopenal.so",                         "OpenAL"},
            {"libopencv_core.so",                    "OpenCV"},
            {"libopencv_java.so",                    "OpenCV"},
            {"libopencv_imgproc.so",                 "OpenCV"},
            {"libopenvpn.so",                        "OpenVPN"},
            {"libsecexe.so",                         "Bangcle"},
            {"libsecmain.so",                        "Bangcle"},
            {"libopenvpn_util.so",                   "OpenVPN"},
            {"librdpdf.so",                          "PDFViewer"},
            {"libtbb.so",                            "TBB"},
            {"libunity.so",                          "Unity"},
            {"libUSToolkit.so",                      "T-Store IAB SDK"},
            {"libvinit.so",                          "Vitamio SDK"},
            {"libxwalkcore.so",                      "crosswalk"},
            {"libysshared.so",                       "Adobe Air"},
            {"libjniavcodec.so",                     "JavaCV"},
            {"libjniavfilter.so",                    "JavaCV"},
            {"libjniavformat.so",                    "JavaCV"},
            {"libjniavutil.so",                      "JavaCV"},
            {"libjniswresample.so",                  "JavaCV"},
            {"libjnipostproc.so",                    "JavaCV"},
            {"libjniswscale.so",                     "JavaCV"},
            {"libjnicvkernels.so",                   "JavaCV"},
            {"libS3DClient.so",                      "Marmalade SDK"},
            {"libsqlcipher_android.so",              "SQLCipher"},
            {"libtess.so",                           "Tesseract OCR"},
            {"liblept.so",                           "Leptonica"},
            {"libdatabase_sqlcipher.so",             "SQLCipher"},
            {"libkroll-v8.so",                       "Titanium"},
            {"libredlaser.so",                       "RedLaser"},
            {"libRSSupport.so",                      "RenderScript"},
            {"librsjni.so",                          "RenderScript"},
            {"libsonic.so",                          "Sonic"},
            {"libspeex.so",                          "Speex"},
            {"libssl.so",                            "OpenSSL"},
            {"libcrypto.so",                         "OpenSSL"},
            {"libSyncNow.so",                        "SyncNow"},
            {"libSyncNowJNI.so",                     "SyncNow"},
            {"libspeex.so",                          "Speex"},
            {"libtorque2d.so",                       "Torque 2D"},
            {"libUnrealEngine3.so",                  "Unreal Engine"},
            {"libUE4.so",                            "Unreal Engine"},
            {"libvinit.so",                          "Vitamio SDK"},
            {"libvxlnative.so",                      "Voxel SDK"},
            {"libwiengine.so",                       "Wi Engine"},
            {"libyoyo.so",                           "GameMaker: Studio"},
            {"libzbarjni.so",                        "ZBar"},
            {"libcrittercism-ndk.so",                "crittercism"},
            {"libcrittercism-v3.so",                 "crittercism"},
            {"libcri_ware_unity.so",                 "CRI middleware"},
            {"lib__57d5__.so",                       "HYPERTECH CrackProof"},
            {"lib__5b53__.so",                       "HYPERTECH CrackProof"},
            {"libnakamap.so",                        "Lobi REC SDK"},
            {"liblobirec.so",                        "Lobi REC SDK"},
            {"liblobirecaudio.so",                   "Lobi REC SDK"},
            {"liblobirecexternalaudio.so",           "Lobi REC SDK"},
            {"liblobirecmuxer.so",                   "Lobi REC SDK"},
            {"liblobirecunity.so",                   "Lobi REC SDK"},
            {"liblobiresampler.so",                  "Lobi REC SDK"},
    };

    static const std::map<const std::string, const std::string> sharedLibSubstringIdentifiers = {
            {"libIPP",                     "IPP"},
            {"libjniopencv_",             "JavaCV"},
            {"libs3e",             "Marmalade SDK"},
            {"libsdl",             "SDL"}
    };

};

#endif //NATIVELIBSMONITOR_NATIVELIBANALYZER_H
