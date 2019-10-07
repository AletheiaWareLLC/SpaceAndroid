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

import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Channel.RecordCallback;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.PoWChannel;
import com.aletheiaware.bc.utils.ChannelUtils;
import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RegistrarArrayAdapter extends ArrayAdapter<String> {

    private final Map<String, Registrar> rm = new HashMap<>();

    public RegistrarArrayAdapter(final Activity activity, final Cache cache, final Network network) {
        super(activity, android.R.layout.simple_list_item_multiple_choice);
        new Thread() {
            @Override
            public void run() {
                try {
                    PoWChannel registrars = SpaceUtils.getRegistrarChannel();
                    ChannelUtils.loadHead(registrars, cache);
                    ChannelUtils.read(registrars.getName(), registrars.getHead(), null, cache, network, null, null, null, new RecordCallback() {
                        @Override
                        public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                            try {
                                Registrar r = Registrar.newBuilder().mergeFrom(payload).build();
                                String a = r.getMerchant().getAlias();
                                if (!rm.containsKey(a)) {
                                    add(a);// Add to adapter
                                    rm.put(a, r);// Put in map
                                }
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
                } finally {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            notifyDataSetChanged();
                        }
                    });
                }
            }
        }.start();
    }

    public Registrar get(String alias) {
        return rm.get(alias);
    }
}
