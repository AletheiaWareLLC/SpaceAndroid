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
import android.support.v7.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.EditText;

public abstract class ImportAccountDialog {

    private final Activity activity;

    public ImportAccountDialog(Activity activity) {
        this.activity = activity;
    }

    public void create() {
        View accessView = View.inflate(activity, R.layout.dialog_import, null);
        final EditText aliasText = accessView.findViewById(R.id.import_alias_text);
        aliasText.setFocusable(true);
        aliasText.setFocusableInTouchMode(true);
        final EditText accessCodeText = accessView.findViewById(R.id.import_access_code_text);
        accessCodeText.setFocusableInTouchMode(true);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.import_account);
        ab.setView(accessView);
        ab.setPositiveButton(R.string.import_account_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                onImport(dialog, aliasText.getText().toString(), accessCodeText.getText().toString());
            }
        });
        final AlertDialog dialog = ab.show();
    }

    public abstract void onImport(DialogInterface dialog, String alias, String accessCode);
}