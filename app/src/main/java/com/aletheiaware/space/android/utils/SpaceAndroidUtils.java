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
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.media.ExifInterface;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.TCPNetwork;
import com.aletheiaware.bc.android.ui.CreateAccountActivity;
import com.aletheiaware.bc.android.ui.StripeDialog;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.space.android.BuildConfig;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.ui.ComposeDocumentActivity;
import com.aletheiaware.space.android.ui.MainActivity;
import com.aletheiaware.space.utils.SpaceUtils;
import com.stripe.android.model.Token;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyPair;
import java.util.HashSet;
import java.util.Set;

public class SpaceAndroidUtils {

    public static final int ACCESS_ACTIVITY = BCAndroidUtils.ACCESS_ACTIVITY;
    public static final int ACCOUNT_ACTIVITY = BCAndroidUtils.ACCOUNT_ACTIVITY;
    public static final int CAPTURE_IMAGE_ACTIVITY = 102;
    public static final int CAPTURE_VIDEO_ACTIVITY = 103;
    public static final int COMPOSE_ACTIVITY = 104;
    public static final int DETAIL_ACTIVITY = 105;
    public static final int DOWNLOAD_ACTIVITY = 106;
    public static final int OPEN_ACTIVITY = 107;
    public static final int SETTINGS_ACTIVITY = 108;
    public static final int UPLOAD_ACTIVITY = 109;
    public static final int PROVIDERS_ACTIVITY = 110;

    public static final String DATA_EXTRA = "data";
    public static final String HASH_EXTRA = "hash";
    public static final String META_EXTRA = "meta";
    public static final String SHARED_EXTRA = "shared";
    public static final String MINING_CHANNEL_ID = "Mining Channel";
    public static final String DOWNLOAD_CHANNEL_ID = "Download Channel";
    public static final int MINING_NOTIFICATION_ID = 1;
    public static final int DOWNLOAD_NOTIFICATION_ID = 2;
    public static final int NOTIFICATION_TIMEOUT = 5 * 60 * 1000;// 5 minutes

    private static Uri tempURI = null;
    private static String tempURIType = null;

    public static String getSpaceHostname() {
        return BuildConfig.DEBUG ? SpaceUtils.SPACE_HOST_TEST : SpaceUtils.SPACE_HOST;
    }

    @WorkerThread
    public static InetAddress getSpaceHost() {
        try {
            return InetAddress.getByName(getSpaceHostname());
        } catch (Exception e) {
            /* Ignored */
            e.printStackTrace();
        }
        return null;
    }

    public static String getSpaceWebsite() {
        return "https://" + getSpaceHostname();
    }

    @WorkerThread
    public static Network getStorageNetwork(final Context context, String alias) {
        Set<String> providers = getStorageProvidersPreference(context, alias);
        InetAddress[] addresses = new InetAddress[providers.size()];
        int i = 0;
        try {
            for (String provider : providers) {
                addresses[i] = InetAddress.getByName(provider);
                i++;
            }
        } catch (Exception e) {
            /* Ignored */
            e.printStackTrace();
        }
        return new TCPNetwork(addresses);
    }

    public static Uri getTempURI() {
        return tempURI;
    }

    public static String getTempURIType() {
        return tempURIType;
    }

    public static void add(final Activity parent) {
        // TODO merge upload into composeDocument
        // TODO use fragments to swap editors based on MIME/Meta.type
        new AlertDialog.Builder(parent, R.style.AlertDialogTheme)
                .setTitle(R.string.title_dialog_add_record)
                .setItems(R.array.add_options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                Log.d(SpaceUtils.TAG, "Compose document");
                                composeDocument(parent);
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
                                    BCAndroidUtils.showErrorDialog(parent, R.string.error_no_camera, new Exception("Device missing camera feature"));
                                }
                                break;
                            case 3:
                                Log.d(SpaceUtils.TAG, "Record video");
                                if (parent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                                    recordVideo(parent);
                                } else {
                                    BCAndroidUtils.showErrorDialog(parent, R.string.error_no_camera, new Exception("Device missing camera feature"));
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

    private static void composeDocument(Activity parent) {
        Intent i = new Intent(parent, ComposeDocumentActivity.class);
        parent.startActivityForResult(i, SpaceAndroidUtils.COMPOSE_ACTIVITY);
    }

    private static void uploadFile(Activity parent) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        parent.startActivityForResult(i, SpaceAndroidUtils.OPEN_ACTIVITY);
    }

    private static void takePicture(Activity parent) {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File images = new File(parent.getCacheDir(), "image");
        if (!images.exists() && !images.mkdirs()) {
            Log.e(SpaceUtils.TAG, "Error making image directory");
        }
        File file = new File(images, "image" + System.currentTimeMillis());
        Log.d(SpaceUtils.TAG, file.getAbsolutePath());
        tempURI = FileProvider.getUriForFile(parent, parent.getString(R.string.file_provider_authority), file);
        Log.d(SpaceUtils.TAG, tempURI.toString());
        tempURIType = SpaceUtils.DEFAULT_IMAGE_TYPE;
        i.putExtra(MediaStore.EXTRA_OUTPUT, tempURI);
        parent.startActivityForResult(i, SpaceAndroidUtils.CAPTURE_IMAGE_ACTIVITY);
    }

    private static void recordVideo(Activity parent) {
        Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        File videos = new File(parent.getCacheDir(), "video");
        if (!videos.exists() && !videos.mkdirs()) {
            Log.e(SpaceUtils.TAG, "Error making video directory");
        }
        File file = new File(videos, "video" + System.currentTimeMillis());
        Log.d(SpaceUtils.TAG, file.getAbsolutePath());
        tempURI = FileProvider.getUriForFile(parent, parent.getString(R.string.file_provider_authority), file);
        Log.d(SpaceUtils.TAG, tempURI.toString());
        tempURIType = SpaceUtils.DEFAULT_VIDEO_TYPE;
        i.putExtra(MediaStore.EXTRA_OUTPUT, tempURI);
        parent.startActivityForResult(i, SpaceAndroidUtils.CAPTURE_VIDEO_ACTIVITY);
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

    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        Log.d(SpaceUtils.TAG, "Orientation: " + orientation);
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.setScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return rotated;
        } catch (OutOfMemoryError e) {
            /* Ignored */
            e.printStackTrace();
        }
        return bitmap;
    }

    public static void setSortPreference(Context context, String alias, String value) {
        BCAndroidUtils.setPreference(context, context.getString(R.string.preference_sort_key, alias), value);
    }

    public static String getSortPreference(Context context, String alias) {
        // 1 - chronological
        // 2 - reverse-chronological
        return BCAndroidUtils.getPreference(context, context.getString(R.string.preference_sort_key, alias), "2");
    }

    public static void addStorageProviderPreference(Context context, String alias, String provider) {
        Set<String> providers = getStorageProvidersPreference(context, alias);
        providers.add(provider);
        BCAndroidUtils.setPreferences(context, context.getString(R.string.preference_storage_providers_key, alias), providers);
    }

    public static Set<String> getStorageProvidersPreference(Context context, String alias) {
        Set<String> defaults = new HashSet<>();
        defaults.add(getSpaceHostname());
        return BCAndroidUtils.getPreferences(context, context.getString(R.string.preference_storage_providers_key, alias), defaults);
    }

    public static void setRemoteMinerPreference(Context context, String alias, String value) {
        BCAndroidUtils.setPreference(context, context.getString(R.string.preference_remote_miner_key, alias), value);
    }

    public static String getRemoteMinerPreference(Context context, String alias) {
        return BCAndroidUtils.getPreference(context, context.getString(R.string.preference_remote_miner_key, alias), getSpaceHostname());
    }

    public interface RegistrationCallback {
        void onRegistered(String customerId);
    }

    @UiThread
    public static void registerCustomer(final Activity parent, final String website, final String description, final String alias, final RegistrationCallback callback) {
        new StripeDialog(parent, description, null) {
            @Override
            public void onSubmit(final String email, final Token token) {
                new Thread() {
                    @Override
                    public void run() {
                        String customerId = null;
                        try {
                            customerId = BCUtils.register(website, alias, email, token.getId());
                        } catch (IOException e) {
                            BCAndroidUtils.showErrorDialog(parent, R.string.error_registering, e);
                        }
                        if (customerId != null && !customerId.isEmpty()) {
                            callback.onRegistered(customerId);
                        }
                    }
                }.start();
            }
        }.create();
    }

    @UiThread
    public static void registerSpaceCustomer(final Activity parent, final String provider, final String alias, final RegistrationCallback callback) {
        registerCustomer(parent, "https://" + provider + "/space-register", "Space Registration", alias, callback);
    }

    public interface SubscriptionCallback {
        void onSubscribed(String subscriptionId);
    }

    @WorkerThread
    public static void subscribeCustomer(final Activity parent, final String website, final String alias, final String customerId, final SubscriptionCallback callback) {
        String subscriptionId = null;
        try {
            subscriptionId = BCUtils.subscribe(website, alias, customerId);
        } catch (IOException e) {
            BCAndroidUtils.showErrorDialog(parent, R.string.error_subscribing, e);
        }
        if (subscriptionId != null && !subscriptionId.isEmpty()) {
            callback.onSubscribed(subscriptionId);
        }
    }

    @WorkerThread
    public static void subscribeSpaceMiningCustomer(final Activity parent, final String provider, final String alias, final String customerId, final SubscriptionCallback callback) {
        subscribeCustomer(parent, "https://" + provider + "/space-subscribe-mining", alias, customerId, callback);
    }

    @WorkerThread
    public static void subscribeSpaceStorageCustomer(final Activity parent, final String provider, final String alias, final String customerId, final SubscriptionCallback callback) {
        subscribeCustomer(parent, "https://" + provider + "/space-subscribe-storage", alias, customerId, callback);
    }

    public interface ProviderCallback {
        void onProviderSelected(String provider);
        void onCancelSelection();
    }

    @UiThread
    public static void showProviderPicker(final Activity parent, final String alias, final ProviderCallback callback) {
        Set<String> ps = getStorageProvidersPreference(parent, alias);
        final String[] providers = new String[ps.size()];
        ps.toArray(providers);
        new AlertDialog.Builder(parent, R.style.AlertDialogTheme)
                .setTitle(R.string.title_dialog_select_provider)
                .setItems(providers, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onProviderSelected(providers[which]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        callback.onCancelSelection();
                        dialog.dismiss();
                    }
                })
                .show();
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel miningChannel = new NotificationChannel(MINING_CHANNEL_ID, context.getString(R.string.notification_channel_name_mining), NotificationManager.IMPORTANCE_HIGH);
            miningChannel.setDescription(context.getString(R.string.notification_channel_description_mining));
            NotificationChannel downloadChannel = new NotificationChannel(DOWNLOAD_CHANNEL_ID, context.getString(R.string.notification_channel_name_download), NotificationManager.IMPORTANCE_HIGH);
            downloadChannel.setDescription(context.getString(R.string.notification_channel_description_download));
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(miningChannel);
                notificationManager.createNotificationChannel(downloadChannel);
            }
        }
    }

    public static void createMiningNotification(Context context, String name) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MINING_CHANNEL_ID)
                .setSmallIcon(R.drawable.cloud_upload)
                .setContentTitle(context.getString(R.string.title_notification_mining))
                .setContentText(name)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setProgress(0, 0, true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(NOTIFICATION_TIMEOUT);

        NotificationManagerCompat.from(context).notify(MINING_NOTIFICATION_ID, builder.build());
    }
}