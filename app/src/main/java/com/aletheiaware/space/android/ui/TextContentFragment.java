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

import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

public abstract class TextContentFragment extends ContentFragment {

    private String text;

    public TextContentFragment() {
        super();
    }

    public void setup(String text) {
        if (text == null) {
            text = "";
        }
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public InputStream getInputStream(Activity parent) {
        return new ByteArrayInputStream(getText().getBytes(Charset.defaultCharset()));
    }

    @Override
    public Preview getPreview(Activity parent) {
        String text = getText();
        return Preview.newBuilder()
                .setType(SpaceUtils.TEXT_PLAIN_TYPE)
                .setData(ByteString.copyFromUtf8(text.substring(0, Math.min(text.length(), SpaceUtils.PREVIEW_TEXT_LENGTH))))
                .build();
    }
}
