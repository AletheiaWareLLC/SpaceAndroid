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
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.aletheiaware.bc.BCProto.KeyShare;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.android.utils.BiometricUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;

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

    private RecyclerView unlockAccountRecycler;
    private View unlockAccountSeparator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access);

        unlockAccountRecycler = findViewById(R.id.access_unlock_account_recycler);
        unlockAccountSeparator = findViewById(R.id.access_unlock_account_separator);
        Button importAccountButton = findViewById(R.id.access_import_account);
        importAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ImportAccountDialog(AccessActivity.this) {
                    @Override
                    public void onImport(DialogInterface dialog, final String alias, final String accessCode) {
                        dialog.dismiss();
                        new Thread() {
                            @Override
                            public void run() {
                                // Use access code to import key
                                try {
                                    KeyShare ks = BCUtils.getKeyShare(alias);
                                    BCUtils.importRSAKeyPair(getFilesDir(), accessCode, ks);
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
        // TODO add button to import backed up keys, either by OCR, or copy/paste.
        Button createAccountButton = findViewById(R.id.access_new_account);
        createAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AccessActivity.this, NewAccountActivity.class);
                startActivity(intent);
            }
        });
        AppCompatImageButton logoButton = findViewById(R.id.aletheia_ware_llc_logo);
        logoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://aletheiaware.com"));
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
            showAccountList();
        }
    }

    private void showAccountList() {
        // A key exists, show unlock option
        final List<String> ks = BCUtils.listRSAKeyPairs(getFilesDir());
        Log.d(SpaceUtils.TAG, "Keys: " + ks);
        if (ks.isEmpty()) {
            unlockAccountRecycler.setVisibility(View.GONE);
            unlockAccountSeparator.setVisibility(View.GONE);
        } else {
            unlockAccountRecycler.setVisibility(View.VISIBLE);
            unlockAccountSeparator.setVisibility(View.VISIBLE);
            unlockAccountRecycler.setLayoutManager(new LinearLayoutManager(this));
            final AccountAdapter adapter = new AccountAdapter(this, ks) {
                @Override
                public void unlockAccount(final String alias) {
                    if (BiometricUtils.isBiometricUnlockAvailable(AccessActivity.this) && BiometricUtils.isBiometricUnlockEnabled(AccessActivity.this, alias)) {
                        biometricUnlock(alias);
                    } else {
                        passwordUnlock(alias);
                    }
                }

                @Override
                public void deleteAccount(final String alias) {
                    SpaceAndroidUtils.showDeleteAccountDialog(AccessActivity.this, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            if (BCUtils.deleteRSAKeyPair(getFilesDir(), alias)) {
                                removeAlias(alias);
                                notifyDataSetChanged();
                                dialog.dismiss();
                                showAccountList();
                            }
                        }
                    });
                }
            };
            unlockAccountRecycler.setAdapter(adapter);
        }
    }

    private void biometricUnlock(final String alias) {
        new BiometricUnlockDialog(AccessActivity.this, alias) {
            @Override
            public void onUnlock(char[] password) {
                try {
                    unlock(alias, password);
                } catch (Exception e) {
                    e.printStackTrace();
                    // Fall back to password unlock
                    passwordUnlock(alias);
                }
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();
                // Fall back to password unlock
                passwordUnlock(alias);
            }
        }.create();
    }

    private void passwordUnlock(final String alias) {
        new PasswordUnlockDialog(AccessActivity.this, alias) {
            @Override
            public void onUnlock(DialogInterface dialog, char[] password) {
                dialog.dismiss();
                try {
                    unlock(alias, password);
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | InvalidKeySpecException | InvalidParameterSpecException | NoSuchAlgorithmException | NoSuchPaddingException e) {
                    SpaceAndroidUtils.showErrorDialog(AccessActivity.this, R.string.error_unlock_account, e);
                }
            }
        }.create();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void unlock(String alias, char[] password) throws BadPaddingException, IOException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException, InvalidKeySpecException, InvalidParameterSpecException, NoSuchAlgorithmException, NoSuchPaddingException {
        // Use password to decrypt key
        KeyPair keyPair = BCUtils.getRSAKeyPair(getFilesDir(), alias, password);
        SpaceAndroidUtils.initialize(alias, keyPair);
        // Unlock successful, exit
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setResult(RESULT_OK);
                finish();
            }
        });
    }
}
