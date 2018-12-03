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

package com.aletheiaware.space.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.w3c.dom.Text;

public abstract class ExportAccountDialog {

    private final Activity activity;
    private final String accessCode;

    public ExportAccountDialog(Activity activity, String accessCode) {
        this.activity = activity;
        this.accessCode = accessCode;
    }

    public void create() {
        View accessView = View.inflate(activity, R.layout.dialog_export, null);
        final TextView accessCodeText = accessView.findViewById(R.id.export_access_code_text);
        accessCodeText.setText(accessCode);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity);
        ab.setTitle(R.string.export_account);
        ab.setView(accessView);
        ab.setPositiveButton(R.string.export_account_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                onExport();
            }
        });
        final AlertDialog dialog = ab.show();
    }

    public abstract void onExport();
}