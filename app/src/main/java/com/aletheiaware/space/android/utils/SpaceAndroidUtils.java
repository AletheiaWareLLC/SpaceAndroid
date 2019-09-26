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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.TextView;

import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.TCPNetwork;
import com.aletheiaware.bc.android.ui.StripeDialog;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.finance.FinanceProto.Merchant;
import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Service;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.finance.utils.FinanceUtils.RegistrationCallback;
import com.aletheiaware.finance.utils.FinanceUtils.SubscriptionCallback;
import com.aletheiaware.space.SpaceProto.Miner;
import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.android.BuildConfig;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.ui.ComposeDocumentActivity;
import com.aletheiaware.space.utils.SpaceUtils;
import com.stripe.android.model.Token;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

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

    public static final String DATA_EXTRA = "data";
    public static final String HASH_EXTRA = "hash";
    public static final String META_EXTRA = "meta";
    public static final String SHARED_EXTRA = "shared";

    private static Uri tempURI = null;
    private static String tempURIType = null;

    public static String getSpaceHostname() {
        return SpaceUtils.getSpaceHostname(BuildConfig.DEBUG);
    }

    @WorkerThread
    public static TCPNetwork getRegistrarNetwork(final Context context, String alias) {
        Set<String> registrars = getRegistrarsPreference(context, alias);
        Set<InetAddress> addresses = new HashSet<>();
        try {
            for (String registrar : registrars) {
                addresses.add(InetAddress.getByName(registrar));
            }
        } catch (Exception e) {
            /* Ignored */
            e.printStackTrace();
        }
        return new TCPNetwork(addresses.toArray(new InetAddress[0]));
    }

    public static Uri getTempURI() {
        return tempURI;
    }

    public static String getTempURIType() {
        return tempURIType;
    }

    public static void add(final Activity parent) {
        new AlertDialog.Builder(parent, R.style.AlertDialogTheme)
                .setTitle(R.string.title_dialog_add_document)
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
                                    CommonAndroidUtils.showErrorDialog(parent, R.style.AlertDialogTheme, R.string.error_no_camera, new Exception("Device missing camera feature"));
                                }
                                break;
                            case 3:
                                Log.d(SpaceUtils.TAG, "Record video");
                                if (parent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                                    recordVideo(parent);
                                } else {
                                    CommonAndroidUtils.showErrorDialog(parent, R.style.AlertDialogTheme, R.string.error_no_camera, new Exception("Device missing camera feature"));
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

    private static void createTempFile(Activity parent, String name) {
        File file = new File(parent.getCacheDir(), "file");
        if (!file.exists() && !file.mkdirs()) {
            Log.e(SpaceUtils.TAG, "Error making files directory");
        }
        File f = new File(file, name);
        Log.d(SpaceUtils.TAG, f.getAbsolutePath());
        tempURI = FileProvider.getUriForFile(parent, parent.getString(R.string.file_provider_authority), f);
        Log.d(SpaceUtils.TAG, tempURI.toString());
    }

    private static void takePicture(Activity parent) {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        createTempFile(parent, "image" + System.currentTimeMillis());
        tempURIType = SpaceUtils.DEFAULT_IMAGE_TYPE;
        i.putExtra(MediaStore.EXTRA_OUTPUT, tempURI);
        parent.startActivityForResult(i, SpaceAndroidUtils.CAPTURE_IMAGE_ACTIVITY);
    }

    private static void recordVideo(Activity parent) {
        Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        createTempFile(parent, "video" + System.currentTimeMillis());
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
        CommonAndroidUtils.setPreference(context, context.getString(R.string.preference_sort_key, alias), value);
    }

    public static String getSortPreference(Context context, String alias) {
        // 1 - chronological
        // 2 - reverse-chronological
        return CommonAndroidUtils.getPreference(context, context.getString(R.string.preference_sort_key, alias), "2");
    }

    public static void addRegistrarPreference(Context context, String alias, String registrar) {
        Set<String> registrars = getRegistrarsPreference(context, alias);
        registrars.add(registrar);
        CommonAndroidUtils.setPreferences(context, context.getString(R.string.preference_registrars_key, alias), registrars);
    }

    public static Set<String> getRegistrarsPreference(Context context, String alias) {
        Set<String> defaults = new HashSet<>();
        defaults.add(getSpaceHostname());
        return CommonAndroidUtils.getPreferences(context, context.getString(R.string.preference_registrars_key, alias), defaults);
    }

    public static void setMinerPreference(Context context, String alias, String value) {
        CommonAndroidUtils.setPreference(context, context.getString(R.string.preference_miner_key, alias), value);
    }

    public static String getMinerPreference(Context context, String alias) {
        return CommonAndroidUtils.getPreference(context, context.getString(R.string.preference_miner_key, alias), getSpaceHostname());
    }

    public interface RegistrationIdCallback {
        void onRegistrationId(String registration);
    }

    @UiThread
    public static void registerCustomer(final Activity parent, final Merchant merchant, final String description, final String alias, @Nullable final RegistrationIdCallback callback) {
        Log.d(SpaceUtils.TAG, "Register Customer");
        new StripeDialog(parent, merchant.getPublishableKey(), description, null) {
            @Override
            public void onSubmit(final String email, final Token token) {
                new Thread() {
                    @Override
                    public void run() {
                        String customerId = null;
                        try {
                            customerId = BCUtils.register("https://" + merchant.getDomain() + merchant.getRegisterUrl(), alias, email, token.getId());
                        } catch (Exception e) {
                            CommonAndroidUtils.showErrorDialog(parent, R.style.AlertDialogTheme, R.string.error_registering, e);
                        }
                        if (customerId != null && !customerId.isEmpty() && callback != null) {
                            //
                            callback.onRegistrationId(customerId);
                        }
                    }
                }.start();
            }
        }.create();
    }

    @UiThread
    public static void registerSpaceCustomer(final Activity parent, final Merchant merchant, final String alias, @Nullable final RegistrationIdCallback callback) {
        Log.d(SpaceUtils.TAG, "Register Space Customer");
        registerCustomer(parent, merchant, "Space Registration", alias, callback);
    }

    @WorkerThread
    public static void getRegistration(final Activity parent, final Cache cache, final Network network, final Merchant merchant, final String alias, final KeyPair keys, @Nullable final RegistrationCallback callback) throws IllegalBlockSizeException, InvalidKeyException, NoSuchAlgorithmException, IOException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        Log.d(SpaceUtils.TAG, "Get Registration");
        final boolean[] success = {false};
        FinanceUtils.readRegistration(SpaceUtils.SPACE_REGISTRATION, cache, network, merchant.getAlias(), null, alias, keys, new RegistrationCallback() {
            @Override
            public void onRegistration(BlockEntry entry, Registration registration) {
                Log.d(SpaceUtils.TAG, "Registration: " + registration);
                if (registration != null) {
                    success[0] = true;
                    if (callback != null) {
                        callback.onRegistration(entry, registration);
                    }
                }
            }
        });
        if (!success[0]) {
            parent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    registerSpaceCustomer(parent, merchant, alias, new RegistrationIdCallback() {
                        @Override
                        public void onRegistrationId(String registration) {
                            if (registration != null && !registration.isEmpty()) {
                                Log.d(SpaceUtils.TAG, "Registered: " + registration);
                                try {
                                    FinanceUtils.readRegistration(SpaceUtils.SPACE_REGISTRATION, cache, network, merchant.getAlias(), null, alias, keys, callback);
                                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchPaddingException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                }
            });
        }
    }

    public interface SubscriptionIdCallback {
        void onSubscriptionId(String subscription);
    }

    @WorkerThread
    public static void subscribeCustomer(final Activity parent, final Merchant merchant, final Service service, final String alias, final String customerId, @Nullable final SubscriptionIdCallback callback) {
        String subscriptionId = null;
        try {
            subscriptionId = BCUtils.subscribe("https://" + merchant.getDomain() + service.getSubscribeUrl(), alias, customerId);
        } catch (Exception e) {
            CommonAndroidUtils.showErrorDialog(parent, R.style.AlertDialogTheme, R.string.error_subscribing, e);
        }
        if (subscriptionId != null && !subscriptionId.isEmpty() && callback != null) {
            callback.onSubscriptionId(subscriptionId);
        }
    }

    @WorkerThread
    public static void subscribeSpaceMinerCustomer(final Activity parent, final Miner miner, final String alias, final String customerId, @Nullable final SubscriptionIdCallback callback) {
        subscribeCustomer(parent, miner.getMerchant(), miner.getService(), alias, customerId, callback);
    }

    @WorkerThread
    public static void subscribeSpaceRegistrarCustomer(final Activity parent, final Registrar registrar, final String alias, final String customerId, @Nullable final SubscriptionIdCallback callback) {
        subscribeCustomer(parent, registrar.getMerchant(), registrar.getService(), alias, customerId, callback);
    }

    @WorkerThread
    public static void getMinerSubscription(final Activity parent, final Cache cache, final Network network, final Miner miner, final String alias, final KeyPair keys, final String customerId, @Nullable final SubscriptionCallback callback) throws IllegalBlockSizeException, InvalidKeyException, NoSuchAlgorithmException, IOException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        Log.d(SpaceUtils.TAG, "Get Miner Subscription");
        final boolean[] success = {false};
        FinanceUtils.readSubscription(SpaceUtils.SPACE_SUBSCRIPTION, cache, network, miner.getMerchant().getAlias(), null, alias, keys, miner.getService().getProductId(), miner.getService().getPlanId(), new SubscriptionCallback() {
            @Override
            public void onSubscription(BlockEntry entry, Subscription subscription) {
                if (subscription != null) {
                    success[0] = true;
                    if (callback != null) {
                        callback.onSubscription(entry, subscription);
                    }
                }
            }
        });
        if (!success[0]) {
            SpaceAndroidUtils.subscribeSpaceMinerCustomer(parent, miner, alias, customerId, new SubscriptionIdCallback() {
                @Override
                public void onSubscriptionId(String subscription) {
                    if (subscription != null && !subscription.isEmpty()) {
                        try {
                            FinanceUtils.readSubscription(SpaceUtils.SPACE_SUBSCRIPTION, cache, network, miner.getMerchant().getAlias(), null, alias, keys, miner.getService().getProductId(), miner.getService().getPlanId(), callback);
                        } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchPaddingException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    @WorkerThread
    public static void getRegistrarSubscription(final Activity parent, final Cache cache, final Network network, final Registrar registrar, final String alias, final KeyPair keys, final String customerId, @Nullable final SubscriptionCallback callback) throws IllegalBlockSizeException, InvalidKeyException, NoSuchAlgorithmException, IOException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        Log.d(SpaceUtils.TAG, "Get Registrar Subscription");
        final boolean[] success = {false};
        FinanceUtils.readSubscription(SpaceUtils.SPACE_SUBSCRIPTION, cache, network, registrar.getMerchant().getAlias(), null, alias, keys, registrar.getService().getProductId(), registrar.getService().getPlanId(), new SubscriptionCallback() {
            @Override
            public void onSubscription(BlockEntry entry, Subscription subscription) {
                if (subscription != null) {
                    success[0] = true;
                    if (callback != null) {
                        callback.onSubscription(entry, subscription);
                    }
                }
            }
        });
        if (!success[0]) {
            SpaceAndroidUtils.subscribeSpaceRegistrarCustomer(parent, registrar, alias, customerId, new SubscriptionIdCallback() {
                @Override
                public void onSubscriptionId(String subscription) {
                    if (subscription != null && !subscription.isEmpty()) {
                        try {
                            FinanceUtils.readSubscription(SpaceUtils.SPACE_SUBSCRIPTION, cache, network, registrar.getMerchant().getAlias(), null, alias, keys, registrar.getService().getProductId(), registrar.getService().getPlanId(), callback);
                        } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchPaddingException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    @WorkerThread
    public static void setStatus(final Activity activity, final TextView progressStatus, final @StringRes int s) {
        setStatus(activity, progressStatus, activity.getString(s));
    }

    @WorkerThread
    public static void setStatus(final Activity activity, final TextView progressStatus, final String s) {
        // TODO move into CommonAndroidUtils
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progressStatus != null) {
                    progressStatus.setText(s);
                }
            }
        });
    }
}
