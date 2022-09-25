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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MenuItem;
import android.view.Window;

/**
 * An activity representing a single ApplicationEntry detail screen. This
 * activity is only used on handset devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link MainActivity}.
 * <p/>
 * This activity is mostly just a 'shell' activity containing nothing
 * more than a {@link com.xh.nativelibsmonitor.app.AppDetailFragment}.
 */
public class TvAppDetailActivity extends Activity {

    private static final String TAG_APP_ID = "app_id";
    private long m_appId = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_activity_app_detail);

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //

        m_appId = getIntent().getLongExtra(AppDetailFragment.ARG_ITEM_ID, m_appId);

        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.

            AppDetailFragment fragment = getAppDetailFragment(m_appId);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.setCustomAnimations(R.animator.app_fragment_in, R.animator.app_fragment_out);
            ft.add(R.id.app_detail_container, fragment);
            ft.commit();
        } else {

            if (m_appId == -1)
                m_appId = savedInstanceState.getLong(TAG_APP_ID, -1);

            AppDetailFragment fragment = getAppDetailFragment(m_appId);
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.setCustomAnimations(R.animator.app_fragment_in, R.animator.app_fragment_out);
            ft.replace(R.id.app_detail_container, fragment);
            ft.commit();
        }

    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        m_appId = savedInstanceState.getLong(TAG_APP_ID, m_appId);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(TAG_APP_ID, m_appId);
    }

    @NonNull
    private AppDetailFragment getAppDetailFragment(long applicationId) {
        Bundle arguments = new Bundle();
        arguments.putLong(AppDetailFragment.ARG_ITEM_ID, applicationId);
        AppDetailFragment fragment = new AppDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        long appId = intent.getLongExtra(AppDetailFragment.ARG_ITEM_ID, -1);

        if (appId != -1) {
            AppDetailFragment fragment = getAppDetailFragment(appId);
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.setCustomAnimations(R.animator.app_fragment_in, R.animator.app_fragment_out);
            ft.replace(R.id.app_detail_container, fragment);
            ft.commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            navigateUpTo(new Intent(this, MainActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
