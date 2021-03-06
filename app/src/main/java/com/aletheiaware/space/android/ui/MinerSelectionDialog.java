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
import android.util.Log;
import android.view.View;
import android.widget.Spinner;

import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.space.SpaceProto.Miner;
import com.aletheiaware.space.android.MinerArrayAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils.CustomerIdCallback;
import com.aletheiaware.space.utils.SpaceUtils;

import java.util.Collections;
import java.util.Map;

import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;

public abstract class MinerSelectionDialog {

    private final Activity activity;
    private final String alias;
    private final MinerArrayAdapter minerArrayAdapter;
    private final Map<String, Registration> registrations;
    private AlertDialog dialog;
    private Spinner minerSpinner;

    public MinerSelectionDialog(Activity activity, String alias, MinerArrayAdapter minerArrayAdapter, Map<String, Registration> registrations) {
        this.activity = activity;
        this.alias = alias;
        this.minerArrayAdapter = minerArrayAdapter;
        this.registrations = registrations;
        // TODO show cost estimates
    }

    public AlertDialog getDialog() {
        return dialog;
    }

    public void create() {
        View view = View.inflate(activity, R.layout.dialog_miner_selection, null);
        minerSpinner = view.findViewById(R.id.miner_spinner);
        minerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        minerSpinner.setAdapter(minerArrayAdapter);
        minerSpinner.setSelection(0);// TODO determine selection index from sharedpreferences
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.title_dialog_miner_selection);
        ab.setIcon(R.drawable.bc_mine);
        ab.setView(view);
        ab.setPositiveButton(R.string.miner_selection_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                final String minerAlias = minerSpinner.getSelectedItem().toString();
                Log.d(SpaceUtils.TAG, "Selected Miner: " + minerAlias);
                final Miner miner = minerArrayAdapter.get(minerAlias);
                if (miner == null) {
                    // FIXME show error
                } else {
                    if (registrations.containsKey(minerAlias)) {
                        onSelect(miner);
                    } else {
                        SpaceAndroidUtils.registerCustomer(activity, Collections.singleton(miner.getMerchant()), alias, new CustomerIdCallback() {
                            @Override
                            public void onCustomerId(String merchant, String customerId) {
                                String subscriptionId = SpaceAndroidUtils.subscribeCustomer(activity, miner.getMerchant(), miner.getService(), alias, customerId);
                                if (subscriptionId != null && !subscriptionId.isEmpty()) {
                                    onSelect(miner);
                                }
                            }
                        });
                    }
                }
            }
        });
        ab.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onCancel();
            }
        });
        ab.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                onCancel();
            }
        });
        dialog = ab.show();
    }

    @UiThread
    public abstract void onSelect(Miner miner);

    @UiThread
    public abstract void onCancel();
}
