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

package com.xh.nativelibsmonitor.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Pair;

import com.xh.nativelibsmonitor.database.DatabaseHandler;
import com.xh.nativelibsmonitor.lib.ApplicationType;

import java.util.HashSet;
import java.util.Set;

public class AppsModificationsReceiver extends BroadcastReceiver {

    private static final String KEY_NATIVELIBS_STATUS_NOTIFICATION_GROUP = "app_nativelibs_status";

    public AppsModificationsReceiver() {
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        Uri data = intent.getData();
        assert data != null;

        String action = intent.getAction();
        assert action != null;


        boolean updating = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            final String packageName = data.getSchemeSpecificPart();
            assert packageName != null;

            if (!BuildConfig.APPLICATION_ID.equals(packageName)) { // ignore itself
                final DatabaseHandler dbHandler = DatabaseHandler.getInstance(context);
                long appId = dbHandler.getApplicationId(packageName);

                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel((int) appId);

                if (!updating) {
                    dbHandler.removeApp(appId);
                }
            }
        } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);

            PackageManager pm = context.getPackageManager();
            assert pm != null;

            String[] packageNames = pm.getPackagesForUid(uid);
            if (packageNames != null) {
                final DatabaseHandler dbHandler = DatabaseHandler.getInstance(context);

                for (final String packageName : packageNames) {
                    addInstalledApp(context, packageName, dbHandler, updating);
                }
            }
        }

    }

    private void addInstalledApp(@NonNull Context context, String packageName, @NonNull DatabaseHandler dbHandler, boolean updating) {
        long appId;
        if (updating) {
            int prevAppType = dbHandler.getApplicationType(packageName);
            Set<String> abisInPrevApk = dbHandler.getApplicationABIsInAPK(packageName);

            long formerAppId = dbHandler.getApplicationId(packageName);

            if (formerAppId != -1) {
                dbHandler.updateApp(formerAppId);
                appId = formerAppId;
            } else {
                appId = dbHandler.insertApp(packageName);
            }

            if (appId != -1) {
                Pair<String, Integer> newApp = dbHandler.getApplicationNameAndType(appId);
                Set<String> abisInNewApk = dbHandler.getApplicationABIsInAPK(appId);

                if (newApp != null && (newApp.second != prevAppType || !abisInNewApk.equals(abisInPrevApk))) { // app type has changed, we show a notification
                    showUpgradeWithChangeNotification(context, appId, newApp.first, prevAppType, newApp.second, abisInPrevApk, abisInNewApk);
                }
            }
        } else {
            appId = dbHandler.insertApp(packageName);
            if (appId != -1) {
                Pair<String, Integer> app = dbHandler.getApplicationNameAndType(appId);
                Set<String> abisInApk = dbHandler.getApplicationABIsInAPK(appId);
                if (app != null) {
                    showInstallNotification(context, appId, app.first, app.second, abisInApk);
                }
            }
        }
    }

    private void showUpgradeWithChangeNotification(@NonNull Context ctx, long appId, @NonNull String appName, int prevAppType, int newAppType, @NonNull Set<String> abisInPrevAPK, @NonNull Set<String> abisInNewAPK) {
        final String contentTitle = String.format(ctx.getString(R.string.app_has_been_updated), appName);

        String message;
        switch (prevAppType) {
            case ApplicationType.NO_NATIVE_LIBS_INSTALLED:
                message = ctx.getString(R.string.was_app_type_no_libs);
                break;
            case ApplicationType.MIX_OF_NATIVE_LIBS_INSTALLED:
                message = ctx.getString(R.string.was_app_type_mix);
                break;
            case ApplicationType.X86_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.was_app_type_x86);
                break;
            case ApplicationType.X86_64_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.was_app_type_x86_64);
                break;
            case ApplicationType.ARM_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.was_app_type_arm);
                break;
            case ApplicationType.ARM_64_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.was_app_type_arm64);
                break;
            case ApplicationType.MIPS_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.was_app_type_mips);
                break;
            case ApplicationType.MIPS_64_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.was_app_type_mips64);
                break;
            case ApplicationType.UNKNOWN:
                message = ctx.getString(R.string.was_app_type_unknown);
                break;
            default:
                message = "";
        }

        switch (newAppType) {
            case ApplicationType.MIX_OF_NATIVE_LIBS_INSTALLED:
                message += ctx.getString(R.string.now_app_type_mix);
                break;
            case ApplicationType.X86_NATIVE_LIBS_ONLY_INSTALLED:
                message += ctx.getString(R.string.now_app_type_x86);
                break;
            case ApplicationType.X86_64_NATIVE_LIBS_ONLY_INSTALLED:
                message += ctx.getString(R.string.now_app_type_x86_64);
                break;
            case ApplicationType.ARM_NATIVE_LIBS_ONLY_INSTALLED:
                message += ctx.getString(R.string.now_app_type_arm);
                break;
            case ApplicationType.NO_NATIVE_LIBS_INSTALLED:
                message += ctx.getString(R.string.now_app_type_no_libs);
                break;
            case ApplicationType.UNKNOWN:
                message += ctx.getString(R.string.now_app_type_unknown);
                break;
            case ApplicationType.ARM_64_NATIVE_LIBS_ONLY_INSTALLED:
                message += ctx.getString(R.string.now_app_type_arm64);
                break;
            case ApplicationType.MIPS_NATIVE_LIBS_ONLY_INSTALLED:
                message += ctx.getString(R.string.now_app_type_mips);
                break;
            case ApplicationType.MIPS_64_NATIVE_LIBS_ONLY_INSTALLED:
                message += ctx.getString(R.string.now_app_type_mips64);
                break;
        }

        if (!abisInPrevAPK.equals(abisInNewAPK)) {

            Set<String> newlySupportedABIs = new HashSet<>();
            for (final String newlySupportedABI : abisInPrevAPK) {
                if (!abisInPrevAPK.contains(newlySupportedABI)) {
                    newlySupportedABIs.add(newlySupportedABI);
                }
            }

            Set<String> notSupportedAnymoreABIs = new HashSet<>();
            for (final String previouslySupportedABI : abisInPrevAPK) {
                if (!abisInNewAPK.contains(previouslySupportedABI)) {
                    notSupportedAnymoreABIs.add(previouslySupportedABI);
                }
            }

            if (newlySupportedABIs.size() > 0) {
                message += "\n" + ctx.getString(R.string.new_abis_in_apk) + newlySupportedABIs.toString().replaceAll("[\\[\\]]", "") + ".";
            }

            if (notSupportedAnymoreABIs.size() > 0) {
                message += "\n" + ctx.getString(R.string.abis_not_in_apk_anymore) + notSupportedAnymoreABIs.toString().replaceAll("[\\[\\]]", "") + ".";
            }

        }

        if (abisInNewAPK.size() > 0)
            message += "\n" + ctx.getString(R.string.abis_inside_apk) + abisInNewAPK.toString().replaceAll("[\\[\\]]", "") + ".";

        showNotification(ctx, appId, contentTitle, message);
    }


    private void showInstallNotification(@NonNull Context ctx, long appId, @NonNull String appName, int appType, @NonNull Set<String> abisInAPK) {

        final String contentTitle = String.format(ctx.getString(R.string.app_has_been_installed), appName);

        String message;
        switch (appType) {
            case ApplicationType.MIX_OF_NATIVE_LIBS_INSTALLED:
                message = ctx.getString(R.string.app_type_mix_desc);
                break;
            case ApplicationType.X86_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.app_type_x86_desc);
                break;
            case ApplicationType.X86_64_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.app_type_x86_64_desc);
                break;
            case ApplicationType.ARM_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.app_type_arm_desc);
                break;
            case ApplicationType.NO_NATIVE_LIBS_INSTALLED:
                message = ctx.getString(R.string.app_type_no_libs_desc);
                break;
            case ApplicationType.UNKNOWN:
                message = ctx.getString(R.string.app_type_unknown_desc);
                break;
            case ApplicationType.ARM_64_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.app_type_arm64_desc);
                break;
            case ApplicationType.MIPS_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.app_type_mips_desc);
                break;
            case ApplicationType.MIPS_64_NATIVE_LIBS_ONLY_INSTALLED:
                message = ctx.getString(R.string.app_type_mips64_desc);
                break;
            default:
                message = "";
        }

        if (abisInAPK.size() > 0)
            message += "\n" + ctx.getString(R.string.abis_inside_apk) + abisInAPK.toString().replaceAll("[\\[\\]]", "") + ".";

        showNotification(ctx, appId, contentTitle, message);
    }

    private void showNotification(@NonNull Context context, long appId, @NonNull String contentTitle, @NonNull String message) {
        Intent detailIntent = new Intent(context, MainActivity.class);
        detailIntent.putExtra(AppDetailFragment.ARG_ITEM_ID, appId);
        detailIntent.setAction(MainActivity.ACTION_SHOW_APP_DETAILS + "_" + appId);
        detailIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent detailPendingIntent =
                PendingIntent.getActivity(
                        context,
                        0,
                        detailIntent,
                        PendingIntent.FLAG_ONE_SHOT
                );

        String CHANNEL_ID = notificationChannelId(context);

        // Create a WearableExtender to add functionality for wearables
        NotificationCompat.WearableExtender wearableExtender =
                new NotificationCompat.WearableExtender()
                        .clearActions();

        NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle();
        bigStyle.bigText(message);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(contentTitle)
                .setContentText(message)
                .setContentIntent(detailPendingIntent)
                .setAutoCancel(true)
                .setStyle(bigStyle)
                .extend(wearableExtender)
                .build();
//        .setGroup(KEY_NATIVELIBS_STATUS_NOTIFICATION_GROUP)

        NotificationManagerCompat.from(context).notify((int) appId, notification);

    }

    private static String notificationChannelId(Context context) {

        // Starting from Android O (API 26) Notification Channels are required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            String id = "NLM CHANNEL ID";
            CharSequence name = "Native_Libs_Monitor";
            int imp = NotificationManager.IMPORTANCE_DEFAULT;
            String desc = "Native_Libs_Monitor Notification";

            NotificationChannel notificationChannel = new NotificationChannel(id, name,imp);
            notificationChannel.setDescription(desc);

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(notificationChannel);

            return id;
        } else {
            return null;
        }
    }


}