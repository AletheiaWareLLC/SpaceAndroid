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
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import com.aletheiaware.alias.AliasProto.Alias;
import com.aletheiaware.alias.utils.AliasUtils;
import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.BC.Channel.RecordCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Share;
import com.aletheiaware.space.android.utils.MinerUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.PublicKey;

public class ShareActivity extends AppCompatActivity {

    private AliasAdapter adapter;
    private MetaLoader loader;
    private TextView nameTextView;
    private AutoCompleteTextView aliasTextView;
    private FloatingActionButton shareFab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MinerUtils.createNotificationChannels(this);

        adapter = new AliasAdapter(this);

        // Setup UI
        setContentView(R.layout.activity_share);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.share_toolbar);
        setSupportActionBar(toolbar);

        // Name TextView
        nameTextView = findViewById(R.id.share_name);

        // Alias EditText
        aliasTextView = findViewById(R.id.share_alias);
        aliasTextView.setAdapter(adapter);
        aliasTextView.setThreshold(3);

        // Share Fab
        shareFab = findViewById(R.id.share_fab);
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
        Meta meta = loader.getMeta();
        if (meta == null) {
            return;
        }
        nameTextView.setText(meta.getName());
        aliasTextView.setEnabled(true);
        shareFab.setEnabled(true);
        shareFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String recipient = aliasTextView.getText().toString();
                Log.d(SpaceUtils.TAG, "Recipient: " + recipient);
                if (!recipient.isEmpty()) {
                    final Alias r = adapter.get(recipient);
                    if (r != null) {
                        aliasTextView.setEnabled(false);
                        shareFab.setEnabled(false);
                        shareFab.hide();
                        new Thread() {
                            @Override
                            public void run() {
                                final String alias = SpaceAndroidUtils.getAlias();
                                final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                                final File cache = getCacheDir();
                                final InetAddress host = SpaceAndroidUtils.getHost();
                                final Channel aliases = new Channel(AliasUtils.ALIAS_CHANNEL, BCUtils.THRESHOLD_STANDARD, cache, host);
                                try {
                                    final PublicKey recipientKey = AliasUtils.getPublicKey(aliases, recipient);
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
                                        final Channel files = new Channel(SpaceUtils.FILE_CHANNEL_PREFIX + alias, BCUtils.THRESHOLD_STANDARD, cache, host);
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
                                            SpaceAndroidUtils.mineShare(ShareActivity.this, recipient, recipientKey, share);
                                            setResult(RESULT_OK);
                                            finish();
                                        }
                                    });
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                    }
                }
            }
        });
        shareFab.show();
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
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
