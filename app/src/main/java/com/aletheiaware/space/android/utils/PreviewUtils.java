/*
 * Copyright 2019 Aletheia Ware LLC
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

package com.aletheiaware.space.android.utils;

import android.app.Activity;
import android.util.Log;

import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Channel.RecordCallback;
import com.aletheiaware.bc.Crypto;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.PoWChannel;
import com.aletheiaware.bc.utils.ChannelUtils;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.SpaceProto.Share;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class PreviewUtils {
    private PreviewUtils() {}

    public interface PreviewCallback {
        void onPreview(ByteString hash, Preview preview);
    }

    public static void loadPreview(final Activity activity, final Cache cache, final String alias, final KeyPair keys, final ByteString metaRecordHash, final boolean shared, final PreviewCallback callback) {
        new Thread() {
            @Override
            public void run() {
                try {
                    final Network network = SpaceAndroidUtils.getRegistrarNetwork(activity, alias);
                    if (shared) {
                        Log.d(SpaceUtils.TAG, "Loading Share Channel");
                        PoWChannel shares = SpaceUtils.getShareChannel(alias);
                        ChannelUtils.loadHead(shares, cache);
                        Log.d(SpaceUtils.TAG, "Pulling Share Channel");
                        try {
                            ChannelUtils.pull(shares, cache, network);
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                        Log.d(SpaceUtils.TAG, "Reading Share Channel");
                        SpaceUtils.readShares(shares, cache, network, alias, keys, null, metaRecordHash, new RecordCallback() {
                            @Override
                            public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                Share.Builder sb = Share.newBuilder();
                                try {
                                    sb.mergeFrom(payload);
                                } catch (InvalidProtocolBufferException e) {
                                    e.printStackTrace();
                                }
                                Share share = sb.build();
                                Reference sharedMetaReference = share.getMetaReference();
                                if (sharedMetaReference.getRecordHash().equals(metaRecordHash)) {
                                    int count = Math.min(share.getPreviewKeyCount(), share.getPreviewReferenceCount());
                                    Preview preview = null;
                                    for (int i = 0; i < count; i++) {
                                        Reference r = share.getPreviewReference(i);
                                        Block b = cache.getBlockContainingRecord(r.getChannelName(), r.getRecordHash());
                                        if (b == null) {
                                            b = network.getBlock(r);
                                            if (b != null) {
                                                try {
                                                    cache.putBlock(ByteString.copyFrom(Crypto.getProtobufHash(b)), b);
                                                } catch (NoSuchAlgorithmException e) {
                                                    /* Ignored */
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                        if (b != null) {
                                            for (BlockEntry e : b.getEntryList()) {
                                                try {
                                                    byte[] previewKey = share.getPreviewKey(i).toByteArray();
                                                    byte[] decryptedPayload = Crypto.decryptAES(previewKey, e.getRecord().getPayload().toByteArray());
                                                    Preview p = Preview.newBuilder().mergeFrom(decryptedPayload).build();
                                                    // TODO choose best preview for screen size
                                                    if (preview == null) {
                                                        preview = p;
                                                    }
                                                } catch (InvalidProtocolBufferException | BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException ex) {
                                                    ex.printStackTrace();
                                                }
                                            }
                                        }
                                    }
                                    if (preview != null) {
                                        callback.onPreview(metaRecordHash, preview);
                                    }
                                }
                                return true;
                            }
                        }, null, null);
                    } else {
                        Log.d(SpaceUtils.TAG, "Loading Preview Channel");
                        PoWChannel previews = SpaceUtils.getPreviewChannel(metaRecordHash);
                        ChannelUtils.loadHead(previews, cache);
                        Log.d(SpaceUtils.TAG, "Pulling Preview Channel");
                        try {
                            ChannelUtils.pull(previews, cache, network);
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                        Log.d(SpaceUtils.TAG, "Reading Preview Channel");
                        ChannelUtils.read(previews.getName(), previews.getHead(), null, cache, network, alias, keys, null, new RecordCallback() {
                            @Override
                            public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                Preview preview = null;
                                for (Reference r : blockEntry.getRecord().getReferenceList()) {
                                    if (r.getRecordHash().equals(metaRecordHash)) {
                                        try {
                                            Preview p = Preview.newBuilder().mergeFrom(payload).build();
                                            // TODO choose best preview for screen size
                                            if (preview == null) {
                                                preview = p;
                                            }
                                        } catch (InvalidProtocolBufferException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                if (preview != null) {
                                    callback.onPreview(metaRecordHash, preview);
                                    return false;
                                }
                                return true;
                            }
                        });
                    }
                } catch (IOException | IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
