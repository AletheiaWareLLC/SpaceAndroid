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

public abstract class CustomHostDialog {

    private final Activity activity;
    private final String defaultHost;
    private AlertDialog dialog;

    public CustomHostDialog(Activity activity, String defaultHost) {
        this.activity = activity;
        this.defaultHost = defaultHost;
    }

    public void create() {
        View customHostView = View.inflate(activity, R.layout.dialog_custom_host, null);
        // Hostname TextView
        final TextView hostnameText = customHostView.findViewById(R.id.custom_hostname);
        hostnameText.setText(defaultHost);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.title_dialog_custom_host);
        ab.setView(customHostView);
        ab.setPositiveButton(R.string.custom_host_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                String hostname = hostnameText.getText().toString();
                onCustomHostname(hostname);
            }
        });
        dialog = ab.show();
    }

    protected abstract void onCustomHostname(String hostname);

    public AlertDialog getDialog() {
        return dialog;
    }
}
