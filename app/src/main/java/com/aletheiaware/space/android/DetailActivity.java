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
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import com.aletheiaware.bc.BC.Channel.RecordCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.IOException;

public class DetailActivity extends AppCompatActivity {

    private MetaLoader loader;

    private ImageView contentImageView;
    private VideoView contentVideoView;
    private TextView contentTextView;
    private TextView nameTextView;
    private TextView typeTextView;
    private TextView sizeTextView;
    private TextView timestampTextView;

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
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            byte[] metaRecordHash = null;
            boolean shared = false;
            final Intent intent = getIntent();
            if (intent != null) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    metaRecordHash = extras.getByteArray(SpaceAndroidUtils.HASH_EXTRA);
                    shared = extras.getBoolean(SpaceAndroidUtils.SHARED_EXTRA, false);
                }
            }
            if (metaRecordHash != null) {
                loader = new MetaLoader(this, metaRecordHash, shared) {
                    @Override
                    void onMetaLoaded() {
                        loadUI();
                    }
                };
            }
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    private void loadUI() {
        if (loader == null) {
            return;
        }
        final Meta meta = loader.getMeta();
        if (meta == null) {
            return;
        }
        final String type = meta.getType();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                nameTextView.setText(meta.getName());
                typeTextView.setText(type);
                sizeTextView.setText(BCUtils.sizeToString(meta.getSize()));
                timestampTextView.setText(BCUtils.timeToString(loader.getTimestamp()));
            }
        });
        if (SpaceUtils.isVideo(type)) {
            Log.d(SpaceUtils.TAG, "Setting Video");
            File videos = new File(getCacheDir(), "video");
            if (!videos.exists() &&!videos.mkdirs()) {
                Log.e(SpaceUtils.TAG, "Error making video directory");
            }
            File f = new File(videos, new String(BCUtils.encodeBase64URL(loader.getRecordHash())));
            Log.d(SpaceUtils.TAG, "File");
            Log.d(SpaceUtils.TAG, "Path: " + f.getAbsolutePath());
            final Uri uri = FileProvider.getUriForFile(DetailActivity.this, getString(R.string.file_provider_authority), f);
            if (!f.exists() || f.length() == 0) {
                try {
                    loader.writeFileToURI(uri);
                } catch (IOException ex) {
                    /* Ignored */
                    ex.printStackTrace();
                }
            }
            Log.d(SpaceUtils.TAG, "Length: " + f.length());
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
            File images = new File(getCacheDir(), "image");
            if (!images.exists() && !images.mkdirs()) {
                Log.e(SpaceUtils.TAG, "Error making image directory");
            }
            File f = new File(images, new String(BCUtils.encodeBase64URL(loader.getRecordHash())));
            Log.d(SpaceUtils.TAG, "File");
            Log.d(SpaceUtils.TAG, "Path: " + f.getAbsolutePath());
            final Uri uri = FileProvider.getUriForFile(DetailActivity.this, getString(R.string.file_provider_authority), f);
            if (!f.exists() || f.length() == 0) {
                try {
                    loader.writeFileToURI(uri);
                } catch (IOException ex) {
                    /* Ignored */
                    ex.printStackTrace();
                }
            }
            Log.d(SpaceUtils.TAG, "Length: " + f.length());
            Drawable[] drawables = { null };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                    drawables[0] = ImageDecoder.decodeDrawable(source, new ImageDecoder.OnHeaderDecodedListener() {
                        @Override
                        public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info, ImageDecoder.Source source) {
                            decoder.setOnPartialImageListener(new ImageDecoder.OnPartialImageListener() {
                                @Override
                                public boolean onPartialImage(ImageDecoder.DecodeException e) {
                                    e.printStackTrace();
                                    return true;
                                }
                            });
                            Log.d(SpaceUtils.TAG, "Image Colour Space: " + info.getColorSpace());
                            Log.d(SpaceUtils.TAG, "Image Is Animated: " + info.isAnimated());
                            Log.d(SpaceUtils.TAG, "Image Mime Type: " + info.getMimeType());
                            Log.d(SpaceUtils.TAG, "Image Size: " + info.getSize());
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            final Drawable drawable = drawables[0];
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    contentImageView.setVisibility(View.VISIBLE);
                    if (drawable != null) {
                        Log.d(SpaceUtils.TAG, "Setting Drawable:" + drawable);
                        contentImageView.setImageDrawable(drawable);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (drawable instanceof AnimatedImageDrawable) {
                                ((AnimatedImageDrawable) drawable).start();
                            }
                        }
                    } else {
                        Log.d(SpaceUtils.TAG, "Setting URI:" + uri);
                        contentImageView.setImageURI(uri);
                    }
                    contentImageView.requestLayout();
                }
            });
        } else if (SpaceUtils.isText(type)) {
            Log.d(SpaceUtils.TAG, "Setting Text");
            final StringBuilder sb = new StringBuilder();
            try {
                loader.readFile(new RecordCallback() {
                    @Override
                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                        if (payload != null && payload.length > 0) {
                            sb.append(new String(payload));
                        }
                        return true;
                    }
                });
            } catch (IOException ex) {
                /* Ignored */
                ex.printStackTrace();
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
                                    try {
                                        loader.writeFileToURI(uri);
                                    } catch (IOException ex) {
                                        /* Ignored */
                                        ex.printStackTrace();
                                    }
                                }
                            }.start();
                        }
                    }
                }
                break;
            case SpaceAndroidUtils.PREVIEW_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
                break;
            case SpaceAndroidUtils.SHARE_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
                break;
            case SpaceAndroidUtils.TAG_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.menu_download:
                download();
                return true;
            case R.id.menu_share:
                share();
                return true;
            case R.id.menu_tag:
                tag();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void download() {
        Meta meta = loader.getMeta();
        if (meta != null) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(meta.getType());
            intent.putExtra(Intent.EXTRA_TITLE, meta.getName());
            startActivityForResult(intent, SpaceAndroidUtils.DOWNLOAD_ACTIVITY);
        }
    }

    private void share() {
        Intent i = new Intent(DetailActivity.this, ShareActivity.class);
        i.putExtra(SpaceAndroidUtils.HASH_EXTRA, loader.getRecordHash());
        i.putExtra(SpaceAndroidUtils.SHARED_EXTRA, loader.isShared());
        startActivityForResult(i, SpaceAndroidUtils.SHARE_ACTIVITY);
    }

    private void tag() {
        Intent i = new Intent(DetailActivity.this, TagActivity.class);
        i.putExtra(SpaceAndroidUtils.HASH_EXTRA, loader.getRecordHash());
        i.putExtra(SpaceAndroidUtils.SHARED_EXTRA, loader.isShared());
        startActivityForResult(i, SpaceAndroidUtils.TAG_ACTIVITY);
    }
}
