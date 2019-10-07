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

import android.os.Bundle;
import android.util.Log;

import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.finance.FinanceProto.Charge;
import com.aletheiaware.finance.FinanceProto.Invoice;
import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.finance.FinanceProto.UsageRecord;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.finance.utils.FinanceUtils.ChargeCallback;
import com.aletheiaware.finance.utils.FinanceUtils.InvoiceCallback;
import com.aletheiaware.finance.utils.FinanceUtils.UsageRecordCallback;
import com.aletheiaware.space.SpaceProto.Miner;
import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.android.ChargeAdapter;
import com.aletheiaware.space.android.InvoiceAdapter;
import com.aletheiaware.space.android.MinerRecyclerAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.RegistrarRecyclerAdapter;
import com.aletheiaware.space.android.UsageRecordAdapter;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils.CustomerIdCallback;
import com.aletheiaware.space.utils.SpaceUtils;
import com.aletheiaware.space.utils.SpaceUtils.MinerCallback;
import com.aletheiaware.space.utils.SpaceUtils.RegistrarCallback;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ProvidersActivity extends AppCompatActivity {

    private String alias;
    private KeyPair keys;
    private Cache cache;
    private Network network;

    private Map<String, Registrar> registrarMap;
    private Map<String, Miner> minerMap;

    private RecyclerView registrarsRecycler;
    private RecyclerView minerRecycler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_providers);

        registrarsRecycler = findViewById(R.id.providers_registrars_recycler);
        registrarsRecycler.setLayoutManager(new LinearLayoutManager(this));

        minerRecycler = findViewById(R.id.providers_miner_recycler);
        minerRecycler.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (BCAndroidUtils.isInitialized()) {
            alias = BCAndroidUtils.getAlias();
            keys = BCAndroidUtils.getKeyPair();
            cache = BCAndroidUtils.getCache();
            new Thread() {
                @Override
                public void run() {
                    // TODO refresh information after onRegister or onSubscribe
                    network = SpaceAndroidUtils.getSpaceNetwork();

                    final Map<String, Registration> registrationMap = new HashMap<>();
                    try {
                        registrationMap.putAll(SpaceAndroidUtils.getRegistrations(alias, keys, cache, network));
                    } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                        // TODO CommonAndroidUtils.showError() - Error reading registrations
                        e.printStackTrace();
                    }
                    Log.d(SpaceUtils.TAG, "Registrations: " + registrationMap.keySet());

                    final Map<String, Subscription> subscriptionMap = new HashMap<>();
                    try {
                        subscriptionMap.putAll(SpaceAndroidUtils.getSubscriptions(alias, keys, cache, network));
                    } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                        // TODO CommonAndroidUtils.showError() - Error reading subscriptions
                        e.printStackTrace();
                    }
                    Log.d(SpaceUtils.TAG, "Subscriptions: " + subscriptionMap.keySet());

                    // Get all Registrars
                    final RegistrarRecyclerAdapter registrarAdapter = new RegistrarRecyclerAdapter(ProvidersActivity.this) {
                        @Override
                        public void onRegistrarSelected(Registrar registrar, Registration registration, Subscription subscription) {
                            showRegistrar(registrar, registration, subscription);
                        }
                    };
                    registrarMap = new HashMap<>();
                    try {
                        SpaceUtils.readRegistrars(SpaceUtils.getRegistrarChannel(), cache, network, null, new RegistrarCallback() {
                            @Override
                            public boolean onRegistrar(BlockEntry blockEntry, Registrar registrar) {
                                String key = registrar.getMerchant().getAlias();
                                if (!registrarMap.containsKey(key)) {
                                    registrarMap.put(key, registrar);
                                }
                                Registration r = registrationMap.get(key);
                                Subscription s = subscriptionMap.get(key+registrar.getService().getProductId()+registrar.getService().getPlanId());
                                registrarAdapter.addRegistrar(registrar, r, s);
                                return true;
                            }
                        });
                    } catch (IOException e) {
                        // TODO CommonAndroidUtils.showError() - Error reading registrars
                        e.printStackTrace();
                    }
                    Log.d(SpaceUtils.TAG, "Registrars: " + registrarMap.keySet());

                    // Get all Miners
                    final MinerRecyclerAdapter minerAdapter = new MinerRecyclerAdapter(ProvidersActivity.this) {
                        @Override
                        public void onMinerSelected(Miner miner, Registration registration, Subscription subscription) {
                            showMiner(miner, registration, subscription);
                        }
                    };
                    minerMap = new HashMap<>();
                    try {
                        SpaceUtils.readMiners(SpaceUtils.getMinerChannel(), cache, network, null, new MinerCallback() {
                            @Override
                            public boolean onMiner(BlockEntry blockEntry, Miner miner) {
                                String key = miner.getMerchant().getAlias();
                                if (!minerMap.containsKey(key)) {
                                    minerMap.put(key, miner);
                                    Registration r = registrationMap.get(key);
                                    Subscription s = subscriptionMap.get(key + miner.getService().getProductId() + miner.getService().getPlanId());
                                    minerAdapter.addMiner(miner, r, s);
                                }
                                return true;
                            }
                        });
                    } catch (IOException e) {
                        // TODO CommonAndroidUtils.showError() - Error reading miners
                        e.printStackTrace();
                    }
                    Log.d(SpaceUtils.TAG, "Miners: " + minerMap.keySet());

                    // Update UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            registrarsRecycler.setAdapter(registrarAdapter);
                            minerRecycler.setAdapter(minerAdapter);
                        }
                    });
                }
            }.start();
        }
    }

    private void showRegistrar(final Registrar registrar, final Registration registration, Subscription subscription) {
        final String registrarAlias = registrar.getMerchant().getAlias();
        final InvoiceAdapter invoiceAdapter = new InvoiceAdapter(ProvidersActivity.this) {
            @Override
            public void onSelection(ByteString recordHash, Invoice invoice) {
                // TODO show selected invoice in new dialog
            }
        };
        final ChargeAdapter chargeAdapter = new ChargeAdapter(ProvidersActivity.this) {
            @Override
            public void onSelection(ByteString recordHash, Charge charge) {
                // TODO show selected charge in new dialog
            }
        };
        final UsageRecordAdapter usageRecordAdapter = new UsageRecordAdapter(ProvidersActivity.this) {
            @Override
            public void onSelection(ByteString recordHash, UsageRecord usageRecord) {
                // TODO show selected usage record in new dialog
            }
        };
        new RegistrarSubscriptionDialog(ProvidersActivity.this, registrar, registration, subscription, invoiceAdapter, chargeAdapter, usageRecordAdapter) {
            @Override
            public void onRegister() {
                SpaceAndroidUtils.registerCustomer(ProvidersActivity.this, registrar.getMerchant(), alias, new CustomerIdCallback() {
                    @Override
                    public void onCustomerId(String customerId) {
                        String storageSubscriptionId = SpaceAndroidUtils.subscribeCustomer(ProvidersActivity.this, registrar.getMerchant(), registrar.getService(), alias, customerId);
                        // TODO show success dialog with customerId and subscriptionId
                    }
                });
            }

            @Override
            public void onSubscribe() {
                new Thread() {
                    @Override
                    public void run() {
                        String storageSubscriptionId = SpaceAndroidUtils.subscribeCustomer(ProvidersActivity.this, registrar.getMerchant(), registrar.getService(), alias, registration.getCustomerId());
                        if (storageSubscriptionId != null && !storageSubscriptionId.isEmpty()) {
                            // TODO show success dialog with subscriptionId
                        }
                    }
                }.start();
            }
        }.create();
        final String[] productId = {null};
        final String[] planId = {null};
        if (subscription != null) {
            productId[0] = subscription.getProductId();
            planId[0] = subscription.getPlanId();
        }
        new Thread() {
            @Override
            public void run() {
                try {
                    FinanceUtils.readInvoices(SpaceUtils.SPACE_INVOICE, cache, network, registrarAlias, null, alias, keys, productId[0], planId[0], new InvoiceCallback() {
                        @Override
                        public boolean onInvoice(BlockEntry entry, Invoice invoice) {
                            invoiceAdapter.addInvoice(entry.getRecordHash(), entry.getRecord().getTimestamp(), invoice);
                            return true;
                        }
                    });
                } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
                try {
                    FinanceUtils.readCharges(SpaceUtils.SPACE_CHARGE, cache, network, registrarAlias, null, alias, keys, productId[0], planId[0], new ChargeCallback() {
                        @Override
                        public boolean onCharge(BlockEntry entry, Charge charge) {
                            chargeAdapter.addCharge(entry.getRecordHash(), entry.getRecord().getTimestamp(), charge);
                            return true;
                        }
                    });
                } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
                try {
                    FinanceUtils.readUsageRecords(SpaceUtils.SPACE_USAGE_RECORD, cache, network, registrarAlias, null, alias, keys, productId[0], planId[0], new UsageRecordCallback() {
                        @Override
                        public boolean onUsageRecord(BlockEntry entry, UsageRecord usageRecord) {
                            usageRecordAdapter.addUsageRecord(entry.getRecordHash(), entry.getRecord().getTimestamp(), usageRecord);
                            return true;
                        }
                    });
                } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void showMiner(final Miner miner, final Registration registration, final Subscription subscription) {
        final String minerAlias = miner.getMerchant().getAlias();
        final InvoiceAdapter invoiceAdapter = new InvoiceAdapter(ProvidersActivity.this) {
            @Override
            public void onSelection(ByteString recordHash, Invoice invoice) {
                // TODO show selected invoice in new dialog
            }
        };
        final ChargeAdapter chargeAdapter = new ChargeAdapter(ProvidersActivity.this) {
            @Override
            public void onSelection(ByteString recordHash, Charge charge) {
                // TODO show selected charge in new dialog
            }
        };
        final UsageRecordAdapter usageRecordAdapter = new UsageRecordAdapter(ProvidersActivity.this) {
            @Override
            public void onSelection(ByteString recordHash, UsageRecord usageRecord) {
                // TODO show selected usage record in new dialog
            }
        };
        new MiningSubscriptionDialog(ProvidersActivity.this, miner, registration, subscription, invoiceAdapter, chargeAdapter, usageRecordAdapter) {
            @Override
            public void onRegister() {
                SpaceAndroidUtils.registerCustomer(ProvidersActivity.this, miner.getMerchant(), alias, new CustomerIdCallback() {
                    @Override
                    public void onCustomerId(String customerId) {
                        String miningSubscriptionId = SpaceAndroidUtils.subscribeCustomer(ProvidersActivity.this, miner.getMerchant(), miner.getService(), alias, customerId);
                        String storageSubscriptionId = null;
                        Registrar registrar = registrarMap.get(minerAlias);
                        if (registrar != null) {
                            storageSubscriptionId = SpaceAndroidUtils.subscribeCustomer(ProvidersActivity.this, registrar.getMerchant(), registrar.getService(), alias, customerId);
                        }
                        // TODO show success dialog with customerId and subscriptionIds
                    }
                });
            }

            @Override
            public void onSubscribe() {
                new Thread() {
                    @Override
                    public void run() {
                        String miningSubscriptionId = SpaceAndroidUtils.subscribeCustomer(ProvidersActivity.this, miner.getMerchant(), miner.getService(), alias, registration.getCustomerId());
                        String storageSubscriptionId = null;
                        Registrar registrar = registrarMap.get(minerAlias);
                        if (registrar != null) {
                            storageSubscriptionId = SpaceAndroidUtils.subscribeCustomer(ProvidersActivity.this, registrar.getMerchant(), registrar.getService(), alias, registration.getCustomerId());
                        }
                        if (miningSubscriptionId != null && !miningSubscriptionId.isEmpty()) {
                            // TODO show success dialog with subscriptionIds
                        }
                    }
                }.start();
            }
        }.create();
        final String[] productId = {null};
        final String[] planId = {null};
        if (subscription != null) {
            productId[0] = subscription.getProductId();
            planId[0] = subscription.getPlanId();
        }
        new Thread() {
            @Override
            public void run() {
                try {
                    FinanceUtils.readInvoices(SpaceUtils.SPACE_INVOICE, cache, network, minerAlias, null, alias, keys, productId[0], planId[0], new InvoiceCallback() {
                        @Override
                        public boolean onInvoice(BlockEntry entry, Invoice invoice) {
                            invoiceAdapter.addInvoice(entry.getRecordHash(), entry.getRecord().getTimestamp(), invoice);
                            return true;
                        }
                    });
                } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
                try {
                    FinanceUtils.readCharges(SpaceUtils.SPACE_CHARGE, cache, network, minerAlias, null, alias, keys, productId[0], planId[0], new ChargeCallback() {
                        @Override
                        public boolean onCharge(BlockEntry entry, Charge charge) {
                            chargeAdapter.addCharge(entry.getRecordHash(), entry.getRecord().getTimestamp(), charge);
                            return true;
                        }
                    });
                } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
                try {
                    FinanceUtils.readUsageRecords(SpaceUtils.SPACE_USAGE_RECORD, cache, network, minerAlias, null, alias, keys, productId[0], planId[0], new UsageRecordCallback() {
                        @Override
                        public boolean onUsageRecord(BlockEntry entry, UsageRecord usageRecord) {
                            usageRecordAdapter.addUsageRecord(entry.getRecordHash(), entry.getRecord().getTimestamp(), usageRecord);
                            return true;
                        }
                    });
                } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
