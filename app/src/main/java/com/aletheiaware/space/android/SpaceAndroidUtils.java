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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.util.Base64;
import android.util.Log;

import com.aletheiaware.bc.BC;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class SpaceAndroidUtils {

    public static final int ACCESS_ACTIVITY = 100;
    public static final int ACCOUNT_ACTIVITY = 101;
    public static final int DETAIL_ACTIVITY = 102;
    public static final int UPLOAD_ACTIVITY = 103;
    public static final int DOWNLOAD_ACTIVITY = 104;
    public static final int OPEN_ACTIVITY = 105;
    public static final int PAYMENT_ACTIVITY = 106;

    public static final String META_EXTRA = "meta";
    public static final String FILE_REFERENCE_EXTRA = "file";
    public static final String PREVIEW_REFERENCE_EXTRA = "preview";
    public static final String TIMESTAMP_EXTRA = "timestamp";
    public static final String EMAIL_EXTRA = "email";
    public static final String STRIPE_AMOUNT_EXTRA = "stripe-amount";
    public static final String STRIPE_TOKEN_EXTRA = "stripe-token";

    private static KeyPair keys = null;
    private static String customerId = null;

    public static boolean isInitialized() {
        return keys != null;
    }

    public static void initialize(KeyPair keyPair) {
        keys = keyPair;
    }

    public static KeyPair getKeys() {
        return keys;
    }

    public static void register(KeyPair keys, String email, String paymentId) throws IOException {
        String params = "stripeEmail=" + URLEncoder.encode(email, "utf-8")
                + "&stripeToken=" + URLEncoder.encode(paymentId, "utf-8")
                + "&publicKeyFormat=" + URLEncoder.encode(keys.getPublic().getFormat(), "utf-8")
                + "&publicKey=" + Base64.encodeToString(keys.getPublic().getEncoded(), Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
        System.out.println("Params:" + params);
        byte[] data = params.getBytes(StandardCharsets.UTF_8);
        //TODO URL url = new URL("https://space.aletheiaware.com:443/register");
        //TODO HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        URL url = new URL("http://space.aletheiaware.com/register");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", Integer.toString(data.length));
        conn.setUseCaches(false);
        try (OutputStream o = conn.getOutputStream()) {
            o.write(data);
            o.flush();
        }

        int response = conn.getResponseCode();
        System.out.println("Response: " + response);
        Scanner in = new Scanner(conn.getInputStream());
        while (in.hasNextLine()) {
            System.out.println(in.nextLine());
        }
    }

    public static void exportKeyPair(File directory, String alias, String accessCode) throws IOException {
        File privFile = new File(directory, alias + ".priv");
        File pubFile = new File(directory, alias + ".pub");
        byte[] privBytes = BCUtils.readFile(privFile);
        byte[] pubBytes = BCUtils.readFile(pubFile);

        String params = "accessCode=" + URLEncoder.encode(accessCode, "utf-8")
                + "&privateKey=" + Base64.encodeToString(privBytes, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE)
                + "&publicKey=" + Base64.encodeToString(pubBytes, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
        System.out.println("Params:" + params);
        byte[] data = params.getBytes(StandardCharsets.UTF_8);

        //TODO URL url = new URL("https://space.aletheiaware.com:443/keys");
        //TODO HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        URL url = new URL("http://space.aletheiaware.com/keys");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", Integer.toString(data.length));
        conn.setUseCaches(false);
        try (OutputStream o = conn.getOutputStream()) {
            o.write(data);
            o.flush();
        }

        int response = conn.getResponseCode();
        System.out.println("Response: " + response);
        Scanner in = new Scanner(conn.getInputStream());
        while (in.hasNextLine()) {
            System.out.println(in.nextLine());
        }
    }

    public static void importKey(File directory, String accessCode) throws IOException {
        //TODO URL url = new URL("https://space.aletheiaware.com:443/keys");
        //TODO HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        URL url = new URL("http://space.aletheiaware.com/keys?accessCode=" + URLEncoder.encode(accessCode, "utf-8"));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("GET");
        conn.setUseCaches(false);

        int response = conn.getResponseCode();
        System.out.println("Response: " + response);
        Scanner in = new Scanner(conn.getInputStream());
        String privKeyLine = in.nextLine();
        System.out.println(privKeyLine);
        byte[] decodedPrivKey = Base64.decode(privKeyLine, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);

        String pubKeyLine = in.nextLine();
        System.out.println(pubKeyLine);
        byte[] decodedPubKey = Base64.decode(pubKeyLine, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);

        System.out.println("PrivateKey:" + decodedPrivKey);
        System.out.println("PublicKey:" + decodedPubKey);

        File privFile = new File(directory, "private.key");
        File pubFile = new File(directory, "public.key");

        BCUtils.writeFile(privFile, decodedPrivKey);
        BCUtils.writeFile(pubFile, decodedPubKey);
    }

    public static String getCustomerId(InetAddress address, KeyPair keys) throws IOException, NoSuchAlgorithmException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException, NoSuchPaddingException, BadPaddingException {
        if (customerId == null) {
            customerId = SpaceUtils.getCustomerId(address, keys);
        }
        return customerId;
    }

    public static void getFileList(final InetAddress address, final KeyPair keys, final DatabaseAdapter adapter) throws IOException, NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, InvalidAlgorithmParameterException, BadPaddingException, NoSuchPaddingException {
        byte[] publicKeyHash = BCUtils.getHash(keys.getPublic().getEncoded());
        String publicKeyString = Base64.encodeToString(publicKeyHash, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);

        String channelName = SpaceUtils.META_CHANNEL_PREFIX + publicKeyString;
        BC.Reference head = SpaceUtils.getHead(address, BC.Reference.newBuilder()
                .setChannelName(channelName)
                .build());
        System.out.println("Head " + head);
        if (head != null) {
            ByteString publicKeyHashByteString = ByteString.copyFrom(publicKeyHash);
            ByteString bh = head.getBlockHash();
            while (bh != null && !bh.isEmpty()) {
                BC.Block b = SpaceUtils.getBlock(address, BC.Reference.newBuilder()
                        .setBlockHash(bh)
                        .setChannelName(channelName)
                        .build());
                for (BC.BlockEntry e : b.getEntryList()) {
                    final ByteString mh = e.getMessageHash();
                    final BC.Message m = e.getMessage();
                    for (BC.Message.Access a : m.getRecipientList()) {
                        if (a.getPublicKeyHash().equals(publicKeyHashByteString)) {
                            byte[] key = a.getSecretKey().toByteArray();
                            byte[] decryptedKey = BCUtils.decryptRSA(keys.getPrivate(), key);
                            byte[] decryptedPayload = BCUtils.decryptAES(decryptedKey, m.getPayload().toByteArray());
                            adapter.addFile(bh, b, e, key, decryptedPayload);
                            List<BC.Reference> refs = m.getReferenceList();
                            if (refs.size() == 2) {
                                // Load preview
                                final BC.Reference preview = refs.get(1);
                                new Thread() {
                                    @Override
                                    public void run() {
                                        try {
                                            adapter.addPreview(mh, SpaceUtils.getMessageData(address, keys, preview));
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        } catch (NoSuchAlgorithmException e) {
                                            e.printStackTrace();
                                        } catch (InvalidKeyException e) {
                                            e.printStackTrace();
                                        } catch (SignatureException e) {
                                            e.printStackTrace();
                                        } catch (InvalidAlgorithmParameterException e) {
                                            e.printStackTrace();
                                        } catch (NoSuchPaddingException e) {
                                            e.printStackTrace();
                                        } catch (BadPaddingException e) {
                                            e.printStackTrace();
                                        } catch (IllegalBlockSizeException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }.start();
                            }
                        }
                    }
                }
                bh = b.getPrevious();
            }
        }
    }

    public static byte[] createPreview(String type, byte[] data) {
        if (SpaceUtils.isImage(type)) {
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

    public static void showDeleteAccountDialog(final Activity parent, final DialogInterface.OnClickListener listener) {
        parent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder ab = new AlertDialog.Builder(parent);
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

    public static AlertDialog.Builder createErrorDialog(Activity parent) {
        return new AlertDialog.Builder(parent)
                .setTitle(R.string.title_dialog_error)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
    }
}