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
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public class NewRecordActivity extends AppCompatActivity {

    private EditText nameEditText;
    private Spinner typeSpinner;
    private TextView sizeTextView;
    private EditText contentEditText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SpaceAndroidUtils.createNotificationChannels(this);

        // Setup UI
        setContentView(R.layout.activity_new_record);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.new_record_toolbar);
        setSupportActionBar(toolbar);

        // Name EditText
        nameEditText = findViewById(R.id.new_record_name);
        // Precomplete EditText with generated name
        String generatedName = "Record" + System.nanoTime() + ".txt";
        nameEditText.setText(generatedName);

        // Type Spinner
        typeSpinner = findViewById(R.id.new_record_type);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.mime_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);

        // Content EditText
        contentEditText = findViewById(R.id.new_record_content);
        contentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                sizeTextView.setText(BCUtils.sizeToString(s.length() * 2));// 16bit char == 2 * 8bit byte
            }
        });

        // Size TextView
        sizeTextView = findViewById(R.id.new_record_size);
        sizeTextView.setText(BCUtils.sizeToString(0));

        // FloatingActionButton
        FloatingActionButton fab = findViewById(R.id.new_record_fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO disable fab, nameEditText, typeSpinner, and contentEditText until mining fails (if mining succeeds, activity will finish)
                //fab.setEnabled(false);
                //nameEditText.setEnabled(false);
                //typeSpinner.setEnabled(false);
                //contentEditText.setEnabled(false);
                String name = nameEditText.getText().toString();
                String type = typeSpinner.getSelectedItem().toString();
                String text = contentEditText.getText().toString();
                Preview preview = Preview.newBuilder()
                        .setType(SpaceUtils.TEXT_PLAIN_TYPE)
                        .setData(ByteString.copyFromUtf8(text.substring(0, Math.min(text.length(), SpaceUtils.PREVIEW_TEXT_LENGTH))))
                        .build();
                ByteArrayInputStream in = new ByteArrayInputStream(text.getBytes(Charset.defaultCharset()));
                SpaceAndroidUtils.mine(NewRecordActivity.this, name, type, preview, in);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            Intent intent = getIntent();
            if (intent != null) {
                Log.d(SpaceUtils.TAG, intent.toString());
            }
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
            case SpaceAndroidUtils.PAYMENT_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        final String name = nameEditText.getText().toString();
                        Log.d(SpaceUtils.TAG, "Name: " + name);
                        final String type = typeSpinner.getSelectedItem().toString();
                        Log.d(SpaceUtils.TAG, "Type: " + type);
                        String text = contentEditText.getText().toString();
                        final Preview preview = Preview.newBuilder()
                                .setType(SpaceUtils.TEXT_PLAIN_TYPE)
                                .setData(ByteString.copyFromUtf8(text.substring(0, Math.min(text.length(), SpaceUtils.PREVIEW_TEXT_LENGTH))))
                                .build();
                        final ByteArrayInputStream in = new ByteArrayInputStream(text.getBytes(Charset.defaultCharset()));
                        final String alias = SpaceAndroidUtils.getAlias();
                        final String email = intent.getStringExtra(SpaceAndroidUtils.EMAIL_EXTRA);
                        Log.d(SpaceUtils.TAG, "Email: " + email);
                        final String paymentId = intent.getStringExtra(SpaceAndroidUtils.STRIPE_TOKEN_EXTRA);
                        Log.d(SpaceUtils.TAG, "PaymentId: " + paymentId);
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    SpaceUtils.subscribe(alias, email, paymentId);
                                    SpaceAndroidUtils.mine(NewRecordActivity.this, name, type, preview, in);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                } catch (InvalidKeyException e) {
                                    e.printStackTrace();
                                } catch (SignatureException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, intent);
                break;
        }
    }
}
