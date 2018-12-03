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
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.aletheiaware.bc.utils.BCUtils;
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
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class CreateAccountActivity extends AppCompatActivity {

    private EditText emailText;
    private EditText passwordText;
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
        setContentView(R.layout.activity_create_account);
        emailText = findViewById(R.id.create_account_email_text);
        passwordText = findViewById(R.id.create_account_password_text);
        confirmPasswordText = findViewById(R.id.create_account_confirm_password_text);
        cardWidget = findViewById(R.id.create_account_card_widget);
        policyCheck = findViewById(R.id.create_account_privacy_policy_check);
        termsCheck = findViewById(R.id.create_account_terms_of_service_check);
        createAccountFab = findViewById(R.id.create_account_fab);
        createAccountFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createAccountFab.setVisibility(View.INVISIBLE);
                // Email
                final String email = emailText.getText().toString();
                // TODO ensure email is valid
                if (email.isEmpty()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, "Invalid email");
                    return;
                }

                // Password
                // TODO ensure password meets minimum security
                final int passwordLength = passwordText.length();
                if (passwordLength < 12) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, "Password too short (12 character minimum)");
                    return;
                }
                if (passwordLength != confirmPasswordText.length()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, "Password and confirm password differ in length");
                    return;
                }

                final char[] password = new char[passwordLength];
                final char[] confirmPassword = new char[passwordLength];
                passwordText.getText().getChars(0, passwordLength, password, 0);
                confirmPasswordText.getText().getChars(0, passwordLength, confirmPassword, 0);
                if (!Arrays.equals(password, confirmPassword)) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, "Password and confirm password do not match");
                    return;
                }

                // Payment
                final Card card = cardWidget.getCard();
                if (card == null || !card.validateCard()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, "Invalid payment information");
                    return;
                }

                // Legal
                if (!policyCheck.isChecked()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, "You must read, understand, and agree to the Privacy Policy");
                    return;
                }

                if (!termsCheck.isChecked()) {
                    SpaceAndroidUtils.showErrorDialog(CreateAccountActivity.this, "You must read, understand, and agree to the Terms of Service");
                    return;
                }
                progressView = View.inflate(CreateAccountActivity.this, R.layout.dialog_progress, null);
                progressBar = progressView.findViewById(R.id.progress);
                dialog = new AlertDialog.Builder(CreateAccountActivity.this)
                        .setTitle(R.string.title_dialog_create_account)
                        .setCancelable(false)
                        .setView(progressBar)
                        .show();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            setProgressBar(1);
                            final KeyPair keyPair = BCUtils.createRSAKeyPair(getFilesDir(), password);
                            setProgressBar(2);
                            Stripe stripe = new Stripe(CreateAccountActivity.this, "pk_test_gvdQJ2CsMiwE0gARM6nGUbUb");
                            setProgressBar(3);
                            stripe.createToken(
                                    card,
                                    new TokenCallback() {
                                        public void onSuccess(final Token token) {
                                            new Thread() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        SpaceAndroidUtils.register(keyPair, email, token.getId());
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }.start();
                                        }

                                        public void onError(Exception error) {
                                            error.printStackTrace();
                                            StringWriter sw = new StringWriter();
                                            error.printStackTrace(new PrintWriter(sw));
                                            Toast.makeText(CreateAccountActivity.this, sw.toString(), Toast.LENGTH_LONG).show();
                                        }
                                    }
                            );
                            setProgressBar(4);
                            SpaceAndroidUtils.initialize(keyPair);
                            setProgressBar(5);
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
                        } finally {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    createAccountFab.setVisibility(View.VISIBLE);
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
    }

    private void setProgressBar(final int v) {
        if (v > 1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setProgress(v);
            }
        });
    }
}
