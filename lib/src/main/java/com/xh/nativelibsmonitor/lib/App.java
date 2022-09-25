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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;


import com.xh.nativelibsanalyzer.lib.R;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class App {
    public final Date installdate = new Date(0);
    public final Date lastupdate = new Date(0);
    public final List<NativeLibrary> installedNativeLibs = new ArrayList<>();
    public final List<NativeLibrary> packagedNativeLibs = new ArrayList<>();
    @Nullable
    public String packagename = "";
    @Nullable
    public String appname = "";
    @Nullable
    public String versionName = "";
    public long versionCode = 0;
    public Set<String> abis_in_apk = new HashSet<>();
    public int type = ApplicationType.UNKNOWN;
    @Nullable
    public byte[] pngIcon = null;
    public Set<String> apkLocations = new HashSet<>();


    private static String humanReadableFileSize(long bytes) {
        int unit = 1024;
        if (bytes < unit) return bytes + " B";
        String[] fileSizeUnit = {"KB", "MB", "GB", "TB", "PB", "EB"};
        int index = (int) (Math.log(bytes) / Math.log(unit));
        String show = fileSizeUnit[index - 1];
        final String format = String.format("%.1f %s", bytes / Math.pow(unit, index), show);
        return format;
    }

    @NonNull
    public String toTextReport(@Nullable Context ctx) {
        if (ctx == null)
            return "";

        final StringBuilder sb = new StringBuilder();
        sb.append(ctx.getString(R.string.list_entry_title_application)).append(": \t").append(this.appname).append('\n');
        sb.append(ctx.getString(R.string.list_entry_title_link)).append(": \t").append("http://play.google.com/store/apps/details?id=").append(this.packagename).append('\n').append('\n');

        sb.append(ctx.getString(R.string.list_entry_title_type)).append(": \t").append(ApplicationType.getApplicationTypeLocalizedTitle(this.type, ctx)).append('\n');
        sb.append(ctx.getString(R.string.list_entry_title_version_code)).append(": \t").append(this.versionCode).append('\n');
        sb.append(ctx.getString(R.string.list_entry_title_version_name)).append(": \t").append(this.versionName).append('\n');
        if(this.apkLocations.size()==1) {
            sb.append(ctx.getString(R.string.list_entry_title_apk_location)).append(": \t").append(this.apkLocations.toArray()[0]).append('\n').append('\n');
        }
        else {
            sb.append(ctx.getString(R.string.list_entry_title_apk_location)).append(": \t");
            for (final String apkLocation : this.apkLocations) {
                sb.append(apkLocation).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }

        sb.append(ctx.getString(R.string.list_entry_title_installed_libraries)).append(":\n");

        for (final NativeLibrary lib : this.installedNativeLibs) {
            appendNativeLibToStringBuilder(sb, lib);
        }

        sb.append('\n');

        sb.append(ctx.getString(R.string.list_entry_title_packaged_libraries)).append(":\n");
        for (final NativeLibrary lib : this.packagedNativeLibs) {
            appendNativeLibToStringBuilder(sb, lib);
        }

        return sb.toString();
    }

    private void appendNativeLibToStringBuilder(@NonNull StringBuilder sb, @NonNull NativeLibrary lib) {
        sb.append(ABI.getStringForABI(lib.abi)).append('\t')
                .append(humanReadableFileSize(lib.size)).append('\t')
                .append(lib.path).append('\t');

        if (lib.frameworks.size() > 0) {
            sb.append(" (");
            for (final String framework : lib.frameworks) {
                sb.append(framework).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
            sb.append(")");
        }

        if (lib.dependencies.size() > 0) {
            sb.append("\t, dependencies: \t");
            for (final String dependency : lib.dependencies) {
                sb.append(dependency).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }

        sb.append('\n');
    }
}
