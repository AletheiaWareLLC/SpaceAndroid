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
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;

public class PreviewActivity extends AppCompatActivity {

    private MetaLoader loader;
    private TextView nameTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SpaceAndroidUtils.createNotificationChannels(this);

        // Setup UI
        setContentView(R.layout.activity_preview);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.preview_toolbar);
        setSupportActionBar(toolbar);

        // Name TextView
        nameTextView = findViewById(R.id.preview_name);
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
