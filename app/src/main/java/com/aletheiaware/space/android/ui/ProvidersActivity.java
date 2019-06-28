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
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.space.android.ProvidersAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils.RegistrationCallback;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils.SubscriptionCallback;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
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
                            public void onLongClickProvider(final String provider) {
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
                            public void onClickProvider(final String provider, final String registrationId, String subscriptionStorageId, String subscriptionMiningId) {
                                new ProviderDialog(ProvidersActivity.this, provider, registrationId, subscriptionStorageId, subscriptionMiningId) {
                                    // TODO show prices, recent bills, charges, usage, etc
                                    // TODO unregister()?
                                    // TODO unsubscribe()?
                                    @Override
                                    public void onRegister() {
                                        SpaceAndroidUtils.registerSpaceCustomer(ProvidersActivity.this, provider, alias, new RegistrationCallback() {
                                            @Override
                                            public void onRegistered(String customerId) {
                                                Log.d(BCUtils.TAG, "Registration ID: " + customerId);
                                                refresh(provider);
                                            }
                                        });
                                    }

                                    @Override
                                    public void onSubscribeStorage() {
                                        new Thread() {
                                            @Override
                                            public void run() {
                                                SpaceAndroidUtils.subscribeSpaceStorageCustomer(ProvidersActivity.this, provider, alias, registrationId, new SubscriptionCallback() {
                                                    @Override
                                                    public void onSubscribed(String subscriptionId) {
                                                        Log.d(BCUtils.TAG, "Space Storage Subscription ID" + subscriptionId);
                                                        refresh(provider);
                                                    }
                                                });
                                                SpaceAndroidUtils.addStorageProviderPreference(ProvidersActivity.this, alias, provider);
                                            }
                                        }.start();
                                    }

                                    @Override
                                    public void onSubscribeMining() {
                                        new Thread() {
                                            @Override
                                            public void run() {
                                                SpaceAndroidUtils.subscribeSpaceMiningCustomer(ProvidersActivity.this, provider, alias, registrationId, new SubscriptionCallback() {
                                                    @Override
                                                    public void onSubscribed(String subscriptionId) {
                                                        Log.d(BCUtils.TAG, "Space Mining Subscription ID" + subscriptionId);
                                                        refresh(provider);
                                                    }
                                                });
                                                SpaceAndroidUtils.setRemoteMinerPreference(ProvidersActivity.this, alias, provider);
                                            }
                                        }.start();
                                    }
                                }.create();
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

    private void refresh() {
        Set<String> providers = SpaceAndroidUtils.getStorageProvidersPreference(this, BCAndroidUtils.getAlias());
        providers.addAll(Arrays.asList(getResources().getStringArray(R.array.provider_options)));
        for (String provider : providers) {
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
