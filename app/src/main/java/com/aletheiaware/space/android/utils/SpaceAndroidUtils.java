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
import com.aletheiaware.space.utils.SpaceUtils.MinerCallback;
import com.aletheiaware.space.utils.SpaceUtils.RegistrarCallback;
import com.stripe.android.model.Token;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    public static final int PROVIDERS_ACTIVITY = 110;

    public static final String DATA_EXTRA = "data";
    public static final String HASH_EXTRA = "hash";
    public static final String META_EXTRA = "meta";
    public static final String SHARED_EXTRA = "shared";

    private static Uri tempURI = null;
    private static String tempURIType = null;

    @WorkerThread
    public static Map<String, Registration> getRegistrations(String alias, KeyPair keys, Cache cache, Network network) throws IllegalBlockSizeException, InvalidKeyException, NoSuchAlgorithmException, IOException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        final Map<String, Registration> registrations = new HashMap<>();
        FinanceUtils.readRegistration(SpaceUtils.SPACE_REGISTRATION, cache, network, null, null, alias, keys, new RegistrationCallback() {
            @Override
            public boolean onRegistration(BlockEntry entry, Registration registration) {
                Log.d(SpaceUtils.TAG, "Registration: " + registration);
                if (registration != null) {
                    String key = registration.getMerchantAlias();
                    if (!registrations.containsKey(key)) {
                        registrations.put(key, registration);
                    }
                }
                return true;
            }
        });
        return registrations;
    }

    @WorkerThread
    public static Map<String, Subscription> getSubscriptions(String alias, KeyPair keys, Cache cache, Network network) throws IllegalBlockSizeException, InvalidKeyException, NoSuchAlgorithmException, IOException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        final Map<String, Subscription> subscriptions = new HashMap<>();
        FinanceUtils.readSubscription(SpaceUtils.SPACE_SUBSCRIPTION, cache, network, null, null, alias, keys, null, null, new SubscriptionCallback() {
            @Override
            public boolean onSubscription(BlockEntry entry, Subscription subscription) {
                Log.d(SpaceUtils.TAG, "Subscription: " + subscription);
                if (subscription != null) {
                    String key = subscription.getMerchantAlias() + subscription.getProductId() + subscription.getPlanId();
                    if (!subscriptions.containsKey(key)) {
                        subscriptions.put(key, subscription);
                    }
                }
                return true;
            }
        });
        return subscriptions;
    }

    @WorkerThread
    public static Map<String, Registrar> getRegistrars(final Set<String> merchants, Cache cache, Network network) throws IOException {
        final Map<String, Registrar> registrars = new HashMap<>();
        SpaceUtils.readRegistrars(SpaceUtils.getRegistrarChannel(), cache, network, null, new RegistrarCallback() {
            @Override
            public boolean onRegistrar(BlockEntry blockEntry, Registrar registrar) {
                String a = registrar.getMerchant().getAlias();
                if (merchants.contains(a) && !registrars.containsKey(a)) {
                    registrars.put(a, registrar);
                }
                return true;
            }
        });
        return registrars;
    }

    @WorkerThread
    public static Map<String, Miner> getMiners(final Set<String> merchants, Cache cache, Network network) throws IOException {
        final Map<String, Miner> miners = new HashMap<>();
        SpaceUtils.readMiners(SpaceUtils.getMinerChannel(), cache, network, null, new MinerCallback() {
            @Override
            public boolean onMiner(BlockEntry blockEntry, Miner miner) {
                String a = miner.getMerchant().getAlias();
                if (merchants.contains(a) && !miners.containsKey(a)) {
                    miners.put(a, miner);
                }
                return true;
            }
        });
        return miners;
    }

    @WorkerThread
    public static Network getSpaceNetwork() {
        Set<InetAddress> addresses = new HashSet<>();
        for (String host : SpaceUtils.getSpaceHosts(BuildConfig.DEBUG)) {
            try {
                addresses.add(InetAddress.getByName(host));
            } catch (UnknownHostException e) {
                /* Ignored */
                e.printStackTrace();
            }
        }
        return new TCPNetwork(addresses.toArray(new InetAddress[0]));
    }

    @WorkerThread
    public static Network getRegistrarNetwork(Map<String, Registrar> registrars) {
        Set<InetAddress> addresses = new HashSet<>();
        try {
            for (String registrar : registrars.keySet()) {
                Registrar r = registrars.get(registrar);
                if (r != null) {
                    addresses.add(InetAddress.getByName(r.getMerchant().getDomain()));
                }
            }
        } catch (Exception e) {
            /* Ignored */
            e.printStackTrace();
        }
        return new TCPNetwork(addresses.toArray(new InetAddress[0]));
    }

    public interface CustomerIdCallback {
        void onCustomerId(String customerId);
    }

    @UiThread
    public static void registerCustomer(final Activity activity, final Merchant merchant, final String alias, final @Nullable CustomerIdCallback callback) {
        Log.d(SpaceUtils.TAG, "Register Customer");
        new StripeDialog(activity, merchant.getPublishableKey(), "SPACE Registration", null) {
            @Override
            public void onSubmit(final String email, final Token token) {
                new Thread() {
                    @Override
                    public void run() {
                        String customerId = null;
                        try {
                            customerId = BCUtils.register("https://" + merchant.getDomain() + merchant.getRegisterUrl(), alias, email, token.getId());
                        } catch (Exception e) {
                            CommonAndroidUtils.showErrorDialog(activity, R.style.AlertDialogTheme, R.string.error_registering, e);
                        }
                        if (customerId != null && !customerId.isEmpty() && callback != null) {
                            callback.onCustomerId(customerId);
                        }
                    }
                }.start();
            }
        }.create();
    }

    @WorkerThread
    public static String subscribeCustomer(final Activity activity, final Merchant merchant, final Service service, final String alias, final String customerId) {
        String subscriptionId = null;
        try {
            subscriptionId = BCUtils.subscribe("https://" + merchant.getDomain() + service.getSubscribeUrl(), alias, customerId);
        } catch (Exception e) {
            CommonAndroidUtils.showErrorDialog(activity, R.style.AlertDialogTheme, R.string.error_subscribing, e);
        }
        return subscriptionId;
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
