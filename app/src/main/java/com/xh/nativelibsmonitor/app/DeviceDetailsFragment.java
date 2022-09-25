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

import android.app.ActivityManager;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLES10;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.xh.nativelibsmonitor.lib.AppAnalyzer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class DeviceDetailsFragment extends DialogFragment {
    private static final String TAG = "fragment_device_details";
    private String mBinaryBridgeVersion;
    private TextView glesDriverTextView;
    private TextView glesDriverVendorTextView;
    private TextView glesHardwareTextView;
    private String mDriverVersion;
    private String mDriverVendor;
    private String mGLESHardware;
    private LinearLayout rootLayout;
    private GLSurfaceView mGlSurfaceView;
    private GLSurfaceView.Renderer mGlRenderer = new GLSurfaceView.Renderer() {

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            mDriverVersion = gl.glGetString(GLES10.GL_VERSION);
            mDriverVendor = gl.glGetString(GLES10.GL_VENDOR);
            mGLESHardware = gl.glGetString(GLES10.GL_RENDERER);

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    glesDriverTextView.setText(mDriverVersion);
                    glesDriverVendorTextView.setText(mDriverVendor);
                    glesHardwareTextView.setText(mGLESHardware);
                    rootLayout.removeView(mGlSurfaceView);
                    glesDriverTextView.invalidate();
                    glesDriverVendorTextView.invalidate();
                    glesHardwareTextView.invalidate();
                    rootLayout.invalidate();
                }
            });
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {

        }

        @Override
        public void onDrawFrame(GL10 gl) {

        }
    };


    public DeviceDetailsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinaryBridgeVersion = AppAnalyzer.getNativeBridgeVersion();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_details, container);
        assert view != null;
        Dialog dlg = getDialog();
        assert dlg != null;
        dlg.setTitle(view.getContext().getString(R.string.device_details_label));

        TextView nativeBridgeVersionTextView = (TextView) view.findViewById(R.id.native_bridge_version_text);

        if (mBinaryBridgeVersion.length() > 0) {
            nativeBridgeVersionTextView.setText(mBinaryBridgeVersion);
        } else {
            nativeBridgeVersionTextView.setVisibility(View.GONE);
            view.findViewById(R.id.native_bridge_version_label).setVisibility(View.GONE);
        }

        TextView cpuAbiTextView = (TextView) view.findViewById(R.id.cpu_abis_text);
        cpuAbiTextView.setText(getSupportedABIs());


        TextView buildVersionTextView = (TextView) view.findViewById(R.id.build_version_text);
        buildVersionTextView.setText(Build.FINGERPRINT);

        rootLayout = (LinearLayout) view.findViewById(R.id.device_details_root_layout);
        glesDriverTextView = (TextView) view.findViewById(R.id.opengles_driver_version_text);
        glesDriverVendorTextView = (TextView) view.findViewById(R.id.opengles_driver_vendor_text);
        glesHardwareTextView = (TextView) view.findViewById(R.id.opengles_hardware_text);


        final ActivityManager activityManager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();

        TextView glesVersionTextView = (TextView) view.findViewById(R.id.opengles_version_text);
        glesVersionTextView.setText(configurationInfo.getGlEsVersion());

        mGlSurfaceView = new GLSurfaceView(getActivity()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                this.setMeasuredDimension(1, 1);
            }
        };
        mGlSurfaceView.setWillNotDraw(true);
        int oglEsVersionMajor = ((configurationInfo.reqGlEsVersion & 0xffff0000) >> 16);
        if (oglEsVersionMajor < 1) oglEsVersionMajor = 1;
        mGlSurfaceView.setEGLContextClientVersion(oglEsVersionMajor);
        //mGlSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mGlSurfaceView.setRenderer(mGlRenderer);
        rootLayout.addView(mGlSurfaceView);

        return view;
    }

    @SuppressWarnings("deprecation")
    private String getSupportedABIs() {
        StringBuilder sb = new StringBuilder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (final String abi : Build.SUPPORTED_ABIS) {
                sb.append(abi).append(", ");
            }
            sb.setLength(sb.length() - 2);
        } else {
            sb.append(Build.CPU_ABI);
            if (!Build.CPU_ABI2.isEmpty())
                sb.append(", ").append(Build.CPU_ABI2);
        }
        return sb.toString();
    }

}