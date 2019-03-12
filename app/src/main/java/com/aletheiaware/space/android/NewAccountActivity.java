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
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.aletheiaware.alias.utils.AliasUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.model.Card;
import com.stripe.android.model.Token;
import com.stripe.android.view.CardInputWidget;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
    private CheckBox policyCheck;
    private CheckBox termsCheck;
    private FloatingActionButton createAccountFab;

    private View progressView;
    private ProgressBar progressBar;
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        policyCheck = findViewById(R.id.new_account_privacy_policy_check);
        termsCheck = findViewById(R.id.new_account_terms_of_service_check);
        createAccountFab = findViewById(R.id.new_account_fab);
        createAccountFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Alias
                final String alias = aliasText.getText().toString();
                // TODO ensure alias is valid
                try {
                    if (!AliasUtils.isUnique(SpaceAndroidUtils.getHost(), alias)) {
                        SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, "Alias already registered");
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, "Could not read Alias blockchain");
                    return;
                }
                if (alias.isEmpty()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, "Invalid alias");
                    return;
                }

                // Email
                final String email = emailText.getText().toString();
                // TODO ensure email is valid
                if (email.isEmpty()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, "Invalid email");
                    return;
                }

                // Password
                // TODO ensure password meets minimum security
                final int passwordLength = newPasswordText.length();
                if (passwordLength < 12) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, "Password must be at least 12 characters");
                    return;
                }
                if (passwordLength != confirmPasswordText.length()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, "Password and confirm password differ in length");
                    return;
                }

                final char[] newPassword = new char[passwordLength];
                final char[] confirmPassword = new char[passwordLength];
                newPasswordText.getText().getChars(0, passwordLength, newPassword, 0);
                confirmPasswordText.getText().getChars(0, passwordLength, confirmPassword, 0);
                if (!Arrays.equals(newPassword, confirmPassword)) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, "Password and confirm password do not match");
                    return;
                }

                // Payment
                final Card card = cardWidget.getCard();
                if (card == null || !card.validateCard()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, "Invalid payment information");
                    return;
                }

                // Legal
                // TODO mine legal responses into blockchain and check server side
                if (!policyCheck.isChecked()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, "You must read, understand, and agree to the Privacy Policy");
                    return;
                }

                if (!termsCheck.isChecked()) {
                    SpaceAndroidUtils.showErrorDialog(NewAccountActivity.this, "You must read, understand, and agree to the Terms of Service");
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
                            Stripe stripe = new Stripe(NewAccountActivity.this, "pk_test_gvdQJ2CsMiwE0gARM6nGUbUb");
                            setProgressBar(4);
                            stripe.createToken(
                                    card,
                                    new TokenCallback() {
                                        public void onSuccess(final Token token) {
                                            new Thread() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        SpaceUtils.register(alias, email, token.getId());
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }.start();
                                        }

                                        public void onError(Exception error) {
                                            error.printStackTrace();
                                            StringWriter sw = new StringWriter();
                                            error.printStackTrace(new PrintWriter(sw));
                                            Toast.makeText(NewAccountActivity.this, sw.toString(), Toast.LENGTH_LONG).show();
                                        }
                                    }
                            );
                            setProgressBar(5);
                            SpaceAndroidUtils.initialize(alias, keyPair);
                            setProgressBar(6);
                            // TODO show user the generated key pair, explain public vs private key, and provide options to backup keys
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setResult(RESULT_OK);
                                    finish();
                                }
                            });
                        } catch (BadPaddingException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (IllegalBlockSizeException e) {
                            e.printStackTrace();
                        } catch (InvalidAlgorithmParameterException e) {
                            e.printStackTrace();
                        } catch (InvalidKeyException e) {
                            e.printStackTrace();
                        } catch (InvalidKeySpecException e) {
                            e.printStackTrace();
                        } catch (InvalidParameterSpecException e) {
                            e.printStackTrace();
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        } catch (NoSuchPaddingException e) {
                            e.printStackTrace();
                        } catch (SignatureException e) {
                            e.printStackTrace();
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
