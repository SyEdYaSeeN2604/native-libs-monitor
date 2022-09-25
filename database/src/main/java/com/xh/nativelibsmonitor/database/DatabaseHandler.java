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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;

import com.xh.nativelibsmonitor.lib.AppAnalyzer;
import com.xh.nativelibsmonitor.lib.ApplicationType;
import com.xh.nativelibsmonitor.lib.NativeLibrary;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DatabaseHandler extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 11;
    private static final String DATABASE_NAME = "applications";
    private static DatabaseHandler singleton;
    @NonNull
    private final Context context;
    private boolean mBeingPopulated = false;
    private volatile int mNumberOfAppInserted = -1;
    private int mTotalNumberOfAppsBeingInserted = -1;

    private synchronized void incrementNumberOfAppsPopulated(){
        ++mNumberOfAppInserted;
    }

    private DatabaseHandler(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        // enforce use of process context here
        Context ctx = context.getApplicationContext();
        assert ctx != null;
        this.context = ctx;
    }

    @NonNull
    public static DatabaseHandler getInstance(@NonNull final Context context) {
        if (singleton == null) {
            singleton = new DatabaseHandler(context);
        }
        return singleton;
    }

    public static boolean isSystemApp(@NonNull ApplicationInfo ai) {
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && !ai.sourceDir.startsWith("/data/");
    }

    private static boolean isLaunchableApp(@NonNull PackageManager pm, @NonNull ApplicationInfo ai) {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(ai.packageName);

        if(pm.queryIntentActivities(mainIntent, 0).size()>0)
            return true;

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
            final Intent mainTVIntent = new Intent(Intent.ACTION_MAIN, null);
            mainTVIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            mainTVIntent.setPackage(ai.packageName);

            if(pm.queryIntentActivities(mainTVIntent, 0).size()>0)
                return true;
        }

        return false;
    }

    public synchronized boolean isNotBeingPopulated() {
        return !mBeingPopulated;
    }

    @Nullable
    public synchronized Pair<Integer,Integer> getCurrentPopulationState(){
        if(!mBeingPopulated){
            return null;
        }
        else {
            return new Pair<>(mNumberOfAppInserted, mTotalNumberOfAppsBeingInserted);
        }
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        createTables(db);
        startInsertingAllApps();
    }

    private synchronized void createTables(@NonNull SQLiteDatabase db) {
        db.execSQL(ApplicationEntry.CREATE_TABLE);
        db.execSQL(NativeLibraryEntry.CREATE_TABLE);
    }

    private synchronized void startInsertingAllApps() {
        //  Debug.startMethodTracing("AppsInsert");
        if (mBeingPopulated)
            return;

        mBeingPopulated = true;
        mNumberOfAppInserted = 0;
        mTotalNumberOfAppsBeingInserted = 0;

        Thread bgThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final PackageManager pm = context.getPackageManager();
                assert pm != null;

                //List<ApplicationInfo> applicationsInfo = pm.getInstalledApplications(0);
                final Set<String> packageNames = new HashSet<>();

                addLaunchableAppsForCategory(pm, Intent.CATEGORY_LAUNCHER, packageNames);

                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP) // add TV apps
                    addLaunchableAppsForCategory(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, packageNames);

                mTotalNumberOfAppsBeingInserted = packageNames.size();
                notifyProviderOnAppChange();

                final ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                for (final String packageName : packageNames) {
                    try {
                        final ApplicationInfo ai;

                        ai = pm.getApplicationInfo(packageName, 0);

                        exec.execute(new Runnable() {
                            @Override
                            public void run() {
                                insertApp(pm, ai);
                                incrementNumberOfAppsPopulated();
                                if(mNumberOfAppInserted%16==5) notifyProviderOnAppChange(); //notify change every 20 inserted apps at most.
                            }
                        });
                    } catch (PackageManager.NameNotFoundException ignore) {

                    }
                }
                exec.shutdown();

                try {
                    // Wait a while for existing tasks to terminate
                    if (!exec.awaitTermination(180, TimeUnit.SECONDS))
                        exec.shutdownNow(); // Cancel currently executing tasks
                } catch (InterruptedException ie) {
                    // (Re-)Cancel if current thread also interrupted
                    exec.shutdownNow();
                    // Preserve interrupt status
                    Thread.currentThread().interrupt();
                } finally {
                    mBeingPopulated = false;
                    notifyProviderOnAppChange();
                    //  Debug.stopMethodTracing();
                }
            }
        });
        bgThread.setPriority(Thread.MIN_PRIORITY);
        bgThread.start();
    }

    private void addLaunchableAppsForCategory(PackageManager pm, String intentCategory, Set<String> packageNames) {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(intentCategory);

        final List<ResolveInfo> pkgAppsList = pm.queryIntentActivities(mainIntent, 0);

        for (final ResolveInfo ri : pkgAppsList) {
            if (ri.activityInfo != null)
                packageNames.add(ri.activityInfo.packageName);
        }
    }

    private long insertApp(@NonNull final PackageManager pm, @NonNull final ApplicationInfo ai) {
        final ApplicationEntry appEntry = new ApplicationEntry();
        appEntry.app = AppAnalyzer.analyzeApp(ai, pm);
        long appId = insertAppEntry(appEntry);

        final NativeLibraryEntry libEntry = new NativeLibraryEntry();
        libEntry.applicationId = appId;

        for (final NativeLibrary lib : appEntry.app.installedNativeLibs) {
            libEntry.nativeLibrary = lib;
            insertLibraryEntry(libEntry);
        }

        for (final NativeLibrary lib : appEntry.app.packagedNativeLibs) {
            libEntry.nativeLibrary = lib;
            insertLibraryEntry(libEntry);
        }

        if (appId > -1 && !mBeingPopulated) //don't notify provider if we're currently populating DB.
            notifyProviderOnAppChange();

        return appId;
    }

    public boolean updateApp(long appId) {
        final PackageManager pm = context.getPackageManager();
        assert pm != null;

        final ApplicationEntry formerEntry = getApplication(appId);

        if (formerEntry != null) {
            try {
                final ApplicationInfo ai = pm.getApplicationInfo(formerEntry.app.packagename, 0);
                if (ai != null && isLaunchableApp(pm, ai))
                    return updateApp(pm, ai, appId);
            } catch (PackageManager.NameNotFoundException ignore) {
                //TODO: handle exception
            }
        }

        return false;
    }

    private boolean updateApp(@NonNull final PackageManager pm, @NonNull final ApplicationInfo ai, long appId) {
        final ApplicationEntry appEntry = new ApplicationEntry();
        appEntry.app = AppAnalyzer.analyzeApp(ai, pm);

        if (updateAppEntry(appEntry) == 1) {

            final NativeLibraryEntry libEntry = new NativeLibraryEntry();
            libEntry.applicationId = appId;

            deleteLibraryEntries(appId);

            for (final NativeLibrary lib : appEntry.app.installedNativeLibs) {
                libEntry.nativeLibrary = lib;
                insertLibraryEntry(libEntry);
            }

            for (final NativeLibrary lib : appEntry.app.packagedNativeLibs) {
                libEntry.nativeLibrary = lib;
                insertLibraryEntry(libEntry);
            }

            notifyProviderOnAppChange();

            return true;
        } else
            return false;
    }


    private synchronized void deleteLibraryEntries(long appId) {
        final SQLiteDatabase db = this.getWritableDatabase();
        assert db != null;
        db.delete(NativeLibraryEntry.TABLE_NAME,
                NativeLibraryEntry.COL_APPLICATIONID + " IS ?",
                new String[]{String.valueOf(appId)});
    }

    private synchronized void insertLibraryEntry(@NonNull final NativeLibraryEntry libEntry) {
        final SQLiteDatabase db = this.getWritableDatabase();
        assert db != null;
        db.insert(NativeLibraryEntry.TABLE_NAME, null, libEntry.getContent());
    }

    private synchronized long insertAppEntry(@NonNull ApplicationEntry appEntry) {
        final SQLiteDatabase db = this.getWritableDatabase();
        assert db != null;
        return db.insertWithOnConflict(ApplicationEntry.TABLE_NAME, null, appEntry.getContent(), SQLiteDatabase.CONFLICT_REPLACE);
    }

    private synchronized long updateAppEntry(@NonNull ApplicationEntry appEntry) {
        final SQLiteDatabase db = this.getWritableDatabase();
        assert db != null;
        return db.update(ApplicationEntry.TABLE_NAME,
                appEntry.getContent(),
                ApplicationEntry.COL_PACKAGENAME + " IS ? ",
                new String[]{appEntry.app.packagename});
    }

    public long insertApp(final String packageName) {
        final PackageManager pm = context.getPackageManager();
        assert pm != null;

        try {
            final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.setPackage(packageName);

            if (pm.queryIntentActivities(mainIntent, 0).size() == 0) // app doesn't have any launcher activity
                return -1;

            final ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            if (ai != null && isLaunchableApp(pm, ai))
                return insertApp(pm, ai);

        } catch (PackageManager.NameNotFoundException ignore) {
            //TODO: handle exception
        }

        return -1;
    }

    public synchronized boolean removeApp(String packageName) {
        final SQLiteDatabase db = this.getWritableDatabase();
        assert db != null;

        final int result = db.delete(ApplicationEntry.TABLE_NAME,
                ApplicationEntry.COL_PACKAGENAME + " IS ?",
                new String[]{packageName});

        if (result > 0) {
            notifyProviderOnAppChange();
        } else
            return false;

        return true;
    }

    public synchronized boolean removeApp(long appId) {
        final SQLiteDatabase db = this.getWritableDatabase();
        assert db != null;

        final int result = db.delete(ApplicationEntry.TABLE_NAME,
                ApplicationEntry.COL_ID + " IS ?",
                new String[]{String.valueOf(appId)});

        if (result > 0) {
            notifyProviderOnAppChange();
        } else
            return false;

        return true;
    }

    public synchronized void refreshDatabase() {
        final SQLiteDatabase db = this.getWritableDatabase();
        assert db != null;
        deleteTables(db);
        createTables(db);
        startInsertingAllApps();
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        deleteTables(db);
        createTables(db);
        startInsertingAllApps();
    }

    private synchronized void deleteTables(@NonNull SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + ApplicationEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + NativeLibraryEntry.TABLE_NAME);
    }

    public synchronized int getApplicationType(final String packageName) {
        int result = ApplicationType.UNKNOWN;

        final SQLiteDatabase db = this.getReadableDatabase();
        assert db != null;

        final Cursor cursor = db.query(ApplicationEntry.TABLE_NAME,
                new String[]{ApplicationEntry.COL_APPLICATIONTYPE}, ApplicationEntry.COL_PACKAGENAME + " IS ?",
                new String[]{packageName}, null, null, null, null);

        if (cursor != null) {
            if (!cursor.isAfterLast() && cursor.moveToFirst()) {
                result = cursor.getInt(0);
            }
            cursor.close();
        }

        return result;
    }

    @Nullable
    public synchronized Pair<String, Integer> getApplicationNameAndType(long id) {
        Pair<String, Integer> result = null;

        final SQLiteDatabase db = this.getReadableDatabase();
        assert db != null;

        final Cursor cursor = db.query(ApplicationEntry.TABLE_NAME,
                new String[]{ApplicationEntry.COL_APPNAME, ApplicationEntry.COL_APPLICATIONTYPE}, ApplicationEntry.COL_ID + " IS ?",
                new String[]{String.valueOf(id)}, null, null, null, null);

        if (cursor != null) {
            if  (!cursor.isAfterLast() && cursor.moveToFirst()) {
                result = Pair.create(cursor.getString(0), cursor.getInt(1));
            }
            cursor.close();
        }

        return result;
    }

    @NonNull
    public synchronized Set<String> getApplicationABIsInAPK(final String packageName) {
        Set<String> result = null;

        final SQLiteDatabase db = this.getReadableDatabase();
        assert db != null;

        final Cursor cursor = db.query(ApplicationEntry.TABLE_NAME,
                new String[]{ApplicationEntry.COL_ABIS_IN_APK}, ApplicationEntry.COL_PACKAGENAME + " IS ?",
                new String[]{packageName}, null, null, null, null);

        if (cursor != null) {
            if (!cursor.isAfterLast() && cursor.moveToFirst()) {
                final String abisString = cursor.getString(0);
                if (abisString != null && abisString.length() > 0)
                    result = new HashSet<>(Arrays.asList(abisString.split(":")));

            }
            cursor.close();
        }

        return (result != null) ? result : new HashSet<String>();
    }

    @NonNull
    public synchronized Set<String> getApplicationABIsInAPK(final long id) {
        Set<String> result = null;

        final SQLiteDatabase db = this.getReadableDatabase();
        assert db != null;

        final Cursor cursor = db.query(ApplicationEntry.TABLE_NAME,
                new String[]{ApplicationEntry.COL_ABIS_IN_APK}, ApplicationEntry.COL_ID + " IS ?",
                new String[]{String.valueOf(id)}, null, null, null, null);

        if (cursor != null) {
            if (!cursor.isAfterLast() && cursor.moveToFirst()) {
                final String abisString = cursor.getString(0);
                if (abisString != null && abisString.length() > 0)
                    result = new HashSet<>(Arrays.asList(abisString.split(":")));

            }
            cursor.close();
        }

        return (result != null) ? result : new HashSet<String>();
    }

    @Nullable
    public synchronized ApplicationEntry getApplication(final long id) {
        final SQLiteDatabase db = this.getReadableDatabase();
        assert db != null;
        final Cursor cursor = db.query(ApplicationEntry.TABLE_NAME,
                ApplicationEntry.FIELDS, ApplicationEntry.COL_ID + " IS ?",
                new String[]{String.valueOf(id)}, null, null, null, null);
        if (cursor == null) {
            return null;
        }
        if (cursor.isAfterLast()) {
            cursor.close();
            return null;
        }

        ApplicationEntry item = null;
        if (cursor.moveToFirst()) {
            item = new ApplicationEntry(cursor);
        }
        cursor.close();

        if (item != null) {
            final Cursor cursor_libs = db.query(NativeLibraryEntry.TABLE_NAME,
                    NativeLibraryEntry.FIELDS, NativeLibraryEntry.COL_APPLICATIONID + " IS ?",
                    new String[]{String.valueOf(id)}, null, null, NativeLibraryEntry.COL_PATH + " COLLATE LOCALIZED ASC", null);
            if (cursor_libs != null) {
                if (!cursor_libs.isAfterLast()) {
                    while (cursor_libs.moveToNext()) {
                        final NativeLibraryEntry libEntry = new NativeLibraryEntry(cursor_libs);
                        if (libEntry.nativeLibrary.type == NativeLibrary.TYPE.INSTALLED)
                            item.app.installedNativeLibs.add(libEntry.nativeLibrary);
                        else if (libEntry.nativeLibrary.type == NativeLibrary.TYPE.IN_PACKAGE)
                            item.app.packagedNativeLibs.add(libEntry.nativeLibrary);
                    }
                }
                cursor_libs.close();
            }
        }

        return item;
    }

    @NonNull
    public synchronized Set<String> getApplicationUsedFrameworks(final long id) {
        final SQLiteDatabase db = this.getReadableDatabase();
        assert db != null;

        Set<String> frameworks = new HashSet<>();

        final Cursor cursor_libs = db.query(NativeLibraryEntry.TABLE_NAME,
                new String[]{NativeLibraryEntry.COL_FRAMEWORKS}, NativeLibraryEntry.COL_APPLICATIONID + " IS ?",
                new String[]{String.valueOf(id)}, null, null, NativeLibraryEntry.COL_PATH + " COLLATE LOCALIZED ASC", null);
        if (cursor_libs != null) {
            if (!cursor_libs.isAfterLast()) {
                while (cursor_libs.moveToNext()) {
                    String frameworksString = cursor_libs.getString(0);
                    if (frameworksString != null && frameworksString.length() > 0)
                        frameworks.addAll(Arrays.asList(frameworksString.split(":")));
                }
            }
            cursor_libs.close();
        }

        return frameworks;
    }

    private void notifyProviderOnAppChange() {
        context.getContentResolver().notifyChange(
                AppProvider.URI_APPS, null, false);
    }

    public synchronized long getApplicationId(String packageName) {
        long result = -1;

        final SQLiteDatabase db = this.getReadableDatabase();
        assert db != null;

        final Cursor cursor = db.query(ApplicationEntry.TABLE_NAME,
                new String[]{ApplicationEntry.COL_ID}, ApplicationEntry.COL_PACKAGENAME + " IS ?",
                new String[]{packageName}, null, null, null, null);

        if (cursor != null) {
            if (!cursor.isAfterLast() && cursor.moveToFirst()) {
                result = cursor.getLong(0);
            }
            cursor.close();
        }

        return result;
    }
}