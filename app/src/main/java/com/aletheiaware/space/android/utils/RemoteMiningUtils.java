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
import android.app.AlertDialog;
import android.app.Dialog;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.TCPNetwork;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.bc.utils.BCUtils.RecordCallback;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.common.utils.CommonUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Miner;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.utils.SpaceUtils;
import com.aletheiaware.space.utils.SpaceUtils.RemoteMiningListener;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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

import androidx.annotation.WorkerThread;

public class RemoteMiningUtils {

    private RemoteMiningUtils() {
    }

    @WorkerThread
    public static void mineFileAndFinish(final Activity activity, Miner miner, final Map<String, Registrar> registrars, String name, String type, Preview preview, InputStream in) {
        Log.d(SpaceUtils.TAG, "Mine file");
        final ProgressBar[] progressBar = new ProgressBar[1];
        final Dialog[] dialog = new Dialog[1];
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Show progress dialog
                View progressView = View.inflate(activity, R.layout.dialog_progress, null);
                progressBar[0] = progressView.findViewById(R.id.progress_bar);
                dialog[0] = new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
                        .setTitle(R.string.title_dialog_mining)
                        .setCancelable(false)
                        .setView(progressView)
                        .show();
            }
        });
        final String website = "https://" + miner.getMerchant().getDomain();
        final String alias = BCAndroidUtils.getAlias();
        final KeyPair keys = BCAndroidUtils.getKeyPair();
        final Cache cache = BCAndroidUtils.getCache();
        final TCPNetwork network = SpaceAndroidUtils.getRegistrarNetwork(activity, alias);
        final Map<String, PublicKey> acl = new HashMap<>();
        acl.put(alias, keys.getPublic());
        final List<Reference> metaReferences = new ArrayList<>();
        final String minerAlias = miner.getMerchant().getAlias();
        final RemoteMiningListener listener = new RemoteMiningListener() {
            @Override
            public void onReference(Reference reference) {
                Log.d(SpaceUtils.TAG, "Mined Reference: " + reference);
                // TODO update UI
            }

            @Override
            public void onBlock(ByteString hash, Block block) {
                Log.d(SpaceUtils.TAG, "Mined Block: " + block);
                // Write block to cache
                cache.putBlock(hash, block);
                for (String registrarAlias : registrars.keySet()) {
                    if (!registrarAlias.equals(minerAlias)) {
                        Registrar registrar = registrars.get(registrarAlias);
                        if (registrar != null) {
                            try {
                                InetAddress address = InetAddress.getByName(registrar.getMerchant().getDomain());
                                network.cast(address, block.getChannelName(), cache, hash, block);
                            } catch (UnknownHostException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        };
        try {
            long size = BCUtils.createEntries(alias, keys, acl, new ArrayList<Reference>(), in, new RecordCallback() {
                @Override
                public void onRecord(Record record) {
                    final Reference[] fileReference = {null};
                    for (int i = 0; fileReference[0] == null && i < 5; i++) {
                        try {
                            SpaceUtils.postRecord(website, "file", record, 1, new RemoteMiningListener() {
                                @Override
                                public void onReference(Reference reference) {
                                    fileReference[0] = reference;
                                    listener.onReference(reference);
                                }

                                @Override
                                public void onBlock(ByteString hash, Block block) {
                                    listener.onBlock(hash, block);
                                }
                            });
                        } catch (IOException | NoSuchAlgorithmException e) {
                            /* Ignored */
                            e.printStackTrace();
                        }
                    }
                    if (fileReference[0] == null) {
                        // FIXME show error dialog with retry option
                        System.err.println("Failed to post file record 5 times");
                        return;
                    }
                    metaReferences.add(fileReference[0]);
                    Log.d(SpaceUtils.TAG, "Uploaded File " + new String(CommonUtils.encodeBase64URL(fileReference[0].getRecordHash().toByteArray())));
                }
            });
            final Meta meta = Meta.newBuilder()
                    .setName(name)
                    .setType(type)
                    .setSize(size)
                    .build();
            Log.d(SpaceUtils.TAG, "Meta " + meta);
            Record metaRecord = BCUtils.createRecord(alias, keys, acl, metaReferences, meta.toByteArray());
            final Reference[] metaReference = {null};
            for (int i = 0; metaReference[0] == null && i < 5; i++) {
                try {
                    SpaceUtils.postRecord(website, "meta", metaRecord, 1, new RemoteMiningListener() {
                        @Override
                        public void onReference(Reference reference) {
                            metaReference[0] = reference;
                            listener.onReference(reference);
                        }

                        @Override
                        public void onBlock(ByteString hash, Block block) {
                            listener.onBlock(hash, block);
                        }
                    });
                } catch (IOException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
            }
            if (metaReference[0] == null) {
                // FIXME show error dialog with retry option
                System.err.println("Failed to post meta record 5 times");
                return;
            }
            Log.d(SpaceUtils.TAG, "Uploaded Meta " + new String(CommonUtils.encodeBase64URL(metaReference[0].getRecordHash().toByteArray())));
            if (preview != null) {
                Log.d(SpaceUtils.TAG, "Preview " + preview);
                final List<Reference> previewReferences = new ArrayList<>();
                previewReferences.add(Reference.newBuilder()
                        .setTimestamp(metaReference[0].getTimestamp())
                        .setChannelName(metaReference[0].getChannelName())
                        .setRecordHash(metaReference[0].getRecordHash())
                        .build());
                Record previewRecord = BCUtils.createRecord(alias, keys, acl, previewReferences, preview.toByteArray());
                final Reference[] previewReference = {null};
                for (int i = 0; previewReference[0] == null && i < 5; i++) {
                    try {
                        SpaceUtils.postRecord(website, "preview", previewRecord, 1, new RemoteMiningListener() {
                            @Override
                            public void onReference(Reference reference) {
                                previewReference[0] = reference;
                                listener.onReference(reference);
                            }

                            @Override
                            public void onBlock(ByteString hash, Block block) {
                                listener.onBlock(hash, block);
                            }
                        });
                    } catch (IOException e) {
                        /* Ignored */
                        e.printStackTrace();
                    }
                }
                if (previewReference[0] == null) {
                    // FIXME show error dialog with retry option
                    System.err.println("Failed to post preview record 5 times");
                    return;
                }
                Log.d(SpaceUtils.TAG, "Uploaded Preview " + new String(CommonUtils.encodeBase64URL(previewReference[0].getRecordHash().toByteArray())));
            }
        } catch (SocketException | SocketTimeoutException e) {
            CommonAndroidUtils.showErrorDialog(activity, R.style.AlertDialogTheme, activity.getString(R.string.error_connection, website), e);
        } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
            CommonAndroidUtils.showErrorDialog(activity, R.style.AlertDialogTheme, R.string.error_uploading, e);
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Dismiss progress dialog
                if (dialog[0].isShowing()) {
                    dialog[0].dismiss();
                }
                // Finish activity
                activity.setResult(Activity.RESULT_OK);
                activity.finish();
            }
        });
    }
}
