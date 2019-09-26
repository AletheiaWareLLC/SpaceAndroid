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

import android.Manifest;
import android.content.Intent;

import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.Crypto;
import com.aletheiaware.bc.MemoryCache;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtilsTest;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.ui.MainActivity;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.runner.AndroidJUnit4;

/**
 * Instrumented test for MainActivity, which will execute on an Android device.
 */
@RunWith(AndroidJUnit4.class)
public class MainInstrumentedTest {

    private final IntentsTestRule<MainActivity> intentsTestRule = new IntentsTestRule<>(MainActivity.class, true, false);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            .around(intentsTestRule);

    @Test
    public void screenshot() throws Exception {
        BCAndroidUtils.initialize(BCUtilsTest.getTestAlias(), BCUtilsTest.getTestKeys(), new MemoryCache());
        MainActivity activity = intentsTestRule.launchActivity(new Intent());
        Meta meta1 = Meta.newBuilder()
                .setName("Document1")
                .setType(SpaceUtils.TEXT_PLAIN_TYPE)
                .setSize(6)
                .build();
        Meta meta2 = Meta.newBuilder()
                .setName("Shopping")
                .setType(SpaceUtils.TEXT_PLAIN_TYPE)
                .setSize(16)
                .build();
        Meta meta3 = Meta.newBuilder()
                .setName("Doggo")
                .setType(SpaceUtils.IMAGE_JPEG_TYPE)
                .setSize(3425160L)
                .build();
        Meta meta4 = Meta.newBuilder()
                .setName("Meow")
                .setType(SpaceUtils.VIDEO_MPEG_TYPE)
                .setSize(64208642L)
                .build();
        Meta meta5 = Meta.newBuilder()
                .setName("Demo")
                .setType(SpaceUtils.AUDIO_MPEG_TYPE)
                .setSize(5310531L)
                .build();
        Record record1 = Record.newBuilder()
                .setTimestamp(createTimestamp(10000))
                .build();
        Record record2 = Record.newBuilder()
                .setTimestamp(createTimestamp(31000))
                .build();
        Record record3 = Record.newBuilder()
                .setTimestamp(createTimestamp(53100))
                .build();
        Record record4 = Record.newBuilder()
                .setTimestamp(createTimestamp(75310))
                .build();
        Record record5 = Record.newBuilder()
                .setTimestamp(createTimestamp(97531))
                .build();
        ByteString recordHash1 = ByteString.copyFrom(Crypto.getProtobufHash(record1));
        ByteString recordHash2 = ByteString.copyFrom(Crypto.getProtobufHash(record2));
        ByteString recordHash3 = ByteString.copyFrom(Crypto.getProtobufHash(record3));
        ByteString recordHash4 = ByteString.copyFrom(Crypto.getProtobufHash(record4));
        ByteString recordHash5 = ByteString.copyFrom(Crypto.getProtobufHash(record5));
        MetaAdapter adapter = activity.getAdapter();
        adapter.addMeta(recordHash1, record1.getTimestamp(), meta1, false);
        adapter.addMeta(recordHash2, record2.getTimestamp(), meta2, false);
        adapter.addMeta(recordHash3, record3.getTimestamp(), meta3, true);
        adapter.addMeta(recordHash4, record4.getTimestamp(), meta4, false);
        adapter.addMeta(recordHash5, record5.getTimestamp(), meta5, true);
        Preview preview1 = Preview.newBuilder()
                .setType(SpaceUtils.TEXT_PLAIN_TYPE)
                .setData(ByteString.copyFromUtf8("FooBar"))
                .build();
        Preview preview2 = Preview.newBuilder()
                .setType(SpaceUtils.TEXT_PLAIN_TYPE)
                .setData(ByteString.copyFromUtf8("1. Bread"))
                .build();
        //Preview preview3;
        //Preview preview4;
        //Preview preview5;
        adapter.onPreview(recordHash1, preview1);
        adapter.onPreview(recordHash2, preview2);
        //adapter.addPreview(recordHash3, preview3);
        //adapter.addPreview(recordHash4, preview4);
        //adapter.addPreview(recordHash5, preview5);
        Thread.sleep(1000);
        CommonAndroidUtils.captureScreenshot(activity, "com.aletheiaware.space.android.MainActivity.png");
    }

    public static long createTimestamp(long offset) {
        return (System.currentTimeMillis() + offset) * 1000000; // Convert milli to nano seconds
    }
}
