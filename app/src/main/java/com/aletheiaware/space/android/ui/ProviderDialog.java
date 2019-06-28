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
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.aletheiaware.space.android.R;

public abstract class ProviderDialog {

    private final Activity activity;
    private final String provider;
    private final String registrationId;
    private final String subscriptionStorageId;
    private final String subscriptionMiningId;
    private AlertDialog dialog;

    public ProviderDialog(Activity activity, String provider, String registrationId, String subscriptionStorageId, String subscriptionMiningId) {
        this.activity = activity;
        this.provider = provider;
        this.registrationId = registrationId;
        this.subscriptionStorageId = subscriptionStorageId;
        this.subscriptionMiningId = subscriptionMiningId;
    }

    public void create() {
        View providerView = View.inflate(activity, R.layout.dialog_provider, null);
        final Button registerButton = providerView.findViewById(R.id.provider_register_button);
        final TextView registrationIdLabel = providerView.findViewById(R.id.provider_registration_id_label);
        final TextView registrationIdText = providerView.findViewById(R.id.provider_registration_id_text);
        if (registrationId == null || registrationId.isEmpty()) {
            registerButton.setVisibility(View.VISIBLE);
            registerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onRegister();
                }
            });
            registrationIdLabel.setVisibility(View.GONE);
            registrationIdText.setVisibility(View.GONE);
        } else {
            registerButton.setVisibility(View.GONE);
            registrationIdLabel.setVisibility(View.VISIBLE);
            registrationIdText.setVisibility(View.VISIBLE);
            registrationIdText.setText(registrationId);
        }
        final Button subscribeStorageButton = providerView.findViewById(R.id.provider_subscribe_storage_button);
        final TextView subscriptionStorageIdLabel = providerView.findViewById(R.id.provider_subscription_storage_id_label);
        final TextView subscriptionStorageIdText = providerView.findViewById(R.id.provider_subscription_storage_id_text);
        if (subscriptionStorageId == null || subscriptionStorageId.isEmpty()) {
            subscribeStorageButton.setVisibility(View.VISIBLE);
            subscribeStorageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onSubscribeStorage();
                }
            });
            subscriptionStorageIdLabel.setVisibility(View.GONE);
            subscriptionStorageIdText.setVisibility(View.GONE);
        } else {
            subscribeStorageButton.setVisibility(View.GONE);
            subscriptionStorageIdLabel.setVisibility(View.VISIBLE);
            subscriptionStorageIdText.setVisibility(View.VISIBLE);
            subscriptionStorageIdText.setText(subscriptionStorageId);
        }
        final Button subscribeMiningButton = providerView.findViewById(R.id.provider_subscribe_mining_button);
        final TextView subscriptionMiningIdLabel = providerView.findViewById(R.id.provider_subscription_mining_id_label);
        final TextView subscriptionMiningIdText = providerView.findViewById(R.id.provider_subscription_mining_id_text);
        if (registrationId == null || registrationId.isEmpty()) {
            subscribeMiningButton.setVisibility(View.VISIBLE);
            subscribeMiningButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onSubscribeMining();
                }
            });
            subscriptionMiningIdLabel.setVisibility(View.GONE);
            subscriptionMiningIdText.setVisibility(View.GONE);
        } else {
            subscribeMiningButton.setVisibility(View.GONE);
            subscriptionMiningIdLabel.setVisibility(View.VISIBLE);
            subscriptionMiningIdText.setVisibility(View.VISIBLE);
            subscriptionMiningIdText.setText(subscriptionMiningId);
        }
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(provider);
        ab.setView(providerView);
        ab.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        dialog = ab.show();
    }

    public abstract void onRegister();

    public abstract void onSubscribeStorage();

    public abstract void onSubscribeMining();

    public AlertDialog getDialog() {
        return dialog;
    }
}
