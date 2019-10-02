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
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

public class ImageViewFragment extends UriContentFragment {

    public Drawable drawable;

    public ImageViewFragment() {
        super();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ImageView contentImageView = (ImageView) inflater.inflate(R.layout.fragment_image_view, container, false);
        if (drawable != null) {
            contentImageView.setImageDrawable(drawable);
        } else {
            contentImageView.setImageURI(uri);
        }
        return contentImageView;
    }

    @Override
    public String getType(Activity parent) {
        if (type == null) {
            // TODO update based on uri
            type = SpaceUtils.DEFAULT_IMAGE_TYPE;
        }
        return type;
    }

    @Override
    public Preview getPreview(Activity parent) {
        if (bitmap == null) {
            try (InputStream in = getInputStream(parent)) {
                Bitmap image = BitmapFactory.decodeStream(in);
                bitmap = ThumbnailUtils.extractThumbnail(image, SpaceUtils.PREVIEW_IMAGE_SIZE, SpaceUtils.PREVIEW_IMAGE_SIZE);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    ExifInterface exif = new ExifInterface(in);
                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                    bitmap = SpaceAndroidUtils.rotateBitmap(bitmap, orientation);
                }
            } catch (IOException e) {
                /* Ignored */
                e.printStackTrace();
            }
        }
        return super.getPreview(parent);
    }
}
