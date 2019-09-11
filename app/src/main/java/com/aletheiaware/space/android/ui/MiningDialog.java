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

import android.app.Activity;
import android.content.DialogInterface;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.finance.utils.FinanceUtils.RegistrationCallback;
import com.aletheiaware.finance.utils.FinanceUtils.SubscriptionCallback;
import com.aletheiaware.space.SpaceProto.Miner;
import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.RegistrarsAdapter;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public abstract class MiningDialog extends Thread {

    private final Map<String, String> customerIds = new HashMap<>();
    private final Activity activity;
    private final String alias;
    private final KeyPair keys;
    private final Cache cache;
    private Network network;
    private AlertDialog dialog;
    private Spinner minerSpinner;
    private int selectedMinerIndex = 0;
    private Map<String, Miner> minerMap;
    private final ArrayAdapter<String> minerAdapter;
    private final RegistrarsAdapter registrarsAdapter;
    private TextView progressStatus;
    private AlertDialog progressDialog;

    public MiningDialog(Activity activity, String alias, KeyPair keys, Cache cache, Network network) {
        this.activity = activity;
        this.alias = alias;
        this.keys = keys;
        this.cache = cache;
        this.network = network;
        minerAdapter = new ArrayAdapter<>(activity, R.layout.miner_list_item);
        minerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        registrarsAdapter = new RegistrarsAdapter(activity);
        // TODO show selected miner and registrars with cost estimates
        start();
    }

    @Override
    public void run() {
        if (network == null) {
            network = SpaceAndroidUtils.getRegistrarNetwork(activity, alias);
        }
        try {
            minerMap = SpaceUtils.readMiners(SpaceUtils.getMinerChannel(), cache, network, null);
        } catch (IOException e) {
            // TODO CommonAndroidUtils.showErrorDialog(); Error reading miners
            e.printStackTrace();
        }
        Log.d(SpaceUtils.TAG, "Miners: " + minerMap.keySet());
        String minerPreference = SpaceAndroidUtils.getMinerPreference(activity, alias);
        Log.d(SpaceUtils.TAG, "Miner Preference: " + minerPreference);
        final Set<String> minerAliases = new HashSet<>();
        int index = 0;
        for (String alias : minerMap.keySet()) {
            minerAliases.add(alias);
            if (alias.equals(minerPreference)) {
                selectedMinerIndex = index;
            }
            index++;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                minerAdapter.addAll(minerAliases);
            }
        });
        Map<String, Registrar> registrarMap = null;
        try {
            registrarMap = SpaceUtils.readRegistrars(SpaceUtils.getRegistrarChannel(), cache, network, null);
            Log.d(SpaceUtils.TAG, "Registrars: " + registrarMap.keySet());
        } catch (IOException e) {
            // TODO CommonAndroidUtils.showErrorDialog(); Error reading registrars
            e.printStackTrace();
        }
        Set<String> registrarPreferences = SpaceAndroidUtils.getRegistrarsPreference(activity, alias);
        Log.d(SpaceUtils.TAG, "Registrar Preferences: " + registrarPreferences);
        registrarsAdapter.setRegistrars(registrarMap, registrarPreferences);
    }

    public AlertDialog getDialog() {
        return dialog;
    }

    public void create() {
        View mineView = View.inflate(activity, R.layout.dialog_mine, null);
        minerSpinner = mineView.findViewById(R.id.miner_spinner);
        minerSpinner.setAdapter(minerAdapter);
        minerSpinner.setSelection(selectedMinerIndex);
        RecyclerView registrarsRecycler = mineView.findViewById(R.id.registrars_recycler);
        registrarsRecycler.setLayoutManager(new LinearLayoutManager(activity));
        registrarsRecycler.setAdapter(registrarsAdapter);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.title_dialog_mine);
        ab.setIcon(R.drawable.bc_mine);
        ab.setView(mineView);
        ab.setPositiveButton(R.string.mine_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                View progressView = View.inflate(activity, R.layout.dialog_progress, null);
                ProgressBar progressBar = progressView.findViewById(R.id.progress_bar);
                progressBar.setIndeterminate(true);
                progressStatus = progressView.findViewById(R.id.progress_status);
                progressStatus.setVisibility(View.VISIBLE);
                progressDialog = new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
                        .setTitle(R.string.title_dialog_mining)
                        .setIcon(R.drawable.bc_mine)
                        .setCancelable(false)
                        .setView(progressView)
                        .show();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            setStatus("Loading Miner Information");
                            getMiner(new MinerCallback() {
                                @Override
                                public void onMiner(final Miner miner) {
                                    setStatus("Loading Registrars Information");
                                    final Map<String, Registrar> registrars = new HashMap<>();
                                    getRegistrars(new RegistrarCallback() {
                                        @Override
                                        public void onRegistrar(Registrar registrar) {
                                            registrars.put(registrar.getMerchant().getAlias(), registrar);
                                        }
                                    });
                                    setStatus("Starting Mining");
                                    onMine(miner, registrars);
                                }
                            });
                        } finally {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (progressDialog != null && progressDialog.isShowing()) {
                                        progressDialog.dismiss();
                                    }
                                }
                            });
                        }
                    }
                }.start();
            }
        });
        ab.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onMiningCancelled();
            }
        });
        ab.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                onMiningCancelled();
            }
        });
        dialog = ab.show();
    }

    @WorkerThread
    private void setStatus(final String s) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progressStatus != null) {
                    progressStatus.setText(s);
                }
            }
        });
    }

    @WorkerThread
    private void getMiner(MinerCallback callback) {
        String minerAlias = minerSpinner.getSelectedItem().toString();
        Log.d(SpaceUtils.TAG, "Selected Miner: " + minerAlias);
        Miner miner = minerMap.get(minerAlias);
        if (miner != null) {
            checkMinerRegistration(miner, callback);
        }
    }

    @WorkerThread
    private void checkMinerRegistration(final Miner miner, final MinerCallback callback)  {
        try {
            SpaceAndroidUtils.getRegistration(activity, cache, network, miner.getMerchant(), alias, keys, new RegistrationCallback() {
                @Override
                public void onRegistration(BlockEntry entry, Registration registration) {
                    customerIds.put(miner.getMerchant().getAlias(), registration.getCustomerId());
                    checkMinerSubscription(miner, registration.getCustomerId(), callback);
                }
            });
        } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
            // TODO CommonAndroidUtils.showErrorDialog(); Error getting registration information
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void checkMinerSubscription(final Miner miner, String customerId, final MinerCallback callback) {
        Log.d(SpaceUtils.TAG, "Miner Customer ID: " + customerId);
        try {
            SpaceAndroidUtils.getMinerSubscription(activity, cache, network, miner, alias, keys, customerId, new SubscriptionCallback() {
                @Override
                public void onSubscription(BlockEntry entry, Subscription subscription) {
                    callback.onMiner(miner);
                }
            });
        } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
            // TODO CommonAndroidUtils.showErrorDialog(); Error getting subscription information
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void getRegistrars(RegistrarCallback callback) {
        final Map<String, Registrar> registrars = registrarsAdapter.getSelectedRegistrars();
        for (String registrarAlias : registrars.keySet()) {
            Log.d(SpaceUtils.TAG, "Selected Registrar: " + registrarAlias);
            Registrar registrar = registrars.get(registrarAlias);
            if (registrar != null) {
                checkRegistrarRegistration(registrar, callback);
            }
        }
    }

    @WorkerThread
    private void checkRegistrarRegistration(final Registrar registrar, final RegistrarCallback callback) {
        try {
            String customerId = customerIds.get(registrar.getMerchant().getAlias());
            if (customerId == null) {
                SpaceAndroidUtils.getRegistration(activity, cache, network, registrar.getMerchant(), alias, keys, new RegistrationCallback() {
                    @Override
                    public void onRegistration(BlockEntry entry, Registration registration) {
                        checkRegistrarSubscription(registrar, registration.getCustomerId(), callback);
                    }
                });
            } else {
                checkRegistrarSubscription(registrar, customerId, callback);
            }
        } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
            // TODO CommonAndroidUtils.showErrorDialog(); Error getting registration information
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void checkRegistrarSubscription(final Registrar registrar, String customerId, final RegistrarCallback callback) {
        Log.d(SpaceUtils.TAG, "Registrar Customer ID: " + customerId);
        try {
            SpaceAndroidUtils.getRegistrarSubscription(activity, cache, network, registrar, alias, keys, customerId, new SubscriptionCallback() {
                @Override
                public void onSubscription(BlockEntry entry, Subscription subscription) {
                    callback.onRegistrar(registrar);
                }
            });
        } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
            // TODO CommonAndroidUtils.showErrorDialog(); Error getting subscription information
            e.printStackTrace();
        }
    }

    @WorkerThread
    public abstract void onMine(Miner miner, Map<String, Registrar> registrars);

    @UiThread
    public abstract void onMiningCancelled();

    private interface MinerCallback {
        void onMiner(Miner miner);
    }

    private interface RegistrarCallback {
        void onRegistrar(Registrar registrar);
    }
}
