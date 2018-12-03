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

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.aletheiaware.bc.utils.BCUtils;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class AccessActivity extends AppCompatActivity {

    private LinearLayout accountLayout;
    private EditText passwordText;
    private Button unlockAccountButton;
    private EditText tokenText;
    private Button importAccountButton;
    private Button createAccountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access);
        accountLayout = findViewById(R.id.access_account);
        passwordText = findViewById(R.id.access_password);
        unlockAccountButton = findViewById(R.id.access_unlock_account);
        unlockAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unlockAccount();
            }
        });
        tokenText = findViewById(R.id.access_token);
        importAccountButton = findViewById(R.id.access_import_account);
        importAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importAccount();
            }
        });
        createAccountButton = findViewById(R.id.access_create_account);
        createAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createAccount();
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
            if (BCUtils.hasRSAKeyPair(getFilesDir())) {
                accountLayout.setVisibility(View.VISIBLE);
            } else {
                accountLayout.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void unlockAccount() {
        // Use passwordText text to decrypt key
        final int passwordLength = passwordText.length();
        final char[] password = new char[passwordLength];
        passwordText.getText().getChars(0, passwordLength, password, 0);
        try {
            KeyPair keyPair = BCUtils.getRSAKeyPair(getFilesDir(), password);
            SpaceAndroidUtils.initialize(keyPair);
            setResult(RESULT_OK);
            finish();
        } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidKeySpecException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidParameterSpecException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
    }

    private void importAccount() {
        // Use access token to import key
        final String token = tokenText.getText().toString();
        try {
            KeyPair keyPair = BCUtils.importRSAKeyPair(getFilesDir(), token);
            SpaceAndroidUtils.initialize(keyPair);
            setResult(RESULT_OK);
            finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createAccount() {
        Intent intent = new Intent(this, CreateAccountActivity.class);
        startActivity(intent);
    }
}
