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
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.utils.ChannelUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.Map;

public class AliasAdapter extends ArrayAdapter<String> implements Filterable {

    private final Map<String, Alias> am = new HashMap<>();

    public AliasAdapter(final Activity activity, final Cache cache, final Network network) {
        super(activity, android.R.layout.simple_dropdown_item_1line);
        new Thread() {
            @Override
            public void run() {
                Reference head = ChannelUtils.getHeadReference(AliasUtils.ALIAS_CHANNEL, cache, network);
                if (head != null) {
                    ByteString bh = head.getBlockHash();
                    while (bh != null && !bh.isEmpty()) {
                        Block b = ChannelUtils.getBlock(AliasUtils.ALIAS_CHANNEL, cache, network, bh);
                        if (b == null) {
                            break;
                        }
                        for (BlockEntry e : b.getEntryList()) {
                            Alias.Builder ab = Alias.newBuilder();
                            try {
                                ab.mergeFrom(e.getRecord().getPayload());
                            } catch (InvalidProtocolBufferException ex) {
                                ex.printStackTrace();
                            }
                            Alias a = ab.build();
                            add(a.getAlias());// Add to adapter
                            am.put(a.getAlias(), a);// Add to map
                        }
                        bh = b.getPrevious();
                    }
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
