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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.aletheiaware.space.SpaceProto.Miner;
import com.aletheiaware.space.android.ChargeAdapter;
import com.aletheiaware.space.android.InvoiceAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.UsageRecordAdapter;

public abstract class MiningSubscriptionDialog {

    private final Activity activity;
    private final Miner miner;
    private final String registration;
    private final String subscription;
    private final InvoiceAdapter invoiceAdapter;
    private final ChargeAdapter chargeAdapter;
    private final UsageRecordAdapter usageRecordAdapter;
    private AlertDialog dialog;

    public MiningSubscriptionDialog(Activity activity, Miner miner, String registration, String subscription, InvoiceAdapter invoiceAdapter, ChargeAdapter chargeAdapter, UsageRecordAdapter usageRecordAdapter) {
        this.activity = activity;
        this.miner = miner;
        this.registration = registration;
        this.subscription = subscription;
        this.invoiceAdapter = invoiceAdapter;
        this.chargeAdapter = chargeAdapter;
        this.usageRecordAdapter = usageRecordAdapter;
    }

    public void create() {
        View subscriptionView = View.inflate(activity, R.layout.dialog_subscription_mining, null);
        // Miner Alias TextView
        final TextView minerAliasText = subscriptionView.findViewById(R.id.miner_alias);
        minerAliasText.setText(miner.getMerchant().getAlias());
        // Miner Registration TextView
        final TextView minerRegistrationText = subscriptionView.findViewById(R.id.miner_registration_id);
        minerRegistrationText.setText(registration);
        // Miner Subscription TextView
        final TextView minerSubscriptionText = subscriptionView.findViewById(R.id.miner_subscription_id);
        minerSubscriptionText.setText(subscription);
        // Miner Subscription Invoice RecyclerView
        final RecyclerView invoiceRecycler = subscriptionView.findViewById(R.id.miner_subscription_invoices);
        invoiceRecycler.setLayoutManager(new LinearLayoutManager(activity));
        invoiceRecycler.setAdapter(invoiceAdapter);
        // Miner Subscription Charge RecyclerView
        final RecyclerView chargeRecycler = subscriptionView.findViewById(R.id.miner_subscription_charges);
        chargeRecycler.setLayoutManager(new LinearLayoutManager(activity));
        chargeRecycler.setAdapter(chargeAdapter);
        // Miner Subscription Usage Records RecyclerView
        final RecyclerView usageRecordRecycler = subscriptionView.findViewById(R.id.miner_subscription_usage_records);
        usageRecordRecycler.setLayoutManager(new LinearLayoutManager(activity));
        usageRecordRecycler.setAdapter(usageRecordAdapter);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.title_dialog_subscription_mining);
        ab.setView(subscriptionView);
        ab.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        dialog = ab.show();
    }

    public AlertDialog getDialog() {
        return dialog;
    }
}
