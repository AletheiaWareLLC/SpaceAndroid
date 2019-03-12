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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DatabaseAdapter extends RecyclerView.Adapter<DatabaseAdapter.ViewHolder> {

    private final Activity activity;
    private final LayoutInflater inflater;
    private final Map<ByteString, Long> timestamps = new HashMap<>();
    private final Map<ByteString, Meta> metas = new HashMap<>();
    private final Map<ByteString, Preview> previews = new HashMap<>();
    private final List<ByteString> sorted = new ArrayList<>();

    DatabaseAdapter(Activity activity) {
        this.activity = activity;
        this.inflater = activity.getLayoutInflater();
    }

    public void addFile(ByteString recordHash, long timestamp, Meta meta) {
        if (metas.put(recordHash, meta) == null) {
            sorted.add(recordHash);// Only add if new
            timestamps.put(recordHash, timestamp);
            sort();
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    public void sort() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String key = activity.getString(R.string.preference_sort_key);
        // 1 - chronological
        // 2 - reverse-chronological
        String value = sharedPrefs.getString(key, "2");
        boolean chronological = "1".equals(value);
        SpaceUtils.sort(sorted, timestamps, chronological);
    }

    public void addPreview(ByteString recordHash, Preview preview) {
        previews.put(recordHash, preview);
        activity.runOnUiThread(new Runnable() {
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LinearLayout view = (LinearLayout) inflater.inflate(R.layout.detail_list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ByteString hash = holder.getHash();
                Meta meta = holder.getMeta();
                if (hash != null) {
                    onSelection(hash, meta);
                }
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (sorted.isEmpty()) {
            holder.setEmptyView();
        } else {
            ByteString hash = sorted.get(position);
            Meta meta = metas.get(hash);
            Preview preview = previews.get(hash);
            if (preview == null) {
                loadPreview(hash);
            }
            holder.set(hash, meta, preview);
        }
    }

    @Override
    public int getItemCount() {
        if (sorted.isEmpty()) {
            return 1;// For empty view
        }
        return sorted.size();
    }

    public abstract void loadPreview(ByteString metaRecordHash);

    public abstract void onSelection(ByteString metaRecordHash, Meta meta);

    static class ViewHolder extends RecyclerView.ViewHolder {

        private ByteString hash;
        private Meta meta;

        private ImageView itemImage;
        private TextView itemText;
        private TextView itemTitle;

        ViewHolder(LinearLayout view) {
            super(view);
            itemImage = view.findViewById(R.id.list_item_image_view);
            itemText = view.findViewById(R.id.list_item_text_view);
            itemTitle = view.findViewById(R.id.list_item_title);
        }

        void set(ByteString hash, Meta meta, Preview preview) {
            this.hash = hash;
            this.meta = meta;
            if (SpaceUtils.isText(meta.getType())) {
                itemImage.setVisibility(View.GONE);
                itemText.setVisibility(View.VISIBLE);
                if (preview == null) {
                    setDefaultTextPreview();
                } else {
                    itemText.setText(preview.getData().toStringUtf8());
                }
            } else if (SpaceUtils.isImage(meta.getType())) {
                itemImage.setVisibility(View.VISIBLE);
                itemText.setVisibility(View.GONE);
                if (preview == null) {
                    setDefaultImagePreview();
                } else {
                    Bitmap bitmap = BitmapFactory.decodeStream(preview.getData().newInput());
                    if (bitmap == null) {
                        setDefaultImagePreview();
                    } else {
                        itemImage.setImageBitmap(bitmap);
                    }
                }
            } else if (SpaceUtils.isVideo(meta.getType())) {
                itemImage.setVisibility(View.VISIBLE);
                itemText.setVisibility(View.GONE);
                if (preview == null) {
                    setDefaultVideoPreview();
                } else {
                    Bitmap bitmap = BitmapFactory.decodeStream(preview.getData().newInput());
                    if (bitmap == null) {
                        setDefaultVideoPreview();
                    } else {
                        itemImage.setImageBitmap(bitmap);
                    }
                }
            }
            itemTitle.setText(meta.getName());
        }

        private void setDefaultTextPreview() {
            itemText.setText(itemText.getContext().getString(R.string.default_text_preview));
        }

        private void setDefaultImagePreview() {
            itemImage.setBackgroundResource(R.color.black);
            itemImage.setImageDrawable(ContextCompat.getDrawable(itemImage.getContext(), R.drawable.bc_image));
        }

        private void setDefaultVideoPreview() {
            itemImage.setBackgroundResource(R.color.black);
            itemImage.setImageDrawable(ContextCompat.getDrawable(itemImage.getContext(), R.drawable.bc_video));
        }

        ByteString getHash() {
            return hash;
        }

        Meta getMeta() {
            return meta;
        }

        void setEmptyView() {
            hash = null;
            meta = null;
            itemTitle.setText(R.string.empty_list);
        }
    }
}
