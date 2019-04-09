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

import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import com.aletheiaware.space.android.R;

public abstract class ExportKeysDialog {

    private final Activity activity;
    private final String alias;
    private final String accessCode;
    private AlertDialog dialog;

    public ExportKeysDialog(Activity activity, String alias, String accessCode) {
        this.activity = activity;
        this.alias = alias;
        this.accessCode = accessCode;
    }

    public void create() {
        View accessView = View.inflate(activity, R.layout.dialog_export, null);
        final TextView aliasText = accessView.findViewById(R.id.export_alias_text);
        aliasText.setText(alias);
        final TextView accessCodeText = accessView.findViewById(R.id.export_access_code_text);
        accessCodeText.setText(accessCode);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.export_keys);
        ab.setView(accessView);
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