/*
 * Copyright 2018 Aletheia Ware LLC
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
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.android.ui.StripeDialog;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.space.android.ProvidersAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.stripe.android.model.Token;

import java.io.IOException;
import java.net.InetAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class ProvidersActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProvidersAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_providers);

        recyclerView = findViewById(R.id.providers_recycler);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, layoutManager.getOrientation()));

        // Add Button
        final Button addButton = findViewById(R.id.providers_add_button);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new CustomProviderDialog(ProvidersActivity.this, SpaceAndroidUtils.getSpaceHostname()) {
                    @Override
                    protected void onCustomProvider(final String provider) {
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    InetAddress address = InetAddress.getByName(provider);
                                    if (address.isReachable(60*1000)) {
                                        String alias = BCAndroidUtils.getAlias();
                                        SpaceAndroidUtils.addStorageProviderPreference(ProvidersActivity.this, alias, provider);
                                        SpaceAndroidUtils.setRemoteMinerPreference(ProvidersActivity.this, alias, provider);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                adapter.notifyDataSetChanged();
                                                recyclerView.requestLayout();
                                            }
                                        });
                                    }
                                } catch (IOException e) {
                                    BCAndroidUtils.showErrorDialog(ProvidersActivity.this, getString(R.string.error_connection, provider), e);
                                }
                            }
                        }.start();
                    }
                }.create();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (BCAndroidUtils.isInitialized()) {
            final String alias = BCAndroidUtils.getAlias();
            new Thread() {
                @Override
                public void run() {
                    if (adapter == null) {
                        // Adapter
                        adapter = new ProvidersAdapter(ProvidersActivity.this) {
                            @Override
                            public void onClickProviderRemove(final String provider) {
                                new DeleteProviderDialog(ProvidersActivity.this) {
                                    @Override
                                    public void onDelete(DialogInterface dialog) {
                                        Set<String> providers = SpaceAndroidUtils.getStorageProvidersPreference(ProvidersActivity.this, alias);
                                        providers.remove(provider);
                                        BCAndroidUtils.setPreferences(ProvidersActivity.this, getString(R.string.preference_storage_providers_key, alias), providers);
                                        notifyDataSetChanged();
                                        recyclerView.requestLayout();
                                        dialog.dismiss();
                                    }
                                }.create();
                            }

                            @Override
                            public void onClickProviderRegistration(String provider, String registrationId) {
                                if (registrationId == null || registrationId.isEmpty()) {
                                    register(provider, "Space Registration", "/space-register");
                                } else {
                                    // TODO show recent bills, charges, usage, etc
                                    // TODO unregister()?
                                }
                            }

                            @Override
                            public void onClickProviderStorage(String provider, String registrationId, String subscriptionId) {
                                if (subscriptionId == null || subscriptionId.isEmpty()) {
                                    subscribe(provider, "/space-subscribe-storage", registrationId);
                                } else {
                                    // TODO unsubscribe()?
                                }
                                SpaceAndroidUtils.addStorageProviderPreference(ProvidersActivity.this, alias, provider);
                            }

                            @Override
                            public void onClickProviderMining(String provider, String registrationId, String subscriptionId) {
                                if (subscriptionId == null || subscriptionId.isEmpty()) {
                                    subscribe(provider, "/space-subscribe-mining", registrationId);
                                } else {
                                    // TODO unsubscribe()?
                                }
                                SpaceAndroidUtils.setRemoteMinerPreference(ProvidersActivity.this, alias, provider);
                            }
                        };
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                recyclerView.setAdapter(adapter);
                            }
                        });
                    }
                    if (adapter.isEmpty()) {
                        refresh();
                    }
                }
            }.start();
        }
    }

    private void register(final String provider, final String description, final String path) {
        new StripeDialog(this, description, null) {
            @Override
            public void onSubmit(final String email, final Token token) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            String alias = BCAndroidUtils.getAlias();
                            String registrationId = BCUtils.register("https://" + provider + path, alias, email, token.getId());
                            Log.d(BCUtils.TAG, "Registration ID: " + registrationId);
                            refresh(provider);
                        } catch (IOException e) {
                            BCAndroidUtils.showErrorDialog(ProvidersActivity.this, R.string.error_registering, e);
                        }
                    }
                }.start();
            }
        }.create();
    }

    private void subscribe(final String provider, final String path, final String customerId) {
        new Thread() {
            @Override
            public void run() {
                try {
                    String alias = BCAndroidUtils.getAlias();
                    String subscriptionId = BCUtils.subscribe("https://" + provider + path, alias, customerId);
                    Log.d(BCUtils.TAG, "Subscription ID" + subscriptionId);
                    refresh(provider);
                } catch (Exception e) {
                    BCAndroidUtils.showErrorDialog(ProvidersActivity.this, R.string.error_subscribing, e);
                }
            }
        }.start();
    }

    private void refresh() {
        for (String provider : SpaceAndroidUtils.getStorageProvidersPreference(this, BCAndroidUtils.getAlias())) {
            refresh(provider);
        }
    }

    private void refresh(final String provider) {
        Log.d(SpaceUtils.TAG, "Provider: " + provider);
        try {
            final String alias = BCAndroidUtils.getAlias();
            final KeyPair keys = BCAndroidUtils.getKeyPair();
            final Cache cache = BCAndroidUtils.getCache();
            final Network network = SpaceAndroidUtils.getStorageNetwork(ProvidersActivity.this, alias);
            final Registration registration = FinanceUtils.getRegistration(cache, network, provider, null, alias, keys);
            Log.d(SpaceUtils.TAG, "Registration: " + registration);
            final Subscription subscriptionStorage = FinanceUtils.getSubscription(cache, network, provider, null, alias, keys, getString(R.string.stripe_subscription_storage_product), getString(R.string.stripe_subscription_storage_plan));
            Log.d(SpaceUtils.TAG, "Storage Subscription: " + subscriptionStorage);
            final Subscription subscriptionMining = FinanceUtils.getSubscription(cache, network, provider, null, alias, keys, getString(R.string.stripe_subscription_mining_product), getString(R.string.stripe_subscription_mining_plan));
            Log.d(SpaceUtils.TAG, "Mining Subscription: " + subscriptionMining);
            String registrationId = (registration == null) ? null : registration.getCustomerId();
            String subscriptionStorageId = (subscriptionStorage == null) ? null : subscriptionStorage.getSubscriptionItemId();
            String subscriptionMiningId = (subscriptionMining == null) ? null : subscriptionMining.getSubscriptionItemId();
            adapter.addProvider(provider, registrationId, subscriptionStorageId, subscriptionMiningId);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adapter.notifyDataSetChanged();
                }
            });
        } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
            BCAndroidUtils.showErrorDialog(ProvidersActivity.this, R.string.error_read_finance_failed, e);
        }
    }
}
