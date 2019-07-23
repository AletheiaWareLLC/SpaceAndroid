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
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Channel.RecordCallback;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.android.ui.AccessActivity;
import com.aletheiaware.bc.android.ui.AccountActivity;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.MetaAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.security.KeyPair;

public class MainActivity extends AppCompatActivity {

    private MetaAdapter adapter;
    private RecyclerView recyclerView;

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
                if (Math.abs(appBar.getTotalScrollRange() + verticalOffset) < 50) {
                    collapsingToolbar.setTitle(title);
                    showing = true;
                } else if (showing) {
                    collapsingToolbar.setTitle("");
                    showing = false;
                }
            }
        });
        setTitle(null);

        // RecyclerView
        recyclerView = findViewById(R.id.main_recycler);
        RecyclerView.LayoutManager layoutManager;
        if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) < Configuration.SCREENLAYOUT_SIZE_LARGE) {
            layoutManager = new LinearLayoutManager(this);
        } else {
            layoutManager = new GridLayoutManager(this, 5, GridLayoutManager.VERTICAL, false);
        }
        recyclerView.setLayoutManager(layoutManager);

        // Add FloatingActionButton
        FloatingActionButton addFab = findViewById(R.id.main_add_fab);
        addFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SpaceAndroidUtils.add(MainActivity.this);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (BCAndroidUtils.isInitialized()) {
            final String alias = BCAndroidUtils.getAlias();
            final KeyPair keys = BCAndroidUtils.getKeyPair();
            final Cache cache = BCAndroidUtils.getCache();
            new Thread() {
                @Override
                public void run() {
                    if (adapter == null || !alias.equals(adapter.getAlias())) {
                        Network network = SpaceAndroidUtils.getStorageNetwork(MainActivity.this, alias);
                        // Adapter
                        adapter = new MetaAdapter(MainActivity.this, cache, network, alias, keys) {
                            @Override
                            public void onSelection(ByteString hash, Meta meta) {
                                Intent i = new Intent(MainActivity.this, DetailActivity.class);
                                i.putExtra(SpaceAndroidUtils.HASH_EXTRA, hash.toByteArray());
                                i.putExtra(SpaceAndroidUtils.META_EXTRA, meta.toByteArray());
                                i.putExtra(SpaceAndroidUtils.SHARED_EXTRA, isShared(hash));
                                startActivityForResult(i, SpaceAndroidUtils.DETAIL_ACTIVITY);
                            }
                        };
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // TODO visually show files that are still getting mined
                                recyclerView.setAdapter(adapter);
                                recyclerView.requestLayout();
                            }
                        });
                    }
                    if (adapter.isEmpty()) {
                        refresh();
                    }
                }
            }.start();
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
            case SpaceAndroidUtils.PROVIDERS_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        break;
                    case RESULT_CANCELED:
                        // TODO
                        break;
                    default:
                        break;
                }
                adapter = null; // Clear adapter to reload from new providers
                refresh();
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
            case R.id.menu_providers:
                providers();
                return true;
            case R.id.menu_settings:
                settings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public MetaAdapter getAdapter() {
        return adapter;
    }

    private void refresh() {
        if (BCAndroidUtils.isInitialized()) {
            // TODO start refresh menu animate
            new Thread() {
                @Override
                public void run() {
                    final String alias = BCAndroidUtils.getAlias();
                    final KeyPair keys = BCAndroidUtils.getKeyPair();
                    final Cache cache = BCAndroidUtils.getCache();
                    final Network network = SpaceAndroidUtils.getStorageNetwork(MainActivity.this, alias);
                    try {
                        SpaceUtils.readMetas(cache, network, alias, keys, null, new RecordCallback() {
                            @Override
                            public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                try {
                                    Meta meta = Meta.newBuilder().mergeFrom(payload).build();
                                    Log.d(SpaceUtils.TAG, "Meta: " + meta);
                                    adapter.addMeta(blockEntry.getRecordHash(), blockEntry.getRecord().getTimestamp(), meta, false);
                                } catch (InvalidProtocolBufferException e) {
                                    /* Ignored */
                                    e.printStackTrace();
                                }
                                return true;
                            }
                        });
                    } catch (IOException e) {
                        CommonAndroidUtils.showErrorDialog(MainActivity.this, R.style.AlertDialogTheme, R.string.error_meta_read_failed, e);
                    }
                    try {
                        SpaceUtils.readShares(cache, network, alias, keys, null, null, null, new RecordCallback() {
                            @Override
                            public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                try {
                                    Meta meta = Meta.newBuilder().mergeFrom(payload).build();
                                    Log.d(SpaceUtils.TAG, "Shared Meta: " + meta);
                                    adapter.addMeta(blockEntry.getRecordHash(), blockEntry.getRecord().getTimestamp(), meta, true);
                                } catch (InvalidProtocolBufferException e) {
                                    /* Ignored */
                                    e.printStackTrace();
                                }
                                return true;
                            }
                        }, null);
                    } catch (IOException e) {
                        CommonAndroidUtils.showErrorDialog(MainActivity.this, R.style.AlertDialogTheme, R.string.error_shared_meta_read_failed, e);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // TODO stop refresh menu animate
                        }
                    });
                }
            }.start();
        }
    }

    private void account() {
        Intent i = new Intent(this, AccountActivity.class);
        startActivityForResult(i, SpaceAndroidUtils.ACCOUNT_ACTIVITY);
    }

    private void providers() {
        Intent i = new Intent(this, ProvidersActivity.class);
        startActivityForResult(i, SpaceAndroidUtils.PROVIDERS_ACTIVITY);
    }

    private void settings() {
        Intent i = new Intent(this, SettingsActivity.class);
        startActivityForResult(i, SpaceAndroidUtils.SETTINGS_ACTIVITY);
    }
}
