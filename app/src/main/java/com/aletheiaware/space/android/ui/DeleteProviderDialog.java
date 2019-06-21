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

import com.aletheiaware.space.android.R;

public abstract class DeleteProviderDialog {

    private final Activity activity;
    private AlertDialog dialog;

    public DeleteProviderDialog(Activity activity) {
        this.activity = activity;
    }

    public void create() {
        View deleteView = View.inflate(activity, R.layout.dialog_delete_provider, null);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.delete_provider);
        ab.setView(deleteView);
        ab.setPositiveButton(R.string.delete_provider_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                onDelete(dialog);
            }
        });
        dialog = ab.show();
    }

    public abstract void onDelete(DialogInterface dialog);

    public AlertDialog getDialog() {
        return dialog;
    }
}