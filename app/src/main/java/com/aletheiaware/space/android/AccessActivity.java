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
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.aletheiaware.bc.utils.BCUtils;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class AccessActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private ListView accountList;
    private Button importAccountButton;
    private Button createAccountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access);

        // Toolbar
        toolbar = findViewById(R.id.access_toolbar);
        setSupportActionBar(toolbar);

        accountList = findViewById(R.id.access_account_list);
        importAccountButton = findViewById(R.id.access_import_account);
        importAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ImportAccountDialog(AccessActivity.this) {
                    @Override
                    public void onImport(final String accessCode) {
                        new Thread() {
                            @Override
                            public void run() {
                                // Use access code to import key
                                try {
                                    SpaceAndroidUtils.importKey(getFilesDir(), accessCode);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            // Reload UI so account list can pickup imported key
                                            recreate();
                                        }
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                    }
                }.create();
            }
        });
        createAccountButton = findViewById(R.id.access_create_account);
        createAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AccessActivity.this, CreateAccountActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            setResult(RESULT_OK);
            finish();
        } else {
            // A key exists, show unlock option
            final List<String> ks = BCUtils.listRSAKeyPairs(getFilesDir());
            if (ks.isEmpty()) {
                accountList.setVisibility(View.GONE);
            } else {
                accountList.setVisibility(View.VISIBLE);
                final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.account_list_item, ks);
                accountList.setAdapter(adapter);
                accountList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        final String alias = ks.get(position);
                        new UnlockDialog(AccessActivity.this, alias) {
                            @Override
                            public void onUnlock(char[] password) {
                                try {
                                    // Use password to decrypt key
                                    KeyPair keyPair = BCUtils.getRSAKeyPair(getFilesDir(), alias, password);
                                    SpaceAndroidUtils.initialize(keyPair);
                                    // Sign In successful, exit
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            setResult(RESULT_OK);
                                            finish();
                                        }
                                    });
                                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | InvalidKeySpecException | InvalidParameterSpecException | NoSuchAlgorithmException | NoSuchPaddingException e) {
                                    SpaceAndroidUtils.showErrorDialog(AccessActivity.this, R.string.error_unlock_account, e);
                                }
                            }
                        }.create();
                    }
                });
                accountList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                        final String alias = ks.get(position);
                        SpaceAndroidUtils.showDeleteAccountDialog(AccessActivity.this, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                if (BCUtils.deleteRSAKeyPair(getFilesDir(), alias)) {
                                    adapter.remove(alias);
                                    adapter.notifyDataSetChanged();
                                    dialog.dismiss();
                                }
                            }
                        });
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }
}
