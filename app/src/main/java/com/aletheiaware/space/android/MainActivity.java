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
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public class MainActivity extends AppCompatActivity {

    private DatabaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.main_toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);
        //account
        //refresh

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
                    Log.d(SpaceUtils.TAG, "Loading Preview for " + new String(BCUtils.encodeBase64URL(metaRecordHash.toByteArray())));
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                String alias = SpaceAndroidUtils.getAlias();
                                KeyPair keys = SpaceAndroidUtils.getKeyPair();
                                InetAddress address = InetAddress.getByName(SpaceUtils.SPACE_HOST);
                                Channel previewChannel = new Channel(SpaceUtils.PREVIEW_CHANNEL_PREFIX + new String(BCUtils.encodeBase64URL(metaRecordHash.toByteArray())), BCUtils.THRESHOLD_STANDARD, getCacheDir(), address);
                                previewChannel.read(alias, keys, null, new RecordCallback() {
                                    @Override
                                    public void onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                        Log.d(SpaceUtils.TAG, "Preview Block " + new String(BCUtils.encodeBase64URL(blockHash.toByteArray())));
                                        Preview preview = null;
                                        for (Reference r : blockEntry.getRecord().getReferenceList()) {
                                            if (r.getRecordHash().equals(metaRecordHash)) {
                                                try {
                                                    Preview p = Preview.newBuilder().mergeFrom(payload).build();
                                                    Log.d(SpaceUtils.TAG, "Found Preview " + p);
                                                    // TODO choose best preview
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
                                    }
                                });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                }
            }

            @Override
            public void onSelection(ByteString metaRecordHash, Meta meta) {
                Intent i = new Intent(MainActivity.this, DetailActivity.class);
                i.putExtra(SpaceAndroidUtils.META_RECORD_HASH_EXTRA, metaRecordHash.toByteArray());
                i.putExtra(SpaceAndroidUtils.META_EXTRA, meta.toByteArray());
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
            refresh();
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
                switch (resultCode) {
                    case RESULT_OK:
                        break;
                    case RESULT_CANCELED:
                        // TODO
                        break;
                    default:
                        break;
                }
                break;
            case SpaceAndroidUtils.DETAIL_ACTIVITY:
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
            case SpaceAndroidUtils.PAYMENT_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        final String alias = SpaceAndroidUtils.getAlias();
                        final String email = intent.getStringExtra(SpaceAndroidUtils.EMAIL_EXTRA);
                        final String paymentId = intent.getStringExtra(SpaceAndroidUtils.STRIPE_TOKEN_EXTRA);
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    SpaceUtils.subscribe(alias, email, paymentId);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                } catch (SignatureException e) {
                                    e.printStackTrace();
                                } catch (InvalidKeyException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
            case SpaceAndroidUtils.NEW_RECORD_ACTIVITY:
                break;
            case SpaceAndroidUtils.UPLOAD_ACTIVITY:
                break;
            case SpaceAndroidUtils.IMAGE_CAPTURE_ACTIVITY:
                Log.d(SpaceUtils.TAG, "IMAGE_CAPTURE_ACTIVITY");
                // fallthrough
            case SpaceAndroidUtils.VIDEO_CAPTURE_ACTIVITY:
                Log.d(SpaceUtils.TAG, "VIDEO_CAPTURE_ACTIVITY");
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void account() {
        Intent i = new Intent(this, AccountActivity.class);
        startActivityForResult(i, SpaceAndroidUtils.ACCOUNT_ACTIVITY);
    }

    private void refresh() {
        new Thread() {
            @Override
            public void run() {
                try {
                    InetAddress address = InetAddress.getByName(SpaceUtils.SPACE_HOST);
                    String alias = SpaceAndroidUtils.getAlias();
                    Channel metas = new Channel(SpaceUtils.META_CHANNEL_PREFIX + alias, BCUtils.THRESHOLD_STANDARD, getCacheDir(), address);
                    metas.sync();
                    KeyPair keys = SpaceAndroidUtils.getKeyPair();
                    metas.read(alias, keys, null, new RecordCallback() {
                        @Override
                        public void onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                            try {
                                Meta meta = Meta.newBuilder().mergeFrom(payload).build();
                                adapter.addFile(blockEntry.getRecordHash(), blockEntry.getRecord().getTimestamp(), meta);
                            } catch (InvalidProtocolBufferException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (IOException | NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
