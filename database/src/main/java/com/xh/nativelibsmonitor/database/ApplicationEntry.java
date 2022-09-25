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

import com.xh.nativelibsmonitor.lib.App;

import java.util.Arrays;
import java.util.HashSet;


public class ApplicationEntry {
    public static final String TABLE_NAME = "Applications";
    public static final String COL_ID = "_id";
    public static final String COL_PACKAGENAME = "packagename";
    public static final String COL_VERSIONNAME = "versionname";
    public static final String COL_VERSIONCODE = "versioncode";
    public static final String COL_APKLOCATIONS = "apklocations";
    public static final String COL_APPNAME = "appname";
    public static final String COL_PNGICON = "pngicon";
    public static final String COL_APPLICATIONTYPE = "applicationtype";
    public static final String COL_ABIS_IN_APK = "abisinapk";
    public static final String COL_INSTALLDATE = "installdate";
    public static final String COL_LASTUPDATE = "lastupdate";

    // For database projection so order is consistent
    public static final String[] FIELDS = {COL_ID, COL_PACKAGENAME, COL_VERSIONNAME, COL_VERSIONCODE, COL_APKLOCATIONS ,COL_APPNAME, COL_PNGICON, COL_APPLICATIONTYPE, COL_ABIS_IN_APK, COL_INSTALLDATE, COL_LASTUPDATE};

    /*
     * The SQL code that creates a Table for storing Persons in.
     * Note that the last row does NOT end in a comma like the others.
     * This is a common source of error.
     */
    public static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + "("
                    + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + COL_PACKAGENAME + " TEXT NOT NULL DEFAULT '',"
                    + COL_VERSIONNAME + " TEXT DEFAULT '',"
                    + COL_VERSIONCODE + " INTEGER NOT NULL DEFAULT 0,"
                    + COL_APKLOCATIONS + " TEXT NOT NULL DEFAULT '',"
                    + COL_APPNAME + " TEXT NOT NULL DEFAULT '',"
                    + COL_PNGICON + " BLOB,"
                    + COL_APPLICATIONTYPE + " INTEGER NOT NULL DEFAULT 0,"
                    + COL_ABIS_IN_APK + " TEXT NOT NULL DEFAULT '',"
                    + COL_INSTALLDATE + " INTEGER NOT NULL DEFAULT 0,"
                    + COL_LASTUPDATE + " INTEGER NOT NULL DEFAULT 0,"
                    + "CONSTRAINT  " + COL_PACKAGENAME + "_UNIQUE UNIQUE (" + COL_PACKAGENAME + ") ON CONFLICT REPLACE"
                    + ")";

    public long id = -1;
    @NonNull
    public App app = new App();

    public ApplicationEntry() {
    }

    /**
     * Convert information from the database into an ApplicationEntry object.
     */
    public ApplicationEntry(@NonNull final Cursor cursor) {
        id = cursor.getLong(0);
        app.packagename = cursor.getString(1);
        app.versionName = cursor.getString(2);
        app.versionCode = cursor.getLong(3);

        final String apkLocationsString = cursor.getString(4);
        if (apkLocationsString != null && apkLocationsString.length() > 0)
            app.apkLocations = new HashSet<>(Arrays.asList(apkLocationsString.split(":")));

        app.appname = cursor.getString(5);
        app.pngIcon = cursor.getBlob(6);
        app.type = cursor.getInt(7);

        final String abisString = cursor.getString(8);
        if (abisString != null && abisString.length() > 0)
            app.abis_in_apk = new HashSet<>(Arrays.asList(abisString.split(":")));

        app.installdate.setTime(cursor.getLong(9));
        app.lastupdate.setTime(cursor.getLong(10));
    }

    /**
     * Return the fields in a ContentValues object, suitable for insertion
     * into the database.
     */
    @NonNull
    public ContentValues getContent() {
        final ContentValues values = new ContentValues();
        // Note that ID is NOT included here
        values.put(COL_PACKAGENAME, app.packagename);
        values.put(COL_VERSIONNAME, app.versionName);
        values.put(COL_VERSIONCODE, app.versionCode);
        values.put(COL_APKLOCATIONS, Arrays.toString(app.apkLocations.toArray()).replace(", ", ":").replaceAll("[\\[\\]]", ""));
        values.put(COL_APPNAME, app.appname);
        values.put(COL_PNGICON, app.pngIcon);
        values.put(COL_APPLICATIONTYPE, app.type);
        values.put(COL_ABIS_IN_APK, Arrays.toString(app.abis_in_apk.toArray()).replace(", ", ":").replaceAll("[\\[\\]]", ""));
        values.put(COL_INSTALLDATE, app.installdate.getTime());
        values.put(COL_LASTUPDATE, app.lastupdate.getTime());

        return values;
    }
}
