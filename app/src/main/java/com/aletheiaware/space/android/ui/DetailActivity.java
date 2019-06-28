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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aletheiaware.alias.AliasProto;
import com.aletheiaware.alias.utils.AliasUtils;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Channel.RecordCallback;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.android.ui.AccessActivity;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Share;
import com.aletheiaware.space.SpaceProto.Tag;
import com.aletheiaware.space.android.MetaLoader;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.MinerUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils.ProviderCallback;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils.RegistrationCallback;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils.SubscriptionCallback;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class DetailActivity extends AppCompatActivity {

    private Cache cache;
    private Network network;
    private MetaLoader loader;

    private TextView nameTextView;
    private TextView typeTextView;
    private TextView sizeTextView;
    private TextView timestampTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SpaceAndroidUtils.createNotificationChannels(this);

        cache = BCAndroidUtils.getCache();

        // Setup UI
        setContentView(R.layout.activity_detail);

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
        if (BCAndroidUtils.isInitialized()) {
            final String alias = BCAndroidUtils.getAlias();
            final KeyPair keys = BCAndroidUtils.getKeyPair();
            new Thread() {
                @Override
                public void run() {
                    network = SpaceAndroidUtils.getStorageNetwork(DetailActivity.this, alias);
                }
            }.start();
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
                ByteString metaRecordHashByteString = ByteString.copyFrom(metaRecordHash);
                if (loader == null || !loader.getMetaRecordHash().equals(metaRecordHashByteString)) {
                    loader = new MetaLoader(this, alias, keys, cache, metaRecordHashByteString, shared) {
                        @Override
                        public void onMetaLoaded() {
                            loadUI();
                        }
                    };
                }
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
        if (SpaceUtils.isVideo(type)) {
            Log.d(SpaceUtils.TAG, "Setting Video");
            File videos = new File(getCacheDir(), "video");
            if (!videos.exists() && !videos.mkdirs()) {
                Log.e(SpaceUtils.TAG, "Error making video directory");
            }
            File f = new File(videos, new String(BCUtils.encodeBase64URL(loader.getMetaRecordHash().toByteArray())));
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
                    VideoViewFragment fragment = new VideoViewFragment();
                    fragment.setup(uri);
                    fragment.name = meta.getName();
                    fragment.type = meta.getType();
                    fragment.size = meta.getSize();
                    setContentFragment(fragment);
                }
            });
        } else if (SpaceUtils.isImage(type)) {
            Log.d(SpaceUtils.TAG, "Setting Image");
            File images = new File(getCacheDir(), "image");
            if (!images.exists() && !images.mkdirs()) {
                Log.e(SpaceUtils.TAG, "Error making image directory");
            }
            File f = new File(images, new String(BCUtils.encodeBase64URL(loader.getMetaRecordHash().toByteArray())));
            Log.d(SpaceUtils.TAG, "File");
            Log.d(SpaceUtils.TAG, "Path: " + f.getAbsolutePath());
            final Uri uri = FileProvider.getUriForFile(DetailActivity.this, getString(R.string.file_provider_authority), f);
            if (!f.exists() || f.length() < meta.getSize()) {
                writeFileToURI(uri);
            }
            Log.d(SpaceUtils.TAG, "Length: " + f.length());
            Drawable[] drawables = {null};
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
                    ImageViewFragment fragment = new ImageViewFragment();
                    fragment.setup(uri);
                    fragment.name = meta.getName();
                    fragment.type = meta.getType();
                    fragment.size = meta.getSize();
                    setContentFragment(fragment);
                    if (drawable != null) {
                        Log.d(SpaceUtils.TAG, "Setting Drawable:" + drawable);
                        fragment.drawable = drawable;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (drawable instanceof AnimatedImageDrawable) {
                                ((AnimatedImageDrawable) drawable).start();
                            }
                        }
                    }
                }
            });
        } else if (SpaceUtils.isText(type)) {
            Log.d(SpaceUtils.TAG, "Setting Text");
            final double size = meta.getSize();
            final double[] progress = {0};
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
            final StringBuilder sb = new StringBuilder();
            try {
                loader.readFile(new RecordCallback() {
                    @Override
                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                        if (payload != null && payload.length > 0) {
                            sb.append(new String(payload));
                            progress[0] += payload.length;
                            final double percent = (progress[0] / size) * 100.0;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar[0].setProgress((int) percent);
                                }
                            });
                        }
                        return true;
                    }
                });
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
            final String text = sb.toString();
            Log.d(SpaceUtils.TAG, "Text: " + text);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextViewFragment fragment = new TextViewFragment();
                    fragment.setup(meta.getName(), meta.getType(), meta.getSize(), text);
                    setContentFragment(fragment);
                }
            });
        }
        // TODO show tags - display current tags for document
        // TODO show previews - display current previews for document
    }

    private void setContentFragment(ContentFragment fragment) {
        setTitle(fragment.getName(this));
        nameTextView.setText(fragment.getName(this));
        typeTextView.setText(fragment.getType(this));
        sizeTextView.setText(BCUtils.sizeToString(fragment.getSize(this)));
        timestampTextView.setText(BCUtils.timeToString(loader.getTimestamp()));
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.detail_content_frame, fragment);
        ft.commit();
    }

    @WorkerThread
    private void writeFileToURI(Uri uri) {
        final double size = loader.getMeta().getSize();
        final double[] progress = {0};
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
                            progress[0] += payload.length;
                            final double percent = (progress[0] / size) * 100.0;
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
                            final long[] count = {0L};
                            new Thread() {
                                @Override
                                public void run() {
                                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                                        if (out != null) {
                                            Log.d(SpaceUtils.TAG, "Downloading to: " + uri.toString());
                                            loader.readFile(new RecordCallback() {
                                                @Override
                                                public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                                    try {
                                                        Log.d(SpaceUtils.TAG, "Writing: " + BCUtils.sizeToString(payload.length));
                                                        out.write(payload);
                                                        count[0] += payload.length;
                                                        final double percent = (count[0] / size) * 100.0;
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
                                            Log.d(SpaceUtils.TAG, "Downloaded: " + BCUtils.sizeToString(count[0]));
                                        }
                                    } catch (IOException ex) {
                                        /* Ignored */
                                        ex.printStackTrace();
                                    } finally {
                                        try {
                                            // Sleep for a second so user can see notification
                                            Thread.sleep(1000);
                                        } catch (InterruptedException ignored) {}
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

    @UiThread
    private void share() {
        new ShareDialog(DetailActivity.this, cache, network, loader.getMeta(), loader.isShared()) {
            @Override
            public void onShare(DialogInterface dialog, final AliasProto.Alias recipient) {
                new Thread() {
                    @Override
                    public void run() {
                        final String alias = BCAndroidUtils.getAlias();
                        final KeyPair keys = BCAndroidUtils.getKeyPair();
                        try {
                            final PublicKey recipientKey = AliasUtils.getPublicKey(cache, network, recipient.getAlias());
                            final Share.Builder sb = Share.newBuilder();
                            if (loader.isShared()) {
                                SpaceUtils.readShares(cache, network, alias, keys, null, loader.getMetaRecordHash(), null, new RecordCallback() {
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
                                SpaceUtils.readMetas(cache, network, alias, keys, loader.getMetaRecordHash(), new RecordCallback() {
                                    @Override
                                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                        for (Reference r : blockEntry.getRecord().getReferenceList()) {
                                            try {
                                                SpaceUtils.readFiles(cache, network, alias, keys, r.getRecordHash(), new RecordCallback() {
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
                            SpaceUtils.readPreviews(cache, network, alias, keys, null, loader.getMetaRecordHash(), new RecordCallback() {
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
                            String provider = SpaceAndroidUtils.getRemoteMinerPreference(DetailActivity.this, alias);
                            if (provider == null || provider.isEmpty()) {
                                showShareProviderPicker(alias, keys, cache, recipient.getAlias(), recipientKey, share);
                            } else {
                                share(alias, keys, cache, provider, recipient.getAlias(), recipientKey, share);
                            }
                        } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }
        }.create();
    }

    @WorkerThread
    private void share(final String alias, final KeyPair keys, final Cache cache, final String provider, final String recipientAlias, final PublicKey recipientKey, final Share share) {
        try {
            final Network network = SpaceAndroidUtils.getStorageNetwork(DetailActivity.this, alias);
            final Registration registration = FinanceUtils.getRegistration(cache, network, provider, null, alias, keys);
            Log.d(SpaceUtils.TAG, "Registration: " + registration);
            final Subscription subscriptionStorage = FinanceUtils.getSubscription(cache, network, provider, null, alias, keys, getString(R.string.stripe_subscription_storage_product), getString(R.string.stripe_subscription_storage_plan));
            Log.d(SpaceUtils.TAG, "Storage Subscription: " + subscriptionStorage);
            final Subscription subscriptionMining = FinanceUtils.getSubscription(cache, network, provider, null, alias, keys, getString(R.string.stripe_subscription_mining_product), getString(R.string.stripe_subscription_mining_plan));
            Log.d(SpaceUtils.TAG, "Mining Subscription: " + subscriptionMining);
            if (registration == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        SpaceAndroidUtils.registerSpaceCustomer(DetailActivity.this, provider, alias, new RegistrationCallback() {
                            @Override
                            public void onRegistered(final String customerId) {
                                Log.d(SpaceUtils.TAG, "Space Customer ID: " + customerId);
                                share(alias, keys, cache, provider, recipientAlias, recipientKey, share);
                            }
                        });
                    }
                });
            } else if (subscriptionStorage == null) {
                SpaceAndroidUtils.subscribeSpaceStorageCustomer(DetailActivity.this, provider, alias, registration.getCustomerId(), new SubscriptionCallback() {
                    @Override
                    public void onSubscribed(String subscriptionId) {
                        Log.d(SpaceUtils.TAG, "Space Storage Subscription ID: " + subscriptionId);
                        share(alias, keys, cache, provider, recipientAlias, recipientKey, share);
                    }
                });
            } else if (subscriptionMining == null) {
                SpaceAndroidUtils.subscribeSpaceMiningCustomer(DetailActivity.this, provider, alias, registration.getCustomerId(), new SubscriptionCallback() {
                    @Override
                    public void onSubscribed(String subscriptionId) {
                        Log.d(SpaceUtils.TAG, "Space Mining Subscription ID: " + subscriptionId);
                        share(alias, keys, cache, provider, recipientAlias, recipientKey, share);
                    }
                });
            } else {
                MinerUtils.mineShare(DetailActivity.this, "https://" + provider, recipientAlias, recipientKey, share);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setResult(Activity.RESULT_OK);
                        finish();
                    }
                });
            }
        } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
            e.printStackTrace();
        }
    }

    @UiThread
    private void showShareProviderPicker(final String alias, final KeyPair keys, final Cache cache, final String recipientAlias, final PublicKey recipientKey, final Share share) {
        SpaceAndroidUtils.showProviderPicker(DetailActivity.this, alias, new ProviderCallback() {
            @Override
            public void onProviderSelected(String provider) {
                share(alias, keys, cache, provider, recipientAlias, recipientKey, share);
            }

            @Override
            public void onCancelSelection() {
                // TODO
            }
        });
    }

    @UiThread
    private void tag() {
        final String alias = BCAndroidUtils.getAlias();
        final KeyPair keys = BCAndroidUtils.getKeyPair();
        new TagDialog(this, alias, keys, cache, network, loader.getMetaRecordHash(), loader.getMeta()) {
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
                String provider = SpaceAndroidUtils.getRemoteMinerPreference(DetailActivity.this, alias);
                if (provider == null || provider.isEmpty()) {
                    showTagProviderPicker(alias, keys, cache, reference, tag);
                } else {
                    tag(alias, keys, cache, provider, reference, tag);
                }
            }
        }.create();
    }

    @WorkerThread
    private void tag(final String alias, final KeyPair keys, final Cache cache, final String provider, final Reference reference, final Tag tag) {
        try {
            final Network network = SpaceAndroidUtils.getStorageNetwork(DetailActivity.this, alias);
            final Registration registration = FinanceUtils.getRegistration(cache, network, provider, null, alias, keys);
            Log.d(SpaceUtils.TAG, "Registration: " + registration);
            final Subscription subscriptionStorage = FinanceUtils.getSubscription(cache, network, provider, null, alias, keys, getString(R.string.stripe_subscription_storage_product), getString(R.string.stripe_subscription_storage_plan));
            Log.d(SpaceUtils.TAG, "Storage Subscription: " + subscriptionStorage);
            final Subscription subscriptionMining = FinanceUtils.getSubscription(cache, network, provider, null, alias, keys, getString(R.string.stripe_subscription_mining_product), getString(R.string.stripe_subscription_mining_plan));
            Log.d(SpaceUtils.TAG, "Mining Subscription: " + subscriptionMining);
            if (registration == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        SpaceAndroidUtils.registerSpaceCustomer(DetailActivity.this, provider, alias, new RegistrationCallback() {
                            @Override
                            public void onRegistered(final String customerId) {
                                Log.d(SpaceUtils.TAG, "Space Customer ID: " + customerId);
                                tag(alias, keys, cache, provider, reference, tag);
                            }
                        });
                    }
                });
            } else if (subscriptionStorage == null) {
                SpaceAndroidUtils.subscribeSpaceStorageCustomer(DetailActivity.this, provider, alias, registration.getCustomerId(), new SubscriptionCallback() {
                    @Override
                    public void onSubscribed(String subscriptionId) {
                        Log.d(SpaceUtils.TAG, "Space Storage Subscription ID: " + subscriptionId);
                        tag(alias, keys, cache, provider, reference, tag);
                    }
                });
            } else if (subscriptionMining == null) {
                SpaceAndroidUtils.subscribeSpaceMiningCustomer(DetailActivity.this, provider, alias, registration.getCustomerId(), new SubscriptionCallback() {
                    @Override
                    public void onSubscribed(String subscriptionId) {
                        Log.d(SpaceUtils.TAG, "Space Mining Subscription ID: " + subscriptionId);
                        tag(alias, keys, cache, provider, reference, tag);
                    }
                });
            } else {
                MinerUtils.mineTag(DetailActivity.this, "https://" + provider, reference, tag);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setResult(Activity.RESULT_OK);
                        finish();
                    }
                });
            }
        } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
            e.printStackTrace();
        }
    }

    @UiThread
    private void showTagProviderPicker(final String alias, final KeyPair keys, final Cache cache, final Reference reference, final Tag tag) {
        SpaceAndroidUtils.showProviderPicker(DetailActivity.this, alias, new ProviderCallback() {
            @Override
            public void onProviderSelected(String provider) {
                tag(alias, keys, cache, provider, reference, tag);
            }

            @Override
            public void onCancelSelection() {
                // TODO
            }
        });
    }
}
