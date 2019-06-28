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

package com.aletheiaware.space.android.utils;

import android.app.Activity;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.bc.utils.BCUtils.RecordCallback;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.SpaceProto.Share;
import com.aletheiaware.space.SpaceProto.Tag;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class MinerUtils {

    private MinerUtils() {
    }

    public static void mineFile(final Activity parent, final String website, final String name, final String type, final Preview preview, final InputStream in) {
        Log.d(SpaceUtils.TAG, "Mine file");
        SpaceAndroidUtils.createMiningNotification(parent, name);
        new Thread() {
            @Override
            public void run() {
                try {
                    final String alias = BCAndroidUtils.getAlias();
                    final KeyPair keys = BCAndroidUtils.getKeyPair();
                    final Map<String, PublicKey> acl = new HashMap<>();
                    acl.put(alias, keys.getPublic());
                    final List<Reference> metaReferences = new ArrayList<>();
                    long size = BCUtils.createEntries(alias, keys, acl, new ArrayList<Reference>(), in, new RecordCallback() {
                        @Override
                        public void onRecord(Record record) {
                            Reference fileReference = null;
                            for (int i = 0; fileReference == null && i < 5; i++) {
                                try {
                                    fileReference = SpaceUtils.postRecord(website, "file", record);
                                } catch (IOException e) {
                                    /* Ignored */
                                    e.printStackTrace();
                                }
                            }
                            if (fileReference == null) {
                                // FIXME
                                System.err.println("Failed to post file record 5 times");
                                return;
                            }
                            metaReferences.add(fileReference);
                            Log.d(SpaceUtils.TAG, "Uploaded File " + new String(BCUtils.encodeBase64URL(fileReference.getRecordHash().toByteArray())));
                        }
                    });
                    final Meta meta = Meta.newBuilder()
                            .setName(name)
                            .setType(type)
                            .setSize(size)
                            .build();
                    Log.d(SpaceUtils.TAG, "Meta " + meta);
                    Record metaRecord = BCUtils.createRecord(alias, keys, acl, metaReferences, meta.toByteArray());
                    Reference metaReference = null;
                    for (int i = 0; metaReference == null && i < 5; i++) {
                        try {
                            metaReference = SpaceUtils.postRecord(website, "meta", metaRecord);
                        } catch (IOException e) {
                            /* Ignored */
                            e.printStackTrace();
                        }
                    }
                    if (metaReference == null) {
                        // FIXME
                        System.err.println("Failed to post meta record 5 times");
                        return;
                    }
                    Log.d(SpaceUtils.TAG, "Uploaded Meta " + new String(BCUtils.encodeBase64URL(metaReference.getRecordHash().toByteArray())));
                    if (preview != null) {
                        Log.d(SpaceUtils.TAG, "Preview " + preview);
                        final List<Reference> previewReferences = new ArrayList<>();
                        previewReferences.add(Reference.newBuilder()
                                .setTimestamp(metaReference.getTimestamp())
                                .setChannelName(metaReference.getChannelName())
                                .setRecordHash(metaReference.getRecordHash())
                                .build());
                        Record previewRecord = BCUtils.createRecord(alias, keys, acl, previewReferences, preview.toByteArray());
                        Reference previewReference = null;
                        for (int i = 0; previewReference == null && i < 5; i++) {
                            try {
                                previewReference = SpaceUtils.postRecord(website, "preview", previewRecord);
                            } catch (IOException e) {
                                /* Ignored */
                                e.printStackTrace();
                            }
                        }
                        if (previewReference == null) {
                            // FIXME
                            System.err.println("Failed to post preview record 5 times");
                            return;
                        }
                        Log.d(SpaceUtils.TAG, "Uploaded Preview " + new String(BCUtils.encodeBase64URL(previewReference.getRecordHash().toByteArray())));
                    }
                } catch (SocketException | SocketTimeoutException e) {
                    BCAndroidUtils.showErrorDialog(parent, parent.getString(R.string.error_connection, website), e);
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    BCAndroidUtils.showErrorDialog(parent, R.string.error_uploading, e);
                } finally {
                    cancelMiningNotification(parent);
                }
            }
        }.start();
    }

    public static void mineShare(final Activity parent, final String website, final String recipientAlias, final PublicKey recipientKey, final Share share) {
        Log.d(SpaceUtils.TAG, "Mine share");
        SpaceAndroidUtils.createMiningNotification(parent, "Sharing with " + recipientAlias);
        new Thread() {
            @Override
            public void run() {
                try {
                    final String alias = BCAndroidUtils.getAlias();
                    final KeyPair keys = BCAndroidUtils.getKeyPair();
                    final Map<String, PublicKey> acl = new HashMap<>();
                    acl.put(recipientAlias, recipientKey);
                    acl.put(alias, keys.getPublic());
                    final List<Reference> shareReferences = new ArrayList<>();
                    Record shareRecord = BCUtils.createRecord(alias, keys, acl, shareReferences, share.toByteArray());
                    Reference shareReference = null;
                    for (int i = 0; shareReference == null && i < 5; i++) {
                        shareReference = SpaceUtils.postRecord(website, "share", shareRecord);
                    }
                    if (shareReference == null) {
                        // FIXME
                        System.err.println("Failed to post share record 5 times");
                        return;
                    }
                    Log.d(SpaceUtils.TAG, "Uploaded Share " + new String(BCUtils.encodeBase64URL(shareReference.getRecordHash().toByteArray())));
                } catch (SocketException | SocketTimeoutException e) {
                    BCAndroidUtils.showErrorDialog(parent, parent.getString(R.string.error_connection, website), e);
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    BCAndroidUtils.showErrorDialog(parent, R.string.error_uploading, e);
                } finally {
                    cancelMiningNotification(parent);
                }
            }
        }.start();
    }

    public static void mineTag(final Activity parent, final String website, final Reference meta, final Tag tag) {
        Log.d(SpaceUtils.TAG, "Mine tag");
        SpaceAndroidUtils.createMiningNotification(parent, "Tagging " + meta + " with " + tag.getValue());
        new Thread() {
            @Override
            public void run() {
                try {
                    final String alias = BCAndroidUtils.getAlias();
                    final KeyPair keys = BCAndroidUtils.getKeyPair();
                    final Map<String, PublicKey> acl = new HashMap<>();
                    acl.put(alias, keys.getPublic());
                    final List<Reference> tagReferences = new ArrayList<>();
                    tagReferences.add(meta);
                    Record tagRecord = BCUtils.createRecord(alias, keys, acl, tagReferences, tag.toByteArray());
                    Reference tagReference = null;
                    for (int i = 0; tagReference == null && i < 5; i++) {
                        tagReference = SpaceUtils.postRecord(website, "tag", tagRecord);
                    }
                    if (tagReference == null) {
                        // FIXME
                        System.err.println("Failed to post tag record 5 times");
                        return;
                    }
                    Log.d(SpaceUtils.TAG, "Uploaded Tag " + new String(BCUtils.encodeBase64URL(tagReference.getRecordHash().toByteArray())));
                } catch (SocketException | SocketTimeoutException e) {
                    BCAndroidUtils.showErrorDialog(parent, parent.getString(R.string.error_connection, website), e);
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    BCAndroidUtils.showErrorDialog(parent, R.string.error_uploading, e);
                } finally {
                    cancelMiningNotification(parent);
                }
            }
        }.start();
    }

    private static void cancelMiningNotification(final Activity parent) {
        try {
            // Sleep for a second so user can see notification
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
        parent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                NotificationManagerCompat.from(parent).cancel(SpaceAndroidUtils.MINING_NOTIFICATION_ID);
            }
        });
    }
}
