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

import android.util.Log;

import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Channel.RecordCallback;
import com.aletheiaware.bc.Crypto;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.PoWChannel;
import com.aletheiaware.bc.utils.ChannelUtils;
import com.aletheiaware.space.SpaceProto.Meta;
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

import androidx.annotation.WorkerThread;

public abstract class MetaLoader implements RecordCallback {

    private final String alias;
    private final KeyPair keys;
    private final Cache cache;
    private final ByteString metaRecordHash;
    private final boolean shared;
    private Network network;
    private ByteString blockHash;
    private String channelName;
    private ByteString recordHash;
    private long timestamp;
    private Meta meta;
    private List<Reference> references;

    public MetaLoader(final String alias, KeyPair keys, Cache cache, Network network, ByteString metaRecordHash, boolean shared) throws IOException {
        this.alias = alias;
        this.keys = keys;
        this.cache = cache;
        this.network = network;
        this.metaRecordHash = metaRecordHash;
        this.shared = shared;
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

    @WorkerThread
    public void loadMeta() throws IOException {
        if (shared) {
            PoWChannel shares = SpaceUtils.getShareChannel(alias);
            ChannelUtils.loadHead(shares, cache);
            try {
                // TODO is this pull needed since SpaceUtils.readShares will request missing blocks?
                ChannelUtils.pull(shares, cache, network);
            } catch (NoSuchAlgorithmException e) {
                /* Ignored */
                e.printStackTrace();
            }
            SpaceUtils.readShares(shares, cache, network, alias, keys, null, metaRecordHash, null,this, null);
        } else {
            final PoWChannel metas = SpaceUtils.getMetaChannel(alias);
            ChannelUtils.loadHead(metas, cache);
            try {
                // TODO is this pull needed since ChannelUtils.read will request missing blocks?
                ChannelUtils.pull(metas, cache, network);
            } catch (NoSuchAlgorithmException e) {
                /* Ignored */
                e.printStackTrace();
            }
            ChannelUtils.read(metas.getName(), metas.getHead(), null, cache, network, alias, keys, metaRecordHash, this);
        }
    }

    public void readFile(RecordCallback callback) throws IOException {
        if (shared) {
            PoWChannel shares = SpaceUtils.getShareChannel(alias);
            ChannelUtils.loadHead(shares, cache);
            try {
                // TODO is this pull needed since SpaceUtils.readShares will request missing blocks?
                ChannelUtils.pull(shares, cache, network);
            } catch (NoSuchAlgorithmException e) {
                /* Ignored */
                e.printStackTrace();
            }
            SpaceUtils.readShares(shares, cache, network, alias, keys, null, metaRecordHash, null, null, callback);
        } else if (references != null) {
            for (Reference reference : references) {
                Log.d(SpaceUtils.TAG, "Reading: " + reference);
                Block block = cache.getBlockContainingRecord(reference.getChannelName(), reference.getRecordHash());
                if (block == null) {
                    block = network.getBlock(reference);
                    if (block != null) {
                        try {
                            cache.putBlock(ByteString.copyFrom(Crypto.getProtobufHash(block)), block);
                        } catch (NoSuchAlgorithmException e) {
                            /* Ignored */
                            e.printStackTrace();
                        }
                    }
                }
                if (block == null) {
                    // TODO show error
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
                    // TODO show error
                    e.printStackTrace();
                }
            }
        }
    }

    public abstract void onMetaLoaded();
}