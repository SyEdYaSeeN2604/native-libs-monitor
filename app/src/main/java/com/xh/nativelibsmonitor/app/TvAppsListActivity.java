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

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Window;

//import com.crashlytics.android.Crashlytics;


/**
 * An activity representing a list of Apps. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link com.xh.nativelibsmonitor.app.AppDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 * <p/>
 * The activity makes heavy use of fragments. The list of items is a
 * {@link com.xh.nativelibsmonitor.app.AppsListFragment} and the item details
 * (if present) is a {@link com.xh.nativelibsmonitor.app.AppDetailFragment}.
 * <p/>
 * This activity also implements the required
 * {@link com.xh.nativelibsmonitor.app.AppsListFragment.Callbacks} interface
 * to listen for item selections.
 */
public class TvAppsListActivity extends Activity
        implements AppsListFragment.Callbacks {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                            .detectAll()
                            .penaltyLog()
                            .build()
            );
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                            .detectLeakedSqlLiteObjects()
                            .detectLeakedClosableObjects()
                            .detectLeakedRegistrationObjects()
                                    // .detectActivityLeaks()
                            .penaltyLog()
                                    // .penaltyDeath()
                            .build()
            );
        }

        super.onCreate(savedInstanceState);
        //Crashlytics.start(this);

        setContentView(R.layout.tv_activity_main);

        if (findViewById(R.id.app_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-large and
            // res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;

            if (savedInstanceState == null) {
                // In two-pane mode, list items should be given the
                // 'activated' state when touched.
                final AppsListFragment appsListFragment = ((AppsListFragment) getFragmentManager()
                        .findFragmentById(R.id.app_list));

                if (appsListFragment != null)
                    appsListFragment.activateOnItemClick();
            }
        }

        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);

        long id = intent.getLongExtra(AppDetailFragment.ARG_ITEM_ID, -1);
        if (id != -1)
            onItemSelected(id);

        setIntent(null);
    }

    /**
     * Callback method from {@link com.xh.nativelibsmonitor.app.AppsListFragment.Callbacks}
     * indicating that the item with the given ID was selected.
     */
    @Override
    public void onItemSelected(long id) {
        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            Bundle arguments = new Bundle();
            arguments.putLong(AppDetailFragment.ARG_ITEM_ID, id);
            AppDetailFragment fragment = new AppDetailFragment();
            fragment.setArguments(arguments);
            //fragment.setRetainInstance(true); //arguments never move when instance is retained...

            FragmentTransaction ft = getFragmentManager().beginTransaction();

            ft.setCustomAnimations(R.animator.app_fragment_twopane_in, R.animator.app_fragment_twopane_out);

            ft.replace(R.id.app_detail_container, fragment);
            ft.commit();

        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, TvAppDetailActivity.class);
            detailIntent.putExtra(AppDetailFragment.ARG_ITEM_ID, id);
            startActivity(detailIntent);
            overridePendingTransition(0, 0); //disable animation, it will be handled by the launched activity
        }
    }
}
