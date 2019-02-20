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
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import com.aletheiaware.bc.BC;
import com.aletheiaware.bc.BCProto;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.sql.Ref;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class DetailActivity extends AppCompatActivity {

    private Meta meta;
    private List<Reference> references = new ArrayList<>();

    private ImageView contentImageView;
    private VideoView contentVideoView;
    private TextView contentTextView;
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
        Toolbar toolbar = findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);

        // Content VideoView
        contentVideoView = findViewById(R.id.detail_video_view);

        // Content ImageView
        contentImageView = findViewById(R.id.detail_image_view);

        // Content TextView
        contentTextView = findViewById(R.id.detail_text_view);

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
            byte[] metaRecordHash = null;
            final Intent intent = getIntent();
            if (intent != null) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    metaRecordHash = extras.getByteArray(SpaceAndroidUtils.META_RECORD_HASH_EXTRA);
                }
            }
            if (metaRecordHash != null) {
                loadData(SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair(), metaRecordHash);
            }
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    private void loadData(final String alias, final KeyPair keys, final byte[] hash) {
        new Thread() {
            @Override
            public void run() {
                try {
                    final long[] timestamp = new long[1];
                    InetAddress address = InetAddress.getByName(SpaceUtils.SPACE_HOST);
                    BC.Channel metas = new BC.Channel(SpaceUtils.META_CHANNEL_PREFIX + alias, BCUtils.THRESHOLD_STANDARD, getCacheDir(), address);
                    BC.Channel files = new BC.Channel(SpaceUtils.FILE_CHANNEL_PREFIX + alias, BCUtils.THRESHOLD_STANDARD, getCacheDir(), address);
                    final Meta.Builder metaBuilder = Meta.newBuilder();
                    metas.read(alias, keys, hash, new BC.Channel.RecordCallback() {
                        @Override
                        public void onRecord(ByteString blockHash, BCProto.Block block, BCProto.BlockEntry blockEntry, byte[] key, byte[] payload) {
                            timestamp[0] = blockEntry.getRecord().getTimestamp();
                            references.addAll(blockEntry.getRecord().getReferenceList());
                            try {
                                metaBuilder.mergeFrom(payload);
                            } catch (InvalidProtocolBufferException e) {
                                SpaceAndroidUtils.showErrorDialog(DetailActivity.this, R.string.error_loading_metadata, e);
                                finish();
                            }
                        }
                    });
                    meta = metaBuilder.build();
                    // TODO if meta has preview, show it
                    String type = meta.getType();
                    if (SpaceUtils.isVideo(type)) {
                        Log.d(SpaceUtils.TAG, "Setting Video");
                        File f = new File(getCacheDir(), new String(BCUtils.encodeBase64URL(hash)));
                        Log.d(SpaceUtils.TAG, f.getAbsolutePath());
                        final Uri uri = FileProvider.getUriForFile(DetailActivity.this, SpaceAndroidUtils.FILE_PROVIDER_PACKAGE, f);
                        if (!f.exists()) {
                            writeToUri(uri, references);
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                contentVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                    @Override
                                    public void onCompletion(MediaPlayer mp) {
                                        Log.d(SpaceUtils.TAG, "onCompletion");
                                    }
                                });
                                contentVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                                    @Override
                                    public boolean onError(MediaPlayer mp, int what, int extra) {
                                        Log.d(SpaceUtils.TAG, "onError " + what + " " + extra);
                                        return true;
                                    }
                                });
                                contentVideoView.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                                    @Override
                                    public boolean onInfo(MediaPlayer mp, int what, int extra) {
                                        Log.d(SpaceUtils.TAG, "onInfo " + what + " " + extra);
                                        return true;
                                    }
                                });
                                contentVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                                    @Override
                                    public void onPrepared(MediaPlayer mp) {
                                        Log.d(SpaceUtils.TAG, "onPrepared");
                                    }
                                });
                                contentVideoView.setVisibility(View.VISIBLE);
                                contentVideoView.requestFocus();
                                final MediaController controller = new MediaController(DetailActivity.this);
                                controller.setMediaPlayer(contentVideoView);
                                controller.setAnchorView(contentVideoView);
                                contentVideoView.setMediaController(controller);
                                contentVideoView.setVideoURI(uri);
                                contentVideoView.setZOrderOnTop(true);
                                contentVideoView.start();
                                contentVideoView.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        if (controller.isShowing()) {
                                            controller.hide();
                                        } else {
                                            controller.show();
                                        }
                                    }
                                });
                                contentVideoView.requestLayout();
                                controller.show(3 * 1000);// Show for 3 seconds in ms
                            }
                        });
                    } else if (SpaceUtils.isImage(type)) {
                        Log.d(SpaceUtils.TAG, "Setting Image");
                        File f = new File(getCacheDir(), new String(BCUtils.encodeBase64URL(hash)));
                        Log.d(SpaceUtils.TAG, f.getAbsolutePath());
                        final Uri uri = FileProvider.getUriForFile(DetailActivity.this, SpaceAndroidUtils.FILE_PROVIDER_PACKAGE, f);
                        if (!f.exists()) {
                            writeToUri(uri, references);
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                contentImageView.setVisibility(View.VISIBLE);
                                contentImageView.setImageURI(uri);
                                contentImageView.requestLayout();
                            }
                        });
                    } else if (SpaceUtils.isText(type)) {
                        Log.d(SpaceUtils.TAG, "Setting Text");
                        final StringBuilder sb = new StringBuilder();
                        for (Reference r : references) {
                            files.read(alias, keys, r.getRecordHash().toByteArray(), new BC.Channel.RecordCallback() {
                                @Override
                                public void onRecord(ByteString blockHash, BCProto.Block block, BCProto.BlockEntry blockEntry, byte[] key, byte[] payload) {
                                    if (payload != null && payload.length > 0) {
                                        sb.append(new String(payload));
                                    }
                                }
                            });
                        }
                        final String text = sb.toString();
                        Log.d(SpaceUtils.TAG, "Text: " + text);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                contentTextView.setText(text);
                                contentTextView.setVisibility(View.VISIBLE);
                                contentTextView.requestLayout();
                            }
                        });
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (meta != null) {
                                nameTextView.setText(meta.getName());
                                typeTextView.setText(meta.getType());
                                sizeTextView.setText(BCUtils.sizeToString(meta.getSize()));
                            }
                            timestampTextView.setText(BCUtils.timeToString(timestamp[0]));

                            // TODO only show FAB when file data has been mined
                            fab.setVisibility(View.VISIBLE);
                        }
                    });
                } catch (UnknownHostException e) {
                    SpaceAndroidUtils.showErrorDialog(DetailActivity.this, R.string.error_loading_file, e);
                    finish();
                } catch (IOException e) {
                    SpaceAndroidUtils.showErrorDialog(DetailActivity.this, R.string.error_loading_file, e);
                    finish();
                }
            }
        }.start();
    }

    private void writeToUri(Uri uri, List<Reference> references) {
        OutputStream output = null;
        try {
            String alias = SpaceAndroidUtils.getAlias();
            KeyPair keys = SpaceAndroidUtils.getKeyPair();
            InetAddress address = InetAddress.getByName(SpaceUtils.SPACE_HOST);
            BC.Channel files = new BC.Channel(SpaceUtils.FILE_CHANNEL_PREFIX + alias, BCUtils.THRESHOLD_STANDARD, getCacheDir(), address);
            output = getContentResolver().openOutputStream(uri);

            if (output != null) {
                final OutputStream out = output;
                for (Reference r : references) {
                    files.read(alias, keys, r.getRecordHash().toByteArray(), new BC.Channel.RecordCallback() {
                        @Override
                        public void onRecord(ByteString blockHash, BCProto.Block block, BCProto.BlockEntry blockEntry, byte[] key, byte[] payload) {
                            if (payload != null && payload.length > 0) {
                                try {
                                    out.write(payload);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                }
            }
        } catch (IOException e) {
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
                                    writeToUri(uri, references);
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
