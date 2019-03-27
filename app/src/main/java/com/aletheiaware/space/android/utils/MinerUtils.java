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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.BC.Node;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Share;
import com.aletheiaware.space.SpaceProto.Tag;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.MainActivity;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.StripeActivity;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
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

    public interface MinerSelectionCallback {
        void onMineLocally();
        void onMineRemotely();
    }

    private static final String LOCAL_CHANNEL_ID = "Local Mining Channel";
    private static final String REMOTE_CHANNEL_ID = "Remote Mining Channel";
    private static final int LOCAL_NOTIFICATION_ID = 1;
    private static final int REMOTE_NOTIFICATION_ID = 2;
    private static final int NOTIFICATION_TIMEOUT = 2 * 60 * 1000;// 2 minutes

    private MinerUtils() {}

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel localChannel = new NotificationChannel(LOCAL_CHANNEL_ID, context.getString(R.string.notification_channel_name_local), NotificationManager.IMPORTANCE_HIGH);
            localChannel.setDescription(context.getString(R.string.notification_channel_description_local));
            NotificationChannel remoteChannel = new NotificationChannel(REMOTE_CHANNEL_ID, context.getString(R.string.notification_channel_name_remote), NotificationManager.IMPORTANCE_HIGH);
            remoteChannel.setDescription(context.getString(R.string.notification_channel_description_remote));
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(localChannel);
                notificationManager.createNotificationChannel(remoteChannel);
            }
        }
    }

    private static void createLocalMiningNotification(Context context, String name) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, LOCAL_CHANNEL_ID)
                .setSmallIcon(R.drawable.bc_mine)
                .setContentTitle(context.getString(R.string.title_notification_local))
                .setContentText(name)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setProgress(0, 0, true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(NOTIFICATION_TIMEOUT);

        NotificationManagerCompat.from(context).notify(LOCAL_NOTIFICATION_ID, builder.build());
    }

    private static void createRemoteMiningNotification(Context context, String name) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, REMOTE_CHANNEL_ID)
                .setSmallIcon(R.drawable.cloud_upload)
                .setContentTitle(context.getString(R.string.title_notification_remote))
                .setContentText(name)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setProgress(0, 0, true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(NOTIFICATION_TIMEOUT);

        NotificationManagerCompat.from(context).notify(REMOTE_NOTIFICATION_ID, builder.build());
    }

    public static void getMinerSelection(final Activity parent, final MinerSelectionCallback callback) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(parent);
        String key = parent.getString(R.string.preference_miner_key);
        // 1 - ask each time
        // 2 - local
        // 3 - remote
        String value = sharedPrefs.getString(key, "1");
        if (value == null) {
            value = "1";
        }
        switch (value) {
            case "1":
                new AlertDialog.Builder(parent, R.style.AlertDialogTheme)
                        .setTitle(R.string.title_dialog_select_miner)
                        .setItems(R.array.miner_options, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                    case 0:
                                        callback.onMineLocally();
                                        break;
                                    case 1:
                                        callback.onMineRemotely();
                                        break;
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.dismiss();
                            }
                        })
                        .show();
                break;
            case "2":
                callback.onMineLocally();
                break;
            case "3":
                callback.onMineRemotely();
                break;
        }
    }

    public static void mineFileLocally(final Activity parent, final String name, final String type, final Preview preview, final InputStream in) {
        Log.d(SpaceUtils.TAG, "Mine file locally");
        createLocalMiningNotification(parent, name);
        new Thread() {
            @Override
            public void run() {
                try {
                    final String alias = SpaceAndroidUtils.getAlias();
                    final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                    final Node node = SpaceAndroidUtils.getNode();
                    final InetAddress host = SpaceAndroidUtils.getHost();
                    final Channel metas = new Channel(SpaceUtils.META_CHANNEL_PREFIX + alias, BCUtils.THRESHOLD_STANDARD, parent.getCacheDir(), host);
                    final Channel files = new Channel(SpaceUtils.FILE_CHANNEL_PREFIX + alias, BCUtils.THRESHOLD_STANDARD, parent.getCacheDir(), host);
                    final Map<String, PublicKey> acl = new HashMap<>();
                    acl.put(alias, keys.getPublic());
                    final List<Reference> metaReferences = new ArrayList<>();
                    long size = BCUtils.createEntries(alias, keys, acl, new ArrayList<Reference>(), in, new BCUtils.RecordCallback() {
                        @Override
                        public void onRecord(Record record) {
                            try {
                                byte[] hash = BCUtils.getHash(record.toByteArray());
                                ByteString recordHash = ByteString.copyFrom(hash);
                                List<BlockEntry> entries = new ArrayList<>(1);
                                entries.add(BlockEntry.newBuilder()
                                        .setRecordHash(recordHash)
                                        .setRecord(record)
                                        .build());
                                BCUtils.Pair<byte[], Block> result = node.mine(files, entries);
                                ByteString blockHash = ByteString.copyFrom(result.a);
                                Block block = result.b;
                                metaReferences.add(Reference.newBuilder()
                                        .setTimestamp(block.getTimestamp())
                                        .setChannelName(files.name)
                                        .setBlockHash(blockHash)
                                        .setRecordHash(recordHash)
                                        .build());
                                Log.d(SpaceUtils.TAG, "Mined File " + new String(BCUtils.encodeBase64URL(result.a)));
                            } catch (BadPaddingException | IOException | NoSuchAlgorithmException e) {
                                SpaceAndroidUtils.showErrorDialog(parent, R.string.error_mining, e);
                            }
                        }
                    });
                    final Meta meta = Meta.newBuilder()
                            .setName(name)
                            .setType(type)
                            .setSize(size)
                            .build();
                    Log.d(SpaceUtils.TAG, "Meta " + meta);
                    Record metaRecord = BCUtils.createRecord(alias, keys, acl, metaReferences, meta.toByteArray());
                    byte[] metaRecordHashBytes = BCUtils.getHash(metaRecord.toByteArray());
                    ByteString metaRecordHash = ByteString.copyFrom(metaRecordHashBytes);
                    List<BlockEntry> metaEntries = new ArrayList<>();
                    metaEntries.add(BlockEntry.newBuilder()
                            .setRecordHash(metaRecordHash)
                            .setRecord(metaRecord)
                            .build());
                    BCUtils.Pair<byte[], Block> metaResult = node.mine(metas, metaEntries);
                    Log.d(SpaceUtils.TAG, "Mined Meta " + new String(BCUtils.encodeBase64URL(metaResult.a)));
                    if (preview != null) {
                        Log.d(SpaceUtils.TAG, "Preview " + preview);
                        final List<Reference> previewReferences = new ArrayList<>();
                        previewReferences.add(Reference.newBuilder()
                                .setTimestamp(metaResult.b.getTimestamp())
                                .setChannelName(metaResult.b.getChannelName())
                                .setBlockHash(ByteString.copyFrom(metaResult.a))
                                .setRecordHash(metaRecordHash)
                                .build());
                        Record previewRecord = BCUtils.createRecord(alias, keys, acl, previewReferences, preview.toByteArray());
                        ByteString previewRecordHash = ByteString.copyFrom(BCUtils.getHash(previewRecord.toByteArray()));
                        List<BlockEntry> previewEntries = new ArrayList<>();
                        previewEntries.add(BlockEntry.newBuilder()
                                .setRecordHash(previewRecordHash)
                                .setRecord(previewRecord)
                                .build());
                        final Channel previews = new Channel(SpaceUtils.PREVIEW_CHANNEL_PREFIX + new String(BCUtils.encodeBase64URL(metaRecordHashBytes)), BCUtils.THRESHOLD_STANDARD, parent.getCacheDir(), host);
                        BCUtils.Pair<byte[], Block> previewResult = node.mine(previews, previewEntries);
                        Log.d(SpaceUtils.TAG, "Mined Preview " + new String(BCUtils.encodeBase64URL(previewResult.a)));
                    }
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    SpaceAndroidUtils.showErrorDialog(parent, R.string.error_mining, e);
                } finally {
                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationManagerCompat.from(parent).cancel(LOCAL_NOTIFICATION_ID);
                        }
                    });
                }
            }
        }.start();
    }

    public static void mineFileRemotely(final Activity parent, final String name, final String type, final Preview preview, final InputStream in) {
        Log.d(SpaceUtils.TAG, "Mine file remotely");
        createRemoteMiningNotification(parent, name);
        new Thread() {
            @Override
            public void run() {
                try {
                    if (SpaceAndroidUtils.isCustomer(parent.getCacheDir())) {
                        final String alias = SpaceAndroidUtils.getAlias();
                        final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                        final Map<String, PublicKey> acl = new HashMap<>();
                        acl.put(alias, keys.getPublic());
                        final List<Reference> metaReferences = new ArrayList<>();
                        long size = BCUtils.createEntries(alias, keys, acl, new ArrayList<Reference>(), in, new BCUtils.RecordCallback() {
                            @Override
                            public void onRecord(Record record) {
                                Reference fileReference = null;
                                for (int i = 0; fileReference == null && i < 5; i++) {
                                    try {
                                        fileReference = SpaceUtils.postRecord("file", record);
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
                                metaReference = SpaceUtils.postRecord("meta", metaRecord);
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
                                    previewReference = SpaceUtils.postRecord("preview", previewRecord);
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
                    } else {
                        parent.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent(parent, StripeActivity.class);
                                parent.startActivityForResult(intent, SpaceAndroidUtils.STRIPE_ACTIVITY);
                            }
                        });
                    }
                } catch (SocketException | SocketTimeoutException e) {
                    SpaceAndroidUtils.showErrorDialog(parent, R.string.error_connection, e);
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    SpaceAndroidUtils.showErrorDialog(parent, R.string.error_uploading, e);
                } finally {
                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationManagerCompat.from(parent).cancel(REMOTE_NOTIFICATION_ID);
                        }
                    });
                }
            }
        }.start();
    }

    public static void mineShareLocally(final Activity parent, final String recipientAlias, final PublicKey recipientKey, final Share share) {
        Log.d(SpaceUtils.TAG, "Mine share locally");
        createLocalMiningNotification(parent, "Sharing with " + recipientAlias);
        new Thread() {
            @Override
            public void run() {
                try {
                    final String alias = SpaceAndroidUtils.getAlias();
                    final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                    final Node node = SpaceAndroidUtils.getNode();
                    final InetAddress host = SpaceAndroidUtils.getHost();
                    final Channel shares = new Channel(SpaceUtils.SHARE_CHANNEL_PREFIX + recipientAlias, BCUtils.THRESHOLD_STANDARD, parent.getCacheDir(), host);
                    final Map<String, PublicKey> acl = new HashMap<>();
                    acl.put(recipientAlias, recipientKey);// Recipient first
                    acl.put(alias, keys.getPublic());// Sender second
                    final List<Reference> shareReferences = new ArrayList<>();
                    Record shareRecord = BCUtils.createRecord(alias, keys, acl, shareReferences, share.toByteArray());
                    byte[] shareRecordHashBytes = BCUtils.getHash(shareRecord.toByteArray());
                    ByteString shareRecordHash = ByteString.copyFrom(shareRecordHashBytes);
                    List<BlockEntry> shareEntries = new ArrayList<>();
                    shareEntries.add(BlockEntry.newBuilder()
                            .setRecordHash(shareRecordHash)
                            .setRecord(shareRecord)
                            .build());
                    BCUtils.Pair<byte[], Block> shareResult = node.mine(shares, shareEntries);
                    Log.d(SpaceUtils.TAG, "Mined Share " + new String(BCUtils.encodeBase64URL(shareResult.a)));
                } catch (IOException | BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    SpaceAndroidUtils.showErrorDialog(parent, R.string.error_mining, e);
                } finally {
                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationManagerCompat.from(parent).cancel(LOCAL_NOTIFICATION_ID);
                        }
                    });
                }
            }
        }.start();
    }

    public static void mineShareRemotely(final Activity parent, final String recipientAlias, final PublicKey recipientKey, final Share share) {
        Log.d(SpaceUtils.TAG, "Mine share remotely");
        createRemoteMiningNotification(parent, "Sharing with " + recipientAlias);
        new Thread() {
            @Override
            public void run() {
                try {
                    if (SpaceAndroidUtils.isCustomer(parent.getCacheDir())) {
                        final String alias = SpaceAndroidUtils.getAlias();
                        final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                        final Map<String, PublicKey> acl = new HashMap<>();
                        acl.put(recipientAlias, recipientKey);// Recipient first
                        acl.put(alias, keys.getPublic());// Sender second
                        final List<Reference> shareReferences = new ArrayList<>();
                        Record shareRecord = BCUtils.createRecord(alias, keys, acl, shareReferences, share.toByteArray());
                        Reference shareReference = null;
                        for (int i = 0; shareReference == null && i < 5; i++) {
                            shareReference = SpaceUtils.postRecord("share", shareRecord);
                        }
                        if (shareReference == null) {
                            // FIXME
                            System.err.println("Failed to post share record 5 times");
                            return;
                        }
                        Log.d(SpaceUtils.TAG, "Uploaded Share " + new String(BCUtils.encodeBase64URL(shareReference.getRecordHash().toByteArray())));
                    } else {
                        parent.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent(parent, StripeActivity.class);
                                parent.startActivityForResult(intent, SpaceAndroidUtils.STRIPE_ACTIVITY);
                            }
                        });
                    }
                } catch (SocketException | SocketTimeoutException e) {
                    SpaceAndroidUtils.showErrorDialog(parent, R.string.error_connection, e);
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    SpaceAndroidUtils.showErrorDialog(parent, R.string.error_uploading, e);
                } finally {
                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationManagerCompat.from(parent).cancel(REMOTE_NOTIFICATION_ID);
                        }
                    });
                }
            }
        }.start();
    }

    public static void mineTagLocally(final Activity parent, final Reference meta, final Tag tag) {
        Log.d(SpaceUtils.TAG, "Mine file locally");
        createLocalMiningNotification(parent, "Tagging " + meta + " with " + tag.getValue());
        new Thread() {
            @Override
            public void run() {
                try {
                    final String alias = SpaceAndroidUtils.getAlias();
                    final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                    final Node node = SpaceAndroidUtils.getNode();
                    final InetAddress host = SpaceAndroidUtils.getHost();
                    byte[] metaRecordHashBytes = meta.getRecordHash().toByteArray();
                    final Channel tags = new Channel(SpaceUtils.TAG_CHANNEL_PREFIX + new String(BCUtils.encodeBase64URL(metaRecordHashBytes)), BCUtils.THRESHOLD_STANDARD, parent.getCacheDir(), host);
                    final Map<String, PublicKey> acl = new HashMap<>();
                    acl.put(alias, keys.getPublic());
                    final List<Reference> tagReferences = new ArrayList<>();
                    tagReferences.add(meta);
                    Record tagRecord = BCUtils.createRecord(alias, keys, acl, tagReferences, tag.toByteArray());
                    byte[] tagRecordHashBytes = BCUtils.getHash(tagRecord.toByteArray());
                    ByteString tagRecordHash = ByteString.copyFrom(tagRecordHashBytes);
                    List<BlockEntry> tagEntries = new ArrayList<>();
                    tagEntries.add(BlockEntry.newBuilder()
                            .setRecordHash(tagRecordHash)
                            .setRecord(tagRecord)
                            .build());
                    BCUtils.Pair<byte[], Block> tagResult = node.mine(tags, tagEntries);
                    Log.d(SpaceUtils.TAG, "Mined Tag " + new String(BCUtils.encodeBase64URL(tagResult.a)));
                } catch (IOException | BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    SpaceAndroidUtils.showErrorDialog(parent, R.string.error_mining, e);
                } finally {
                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationManagerCompat.from(parent).cancel(LOCAL_NOTIFICATION_ID);
                        }
                    });
                }
            }
        }.start();
    }

    public static void mineTagRemotely(final Activity parent, final Reference meta, final Tag tag) {
        Log.d(SpaceUtils.TAG, "Mine tag remotely");
        createRemoteMiningNotification(parent, "Tagging " + meta + " with " + tag.getValue());
        new Thread() {
            @Override
            public void run() {
                try {
                    if (SpaceAndroidUtils.isCustomer(parent.getCacheDir())) {
                        final String alias = SpaceAndroidUtils.getAlias();
                        final KeyPair keys = SpaceAndroidUtils.getKeyPair();
                        final Map<String, PublicKey> acl = new HashMap<>();
                        acl.put(alias, keys.getPublic());
                        final List<Reference> tagReferences = new ArrayList<>();
                        tagReferences.add(meta);
                        Record tagRecord = BCUtils.createRecord(alias, keys, acl, tagReferences, tag.toByteArray());
                        Reference tagReference = null;
                        for (int i = 0; tagReference == null && i < 5; i++) {
                            tagReference = SpaceUtils.postRecord("tag", tagRecord);
                        }
                        if (tagReference == null) {
                            // FIXME
                            System.err.println("Failed to post tag record 5 times");
                            return;
                        }
                        Log.d(SpaceUtils.TAG, "Uploaded Tag " + new String(BCUtils.encodeBase64URL(tagReference.getRecordHash().toByteArray())));
                    } else {
                        parent.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent(parent, StripeActivity.class);
                                parent.startActivityForResult(intent, SpaceAndroidUtils.STRIPE_ACTIVITY);
                            }
                        });
                    }
                } catch (SocketException | SocketTimeoutException e) {
                    SpaceAndroidUtils.showErrorDialog(parent, R.string.error_connection, e);
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    SpaceAndroidUtils.showErrorDialog(parent, R.string.error_uploading, e);
                } finally {
                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationManagerCompat.from(parent).cancel(REMOTE_NOTIFICATION_ID);
                        }
                    });
                }
            }
        }.start();
    }
}
