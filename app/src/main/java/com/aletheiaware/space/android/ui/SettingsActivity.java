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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.aletheiaware.bc.android.ui.PasswordUnlockDialog;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.android.utils.BiometricUtils;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.common.utils.CommonUtils;
import com.aletheiaware.space.android.BuildConfig;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.utils.SpaceUtils;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.concurrent.CountDownLatch;

import javax.crypto.NoSuchPaddingException;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

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
        fragment.setAlias(BCAndroidUtils.getAlias());
        fragment.update();
    }

    public static class SettingsPreferenceFragment extends PreferenceFragmentCompat {

        private PreferenceCategory generalCategory;
        private PreferenceCategory securityCategory;
        private PreferenceCategory cacheCategory;
        private PreferenceCategory aboutCategory;
        private ListPreference sortPreference;
        private CheckBoxPreference biometricPreference;
        private Preference cacheSizePreference;
        private Preference cacheCopyPreference;
        private Preference cachePurgePreference;
        private Preference versionPreference;
        private Preference supportPreference;
        private Preference morePreference;

        private String alias;

        public void setAlias(String alias) {
            this.alias = alias;
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String key) {
            Context context = getPreferenceManager().getContext();
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);

            generalCategory = new PreferenceCategory(context);
            generalCategory.setTitle(R.string.preference_general_title);
            screen.addPreference(generalCategory);

            sortPreference = new ListPreference(context);
            sortPreference.setTitle(R.string.preference_sort_title);
            sortPreference.setIcon(R.drawable.sort);
            sortPreference.setSummary(R.string.preference_sort_description);
            // FIXME doesn't show current preference
            sortPreference.setEntries(R.array.preference_sort_options);
            sortPreference.setEntryValues(R.array.preference_sort_values);
            generalCategory.addPreference(sortPreference);

            securityCategory = new PreferenceCategory(context);
            securityCategory.setTitle(R.string.preference_security_title);
            screen.addPreference(securityCategory);

            biometricPreference = new CheckBoxPreference(context);
            biometricPreference.setTitle(R.string.preference_biometric_title);
            biometricPreference.setIcon(R.drawable.security);
            biometricPreference.setSummary(R.string.preference_biometric_description);
            biometricPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    final FragmentActivity activity = getActivity();
                    if (activity == null) {
                        return false;
                    }
                    if ((Boolean) o) {
                        new PasswordUnlockDialog(activity, alias) {
                            @Override
                            public void onUnlock(DialogInterface dialog, char[] password) {
                                dialog.dismiss();
                                try {
                                    final CountDownLatch latch = new CountDownLatch(1);
                                    BiometricUtils.enableBiometricUnlock(activity, alias, password, latch);
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            try {
                                                latch.await();
                                            } catch (InterruptedException ignored) {
                                            }
                                            update();
                                        }
                                    }.start();
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
            securityCategory.addPreference(biometricPreference);

            cacheCategory = new PreferenceCategory(context);
            cacheCategory.setTitle(R.string.preference_cache_title);
            screen.addPreference(cacheCategory);

            cacheSizePreference = new Preference(context);
            cacheSizePreference.setTitle(R.string.preference_cache_size_title);
            cacheSizePreference.setIcon(R.drawable.cache);
            cacheSizePreference.setShouldDisableView(true);
            cacheSizePreference.setEnabled(false);
            cacheSizePreference.setSelectable(false);
            cacheCategory.addPreference(cacheSizePreference);

            cacheCopyPreference = new Preference(context);
            cacheCopyPreference.setTitle(R.string.preference_cache_copy_title);
            cacheCopyPreference.setIcon(R.drawable.copy);
            cacheCopyPreference.setSummary(R.string.preference_cache_copy_description);
            cacheCopyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    boolean result = BCAndroidUtils.copyCache(getContext());
                    update();
                    return result;
                }
            });
            cacheCategory.addPreference(cacheCopyPreference);

            cachePurgePreference = new Preference(context);
            cachePurgePreference.setTitle(R.string.preference_cache_purge_title);
            cachePurgePreference.setIcon(R.drawable.delete);
            cachePurgePreference.setSummary(R.string.preference_cache_purge_description);
            cachePurgePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    boolean result = BCAndroidUtils.purgeCache(getContext());
                    update();
                    return result;
                }
            });
            cacheCategory.addPreference(cachePurgePreference);

            aboutCategory = new PreferenceCategory(context);
            aboutCategory.setTitle(R.string.preference_about_title);
            screen.addPreference(aboutCategory);

            versionPreference = new Preference(context);
            versionPreference.setTitle(R.string.preference_version_title);
            versionPreference.setIcon(R.drawable.info);
            versionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.aletheiaware.space.android"));
                    startActivity(intent);
                    return true;
                }
            });
            aboutCategory.addPreference(versionPreference);

            supportPreference = new Preference(context);
            supportPreference.setTitle(R.string.preference_support_title);
            supportPreference.setIcon(R.drawable.support);
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
            aboutCategory.addPreference(supportPreference);

            morePreference = new Preference(context);
            morePreference.setTitle(R.string.preference_more_title);
            morePreference.setIcon(R.drawable.aletheia_ware_llc_logo_small);
            morePreference.setSummary(R.string.preference_more_description);
            morePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/developer?id=Aletheia+Ware+LLC"));
                    startActivity(intent);
                    return true;
                }
            });
            aboutCategory.addPreference(morePreference);

            setPreferenceScreen(screen);
        }

        void update() {
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
            securityCategory.setVisible(available);
            biometricPreference.setEnabled(available);
            biometricPreference.setSelectable(available);
            biometricPreference.setVisible(available);
            biometricPreference.setChecked(BiometricUtils.isBiometricUnlockEnabled(activity, alias));

            long size = BCAndroidUtils.getCacheSize(activity);
            Log.d(SpaceUtils.TAG, "Cache Size: " + size);
            cacheSizePreference.setSummary(CommonUtils.binarySizeToString(size));
            cacheCopyPreference.setEnabled(size > 0L);
            cachePurgePreference.setEnabled(size > 0L);

            versionPreference.setSummary(BuildConfig.BUILD_TYPE + "-" + BuildConfig.VERSION_NAME);
        }
    }
}
