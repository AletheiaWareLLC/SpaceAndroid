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
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.VideoView;

import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.utils.SpaceUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class VideoViewFragment extends UriContentFragment implements OnClickListener, OnPreparedListener, OnCompletionListener, OnErrorListener, OnInfoListener {

    private VideoView contentVideoView;
    private MediaController controller;

    public VideoViewFragment() {
        super();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        Log.i(SpaceUtils.TAG, "On Create View");
        return inflater.inflate(R.layout.fragment_video_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.i(SpaceUtils.TAG, "On View Created");
        contentVideoView = (VideoView) view;
        contentVideoView.setOnClickListener(this);
        contentVideoView.setOnPreparedListener(this);
        contentVideoView.setOnCompletionListener(this);
        contentVideoView.setOnInfoListener(this);
        contentVideoView.setOnErrorListener(this);
        controller = new MediaController(getActivity());
        controller.setMediaPlayer(contentVideoView);
        controller.setAnchorView(contentVideoView);
        contentVideoView.setMediaController(controller);
        contentVideoView.setVideoURI(uri);
        contentVideoView.setZOrderOnTop(true);
        contentVideoView.requestFocus();
    }

    @Override
    public void onClick(View v) {
        Log.i(SpaceUtils.TAG, "On Click");
        if (controller != null) {
            if (controller.isShowing()) {
                controller.hide();
            } else {
                controller.show();
            }
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        Log.i(SpaceUtils.TAG, "On Prepared");
        Log.i(SpaceUtils.TAG, "Duration: " + contentVideoView.getDuration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.i(SpaceUtils.TAG, "Metrics: " + mp.getMetrics());
        }
        if (controller != null) {
            controller.show(3 * 1000);// Show for 3 seconds in ms
        }
        ViewGroup.LayoutParams params = contentVideoView.getLayoutParams();
        params.width = mp.getVideoWidth();
        params.height = mp.getVideoHeight();
        contentVideoView.requestLayout();
        contentVideoView.start();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.i(SpaceUtils.TAG, "On Completion");
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        Log.i(SpaceUtils.TAG, "On Info " + what + " " + extra);
        switch (what) {
            case MediaPlayer.MEDIA_INFO_UNKNOWN:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_UNKNOWN");
                break;
            case MediaPlayer.MEDIA_INFO_STARTED_AS_NEXT:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_STARTED_AS_NEXT");
                break;
            case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_VIDEO_RENDERING_START");
                break;
            case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_VIDEO_TRACK_LAGGING");
                break;
            case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_BUFFERING_START");
                break;
            case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_BUFFERING_END");
                break;
            case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_BAD_INTERLEAVING");
                break;
            case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_NOT_SEEKABLE");
                break;
            case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_METADATA_UPDATE");
                break;
            case MediaPlayer.MEDIA_INFO_AUDIO_NOT_PLAYING:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_AUDIO_NOT_PLAYING");
                break;
            case MediaPlayer.MEDIA_INFO_VIDEO_NOT_PLAYING:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_VIDEO_NOT_PLAYING");
                break;
            case MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_UNSUPPORTED_SUBTITLE");
                break;
            case MediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                Log.i(SpaceUtils.TAG, "MEDIA_INFO_SUBTITLE_TIMED_OUT");
                break;
        }
        return false;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(SpaceUtils.TAG, "On Error " + what + " " + extra);
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.i(SpaceUtils.TAG, "MEDIA_ERROR_UNKNOWN");
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.i(SpaceUtils.TAG, "MEDIA_ERROR_SERVER_DIED");
                break;
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.i(SpaceUtils.TAG, "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK");
                break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                Log.i(SpaceUtils.TAG, "MEDIA_ERROR_TIMED_OUT");
                break;
            case MediaPlayer.MEDIA_ERROR_IO:
                Log.i(SpaceUtils.TAG, "MEDIA_ERROR_IO");
                break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                Log.i(SpaceUtils.TAG, "MEDIA_ERROR_MALFORMED");
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                Log.i(SpaceUtils.TAG, "MEDIA_ERROR_UNSUPPORTED");
                break;
        }
        return false;
    }

    @Override
    public String getType(Activity parent) {
        if (type == null) {
            // TODO update based on uri
            type = SpaceUtils.DEFAULT_VIDEO_TYPE;
        }
        return super.getType(parent);
    }

    @Override
    public Preview getPreview(Activity parent) {
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
        return super.getPreview(parent);
    }
}
