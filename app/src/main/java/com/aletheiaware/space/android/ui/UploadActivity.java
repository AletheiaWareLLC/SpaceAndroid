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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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

public class UploadActivity extends AppCompatActivity {

    private EditText nameEditText;
    private TextView typeTextView;
    private TextView sizeTextView;
    private FrameLayout contentFrame;
    private ContentFragment contentFragment;

    private Button uploadButton;

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
        setContentView(R.layout.activity_upload);

        // Name EditText
        nameEditText = findViewById(R.id.upload_name);

        // Type TextView
        typeTextView = findViewById(R.id.upload_type);

        // Size TextView
        sizeTextView = findViewById(R.id.upload_size);

        // Content Frame
        contentFrame = findViewById(R.id.upload_content_frame);

        // Upload Button
        uploadButton = findViewById(R.id.upload_button);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setEditable(false);
                final String alias = BCAndroidUtils.getAlias();
                final KeyPair keys = BCAndroidUtils.getKeyPair();
                final Cache cache = BCAndroidUtils.getCache();
                final String name = nameEditText.getText().toString();
                final String type = typeTextView.getText().toString();
                final InputStream in = contentFragment.getInputStream(UploadActivity.this);
                final Preview preview = contentFragment.getPreview(UploadActivity.this);
                new MiningDialog(UploadActivity.this, alias, keys, cache, null) {
                    @Override
                    public void onMine(Miner miner, final Map<String, Registrar> registrars) {
                        RemoteMiningUtils.mineFileAndFinish(UploadActivity.this, miner, registrars,  name, type, preview, in);
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
    }

    private void setEditable(boolean editable) {
        uploadButton.setVisibility(editable ? View.VISIBLE : View.GONE);
        uploadButton.setEnabled(editable);
        nameEditText.setEnabled(editable);
        typeTextView.setEnabled(editable);
        sizeTextView.setEnabled(editable);
        contentFrame.setEnabled(editable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (BCAndroidUtils.isInitialized()) {
            Intent intent = getIntent();
            if (intent != null) {
                String action = intent.getAction();
                if (action == null) {
                    action = "";
                }
                Log.d(SpaceUtils.TAG, "Action:" + action);
                Uri uri = intent.getData();
                if (uri == null) {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                }
                Log.d(SpaceUtils.TAG, "URI:" + uri);
                String type = intent.getType();
                Log.d(SpaceUtils.TAG, "Type:" + type);
                if (type == null && uri != null) {
                    type = getContentResolver().getType(uri);
                    Log.i(SpaceUtils.TAG, "Type: " + type);
                    if (type == null) {
                        type = SpaceUtils.getTypeByExtension(uri.toString());
                        Log.i(SpaceUtils.TAG, "Type: " + type);
                    }
                }
                if (type == null) {
                    type = SpaceUtils.UNKNOWN_TYPE;
                } else if (type.equals("image/*")) {
                    type = SpaceUtils.DEFAULT_IMAGE_TYPE;
                } else if (type.equals("video/*")) {
                    type = SpaceUtils.DEFAULT_VIDEO_TYPE;
                }

                if (SpaceUtils.isText(type)) {
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    EditTextFragment fragment = new EditTextFragment();
                    fragment.setup(text, new TextWatcher() {
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
                    ImageViewFragment fragment = new ImageViewFragment();
                    fragment.setup(uri);
                    fragment.type = type;
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        fragment.bitmap = (Bitmap) extras.get(SpaceAndroidUtils.DATA_EXTRA);
                    }
                    setContentFragment(fragment);
                } else if (SpaceUtils.isVideo(type)) {
                    VideoViewFragment fragment = new VideoViewFragment();
                    fragment.setup(uri);
                    fragment.type = type;
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        fragment.bitmap = (Bitmap) extras.get(SpaceAndroidUtils.DATA_EXTRA);
                    }
                    setContentFragment(fragment);
                } else if (SpaceUtils.PDF_TYPE.equals(type)) {
                    PdfViewFragment fragment = new PdfViewFragment();
                    fragment.setup(uri);
                    fragment.type = type;
                    setContentFragment(fragment);
                } else {
                    GenericFileViewFragment fragment = new GenericFileViewFragment();
                    fragment.setup(uri);
                    fragment.type = type;
                    setContentFragment(fragment);
                }
                uploadButton.setVisibility(View.VISIBLE);
            }
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    private void setContentFragment(ContentFragment fragment) {
        contentFragment = fragment;
        nameEditText.setText(contentFragment.getName(this));
        typeTextView.setText(contentFragment.getType(this));
        updateSize(contentFragment.getSize(this));
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.upload_content_frame, fragment);
        ft.commit();
        nameEditText.requestFocus();
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
