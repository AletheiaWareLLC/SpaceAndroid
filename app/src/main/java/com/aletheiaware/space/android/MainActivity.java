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
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.aletheiaware.bc.BC.Channel.RecordCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyPair;

public class MainActivity extends AppCompatActivity {

    private MetaAdapter adapter;
    private RecyclerView recyclerView;
    private Spinner sortSpinner;

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

        // SortSpinner
        sortSpinner = findViewById(R.id.main_sort);
        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(this, R.array.preference_sort_options, R.layout.sort_list_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final String value = String.valueOf(position + 1);
                new Thread() {
                    @Override
                    public void run() {
                        SpaceAndroidUtils.setSortPreference(MainActivity.this, value);
                        adapter.sort();
                    }
                }.start();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // RecyclerView
        recyclerView = findViewById(R.id.main_recycler);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, layoutManager.getOrientation()));

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
        if (SpaceAndroidUtils.isInitialized()) {
            String alias = SpaceAndroidUtils.getAlias();
            if (adapter == null || !alias.equals(adapter.getAlias())) {
                // Adapter
                adapter = new MetaAdapter(this, alias) {
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
            if (adapter.isEmpty()) {
                refresh();
            }
            // TODO hide sortSpinner if adapter is empty
            switch (SpaceAndroidUtils.getSortPreference(this)) {
                case "1":
                    sortSpinner.setSelection(0);
                    break;
                case "2":
                    sortSpinner.setSelection(1);
                    break;
            }
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
        // TODO start refresh menu animate
        new Thread() {
            @Override
            public void run() {
                final InetAddress address = SpaceAndroidUtils.getSpaceHost();
                final File cache = getCacheDir();
                final String alias = SpaceAndroidUtils.getAlias();
                final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                try {
                    SpaceUtils.readMetas(address, cache, alias, keys, null, new RecordCallback() {
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
                    SpaceAndroidUtils.showErrorDialog(MainActivity.this, R.string.error_meta_read_failed, e);
                }
                try {
                    SpaceUtils.readShares(address, cache, alias, keys, null, null, new RecordCallback() {
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
                    SpaceAndroidUtils.showErrorDialog(MainActivity.this, R.string.error_shared_meta_read_failed, e);
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

    private void account() {
        Intent i = new Intent(this, AccountActivity.class);
        startActivityForResult(i, SpaceAndroidUtils.ACCOUNT_ACTIVITY);
    }

    private void settings() {
        Intent i = new Intent(this, SettingsActivity.class);
        startActivityForResult(i, SpaceAndroidUtils.SETTINGS_ACTIVITY);
    }
}
