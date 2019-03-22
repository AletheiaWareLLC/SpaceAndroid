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

import android.app.AlertDialog;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;

import com.aletheiaware.alias.utils.AliasUtils;
import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.model.Card;
import com.stripe.android.model.Token;
import com.stripe.android.view.CardInputWidget;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class NewAccountActivity extends AppCompatActivity {

    private EditText aliasText;
    private EditText emailText;
    private EditText newPasswordText;
    private EditText confirmPasswordText;
    private CardInputWidget cardWidget;
    private CheckBox termsCheck;
    private CheckBox policyCheck;
    private CheckBox betaCheck;
    private FloatingActionButton createAccountFab;

    private View progressView;
    private ProgressBar progressBar;
    private AlertDialog dialog;

    private Channel aliases;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new Thread() {
            @Override
            public void run() {
                aliases = new Channel(AliasUtils.ALIAS_CHANNEL, BCUtils.THRESHOLD_STANDARD, getCacheDir(), SpaceAndroidUtils.getHost());
                try {
                    aliases.sync();
                } catch (IOException | NoSuchAlgorithmException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
            }
        }.start();

        // Setup UI
        setContentView(R.layout.activity_new_account);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.new_account_toolbar);
        setSupportActionBar(toolbar);

        aliasText = findViewById(R.id.new_account_alias_text);
        emailText = findViewById(R.id.new_account_email_text);
        newPasswordText = findViewById(R.id.new_account_new_password_text);
        confirmPasswordText = findViewById(R.id.new_account_confirm_password_text);
        cardWidget = findViewById(R.id.new_account_card_widget);
        termsCheck = findViewById(R.id.new_account_terms_of_service_check);
        policyCheck = findViewById(R.id.new_account_privacy_policy_check);
        betaCheck = findViewById(R.id.new_account_beta_test_agreement_check);
        createAccountFab = findViewById(R.id.new_account_fab);
        createAccountFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Alias
                final String alias = aliasText.getText().toString();
                // TODO ensure alias is valid
                try {
                    // TODO add textwatcher to aliasText, each change check if alias is unique and if not display aliasErrorText
                    if (!AliasUtils.isUnique(aliases, alias)) {
                        SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, getString(R.string.error_alias_taken));
                        return;
                    }
                } catch (IOException e) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, R.string.error_alias_read_failed, e);
                    return;
                }
                if (alias.isEmpty()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, getString(R.string.error_alias_invalid));
                    return;
                }

                // Email
                final String email = emailText.getText().toString();
                // TODO ensure email is valid
                if (email.isEmpty()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, getString(R.string.error_email_invalid));
                    return;
                }

                // Password
                // TODO ensure password meets minimum security
                final int passwordLength = newPasswordText.length();
                if (passwordLength < 12) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, getString(R.string.error_password_short));
                    return;
                }
                if (passwordLength != confirmPasswordText.length()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, getString(R.string.error_password_lengths_differ));
                    return;
                }

                final char[] newPassword = new char[passwordLength];
                final char[] confirmPassword = new char[passwordLength];
                newPasswordText.getText().getChars(0, passwordLength, newPassword, 0);
                confirmPasswordText.getText().getChars(0, passwordLength, confirmPassword, 0);
                if (!Arrays.equals(newPassword, confirmPassword)) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, getString(R.string.error_passwords_differ));
                    return;
                }

                // Payment
                final Card card = cardWidget.getCard();
                if (card == null || !card.validateCard()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, getString(R.string.error_stripe_invalid_payment));
                    return;
                }

                // Legal
                if (!termsCheck.isChecked()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, getString(R.string.error_terms_of_service_required));
                    return;
                }
                if (!policyCheck.isChecked()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, getString(R.string.error_privacy_policy_required));
                    return;
                }
                if (!betaCheck.isChecked()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, getString(R.string.error_beta_test_agreement_required));
                    return;
                }

                createAccountFab.hide();

                progressView = View.inflate(NewAccountActivity.this, R.layout.dialog_progress, null);
                progressBar = progressView.findViewById(R.id.progress);
                dialog = new AlertDialog.Builder(NewAccountActivity.this, R.style.AlertDialogTheme)
                        .setTitle(R.string.title_dialog_new_account)
                        .setCancelable(false)
                        .setView(progressBar)
                        .show();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            setProgressBar(1);
                            final KeyPair keyPair = BCUtils.createRSAKeyPair(getFilesDir(), alias, newPassword);
                            setProgressBar(2);
                            AliasUtils.registerAlias(alias, keyPair);
                            setProgressBar(3);
                            // TODO mine terms of service agreement into blockchain
                            setProgressBar(4);
                            // TODO mine privacy policy agreement into blockchain
                            setProgressBar(5);
                            Stripe stripe = new Stripe(NewAccountActivity.this, "pk_test_gvdQJ2CsMiwE0gARM6nGUbUb");
                            setProgressBar(6);
                            stripe.createToken(
                                    card,
                                    new TokenCallback() {
                                        public void onSuccess(final Token token) {
                                            new Thread() {
                                                @Override
                                                public void run() {
                                                    String customerId = null;
                                                    try {
                                                        customerId = SpaceUtils.register(alias, email, token.getId());
                                                    } catch (IOException e) {
                                                        SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, R.string.error_registering, e);
                                                    }
                                                    Log.d(SpaceUtils.TAG, "Customer ID: " + customerId);
                                                    if (customerId != null && !customerId.isEmpty()) {
                                                        try {
                                                            String subscriptionId = SpaceUtils.subscribe(alias, customerId);
                                                            Log.d(SpaceUtils.TAG, "Subscription ID: " + subscriptionId);
                                                        } catch (IOException e) {
                                                            SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, R.string.error_subscribing, e);
                                                        }
                                                    }
                                                }
                                            }.start();
                                        }

                                        public void onError(Exception error) {
                                            SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, R.string.error_new_account, error);
                                        }
                                    }
                            );
                            setProgressBar(7);
                            SpaceAndroidUtils.initialize(alias, keyPair);
                            setProgressBar(8);
                            // TODO show user the generated key pair, explain public vs private key, and provide options to backup keys
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setResult(RESULT_OK);
                                    finish();
                                }
                            });
                        } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | InvalidKeySpecException | InvalidParameterSpecException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                            SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, R.string.error_new_account, e);
                        } finally {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    createAccountFab.show();
                                    if (dialog != null && dialog.isShowing()) {
                                        dialog.dismiss();
                                    }
                                }
                            });
                        }
                    }
                }.start();
            }
        });
        aliasText.requestFocus();
    }

    private void setProgressBar(final int v) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setProgress(v);
            }
        });
    }
}
