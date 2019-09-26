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
import android.view.View;
import android.widget.TextView;

import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.android.ChargeAdapter;
import com.aletheiaware.space.android.InvoiceAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.UsageRecordAdapter;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public abstract class RegistrarSubscriptionDialog {

    private final Activity activity;
    private final Registrar registrar;
    private final String registration;
    private final String subscription;
    private final InvoiceAdapter invoiceAdapter;
    private final ChargeAdapter chargeAdapter;
    private final UsageRecordAdapter usageRecordAdapter;
    private AlertDialog dialog;

    public RegistrarSubscriptionDialog(Activity activity, Registrar registrar, String registration, String subscription, InvoiceAdapter invoiceAdapter, ChargeAdapter chargeAdapter, UsageRecordAdapter usageRecordAdapter) {
        this.activity = activity;
        this.registrar = registrar;
        this.registration = registration;
        this.subscription = subscription;
        this.invoiceAdapter = invoiceAdapter;
        this.chargeAdapter = chargeAdapter;
        this.usageRecordAdapter = usageRecordAdapter;
    }

    public void create() {
        View subscriptionView = View.inflate(activity, R.layout.dialog_subscription_registrar, null);
        // Registrar Alias TextView
        final TextView registrarAliasText = subscriptionView.findViewById(R.id.registrar_alias);
        registrarAliasText.setText(registrar.getMerchant().getAlias());
        // Registrar Registration TextView
        final TextView registrarRegistrationText = subscriptionView.findViewById(R.id.registrar_registration_id);
        registrarRegistrationText.setText(registration);
        // Registrar Subscription TextView
        final TextView registrarSubscriptionText = subscriptionView.findViewById(R.id.registrar_subscription_id);
        registrarSubscriptionText.setText(subscription);
        // Registrar Subscription Invoice RecyclerView
        final RecyclerView invoiceRecycler = subscriptionView.findViewById(R.id.registrar_subscription_invoices);
        invoiceRecycler.setLayoutManager(new LinearLayoutManager(activity));
        invoiceRecycler.setAdapter(invoiceAdapter);
        // Registrar Subscription Charge RecyclerView
        final RecyclerView chargeRecycler = subscriptionView.findViewById(R.id.registrar_subscription_charges);
        chargeRecycler.setLayoutManager(new LinearLayoutManager(activity));
        chargeRecycler.setAdapter(chargeAdapter);
        // Registrar Subscription Usage Records RecyclerView
        final RecyclerView usageRecordRecycler = subscriptionView.findViewById(R.id.registrar_subscription_usage_records);
        usageRecordRecycler.setLayoutManager(new LinearLayoutManager(activity));
        usageRecordRecycler.setAdapter(usageRecordAdapter);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.title_dialog_subscription_registrar);
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
