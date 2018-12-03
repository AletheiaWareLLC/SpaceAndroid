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

public abstract class UnlockDialog {

    private final Activity activity;
    private final String alias;

    public UnlockDialog(Activity activity, String alias) {
        this.activity = activity;
        this.alias = alias;
    }

    public void create() {
        View unlockView = View.inflate(activity, R.layout.dialog_unlock, null);
        final TextView aliasText = unlockView.findViewById(R.id.unlock_alias_text);
        aliasText.setText(alias);
        final EditText passwordText = unlockView.findViewById(R.id.unlock_password_text);
        passwordText.setFocusable(true);
        passwordText.setFocusableInTouchMode(true);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity);
        ab.setTitle(R.string.unlock_account);
        ab.setView(unlockView);
        ab.setPositiveButton(R.string.unlock_account_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                final int passwordLength = passwordText.length();
                final char[] password = new char[passwordLength];
                passwordText.getText().getChars(0, passwordLength, password, 0);
                onUnlock(password);
            }
        });
        final AlertDialog dialog = ab.show();
    }

    public abstract void onUnlock(char[] password);
}