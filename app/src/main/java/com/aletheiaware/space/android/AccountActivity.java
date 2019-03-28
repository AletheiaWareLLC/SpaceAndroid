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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class AccountActivity extends AppCompatActivity {

    private TextView aliasText;
    private TextView publicKeyText;
    private TextView customerText;
    private TextView subscriptionText;
    private Button registerButton;
    private Button subscribeButton;
    private Button exportButton;
    private Button switchButton;
    private Button deleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_account);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.account_toolbar);
        setSupportActionBar(toolbar);

        aliasText = findViewById(R.id.account_alias_text);
        aliasText.setOnClickListener(new CopyToClipboardListener(aliasText, "Alias"));
        publicKeyText = findViewById(R.id.account_public_key_text);
        publicKeyText.setOnClickListener(new CopyToClipboardListener(publicKeyText, "Public Key"));
        customerText = findViewById(R.id.account_customer_text);
        customerText.setOnClickListener(new CopyToClipboardListener(customerText, "Customer ID"));
        subscriptionText = findViewById(R.id.account_subscription_text);
        subscriptionText.setOnClickListener(new CopyToClipboardListener(subscriptionText, "Subscription ID"));

        // TODO add button to backup keys, either by printing, or emailing.
        registerButton = findViewById(R.id.account_register_button);
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                register();
            }
        });
        subscribeButton = findViewById(R.id.account_subscribe_button);
        subscribeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            final String website = SpaceAndroidUtils.getSpaceWebsite();
                            final String alias = SpaceAndroidUtils.getAlias();
                            final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                            final InetAddress address = SpaceAndroidUtils.getSpaceHost();
                            final String customerId = FinanceUtils.getCustomerId(address, alias, keys);
                            if (customerId == null || customerId.isEmpty()) {
                                register();
                            } else {
                                String subscriptionId = SpaceUtils.subscribe(website, alias, customerId);
                                Log.d(SpaceUtils.TAG, "Subscription ID" + subscriptionId);
                                updateStripeInfo();
                            }
                        } catch (Exception e) {
                            SpaceAndroidUtils.showErrorDialog(AccountActivity.this, R.string.error_subscribing, e);
                        }
                    }
                }.start();
            }
        });
        exportButton = findViewById(R.id.account_export_button);
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String alias = SpaceAndroidUtils.getAlias();
                new PasswordUnlockDialog(AccountActivity.this, alias) {
                    @Override
                    public void onUnlock(DialogInterface dialog, final char[] password) {
                        dialog.dismiss();
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    final String website = SpaceAndroidUtils.getSpaceWebsite();
                                    final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                                    final byte[] accessCode = BCUtils.generateSecretKey(BCUtils.AES_KEY_SIZE_BYTES);
                                    BCUtils.exportKeyPair(website, getFilesDir(), alias, password, keys, accessCode);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            new ExportKeysDialog(AccountActivity.this, alias, Base64.encodeToString(accessCode, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)) {
                                            }.create();
                                        }
                                    });
                                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException e) {
                                    SpaceAndroidUtils.showErrorDialog(AccountActivity.this, R.string.error_export_key_pair, e);
                                }
                            }
                        }.start();
                    }
                }.create();
            }
        });
        switchButton = findViewById(R.id.account_switch_button);
        switchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpaceAndroidUtils.initialize(null, null);
                setResult(RESULT_OK);
                finish();
            }
        });
        deleteButton = findViewById(R.id.account_delete_button);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpaceAndroidUtils.showDeleteKeysDialog(AccountActivity.this, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (BCUtils.deleteRSAKeyPair(getFilesDir(), SpaceAndroidUtils.getAlias())) {
                            dialog.dismiss();
                            setResult(RESULT_OK);
                            finish();
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            final String alias = SpaceAndroidUtils.getAlias();
            aliasText.setText(alias);
            final KeyPair keys = SpaceAndroidUtils.getKeyPair();
            final PublicKey key = keys.getPublic();
            publicKeyText.setText(Base64.encodeToString(key.getEncoded(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP));

            new Thread() {
                @Override
                public void run() {
                    updateStripeInfo();
                }
            }.start();
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
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
            case SpaceAndroidUtils.STRIPE_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        final String website = SpaceAndroidUtils.getSpaceWebsite();
                        final String alias = SpaceAndroidUtils.getAlias();
                        final String email = intent.getStringExtra(SpaceAndroidUtils.EMAIL_EXTRA);
                        final String paymentId = intent.getStringExtra(SpaceAndroidUtils.STRIPE_TOKEN_EXTRA);
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    String customerId = SpaceUtils.register(website, alias, email, paymentId);
                                    Log.d(SpaceUtils.TAG, "Customer ID: " + customerId);
                                    updateStripeInfo();
                                } catch (IOException e) {
                                    SpaceAndroidUtils.showErrorDialog(AccountActivity.this, R.string.error_registering, e);
                                }
                            }
                        }.start();
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
        }
    }

    private void register() {
        Intent intent = new Intent(AccountActivity.this, StripeActivity.class);
        startActivityForResult(intent, SpaceAndroidUtils.STRIPE_ACTIVITY);
    }

    private void updateStripeInfo() {
        try {
            final String alias = SpaceAndroidUtils.getAlias();
            final KeyPair keys = SpaceAndroidUtils.getKeyPair();
            final InetAddress host = SpaceAndroidUtils.getSpaceHost();
            final String customerId = FinanceUtils.getCustomerId(host, alias, keys);
            final String subscriptionId = FinanceUtils.getSubscriptionId(host, alias, keys);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (customerId == null || customerId.isEmpty()) {
                        customerText.setText(R.string.account_not_registered);
                        registerButton.setVisibility(View.VISIBLE);
                    } else {
                        customerText.setText(customerId);
                        registerButton.setVisibility(View.GONE);
                        if (subscriptionId == null || subscriptionId.isEmpty()) {
                            // Only show subscribe button if customer is registered
                            subscribeButton.setVisibility(View.VISIBLE);
                        }
                    }
                    if (subscriptionId == null || subscriptionId.isEmpty()) {
                        subscriptionText.setText(R.string.account_not_subscribed);
                    } else {
                        subscriptionText.setText(subscriptionId);
                        subscribeButton.setVisibility(View.GONE);
                    }
                }
            });
        } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
            SpaceAndroidUtils.showErrorDialog(AccountActivity.this, R.string.error_finance_read_failed, e);
        }
    }

    private static class CopyToClipboardListener implements View.OnClickListener {
        private final TextView view;
        private final String label;

        CopyToClipboardListener(TextView view, String label) {
            this.view = view;
            this.label = label;
        }

        @Override
        public void onClick(View v) {
            Context context = view.getContext();
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(label, view.getText()));
            Toast.makeText(context, label + " copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }
}
