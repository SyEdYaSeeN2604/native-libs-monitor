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

package com.xh.nativelibsmonitor.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;

import com.xh.nativelibsmonitor.lib.NativeLibrary;

import java.util.Arrays;

class NativeLibraryEntry {

    public static final String TABLE_NAME = "NativeLibraries";
    public static final String COL_ID = "_id";
    public static final String COL_APPLICATIONID = "applicationid";
    public static final String COL_ABI = "abi";
    public static final String COL_ENTRYPOINTS = "entrypoints";
    public static final String COL_PATH = "path";
    public static final String COL_SIZE = "size";
    public static final String COL_TYPE = "type";
    public static final String COL_FRAMEWORKS = "frameworks";
    public static final String COL_DEPENDENCIES = "dependencies";

    // For database projection so order is consistent
    public static final String[] FIELDS = {COL_ID, COL_APPLICATIONID, COL_ABI, COL_ENTRYPOINTS, COL_PATH, COL_SIZE, COL_TYPE, COL_FRAMEWORKS, COL_DEPENDENCIES};

    public static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + "("
                    + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + COL_APPLICATIONID + " TEXT NOT NULL DEFAULT '',"
                    + COL_ABI + " INTEGER NOT NULL DEFAULT 0,"
                    + COL_ENTRYPOINTS + " TEXT NOT NULL DEFAULT '',"
                    + COL_PATH + " TEXT NOT NULL DEFAULT '',"
                    + COL_SIZE + " INTEGER NOT NULL DEFAULT 0,"
                    + COL_TYPE + " INTEGER NOT NULL DEFAULT 0,"
                    + COL_FRAMEWORKS + " INTEGER NOT NULL DEFAULT 0,"
                    + COL_DEPENDENCIES + " INTEGER NOT NULL DEFAULT 0,"
                    + "CONSTRAINT _UNIQUE UNIQUE (" + COL_APPLICATIONID + ", " + COL_PATH + ", " + COL_TYPE + ", " + COL_ABI + ") ON CONFLICT REPLACE,"
                    + "FOREIGN KEY(" + COL_APPLICATIONID + ") REFERENCES " + ApplicationEntry.TABLE_NAME + "(" + ApplicationEntry.COL_ID + ") ON DELETE CASCADE"
                    + "); CREATE INDEX " + TABLE_NAME + "_" + COL_PATH + "_idx ON " + TABLE_NAME + "(" + COL_PATH + ");";

    // Fields corresponding to database columns
    public long id = -1;
    public long applicationId = -1;
    public NativeLibrary nativeLibrary = new NativeLibrary();

    public NativeLibraryEntry() {
    }

    /**
     * Convert information from the database into a Library object.
     */
    public NativeLibraryEntry(@NonNull final Cursor cursor) {
        this.id = cursor.getLong(0);
        this.applicationId = cursor.getLong(1);
        nativeLibrary.abi = cursor.getInt(2);

        String entryPointsString = cursor.getString(3);
        if (entryPointsString != null && entryPointsString.length() > 0)
            nativeLibrary.entryPoints = Arrays.asList(entryPointsString.split(":"));

        nativeLibrary.path = cursor.getString(4);
        nativeLibrary.size = cursor.getLong(5);
        nativeLibrary.type = cursor.getInt(6);

        String frameworksString = cursor.getString(7);
        if (frameworksString != null && frameworksString.length() > 0)
            nativeLibrary.frameworks = Arrays.asList(frameworksString.split(":"));

        String dependenciesString = cursor.getString(8);
        if (dependenciesString != null && dependenciesString.length() > 0)
            nativeLibrary.dependencies = Arrays.asList(dependenciesString.split(":"));
    }

    /**
     * Return the fields in a ContentValues object, suitable for insertion
     * into the database.
     */
    @NonNull
    public ContentValues getContent() {
        final ContentValues values = new ContentValues();
        // Note that ID is NOT included here
        values.put(COL_APPLICATIONID, applicationId);
        values.put(COL_ABI, nativeLibrary.abi);
        values.put(COL_ENTRYPOINTS, Arrays.toString(nativeLibrary.entryPoints.toArray()).replace(", ", ":").replaceAll("[\\[\\]]", ""));
        values.put(COL_PATH, nativeLibrary.path);
        values.put(COL_SIZE, nativeLibrary.size);
        values.put(COL_TYPE, nativeLibrary.type);
        values.put(COL_FRAMEWORKS, Arrays.toString(nativeLibrary.frameworks.toArray()).replace(", ", ":").replaceAll("[\\[\\]]", ""));
        values.put(COL_DEPENDENCIES, Arrays.toString(nativeLibrary.dependencies.toArray()).replace(", ", ":").replaceAll("[\\[\\]]", ""));

        return values;
    }
}
