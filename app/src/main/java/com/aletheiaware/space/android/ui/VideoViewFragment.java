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

import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.VideoView;

import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.IOException;
import java.io.InputStream;

public class VideoViewFragment extends UriContentFragment {

    public VideoViewFragment() {
        super();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video_view, container, false);
        VideoView contentVideoView = view.findViewById(R.id.fragment_video_view);
        contentVideoView.requestFocus();
        final MediaController controller = new MediaController(inflater.getContext());
        controller.setMediaPlayer(contentVideoView);
        controller.setAnchorView(contentVideoView);
        contentVideoView.setMediaController(controller);
        contentVideoView.setVideoURI(uri);
        contentVideoView.setZOrderOnTop(true);
        contentVideoView.start();
        contentVideoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (controller.isShowing()) {
                    controller.hide();
                } else {
                    controller.show();
                }
            }
        });
        contentVideoView.requestLayout();
        controller.show(3 * 1000);// Show for 3 seconds in ms
        return view;
    }

    @Override
    public String getType() {
        if (type == null) {
            // TODO update based on uri
            type = SpaceUtils.DEFAULT_VIDEO_TYPE;
        }
        return type;
    }

    @Override
    public InputStream getInputStream() {
        try {
            return parent.getContentResolver().openInputStream(uri);
        } catch (IOException e) {
            BCAndroidUtils.showErrorDialog(parent, R.string.error_reading_uri, e);
        }
        return null;
    }

    @Override
    public Preview getPreview() {
        if (bitmap == null) {
            MediaMetadataRetriever retriever = null;
            try {
                retriever = new MediaMetadataRetriever();
                retriever.setDataSource(parent, uri);
                bitmap = retriever.getFrameAtTime();
            } catch (Exception e) {
                /* Ignored */
                e.printStackTrace();
            } finally {
                if (retriever != null) {
                    retriever.release();
                }
            }
        }
        return super.getPreview();
    }
}
