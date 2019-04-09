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

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

public class ComposeDocumentActivity extends AppCompatActivity {

    private EditText nameEditText;
    private Spinner typeSpinner;
    private TextView sizeTextView;
    private EditText contentEditText;
    private Button composeButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SpaceAndroidUtils.createNotificationChannels(this);

        // Setup UI
        setContentView(R.layout.activity_compose_document);

        // Name EditText
        nameEditText = findViewById(R.id.compose_document_name);
        // Precomplete EditText with generated name
        String generatedName = "Document" + System.currentTimeMillis();
        nameEditText.setText(generatedName);

        // Type Spinner
        typeSpinner = findViewById(R.id.compose_document_type);
        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(this, R.array.mime_types, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);

        // Content EditText
        contentEditText = findViewById(R.id.compose_document_content);
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
        sizeTextView = findViewById(R.id.compose_document_size);
        sizeTextView.setText(BCUtils.sizeToString(0));

        // Compose Button
        composeButton = findViewById(R.id.compose_document_button);
        composeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                composeButton.setVisibility(View.GONE);
                composeButton.setEnabled(false);
                nameEditText.setEnabled(false);
                typeSpinner.setEnabled(false);
                sizeTextView.setEnabled(false);
                contentEditText.setEnabled(false);
                String name = nameEditText.getText().toString();
                String type = typeSpinner.getSelectedItem().toString();
                String text = contentEditText.getText().toString();
                Preview preview = Preview.newBuilder()
                        .setType(SpaceUtils.TEXT_PLAIN_TYPE)
                        .setData(ByteString.copyFromUtf8(text.substring(0, Math.min(text.length(), SpaceUtils.PREVIEW_TEXT_LENGTH))))
                        .build();
                SpaceAndroidUtils.mineFile(ComposeDocumentActivity.this, name, type, preview, new ByteArrayInputStream(text.getBytes(Charset.defaultCharset())));
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
            composeButton.setVisibility(View.VISIBLE);
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
                        final String website = SpaceAndroidUtils.getSpaceWebsite();
                        final String alias = SpaceAndroidUtils.getAlias();
                        final String email = intent.getStringExtra(SpaceAndroidUtils.EMAIL_EXTRA);
                        Log.d(SpaceUtils.TAG, "Email: " + email);
                        final String paymentId = intent.getStringExtra(SpaceAndroidUtils.STRIPE_TOKEN_EXTRA);
                        Log.d(SpaceUtils.TAG, "PaymentId: " + paymentId);
                        new Thread() {
                            @Override
                            public void run() {
                                String customerId = null;
                                try {
                                    customerId = BCUtils.register(website+"/space-register", alias, email, paymentId);
                                } catch (IOException e) {
                                    SpaceAndroidUtils.showErrorDialog(ComposeDocumentActivity.this, R.string.error_registering, e);
                                }
                                if (customerId != null && !customerId.isEmpty()) {
                                    Log.d(SpaceUtils.TAG, "Customer ID: " + customerId);
                                    SpaceAndroidUtils.mineFile(ComposeDocumentActivity.this, name, type, preview, in);
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
