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
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class AccountActivity extends AppCompatActivity {

    private TextView aliasText;
    private Button switchButton;
    private Button exportButton;
    private Button deleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        aliasText = findViewById(R.id.account_alias_text);

        switchButton = findViewById(R.id.account_switch_button);

        exportButton = findViewById(R.id.account_export_button);

        deleteButton = findViewById(R.id.account_delete_button);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            final String alias = SpaceAndroidUtils.getAlias();
            aliasText.setText(alias);
            switchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SpaceAndroidUtils.initialize(null,null);
                    setResult(RESULT_OK);
                    finish();
                }
            });
            exportButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new UnlockDialog(AccountActivity.this, alias) {
                        @Override
                        public void onUnlock(DialogInterface dialog, final char[] password) {
                            new Thread() {
                                @Override
                                public void run() {
                                    try {
                                        final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                                        final byte[] accessCode = BCUtils.generateSecretKey(BCUtils.AES_KEY_SIZE_BYTES);
                                        BCUtils.exportKeyPair(getFilesDir(), alias, password, keys, accessCode);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                new ExportAccountDialog(AccountActivity.this, alias, Base64.encodeToString(accessCode, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)) {
                                                }.create();
                                            }
                                        });
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    } catch (NoSuchPaddingException e) {
                                        e.printStackTrace();
                                    } catch (NoSuchAlgorithmException e) {
                                        e.printStackTrace();
                                    } catch (InvalidKeyException e) {
                                        e.printStackTrace();
                                    } catch (InvalidAlgorithmParameterException e) {
                                        e.printStackTrace();
                                    } catch (IllegalBlockSizeException e) {
                                        e.printStackTrace();
                                    } catch (BadPaddingException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }.start();
                        }
                    }.create();
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
