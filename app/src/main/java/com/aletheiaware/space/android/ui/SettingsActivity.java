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

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.util.Log;

import com.aletheiaware.bc.android.ui.PasswordUnlockDialog;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.android.utils.BiometricUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.common.utils.CommonUtils;
import com.aletheiaware.space.android.BuildConfig;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.utils.SpaceUtils;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.crypto.NoSuchPaddingException;

public class SettingsActivity extends AppCompatActivity {

    private SettingsPreferenceFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            fragment = new SettingsPreferenceFragment();
            ft.add(R.id.preference_frame, fragment);
            ft.commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fragment.update();
    }

    public static class SettingsPreferenceFragment extends PreferenceFragmentCompat {
        private PreferenceCategory general;
        private PreferenceCategory security;
        private PreferenceCategory cache;
        private PreferenceCategory about;
        private ListPreference sortPreference;
        private CheckBoxPreference biometricPreference;
        private Preference cacheSizePreference;
        private Preference cachePurgePreference;
        private Preference appVersionPreference;
        private Preference supportPreference;

        @Override
        public void onCreatePreferences(Bundle bundle, String key) {
            Context context = getPreferenceManager().getContext();
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);

            general = new PreferenceCategory(context);
            general.setTitle(R.string.preference_general_title);
            screen.addPreference(general);

            sortPreference = new ListPreference(context);
            sortPreference.setTitle(R.string.preference_sort_title);
            sortPreference.setSummary(R.string.preference_sort_description);
            sortPreference.setEntries(R.array.preference_sort_options);
            sortPreference.setEntryValues(R.array.preference_sort_values);
            general.addPreference(sortPreference);

            security = new PreferenceCategory(context);
            security.setTitle(R.string.preference_security_title);
            screen.addPreference(security);

            biometricPreference = new CheckBoxPreference(context);
            biometricPreference.setTitle(R.string.preference_biometric_title);
            biometricPreference.setSummary(R.string.preference_biometric_description);
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
                                    // TODO update after biometric callback success
                                    update();
                                } catch (InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | NoSuchProviderException e) {
                                    CommonAndroidUtils.showErrorDialog(activity, R.style.AlertDialogTheme, R.string.error_biometric_enroll, e);
                                }
                            }
                        }.create();
                        return BiometricUtils.isBiometricUnlockAvailable(activity);
                    } else {
                        return BiometricUtils.disableBiometricUnlock(activity, alias);
                    }
                }
            });
            security.addPreference(biometricPreference);

            cache = new PreferenceCategory(context);
            cache.setTitle(R.string.preference_cache_title);
            screen.addPreference(cache);

            cacheSizePreference = new Preference(context);
            cacheSizePreference.setTitle(R.string.preference_cache_size_title);
            cacheSizePreference.setShouldDisableView(true);
            cacheSizePreference.setEnabled(false);
            cacheSizePreference.setSelectable(false);
            cache.addPreference(cacheSizePreference);

            cachePurgePreference = new Preference(context);
            cachePurgePreference.setTitle(R.string.preference_cache_purge_title);
            cachePurgePreference.setSummary(R.string.preference_cache_purge_description);
            cachePurgePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    boolean result = BCAndroidUtils.purgeCache(getContext());
                    update();
                    return result;
                }
            });
            cache.addPreference(cachePurgePreference);

            about = new PreferenceCategory(context);
            about.setTitle(R.string.preference_about_title);
            screen.addPreference(about);

            appVersionPreference = new Preference(context);
            appVersionPreference.setTitle(R.string.preference_app_version_title);
            appVersionPreference.setShouldDisableView(true);
            appVersionPreference.setEnabled(false);
            appVersionPreference.setSelectable(false);
            about.addPreference(appVersionPreference);

            supportPreference = new Preference(context);
            supportPreference.setTitle(R.string.preference_support_title);
            supportPreference.setSummary(R.string.preference_support_description);
            supportPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final FragmentActivity activity = getActivity();
                    if (activity == null) {
                        return false;
                    }
                    CommonAndroidUtils.support(activity, new StringBuilder());
                    return true;
                }
            });
            about.addPreference(supportPreference);

            setPreferenceScreen(screen);
        }

        public void update() {
            String alias = BCAndroidUtils.getAlias();
            Log.d(SpaceUtils.TAG, "Setting Alias: " + alias);
            if (alias == null || alias.isEmpty()) {
                sortPreference.setVisible(false);
            } else {
                sortPreference.setVisible(true);
                sortPreference.setKey(getString(R.string.preference_sort_key, alias));
                biometricPreference.setKey(getString(R.string.preference_biometric_key, alias));
            }

            final FragmentActivity activity = getActivity();
            if (activity == null) {
                return;
            }
            boolean available = BiometricUtils.isBiometricUnlockAvailable(activity);
            Log.d(SpaceUtils.TAG, "Biometric Available: " + available);
            security.setVisible(available);
            biometricPreference.setEnabled(available);
            biometricPreference.setSelectable(available);
            biometricPreference.setVisible(available);
            biometricPreference.setChecked(BiometricUtils.isBiometricUnlockEnabled(activity, alias));

            long size = BCAndroidUtils.getCacheSize(activity);
            Log.d(SpaceUtils.TAG, "Cache Size: " + size);
            cacheSizePreference.setSummary(CommonUtils.sizeToString(size));
            cachePurgePreference.setEnabled(size > 0L);

            appVersionPreference.setSummary(BuildConfig.BUILD_TYPE + "-" + BuildConfig.VERSION_NAME);
        }
    }
}
