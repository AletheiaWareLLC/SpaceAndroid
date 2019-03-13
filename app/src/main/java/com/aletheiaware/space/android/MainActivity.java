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
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.BC.Channel.RecordCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.SpaceProto.Share;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class MainActivity extends AppCompatActivity {

    private DatabaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_main);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.main_toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        // CollapsingToolbar
        final CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.main_collapsing_toolbar);
        collapsingToolbar.setTitle("");
        final CharSequence title = getString(R.string.title_activity_main);
        // Appbar
        AppBarLayout appbar = findViewById(R.id.main_appbar);
        appbar.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            boolean showing = false;
            @Override
            public void onOffsetChanged(AppBarLayout appBar, int verticalOffset) {
                if (Math.abs(appBar.getTotalScrollRange() + verticalOffset) < 10) {
                    collapsingToolbar.setTitle(title);
                    showing = true;
                } else if (showing) {
                    collapsingToolbar.setTitle("");
                    showing = false;
                }
            }
        });

        // RecyclerView
        RecyclerView recyclerView = findViewById(R.id.main_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Add FloatingActionButton
        FloatingActionButton addFab = findViewById(R.id.main_add_fab);
        addFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SpaceAndroidUtils.add(MainActivity.this);
            }
        });

        // Adapter
        adapter = new DatabaseAdapter(this) {
            @Override
            public void loadPreview(final ByteString metaRecordHash) {
                if (SpaceAndroidUtils.isInitialized()) {
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                final String alias = SpaceAndroidUtils.getAlias();
                                final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                                final InetAddress host = SpaceAndroidUtils.getHost();
                                final byte[] metaRecordHashBytes = metaRecordHash.toByteArray();
                                final Channel previews = new Channel(SpaceUtils.PREVIEW_CHANNEL_PREFIX + new String(BCUtils.encodeBase64URL(metaRecordHashBytes)), BCUtils.THRESHOLD_STANDARD, getCacheDir(), host);
                                try {
                                    previews.sync();
                                } catch (IOException | NoSuchAlgorithmException e) {
                                    /* Ignored */
                                    e.printStackTrace();
                                }
                                if (isShared(metaRecordHash)) {
                                    final Channel shares = new Channel(SpaceUtils.SHARE_CHANNEL_PREFIX + alias, BCUtils.THRESHOLD_STANDARD, getCacheDir(), host);
                                    try {
                                        shares.sync();
                                    } catch (IOException | NoSuchAlgorithmException e) {
                                        /* Ignored */
                                        e.printStackTrace();
                                    }
                                    shares.read(alias, keys, null, new RecordCallback() {
                                        @Override
                                        public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                            try {
                                                Share share = Share.newBuilder().mergeFrom(payload).build();
                                                Reference sharedMetaReference = share.getMetaReference();
                                                if (Arrays.equals(sharedMetaReference.getRecordHash().toByteArray(), metaRecordHashBytes)) {
                                                    int count = Math.min(share.getPreviewKeyCount(), share.getPreviewReferenceCount());
                                                    Preview preview = null;
                                                    for (int i = 0; i < count; i++) {
                                                        try {
                                                            Block b = BCUtils.getBlock(host, share.getPreviewReference(i));
                                                            if (b != null) {
                                                                for (BlockEntry e : b.getEntryList()) {
                                                                    try {
                                                                        byte[] previewKey = share.getPreviewKey(i).toByteArray();
                                                                        byte[] decryptedPayload = BCUtils.decryptAES(previewKey, e.getRecord().getPayload().toByteArray());
                                                                        Preview p = Preview.newBuilder().mergeFrom(decryptedPayload).build();
                                                                        // TODO choose best preview for screen size
                                                                        if (preview == null) {
                                                                            preview = p;
                                                                        }
                                                                    } catch (InvalidProtocolBufferException ex) {
                                                                        ex.printStackTrace();
                                                                    } catch (NoSuchPaddingException ex) {
                                                                        ex.printStackTrace();
                                                                    } catch (NoSuchAlgorithmException ex) {
                                                                        ex.printStackTrace();
                                                                    } catch (InvalidKeyException ex) {
                                                                        ex.printStackTrace();
                                                                    } catch (InvalidAlgorithmParameterException ex) {
                                                                        ex.printStackTrace();
                                                                    } catch (IllegalBlockSizeException ex) {
                                                                        ex.printStackTrace();
                                                                    } catch (BadPaddingException ex) {
                                                                        ex.printStackTrace();
                                                                    }
                                                                }
                                                            }
                                                        } catch (IOException e) {
                                                            e.printStackTrace();
                                                        }
                                                    }
                                                    if (preview != null) {
                                                        addPreview(metaRecordHash, preview);
                                                    }
                                                }
                                            } catch (InvalidProtocolBufferException e) {
                                                e.printStackTrace();
                                            }
                                            return true;
                                        }
                                    });
                                } else {
                                    previews.read(alias, keys, null, new RecordCallback() {
                                        @Override
                                        public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                            Preview preview = null;
                                            for (Reference r : blockEntry.getRecord().getReferenceList()) {
                                                if (r.getRecordHash().equals(metaRecordHash)) {
                                                    try {
                                                        Preview p = Preview.newBuilder().mergeFrom(payload).build();
                                                        // TODO choose best preview for screen size
                                                        if (preview == null) {
                                                            preview = p;
                                                        }
                                                    } catch (InvalidProtocolBufferException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                            if (preview != null) {
                                                addPreview(metaRecordHash, preview);
                                            }
                                            return true;
                                        }
                                    });
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                }
            }

            @Override
            public void onSelection(ByteString hash, Meta meta) {
                Intent i = new Intent(MainActivity.this, DetailActivity.class);
                i.putExtra(SpaceAndroidUtils.HASH_EXTRA, hash.toByteArray());
                i.putExtra(SpaceAndroidUtils.META_EXTRA, meta.toByteArray());
                i.putExtra(SpaceAndroidUtils.SHARED_EXTRA, isShared(hash));
                startActivityForResult(i, SpaceAndroidUtils.DETAIL_ACTIVITY);
            }
        };
        // TODO visually show files that are still getting mined
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            // TODO refresh();
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SpaceAndroidUtils.ACCESS_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        refresh();
                        break;
                    case RESULT_CANCELED:
                        setResult(RESULT_CANCELED);
                        finish();
                        break;
                    default:
                        break;
                }
                break;
            case SpaceAndroidUtils.ACCOUNT_ACTIVITY:
                refresh();
                break;
            case SpaceAndroidUtils.DETAIL_ACTIVITY:
                refresh();
                break;
            case SpaceAndroidUtils.OPEN_ACTIVITY:
                Log.d(SpaceUtils.TAG, "OPEN_ACTIVITY");
                switch (resultCode) {
                    case RESULT_OK:
                        Log.d(SpaceUtils.TAG, "Intent:" + intent);
                        if (intent != null) {
                            Uri uri = intent.getData();
                            Log.d(SpaceUtils.TAG, "URI:" + uri);
                            String type = intent.getType();
                            Log.d(SpaceUtils.TAG, "Type:" + type);
                            Intent i = new Intent(this, UploadActivity.class);
                            i.setAction(Intent.ACTION_SEND);
                            i.setDataAndType(uri, type);
                            startActivityForResult(i, SpaceAndroidUtils.UPLOAD_ACTIVITY);
                        }
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
                break;
            case SpaceAndroidUtils.COMPOSE_ACTIVITY:
                refresh();
                break;
            case SpaceAndroidUtils.UPLOAD_ACTIVITY:
                refresh();
                break;
            case SpaceAndroidUtils.CAPTURE_IMAGE_ACTIVITY:
                Log.d(SpaceUtils.TAG, "CAPTURE_IMAGE_ACTIVITY");
                // fallthrough
            case SpaceAndroidUtils.CAPTURE_VIDEO_ACTIVITY:
                Log.d(SpaceUtils.TAG, "CAPTURE_VIDEO_ACTIVITY");
                switch (resultCode) {
                    case RESULT_OK:
                        Log.d(SpaceUtils.TAG, "Intent:" + intent);
                        Uri uri = null;
                        String type = null;
                        Bitmap preview = null;
                        if (intent != null) {
                            uri = intent.getData();
                            type = intent.getType();
                            Bundle extras = intent.getExtras();
                            if (extras != null) {
                                preview = (Bitmap) extras.get(SpaceAndroidUtils.DATA_EXTRA);
                            }
                        }
                        if (uri == null) {
                            uri = SpaceAndroidUtils.getTempURI();
                        }
                        if (type == null) {
                            type = SpaceAndroidUtils.getTempURIType();
                        }
                        Log.d(SpaceUtils.TAG, "URI:" + uri);
                        Log.d(SpaceUtils.TAG, "Type:" + type);
                        Intent i = new Intent(this, UploadActivity.class);
                        i.setAction(Intent.ACTION_SEND);
                        if (preview != null) {
                            i.putExtra(SpaceAndroidUtils.DATA_EXTRA, preview);
                        }
                        i.setDataAndType(uri, type);
                        startActivityForResult(i, SpaceAndroidUtils.UPLOAD_ACTIVITY);
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
                break;
            case SpaceAndroidUtils.SETTINGS_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        break;
                    case RESULT_CANCELED:
                        // TODO
                        break;
                    default:
                        break;
                }
                refresh();
                break;
            default:
                super.onActivityResult(requestCode, resultCode, intent);
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                refresh();
                return true;
            case R.id.menu_account:
                account();
                return true;
            case R.id.menu_settings:
                settings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void refresh() {
        adapter.sort();
        adapter.notifyDataSetChanged();
        // TODO start refresh menu animate
        new Thread() {
            @Override
            public void run() {
                try {
                    final InetAddress address = SpaceAndroidUtils.getHost();
                    final File cache = getCacheDir();
                    final String alias = SpaceAndroidUtils.getAlias();
                    final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                    SpaceUtils.readMetas(address, cache, alias, keys, null, new RecordCallback() {
                        @Override
                        public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                            try {
                                Meta meta = Meta.newBuilder().mergeFrom(payload).build();
                                Log.d(SpaceUtils.TAG, "Meta: " + meta);
                                adapter.addFile(blockEntry.getRecordHash(), blockEntry.getRecord().getTimestamp(), meta, false);
                            } catch (InvalidProtocolBufferException e) {
                                e.printStackTrace();
                            }
                            return true;
                        }
                    });
                    SpaceUtils.readShares(address, cache, alias, keys, null, null, new RecordCallback() {
                        @Override
                        public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                            try {
                                Meta meta = Meta.newBuilder().mergeFrom(payload).build();
                                Log.d(SpaceUtils.TAG, "Shared Meta: " + meta);
                                adapter.addFile(blockEntry.getRecordHash(), blockEntry.getRecord().getTimestamp(), meta, true);
                            } catch (InvalidProtocolBufferException e) {
                                e.printStackTrace();
                            }
                            return true;
                        }
                    }, null);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // TODO stop refresh menu animate
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void account() {
        Intent i = new Intent(this, AccountActivity.class);
        startActivityForResult(i, SpaceAndroidUtils.ACCOUNT_ACTIVITY);
    }

    private void settings() {
        Intent i = new Intent(this, SettingsActivity.class);
        startActivityForResult(i, SpaceAndroidUtils.SETTINGS_ACTIVITY);
    }
}
