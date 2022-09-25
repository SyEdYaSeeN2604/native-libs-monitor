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
import android.app.FragmentManager;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.format.DateFormat;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Adapter;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.xh.nativelibsmonitor.database.AppProvider;
import com.xh.nativelibsmonitor.database.ApplicationEntry;
import com.xh.nativelibsmonitor.database.DatabaseHandler;
import com.xh.nativelibsmonitor.lib.ApplicationType;

import java.lang.ref.WeakReference;


/**
 * A list fragment representing a list of Apps. This fragment
 * also supports tablet devices by allowing list items to be given an
 * 'activated' state upon selection. This helps indicate which item is
 * currently being viewed in a {@link AppDetailFragment}.
 * <p/>
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
public class AppsListFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>, SearchView.OnQueryTextListener {

    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";
    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static final Callbacks sAppCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(long id) {
        }
    };
    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    @NonNull
    private Callbacks mCallbacks = sAppCallbacks;
    private static final String PREF_SORT_TYPE = "sort-by";
    private static final String PREF_LIST_APPS_WITH_NO_LIBS = "list-java-apps";

    private static final int SORT_TYPE_ALPHABETICAL = 0;
    private static final int SORT_TYPE_LAST_UPDATED = 1;
    private static final int SORT_TYPE_APPLICATION_TYPE = 2;
    private int mSortType = SORT_TYPE_ALPHABETICAL;

    @Nullable
    SearchView mSearchView;
    @NonNull
    private String mCurFilter = "";
    /**
     * The current activated item position. Only used on tablets.
     */
    private int mActivatedPosition = ListView.INVALID_POSITION;
    private MenuItem mAlphabeticalOrderMenuItem;
    private MenuItem mLastUpdateOrderMenuItem;
    private MenuItem mTypeOrderMenuItem;
    private boolean mListJavaApps = true;
    private TextView mLoadingTextView;
    private boolean mListShown = true;
    private View mListView;
    private View mProgressContainer;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public AppsListFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_apps_list,
                container, false);

        mLoadingTextView = (TextView) v.findViewById(R.id.loadingTextView);
        mListView = v.findViewById(android.R.id.list);
        mProgressContainer = v.findViewById(R.id.progressContainer);

        return v;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        Activity activity = getActivity();
        assert activity != null;

        SharedPreferences prefs = activity.getPreferences(Context.MODE_PRIVATE);
        mSortType = prefs.getInt(PREF_SORT_TYPE, 0);
        mListJavaApps = prefs.getBoolean(PREF_LIST_APPS_WITH_NO_LIBS, true);

        SimpleCursorAdapter adapter = new SimpleCursorAdapter(getActivity(),
                R.layout.list_item_apps, null, new String[]{
                ApplicationEntry.COL_APPNAME, ApplicationEntry.COL_PACKAGENAME,
                ApplicationEntry.COL_APPLICATIONTYPE, ApplicationEntry.COL_PNGICON, ApplicationEntry.COL_LASTUPDATE}, new int[]{R.id.listAppName,
                R.id.listAppPackageName, R.id.listAppType, R.id.listAppImageView, R.id.listAppLastUpdate}, 0
        );

        adapter.setViewBinder(new ApplicationListItemViewBinder());
        setListAdapter(adapter);

        // Load the content
        LoaderManager loaderManager = getLoaderManager();
        assert loaderManager != null;

        loaderManager.initLoader(0, null, this);
    }

    @Override
    public void onPause() {
        super.onPause();

        Activity activity = getActivity();
        assert activity != null;

        SharedPreferences.Editor editor = activity.getPreferences(Context.MODE_PRIVATE).edit();
        editor.putInt(PREF_SORT_TYPE, mSortType);
        editor.putBoolean(PREF_LIST_APPS_WITH_NO_LIBS, mListJavaApps);
        editor.apply();
    }

    @Nullable
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri baseUri;
        if (mCurFilter.length() > 0) {
            baseUri = Uri.withAppendedPath(AppProvider.URI_APPS_FILTERED,
                    Uri.encode(mCurFilter));
        } else {
            baseUri = AppProvider.URI_APPS;
        }

        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        String select = mListJavaApps ? "" : ApplicationEntry.COL_APPLICATIONTYPE + "!=" + ApplicationType.NO_NATIVE_LIBS_INSTALLED;
        String order;
        switch (mSortType) {

            case SORT_TYPE_ALPHABETICAL:
                order = ApplicationEntry.COL_APPNAME + " COLLATE LOCALIZED ASC";
                break;
            case SORT_TYPE_LAST_UPDATED:
                order = ApplicationEntry.COL_LASTUPDATE + " DESC";
                break;
            case SORT_TYPE_APPLICATION_TYPE:
                order = ApplicationEntry.COL_APPLICATIONTYPE + " ASC, " + ApplicationEntry.COL_APPNAME + " COLLATE LOCALIZED ASC";
                break;
            default:
                order = "";
        }

        return new CursorLoader(getActivity(), baseUri,
                ApplicationEntry.FIELDS, select, null,
                order);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
        CursorAdapter cursorListAdapter = (CursorAdapter) getListAdapter();
        assert cursorListAdapter != null;
        cursorListAdapter.swapCursor(c);

        Activity activity = getActivity();
        assert activity != null;

        final DatabaseHandler db = DatabaseHandler.getInstance(activity);
        if (db.isNotBeingPopulated()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopDisplayingLoader();
                }
            });
        } else { // update loader
            final Pair<Integer, Integer> populationState = db.getCurrentPopulationState();
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateDisplayedLoader(populationState);
                }
            });
        }
    }

    private void startDisplayingLoader() {
        setListShown(false);
        mLoadingTextView.setText(R.string.analyzing_apps);
    }

    private void updateDisplayedLoader(Pair<Integer, Integer> populationState) {
        mLoadingTextView.setText(String.format(getResources().getString(R.string.analyzing_apps_n_remaining), populationState.second - populationState.first));
    }

    private void stopDisplayingLoader() {
        setListShown(true);
        mLoadingTextView.setText("");
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        CursorAdapter cursorListAdapter = (CursorAdapter) getListAdapter();
        assert cursorListAdapter != null;
        cursorListAdapter.swapCursor(null);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        startDisplayingLoader();

        // Restore the previously serialized activated item position.
        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = sAppCallbacks;
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        Adapter adapter = getListAdapter();
        assert adapter != null;
        long appId = adapter.getItemId(position);
        mCallbacks.onItemSelected(appId);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */

    public void activateOnItemClick() {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        ListView listView = getListView();
        assert listView != null;
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    }

    private void setActivatedPosition(int position) {
        ListView listView = getListView();
        assert listView != null;

        if (position == ListView.INVALID_POSITION) {
            listView.setItemChecked(mActivatedPosition, false);
        } else {
            listView.setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.apps_list_menu, menu);

        Activity activity = getActivity();
        assert activity != null;

        mSearchView = (SearchView) menu.findItem(R.id.search_filter).getActionView();
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                AppsListFragment.this.onQueryTextSubmit(mSearchView.getQuery().toString());
            }
        });

        mAlphabeticalOrderMenuItem = menu.findItem(R.id.menu_apps_list_sort_alphabetically).setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(@NonNull MenuItem item) {
                        if (mSortType != SORT_TYPE_ALPHABETICAL) {
                            mSortType = SORT_TYPE_ALPHABETICAL;
                            item.setChecked(true);
                            mLastUpdateOrderMenuItem.setChecked(false);
                            mTypeOrderMenuItem.setChecked(false);
                            restartLoader();
                        }
                        return true;
                    }
                }
        ).setChecked(mSortType == SORT_TYPE_ALPHABETICAL);

        mLastUpdateOrderMenuItem = menu.findItem(R.id.menu_apps_list_sort_by_last_update).setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(@NonNull MenuItem item) {
                        if (mSortType != SORT_TYPE_LAST_UPDATED) {
                            mSortType = SORT_TYPE_LAST_UPDATED;
                            item.setChecked(true);
                            mAlphabeticalOrderMenuItem.setChecked(false);
                            mTypeOrderMenuItem.setChecked(false);
                            restartLoader();
                        }
                        return true;
                    }
                }
        ).setChecked(mSortType == SORT_TYPE_LAST_UPDATED);

        mTypeOrderMenuItem = menu.findItem(R.id.menu_apps_list_sort_by_type).setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(@NonNull MenuItem item) {
                        if (mSortType != SORT_TYPE_APPLICATION_TYPE) {
                            mSortType = SORT_TYPE_APPLICATION_TYPE;
                            item.setChecked(true);
                            mAlphabeticalOrderMenuItem.setChecked(false);
                            mLastUpdateOrderMenuItem.setChecked(false);
                            restartLoader();
                        }
                        return true;
                    }
                }
        ).setChecked(mSortType == SORT_TYPE_APPLICATION_TYPE);

        menu.findItem(R.id.menu_apps_list_include_java_apps_menu_item_entry).setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(@NonNull MenuItem item) {
                        mListJavaApps = !mListJavaApps;
                        item.setChecked(mListJavaApps);
                        restartLoader();
                        return true;
                    }
                }
        ).setChecked(mListJavaApps);

        final WeakReference<Activity> weakReferenceActivity = new WeakReference<>(activity);

        menu.findItem(R.id.menu_apps_list_export_as_csv).setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        Activity activity = weakReferenceActivity.get();
                        if (activity != null) {
                            Intent shareIntent = new Intent();
                            shareIntent.setAction(Intent.ACTION_SEND);
                            shareIntent.putExtra(Intent.EXTRA_STREAM, AppProvider.URI_DATABASE);
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            shareIntent.setType("text/csv");
                            startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.export_database_csv)));
                            activity.revokeUriPermission(AppProvider.URI_DATABASE, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                        return true;
                    }
                }
        );

        menu.findItem(R.id.menu_apps_list_refresh).setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        Activity activity = weakReferenceActivity.get();
                        if (activity != null) {
                            final DatabaseHandler db = DatabaseHandler.getInstance(activity);
                            if (db.isNotBeingPopulated()) {
                                startDisplayingLoader();
                                Thread bgThread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        db.refreshDatabase();
                                    }
                                });
                                bgThread.setPriority(Thread.MIN_PRIORITY);
                                bgThread.start();
                            }
                        }
                        return true;
                    }
                }
        );

        menu.add(Menu.NONE, Menu.NONE, 19, "Device Details").setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        showDeviceDetails();
                        return true;
                    }
                }

        );

    }

    private void showDeviceDetails() {
        FragmentManager fm = getFragmentManager();
        assert fm != null;

        DeviceDetailsFragment dialog = new DeviceDetailsFragment();
        dialog.show(fm, "fragment_device_details");
    }

    private void restartLoader() {
        LoaderManager lm = getLoaderManager();
        if (lm != null)
            lm.restartLoader(0, null, this);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mCurFilter = query;
        restartLoader();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mCurFilter = newText;
        restartLoader();
        return true;
    }

    @Override
    public void setListShown(boolean shown) {
        if (mListShown == shown) {
            return;
        }
        mListShown = shown;
        if (shown) {
            if (mSearchView != null) mSearchView.setEnabled(true);
            mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
                    getActivity(), android.R.anim.fade_out));
            mListView.startAnimation(AnimationUtils.loadAnimation(
                    getActivity(), android.R.anim.fade_in));

            mProgressContainer.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
        } else {
            if (mSearchView != null) mSearchView.setEnabled(false);
            mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
                    getActivity(), android.R.anim.fade_in));
            mListView.startAnimation(AnimationUtils.loadAnimation(
                    getActivity(), android.R.anim.fade_out));

            mProgressContainer.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.GONE);
        }
    }

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when an item has been selected.
         */
        void onItemSelected(long id);
    }

    private static class ApplicationListItemViewBinder implements SimpleCursorAdapter.ViewBinder {
        @Override
        public boolean setViewValue(@NonNull View view, @NonNull Cursor cursor, int columnIndex) {
            if (view.getId() == R.id.listAppImageView) {
                final ImageView iv = (ImageView) view;
                final byte[] pngIcon = cursor.getBlob(columnIndex);
                if (pngIcon != null && pngIcon.length > 0) {
                    iv.setImageBitmap(BitmapFactory.decodeByteArray(pngIcon, 0, pngIcon.length));
                } else
                    iv.setVisibility(View.GONE);
                return true;
            } else if (view.getId() == R.id.listAppLastUpdate) {
                final TextView tv = (TextView) view;
                tv.setText(DateFormat.format("yyyy-MM-dd", cursor.getLong(columnIndex)));
                return true;
            } else if (view.getId() == R.id.listAppType) {
                final TextView tv = (TextView) view;
                Context ctx = view.getContext();
                assert ctx != null;
                tv.setText(ApplicationType.getApplicationTypeLocalizedTitle(cursor.getInt(columnIndex), ctx));
                return true;
            }
            return false;
        }
    }
}