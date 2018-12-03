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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.aletheiaware.bc.BC.Reference;
import com.aletheiaware.space.Space.Meta;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class DetailActivity extends AppCompatActivity {

    private Meta meta;
    private Reference file;
    private Reference preview;

    private Toolbar toolbar;
    private ImageView previewImageView;
    private TextView previewTextView;
    private TextView nameTextView;
    private TextView typeTextView;
    private TextView sizeTextView;
    private TextView timestampTextView;
    private FloatingActionButton fab;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_detail);

        // Toolbar
        toolbar = findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);

        // Preview ImageView
        previewImageView = findViewById(R.id.detail_image_view);

        // Preview TextView
        previewTextView = findViewById(R.id.detail_text_view);

        // Name TextView
        nameTextView = findViewById(R.id.detail_name);

        // Type TextView
        typeTextView = findViewById(R.id.detail_type);

        // Size TextView
        sizeTextView = findViewById(R.id.detail_size);

        // Timestamp TextView
        timestampTextView = findViewById(R.id.detail_timestamp);

        // FloatingActionButton
        fab = findViewById(R.id.detail_fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(meta.getType());
                intent.putExtra(Intent.EXTRA_TITLE, meta.getName());
                startActivityForResult(intent, SpaceAndroidUtils.DOWNLOAD_ACTIVITY);
            }
        });
        fab.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            long timestamp = 0L;
            Meta.Builder mb = Meta.newBuilder();
            Reference.Builder fb = Reference.newBuilder();
            Reference.Builder pb = Reference.newBuilder();
            boolean hasPreview = false;
            Intent intent = getIntent();
            if (intent != null) {
                timestamp = intent.getLongExtra(SpaceAndroidUtils.TIMESTAMP_EXTRA, 0L);
                try {
                    byte[] metaBytes = intent.getByteArrayExtra(SpaceAndroidUtils.META_EXTRA);
                    if (metaBytes != null && metaBytes.length > 0) {
                        mb.mergeFrom(metaBytes);
                    }
                    byte[] fileBytes = intent.getByteArrayExtra(SpaceAndroidUtils.FILE_REFERENCE_EXTRA);
                    if (fileBytes != null && fileBytes.length > 0) {
                        fb.mergeFrom(fileBytes);
                    }
                    byte[] previewBytes = intent.getByteArrayExtra(SpaceAndroidUtils.PREVIEW_REFERENCE_EXTRA);
                    if (previewBytes != null && previewBytes.length > 0) {
                        hasPreview = true;
                        pb.mergeFrom(previewBytes);
                    }
                } catch (InvalidProtocolBufferException e) {
                    SpaceAndroidUtils.showErrorDialog(DetailActivity.this, R.string.error_loading_extras, e);
                    finish();
                }
            }
            meta = mb.build();
            file = fb.build();
            preview = pb.build();

            nameTextView.setText(meta.getName());
            typeTextView.setText(meta.getType());
            sizeTextView.setText(SpaceUtils.sizeToString(meta.getSize()));
            timestampTextView.setText(SpaceUtils.timeToString(timestamp));

            // TODO only show FAB when file data has been mined
            fab.setVisibility(View.VISIBLE);

            loadData(SpaceAndroidUtils.getKeys(), file, hasPreview);
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    private void loadData(final KeyPair keys, final Reference file, final boolean hasPreview) {
        new Thread() {
            @Override
            public void run() {
                try {
                    InetAddress address = InetAddress.getByName("space.aletheiaware.com");
                    byte[] data;
                    if (hasPreview) {
                        data = SpaceUtils.getMessageData(address, keys, preview);
                    } else {
                        data = SpaceUtils.getMessageData(address, keys, file);
                    }
                    if (data != null && data.length > 0) {
                        if (meta.getType().startsWith("image/")) {
                            Log.d(SpaceUtils.TAG, "Setting Image");
                            final Bitmap image = BitmapFactory.decodeStream(new ByteArrayInputStream(data));
                            if (image == null) {
                                SpaceAndroidUtils.showErrorDialog(DetailActivity.this, R.string.error_decoding_image, null);
                            } else {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        previewImageView.setImageBitmap(image);
                                        previewImageView.setVisibility(View.VISIBLE);
                                    }
                                });
                            }
                        } else if (meta.getType().startsWith("text/")) {
                            Log.d(SpaceUtils.TAG, "Setting Text");
                            final String text = new String(data);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    previewTextView.setText(text);
                                    previewTextView.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    }
                } catch (SocketException | SocketTimeoutException e) {
                    SpaceAndroidUtils.showErrorDialog(DetailActivity.this, R.string.error_connection, e);
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    SpaceAndroidUtils.showErrorDialog(DetailActivity.this, R.string.error_downloading_preview, e);
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
            case SpaceAndroidUtils.DOWNLOAD_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        final Uri uri = data.getData();
                        if (uri != null) {
                            new Thread() {
                                @Override
                                public void run() {
                                    OutputStream output = null;
                                    try {
                                        InetAddress address = InetAddress.getByName("space.aletheiaware.com");
                                        output = getContentResolver().openOutputStream(uri);

                                        byte[] data = SpaceUtils.getMessageData(address, SpaceAndroidUtils.getKeys(), file);
                                        if (output != null && data != null && data.length > 0) {
                                            output.write(data);
                                        }
                                    } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                                        SpaceAndroidUtils.showErrorDialog(DetailActivity.this, R.string.error_downloading, e);
                                    } finally {
                                        if (output != null) {
                                            try {
                                                output.close();
                                            } catch (IOException e) {
                                                /* Ignored */
                                            }
                                        }
                                    }
                                }
                            }.start();
                        }
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
