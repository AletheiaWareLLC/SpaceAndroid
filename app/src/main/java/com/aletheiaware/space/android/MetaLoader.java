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

import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Channel.RecordCallback;
import com.aletheiaware.bc.Crypto;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public abstract class MetaLoader implements RecordCallback {

    private final Context context;
    private final String alias;
    private final KeyPair keys;
    private final Cache cache;
    private Network network;
    private final ByteString metaRecordHash;
    private final boolean shared;
    private ByteString blockHash;
    private String channelName;
    private ByteString recordHash;
    private long timestamp;
    private Meta meta;
    private List<Reference> references;

    public MetaLoader(final Context context, final String alias, final KeyPair keys, Cache cache, ByteString metaRecordHash, boolean shared) {
        this.context = context;
        this.alias = alias;
        this.keys = keys;
        this.cache = cache;
        this.metaRecordHash = metaRecordHash;
        this.shared = shared;
        new Thread() {
            @Override
            public void run() {
                try {
                    network = SpaceAndroidUtils.getStorageNetwork(context, alias);
                    loadMeta();
                } catch (IOException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public ByteString getMetaRecordHash() {
        return metaRecordHash;
    }

    public ByteString getBlockHash() {
        return blockHash;
    }

    public String getChannelName() {
        return channelName;
    }

    public ByteString getRecordHash() {
        return recordHash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Meta getMeta() {
        return meta;
    }

    public boolean isShared() {
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
            SpaceUtils.readShares(cache, network, alias, keys, null, metaRecordHash, null,this, null);
        } else {
            SpaceUtils.readMetas(cache, network, alias, keys, metaRecordHash, this);
        }
    }

    public void readFile(RecordCallback callback) throws IOException {
        if (shared) {
            SpaceUtils.readShares(cache, network, alias, keys, null, metaRecordHash, null, null, callback);
        } else if (references != null) {
            // TODO maintain mapping of record to parent block stored under cache/record/<record-hash>
            // So records can be loaded from cache if block exists, currently have to request from server each time
            for (Reference reference : references) {
                // SpaceUtils.readFiles(host, cache, alias, keys, reference.getRecordHash().toByteArray(), callback);
                Log.d(SpaceUtils.TAG, "Reading: " + reference);
                Block block = network.getBlock(reference);
                if (block == null) {
                    break;
                }
                try {
                    ByteString hash = ByteString.copyFrom(Crypto.getProtobufHash(block));
                    for (BlockEntry entry : block.getEntryList()) {
                        final ByteString rh = entry.getRecordHash();
                        if (Arrays.equals(rh.toByteArray(), reference.getRecordHash().toByteArray())) {
                            final Record record = entry.getRecord();
                            for (Record.Access a : record.getAccessList()) {
                                if (a.getAlias().equals(alias)) {
                                    byte[] key = a.getSecretKey().toByteArray();
                                    byte[] decryptedKey = Crypto.decryptRSA(keys.getPrivate(), key);
                                    byte[] decryptedPayload = Crypto.decryptAES(decryptedKey, record.getPayload().toByteArray());
                                    if (!callback.onRecord(hash, block, entry, decryptedKey, decryptedPayload)) {
                                        return;
                                    }
                                }
                            }
                        }
                    }
                } catch (BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public abstract void onMetaLoaded();
}