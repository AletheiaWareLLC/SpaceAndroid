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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Channel.RecordCallback;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.PoWChannel;
import com.aletheiaware.bc.android.ui.AccessActivity;
import com.aletheiaware.bc.android.ui.AccountActivity;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.ChannelUtils;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.MetaAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.PreviewUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {

    private TextView emptyListHelpText;
    private MetaAdapter adapter;
    private RecyclerView recyclerView;
    private volatile boolean refreshing = false;

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

        emptyListHelpText = findViewById(R.id.main_empty_list_help_text);

        // RecyclerView
        recyclerView = findViewById(R.id.main_recycler);
        RecyclerView.LayoutManager layoutManager;
        if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) < Configuration.SCREENLAYOUT_SIZE_LARGE) {
            layoutManager = new LinearLayoutManager(this);
        } else {
            layoutManager = new GridLayoutManager(this, 5, GridLayoutManager.VERTICAL, false);
        }
        recyclerView.setLayoutManager(layoutManager);

        // FloatingActionButton
        FloatingActionButton addFab = findViewById(R.id.main_add_fab);
        addFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO expand FAB to show actions, rather than dialog
                //  add icons to actions;
                //  - compose: create, or pencil
                //  - upload: file upload, note add, or paperclip?
                //  - picture: camera
                //  - video: camcorder
                //  - audio: mic
                //  - location: pin
                //  - temperature: thermometer
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
            if (adapter == null || !alias.equals(adapter.getAlias())) {
                // Adapter
                adapter = new MetaAdapter(MainActivity.this, alias) {
                    @Override
                    protected void loadPreview(ByteString hash) {
                        PreviewUtils.loadPreview(MainActivity.this, cache, alias, keys, hash, isShared(hash), this);
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
            if (adapter.isEmpty()) {
                refresh();
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
                }
                break;
            case SpaceAndroidUtils.ACCOUNT_ACTIVITY:
                // Fallthrough
            case SpaceAndroidUtils.COMPOSE_ACTIVITY:
                // Fallthrough
            case SpaceAndroidUtils.DETAIL_ACTIVITY:
                // Fallthrough
            case SpaceAndroidUtils.SETTINGS_ACTIVITY:
                // Fallthrough
                // TODO if sort preference has changed, recycler view need to relayout data in adapter
            case SpaceAndroidUtils.UPLOAD_ACTIVITY:
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
                }
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
        if (BCAndroidUtils.isInitialized() && !refreshing) {
            refreshing = true;
            // Create refreshing dialog
            View progressView = View.inflate(MainActivity.this, R.layout.dialog_progress, null);
            final ProgressBar progressBar = progressView.findViewById(R.id.progress_bar);
            progressBar.setIndeterminate(true);
            final TextView progressStatus = progressView.findViewById(R.id.progress_status);
            progressStatus.setVisibility(View.VISIBLE);
            final AlertDialog progressDialog = new AlertDialog.Builder(MainActivity.this, R.style.AlertDialogTheme)
                    .setTitle(R.string.title_dialog_refreshing)
                    .setIcon(R.drawable.refresh)
                    .setCancelable(false)
                    .setView(progressView)
                    .show();
            new Thread() {
                @Override
                public void run() {
                    final String alias = BCAndroidUtils.getAlias();
                    final KeyPair keys = BCAndroidUtils.getKeyPair();
                    final Cache cache = BCAndroidUtils.getCache();
                    final Network network = SpaceAndroidUtils.getRegistrarNetwork(MainActivity.this, alias);
                    try {
                        SpaceAndroidUtils.setStatus(MainActivity.this, progressStatus, R.string.main_loading_meta);
                        final PoWChannel metas = SpaceUtils.getMetaChannel(alias);
                        ChannelUtils.loadHead(metas, cache);
                        SpaceAndroidUtils.setStatus(MainActivity.this, progressStatus, R.string.main_pulling_meta);
                        try {
                            ChannelUtils.pull(metas, cache, network);
                        } catch (NoSuchAlgorithmException e) {
                            /* Ignored */
                            e.printStackTrace();
                        }
                        ByteString head = metas.getHead();
                        if (head != null && !head.equals(adapter.getMetaHead())) {
                            SpaceAndroidUtils.setStatus(MainActivity.this, progressStatus, R.string.main_reading_meta);
                            ChannelUtils.read(metas.getName(), metas.getHead(), null, cache, network, alias, keys, null, new RecordCallback() {
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
                            adapter.setMetaHead(head);
                        }
                    } catch (IOException e) {
                        CommonAndroidUtils.showErrorDialog(MainActivity.this, R.style.AlertDialogTheme, R.string.error_meta_read_failed, e);
                    }
                    try {
                        SpaceAndroidUtils.setStatus(MainActivity.this, progressStatus, R.string.main_loading_share);
                        final PoWChannel shares = SpaceUtils.getShareChannel(alias);
                        ChannelUtils.loadHead(shares, cache);
                        SpaceAndroidUtils.setStatus(MainActivity.this, progressStatus, R.string.main_pulling_share);
                        try {
                            ChannelUtils.pull(shares, cache, network);
                        } catch (NoSuchAlgorithmException e) {
                            /* Ignored */
                            e.printStackTrace();
                        }
                        ByteString head = shares.getHead();
                        if (head != null && !head.equals(adapter.getShareHead())) {
                            SpaceAndroidUtils.setStatus(MainActivity.this, progressStatus, R.string.main_reading_share);
                            SpaceUtils.readShares(shares, cache, network, alias, keys, null, null, null, new RecordCallback() {
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
                            adapter.setShareHead(head);
                        }
                    } catch (IOException e) {
                        CommonAndroidUtils.showErrorDialog(MainActivity.this, R.style.AlertDialogTheme, R.string.error_shared_meta_read_failed, e);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Dismiss refreshing dialog
                            if (progressDialog != null && progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            // Show help text if adapter is empty
                            if (adapter.isEmpty()) {
                                final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                                final String preference = getString(R.string.preference_main_empty_list_help, alias);
                                if (preferences.getBoolean(preference, true)) {
                                    emptyListHelpText.setVisibility(View.VISIBLE);
                                    emptyListHelpText.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            preferences.edit().putBoolean(preference, false).apply();
                                            emptyListHelpText.setVisibility(View.GONE);
                                        }
                                    });
                                }
                            } else {
                                emptyListHelpText.setVisibility(View.GONE);
                            }
                        }
                    });
                    refreshing = false;
                }
            }.start();
        }
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
