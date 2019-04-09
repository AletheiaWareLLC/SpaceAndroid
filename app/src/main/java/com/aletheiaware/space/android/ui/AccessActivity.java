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

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import com.aletheiaware.bc.BCProto.KeyShare;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.android.BiometricUnlockDialog;
import com.aletheiaware.space.android.KeysAdapter;
import com.aletheiaware.space.android.R;
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

    private RecyclerView unlockKeysRecycler;
    private View unlockKeysSeparator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access);

        unlockKeysRecycler = findViewById(R.id.access_unlock_keys_recycler);
        unlockKeysSeparator = findViewById(R.id.access_unlock_keys_separator);
        Button importKeysButton = findViewById(R.id.access_import_keys);
        importKeysButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ImportKeysDialog(AccessActivity.this) {
                    @Override
                    public void onImport(DialogInterface dialog, final String alias, final String accessCode) {
                        dialog.dismiss();
                        new Thread() {
                            @Override
                            public void run() {
                                // Use access code to import key
                                try {
                                    final String website = SpaceAndroidUtils.getSpaceWebsite();
                                    KeyShare ks = BCUtils.getKeyShare(website, alias);
                                    BCUtils.importRSAKeyPair(getFilesDir(), accessCode, ks);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            // Reload UI so account list can pickup imported key
                                            recreate();
                                        }
                                    });
                                } catch (Exception e) {
                                    SpaceAndroidUtils.showErrorDialog(AccessActivity.this, R.string.error_import_key_pair, e);
                                }
                            }
                        }.start();
                    }
                }.create();
            }
        });
        // TODO add button to import backed up keys, either by OCR, or copy/paste.
        Button createAccountButton = findViewById(R.id.access_create_account);
        createAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AccessActivity.this, CreateAccountActivity.class);
                startActivity(intent);
            }
        });
        ImageButton logoButton = findViewById(R.id.aletheia_ware_llc_logo);
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
            showKeysList();
        }
    }

    private void showKeysList() {
        // A key exists, show unlock option
        final List<String> ks = BCUtils.listRSAKeyPairs(getFilesDir());
        Log.d(SpaceUtils.TAG, "Keys: " + ks);
        if (ks.isEmpty()) {
            unlockKeysRecycler.setVisibility(View.GONE);
            unlockKeysSeparator.setVisibility(View.GONE);
        } else {
            unlockKeysRecycler.setVisibility(View.VISIBLE);
            unlockKeysSeparator.setVisibility(View.VISIBLE);
            unlockKeysRecycler.setLayoutManager(new LinearLayoutManager(this));
            final KeysAdapter adapter = new KeysAdapter(this, ks) {
                @Override
                public void unlockKeys(final String alias) {
                    if (BiometricUtils.isBiometricUnlockAvailable(AccessActivity.this) && BiometricUtils.isBiometricUnlockEnabled(AccessActivity.this, alias)) {
                        biometricUnlock(alias);
                    } else {
                        passwordUnlock(alias);
                    }
                }

                @Override
                public void deleteKeys(final String alias) {
                    SpaceAndroidUtils.showDeleteKeysDialog(AccessActivity.this, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            if (BCUtils.deleteRSAKeyPair(getFilesDir(), alias)) {
                                removeAlias(alias);
                                notifyDataSetChanged();
                                dialog.dismiss();
                                showKeysList();
                            }
                        }
                    });
                }
            };
            unlockKeysRecycler.setAdapter(adapter);
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
                    SpaceAndroidUtils.showErrorDialog(AccessActivity.this, R.string.error_unlock_keys, e);
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
