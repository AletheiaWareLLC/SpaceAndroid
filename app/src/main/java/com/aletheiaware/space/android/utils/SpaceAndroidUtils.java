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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.media.ExifInterface;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.BC.Channel.RecordCallback;
import com.aletheiaware.bc.BC.Node;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.finance.FinanceProto.Customer;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.space.SpaceProto;
import com.aletheiaware.space.android.BuildConfig;
import com.aletheiaware.space.android.ComposeDocumentActivity;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Map;

public class SpaceAndroidUtils {

    public static final int ACCESS_ACTIVITY = 100;
    public static final int ACCOUNT_ACTIVITY = 101;
    public static final int CAPTURE_IMAGE_ACTIVITY = 102;
    public static final int CAPTURE_VIDEO_ACTIVITY = 103;
    public static final int COMPOSE_ACTIVITY = 104;
    public static final int DETAIL_ACTIVITY = 105;
    public static final int DOWNLOAD_ACTIVITY = 106;
    public static final int OPEN_ACTIVITY = 107;
    public static final int PREVIEW_ACTIVITY = 108;
    public static final int SETTINGS_ACTIVITY = 109;
    public static final int SHARE_ACTIVITY = 110;
    public static final int STRIPE_ACTIVITY = 111;
    public static final int TAG_ACTIVITY = 112;
    public static final int UPLOAD_ACTIVITY = 113;

    public static final String DATA_EXTRA = "data";
    public static final String EMAIL_EXTRA = "email";
    public static final String HASH_EXTRA = "hash";
    public static final String META_EXTRA = "meta";
    public static final String SHARED_EXTRA = "shared";
    public static final String STRIPE_AMOUNT_EXTRA = "stripe-amount";
    public static final String STRIPE_TOKEN_EXTRA = "stripe-token";

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

    public static InetAddress getHost() {
        try {
            return InetAddress.getByName(SpaceUtils.SPACE_HOST);
        } catch (Exception e) {
            /* Ignored */
            e.printStackTrace();
        }
        return null;
    }

    public static Uri getTempURI() {
        return tempURI;
    }

    public static String getTempURIType() {
        return tempURIType;
    }

    public static void add(final Activity parent) {
        new AlertDialog.Builder(parent, R.style.AlertDialogTheme)
                .setTitle(R.string.title_dialog_add_record)
                .setItems(R.array.inputs, new DialogInterface.OnClickListener() {
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
                                    showErrorDialog(parent, R.string.error_no_camera, new Exception("Device missing camera feature"));
                                }
                                break;
                            case 3:
                                Log.d(SpaceUtils.TAG, "Record video");
                                if (parent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                                    recordVideo(parent);
                                } else {
                                    showErrorDialog(parent, R.string.error_no_camera, new Exception("Device missing camera feature"));
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

    private static void composeDocument(final Activity parent) {
        Intent i = new Intent(parent, ComposeDocumentActivity.class);
        parent.startActivityForResult(i, SpaceAndroidUtils.COMPOSE_ACTIVITY);
    }

    private static void uploadFile(final Activity parent) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        parent.startActivityForResult(i, SpaceAndroidUtils.OPEN_ACTIVITY);
    }

    private static void takePicture(Activity parent) {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File images = new File(parent.getCacheDir(), "image");
        if (!images.exists() &&!images.mkdirs()) {
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
        if (!videos.exists() &&!videos.mkdirs()) {
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

    public static boolean isCustomer(File cache) throws IOException {
        final Channel customers = new Channel(FinanceUtils.CUSTOMER_CHANNEL, BCUtils.THRESHOLD_STANDARD, cache, getHost());
        final Customer.Builder cb = Customer.newBuilder();
        customers.read(alias, keyPair, null, new RecordCallback() {
            @Override
            public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                try {
                    cb.mergeFrom(payload);
                    return false;
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        final Customer customer = cb.build();
        String customerId = customer.getCustomerId();
        return customerId != null && !customerId.isEmpty();
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

    public static void showDeleteKeysDialog(final Activity parent, final DialogInterface.OnClickListener listener) {
        parent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder ab = new AlertDialog.Builder(parent, R.style.AlertDialogTheme);
                ab.setTitle(R.string.delete_keys);
                ab.setMessage(R.string.delete_keys_legalese);
                ab.setPositiveButton(R.string.delete_keys_action, listener);
                ab.show();
            }
        });
    }

    public static void showErrorDialog(final Activity parent, final int resource, final Exception exception) {
        exception.printStackTrace();
        parent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createErrorDialog(parent)
                        .setNeutralButton(R.string.error_report, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                StringBuilder sb = new StringBuilder();
                                sb.append("======== Exception ========");
                                StringWriter sw = new StringWriter();
                                exception.printStackTrace(new PrintWriter(sw));
                                sb.append(sw.toString());
                                support(parent, sb);
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

    @SuppressWarnings("deprecation")
    public static void support(Activity parent, StringBuilder content) {
        content.append("\n\n\n");
        content.append("======== App Info ========\n");
        content.append("Build: ").append(BuildConfig.BUILD_TYPE).append("\n");
        content.append("App ID: ").append(BuildConfig.APPLICATION_ID).append("\n");
        content.append("Version: ").append(BuildConfig.VERSION_NAME).append("\n");
        content.append("======== Device Info ========\n");
        content.append("Board: ").append(Build.BOARD).append("\n");
        content.append("Bootloader: ").append(Build.BOOTLOADER).append("\n");
        content.append("Brand: ").append(Build.BRAND).append("\n");
        content.append("Build ID: ").append(Build.ID).append("\n");
        content.append("Device: ").append(Build.DEVICE).append("\n");
        content.append("Display: ").append(Build.DISPLAY).append("\n");
        content.append("Fingerprint: ").append(Build.FINGERPRINT).append("\n");
        content.append("Hardware: ").append(Build.HARDWARE).append("\n");
        content.append("Host: ").append(Build.HOST).append("\n");
        content.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        content.append("Model: ").append(Build.MODEL).append("\n");
        content.append("Product: ").append(Build.PRODUCT).append("\n");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            content.append("CPU ABI: ").append(Build.CPU_ABI).append("\n");
            content.append("CPU ABI2: ").append(Build.CPU_ABI2).append("\n");
        } else {
            content.append("Supported ABIs: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append("\n");
        }
        content.append("Tags: ").append(Build.TAGS).append("\n");
        content.append("Type: ").append(Build.TYPE).append("\n");
        content.append("User: ").append(Build.USER).append("\n");
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(parent);
        Map<String, ?> map = sharedPrefs.getAll();
        content.append("======== Preferences ========\n");
        for (String key : map.keySet()) {
            content.append(key).append(":").append(map.get(key)).append("\n");
        }
        content.append("\n\n\n");
        Log.d(SpaceUtils.TAG, content.toString());
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{parent.getString(R.string.support_email)});
        intent.putExtra(Intent.EXTRA_SUBJECT, "SPACE Support");
        intent.putExtra(Intent.EXTRA_TEXT, content.toString());
        parent.startActivity(intent);
    }

    private static long calculateSize(File file) {
        if (file.isDirectory()) {
            long sum = 0L;
            for (File f : file.listFiles()) {
                sum += calculateSize(f);
            }
            return sum;
        }
        return file.length();
    }

    private static boolean recursiveDelete(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                if (!recursiveDelete(f)) {
                    return false;
                }
            }
        } else {
            return file.delete();
        }
        return true;
    }

    public static long getCacheSize(Context context) {
        if (context != null) {
            File cache = context.getCacheDir();
            if (cache != null) {
                return calculateSize(cache);
            }
        }
        return 0L;
    }

    public static boolean purgeCache(Context context) {
        if (context != null) {
            File cache = context.getCacheDir();
            if (cache != null) {
                return recursiveDelete(cache);
            }
        }
        return false;
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

    public static void mineFile(final Activity parent, final String name, final String type, final SpaceProto.Preview preview, final InputStream in) {
        MinerUtils.getMinerSelection(parent, new MinerUtils.MinerSelectionCallback() {
            @Override
            public void onMineLocally() {
                MinerUtils.mineFileLocally(parent, name, type, preview, in);
            }

            @Override
            public void onMineRemotely() {
                MinerUtils.mineFileRemotely(parent, name, type, preview, in);
            }
        });
    }

    public static void mineShare(final Activity parent, final String recipient, final PublicKey recipientKey, final SpaceProto.Share share) {
        MinerUtils.getMinerSelection(parent, new MinerUtils.MinerSelectionCallback() {
            @Override
            public void onMineLocally() {
                MinerUtils.mineShareLocally(parent, recipient, recipientKey, share);
            }

            @Override
            public void onMineRemotely() {
                MinerUtils.mineShareRemotely(parent, recipient, recipientKey, share);
            }
        });
    }

    public static void mineTag(final Activity parent, final Reference meta, final SpaceProto.Tag tag) {
        MinerUtils.getMinerSelection(parent, new MinerUtils.MinerSelectionCallback() {
            @Override
            public void onMineLocally() {
                MinerUtils.mineTagLocally(parent, meta, tag);
            }

            @Override
            public void onMineRemotely() {
                MinerUtils.mineTagRemotely(parent, meta, tag);
            }
        });
    }

    @SuppressLint("ApplySharedPref")
    public static void setSortPreference(Activity parent, String value) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(parent);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        String key = parent.getString(R.string.preference_sort_key);
        editor.putString(key, value);
        editor.commit();
    }

    public static String getSortPreference(Activity parent) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(parent);
        String key = parent.getString(R.string.preference_sort_key);
        // 1 - chronological
        // 2 - reverse-chronological
        String value = sharedPrefs.getString(key, "2");
        if (value == null) {
            value = "2";
        }
        return value;
    }
}