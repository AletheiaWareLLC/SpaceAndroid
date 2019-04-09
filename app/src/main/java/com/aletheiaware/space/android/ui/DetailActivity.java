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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.aletheiaware.alias.AliasProto;
import com.aletheiaware.alias.utils.AliasUtils;
import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.BC.Channel.RecordCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Share;
import com.aletheiaware.space.SpaceProto.Tag;
import com.aletheiaware.space.android.MetaLoader;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.PublicKey;

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
        SpaceAndroidUtils.createNotificationChannels(this);

        // Setup UI
        setContentView(R.layout.activity_detail);

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
                    public void onMetaLoaded() {
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
                setTitle(meta.getName());
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
            File f = new File(videos, new String(BCUtils.encodeBase64URL(loader.getMetaRecordHash())));
            Log.d(SpaceUtils.TAG, "File");
            Log.d(SpaceUtils.TAG, "Path: " + f.getAbsolutePath());
            final Uri uri = FileProvider.getUriForFile(DetailActivity.this, getString(R.string.file_provider_authority), f);
            if (!f.exists() || f.length() < meta.getSize()) {
                writeFileToURI(uri);
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
            File f = new File(images, new String(BCUtils.encodeBase64URL(loader.getMetaRecordHash())));
            Log.d(SpaceUtils.TAG, "File");
            Log.d(SpaceUtils.TAG, "Path: " + f.getAbsolutePath());
            final Uri uri = FileProvider.getUriForFile(DetailActivity.this, getString(R.string.file_provider_authority), f);
            if (!f.exists() || f.length() < meta.getSize()) {
                writeFileToURI(uri);
            }
            Log.d(SpaceUtils.TAG, "Length: " + f.length());
            Drawable[] drawables = { null };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                try {
                    drawables[0] = ImageDecoder.decodeDrawable(source, new ImageDecoder.OnHeaderDecodedListener() {
                        @Override
                        public void onHeaderDecoded(@NonNull ImageDecoder decoder, @NonNull ImageDecoder.ImageInfo info, @NonNull ImageDecoder.Source source) {
                            decoder.setOnPartialImageListener(new ImageDecoder.OnPartialImageListener() {
                                @Override
                                public boolean onPartialImage(@NonNull ImageDecoder.DecodeException e) {
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
        // TODO show tags - display current tags for document
        // TODO show previews - display current previews for document
    }

    private void writeFileToURI(Uri uri) {
        final double size = loader.getMeta().getSize();
        final ProgressBar[] progressBar = new ProgressBar[1];
        final Dialog[] dialog = new Dialog[1];
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                View progressView = View.inflate(DetailActivity.this, R.layout.dialog_progress, null);
                progressBar[0] = progressView.findViewById(R.id.progress);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    progressBar[0].setMin(0);
                }
                progressBar[0].setMax(100);
                dialog[0] = new AlertDialog.Builder(DetailActivity.this, R.style.AlertDialogTheme)
                        .setTitle(R.string.title_dialog_loading_document)
                        .setCancelable(false)
                        .setView(progressBar[0])
                        .show();
            }
        });
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            Log.d(SpaceUtils.TAG, "Writing to: " + uri.toString());
            if (out != null) {
                loader.readFile(new RecordCallback() {
                    @Override
                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                        try {
                            Log.d(SpaceUtils.TAG, "Writing: " + payload.length);
                            out.write(payload);
                            final double percent = (payload.length / size) * 100.0;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar[0].setProgress((int) percent);
                                }
                            });
                        } catch (IOException e) {
                            /* Ignored */
                            e.printStackTrace();
                        }
                        return true;
                    }
                });
            }
        } catch (IOException ex) {
            /* Ignored */
            ex.printStackTrace();
        } finally {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (dialog[0].isShowing()) {
                        dialog[0].dismiss();
                    }
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
                            // Create download notification
                            final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SpaceAndroidUtils.DOWNLOAD_CHANNEL_ID)
                                    .setSmallIcon(R.drawable.cloud_download)
                                    .setContentTitle(getString(R.string.title_notification_download))
                                    .setContentText(loader.getMeta().getName())
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .setProgress(100, 0, false)
                                    .setAutoCancel(true)
                                    .setTimeoutAfter(SpaceAndroidUtils.NOTIFICATION_TIMEOUT);

                            final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
                            notificationManager.notify(SpaceAndroidUtils.DOWNLOAD_NOTIFICATION_ID, builder.build());

                            final double size = loader.getMeta().getSize();
                            new Thread() {
                                @Override
                                public void run() {
                                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                                        Log.d(SpaceUtils.TAG, "Downloading to: " + uri.toString());
                                        if (out != null) {
                                            loader.readFile(new RecordCallback() {
                                                @Override
                                                public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                                    try {
                                                        Log.d(SpaceUtils.TAG, "Downloading: " + payload.length);
                                                        out.write(payload);
                                                        final double percent = (payload.length / size) * 100.0;
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                builder.setProgress(100, (int) percent, false);
                                                                notificationManager.notify(SpaceAndroidUtils.DOWNLOAD_NOTIFICATION_ID, builder.build());
                                                            }
                                                        });
                                                    } catch (IOException e) {
                                                        /* Ignored */
                                                        e.printStackTrace();
                                                    }
                                                    return true;
                                                }
                                            });
                                        }
                                    } catch (IOException ex) {
                                        /* Ignored */
                                        ex.printStackTrace();
                                    } finally {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                // Dismiss download notification
                                                notificationManager.cancel(SpaceAndroidUtils.DOWNLOAD_NOTIFICATION_ID);
                                            }
                                        });
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
            // TODO add option to generate new preview
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
        new ShareDialog(DetailActivity.this, loader.getMetaRecordHash(), loader.getMeta(), loader.isShared()) {
            @Override
            public void onShare(DialogInterface dialog, final AliasProto.Alias recipient) {
                new Thread() {
                    @Override
                    public void run() {
                        final String alias = SpaceAndroidUtils.getAlias();
                        final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                        final File cache = getCacheDir();
                        final InetAddress host = SpaceAndroidUtils.getSpaceHost();
                        final Channel aliases = new Channel(AliasUtils.ALIAS_CHANNEL, BCUtils.THRESHOLD_STANDARD, cache, host);
                        try {
                            final PublicKey recipientKey = AliasUtils.getPublicKey(aliases, recipient.getAlias());
                            final Share.Builder sb = Share.newBuilder();
                            if (loader.isShared()) {
                                SpaceUtils.readShares(host, cache, alias, keys, null, loader.getMetaRecordHash(), new RecordCallback() {
                                    @Override
                                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                        sb.setMetaReference(Reference.newBuilder()
                                                .setTimestamp(blockEntry.getRecord().getTimestamp())
                                                .setChannelName(block.getChannelName())
                                                .setRecordHash(blockEntry.getRecordHash()));
                                        sb.setMetaKey(ByteString.copyFrom(key));
                                        return false;
                                    }
                                }, new RecordCallback() {
                                    @Override
                                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                        sb.addChunkKey(ByteString.copyFrom(key));
                                        return false;
                                    }
                                });
                            } else {
                                final Channel files = new Channel(SpaceUtils.SPACE_PREFIX_FILE + alias, BCUtils.THRESHOLD_STANDARD, cache, host);
                                SpaceUtils.readMetas(host, cache, alias, keys, loader.getMetaRecordHash(), new RecordCallback() {
                                    @Override
                                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                        for (Reference r : blockEntry.getRecord().getReferenceList()) {
                                            try {
                                                files.read(alias, keys, r.getRecordHash().toByteArray(), new RecordCallback() {
                                                    @Override
                                                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                                        sb.addChunkKey(ByteString.copyFrom(key));
                                                        return false;
                                                    }
                                                });
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        sb.setMetaReference(Reference.newBuilder()
                                                .setTimestamp(blockEntry.getRecord().getTimestamp())
                                                .setChannelName(block.getChannelName())
                                                .setRecordHash(blockEntry.getRecordHash()));
                                        sb.setMetaKey(ByteString.copyFrom(key));
                                        return false;
                                    }
                                });
                            }
                            SpaceUtils.readPreviews(host, cache, alias, keys, null, loader.getMetaRecordHash(), new RecordCallback() {
                                @Override
                                public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                    sb.addPreviewReference(Reference.newBuilder()
                                            .setTimestamp(blockEntry.getRecord().getTimestamp())
                                            .setChannelName(block.getChannelName())
                                            .setRecordHash(blockEntry.getRecordHash()));
                                    sb.addPreviewKey(ByteString.copyFrom(key));
                                    return false;
                                }
                            });
                            final Share share = sb.build();
                            Log.d(SpaceUtils.TAG, "Share: " + share);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    SpaceAndroidUtils.mineShare(DetailActivity.this, recipient.getAlias(), recipientKey, share);
                                }
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }
        }.create();
    }

    private void tag() {
        new TagDialog(this, SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair(), loader.getMetaRecordHash(), loader.getMeta(), loader.isShared()) {
            @Override
            public void onTag(DialogInterface dialog, String value, String reason) {
                Tag.Builder tb = Tag.newBuilder()
                        .setValue(value);
                if (!reason.isEmpty()) {
                    tb.setReason(reason);
                }
                final Tag tag = tb.build();
                final Reference reference = Reference.newBuilder()
                        .setTimestamp(loader.getTimestamp())
                        .setBlockHash(loader.getBlockHash())
                        .setChannelName(loader.getChannelName())
                        .setRecordHash(loader.getRecordHash())
                        .build();
                Log.d(SpaceUtils.TAG, "Tagging " + reference + " with " + tag);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        SpaceAndroidUtils.mineTag(DetailActivity.this, reference, tag);
                    }
                });
            }
        }.create();
    }
}
