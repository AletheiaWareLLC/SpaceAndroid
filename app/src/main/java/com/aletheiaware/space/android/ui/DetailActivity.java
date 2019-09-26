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
import android.content.Intent;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aletheiaware.alias.AliasProto.Alias;
import com.aletheiaware.alias.utils.AliasUtils;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Channel.RecordCallback;
import com.aletheiaware.bc.PoWChannel;
import com.aletheiaware.bc.TCPNetwork;
import com.aletheiaware.bc.android.ui.AccessActivity;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.bc.utils.ChannelUtils;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.common.utils.CommonUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Miner;
import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.SpaceProto.Share;
import com.aletheiaware.space.SpaceProto.Tag;
import com.aletheiaware.space.android.MetaLoader;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.TagAdapter;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.aletheiaware.space.utils.SpaceUtils.RemoteMiningListener;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentTransaction;

public class DetailActivity extends AppCompatActivity {

    private Cache cache;
    private MetaLoader loader;

    private TextView nameTextView;
    private TextView typeTextView;
    private TextView sizeTextView;
    private TextView timestampTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        File file = new File(getCacheDir(), "file");
        if (!file.exists() && !file.mkdirs()) {
            Log.e(SpaceUtils.TAG, "Error making file directory");
        }
        File f = new File(file, new String(CommonUtils.encodeBase64URL(loader.getMetaRecordHash().toByteArray())));
        Log.d(SpaceUtils.TAG, "File");
        Log.d(SpaceUtils.TAG, "Path: " + f.getAbsolutePath());
        final Uri uri = FileProvider.getUriForFile(DetailActivity.this, getString(R.string.file_provider_authority), f);
        if (!f.exists() || f.length() < meta.getSize()) {
            writeDocumentToURI(uri);
        }
        Log.d(SpaceUtils.TAG, "Length: " + f.length());
        if (SpaceUtils.isVideo(type)) {
            Log.d(SpaceUtils.TAG, "Setting Video");
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
            final StringBuilder sb = new StringBuilder();
            try (Scanner in = new Scanner(getContentResolver().openInputStream(uri))) {
                while (in.hasNextLine()) {
                    sb.append(in.nextLine());
                    sb.append(System.lineSeparator());
                }
            } catch (IOException ex) {
                ex.printStackTrace();
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
        } else if (SpaceUtils.PDF_TYPE.equals(type)) {
            Log.d(SpaceUtils.TAG, "Setting PDF");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    PdfViewFragment fragment = new PdfViewFragment();
                    fragment.setup(uri);
                    fragment.name = meta.getName();
                    fragment.type = meta.getType();
                    fragment.size = meta.getSize();
                    setContentFragment(fragment);
                }
            });
        } else {
            Log.d(SpaceUtils.TAG, "Setting Generic File");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    GenericFileViewFragment fragment = new GenericFileViewFragment();
                    fragment.setup(uri);
                    fragment.name = meta.getName();
                    fragment.type = meta.getType();
                    fragment.size = meta.getSize();
                    setContentFragment(fragment);
                }
            });
        }
        // TODO show previews - display current previews for document
        // TODO show shares - display current shares for document
        // TODO show tags - display current tags for document
    }

    private void setContentFragment(ContentFragment fragment) {
        setTitle(fragment.getName(this));
        nameTextView.setText(fragment.getName(this));
        typeTextView.setText(fragment.getType(this));
        sizeTextView.setText(CommonUtils.binarySizeToString(fragment.getSize(this)));
        timestampTextView.setText(CommonUtils.timeToString(loader.getTimestamp()));
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.detail_content_frame, fragment);
        ft.commit();
    }

    @WorkerThread
    private void writeDocumentToURI(final Uri uri) {
        final double size = loader.getMeta().getSize();
        final double[] progress = {0};
        final ProgressBar[] progressBar = new ProgressBar[1];
        final Dialog[] dialog = new Dialog[1];
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                View progressView = View.inflate(DetailActivity.this, R.layout.dialog_progress, null);
                progressBar[0] = progressView.findViewById(R.id.progress_bar);
                TextView progressStatus = progressView.findViewById(R.id.progress_status);
                progressStatus.setVisibility(View.VISIBLE);
                progressStatus.setText(getString(R.string.detail_writing_uri, uri.toString()));
                dialog[0] = new AlertDialog.Builder(DetailActivity.this, R.style.AlertDialogTheme)
                        .setTitle(R.string.title_dialog_loading_document)
                        .setCancelable(false)
                        .setView(progressView)
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
                                    if (progressBar[0] != null) {
                                        progressBar[0].setProgress((int) percent);
                                    }
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
                }
                break;
            case SpaceAndroidUtils.DOWNLOAD_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        final Uri uri = data.getData();
                        if (uri != null) {
                            View progressView = View.inflate(DetailActivity.this, R.layout.dialog_progress, null);
                            final ProgressBar progressBar = progressView.findViewById(R.id.progress_bar);
                            final TextView progressStatus = progressView.findViewById(R.id.progress_status);
                            progressStatus.setVisibility(View.VISIBLE);
                            final AlertDialog progressDialog = new AlertDialog.Builder(DetailActivity.this, R.style.AlertDialogTheme)
                                    .setTitle(R.string.title_dialog_downloading_document)
                                    .setIcon(R.drawable.cloud_download)
                                    .setCancelable(false)
                                    .setView(progressView)
                                    .show();
                            progressStatus.setText(loader.getMeta().getName());
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
                                                        Log.d(SpaceUtils.TAG, "Writing: " + CommonUtils.binarySizeToString(payload.length));
                                                        out.write(payload);
                                                        count[0] += payload.length;
                                                        final double percent = (count[0] / size) * 100.0;
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                progressBar.setProgress((int) percent);
                                                            }
                                                        });
                                                    } catch (IOException e) {
                                                        /* Ignored */
                                                        e.printStackTrace();
                                                    }
                                                    return true;
                                                }
                                            });
                                            Log.d(SpaceUtils.TAG, "Downloaded: " + CommonUtils.binarySizeToString(count[0]));
                                        }
                                    } catch (IOException ex) {
                                        /* Ignored */
                                        ex.printStackTrace();
                                    } finally {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                // Dismiss download progress dialog
                                                if (progressDialog != null && progressDialog.isShowing()) {
                                                    progressDialog.dismiss();
                                                }
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
        final String alias = BCAndroidUtils.getAlias();
        final KeyPair keys = BCAndroidUtils.getKeyPair();
        final TCPNetwork network = loader.getNetwork();
        final ByteString metaRecordHash = loader.getMetaRecordHash();
        final Meta meta = loader.getMeta();
        final boolean shared = loader.isShared();
        new ShareMiningDialog(DetailActivity.this, cache, network, meta) {
            @Override
            @UiThread
            public void onShare(final Alias recipient) {
                View progressView = View.inflate(DetailActivity.this, R.layout.dialog_progress, null);
                final ProgressBar progressBar = progressView.findViewById(R.id.progress_bar);
                final TextView progressStatus = progressView.findViewById(R.id.progress_status);
                progressStatus.setVisibility(View.VISIBLE);
                final AlertDialog progressDialog = new AlertDialog.Builder(DetailActivity.this, R.style.AlertDialogTheme)
                        .setTitle(R.string.title_dialog_sharing_document)
                        .setIcon(R.drawable.share)
                        .setCancelable(false)
                        .setView(progressView)
                        .show();
                progressStatus.setText(meta.getName());
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            final PublicKey recipientKey = AliasUtils.getPublicKey(cache, network, recipient.getAlias());
                            final Share.Builder sb = Share.newBuilder();
                            if (shared) {
                                PoWChannel shares = SpaceUtils.getShareChannel(alias);
                                progressStatus.setText(R.string.detail_loading_share);
                                ChannelUtils.loadHead(shares, cache);
                                progressStatus.setText(R.string.detail_pulling_share);
                                try {
                                    ChannelUtils.pull(shares, cache, network);
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                }
                                progressStatus.setText(R.string.detail_reading_share);
                                SpaceUtils.readShares(shares, cache, network, alias, keys, null, metaRecordHash, null, new RecordCallback() {
                                    @Override
                                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                        sb.setMetaReference(Reference.newBuilder()
                                                .setTimestamp(blockEntry.getRecord().getTimestamp())
                                                .setChannelName(block.getChannelName())
                                                .setRecordHash(blockEntry.getRecordHash()));
                                        sb.setMetaKey(ByteString.copyFrom(key));
                                        // TODO update progress dialog
                                        return false;
                                    }
                                }, new RecordCallback() {
                                    @Override
                                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                        sb.addChunkKey(ByteString.copyFrom(key));
                                        // TODO update progress dialog
                                        return false;
                                    }
                                });
                            } else {
                                final PoWChannel files = SpaceUtils.getFileChannel(alias);
                                progressStatus.setText(R.string.detail_loading_file);
                                ChannelUtils.loadHead(files, cache, network);

                                PoWChannel metas = SpaceUtils.getMetaChannel(alias);
                                progressStatus.setText(R.string.detail_loading_meta);
                                ChannelUtils.loadHead(metas, cache);
                                progressStatus.setText(R.string.detail_pulling_meta);
                                try {
                                    ChannelUtils.pull(metas, cache, network);
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                }
                                progressStatus.setText(R.string.detail_reading_meta);
                                ChannelUtils.read(metas.getName(), metas.getHead(), null, cache, network, alias, keys, metaRecordHash, new RecordCallback() {
                                    @Override
                                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                        for (Reference r : blockEntry.getRecord().getReferenceList()) {
                                            try {
                                                ChannelUtils.read(files.getName(), files.getHead(), null, cache, network, alias, keys, r.getRecordHash(), new RecordCallback() {
                                                    @Override
                                                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                                        sb.addChunkKey(ByteString.copyFrom(key));
                                                        // TODO update progress dialog
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
                                        // TODO update progress dialog
                                        return false;
                                    }
                                });
                            }
                            PoWChannel previews = SpaceUtils.getPreviewChannel(metaRecordHash);
                            progressStatus.setText(R.string.detail_loading_preview);
                            ChannelUtils.loadHead(previews, cache);
                            progressStatus.setText(R.string.detail_pulling_preview);
                            try {
                                ChannelUtils.pull(previews, cache, network);
                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            }
                            progressStatus.setText(R.string.detail_reading_preview);
                            ChannelUtils.read(previews.getName(), previews.getHead(), null, cache, network, alias, keys, null, new RecordCallback() {
                                @Override
                                public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                    sb.addPreviewReference(Reference.newBuilder()
                                            .setTimestamp(blockEntry.getRecord().getTimestamp())
                                            .setChannelName(block.getChannelName())
                                            .setRecordHash(blockEntry.getRecordHash()));
                                    sb.addPreviewKey(ByteString.copyFrom(key));
                                    // TODO update progress dialog
                                    return false;
                                }
                            });
                            final Share share = sb.build();
                            Log.d(SpaceUtils.TAG, "Share: " + share);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    new MiningDialog(DetailActivity.this, alias, keys, cache, network) {
                                        @Override
                                        @WorkerThread
                                        public void onMine(Miner miner, final Map<String, Registrar> registrars) {
                                            // TODO show progress dialog
                                            final String minerAlias = miner.getMerchant().getAlias();
                                            final String website = "https://" + miner.getMerchant().getDomain();
                                            try {
                                                final Map<String, PublicKey> acl = new HashMap<>();
                                                acl.put(recipient.getAlias(), recipientKey);
                                                acl.put(alias, keys.getPublic());
                                                final List<Reference> shareReferences = new ArrayList<>();
                                                Record shareRecord = BCUtils.createRecord(alias, keys, acl, shareReferences, share.toByteArray());
                                                final Reference[] shareReference = {null};
                                                for (int i = 0; shareReference[0] == null && i < 5; i++) {
                                                    int results = 1 + 1;// 1 for sharer + 1 for each recipient
                                                    SpaceUtils.postRecord(website, "share", shareRecord, results, new RemoteMiningListener() {
                                                        @Override
                                                        public void onReference(Reference reference) {
                                                            shareReference[0] = reference;
                                                            // TODO update progress dialog
                                                        }

                                                        @Override
                                                        public void onBlock(ByteString hash, Block block) {
                                                            Log.d(SpaceUtils.TAG, "Mined Block: " + block);
                                                            // Write block to cache
                                                            cache.putBlock(hash, block);
                                                            for (String registrarAlias : registrars.keySet()) {
                                                                if (!registrarAlias.equals(minerAlias)) {
                                                                    Registrar registrar = registrars.get(registrarAlias);
                                                                    if (registrar != null) {
                                                                        try {
                                                                            InetAddress address = InetAddress.getByName(registrar.getMerchant().getDomain());
                                                                            network.cast(address, block.getChannelName(), cache, hash, block);
                                                                        } catch (UnknownHostException e) {
                                                                            e.printStackTrace();
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    });
                                                }
                                                if (shareReference[0] == null) {
                                                    // FIXME show error dialog with retry option
                                                    System.err.println("Failed to post share record 5 times");
                                                    return;
                                                }
                                                Log.d(SpaceUtils.TAG, "Uploaded Share " + new String(CommonUtils.encodeBase64URL(shareReference[0].getRecordHash().toByteArray())));
                                                // TODO update UI to show new Share
                                            } catch (SocketException | SocketTimeoutException e) {
                                                CommonAndroidUtils.showErrorDialog(DetailActivity.this, R.style.AlertDialogTheme, getString(R.string.error_connection, website), e);
                                            } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                                                CommonAndroidUtils.showErrorDialog(DetailActivity.this, R.style.AlertDialogTheme, R.string.error_uploading, e);
                                            } finally {
                                                // TODO dismiss progress dialog
                                            }
                                        }

                                        @Override
                                        public void onMiningCancelled() {
                                            // TODO
                                        }
                                    }.create();
                                }
                            });
                        } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        } finally {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // Dismiss download progress dialog
                                    if (progressDialog != null && progressDialog.isShowing()) {
                                        progressDialog.dismiss();
                                    }
                                }
                            });
                        }
                    }
                }.start();
            }
        }.create();
    }

    @UiThread
    private void tag() {
        final String alias = BCAndroidUtils.getAlias();
        final KeyPair keys = BCAndroidUtils.getKeyPair();
        final TCPNetwork network = loader.getNetwork();
        final TagAdapter adapter = new TagAdapter(DetailActivity.this, alias, keys, loader.getMetaRecordHash(), cache, network);
        final Reference reference = Reference.newBuilder()
                .setTimestamp(loader.getTimestamp())
                .setBlockHash(loader.getBlockHash())
                .setChannelName(loader.getChannelName())
                .setRecordHash(loader.getRecordHash())
                .build();
        new TagMiningDialog(DetailActivity.this, loader.getMeta(), adapter) {
            @Override
            @UiThread
            public void onTag(final Tag tag) {
                Log.d(SpaceUtils.TAG, "Tagging " + reference + " with " + tag);
                new MiningDialog(DetailActivity.this, alias, keys, cache, network) {
                    @Override
                    @WorkerThread
                    public void onMine(Miner miner, final Map<String, Registrar> registrars) {
                        // TODO show progress dialog
                        final String minerAlias = miner.getMerchant().getAlias();
                        final String website = "https://" + miner.getMerchant().getDomain();
                        try {
                            final Map<String, PublicKey> acl = new HashMap<>();
                            acl.put(alias, keys.getPublic());
                            final List<Reference> tagReferences = new ArrayList<>();
                            tagReferences.add(reference);
                            Record tagRecord = BCUtils.createRecord(alias, keys, acl, tagReferences, tag.toByteArray());
                            final Reference[] tagReference = {null};
                            for (int i = 0; tagReference[0] == null && i < 5; i++) {
                                SpaceUtils.postRecord(website, "tag", tagRecord, 1, new RemoteMiningListener() {
                                    @Override
                                    public void onReference(Reference reference) {
                                        tagReference[0] = reference;
                                        // TODO update progress dialog
                                    }

                                    @Override
                                    public void onBlock(ByteString hash, Block block) {
                                        Log.d(SpaceUtils.TAG, "Mined Block: " + block);
                                        // Write block to cache
                                        cache.putBlock(hash, block);
                                        for (String registrarAlias : registrars.keySet()) {
                                            if (!registrarAlias.equals(minerAlias)) {
                                                Registrar registrar = registrars.get(registrarAlias);
                                                if (registrar != null) {
                                                    try {
                                                        InetAddress address = InetAddress.getByName(registrar.getMerchant().getDomain());
                                                        network.cast(address, block.getChannelName(), cache, hash, block);
                                                    } catch (UnknownHostException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                        }
                                    }
                                });
                            }
                            if (tagReference[0] == null) {
                                // FIXME show error dialog with retry option
                                System.err.println("Failed to post tag record 5 times");
                                return;
                            }
                            Log.d(SpaceUtils.TAG, "Uploaded Tag " + new String(CommonUtils.encodeBase64URL(tagReference[0].getRecordHash().toByteArray())));
                            // TODO update UI to show new Tag
                        } catch (SocketException | SocketTimeoutException e) {
                            CommonAndroidUtils.showErrorDialog(DetailActivity.this, R.style.AlertDialogTheme, getString(R.string.error_connection, website), e);
                        } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                            CommonAndroidUtils.showErrorDialog(DetailActivity.this, R.style.AlertDialogTheme, R.string.error_uploading, e);
                        } finally {
                            // TODO dismiss progress dialog
                        }
                    }

                    @Override
                    public void onMiningCancelled() {
                        // TODO
                    }
                }.create();
            }
        }.create();
    }
}
