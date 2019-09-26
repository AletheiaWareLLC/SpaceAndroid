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
import android.graphics.Bitmap;
import android.net.Uri;

import com.aletheiaware.common.android.utils.CommonAndroidUtils;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class UriContentFragment extends ContentFragment {

    Uri uri;
    String name;
    String type;
    long size;
    Bitmap bitmap;

    public void setup(Uri uri) {
        this.uri = uri;
    }

    @Override
    public String getName(Activity parent) {
        if (name == null || name.isEmpty()) {
            if (uri != null) {
                name = SpaceAndroidUtils.getName(parent, uri);
            }
        }
        return name;
    }

    @Override
    public String getType(Activity parent) {
        return type;
    }

    @Override
    public long getSize(Activity parent) {
        if (size == 0 && uri != null) {
            try (InputStream in = getInputStream(parent)) {
                size = in.available();
            } catch (IOException e) {
                CommonAndroidUtils.showErrorDialog(parent, R.style.AlertDialogTheme, R.string.error_reading_uri, e);
            }
        }
        return size;
    }

    @Override
    public InputStream getInputStream(Activity parent) {
        if (uri != null) {
            try {
                return parent.getContentResolver().openInputStream(uri);
            } catch (IOException e) {
                CommonAndroidUtils.showErrorDialog(parent, R.style.AlertDialogTheme, R.string.error_reading_uri, e);
            }
        }
        return null;
    }

    @Override
    public Preview getPreview(Activity parent) {
        if (bitmap != null) {
            if (bitmap.getWidth() > SpaceUtils.PREVIEW_IMAGE_SIZE || bitmap.getHeight() > SpaceUtils.PREVIEW_IMAGE_SIZE) {
                // FIXME this scales rectangular bitmap into square and doesn't preserve aspect ratio
                bitmap = Bitmap.createScaledBitmap(bitmap, SpaceUtils.PREVIEW_IMAGE_SIZE, SpaceUtils.PREVIEW_IMAGE_SIZE, false);
            }
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            return Preview.newBuilder()
                    .setType(SpaceUtils.IMAGE_JPEG_TYPE)
                    .setData(ByteString.copyFrom(os.toByteArray()))
                    .setWidth(bitmap.getWidth())
                    .setHeight(bitmap.getHeight())
                    .build();
        }
        return null;
    }
}
