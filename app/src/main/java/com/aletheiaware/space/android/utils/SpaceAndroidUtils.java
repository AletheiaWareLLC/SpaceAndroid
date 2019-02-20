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
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.aletheiaware.bc.BC;
import com.aletheiaware.bc.BC.Node;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.MainActivity;
import com.aletheiaware.space.android.NewRecordActivity;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.StripeActivity;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class SpaceAndroidUtils {

    public static final int ACCESS_ACTIVITY = 100;
    public static final int ACCOUNT_ACTIVITY = 101;
    public static final int NEW_RECORD_ACTIVITY = 102;
    public static final int DETAIL_ACTIVITY = 103;
    public static final int UPLOAD_ACTIVITY = 104;
    public static final int DOWNLOAD_ACTIVITY = 105;
    public static final int OPEN_ACTIVITY = 106;
    public static final int PAYMENT_ACTIVITY = 107;
    public static final int IMAGE_CAPTURE_ACTIVITY = 108;
    public static final int VIDEO_CAPTURE_ACTIVITY = 109;

    public static final String DATA_EXTRA = "data";
    public static final String EMAIL_EXTRA = "email";
    public static final String META_EXTRA = "meta";
    public static final String META_RECORD_HASH_EXTRA = "meta-record-hash";
    public static final String STRIPE_AMOUNT_EXTRA = "stripe-amount";
    public static final String STRIPE_TOKEN_EXTRA = "stripe-token";

    public static final String FILE_PROVIDER_PACKAGE = "com.aletheiaware.space.android.fileprovider";

    private static final String LOCAL_CHANNEL_ID = "Local Mining Channel";
    private static final String REMOTE_CHANNEL_ID = "Remote Mining Channel";
    private static final int LOCAL_NOTIFICATION_ID = 1;
    private static final int REMOTE_NOTIFICATION_ID = 2;
    private static final int NOTIFICATION_TIMEOUT = 2 * 60 * 1000;// 2 minutes

    private static String alias = null;
    private static KeyPair keyPair = null;
    private static Node node = null;
    private static Uri tempURI = null;
    private static String tempURIType = null;

    public static boolean isInitialized() {
        return alias != null && keyPair != null;
    }

    public static void initialize(String alias, KeyPair keyPair) {
        SpaceAndroidUtils.alias = alias;
        SpaceAndroidUtils.keyPair = keyPair;
        node = new Node(alias, keyPair);
    }

    public static String getAlias() {
        return alias;
    }

    public static KeyPair getKeyPair() {
        return keyPair;
    }

    public static Node getNode() {
        return node;
    }

    public static Uri getTempURI() {
        return tempURI;
    }

    public static String getTempURIType() {
        return tempURIType;
    }

    public static void add(final Activity parent) {
        new AlertDialog.Builder(parent, R.style.AlertDialogTheme)
                .setTitle(R.string.title_dialog_select_input)
                .setItems(R.array.inputs, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                Log.d(SpaceUtils.TAG, "New text record");
                                newRecord(parent);
                                break;
                            case 1:
                                Log.d(SpaceUtils.TAG, "Upload file");
                                uploadFile(parent);
                                break;
                            case 2:
                                Log.d(SpaceUtils.TAG, "Take picture");
                                if (parent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                                    takePicture(parent);
                                } else {
                                    showErrorDialog(parent, R.string.error_no_camera, null);
                                }
                                break;
                            case 3:
                                Log.d(SpaceUtils.TAG, "Record video");
                                if (parent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                                    recordVideo(parent);
                                } else {
                                    showErrorDialog(parent, R.string.error_no_camera, null);
                                }
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
    }

    private static void newRecord(final Activity parent) {
        Intent i = new Intent(parent, NewRecordActivity.class);
        parent.startActivityForResult(i, SpaceAndroidUtils.NEW_RECORD_ACTIVITY);
    }

    private static void uploadFile(final Activity parent) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        parent.startActivityForResult(i, SpaceAndroidUtils.OPEN_ACTIVITY);
    }

    private static void takePicture(Activity parent) {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            File f = File.createTempFile("picture", ".jpg", parent.getCacheDir());
            Log.d(SpaceUtils.TAG, f.getAbsolutePath());
            tempURI = FileProvider.getUriForFile(parent, FILE_PROVIDER_PACKAGE, f);
            tempURIType = SpaceUtils.DEFAULT_IMAGE_TYPE;
            i.putExtra(MediaStore.EXTRA_OUTPUT, tempURI);
        } catch (IOException e) {
            e.printStackTrace();
        }
        parent.startActivityForResult(i, SpaceAndroidUtils.IMAGE_CAPTURE_ACTIVITY);
    }

    private static void recordVideo(Activity parent) {
        Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        try {
            File f = File.createTempFile("video", ".mpg", parent.getCacheDir());
            Log.d(SpaceUtils.TAG, f.getAbsolutePath());
            tempURI = FileProvider.getUriForFile(parent, FILE_PROVIDER_PACKAGE, f);
            tempURIType = SpaceUtils.DEFAULT_VIDEO_TYPE;
            i.putExtra(MediaStore.EXTRA_OUTPUT, tempURI);
        } catch (IOException e) {
            e.printStackTrace();
        }
        parent.startActivityForResult(i, SpaceAndroidUtils.VIDEO_CAPTURE_ACTIVITY);
    }

    public static void mine(final Activity parent, final String name, final String type, final Preview preview, final InputStream in) {
        new AlertDialog.Builder(parent, R.style.AlertDialogTheme)
                .setTitle(R.string.title_dialog_select_miner)
                .setItems(R.array.miners, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                Log.d(SpaceUtils.TAG, "Mine locally");
                                createLocalMiningNotification(parent, name);
                                new Thread() {
                                    @Override
                                    public void run() {
                                        try {
                                            final InetAddress host = InetAddress.getByName(SpaceUtils.SPACE_HOST);
                                            final BC.Channel metaChannel = new BC.Channel(SpaceUtils.META_CHANNEL_PREFIX + alias, BCUtils.THRESHOLD_STANDARD, parent.getCacheDir(), host);
                                            final BC.Channel fileChannel = new BC.Channel(SpaceUtils.FILE_CHANNEL_PREFIX + alias, BCUtils.THRESHOLD_STANDARD, parent.getCacheDir(), host);
                                            final Map<String, PublicKey> acl = new HashMap<>();
                                            acl.put(alias, keyPair.getPublic());
                                            final List<Reference> references = new ArrayList<>();
                                            long size = BCUtils.createEntries(alias, keyPair, acl, new ArrayList<Reference>(), in, new BCUtils.RecordCallback() {
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
                                                        BCUtils.Pair<byte[], Block> result = node.mine(fileChannel, entries);
                                                        ByteString blockHash = ByteString.copyFrom(result.a);
                                                        Block block = result.b;
                                                        references.add(Reference.newBuilder()
                                                                .setTimestamp(block.getTimestamp())
                                                                .setChannelName(fileChannel.name)
                                                                .setBlockHash(blockHash)
                                                                .setRecordHash(recordHash)
                                                                .build());
                                                        Log.d(SpaceUtils.TAG, "Mined File " + new String(BCUtils.encodeBase64URL(result.a)));
                                                    } catch (BadPaddingException e) {
                                                        e.printStackTrace();
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    } catch (NoSuchAlgorithmException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            });
                                            final Meta meta = Meta.newBuilder()
                                                    .setName(name)
                                                    .setType(type)
                                                    .setSize(size)
                                                    .setPreview(preview)
                                                    .build();
                                            Log.d(SpaceUtils.TAG, "Meta " + meta);
                                            Record record = BCUtils.createRecord(alias, keyPair, acl, references, meta.toByteArray());
                                            ByteString recordHash = ByteString.copyFrom(BCUtils.getHash(record.toByteArray()));
                                            List<BlockEntry> entries = new ArrayList<>();
                                            entries.add(BlockEntry.newBuilder()
                                                    .setRecordHash(recordHash)
                                                    .setRecord(record)
                                                    .build());
                                            BCUtils.Pair<byte[], Block> result = node.mine(metaChannel, entries);
                                            Log.d(SpaceUtils.TAG, "Mined Meta " + new String(BCUtils.encodeBase64URL(result.a)));
                                        } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                                            SpaceAndroidUtils.showErrorDialog(parent, R.string.error_mining, e);
                                        } finally {
                                            parent.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    NotificationManagerCompat.from(parent).cancel(LOCAL_NOTIFICATION_ID);
                                                    parent.setResult(Activity.RESULT_OK);
                                                    parent.finish();
                                                }
                                            });
                                        }
                                    }
                                }.start();
                                break;
                            case 1:
                                Log.d(SpaceUtils.TAG, "Mine remotely");
                                createRemoteMiningNotification(parent, name);
                                new Thread() {
                                    @Override
                                    public void run() {
                                        try {
                                            final InetAddress address = InetAddress.getByName(SpaceUtils.SPACE_HOST);
                                            final String customerId = FinanceUtils.getCustomerId(address, alias, keyPair);
                                            if (customerId == null) {
                                                parent.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Intent intent = new Intent(parent, StripeActivity.class);
                                                        parent.startActivityForResult(intent, SpaceAndroidUtils.PAYMENT_ACTIVITY);
                                                    }
                                                });
                                            } else {
                                                final Map<String, PublicKey> acl = new HashMap<>();
                                                acl.put(alias, keyPair.getPublic());
                                                final List<Reference> references = new ArrayList<>();
                                                long size = BCUtils.createEntries(alias, keyPair, acl, new ArrayList<Reference>(), in, new BCUtils.RecordCallback() {
                                                    @Override
                                                    public void onRecord(Record record) {
                                                        try {
                                                            Reference reference = SpaceUtils.postRecord("file", record);
                                                            // TODO handle null reference
                                                            references.add(reference);
                                                            Log.d(SpaceUtils.TAG, "Uploaded File " + new String(BCUtils.encodeBase64URL(reference.getRecordHash().toByteArray())));
                                                        } catch (IOException e) {
                                                            e.printStackTrace();
                                                        }
                                                    }
                                                });
                                                final Meta meta = Meta.newBuilder()
                                                        .setName(name)
                                                        .setType(type)
                                                        .setSize(size)
                                                        .build();
                                                Log.d(SpaceUtils.TAG, "Meta " + meta);
                                                Record record = BCUtils.createRecord(alias, keyPair, acl, references, meta.toByteArray());
                                                Reference reference = SpaceUtils.postRecord("meta", record);
                                                Log.d(SpaceUtils.TAG, "Uploaded Meta " + new String(BCUtils.encodeBase64URL(reference.getRecordHash().toByteArray())));
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
                                                    parent.setResult(Activity.RESULT_OK);
                                                    parent.finish();
                                                }
                                            });
                                        }
                                    }
                                }.start();
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
    }

    public static byte[] createPreview(String type, byte[] data) {
        if (SpaceUtils.isVideo(type)) {
            // TODO
        } else if (SpaceUtils.isImage(type)) {
            Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);
            int width = image.getWidth();
            int height = image.getHeight();
            System.out.println("Image size: " + width + "x" + height);
            float max = Math.max(width, height);
            // Only create preview if image is bigger than preview image size
            if (max > SpaceUtils.PREVIEW_IMAGE_SIZE) {
                Bitmap preview = ThumbnailUtils.extractThumbnail(image, SpaceUtils.PREVIEW_IMAGE_SIZE, SpaceUtils.PREVIEW_IMAGE_SIZE);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                switch (type) {
                    case SpaceUtils.IMAGE_JPEG_TYPE:
                        preview.compress(Bitmap.CompressFormat.JPEG, 100, os);
                        break;
                    case SpaceUtils.IMAGE_PNG_TYPE:
                        preview.compress(Bitmap.CompressFormat.PNG, 100, os);
                        break;
                    case SpaceUtils.IMAGE_WEBP_TYPE:
                        preview.compress(Bitmap.CompressFormat.WEBP, 100, os);
                        break;
                    default:
                        Log.e(SpaceUtils.TAG, "Unrecognized type: " + type);
                        return null;
                }
                return os.toByteArray();
            }
        } else if (SpaceUtils.isText(type)) {
            // Only create preview if text is longer than preview text length
            if (data.length > SpaceUtils.PREVIEW_TEXT_LENGTH) {
                // TODO cut on character boundary instead of byte boundary
                return Arrays.copyOf(data, SpaceUtils.PREVIEW_TEXT_LENGTH);
            }
        } else {
            // TODO create binary string
        }
        return null;
    }

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

    public static String getName(Context context, Uri uri) {
        String name = null;
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
            cursor.close();
        }
        if (name == null) {
            // Return last part of URI path
            String path = uri.getPath();
            if (path != null) {
                name = path.substring(path.lastIndexOf('/') + 1);
            }
        }
        return name;
    }

    public static void showDeleteAccountDialog(final Activity parent, final DialogInterface.OnClickListener listener) {
        parent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder ab = new AlertDialog.Builder(parent, R.style.AlertDialogTheme);
                ab.setTitle(R.string.delete_account);
                ab.setMessage(R.string.delete_account_legalese);
                ab.setPositiveButton(R.string.delete_account_action, listener);
                ab.show();
            }
        });
    }

    public static void showErrorDialog(final Activity parent, final int resource, final Exception exception) {
        if (exception != null) {
            exception.printStackTrace();
        }
        parent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createErrorDialog(parent)
                        .setNeutralButton(R.string.more_info, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                StringWriter sw = new StringWriter();
                                if (exception != null) {
                                    exception.printStackTrace(new PrintWriter(sw));
                                }
                                showErrorDialog(parent, sw.toString());
                            }
                        })
                        .setMessage(resource)
                        .show();
            }
        });
    }

    public static void showErrorDialog(final Activity parent, final String message) {
        parent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createErrorDialog(parent)
                        .setMessage(message)
                        .show();
            }
        });
    }

    private static AlertDialog.Builder createErrorDialog(Activity parent) {
        return new AlertDialog.Builder(parent, R.style.AlertDialogTheme)
                .setTitle(R.string.title_dialog_error)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
    }
}