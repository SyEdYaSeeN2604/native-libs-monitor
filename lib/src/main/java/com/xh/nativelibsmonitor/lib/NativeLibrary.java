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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NativeLibrary {

    public int abi = ABI.unknown;

    public List<String> entryPoints = new ArrayList<>();
    public List<String> frameworks = new ArrayList<>();
    public List<String> dependencies = new ArrayList<>();
    @NonNull
    public String path = "";
    public long size = -1;
    public int type = TYPE.UNDEFINED;

    public NativeLibrary() {
    }

    public NativeLibrary(@NonNull String name, long size) {
        this.path = name;
        this.size = size;
    }

    public NativeLibrary(@NonNull String name, long size, int abi, int type, List<String> entryPoints, List<String> frameworks, List<String> dependencies) {
        this.path = name;
        this.size = size;
        this.abi = abi;
        this.entryPoints = entryPoints;
        this.frameworks = frameworks;
        this.dependencies = dependencies;
        this.type = type;
    }

    public NativeLibrary(long size, int abi, @Nullable String[] entryPoints, @Nullable String[] frameworks, @Nullable String[] dependencies) {
        this.size = size;
        this.abi = abi;
        if (entryPoints != null)
            this.entryPoints = Arrays.asList(entryPoints);
        if (frameworks != null)
            this.frameworks = Arrays.asList(frameworks);
        if (dependencies != null)
            this.dependencies = Arrays.asList(dependencies);
    }

    public static final class TYPE {
        public static final int UNDEFINED = 0;
        public static final int INSTALLED = 1;
        public static final int IN_PACKAGE = 2;

        private TYPE() {
            throw new AssertionError();
        }
    }
}
