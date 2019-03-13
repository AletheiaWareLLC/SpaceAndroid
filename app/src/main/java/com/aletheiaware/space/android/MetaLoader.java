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

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.aletheiaware.bc.BC.Channel.RecordCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public abstract class MetaLoader implements RecordCallback {
    private final Context context;
    private final byte[] recordHash;
    private final boolean shared;
    private long timestamp;
    private Meta meta;
    private List<Reference> references;

    MetaLoader(Context context, byte[] recordHash, boolean shared) {
        this.context = context;
        this.recordHash = recordHash;
        this.shared = shared;
        new Thread() {
            @Override
            public void run() {
                try {
                    loadMeta();
                } catch (IOException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
            }
        }.start();
    }

    byte[] getRecordHash() {
        return recordHash;
    }

    Meta getMeta() {
        return meta;
    }

    long getTimestamp() {
        return timestamp;
    }

    boolean isShared() {
        return shared;
    }

    void writeFileToURI(Uri uri) throws IOException {
        try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            Log.d(SpaceUtils.TAG, "Writing to: " + uri.toString());
            if (out != null) {
                readFile(new RecordCallback() {
                    @Override
                    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                        try {
                            Log.d(SpaceUtils.TAG, "Writing: " + payload.length);
                            out.write(payload);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
        try {
            Record record = blockEntry.getRecord();
            timestamp = record.getTimestamp();
            meta = Meta.newBuilder().mergeFrom(payload).build();
            references = record.getReferenceList();
            Log.d(SpaceUtils.TAG, "Timestamp: " + timestamp);
            Log.d(SpaceUtils.TAG, "Meta: " + meta);
            Log.d(SpaceUtils.TAG, "Refs: " + references);
            onMetaLoaded();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return true;
    }

    private void loadMeta() throws IOException {
        if (shared) {
            SpaceUtils.readShares(SpaceAndroidUtils.getHost(), context.getCacheDir(), SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair(), null, recordHash, this, null);
        } else {
            SpaceUtils.readMetas(SpaceAndroidUtils.getHost(), context.getCacheDir(), SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair(), recordHash, this);
        }
    }

    void readFile(RecordCallback callback) throws IOException {
        if (shared) {
            SpaceUtils.readShares(SpaceAndroidUtils.getHost(), context.getCacheDir(), SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair(), null, recordHash, null, callback);
        } else if (references != null){
            for (Reference reference : references) {
                SpaceUtils.readFiles(SpaceAndroidUtils.getHost(), context.getCacheDir(), SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair(), reference.getRecordHash().toByteArray(), callback);
            }
        }
    }

    abstract void onMetaLoaded();
}