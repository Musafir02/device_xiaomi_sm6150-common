/*
 * Copyright (C) 2021 crDroid Android Project
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
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.Display;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RefreshTileService extends TileService {
    private static final String TAG = "RefreshTileService";
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";

    private Context context;
    private Tile tile;

    private final List<Integer> availableRates = new ArrayList<>();
    private int activeRateMin;
    private int activeRateMax;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        if (context != null) {
            initializeAvailableRates();
            syncFromSettings();
        }
    }

    private void initializeAvailableRates() {
        try {
            Display display = context.getDisplay();
            if (display == null) {
                Log.e(TAG, "Display is null");
                // Fallback to common refresh rates
                availableRates.add(60);
                availableRates.add(90);
                availableRates.add(120);
                return;
            }

            Display.Mode mode = display.getMode();
            Display.Mode[] modes = display.getSupportedModes();
            
            for (Display.Mode m : modes) {
                int rate = (int) Math.round(m.getRefreshRate());
                if (m.getPhysicalWidth() == mode.getPhysicalWidth() &&
                    m.getPhysicalHeight() == mode.getPhysicalHeight() &&
                    !availableRates.contains(rate)) {
                    availableRates.add(rate);
                }
            }
            
            // Sort rates in ascending order
            Collections.sort(availableRates);
            
            // Ensure we have at least one rate
            if (availableRates.isEmpty()) {
                availableRates.add(60);
                availableRates.add(120);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing refresh rates", e);
            // Fallback rates
            availableRates.clear();
            availableRates.add(60);
            availableRates.add(90);
            availableRates.add(120);
        }
    }

    private int getSettingOf(String key) {
        try {
            float rate = Settings.System.getFloat(context.getContentResolver(), key, 120);
            int roundedRate = (int) Math.round(rate);
            int index = availableRates.indexOf(roundedRate);
            return Math.max(index, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error getting setting for " + key, e);
            return 0;
        }
    }

    private void syncFromSettings() {
        activeRateMin = getSettingOf(KEY_MIN_REFRESH_RATE);
        activeRateMax = getSettingOf(KEY_PEAK_REFRESH_RATE);
        
        // Ensure valid indices
        if (activeRateMin >= availableRates.size()) {
            activeRateMin = 0;
        }
        if (activeRateMax >= availableRates.size()) {
            activeRateMax = availableRates.size() - 1;
        }
        
        // Ensure min <= max
        if (activeRateMin > activeRateMax) {
            activeRateMin = activeRateMax;
        }
    }

    private void cycleRefreshRate() {
        try {
            // Cycle through max refresh rate
            activeRateMax++;
            if (activeRateMax >= availableRates.size()) {
                activeRateMax = 0;
            }
            
            // Set min rate to match max rate for simplicity
            activeRateMin = activeRateMax;
            
            float maxRate = availableRates.get(activeRateMax);
            float minRate = availableRates.get(activeRateMin);
            
            Settings.System.putFloat(context.getContentResolver(), KEY_PEAK_REFRESH_RATE, maxRate);
            Settings.System.putFloat(context.getContentResolver(), KEY_MIN_REFRESH_RATE, minRate);
            
            Log.d(TAG, "Set refresh rate to: " + maxRate + " Hz");
        } catch (Exception e) {
            Log.e(TAG, "Error cycling refresh rate", e);
        }
    }

    private void updateTileView() {
        if (tile == null) return;
        
        try {
            if (availableRates.isEmpty()) {
                tile.setSubtitle("N/A");
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.updateTile();
                return;
            }
            
            int min = availableRates.get(activeRateMin);
            int max = availableRates.get(activeRateMax);

            String displayText = String.format(Locale.US, "%d Hz", max);
            
            tile.setContentDescription(displayText);
            tile.setSubtitle(displayText);
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        } catch (Exception e) {
            Log.e(TAG, "Error updating tile view", e);
            tile.setSubtitle("Error");
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.updateTile();
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        tile = getQsTile();
        if (tile != null) {
            syncFromSettings();
            updateTileView();
        }
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        tile = null;
    }

    @Override
    public void onClick() {
        super.onClick();
        if (tile != null && !availableRates.isEmpty()) {
            cycleRefreshRate();
            syncFromSettings();
            updateTileView();
        }
    }
}