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
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import com.xh.nativelibsanalyzer.lib.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class ApplicationType {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({UNKNOWN, NO_NATIVE_LIBS_INSTALLED, ARM_NATIVE_LIBS_ONLY_INSTALLED, ARM_64_NATIVE_LIBS_ONLY_INSTALLED, MIPS_NATIVE_LIBS_ONLY_INSTALLED, MIPS_64_NATIVE_LIBS_ONLY_INSTALLED, X86_NATIVE_LIBS_ONLY_INSTALLED, X86_64_NATIVE_LIBS_ONLY_INSTALLED, MIX_OF_NATIVE_LIBS_INSTALLED})
    public @interface type {}
    public static final int UNKNOWN = 0;
    public static final int NO_NATIVE_LIBS_INSTALLED = 1;
    public static final int ARM_NATIVE_LIBS_ONLY_INSTALLED = 2;
    public static final int ARM_64_NATIVE_LIBS_ONLY_INSTALLED = 3;
    public static final int MIPS_NATIVE_LIBS_ONLY_INSTALLED = 4;
    public static final int MIPS_64_NATIVE_LIBS_ONLY_INSTALLED = 5;
    public static final int X86_NATIVE_LIBS_ONLY_INSTALLED = 6;
    public static final int X86_64_NATIVE_LIBS_ONLY_INSTALLED = 7;
    public static final int MIX_OF_NATIVE_LIBS_INSTALLED = 8;

    private ApplicationType() {
        throw new AssertionError();
    }

    @type
    public static int getApplicationTypeAssociatedToABI(@ABI.type int abi) {
        switch (abi) {
            case ABI.x86:
                return X86_NATIVE_LIBS_ONLY_INSTALLED;
            case ABI.x86_64:
                return X86_64_NATIVE_LIBS_ONLY_INSTALLED;
            case ABI.arm:
            case ABI.armv5:
            case ABI.armv7:
                return ARM_NATIVE_LIBS_ONLY_INSTALLED;
            case ABI.arm64:
                return ARM_64_NATIVE_LIBS_ONLY_INSTALLED;
            case ABI.mips:
                return MIPS_NATIVE_LIBS_ONLY_INSTALLED;
            case ABI.mips64:
                return MIPS_64_NATIVE_LIBS_ONLY_INSTALLED;
            default:
                return UNKNOWN;
        }
    }

    public static String getApplicationTypeLocalizedTitle(@type int applicationType, @NonNull Context ctx) {
        switch (applicationType) {
            case UNKNOWN:
                return ctx.getString(R.string.unknown);
            case NO_NATIVE_LIBS_INSTALLED:
                return ctx.getString(R.string.application_type_no_libs);
            case X86_NATIVE_LIBS_ONLY_INSTALLED:
                return ctx.getString(R.string.application_type_x86_libs);
            case X86_64_NATIVE_LIBS_ONLY_INSTALLED:
                return ctx.getString(R.string.application_type_x86_64_libs);
            case ARM_NATIVE_LIBS_ONLY_INSTALLED:
                return ctx.getString(R.string.application_type_arm_libs);
            case ARM_64_NATIVE_LIBS_ONLY_INSTALLED:
                return ctx.getString(R.string.application_type_arm64_libs);
            case MIPS_NATIVE_LIBS_ONLY_INSTALLED:
                return ctx.getString(R.string.application_type_mips_libs);
            case MIPS_64_NATIVE_LIBS_ONLY_INSTALLED:
                return ctx.getString(R.string.application_type_mips_64_libs);
            case MIX_OF_NATIVE_LIBS_INSTALLED:
                return ctx.getString(R.string.application_type_mixed_abis_libs);
        }
        return ctx.getString(R.string.unknown);
    }
}
