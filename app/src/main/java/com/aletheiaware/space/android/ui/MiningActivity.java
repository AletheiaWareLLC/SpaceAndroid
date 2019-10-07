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

package com.aletheiaware.space.android.ui;

import android.app.Activity;
import android.app.Dialog;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.TCPNetwork;
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

import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public abstract class MiningActivity extends AppCompatActivity {

    @UiThread
    public void mine(final String alias, final KeyPair keys, final Cache cache, final Network network, final Miner miner, final Map<String, Registrar> registrars, final String name, final String type, final Preview preview, final InputStream in) {
        Log.d(SpaceUtils.TAG, "Mine file");
        // Show progress dialog
        View progressView = View.inflate(MiningActivity.this, R.layout.dialog_progress, null);
        final ProgressBar progressBar = progressView.findViewById(R.id.progress_bar);
        progressBar.setIndeterminate(true);
        final Dialog dialog = new AlertDialog.Builder(MiningActivity.this, R.style.AlertDialogTheme)
                .setTitle(R.string.title_dialog_mining)
                .setIcon(R.drawable.bc_mine)
                .setCancelable(false)
                .setView(progressView)
                .show();
        new Thread() {
            @Override
            public void run() {
                final String website = "https://" + miner.getMerchant().getDomain();
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
                                        TCPNetwork.cast(address, block.getChannelName(), cache, network, hash, block);
                                    } catch (UnknownHostException e) {
                                        /* Ignored */
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
                    CommonAndroidUtils.showErrorDialog(MiningActivity.this, R.style.AlertDialogTheme, getString(R.string.error_connection, website), e);
                } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                    CommonAndroidUtils.showErrorDialog(MiningActivity.this, R.style.AlertDialogTheme, R.string.error_uploading, e);
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Dismiss progress dialog
                            if (dialog != null && dialog.isShowing()) {
                                dialog.dismiss();
                            }
                            // Finish activity
                            setResult(Activity.RESULT_OK);
                            finish();
                        }
                    });
                }
            }
        }.start();
    }
}
