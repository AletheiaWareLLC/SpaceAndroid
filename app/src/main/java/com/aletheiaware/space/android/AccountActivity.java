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

import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.aletheiaware.bc.utils.BCUtils;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

public class AccountActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private TextView aliasText;
    private Button switchButton;
    private Button exportButton;
    private Button deleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        // Toolbar
        toolbar = findViewById(R.id.account_toolbar);
        setSupportActionBar(toolbar);

        aliasText = findViewById(R.id.account_alias_text);

        switchButton = findViewById(R.id.account_switch_button);

        exportButton = findViewById(R.id.account_export_button);

        deleteButton = findViewById(R.id.account_delete_button);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            try {
                final KeyPair keys = SpaceAndroidUtils.getKeys();
                final String alias = BCUtils.getKeyAlias(keys.getPublic());
                aliasText.setText(alias);
                switchButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SpaceAndroidUtils.initialize(null);
                        setResult(RESULT_OK);
                        finish();
                    }
                });
                exportButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new Thread() {
                            @Override
                            public void run() {
                                final String accessCode = new String(BCUtils.encodeBase64(BCUtils.generateSecretKey(BCUtils.AES_KEY_SIZE_BYTES)));
                                try {
                                    SpaceAndroidUtils.exportKeyPair(getFilesDir(), alias, accessCode);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            new ExportAccountDialog(AccountActivity.this, accessCode) {
                                                @Override
                                                public void onExport() {
                                                    // TODO
                                                }
                                            }.create();
                                        }
                                    });
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                    }
                });
                deleteButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SpaceAndroidUtils.showDeleteAccountDialog(AccountActivity.this, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                if (BCUtils.deleteRSAKeyPair(getFilesDir(), alias)) {
                                    dialog.dismiss();
                                    setResult(RESULT_OK);
                                    finish();
                                }
                            }
                        });
                    }
                });
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SpaceAndroidUtils.ACCESS_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        break;
                    case RESULT_CANCELED:
                        setResult(RESULT_CANCELED);
                        finish();
                        break;
                    default:
                        break;
                }
                break;
        }
    }
}
