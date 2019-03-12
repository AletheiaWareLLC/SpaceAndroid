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

package com.aletheiaware.space.android;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.android.utils.BiometricUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.NoSuchPaddingException;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_settings);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);

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
        private ListPreference minerPreference;
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

            minerPreference = (ListPreference) findPreference(getString(R.string.preference_miner_key));
            minerPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
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
                    boolean result = SpaceAndroidUtils.purgeCache(getContext());
                    update();
                    return result;
                }
            });

            biometricPreference = (CheckBoxPreference) findPreference(getString(R.string.preference_biometric_key));
            biometricPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    final FragmentActivity activity = getActivity();
                    final String alias = SpaceAndroidUtils.getAlias();
                    if ((Boolean) o) {
                        final AtomicBoolean success = new AtomicBoolean(false);
                        final CountDownLatch latch = new CountDownLatch(1);
                        new PasswordUnlockDialog(activity, alias) {
                            @Override
                            public void onUnlock(DialogInterface dialog, char[] password) {
                                dialog.dismiss();
                                try {
                                    BiometricUtils.enableBiometricUnlock(activity, alias, password);
                                    success.set(true);
                                } catch (InvalidAlgorithmParameterException e) {
                                    e.printStackTrace();
                                } catch (InvalidKeyException e) {
                                    e.printStackTrace();
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                } catch (NoSuchPaddingException e) {
                                    e.printStackTrace();
                                } catch (NoSuchProviderException e) {
                                    e.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            }
                        }.create();
                        return success.get();
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
                    support();
                    return true;
                }
            });

            update();
        }

        public void update() {
            final FragmentActivity activity = getActivity();
            final String alias = SpaceAndroidUtils.getAlias();

            long size = SpaceAndroidUtils.getCacheSize(activity);
            cacheSizePreference.setSummary(BCUtils.sizeToString(size));
            cachePurgePreference.setEnabled(size > 0L);

            boolean available = BiometricUtils.isBiometricUnlockAvailable(activity);
            biometricPreference.setEnabled(available);
            biometricPreference.setSelectable(available);
            biometricPreference.setVisible(available);
            biometricPreference.setChecked(BiometricUtils.isBiometricUnlockEnabled(activity, alias));

            appVersionPreference.setSummary(BuildConfig.BUILD_TYPE + "-" + BuildConfig.VERSION_NAME);
        }

        private void support() {
            StringBuilder sb = new StringBuilder();
            sb.append("======== App Info ========\n");
            sb.append("Build: ").append(BuildConfig.BUILD_TYPE).append("\n");
            sb.append("App ID: ").append(BuildConfig.APPLICATION_ID).append("\n");
            sb.append("Version: ").append(BuildConfig.VERSION_NAME).append("\n");
            sb.append("======== Device Info ========\n");
            sb.append("Board: ").append(Build.BOARD).append("\n");
            sb.append("Bootloader: ").append(Build.BOOTLOADER).append("\n");
            sb.append("Brand: ").append(Build.BRAND).append("\n");
            sb.append("Build ID: ").append(Build.ID).append("\n");
            sb.append("Device: ").append(Build.DEVICE).append("\n");
            sb.append("Display: ").append(Build.DISPLAY).append("\n");
            sb.append("Fingerprint: ").append(Build.FINGERPRINT).append("\n");
            sb.append("Hardware: ").append(Build.HARDWARE).append("\n");
            sb.append("Host: ").append(Build.HOST).append("\n");
            sb.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
            sb.append("Model: ").append(Build.MODEL).append("\n");
            sb.append("Product: ").append(Build.PRODUCT).append("\n");
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                sb.append("CPU ABI: ").append(Build.CPU_ABI).append("\n");
                sb.append("CPU ABI2: ").append(Build.CPU_ABI2).append("\n");
            } else {
                sb.append("Supported ABIs: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append("\n");
            }
            sb.append("Tags: ").append(Build.TAGS).append("\n");
            sb.append("Type: ").append(Build.TYPE).append("\n");
            sb.append("User: ").append(Build.USER).append("\n");
            Context context = getContext();
            if (context != null) {
                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                Map<String, ?> map = sharedPrefs.getAll();
                sb.append("======== Preferences ========\n");
                for (String key : map.keySet()) {
                    sb.append(key).append(":").append(map.get(key)).append("\n");
                }
            }
            Log.d(SpaceUtils.TAG, sb.toString());
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.support_email)});
            intent.putExtra(Intent.EXTRA_SUBJECT, "SPACE Support");
            intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(intent);
        }
    }
}
