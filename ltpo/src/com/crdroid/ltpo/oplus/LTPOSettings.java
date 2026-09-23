/*
 * Copyright (C) 2025 crDroid Android Project
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

package com.crdroid.ltpo.oplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.Arrays;

public class LTPOSettings extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = LTPOSettings.class.getSimpleName();

    private static final String KEY_LTPO_SWITCH = "ltpo_enabled";

    private static final String FILE_LTPO = "/sys/kernel/oplus_display/adfr_config";

    /**
     * What the display driver loads from the panel device tree when the panel initialises: after
     * every boot the node reads back as this on astonc. The bits are GLOBAL | IDLE_MODE |
     * OA_BL_MUTUAL_EXCLUSION | HIGH_PRECISION_SA_MODE | HIGH_PRECISION_SWITCH, the configuration
     * the vendor tunes this panel with.
     *
     * Enabling LTPO has to restore that value. It used to write 0x109f, which sets an undefined
     * bit 12 plus a different feature set (FAKEFRAME, VSYNC_SWITCH, VSYNC_SWITCH_MODE,
     * SA_MODE_RESTORE), so it replaced the vendor configuration with an unvalidated one. The
     * driver keeps no default of its own: the node's value after boot is the device tree's.
     */
    private static final String LTPO_ON_VALUE = "0xe51";
    private static final String LTPO_OFF_VALUE = "0x0";

    private SwitchPreferenceCompat mLTPOSwitch;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.ltpo_settings);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        mLTPOSwitch = (SwitchPreferenceCompat) findPreference(KEY_LTPO_SWITCH);
        if (Utils.fileWritable(FILE_LTPO)) {
            mLTPOSwitch.setEnabled(true);
            String current = currentValue();
            mLTPOSwitch.setChecked(sharedPrefs.getBoolean(KEY_LTPO_SWITCH, isEnabled(current)));
            mLTPOSwitch.setOnPreferenceChangeListener(this);
        } else {
            mLTPOSwitch.setEnabled(false);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mLTPOSwitch) {
            boolean enabled = (Boolean) newValue;
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            sharedPrefs.edit().putBoolean(KEY_LTPO_SWITCH, enabled).apply();
            writeLtpoState(enabled);
            return true;
        }

        return false;
    }

    public static void restoreLTPOSetting(Context context) {
        if (!Utils.fileWritable(FILE_LTPO)) {
            Log.w(TAG, "adfr_config is not writable, leaving the panel default alone");
            return;
        }
        String current = currentValue();
        if (current == null) {
            Log.w(TAG, "adfr_config is unreadable, leaving the panel default alone");
            return;
        }
        String trimmed = current.trim();
        if (!LTPO_OFF_VALUE.equalsIgnoreCase(trimmed) && !LTPO_ON_VALUE.equalsIgnoreCase(trimmed)) {
            Log.w(TAG, "adfr_config holds " + trimmed + ", expected the panel default "
                    + LTPO_ON_VALUE + "; writing " + LTPO_ON_VALUE + " for LTPO on");
        }
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        writeLtpoState(sharedPrefs.getBoolean(KEY_LTPO_SWITCH, isEnabled(trimmed)));
    }

    /**
     * Writes only when the node disagrees, then reads it back, so the switch cannot end up
     * claiming a state the panel is not in.
     */
    private static void writeLtpoState(boolean enabled) {
        String want = enabled ? LTPO_ON_VALUE : LTPO_OFF_VALUE;
        String before = currentValue();
        if (want.equalsIgnoreCase(before != null ? before.trim() : null)) {
            return;
        }
        if (!Utils.writeValue(FILE_LTPO, want)) {
            Log.w(TAG, "writing " + want + " to adfr_config failed, node still " + before);
            return;
        }
        String after = currentValue();
        if (after == null || !want.equalsIgnoreCase(after.trim())) {
            Log.w(TAG, "adfr_config did not take " + want + ", reads back as " + after);
        }
    }

    private static boolean isEnabled(String value) {
        return value != null && !LTPO_OFF_VALUE.equalsIgnoreCase(value.trim());
    }

    private static String currentValue() {
        return Utils.getFileValue(FILE_LTPO, null);
    }
}
