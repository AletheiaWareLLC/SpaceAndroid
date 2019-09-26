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

import com.aletheiaware.bc.MemoryCache;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtilsTest;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.space.android.ui.SettingsActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.runner.AndroidJUnit4;

/**
 * Instrumented test for SettingsActivity, which will execute on an Android device.
 */
@RunWith(AndroidJUnit4.class)
public class SettingsInstrumentedTest {

    private final IntentsTestRule<SettingsActivity> intentsTestRule = new IntentsTestRule<>(SettingsActivity.class, true, false);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            .around(intentsTestRule);

    @Test
    public void screenshot() throws Exception {
        BCAndroidUtils.initialize(BCUtilsTest.getTestAlias(), BCUtilsTest.getTestKeys(), new MemoryCache());
        SettingsActivity activity = intentsTestRule.launchActivity(new Intent());
        Thread.sleep(1000);
        CommonAndroidUtils.captureScreenshot(activity, "com.aletheiaware.space.android.SettingsActivity.png");
    }
}
