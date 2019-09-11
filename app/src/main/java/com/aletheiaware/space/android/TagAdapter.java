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

import com.aletheiaware.bc.BC;
import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Channel.RecordCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.PoWChannel;
import com.aletheiaware.bc.utils.ChannelUtils;
import com.aletheiaware.common.utils.CommonUtils;
import com.aletheiaware.space.SpaceProto.Tag;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

public class TagAdapter extends ArrayAdapter<String> implements Filterable {

    public TagAdapter(final Activity activity, final String alias, final KeyPair keys, final ByteString metaRecordHash, final Cache cache, final Network network) {
        super(activity, android.R.layout.simple_dropdown_item_1line);
        new Thread() {
            @Override
            public void run() {
                PoWChannel tags = new PoWChannel(SpaceUtils.SPACE_PREFIX_TAG + new String(CommonUtils.encodeBase64URL(metaRecordHash.toByteArray())), BC.THRESHOLD_STANDARD);
                ChannelUtils.loadHead(tags, cache);
                try {
                    ChannelUtils.pull(tags, cache, network);
                } catch (NoSuchAlgorithmException e) {
                    /* Ignored */
                    e.printStackTrace();
                }
                final Set<String> ts = new HashSet<>();
                try {
                    ChannelUtils.read(tags.getName(), tags.getHead(), null, cache, network, alias, keys, null, new RecordCallback() {
                        @Override
                        public boolean onRecord(ByteString blockHash, Block block, BlockEntry blockEntry, byte[] key, byte[] payload) {
                            try {
                                Tag t = Tag.newBuilder().mergeFrom(payload).build();
                                ts.add(t.getValue());
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
                addAll(ts);
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
