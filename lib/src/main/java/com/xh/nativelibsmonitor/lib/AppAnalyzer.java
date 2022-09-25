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

package com.xh.nativelibsmonitor.lib;

import android.annotation.TargetApi;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.stericson.RootShell.RootShell;
import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootShell.execution.Shell;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static android.graphics.Bitmap.Config.ARGB_8888;

public class AppAnalyzer {
    private final static String TAG = "appanalyzer";
    private final static String LIB_PREFIX = "lib";
    private final static int LIB_PREFIX_LENGTH = LIB_PREFIX.length();
    private final static String LIB_SUFFIX = ".so";
    private final static int LIB_SUFFIX_LENGTH = LIB_SUFFIX.length();
    private final static int MIN_ENTRY_LENGTH = 7 + LIB_PREFIX_LENGTH + 1 + LIB_SUFFIX_LENGTH;
    private static final ThreadLocal<byte[]> mThreadLocalBuffer = new ThreadLocal<>();
    private final static float mSystemDensity = Resources.getSystem().getDisplayMetrics().density;

    static {
        try {
            System.loadLibrary("elf");
            System.loadLibrary("nativelibanalyzer");
            nativeLibLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, e.getMessage());
            nativeLibLoaded = false;
        }
    }

    private static boolean nativeLibLoaded;

    @NonNull
    private native static NativeLibrary analyzeNativeLib(String nativeLibAbsoluteLocation) throws RuntimeException;

    @NonNull
    private native static NativeLibrary analyzeNativeLib(String nativeLibPath, byte[] soFileContent, long soFileSize) throws RuntimeException;

    @NonNull
    private static native NativeLibrary analyzeNativeLib(String nativeLibPath, InputStream inputStream, long soFileSize, byte[] buffer) throws RuntimeException;

    @NonNull
    public native static String getNativeBridgeVersion();


    @NonNull
    public static App analyzeApp(@NonNull ApplicationInfo ai, @NonNull PackageManager pm) {
        App app = new App();
        app.appname = (new StringBuilder()).append(ai.loadLabel(pm)).toString();
        app.packagename = ai.packageName;

        app.apkLocations.add(ai.sourceDir);
        if (ai.publicSourceDir != null)
            app.apkLocations.add(ai.publicSourceDir);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (ai.splitSourceDirs != null)
                app.apkLocations.addAll(Arrays.asList(ai.splitSourceDirs));

            if (ai.splitPublicSourceDirs != null)
                app.apkLocations.addAll(Arrays.asList(ai.splitPublicSourceDirs));
        }


        app.pngIcon = getAppPngIcon(ai, pm);
        app.type = ApplicationType.NO_NATIVE_LIBS_INSTALLED;

        try {
            final PackageInfo pi = pm.getPackageInfo(ai.packageName, 0);
            app.versionCode = pi.versionCode;
            app.versionName = pi.versionName;
            app.lastupdate.setTime(pi.lastUpdateTime);
            app.installdate.setTime(pi.firstInstallTime);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w("appanalyzer", e.getMessage());
        }

        for (final String apkLocation : app.apkLocations) {
            try {

                addNativeLibsFromZipFileIntoApp(apkLocation, app);

            } catch (IOException e) {
                Log.d(TAG, "Couldn't open " + apkLocation + ", IOException: " + e.getMessage());
                if (RootShell.isRootAvailable()) {
                    Log.d(TAG, "Root is available, trying to get access");
                    try {
                        RootShell.getShell(true);
                        getReadAccessToFile(apkLocation);

                        addNativeLibsFromZipFileIntoApp(apkLocation, app);

                        releaseReadAccessToFile(apkLocation);

                        RootShell.closeShell(true);
                    } catch (IOException | InterruptedException | TimeoutException | RootDeniedException exception) {
                        Log.d(TAG, "Couldn't get root access: " + exception.getMessage());
                    }
                }
            }
        }

        if (ai.nativeLibraryDir != null) {
            addNativeLibsFromDirectoryToApp(ai, app);
        }

        app.type = getApplicationTypeFromInstalledLibs(app.installedNativeLibs);
        if ((app.type == ApplicationType.NO_NATIVE_LIBS_INSTALLED || app.type == ApplicationType.UNKNOWN) && app.packagedNativeLibs.size() > 0) { // if app has native libraries but none get installed, get its type from the packaged libs.
            // this is quite likely to happen starting with Marshmallow, as apps can use .so files which are stored uncompressed in the APK.
            app.type = getApplicationTypeFromPackagedLibs(app.packagedNativeLibs);
        }

        app.abis_in_apk = getABIsFromPackagedLibs(app.packagedNativeLibs);

        return app;
    }

    private static int getApplicationTypeFromInstalledLibs(@NonNull Collection<NativeLibrary> installedNativeLibs) {
        int appType = ApplicationType.NO_NATIVE_LIBS_INSTALLED;
        for (final NativeLibrary nativeLibrary : installedNativeLibs) {
            final int appTypeAssociatedToNativeLibrary = ApplicationType.getApplicationTypeAssociatedToABI(nativeLibrary.abi);
            if (appType == ApplicationType.NO_NATIVE_LIBS_INSTALLED
                    || appType == ApplicationType.UNKNOWN) { //avoid returning mixed ABI when App is x86+unknown
                appType = appTypeAssociatedToNativeLibrary;
            } else if (appTypeAssociatedToNativeLibrary != appType
                    && appTypeAssociatedToNativeLibrary != ApplicationType.UNKNOWN) { //avoid returning mixed ABI when App is x86+unknown
                appType = ApplicationType.MIX_OF_NATIVE_LIBS_INSTALLED;
                break;
            }
        }
        return appType;
    }

    private static int getApplicationTypeFromPackagedLibs(@NonNull Collection<NativeLibrary> packagedNativeLibs) {
        String[] supportedABIs;

        int appType = ApplicationType.NO_NATIVE_LIBS_INSTALLED;
        boolean nativeLibsFoundForSupportedABI = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            supportedABIs = Build.SUPPORTED_ABIS;
        else
            supportedABIs = new String[]{Build.CPU_ABI, Build.CPU_ABI2};

        for (final String supportedAbiString : supportedABIs) {
            final int supportedAbi = ABI.fromString(supportedAbiString);

            for (NativeLibrary packagedNativeLib : packagedNativeLibs) {
                final int appTypeAssociatedToNativeLibrary = ApplicationType.getApplicationTypeAssociatedToABI(packagedNativeLib.abi);

                if (packagedNativeLib.path.startsWith("lib/" + supportedAbiString + "/")) { //lib is in the standard location inside the APK for the ABI we're looking for
                    nativeLibsFoundForSupportedABI = true;

                    if (packagedNativeLib.abi == supportedAbi) { //and lib is of the corresponding ABI.
                        if (appType == ApplicationType.NO_NATIVE_LIBS_INSTALLED || appType == ApplicationType.UNKNOWN)
                            appType = appTypeAssociatedToNativeLibrary;
                        else if (appType != appTypeAssociatedToNativeLibrary
                                && appTypeAssociatedToNativeLibrary != ApplicationType.UNKNOWN) { //avoid returning mixed ABI when App is x86+unknown
                            appType = ApplicationType.MIX_OF_NATIVE_LIBS_INSTALLED;
                            break;
                        }
                    }
                } else if (!packagedNativeLib.path.startsWith("lib/") && packagedNativeLib.abi == supportedAbi) { //lib found outside of the standard location for any ABI but it's of the supported ABI, so we're taking it into consideration.
                    nativeLibsFoundForSupportedABI = true;
                    if (appType == ApplicationType.NO_NATIVE_LIBS_INSTALLED || appType == ApplicationType.UNKNOWN) {
                        appType = appTypeAssociatedToNativeLibrary;
                    }
                }
            }

            if (nativeLibsFoundForSupportedABI) break;
        }

        if (appType == ApplicationType.NO_NATIVE_LIBS_INSTALLED && packagedNativeLibs.size() > 0)
            appType = ApplicationType.UNKNOWN;

        return appType;
    }

    @NonNull
    private static Set<String> getABIsFromPackagedLibs(@NonNull Collection<NativeLibrary> packagedNativeLibs) {
        HashSet<String> abis = new HashSet<>();
        for (final NativeLibrary nativeLibrary : packagedNativeLibs) {
            abis.add(ABI.getStringForABI(nativeLibrary.abi));
        }
        return abis;
    }

    /*
    WARNING: for now, to make the difference between ARMv5 and ARMv7 libs, this needs to be called after addNativeLibsFromZipFile().
     */
    private static void addNativeLibsFromDirectoryToApp(@NonNull ApplicationInfo ai, @NonNull App app) {
        final String[] libsInInstallDirectory = (new File(ai.nativeLibraryDir)).list();
        if (libsInInstallDirectory == null)
            return;

        for (final String file : libsInInstallDirectory) {
            final NativeLibrary nativeLibrary = nativeLibLoaded ? analyzeNativeLib(ai.nativeLibraryDir + "/" + file) : new NativeLibrary(file, (new File(ai.nativeLibraryDir + "/" + file)).length());
            nativeLibrary.path = file;
            nativeLibrary.type = NativeLibrary.TYPE.INSTALLED;

            if(nativeLibrary.path.endsWith(".crc32")) //ignore .crc32 files from installed libs.
                continue;

            if (nativeLibrary.abi == ABI.arm) { //decide between ARMv5 and ARMv7.
                final String pathEnd = "/" + file;
                for (final NativeLibrary packagedLib : app.packagedNativeLibs) {
                    if (nativeLibrary.size == packagedLib.size && packagedLib.path.endsWith(pathEnd)) {
                        nativeLibrary.abi = packagedLib.abi;
                        break;
                    }
                }
            }

            app.installedNativeLibs.add(nativeLibrary);
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    @Nullable
    private static byte[] getAppPngIcon(@NonNull ApplicationInfo ai, @NonNull PackageManager pm) {

        int iconSizeInPx = (int) (mSystemDensity * 36);

        Drawable appIcon = ai.loadIcon(pm);

        if (appIcon == null) appIcon = ai.loadLogo(pm);

        if (appIcon == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            appIcon = ai.loadBanner(pm);
        }

        if (appIcon == null) appIcon = ai.loadIcon(pm);

        if (appIcon instanceof BitmapDrawable) {
            if (appIcon.getIntrinsicWidth() <= iconSizeInPx && appIcon.getIntrinsicHeight() <= iconSizeInPx) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ((BitmapDrawable) appIcon).getBitmap().compress(Bitmap.CompressFormat.PNG, 100, bos);
                return bos.toByteArray();
            } else {
                Bitmap bitmap = Bitmap.createScaledBitmap(((BitmapDrawable) appIcon).getBitmap(), iconSizeInPx, iconSizeInPx, true);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
                return bos.toByteArray();
            }
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appIcon instanceof AdaptiveIconDrawable) {
                int width = appIcon.getIntrinsicWidth();
                int height = appIcon.getIntrinsicHeight();

                Bitmap bitmap = Bitmap.createBitmap(width, height, ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                appIcon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                appIcon.draw(canvas);

                if (appIcon.getIntrinsicWidth() <= iconSizeInPx && appIcon.getIntrinsicHeight() <= iconSizeInPx) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
                    return bos.toByteArray();
                } else {
                    Bitmap bitmapScaled = Bitmap.createScaledBitmap(bitmap, iconSizeInPx, iconSizeInPx, true);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bitmapScaled.compress(Bitmap.CompressFormat.PNG, 100, bos);
                    return bos.toByteArray();
                }
            }
        }

        return null;
    }

    private static void getReadAccessToFile(@NonNull String filePath) throws IOException, TimeoutException, RootDeniedException, InterruptedException {
        final String mntPath = filePath.substring(0, filePath.lastIndexOf('/'));
        final Command command = new Command(0, "mount -o remount,rw " + mntPath, "chmod 644 " + filePath);
        Shell.runRootCommand(command);

        if (!command.isFinished()) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (command) {
                command.wait();
            }
        }
    }

    private static void releaseReadAccessToFile(@NonNull String filePath) throws IOException, TimeoutException, RootDeniedException {
        final String mntPath = filePath.substring(0, filePath.lastIndexOf('/'));
        final Command command = new Command(0, "chmod 640 " + filePath, "mount -o remount,ro " + mntPath);

        Shell.runRootCommand(command);
    }

    private static void addNativeLibsFromZipFileIntoApp(String apkPath, @NonNull App app) throws IOException {
        final ZipFile zipFile = new ZipFile(apkPath);

        try {
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {

                final ZipEntry entry = entries.nextElement();

                // skip directories
                if (entry.isDirectory())
                    continue;

                final String entryName = entry.getName();

                if (entryName.length() < MIN_ENTRY_LENGTH || !entryName.endsWith(LIB_SUFFIX))
                    continue;

                int lastSlash = entryName.lastIndexOf('/');
                if (lastSlash < 0 || !entryName.regionMatches(lastSlash + 1, LIB_PREFIX, 0, LIB_PREFIX_LENGTH))
                    continue;

                final NativeLibrary nativeLibrary = nativeLibLoaded ? analyzeNativeLib(entry, zipFile) : new NativeLibrary(entryName, zipFile.size());
                nativeLibrary.path = entryName;
                nativeLibrary.type = NativeLibrary.TYPE.IN_PACKAGE;

                if (nativeLibrary.abi == ABI.arm) {
                    int slashBeforeLastSlash = entryName.substring(0, lastSlash).lastIndexOf('/');
                    final String dirName = entryName.substring(slashBeforeLastSlash + 1, lastSlash);

                    if (dirName.equals("armeabi"))
                        nativeLibrary.abi = ABI.armv5;
                    if (dirName.equals("armeabi-v7a"))
                        nativeLibrary.abi = ABI.armv7;
                    if (dirName.equals("arm64-v8a"))
                        nativeLibrary.abi = ABI.arm64;
                }

                app.packagedNativeLibs.add(nativeLibrary);
            }
        } finally {
            zipFile.close();
        }
    }

    @NonNull
    private static NativeLibrary analyzeNativeLib(@NonNull final ZipEntry entry, @NonNull final ZipFile zipFile) throws IOException {
        if (entry.getSize() < Integer.MAX_VALUE) {
            int numBytesToRead = (int) entry.getSize();

            byte[] buffer = mThreadLocalBuffer.get();
            if (buffer == null) {
                buffer = new byte[2048];
                mThreadLocalBuffer.set(buffer);
            }

            InputStream inputStream = zipFile.getInputStream(entry);
            NativeLibrary result = analyzeNativeLib(entry.getName(), inputStream, numBytesToRead, buffer);
            inputStream.close();
            return result;

            //return analyzeNativeLib(entry.getName(), zipFile.getInputStream(entry), numBytesToRead);

            /*former method, with big Java alloc:*/
            //    byte[] soFileContentBytes = new byte[numBytesToRead];
            //    new DataInputStream(zipFile.getInputStream(entry)).readFully(soFileContentBytes);
            //    return analyzeNativeLib(entry.getName(), soFileContentBytes, numBytesToRead);
        } else
            return new NativeLibrary(entry.getName(), entry.getSize());
    }
}
