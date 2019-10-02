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
import android.widget.Button;
import android.widget.TextView;

import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.android.ChargeAdapter;
import com.aletheiaware.space.android.InvoiceAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.UsageRecordAdapter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public abstract class RegistrarSubscriptionDialog {

    private final Activity activity;
    private final Registrar registrar;
    private final Registration registration;
    private final Subscription subscription;
    private final InvoiceAdapter invoiceAdapter;
    private final ChargeAdapter chargeAdapter;
    private final UsageRecordAdapter usageRecordAdapter;
    private AlertDialog dialog;

    public RegistrarSubscriptionDialog(Activity activity, Registrar registrar, @Nullable Registration registration, @Nullable Subscription subscription, InvoiceAdapter invoiceAdapter, ChargeAdapter chargeAdapter, UsageRecordAdapter usageRecordAdapter) {
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
        final TextView registrarAliasText = subscriptionView.findViewById(R.id.registrar_alias);
        registrarAliasText.setText(registrar.getMerchant().getAlias());

        final Button registrarRegisterButton = subscriptionView.findViewById(R.id.registrar_register_button);
        registrarRegisterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRegister();
            }
        });
        final TextView registrarRegistrationTextLabel = subscriptionView.findViewById(R.id.registrar_registration_id_label);
        final TextView registrarRegistrationText = subscriptionView.findViewById(R.id.registrar_registration_id);
        final Button registrarSubscribeButton = subscriptionView.findViewById(R.id.registrar_subscribe_button);
        registrarSubscribeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSubscribe();
            }
        });
        final TextView registrarSubscriptionTextLabel = subscriptionView.findViewById(R.id.registrar_subscription_id_label);
        final TextView registrarSubscriptionText = subscriptionView.findViewById(R.id.registrar_subscription_id);
        final TextView invoiceRecyclerLabel = subscriptionView.findViewById(R.id.registrar_subscription_invoices_label);
        final RecyclerView invoiceRecycler = subscriptionView.findViewById(R.id.registrar_subscription_invoices);
        invoiceRecycler.setLayoutManager(new LinearLayoutManager(activity));
        invoiceRecycler.setAdapter(invoiceAdapter);
        final TextView chargeRecyclerLabel = subscriptionView.findViewById(R.id.registrar_subscription_charges_label);
        final RecyclerView chargeRecycler = subscriptionView.findViewById(R.id.registrar_subscription_charges);
        chargeRecycler.setLayoutManager(new LinearLayoutManager(activity));
        chargeRecycler.setAdapter(chargeAdapter);
        final TextView usageRecordRecyclerLabel = subscriptionView.findViewById(R.id.registrar_subscription_usage_records_label);
        final RecyclerView usageRecordRecycler = subscriptionView.findViewById(R.id.registrar_subscription_usage_records);
        usageRecordRecycler.setLayoutManager(new LinearLayoutManager(activity));
        usageRecordRecycler.setAdapter(usageRecordAdapter);

        if (registration == null) {
            registrarRegisterButton.setVisibility(View.VISIBLE);
            registrarRegistrationTextLabel.setVisibility(View.GONE);
            registrarRegistrationText.setVisibility(View.GONE);
            registrarSubscribeButton.setVisibility(View.GONE);
            registrarSubscriptionTextLabel.setVisibility(View.GONE);
            registrarSubscriptionText.setVisibility(View.GONE);
            invoiceRecyclerLabel.setVisibility(View.GONE);
            invoiceRecycler.setVisibility(View.GONE);
            chargeRecyclerLabel.setVisibility(View.GONE);
            chargeRecycler.setVisibility(View.GONE);
            usageRecordRecyclerLabel.setVisibility(View.GONE);
            usageRecordRecycler.setVisibility(View.GONE);
        } else {
            registrarRegisterButton.setVisibility(View.GONE);
            registrarRegistrationText.setText(registration.getCustomerId());
            registrarRegistrationTextLabel.setVisibility(View.VISIBLE);
            registrarRegistrationText.setVisibility(View.VISIBLE);
            invoiceRecyclerLabel.setVisibility(View.VISIBLE);
            invoiceRecycler.setVisibility(View.VISIBLE);
            chargeRecyclerLabel.setVisibility(View.VISIBLE);
            chargeRecycler.setVisibility(View.VISIBLE);
            usageRecordRecyclerLabel.setVisibility(View.VISIBLE);
            usageRecordRecycler.setVisibility(View.VISIBLE);
            if (subscription == null) {
                registrarSubscribeButton.setVisibility(View.VISIBLE);
                registrarSubscriptionTextLabel.setVisibility(View.GONE);
                registrarSubscriptionText.setVisibility(View.GONE);
            } else {
                registrarSubscribeButton.setVisibility(View.GONE);
                registrarSubscriptionText.setText(subscription.getSubscriptionItemId());
                registrarSubscriptionTextLabel.setVisibility(View.VISIBLE);
                registrarSubscriptionText.setVisibility(View.VISIBLE);
            }
        }
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

    public abstract void onRegister();

    public abstract void onSubscribe();
}
