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
import java.util.List;

public abstract class MetaLoader implements RecordCallback {
    private final Context context;
    private final byte[] metaRecordHash;
    private final boolean shared;
    private ByteString blockHash;
    private String channelName;
    private ByteString recordHash;
    private long timestamp;
    private Meta meta;
    private List<Reference> references;

    MetaLoader(Context context, byte[] metaRecordHash, boolean shared) {
        this.context = context;
        this.metaRecordHash = metaRecordHash;
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

    byte[] getMetaRecordHash() {
        return metaRecordHash;
    }

    ByteString getBlockHash() {
        return blockHash;
    }

    String getChannelName() {
        return channelName;
    }

    ByteString getRecordHash() {
        return recordHash;
    }

    long getTimestamp() {
        return timestamp;
    }

    Meta getMeta() {
        return meta;
    }

    boolean isShared() {
        return shared;
    }

    @Override
    public boolean onRecord(ByteString hash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
        try {
            blockHash = hash;
            channelName = block.getChannelName();
            recordHash = blockEntry.getRecordHash();
            Record record = blockEntry.getRecord();
            timestamp = record.getTimestamp();
            meta = Meta.newBuilder().mergeFrom(payload).build();
            references = record.getReferenceList();
            Log.d(SpaceUtils.TAG, "Timestamp: " + timestamp);
            Log.d(SpaceUtils.TAG, "Meta: " + meta);
            Log.d(SpaceUtils.TAG, "Refs: " + references);
            onMetaLoaded();
        } catch (InvalidProtocolBufferException e) {
            /* Ignored */
            e.printStackTrace();
        }
        return true;
    }

    private void loadMeta() throws IOException {
        if (shared) {
            SpaceUtils.readShares(SpaceAndroidUtils.getSpaceHost(), context.getCacheDir(), SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair(), null, metaRecordHash, this, null);
        } else {
            SpaceUtils.readMetas(SpaceAndroidUtils.getSpaceHost(), context.getCacheDir(), SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair(), metaRecordHash, this);
        }
    }

    void readFile(RecordCallback callback) throws IOException {
        if (shared) {
            SpaceUtils.readShares(SpaceAndroidUtils.getSpaceHost(), context.getCacheDir(), SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair(), null, metaRecordHash, null, callback);
        } else if (references != null){
            for (Reference reference : references) {
                SpaceUtils.readFiles(SpaceAndroidUtils.getSpaceHost(), context.getCacheDir(), SpaceAndroidUtils.getAlias(), SpaceAndroidUtils.getKeyPair(), reference.getRecordHash().toByteArray(), callback);
            }
        }
    }

    abstract void onMetaLoaded();
}