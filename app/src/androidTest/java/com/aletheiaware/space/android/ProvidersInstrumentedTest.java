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
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.MemoryCache;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtilsTest;
import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.space.android.ui.ProvidersActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * Instrumented test for ProvidersActivity, which will execute on an Android device.
 */
@RunWith(AndroidJUnit4.class)
public class ProvidersInstrumentedTest {

    public IntentsTestRule<ProvidersActivity> intentsTestRule = new IntentsTestRule<>(ProvidersActivity.class, false, false);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            .around(intentsTestRule);

    @Test
    public void screenshot() throws Exception {
        Cache cache = new MemoryCache();
        BCAndroidUtils.initialize(BCUtilsTest.getTestAlias(), BCUtilsTest.getTestKeys(), cache);
        ProvidersActivity activity = intentsTestRule.launchActivity(new Intent());
        Thread.sleep(1000);
        CommonAndroidUtils.captureScreenshot(activity, "com.aletheiaware.space.android.ProvidersActivity.png");
    }
}
