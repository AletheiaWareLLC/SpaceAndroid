/*
 * Copyright 2019 Aletheia Ware LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aletheiaware.space.android.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

import com.aletheiaware.bc.android.ui.PasswordUnlockDialog;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.android.utils.BiometricUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.android.BuildConfig;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.crypto.NoSuchPaddingException;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            Fragment preferenceFragment = new SettingsPreferenceFragment();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.preference_frame, preferenceFragment);
            ft.commit();
        }
    }

    // FIXME these settings are shared between all accounts
    // ie. if one user changes sort, all users will have it changed
    // ie. if one user changes miner, all users will have it changed
    // ie. if one user disables biometric unlock, all users will have it disabled
    // Possible solutions are a) use SQLite or b) store files prefixed with alias or c) store in blockchain, either way don't rely on SharedPreferences
    public static class SettingsPreferenceFragment extends PreferenceFragmentCompat {
        private ListPreference sortPreference;
        private ListPreference miningLocationPreference;
        private ListPreference hostPreference;
        private Preference cacheSizePreference;
        private Preference cachePurgePreference;
        private CheckBoxPreference biometricPreference;
        private Preference appVersionPreference;
        private Preference supportPreference;

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(R.xml.fragment_preference);

            sortPreference = (ListPreference) findPreference(getString(R.string.preference_sort_key));
            sortPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    return true;
                }
            });

            miningLocationPreference = (ListPreference) findPreference(getString(R.string.preference_mining_location_key));
            miningLocationPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    return true;
                }
            });

            hostPreference = (ListPreference) findPreference(getString(R.string.preference_host_key));
            hostPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.equals("-1")) {
                        final FragmentActivity activity = getActivity();
                        if (activity == null) {
                            return false;
                        }
                        String defaultHostname = SpaceAndroidUtils.getHostPreference(activity);
                        new CustomHostDialog(activity, defaultHostname) {
                            @Override
                            protected void onCustomHostname(String hostname) {
                                SpaceAndroidUtils.setHostPreference(activity, hostname);
                            }
                        }.create();
                    }
                    return true;
                }
            });

            cacheSizePreference = findPreference(getString(R.string.preference_cache_size_key));
            cacheSizePreference.setShouldDisableView(true);
            cacheSizePreference.setEnabled(false);
            cacheSizePreference.setSelectable(false);

            cachePurgePreference = findPreference(getString(R.string.preference_cache_purge_key));
            cachePurgePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    boolean result = BCAndroidUtils.purgeCache(getContext());
                    update();
                    return result;
                }
            });

            biometricPreference = (CheckBoxPreference) findPreference(getString(R.string.preference_biometric_key));
            biometricPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    final FragmentActivity activity = getActivity();
                    if (activity == null) {
                        return false;
                    }
                    final String alias = BCAndroidUtils.getAlias();
                    if ((Boolean) o) {
                        new PasswordUnlockDialog(activity, alias) {
                            @Override
                            public void onUnlock(DialogInterface dialog, char[] password) {
                                dialog.dismiss();
                                try {
                                    BiometricUtils.enableBiometricUnlock(activity, alias, password);
                                    update();
                                } catch (InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | NoSuchProviderException e) {
                                    BCAndroidUtils.showErrorDialog(activity, R.string.error_biometric_enroll, e);
                                }
                            }
                        }.create();
                        return BiometricUtils.isBiometricUnlockAvailable(activity);
                    } else {
                        return BiometricUtils.disableBiometricUnlock(activity, alias);
                    }
                }
            });

            appVersionPreference = findPreference(getString(R.string.preference_app_version_key));
            appVersionPreference.setShouldDisableView(true);
            appVersionPreference.setEnabled(false);
            appVersionPreference.setSelectable(false);

            supportPreference = findPreference(getString(R.string.preference_support_key));
            supportPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final FragmentActivity activity = getActivity();
                    if (activity == null) {
                        return false;
                    }
                    BCAndroidUtils.support(activity, new StringBuilder());
                    return true;
                }
            });

            update();
        }

        public void update() {
            final FragmentActivity activity = getActivity();
            if (activity == null) {
                return;
            }
            final String alias = BCAndroidUtils.getAlias();

            long size = BCAndroidUtils.getCacheSize(activity);
            cacheSizePreference.setSummary(BCUtils.sizeToString(size));
            cachePurgePreference.setEnabled(size > 0L);

            boolean available = BiometricUtils.isBiometricUnlockAvailable(activity);
            biometricPreference.setEnabled(available);
            biometricPreference.setSelectable(available);
            biometricPreference.setVisible(available);
            biometricPreference.setChecked(BiometricUtils.isBiometricUnlockEnabled(activity, alias));

            appVersionPreference.setSummary(BuildConfig.BUILD_TYPE + "-" + BuildConfig.VERSION_NAME);
        }
    }
}
