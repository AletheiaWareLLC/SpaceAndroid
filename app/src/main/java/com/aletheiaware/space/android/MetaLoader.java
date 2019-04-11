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
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
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
    private final byte[] metaRecordHash;
    private final boolean shared;
    private ByteString blockHash;
    private String channelName;
    private ByteString recordHash;
    private long timestamp;
    private Meta meta;
    private List<Reference> references;

    public MetaLoader(Context context, byte[] metaRecordHash, boolean shared) {
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

    public byte[] getMetaRecordHash() {
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
        final InetAddress host = SpaceAndroidUtils.getSpaceHost();
        final File cache = context.getCacheDir();
        final String alias = BCAndroidUtils.getAlias();
        final KeyPair keys = BCAndroidUtils.getKeyPair();
        if (shared) {
            SpaceUtils.readShares(host, cache, alias, keys, null, metaRecordHash, this, null);
        } else {
            SpaceUtils.readMetas(host, cache, alias, keys, metaRecordHash, this);
        }
    }

    public void readFile(RecordCallback callback) throws IOException {
        final InetAddress host = SpaceAndroidUtils.getSpaceHost();
        final File cache = context.getCacheDir();
        final String alias = BCAndroidUtils.getAlias();
        final KeyPair keys = BCAndroidUtils.getKeyPair();
        if (shared) {
            SpaceUtils.readShares(host, cache, alias, keys, null, metaRecordHash, null, callback);
        } else if (references != null) {
            // TODO maintain mapping of record to parent block stored under cache/record/<record-hash>
            // So records can be loaded from cache if block exists, currently have to request from server each time
            for (Reference reference : references) {
                // SpaceUtils.readFiles(host, cache, alias, keys, reference.getRecordHash().toByteArray(), callback);
                Block block = BCUtils.getBlock(host, reference);
                if (block == null) {
                    break;
                }
                try {
                    ByteString hash = ByteString.copyFrom(BCUtils.getHash(block.toByteArray()));
                    for (BlockEntry entry : block.getEntryList()) {
                        final ByteString rh = entry.getRecordHash();
                        if (Arrays.equals(rh.toByteArray(), reference.getRecordHash().toByteArray())) {
                            final Record record = entry.getRecord();
                            for (Record.Access a : record.getAccessList()) {
                                if (a.getAlias().equals(alias)) {
                                    byte[] key = a.getSecretKey().toByteArray();
                                    byte[] decryptedKey = BCUtils.decryptRSA(keys.getPrivate(), key);
                                    byte[] decryptedPayload = BCUtils.decryptAES(decryptedKey, record.getPayload().toByteArray());
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