/*
 * Copyright (C) 2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.refreshrate;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import androidx.preference.PreferenceManager;

public final class RefreshUtils {

    private static final String TAG = "RefreshUtils";
    private static final String REFRESH_CONTROL = "refresh_control";
    private static final String REFRESH_SERVICE = "refresh_service";

    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private Context mContext;

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_STANDARD = 1;
    protected static final int STATE_HIGH = 2;
    protected static final int STATE_EXTREME = 3;

    private static final float REFRESH_STATE_DEFAULT = 120f;
    private static final float REFRESH_STATE_STANDARD = 60f;
    private static final float REFRESH_STATE_HIGH = 90f;
    private static final float REFRESH_STATE_EXTREME = 120f;

    private static final String REFRESH_STANDARD = "refresh.standard=";
    private static final String REFRESH_HIGH = "refresh.high=";
    private static final String REFRESH_EXTREME = "refresh.extreme=";
    
    private boolean isAppInList = false;
    private float defaultMaxRate;
    private float defaultMinRate;

    private SharedPreferences mSharedPrefs;

    protected RefreshUtils(Context context) {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mContext = context;
        // Initialize defaults
        defaultMaxRate = Settings.System.getFloat(context.getContentResolver(), KEY_PEAK_REFRESH_RATE, REFRESH_STATE_DEFAULT);
        defaultMinRate = Settings.System.getFloat(context.getContentResolver(), KEY_MIN_REFRESH_RATE, REFRESH_STATE_DEFAULT);
    }

    public static void initialize(Context context) {
        if (isServiceEnabled(context))
            startService(context);
        else
            setDefaultRefreshRate(context);
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, RefreshService.class),
                UserHandle.CURRENT);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(REFRESH_SERVICE, "true").apply();
    }

    protected static void stopService(Context context) {
        context.stopService(new Intent(context, RefreshService.class));
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(REFRESH_SERVICE, "false").apply();
    }

    protected static boolean isServiceEnabled(Context context) {
        return true;
    }

    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(REFRESH_CONTROL, profiles).apply();
    }

    private String getValue() {
        String value = mSharedPrefs.getString(REFRESH_CONTROL, null);

        if (value == null || value.isEmpty()) {
            value = REFRESH_STANDARD + ":" + REFRESH_HIGH + ":" + REFRESH_EXTREME;
            writeValue(value);
        }
        return value;
    }

    protected void writePackage(String packageName, int mode) {
        String value = getValue();
        String[] modes = value.split(":");
        
        // Ensure we have 3 modes
        if (modes.length < 3) {
            modes = new String[] {REFRESH_STANDARD, REFRESH_HIGH, REFRESH_EXTREME};
        }

        // Remove package from all modes first
        for (int i = 0; i < modes.length; i++) {
            modes[i] = modes[i].replace(packageName + ",", "");
        }

        // Add package to selected mode (skip DEFAULT)
        switch (mode) {
            case STATE_STANDARD:
                modes[0] += packageName + ",";
                break;
            case STATE_HIGH:
                modes[1] += packageName + ",";
                break;
            case STATE_EXTREME:
                modes[2] += packageName + ",";
                break;
            case STATE_DEFAULT:
            default:
                // Don't add to any mode for default
                break;
        }

        writeValue(modes[0] + ":" + modes[1] + ":" + modes[2]);
    }

    protected int getStateForPackage(String packageName) {
        String value = getValue();
        String[] modes = value.split(":");
        
        if (modes.length < 3) {
            return STATE_DEFAULT;
        }
        
        if (modes[0].contains(packageName + ",")) {
            return STATE_STANDARD;
        } else if (modes[1].contains(packageName + ",")) {
            return STATE_HIGH;
        } else if (modes[2].contains(packageName + ",")) {
            return STATE_EXTREME;
        }
        return STATE_DEFAULT;
    }

    public static void setDefaultRefreshRate(Context context) {
        try {
            float defaultMax = Settings.System.getFloat(context.getContentResolver(), KEY_PEAK_REFRESH_RATE, REFRESH_STATE_DEFAULT);
            float defaultMin = Settings.System.getFloat(context.getContentResolver(), KEY_MIN_REFRESH_RATE, REFRESH_STATE_DEFAULT);
            Settings.System.putFloat(context.getContentResolver(), KEY_PEAK_REFRESH_RATE, defaultMax);
            Settings.System.putFloat(context.getContentResolver(), KEY_MIN_REFRESH_RATE, defaultMin);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set default refresh rate", e);
        }
    }
    
    protected void setRefreshRate(String packageName) {
        if (mContext == null || packageName == null) return;
        
        try {
            String value = getValue();
            if (value == null) return;
            
            String[] modes = value.split(":");
            if (modes.length < 3) return;

            if (!isAppInList) {
                defaultMaxRate = Settings.System.getFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, REFRESH_STATE_DEFAULT);
                defaultMinRate = Settings.System.getFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, REFRESH_STATE_DEFAULT);
            }

            float minrate = defaultMinRate;
            float maxrate = defaultMaxRate;

            if (modes[0].contains(packageName + ",")) {
                maxrate = REFRESH_STATE_STANDARD;
                isAppInList = true;
            } else if (modes[1].contains(packageName + ",")) {
                maxrate = REFRESH_STATE_HIGH;
                isAppInList = true;
            } else if (modes[2].contains(packageName + ",")) {
                maxrate = REFRESH_STATE_EXTREME;
                isAppInList = true;
            } else {
                isAppInList = false;
            }

            // Adjust min rate if needed
            if (minrate > maxrate) {
                minrate = maxrate;
            }

            Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, maxrate);
            Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, minrate);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set refresh rate for " + packageName, e);
        }
    }
}