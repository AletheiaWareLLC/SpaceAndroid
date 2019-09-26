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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.android.ui.AccessActivity;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.common.utils.CommonUtils;
import com.aletheiaware.space.SpaceProto.Miner;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.RemoteMiningUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.InputStream;
import java.security.KeyPair;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

public class ComposeDocumentActivity extends AppCompatActivity {

    private EditText nameEditText;
    private ArrayAdapter<CharSequence> typeAdapter;
    private Spinner typeSpinner;
    private TextView sizeTextView;
    private FrameLayout contentFrame;
    private ContentFragment contentFragment;
    private Button composeButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO add menu items
        // TODO - Preview - choose which preview sizes to generate and mine after meta. Consider adding default preview sizes to settings.
        // TODO - Tag - choose which tags to apply and mine after meta. Consider adding default tags to settings.
        // TODO - Reference - choose which documents to reference. Consider adding default references to settings.
        // TODO - Security - choose which compression, encryption, and signature algorithms to use. Consider adding default algorithms to settings.

        // TODO Add access spinner - choose from private, public, or a list of recipients with whom to grant access and mine after meta.

        // Setup UI
        setContentView(R.layout.activity_compose_document);

        // Name EditText
        nameEditText = findViewById(R.id.compose_document_name);
        // Precomplete EditText with generated name
        nameEditText.setText(getString(R.string.default_document_name, System.currentTimeMillis()));

        typeAdapter = ArrayAdapter.createFromResource(this, R.array.mime_types, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Type Spinner
        typeSpinner = findViewById(R.id.compose_document_type);
        typeSpinner.setAdapter(typeAdapter);
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setContentFragment(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                setContentFragment(-1);
            }
        });

        // Size TextView
        sizeTextView = findViewById(R.id.compose_document_size);
        sizeTextView.setText(CommonUtils.binarySizeToString(0));

        // Content Frame
        contentFrame = findViewById(R.id.compose_document_content_frame);

        // Compose Button
        composeButton = findViewById(R.id.compose_document_button);
        composeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setEditable(false);
                final String alias = BCAndroidUtils.getAlias();
                final KeyPair keys = BCAndroidUtils.getKeyPair();
                final Cache cache = BCAndroidUtils.getCache();
                final String name = nameEditText.getText().toString();
                final String type = typeSpinner.getSelectedItem().toString();
                final InputStream in = contentFragment.getInputStream(ComposeDocumentActivity.this);
                final Preview preview = contentFragment.getPreview(ComposeDocumentActivity.this);
                new MiningDialog(ComposeDocumentActivity.this, alias, keys, cache, null) {
                    @Override
                    public void onMine(Miner miner, final Map<String, Registrar> registrars) {
                        RemoteMiningUtils.mineFileAndFinish(ComposeDocumentActivity.this, miner, registrars,  name, type, preview, in);
                    }

                    @Override
                    public void onMiningCancelled() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setEditable(true);
                            }
                        });
                    }
                }.create();
            }
        });

        setContentFragment(0);
    }

    private void setEditable(boolean editable) {
        composeButton.setVisibility(editable ? View.VISIBLE : View.GONE);
        composeButton.setEnabled(editable);
        nameEditText.setEnabled(editable);
        typeSpinner.setEnabled(editable);
        sizeTextView.setEnabled(editable);
        contentFrame.setEnabled(editable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (BCAndroidUtils.isInitialized()) {
            Intent intent = getIntent();
            if (intent != null) {
                Log.d(SpaceUtils.TAG, "Intent: " + intent.toString());
                String action = intent.getAction();
                if (action == null) {
                    action = "";
                }
                Log.d(SpaceUtils.TAG, "Action:" + action);
            }
            composeButton.setVisibility(View.VISIBLE);
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    private void setContentFragment(int position) {
        String type = "";
        CharSequence item = typeAdapter.getItem(position);
        if (item != null) {
            type = item.toString();
        }
        if (SpaceUtils.isText(type)) {
            EditTextFragment fragment = new EditTextFragment();
            fragment.setup("", new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    updateSize(s.length() * 2);// 16bit char == 2 * 8bit byte
                }
            });
            setContentFragment(fragment);
        } else if (SpaceUtils.isImage(type)) {
            // TODO
        } else if (SpaceUtils.isVideo(type)) {
            // TODO
        } else {
            // TODO change frame to empty view - "Select type above"
        }
    }

    private void setContentFragment(ContentFragment fragment) {
        contentFragment = fragment;
        typeSpinner.setSelection(typeAdapter.getPosition(contentFragment.getType(this)));
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.compose_document_content_frame, fragment);
        ft.commit();
    }

    private void updateSize(long size) {
        sizeTextView.setText(CommonUtils.binarySizeToString(size));
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
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, intent);
                break;
        }
    }
}
