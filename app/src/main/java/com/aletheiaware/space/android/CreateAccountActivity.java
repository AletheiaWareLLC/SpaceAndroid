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
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

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

public class CreateAccountActivity extends AppCompatActivity {

    private EditText aliasText;
    private EditText emailText;
    private EditText newPasswordText;
    private EditText confirmPasswordText;
    private CardInputWidget cardWidget;
    private CheckBox termsCheck;
    private CheckBox policyCheck;
    private CheckBox betaCheck;
    private Button createAccountButton;

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
                aliases = new Channel(AliasUtils.ALIAS_CHANNEL, BCUtils.THRESHOLD_STANDARD, getCacheDir(), SpaceAndroidUtils.getSpaceHost());
                try {
                    aliases.sync();
                } catch (IOException | NoSuchAlgorithmException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
            }
        }.start();

        // Setup UI
        setContentView(R.layout.activity_create_account);

        aliasText = findViewById(R.id.create_account_alias_text);
        aliasText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                final String alias = s.toString();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            final boolean unique = AliasUtils.isUnique(aliases, alias);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (unique) {
                                        aliasText.setError(null);
                                    } else {
                                        aliasText.setError(getString(R.string.error_alias_taken));
                                    }
                                }
                            });
                        } catch (IOException e) {
                            SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, R.string.error_alias_read_failed, e);
                        }
                    }
                }.start();
            }
        });
        emailText = findViewById(R.id.create_account_email_text);
        newPasswordText = findViewById(R.id.create_account_new_password_text);
        confirmPasswordText = findViewById(R.id.create_account_confirm_password_text);
        cardWidget = findViewById(R.id.create_account_card_widget);
        TextView legaleseLabel = findViewById(R.id.create_account_legalese_label);
        legaleseLabel.setMovementMethod(LinkMovementMethod.getInstance());
        termsCheck = findViewById(R.id.create_account_terms_of_service_check);
        policyCheck = findViewById(R.id.create_account_privacy_policy_check);
        TextView legaleseBetaLabel = findViewById(R.id.create_account_legalese_beta_label);
        legaleseBetaLabel.setMovementMethod(LinkMovementMethod.getInstance());
        betaCheck = findViewById(R.id.create_account_beta_test_agreement_check);
        createAccountButton = findViewById(R.id.create_account_button);
        createAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Alias
                final String alias = aliasText.getText().toString();
                // TODO ensure alias is valid
                if (alias.isEmpty()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_alias_invalid));
                    return;
                }

                // Email
                final String email = emailText.getText().toString();
                // TODO ensure email is valid
                if (email.isEmpty()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_email_invalid));
                    return;
                }

                // Password
                // TODO ensure password meets minimum security
                final int passwordLength = newPasswordText.length();
                if (passwordLength < 12) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_password_short));
                    return;
                }
                if (passwordLength != confirmPasswordText.length()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_password_lengths_differ));
                    return;
                }

                final char[] newPassword = new char[passwordLength];
                final char[] confirmPassword = new char[passwordLength];
                newPasswordText.getText().getChars(0, passwordLength, newPassword, 0);
                confirmPasswordText.getText().getChars(0, passwordLength, confirmPassword, 0);
                if (!Arrays.equals(newPassword, confirmPassword)) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_passwords_differ));
                    return;
                }

                // Payment
                final Card card = cardWidget.getCard();
                if (card == null || !card.validateCard()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_stripe_invalid_payment));
                    return;
                }

                // Legal
                if (!termsCheck.isChecked()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_terms_of_service_required));
                    return;
                }
                if (!policyCheck.isChecked()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_privacy_policy_required));
                    return;
                }
                if (!betaCheck.isChecked()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_beta_test_agreement_required));
                    return;
                }

                createAccountButton.setEnabled(false);

                progressView = View.inflate(CreateAccountActivity.this, R.layout.dialog_progress, null);
                progressBar = progressView.findViewById(R.id.progress);
                dialog = new AlertDialog.Builder(CreateAccountActivity.this, R.style.AlertDialogTheme)
                        .setTitle(R.string.title_dialog_creating_account)
                        .setCancelable(false)
                        .setView(progressBar)
                        .show();
                new Thread() {
                    @Override
                    public void run() {
                        final String website = SpaceAndroidUtils.getSpaceWebsite();
                        try {
                            setProgressBar(1);
                            final KeyPair keyPair = BCUtils.createRSAKeyPair(getFilesDir(), alias, newPassword);
                            setProgressBar(2);
                            AliasUtils.registerAlias(website, alias, keyPair);
                            setProgressBar(3);
                            // TODO mine terms of service agreement into blockchain
                            setProgressBar(4);
                            // TODO mine privacy policy agreement into blockchain
                            setProgressBar(5);
                            Stripe stripe = new Stripe(CreateAccountActivity.this, getString(R.string.stripe_publishable_key));
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
                                                        customerId = SpaceUtils.register(website, alias, email, token.getId());
                                                    } catch (IOException e) {
                                                        SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, R.string.error_registering, e);
                                                    }
                                                    Log.d(SpaceUtils.TAG, "Customer ID: " + customerId);
                                                    if (customerId != null && !customerId.isEmpty()) {
                                                        try {
                                                            String subscriptionId = SpaceUtils.subscribe(website, alias, customerId);
                                                            Log.d(SpaceUtils.TAG, "Subscription ID: " + subscriptionId);
                                                        } catch (IOException e) {
                                                            SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, R.string.error_subscribing, e);
                                                        }
                                                    }
                                                }
                                            }.start();
                                        }

                                        public void onError(Exception error) {
                                            SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, R.string.error_create_account, error);
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
                            SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, R.string.error_create_account, e);
                        } finally {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    createAccountButton.setEnabled(true);
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
