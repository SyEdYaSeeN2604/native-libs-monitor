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

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.xh.nativelibsmonitor.lib.ApplicationType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class AppProvider extends ContentProvider {
    // All URIs share these parts
    public static final String AUTHORITY = "com.xh.nativelibsmonitor.provider";
    public static final String SCHEME = "content://";

    // URIs
    public static final String APPS = SCHEME + AUTHORITY + "/app";
    public static final Uri URI_APPS = Uri.parse(APPS);
    public static final Uri URI_APPS_FILTERED = Uri.parse(APPS + "_filtered/");
    // Used for a single app, just add the id to the end
    public static final String APP_BASE = APPS + "/";

    public static final String DATABASE_FILENAME = "apps_list.csv";
    public static final Uri URI_DATABASE = Uri.parse(SCHEME + AUTHORITY + "/" + DATABASE_FILENAME);

    public AppProvider() {
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        // Implement this to handle requests to delete one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {

        if (URI_APPS.equals(uri))
            return "vnd.android.cursor.item/vnd.com.xh.nativelibsmonitor.application_entry_item";
        else if (uri.toString().startsWith(APP_BASE))
            return "vnd.android.cursor.dir/vnd.com.xh.nativelibsmonitor.application_entry_item";
        else if (URI_DATABASE.equals(uri))
            return "vnd.android.cursor.item/text/csv";
        else
            return null;
    }

    @Nullable
    @Override
    public String[] getStreamTypes(@NonNull Uri uri, @NonNull String mimeTypeFilter) {
        if (URI_DATABASE.equals(uri))
            return new String[]{"text/csv"};
        else
            return super.getStreamTypes(uri, mimeTypeFilter);
    }

    @NonNull
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Cursor result;
        if (URI_APPS.equals(uri)) {
            Context ctx = getContext();
            assert ctx != null;

            SQLiteDatabase db = DatabaseHandler
                    .getInstance(ctx)
                    .getReadableDatabase();
            assert db != null;

            result = db.query(ApplicationEntry.TABLE_NAME, ApplicationEntry.FIELDS, selection, selectionArgs, null,
                    null, sortOrder, null);

            result.setNotificationUri(ctx.getContentResolver(), URI_APPS);
        } else if (uri.toString().startsWith(APP_BASE)) {
            final long id = Long.parseLong(uri.getLastPathSegment());

            Context ctx = getContext();
            assert ctx != null;

            SQLiteDatabase db = DatabaseHandler
                    .getInstance(ctx)
                    .getReadableDatabase();
            assert db != null;

            result = db.query(ApplicationEntry.TABLE_NAME, ApplicationEntry.FIELDS,
                    ApplicationEntry.COL_ID + " IS ?",
                    new String[]{String.valueOf(id)}, null, null,
                    sortOrder, null);
            result.setNotificationUri(ctx.getContentResolver(), URI_APPS);
        } else if (uri.toString().startsWith(URI_APPS_FILTERED.toString())) { //filter list
            final String query = uri.getLastPathSegment();
            Context ctx = getContext();
            assert ctx != null;

            SQLiteDatabase db = DatabaseHandler
                    .getInstance(ctx)
                    .getReadableDatabase();
            assert db != null;

            if (query.length() > 0) {
                result = db.query(ApplicationEntry.TABLE_NAME, ApplicationEntry.FIELDS,
                        ApplicationEntry.COL_APPNAME + " LIKE ? OR " + ApplicationEntry.COL_PACKAGENAME + " LIKE ?",
                        new String[]{"%" + query + "%", "%" + query + "%"}, null, null,
                        sortOrder, null);
            } else {
                result = db.query(ApplicationEntry.TABLE_NAME, ApplicationEntry.FIELDS, selection, selectionArgs, null,
                        null, sortOrder, null);
            }

            result.setNotificationUri(ctx.getContentResolver(), uri);
        } else if (URI_DATABASE.equals(uri)) {
            result = new MyFakeCursor();
        } else {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        return result;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context ctx = getContext();
        assert ctx != null;

        File appsListCSVFile = new File(ctx.getCacheDir(), DATABASE_FILENAME);
        try (FileWriter fw = new FileWriter(appsListCSVFile);) {
            writeAppsListToCSV(fw);
        } catch (IOException e) {
            throw new FileNotFoundException("couldn't create file" + e.getMessage());
        }

        return ParcelFileDescriptor.open(appsListCSVFile, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private void writeAppsListToCSV(FileWriter fw) throws IOException {
        SimpleDateFormat dateFormater = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Context ctx = getContext();
        assert ctx != null;
        DatabaseHandler dbHandler = DatabaseHandler.getInstance(ctx);

        fw.append("App Name");
        fw.append(',');

        fw.append("Package Name");
        fw.append(',');

        fw.append("Type");
        fw.append(',');

        fw.append("Type of libs in APK");
        fw.append(',');

        fw.append("Well-Known Frameworks/Libs");
        fw.append(',');

        fw.append("Version Code");
        fw.append(',');

        fw.append("Version Name");
        fw.append(',');

        fw.append("Last update");
        fw.append('\n');

        try (Cursor cursor = query(URI_APPS, null, null, null, ApplicationEntry.COL_APPNAME + " COLLATE LOCALIZED ASC");) {
            if (cursor.moveToFirst()) {
                do {
                    ApplicationEntry appEntry = new ApplicationEntry(cursor);
                    fw.append(appEntry.app.appname);
                    fw.append(',');
                    fw.append(appEntry.app.packagename);
                    fw.append(',');
                    fw.append(ApplicationType.getApplicationTypeLocalizedTitle(appEntry.app.type, ctx));
                    fw.append(',');
                    fw.append(appEntry.app.abis_in_apk.toString().replace(",", ";").replaceAll("[\\[\\]]", ""));
                    fw.append(',');
                    fw.append(dbHandler.getApplicationUsedFrameworks(appEntry.id).toString().replace(",", ";").replaceAll("[\\[\\]]", ""));
                    fw.append(',');
                    fw.append(Long.toString(appEntry.app.versionCode));
                    fw.append(',');
                    fw.append(appEntry.app.versionName);
                    fw.append(',');
                    fw.append(dateFormater.format(appEntry.app.lastupdate));
                    fw.append('\n');
                } while (cursor.moveToNext());
            }
        }
        return;
    }

    private static class MyFakeCursor implements Cursor {

        @Override
        public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {

        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public int getPosition() {
            return 0;
        }

        @Override
        public boolean move(int offset) {
            return false;
        }

        @Override
        public boolean moveToPosition(int position) {
            return false;
        }

        @Override
        public boolean moveToFirst() {
            return true;
        }

        @Override
        public boolean moveToLast() {
            return true;
        }

        @Override
        public boolean moveToNext() {
            return false;
        }

        @Override
        public boolean moveToPrevious() {
            return false;
        }

        @Override
        public boolean isFirst() {
            return true;
        }

        @Override
        public boolean isLast() {
            return true;
        }

        @Override
        public boolean isBeforeFirst() {
            return false;
        }

        @Override
        public boolean isAfterLast() {
            return false;
        }

        @Override
        public int getColumnIndex(String columnName) {
            return 0;
        }

        @Override
        public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
            return 0;
        }

        @Nullable
        @Override
        public String getColumnName(int columnIndex) {
            return null;
        }

        @NonNull
        @Override
        public String[] getColumnNames() {
            return new String[0];
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @NonNull
        @Override
        public byte[] getBlob(int columnIndex) {
            return new byte[0];
        }

        @Nullable
        @Override
        public String getString(int columnIndex) {
            return "apps_data.csv";
        }

        @Override
        public short getShort(int columnIndex) {
            return 0;
        }

        @Override
        public int getInt(int columnIndex) {
            return 0;
        }

        @Override
        public long getLong(int columnIndex) {
            return 0;
        }

        @Override
        public float getFloat(int columnIndex) {
            return 0;
        }

        @Override
        public double getDouble(int columnIndex) {
            return 0;
        }

        @Override
        public int getType(int columnIndex) {
            return 0;
        }

        @Override
        public boolean isNull(int columnIndex) {
            return false;
        }

        @Override
        public void setExtras(Bundle extras) {

        }

        @Override
        @Deprecated
        public void deactivate() {
        }

        @Override
        @Deprecated
        public boolean requery() {
            return false;
        }

        @Override
        public void close() {

        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void registerContentObserver(ContentObserver observer) {

        }

        @Override
        public void unregisterContentObserver(ContentObserver observer) {

        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {

        }

        @Override
        public void setNotificationUri(ContentResolver cr, Uri uri) {

        }

        @Override
        public Uri getNotificationUri() {
            return null;
        }

        @Override
        public boolean getWantsAllOnMoveCalls() {
            return false;
        }

        @Override
        public Bundle getExtras() {
            return null;
        }

        @Override
        public Bundle respond(Bundle extras) {
            return null;
        }

    }
}