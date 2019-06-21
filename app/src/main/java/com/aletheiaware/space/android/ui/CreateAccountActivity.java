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

import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aletheiaware.alias.utils.AliasUtils;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Crypto;
import com.aletheiaware.bc.FileCache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.TCPNetwork;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;

import java.io.IOException;
import java.net.InetAddress;
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

    Cache cache;
    Network network;
    TextView legaleseLabel;
    CheckBox termsCheck;
    CheckBox policyCheck;
    TextView legaleseBetaLabel;
    CheckBox betaCheck;
    EditText aliasText;
    EditText newPasswordText;
    EditText confirmPasswordText;
    Button button;
    ProgressBar progressBar;
    AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        cache = new FileCache(getCacheDir());
        new Thread() {
            @Override
            public void run() {
                network = new TCPNetwork(new InetAddress[]{SpaceAndroidUtils.getSpaceHost()});
            }
        }.start();

        legaleseLabel = findViewById(R.id.create_account_legalese_label);
        legaleseLabel.setMovementMethod(LinkMovementMethod.getInstance());
        termsCheck = findViewById(R.id.create_account_terms_of_service_check);
        policyCheck = findViewById(R.id.create_account_privacy_policy_check);
        legaleseBetaLabel = findViewById(R.id.create_account_legalese_beta_label);
        legaleseBetaLabel.setMovementMethod(LinkMovementMethod.getInstance());
        betaCheck = findViewById(R.id.create_account_beta_test_agreement_check);
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
                            final boolean unique = AliasUtils.isUnique(cache, network, alias);
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
                            BCAndroidUtils.showErrorDialog(CreateAccountActivity.this, R.string.error_read_alias_failed, e);
                        }
                    }
                }.start();
            }
        });
        newPasswordText = findViewById(R.id.create_account_new_password_text);
        confirmPasswordText = findViewById(R.id.create_account_confirm_password_text);
        button = findViewById(R.id.create_account_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createAccount();
            }
        });
        termsCheck.requestFocus();
    }

    private void createAccount() {
        // Legal
        if (!termsCheck.isChecked()) {
            BCAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_terms_of_service_required));
            return;
        }
        if (!policyCheck.isChecked()) {
            BCAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_privacy_policy_required));
            return;
        }
        if (!betaCheck.isChecked()) {
            BCAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_beta_test_agreement_required));
            return;
        }

        // Alias
        final String alias = aliasText.getText().toString();
        // TODO ensure alias is valid
        if (alias.isEmpty()) {
            BCAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_alias_invalid));
            return;
        }

        // Password
        // TODO ensure password meets minimum security
        final int passwordLength = newPasswordText.length();
        if (passwordLength < 12) {
            BCAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_password_short));
            return;
        }
        if (passwordLength != confirmPasswordText.length()) {
            BCAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_password_lengths_differ));
            return;
        }

        final char[] newPassword = new char[passwordLength];
        final char[] confirmPassword = new char[passwordLength];
        newPasswordText.getText().getChars(0, passwordLength, newPassword, 0);
        confirmPasswordText.getText().getChars(0, passwordLength, confirmPassword, 0);
        if (!Arrays.equals(newPassword, confirmPassword)) {
            BCAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_passwords_differ));
            return;
        }

        View progressView = View.inflate(CreateAccountActivity.this, R.layout.dialog_progress, null);
        progressBar = progressView.findViewById(R.id.progress);
        progressDialog = new AlertDialog.Builder(CreateAccountActivity.this, R.style.AlertDialogTheme)
                .setTitle(R.string.title_dialog_creating_account)
                .setCancelable(false)
                .setView(progressBar)
                .show();
        new Thread() {
            @Override
            public void run() {
                try {
                    if (!AliasUtils.isUnique(cache, network, alias)) {
                        BCAndroidUtils.showErrorDialog(CreateAccountActivity.this, getString(R.string.error_alias_taken));
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
                try {
                    // TODO mine terms of service agreement into blockchain
                    setProgressBar(1);
                    // TODO mine privacy policy agreement into blockchain
                    setProgressBar(2);
                    // TODO mine beta test agreement into blockchain
                    setProgressBar(3);
                    final KeyPair keyPair = Crypto.createRSAKeyPair(getFilesDir(), alias, newPassword);
                    setProgressBar(4);
                    AliasUtils.registerAlias(SpaceAndroidUtils.getSpaceWebsite(), alias, keyPair);
                    setProgressBar(5);
                    BCAndroidUtils.initialize(alias, keyPair, cache);
                    setProgressBar(6);
                    // TODO show user the generated key pair, explain public vs private key, and provide options to backup keys
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | InvalidKeySpecException | InvalidParameterSpecException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    BCAndroidUtils.showErrorDialog(CreateAccountActivity.this, R.string.error_create_account, e);
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (progressDialog != null && progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            setResult(RESULT_OK);
                            finish();
                        }
                    });
                }
            }
        }.start();
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