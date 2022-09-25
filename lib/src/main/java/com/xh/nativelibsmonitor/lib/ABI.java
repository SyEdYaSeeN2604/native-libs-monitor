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

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class ABI {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({unknown, x86, armv5, armv7, mips, x86_64, arm64, mips64, arm})
    public @interface type {}

    public static final int unknown = 0;
    public static final int x86 = 1;
    public static final int armv5 = 2;
    public static final int armv7 = 3;
    public static final int mips = 4;
    public static final int x86_64 = 5;
    public static final int arm64 = 6;
    public static final int mips64 = 7;
    public static final int arm = 8;

    private ABI() {
        throw new AssertionError();
    }

    @NonNull
    public static String getStringForABI(@type int abi) {
        switch (abi) {
            case unknown:
                return "unknown";
            case x86:
                return "x86";
            case armv5:
                return "armv5";
            case armv7:
                return "armv7";
            case mips:
                return "mips";
            case x86_64:
                return "x86_64";
            case arm64:
                return "arm64";
            case mips64:
                return "mips64";
            case arm:
                return "arm";
        }

        return "unknown";
    }

    public static int fromString(String abiString) {
        switch (abiString) {
            case "x86":
                return x86;
            case "armeabi":
                return armv5;
            case "armeabi-v7a":
                return armv7;
            case "mips":
                return mips;
            case "x86_64":
                return x86_64;
            case "arm64-v8a":
                return arm64;
            case "mips64":
                return mips64;
        }

        return unknown;
    }
}