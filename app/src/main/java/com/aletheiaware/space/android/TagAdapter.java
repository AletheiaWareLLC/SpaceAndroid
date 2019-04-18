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

package com.aletheiaware.space.android;

import android.app.Activity;
import android.widget.ArrayAdapter;
import android.widget.Filterable;

import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.BC.Channel.RecordCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Tag;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

public class TagAdapter extends ArrayAdapter<String> implements Filterable {

    public TagAdapter(final Activity activity, final String alias, final KeyPair keys, final byte[] metaRecordHash, boolean shared) {
        super(activity, android.R.layout.simple_dropdown_item_1line);
        new Thread() {
            @Override
            public void run() {
                Channel tags = new Channel(SpaceUtils.SPACE_PREFIX_TAG + new String(BCUtils.encodeBase64URL(metaRecordHash)), BCUtils.THRESHOLD_STANDARD, activity.getCacheDir(), SpaceAndroidUtils.getHostAddress(activity));
                try {
                    tags.sync();
                } catch (IOException | NoSuchAlgorithmException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
                try {
                    tags.read(alias, keys, null, new RecordCallback() {
                        @Override
                        public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                            try {
                                Tag t = Tag.newBuilder().mergeFrom(payload).build();
                                // TODO this is inefficient for large tag counts
                                remove(t.getValue());// Remove duplicates
                                add(t.getValue());// Add to arrayadapter
                            } catch (InvalidProtocolBufferException e) {
                                /* Ignored */
                                e.printStackTrace();
                            }
                            return true;
                        }
                    });
                } catch (IOException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        notifyDataSetChanged();
                    }
                });
            }
        }.start();
    }
}
