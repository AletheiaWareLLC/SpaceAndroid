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

import com.aletheiaware.bc.BC;
import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.BC.Channel.RecordCallback;
import com.aletheiaware.bc.BCProto;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.SpaceProto.Share;
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

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class PreviewUtils {
    private PreviewUtils() {}

    public interface PreviewCallback {
        void onPreview(ByteString metaRecordHash, boolean shared, Preview preview);
    }

    public static void loadPreview(final File cacheDir, final ByteString metaRecordHash, final boolean shared, final PreviewCallback callback) {
        if (SpaceAndroidUtils.isInitialized()) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        final String alias = SpaceAndroidUtils.getAlias();
                        final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                        final InetAddress host = SpaceAndroidUtils.getSpaceHost();
                        final byte[] metaRecordHashBytes = metaRecordHash.toByteArray();
                        final Channel previews = new Channel(SpaceUtils.SPACE_PREFIX_PREVIEW + new String(BCUtils.encodeBase64URL(metaRecordHashBytes)), BCUtils.THRESHOLD_STANDARD, cacheDir, host);
                        try {
                            previews.sync();
                        } catch (IOException | NoSuchAlgorithmException e) {
                            /* Ignored */
                            e.printStackTrace();
                        }
                        if (shared) {
                            final Channel shares = new Channel(SpaceUtils.SPACE_PREFIX_SHARE + alias, BCUtils.THRESHOLD_STANDARD, cacheDir, host);
                            try {
                                shares.sync();
                            } catch (IOException | NoSuchAlgorithmException e) {
                                /* Ignored */
                                e.printStackTrace();
                            }
                            shares.read(alias, keys, null, new RecordCallback() {
                                @Override
                                public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                    Share.Builder sb = Share.newBuilder();
                                    try {
                                        sb.mergeFrom(payload);
                                    } catch (InvalidProtocolBufferException e) {
                                        e.printStackTrace();
                                    }
                                    Share share = sb.build();
                                    BCProto.Reference sharedMetaReference = share.getMetaReference();
                                    if (Arrays.equals(sharedMetaReference.getRecordHash().toByteArray(), metaRecordHashBytes)) {
                                        int count = Math.min(share.getPreviewKeyCount(), share.getPreviewReferenceCount());
                                        Preview preview = null;
                                        for (int i = 0; i < count; i++) {
                                            Block b = null;
                                            try {
                                                b = BCUtils.getBlock(host, share.getPreviewReference(i));
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                            if (b != null) {
                                                for (BlockEntry e : b.getEntryList()) {
                                                    try {
                                                        byte[] previewKey = share.getPreviewKey(i).toByteArray();
                                                        byte[] decryptedPayload = BCUtils.decryptAES(previewKey, e.getRecord().getPayload().toByteArray());
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
                                            callback.onPreview(metaRecordHash, shared, preview);
                                        }
                                    }
                                    return true;
                                }
                            });
                        } else {
                            previews.read(alias, keys, null, new BC.Channel.RecordCallback() {
                                @Override
                                public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                                    Preview preview = null;
                                    for (BCProto.Reference r : blockEntry.getRecord().getReferenceList()) {
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
                                        callback.onPreview(metaRecordHash, shared, preview);
                                    }
                                    return true;
                                }
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }
}
