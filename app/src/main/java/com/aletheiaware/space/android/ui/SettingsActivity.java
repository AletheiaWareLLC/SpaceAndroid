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
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.WorkerThread;
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

import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.android.ui.PasswordUnlockDialog;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.android.utils.BiometricCallback;
import com.aletheiaware.bc.android.utils.BiometricUtils;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.common.utils.CommonUtils;
import com.aletheiaware.finance.FinanceProto.Charge;
import com.aletheiaware.finance.FinanceProto.Invoice;
import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.finance.FinanceProto.UsageRecord;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.finance.utils.FinanceUtils.ChargeCallback;
import com.aletheiaware.finance.utils.FinanceUtils.InvoiceCallback;
import com.aletheiaware.finance.utils.FinanceUtils.RegistrationCallback;
import com.aletheiaware.finance.utils.FinanceUtils.SubscriptionCallback;
import com.aletheiaware.finance.utils.FinanceUtils.UsageRecordCallback;
import com.aletheiaware.space.SpaceProto.Miner;
import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.android.BuildConfig;
import com.aletheiaware.space.android.ChargeAdapter;
import com.aletheiaware.space.android.InvoiceAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.UsageRecordAdapter;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
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
        new Thread() {
            @Override
            public void run() {
                fragment.load();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fragment.update();
                    }
                });
            }
        }.start();
    }

    public static class SettingsPreferenceFragment extends PreferenceFragmentCompat {

        private PreferenceCategory generalCategory;
        private PreferenceCategory securityCategory;
        private PreferenceCategory networkCategory;
        private PreferenceCategory cacheCategory;
        private PreferenceCategory aboutCategory;
        private ListPreference sortPreference;
        private ListPreference minerPreference;
        private ListPreference registrarsPreference;
        private CheckBoxPreference biometricPreference;
        private Preference cacheSizePreference;
        private Preference cachePurgePreference;
        private Preference versionPreference;
        private Preference supportPreference;
        private Preference morePreference;

        private String alias;
        private KeyPair keys;
        private Cache cache;
        private Network network;
        private Map<String, Miner> minerMap;
        private Map<String, Registrar> registrarMap;

        @WorkerThread
        public void load() {
            alias = BCAndroidUtils.getAlias();
            keys = BCAndroidUtils.getKeyPair();
            cache = BCAndroidUtils.getCache();
            network = SpaceAndroidUtils.getRegistrarNetwork(getActivity(), alias);
            try {
                minerMap = SpaceUtils.readMiners(SpaceUtils.getMinerChannel(), cache, network, null);
            } catch (IOException e) {
                // TODO CommonAndroidUtils.showErrorDialog(); Error reading miners
                e.printStackTrace();
            }
            try {
                registrarMap = SpaceUtils.readRegistrars(SpaceUtils.getRegistrarChannel(), cache, network, null);
            } catch (IOException e) {
                // TODO CommonAndroidUtils.showErrorDialog(); Error reading registrars
                e.printStackTrace();
            }
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

            networkCategory = new PreferenceCategory(context);
            networkCategory.setTitle(R.string.preference_network_title);
            screen.addPreference(networkCategory);

            minerPreference = new ListPreference(context);
            minerPreference.setTitle(R.string.preference_miner_title);
            minerPreference.setIcon(R.drawable.bc_mine);
            minerPreference.setSummary(R.string.preference_miner_description);
            minerPreference.setDefaultValue(SpaceAndroidUtils.getSpaceHostname());
            minerPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    final FragmentActivity activity = getActivity();
                    if (activity == null) {
                        return false;
                    }
                    final String minerAlias = value.toString();
                    final Miner miner = minerMap.get(minerAlias);
                    if (miner == null) {
                        return false;
                    }
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                SpaceAndroidUtils.getRegistration(activity, cache, network, miner.getMerchant(), alias, keys, new RegistrationCallback() {
                                    @Override
                                    public void onRegistration(BlockEntry entry, final Registration registration) {
                                        try {
                                            SpaceAndroidUtils.getMinerSubscription(activity, cache, network, miner, alias, keys, registration.getCustomerId(), new SubscriptionCallback() {
                                                @Override
                                                public void onSubscription(BlockEntry entry, final Subscription subscription) {
                                                    final InvoiceAdapter invoiceAdapter = new InvoiceAdapter(activity) {
                                                        @Override
                                                        public void onSelection(ByteString recordHash, Invoice invoice) {
                                                            // TODO show selected invoice in new dialog
                                                        }
                                                    };
                                                    final ChargeAdapter chargeAdapter = new ChargeAdapter(activity) {
                                                        @Override
                                                        public void onSelection(ByteString recordHash, Charge charge) {
                                                            // TODO show selected charge in new dialog
                                                        }
                                                    };
                                                    final UsageRecordAdapter usageRecordAdapter = new UsageRecordAdapter(activity) {
                                                        @Override
                                                        public void onSelection(ByteString recordHash, UsageRecord usageRecord) {
                                                            // TODO show selected usage record in new dialog
                                                        }
                                                    };
                                                    activity.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            new MiningSubscriptionDialog(activity, miner, registration.getCustomerId(), subscription.getSubscriptionItemId(), invoiceAdapter, chargeAdapter, usageRecordAdapter) {
                                                            }.create();
                                                        }
                                                    });
                                                    try {
                                                        FinanceUtils.readInvoices(SpaceUtils.SPACE_INVOICE, cache, network, minerAlias, null, alias, keys, subscription.getProductId(), subscription.getPlanId(), new InvoiceCallback() {
                                                            @Override
                                                            public void onInvoice(BlockEntry entry, Invoice invoice) {
                                                                invoiceAdapter.addInvoice(entry.getRecordHash(), entry.getRecord().getTimestamp(), invoice);
                                                            }
                                                        });
                                                    } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                                                        e.printStackTrace();
                                                    }
                                                    try {
                                                        FinanceUtils.readCharges(SpaceUtils.SPACE_CHARGE, cache, network, minerAlias, null, alias, keys, subscription.getProductId(), subscription.getPlanId(), new ChargeCallback() {
                                                            @Override
                                                            public void onCharge(BlockEntry entry, Charge charge) {
                                                                chargeAdapter.addCharge(entry.getRecordHash(), entry.getRecord().getTimestamp(), charge);
                                                            }
                                                        });
                                                    } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                                                        e.printStackTrace();
                                                    }
                                                    try {
                                                        FinanceUtils.readUsageRecords(SpaceUtils.SPACE_USAGE_RECORD, cache, network, minerAlias, null, alias, keys, subscription.getProductId(), subscription.getPlanId(), new UsageRecordCallback() {
                                                            @Override
                                                            public void onUsageRecord(BlockEntry entry, UsageRecord usageRecord) {
                                                                usageRecordAdapter.addUsageRecord(entry.getRecordHash(), entry.getRecord().getTimestamp(), usageRecord);
                                                            }
                                                        });
                                                    } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            });
                                        } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                    return true;
                }
            });
            networkCategory.addPreference(minerPreference);

            registrarsPreference = new ListPreference(context);
            registrarsPreference.setTitle(R.string.preference_registrars_title);
            registrarsPreference.setIcon(R.drawable.bc_storage);
            registrarsPreference.setSummary(R.string.preference_registrars_description);
            registrarsPreference.setDefaultValue(SpaceAndroidUtils.getSpaceHostname());
            registrarsPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    final FragmentActivity activity = getActivity();
                    if (activity == null) {
                        return false;
                    }
                    final String registrarAlias = value.toString();
                    final Registrar registrar = registrarMap.get(registrarAlias);
                    if (registrar == null) {
                        return false;
                    }
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                SpaceAndroidUtils.getRegistration(activity, cache, network, registrar.getMerchant(), alias, keys, new RegistrationCallback() {
                                    @Override
                                    public void onRegistration(BlockEntry entry, final Registration registration) {
                                        try {
                                            SpaceAndroidUtils.getRegistrarSubscription(activity, cache, network, registrar, alias, keys, registration.getCustomerId(), new SubscriptionCallback() {
                                                @Override
                                                public void onSubscription(BlockEntry entry, final Subscription subscription) {
                                                    final InvoiceAdapter invoiceAdapter = new InvoiceAdapter(activity) {
                                                        @Override
                                                        public void onSelection(ByteString recordHash, Invoice invoice) {
                                                            // TODO show selected invoice in new dialog
                                                        }
                                                    };
                                                    final ChargeAdapter chargeAdapter = new ChargeAdapter(activity) {
                                                        @Override
                                                        public void onSelection(ByteString recordHash, Charge charge) {
                                                            // TODO show selected charge in new dialog
                                                        }
                                                    };
                                                    final UsageRecordAdapter usageRecordAdapter = new UsageRecordAdapter(activity) {
                                                        @Override
                                                        public void onSelection(ByteString recordHash, UsageRecord usageRecord) {
                                                            // TODO show selected usage record in new dialog
                                                        }
                                                    };
                                                    activity.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            new RegistrarSubscriptionDialog(activity, registrar, registration.getCustomerId(), subscription.getSubscriptionItemId(), invoiceAdapter, chargeAdapter, usageRecordAdapter) {
                                                            }.create();
                                                        }
                                                    });
                                                    try {
                                                        FinanceUtils.readInvoices(SpaceUtils.SPACE_INVOICE, cache, network, registrarAlias, null, alias, keys, subscription.getProductId(), subscription.getPlanId(), new InvoiceCallback() {
                                                            @Override
                                                            public void onInvoice(BlockEntry entry, Invoice invoice) {
                                                                invoiceAdapter.addInvoice(entry.getRecordHash(), entry.getRecord().getTimestamp(), invoice);
                                                            }
                                                        });
                                                    } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                                                        e.printStackTrace();
                                                    }
                                                    try {
                                                        FinanceUtils.readCharges(SpaceUtils.SPACE_CHARGE, cache, network, registrarAlias, null, alias, keys, subscription.getProductId(), subscription.getPlanId(), new ChargeCallback() {
                                                            @Override
                                                            public void onCharge(BlockEntry entry, Charge charge) {
                                                                chargeAdapter.addCharge(entry.getRecordHash(), entry.getRecord().getTimestamp(), charge);
                                                            }
                                                        });
                                                    } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                                                        e.printStackTrace();
                                                    }
                                                    try {
                                                        FinanceUtils.readUsageRecords(SpaceUtils.SPACE_USAGE_RECORD, cache, network, registrarAlias, null, alias, keys, subscription.getProductId(), subscription.getPlanId(), new UsageRecordCallback() {
                                                            @Override
                                                            public void onUsageRecord(BlockEntry entry, UsageRecord usageRecord) {
                                                                usageRecordAdapter.addUsageRecord(entry.getRecordHash(), entry.getRecord().getTimestamp(), usageRecord);
                                                            }
                                                        });
                                                    } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            });
                                        } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                    return true;
                }
            });
            networkCategory.addPreference(registrarsPreference);

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

        public void update() {
            if (alias == null || alias.isEmpty()) {
                sortPreference.setVisible(false);
            } else {
                sortPreference.setVisible(true);
                sortPreference.setKey(getString(R.string.preference_sort_key, alias));
                biometricPreference.setKey(getString(R.string.preference_biometric_key, alias));
                minerPreference.setKey(getString(R.string.preference_miner_key, alias));
                registrarsPreference.setKey(getString(R.string.preference_registrars_key, alias));
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

            String[] minerAliases = minerMap.keySet().toArray(new String[0]);
            minerPreference.setEntries(minerAliases);
            minerPreference.setEntryValues(minerAliases);

            String[] registrarAliases = registrarMap.keySet().toArray(new String[0]);
            registrarsPreference.setEntries(registrarAliases);
            registrarsPreference.setEntryValues(registrarAliases);

            long size = BCAndroidUtils.getCacheSize(activity);
            Log.d(SpaceUtils.TAG, "Cache Size: " + size);
            cacheSizePreference.setSummary(CommonUtils.binarySizeToString(size));
            cachePurgePreference.setEnabled(size > 0L);

            versionPreference.setSummary(BuildConfig.BUILD_TYPE + "-" + BuildConfig.VERSION_NAME);
        }
    }
}
