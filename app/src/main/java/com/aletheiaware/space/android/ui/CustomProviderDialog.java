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
import android.widget.TextView;

import com.aletheiaware.space.android.R;

public abstract class CustomProviderDialog {

    private final Activity activity;
    private final String defaultValue;
    private AlertDialog dialog;

    public CustomProviderDialog(Activity activity, String defaultValue) {
        this.activity = activity;
        this.defaultValue = defaultValue;
    }

    public void create() {
        View view = View.inflate(activity, R.layout.dialog_custom_provider, null);
        // Provider Alias TextView
        final TextView providerAliasText = view.findViewById(R.id.custom_provider_alias);
        if (defaultValue != null && !defaultValue.isEmpty()) {
            providerAliasText.setText(defaultValue);
        }
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.title_dialog_custom_provider);
        ab.setView(view);
        ab.setPositiveButton(R.string.custom_provider_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                String provider = providerAliasText.getText().toString();
                onCustomProvider(provider);
            }
        });
        dialog = ab.show();
    }

    protected abstract void onCustomProvider(String provider);

    public AlertDialog getDialog() {
        return dialog;
    }
}
