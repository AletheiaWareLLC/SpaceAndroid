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

import com.aletheiaware.alias.AliasProto.Alias;
import com.aletheiaware.alias.utils.AliasUtils;
import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.BC.Channel.EntryCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class AliasAdapter extends ArrayAdapter<String> implements Filterable {

    private final Map<String, Alias> am = new HashMap<>();

    public AliasAdapter(final Activity activity) {
        super(activity, android.R.layout.simple_dropdown_item_1line);
        new Thread() {
            @Override
            public void run() {
                Channel aliases = new Channel(AliasUtils.ALIAS_CHANNEL, BCUtils.THRESHOLD_STANDARD, activity.getCacheDir(), SpaceAndroidUtils.getSpaceHost());
                try {
                    aliases.sync();
                } catch (IOException | NoSuchAlgorithmException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
                try {
                    aliases.iterate(new EntryCallback() {
                        @Override
                        public boolean onEntry(ByteString blockHash, Block block, BlockEntry blockEntry) {
                            try {
                                Alias a = Alias.newBuilder().mergeFrom(blockEntry.getRecord().getPayload()).build();
                                add(a.getAlias());// Add to arrayadapter
                                am.put(a.getAlias(), a);// Add to map
                            } catch (InvalidProtocolBufferException e) {
                                /* Ignored */
                                e.printStackTrace();
                            }
                            return true;
                        }
                    });
                } catch (IOException e) {
                    SpaceAndroidUtils.showErrorDialog(activity, R.string.error_alias_read_failed, e);
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

    public Alias get(String alias) {
        return am.get(alias);
    }
}
