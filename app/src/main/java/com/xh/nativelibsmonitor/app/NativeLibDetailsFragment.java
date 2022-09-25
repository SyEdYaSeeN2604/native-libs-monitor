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
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.xh.nativelibsmonitor.lib.ABI;
import com.xh.nativelibsmonitor.lib.NativeLibrary;

public class NativeLibDetailsFragment extends DialogFragment {
    private static NativeLibrary nativeLibrary;

    public NativeLibDetailsFragment() {
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    public void setNativeLib(NativeLibrary nativeLib) {
        nativeLibrary = nativeLib;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_native_lib_details, container);
        assert view != null;
        Dialog dlg = getDialog();
        assert dlg != null;
        dlg.setTitle(nativeLibrary.path);

        ListView entryPointsListView = (ListView) view.findViewById(R.id.entryPointsListView);
        TextView abiTextView = (TextView) view.findViewById(R.id.entryPointsABITextView);
        TextView sizeTextView = (TextView) view.findViewById(R.id.entryPointsSizeTextView);
        TextView entryPointsTitleTextView = (TextView) view.findViewById(R.id.entryPointsTitleTextView);
        TextView dependenciesTextView = (TextView) view.findViewById(R.id.dependenciesTextView);
        TextView frameworksTextView = (TextView) view.findViewById(R.id.frameworksTextView);

        sizeTextView.setText(Html.fromHtml("<b>Size:</b> " + humanReadableFileSize(nativeLibrary.size)));
        abiTextView.setText(Html.fromHtml("<b>ABI:</b> " + ABI.getStringForABI(nativeLibrary.abi)));

        if (nativeLibrary.entryPoints.size() > 0) {
            Activity activity = getActivity();
            assert activity != null;
            entryPointsListView.setAdapter(new ArrayAdapter<>(activity,
                    R.layout.list_item_entrypoints, android.R.id.text1, nativeLibrary.entryPoints));
        } else
            entryPointsTitleTextView.setVisibility(View.GONE);

        if (nativeLibrary.dependencies.size() > 0) {
            dependenciesTextView.setText(Html.fromHtml("<b>Dependencies:</b> " + nativeLibrary.dependencies.toString().replaceAll("[\\[\\]]", "")));
        } else
            dependenciesTextView.setVisibility(View.GONE);

        if (nativeLibrary.frameworks.size() > 0) {
            frameworksTextView.setText(Html.fromHtml("<b>Known Frameworks:</b> " + nativeLibrary.frameworks.toString().replaceAll("[\\[\\]]", "")));
        } else
            frameworksTextView.setVisibility(View.GONE);

        return view;
    }
}