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
import android.widget.EditText;
import android.widget.TextView;

import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Tag;
import com.aletheiaware.space.android.utils.MinerUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;

public class TagActivity extends AppCompatActivity {

    private TagAdapter adapter;
    private MetaLoader loader;
    private TextView nameTextView;
    private AutoCompleteTextView valueTextView;
    private EditText reasonEditText;
    private FloatingActionButton tagFab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MinerUtils.createNotificationChannels(this);

        // Setup UI
        setContentView(R.layout.activity_tag);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.tag_toolbar);
        setSupportActionBar(toolbar);

        // Name TextView
        nameTextView = findViewById(R.id.tag_name);

        // Value TextView
        valueTextView = findViewById(R.id.tag_value);
        valueTextView.setThreshold(3);

        // Reason EditText
        reasonEditText = findViewById(R.id.tag_reason);

        // Tag Fab
        tagFab = findViewById(R.id.tag_fab);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            adapter = new TagAdapter(this, SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair());
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
        valueTextView.setAdapter(adapter);
        tagFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String value = valueTextView.getText().toString();
                final String reason = reasonEditText.getText().toString();
                if (!value.isEmpty()) {
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
                            SpaceAndroidUtils.mineTag(TagActivity.this, reference, tag);
                        }
                    });
                }
            }
        });
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
