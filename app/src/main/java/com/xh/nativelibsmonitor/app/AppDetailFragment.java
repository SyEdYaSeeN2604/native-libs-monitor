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
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.icu.text.UFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.xh.nativelibsmonitor.database.ApplicationEntry;
import com.xh.nativelibsmonitor.database.DatabaseHandler;
import com.xh.nativelibsmonitor.lib.ABI;
import com.xh.nativelibsmonitor.lib.App;
import com.xh.nativelibsmonitor.lib.NativeLibrary;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

//import com.crashlytics.android.Crashlytics;

/**
 * A fragment representing a single ApplicationEntry detail screen.
 * This fragment is either contained in a {@link MainActivity}
 * in two-pane mode (on tablets) or a {@link AppDetailActivity}
 * on handsets.
 */
public class AppDetailFragment extends Fragment {
    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_ITEM_ID = "item_id";

    /**
     * The dummy content this fragment is presenting.
     */
    private ApplicationEntry mItem;
    @Nullable
    private ListAdapter mInstalledNativeLibsAdapter;
    @Nullable
    private ListAdapter mPackagedNativeLibsAdapter;
    private String mFrameworksUsed = "";

    private Intent mShareIntent;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public AppDetailFragment() {
    }

    private static String humanReadableFileSize(long bytes) {
        int unit = 1024;
        if (bytes < unit) return bytes + " B";
        String[] fileSizeUnit = {"KB", "MB", "GB", "TB", "PB", "EB"};
        int index = (int) (Math.log(bytes) / Math.log(unit));
        String show = fileSizeUnit[index - 1];
        final String format = String.format("%.1f %s", bytes / Math.pow(unit, index), show);
        return format;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        long appId = -1;
        Bundle args= getArguments();
        if (args != null ) {
            if (args.containsKey(ARG_ITEM_ID))
                appId = args.getLong(ARG_ITEM_ID);
        } else if (savedInstanceState != null && savedInstanceState.containsKey(ARG_ITEM_ID)) {
            appId = savedInstanceState.getLong(ARG_ITEM_ID);
        }

        Activity activity = getActivity();
        assert activity != null;
//        prepareContent(activity, appId);

        new LoadContentAsyncTask(this).execute(appId);

    }

    protected void prepareContent(@NonNull Activity activity, long appId) {
        mItem = DatabaseHandler.getInstance(activity).getApplication(appId);

        if (mItem == null) {
            mPackagedNativeLibsAdapter = null;
            mInstalledNativeLibsAdapter = null;
            mFrameworksUsed = "";
        } else {

            //Crashlytics.setString("app_package_name", mItem.app.packagename);


            if (mItem.app.packagedNativeLibs.size() > 0) {
                mPackagedNativeLibsAdapter = new NativeLibsAdapter(activity, mItem.app.packagedNativeLibs, activity.getLayoutInflater());
            }

            if (mItem.app.installedNativeLibs.size() > 0) {
                mInstalledNativeLibsAdapter = new NativeLibsAdapter(activity, mItem.app.installedNativeLibs, activity.getLayoutInflater());
            }

                            /* adding frameworks from installed and packaged libs */
            final TreeSet<String> frameworksInApp = new TreeSet<>();
            for (final NativeLibrary lib : mItem.app.installedNativeLibs) {
                frameworksInApp.addAll(lib.frameworks);
            }

            for (final NativeLibrary lib : mItem.app.packagedNativeLibs) {
                frameworksInApp.addAll(lib.frameworks);
            }

            if (frameworksInApp.size() > 0)
                mFrameworksUsed = Arrays.toString(frameworksInApp.toArray()).replaceAll("[\\[\\]]", "");
        }
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mItem != null && mItem.id > 0) {
            outState.putLong(ARG_ITEM_ID, mItem.id);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_app_details, container, false);
        assert rootView != null;

        if (mItem != null)
            populateView(rootView);

        return rootView;
    }

    private void populateView(@NonNull View view) {
        final Activity activity = getActivity();
        final Resources resources = getResources();

        if (mItem == null) {
            view.setAlpha(0);
            activity.setTitle(resources.getString(R.string.app_details_fragment_title_not_loaded));
        } else {
            view.setAlpha(1.0f);
            activity.setTitle(String.format(resources.getString(R.string.app_details_fragment_title), mItem.app.appname));

            TextView textAppName = ((TextView) view.findViewById(R.id.textAppName));
            textAppName.setText(mItem.app.appname);

            Button storeButton = (Button) view.findViewById(R.id.goToPlayStoreButton);
            storeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + mItem.app.packagename)));
                    } catch (ActivityNotFoundException e) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + mItem.app.packagename)));
                        } catch (ActivityNotFoundException ignore) {
                            Toast.makeText(getActivity(), "Play Store isn't available", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

            TextView textPackageName = ((TextView) view.findViewById(R.id.textPackageName));
            textPackageName.setText(mItem.app.packagename);

            TextView textAPKVersionCode = ((TextView) view.findViewById(R.id.textAPKVersion));
            textAPKVersionCode.setText(String.format("%d (%s)", mItem.app.versionCode, mItem.app.versionName ));

            TextView textAPKLocations = ((TextView) view.findViewById(R.id.textApkLocations));
            textAPKLocations.setText(Arrays.toString(mItem.app.apkLocations.toArray()).replaceAll("[\\[\\]]", ""));

            if(mItem.app.apkLocations.size()>1){
                TextView titleAPKLocations = ((TextView) view.findViewById(R.id.titleApkLocation));
                titleAPKLocations.setText(R.string.list_entry_title_apk_locations);
            }

            AdapterView.OnItemClickListener listener = new AdapterView.OnItemClickListener() {
                public void onItemClick(@NonNull AdapterView<?> parent, View view, int position, long id) {
                    NativeLibrary currentItem = (NativeLibrary) parent.getItemAtPosition(position);
                    assert currentItem != null;
                    showAppDialog(currentItem);
                }
            };

            if (mItem.app.installedNativeLibs.size() > 0) {
                ListView installedNativeLibsListView = (ListView) view.findViewById(R.id.installedNativeLibsListView);
                installedNativeLibsListView.setAdapter(mInstalledNativeLibsAdapter);
                installedNativeLibsListView.setOnItemClickListener(listener);
            } else {
                TextView tv = (TextView) view.findViewById(R.id.installedNativeLibsTitle);
                if (mItem.app.packagedNativeLibs.size()>0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    tv.setText(R.string.app_details_fragment_no_native_libs_installed_marshmallow);
                }
                else
                    tv.setText(R.string.app_details_fragment_no_native_libs_installed);
            }

            if (mItem.app.packagedNativeLibs.size() > 0) {
                ListView packagedNativeLibsListView = (ListView) view.findViewById(R.id.packagedNativeLibsListView);
                packagedNativeLibsListView.setAdapter(mPackagedNativeLibsAdapter);
                packagedNativeLibsListView.setOnItemClickListener(listener);
            } else {
                TextView tv = (TextView) view.findViewById(R.id.packagedNativeLibsTitle);
                tv.setText(R.string.app_details_fragment_no_native_libs_inside_apk);
            }

            TextView tvFrameworksUsed = (TextView) view.findViewById(R.id.textFrameworksUsed);
            if (mFrameworksUsed.length() > 0) {
                tvFrameworksUsed.setText(mFrameworksUsed);
            } else {
                tvFrameworksUsed.setVisibility(View.GONE);
                TextView tv = (TextView) view.findViewById(R.id.titleFrameworksUsed);
                tv.setVisibility(View.GONE);
//                tv.setText("No well-known frameworks or libraries found.");
            }
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_app_details_refresh:
                if (mItem != null) {
                    new RefreshAppAsyncTask(mItem.id,
                            AppDetailFragment.this
                    ).execute();
                }
                break;
            case R.id.menu_app_details_share:
                if (mShareIntent != null)
                    startActivity(mShareIntent);
                break;
        }


        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.app_details_menu, menu);
        initShareAction(menu);
    }

    private void initShareAction(Menu menu) {
        MenuItem item = menu.findItem(R.id.menu_app_details_share);
        assert item != null;

        item.setIcon(R.drawable.ic_menu_share);

        mShareIntent = new Intent(Intent.ACTION_SEND);
        mShareIntent.setType("text/plain");
        mShareIntent.putExtra(Intent.EXTRA_TEXT, "content hasn't been loaded, try sharing again in few seconds.");

        if (mItem != null) {
            populateShareActionProvider();
        }
    }

    private void populateShareActionProvider() {
        final Activity activity = getActivity();
        assert activity != null;

        if (mItem != null) {
            new TextReportGenerationTask(mShareIntent,
                    activity,
                    mItem.app
            ).execute();
        }
    }


    private void showAppDialog(NativeLibrary nativeLib) {
        FragmentManager fm = getFragmentManager();
        assert fm != null;

        NativeLibDetailsFragment dialog = new NativeLibDetailsFragment();
        dialog.setNativeLib(nativeLib);
        dialog.show(fm, "fragment_app_info");
    }

    private static class NativeLibsAdapter extends ArrayAdapter<NativeLibrary> {
        private final LayoutInflater mInflater;
        private final int mLayout;

        public NativeLibsAdapter(@NonNull Context context, List<NativeLibrary> nativeLibs, LayoutInflater inflater) {
            super(context, -1, nativeLibs);
            this.mInflater = inflater;
            this.mLayout = R.layout.list_item_nativelibs;
        }


        @Override
        public View getView(int position, @Nullable View convertView, ViewGroup parent) {

            if (convertView == null) {
                convertView = mInflater.inflate(mLayout, parent, false);
            }
            assert convertView != null;

            final NativeLibrary lib = getItem(position);

            TextView libNameTextView = (TextView) convertView.findViewById(R.id.nativeLibNameTextView);
            if (libNameTextView != null)
                libNameTextView.setText(lib.path);
            TextView libSizeTextView = (TextView) convertView.findViewById(R.id.nativeLibSizeTextView);
            if (libSizeTextView != null)
                libSizeTextView.setText(humanReadableFileSize(lib.size));

            TextView libABITextView = (TextView) convertView.findViewById(R.id.nativeLibABIextView);
            if (libABITextView != null)
                libABITextView.setText(ABI.getStringForABI(lib.abi));

            // Return the completed view to render on screen
            return convertView;
        }


    }

    private static class TextReportGenerationTask extends AsyncTask<Object, Integer, String> {
        private WeakReference<Intent> shareIntent;
        private WeakReference<Context> ctx;
        private App app;

        public TextReportGenerationTask(Intent shareIntent, Context ctx, App app) {
            super();
            this.shareIntent = new WeakReference<>(shareIntent);
            this.ctx = new WeakReference<>(ctx);
            this.app = app;
        }

        @Override
        protected String doInBackground(Object... saps) {
            return app.toTextReport(ctx.get());
        }

        @Override
        protected void onPostExecute(String result) {
            Intent i = shareIntent.get();
            if (i != null) {
                i.putExtra(Intent.EXTRA_TEXT, result);
            }
        }
    }

    private static class RefreshAppAsyncTask extends AsyncTask<Object, Float, Boolean> {

        private WeakReference<AppDetailFragment> appDetailFragmentWeakReference;
        private long appId;

        private RefreshAppAsyncTask(long appId, AppDetailFragment appDetailFragment) {
            this.appId = appId;
            this.appDetailFragmentWeakReference = new WeakReference<>(appDetailFragment);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            AppDetailFragment f = appDetailFragmentWeakReference.get();
            if (f != null) {
                View v = f.getView();
                if (v != null) {
                    v.setVisibility(View.INVISIBLE);
                    Activity activity = f.getActivity();
                    if (activity != null) {
                        activity.setProgressBarIndeterminate(true);
                        activity.setProgressBarIndeterminateVisibility(true);
                    }
                }
            }
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            AppDetailFragment f = appDetailFragmentWeakReference.get();
            if (f != null) {
                Activity activity = f.getActivity();
                if (activity != null) {
                    DatabaseHandler dbHandler = DatabaseHandler.getInstance(activity);
                    dbHandler.updateApp(appId);
                    f.prepareContent(activity, appId);
                    return true;
                }
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean worked) {
            super.onPostExecute(worked);

            if (worked) {
                AppDetailFragment f = appDetailFragmentWeakReference.get();
                if (f != null) {
                    View view = f.getView();
                    Activity activity = f.getActivity();

                    if (view != null && activity != null) {
                        f.populateView(view);
                        f.populateShareActionProvider();
                        view.setVisibility(View.VISIBLE);
                        activity.setProgressBarIndeterminate(false);
                        activity.setProgressBarIndeterminateVisibility(false);
                    }
                }
            }
        }
    }

    private static class LoadContentAsyncTask extends AsyncTask<Long, Float, ApplicationEntry> {
        private WeakReference<AppDetailFragment> appDetailFragmentWeakReference;

        public LoadContentAsyncTask(AppDetailFragment appDetailFragment) {
            super();
            appDetailFragmentWeakReference = new WeakReference<>(appDetailFragment);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            AppDetailFragment fragment = appDetailFragmentWeakReference.get();
            if (fragment != null) {
                Activity activity = fragment.getActivity();
                if (activity != null) {
                    activity.setProgressBarIndeterminate(true);
                    activity.setProgressBarIndeterminateVisibility(true);
                }
            }
        }

        @Override
        protected ApplicationEntry doInBackground(Long... appIds) {
            AppDetailFragment fragment = appDetailFragmentWeakReference.get();
            if (fragment != null) {
                Activity activity = fragment.getActivity();
                if (activity != null)
                    return DatabaseHandler.getInstance(activity).getApplication(appIds[0]);
            }
            return null;
        }

        @Override
        protected void onPostExecute(ApplicationEntry applicationEntry) {
            super.onPostExecute(applicationEntry);

            AppDetailFragment f = appDetailFragmentWeakReference.get();
            if (f != null) {
                f.mItem = applicationEntry;

                if (f.mItem == null) {
                    f.mPackagedNativeLibsAdapter = null;
                    f.mInstalledNativeLibsAdapter = null;
                    f.mFrameworksUsed = "";
                } else {

                    //Crashlytics.setString("app_package_name", applicationEntry.app.packagename);
                    Activity activity = f.getActivity();
                    if (activity != null) {

                        if (applicationEntry.app.packagedNativeLibs.size() > 0) {
                            f.mPackagedNativeLibsAdapter = new NativeLibsAdapter(activity, applicationEntry.app.packagedNativeLibs, activity.getLayoutInflater());
                        }

                        if (applicationEntry.app.installedNativeLibs.size() > 0) {
                            f.mInstalledNativeLibsAdapter = new NativeLibsAdapter(activity, applicationEntry.app.installedNativeLibs, activity.getLayoutInflater());
                        }

                            /* adding frameworks from installed and packaged libs */
                        final TreeSet<String> frameworksInApp = new TreeSet<>();
                        for (final NativeLibrary lib : applicationEntry.app.installedNativeLibs) {
                            frameworksInApp.addAll(lib.frameworks);
                        }

                        for (final NativeLibrary lib : applicationEntry.app.packagedNativeLibs) {
                            frameworksInApp.addAll(lib.frameworks);
                        }

                        if (frameworksInApp.size() > 0)
                            f.mFrameworksUsed = Arrays.toString(frameworksInApp.toArray()).replaceAll("[\\[\\]]", "");

                        View v = f.getView();
                        if (v != null)
                            f.populateView(v);

                        f.populateShareActionProvider();
                        activity.setProgressBarIndeterminate(false);
                        activity.setProgressBarIndeterminateVisibility(false);
                    }
                }
            }
        }
    }

}
