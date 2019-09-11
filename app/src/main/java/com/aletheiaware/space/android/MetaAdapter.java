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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aletheiaware.common.utils.CommonUtils;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.utils.PreviewUtils.PreviewCallback;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class MetaAdapter extends RecyclerView.Adapter<MetaAdapter.ViewHolder> implements PreviewCallback {

    private final Activity activity;
    private final LayoutInflater inflater;
    private final String alias;
    private final Map<ByteString, Meta> metas = new HashMap<>();
    private final Map<ByteString, Preview> previews = new HashMap<>();
    private final Map<ByteString, Long> timestamps = new HashMap<>();
    private final Set<ByteString> shared = new HashSet<>();
    private final List<ByteString> sorted = new ArrayList<>();
    private ByteString metaHead;
    private ByteString shareHead;

    public MetaAdapter(Activity activity, String alias) {
        this.activity = activity;
        inflater = activity.getLayoutInflater();
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }

    public synchronized void addMeta(ByteString recordHash, long timestamp, Meta meta, boolean shared) {
        if (meta == null) {
            throw new NullPointerException();
        }
        if (metas.put(recordHash, meta) == null) {
            sorted.add(recordHash);// Only add if new
            timestamps.put(recordHash, timestamp);
            if (shared) {
                this.shared.add(recordHash);
            }
            sort();
        }
    }

    @Override
    public void onPreview(ByteString hash, Preview preview) {
        if (preview != null) {
            previews.put(hash, preview);
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    notifyDataSetChanged();
                }
            });
        }
    }

    private synchronized void sort() {
        String value = SpaceAndroidUtils.getSortPreference(activity, alias);
        boolean chronological = "1".equals(value);
        SpaceUtils.sort(sorted, timestamps, chronological);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    protected boolean isShared(ByteString hash) {
        return shared.contains(hash);
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
                if (hash != null) {
                    Meta meta = metas.get(hash);
                    if (meta != null) {
                        onSelection(hash, meta);
                    }
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
            final ByteString hash = sorted.get(position);
            Long time = timestamps.get(hash);
            Meta meta = metas.get(hash);
            if (time != null && meta != null) {
                if (!previews.containsKey(hash)) {
                    loadPreview(hash);
                }
                Preview preview = previews.get(hash);
                holder.set(hash, time, meta, preview, isShared(hash));
            }
        }
    }

    protected abstract void loadPreview(ByteString hash);

    @Override
    public int getItemCount() {
        if (sorted.isEmpty()) {
            return 1;// For empty view
        }
        return sorted.size();
    }

    public abstract void onSelection(ByteString metaRecordHash, Meta meta);

    public boolean isEmpty() {
        return sorted.isEmpty();
    }

    public ByteString getMetaHead() {
        return metaHead;
    }

    public void setMetaHead(ByteString metaHead) {
        this.metaHead = metaHead;
    }

    public ByteString getShareHead() {
        return shareHead;
    }

    public void setShareHead(ByteString shareHead) {
        this.shareHead = shareHead;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private ByteString hash;

        private final ImageView itemImage;
        private final TextView itemText;
        private final TextView itemTitle;
        private final TextView itemTime;

        ViewHolder(LinearLayout view) {
            super(view);
            itemImage = view.findViewById(R.id.detail_list_item_image_view);
            itemText = view.findViewById(R.id.detail_list_item_text_view);
            itemTitle = view.findViewById(R.id.detail_list_item_title);
            itemTime = view.findViewById(R.id.detail_list_item_time);
        }

        void set(ByteString hash, Long time, Meta meta, Preview preview, boolean shared) {
            this.hash = hash;
            String type = meta.getType();
            if (SpaceUtils.isText(type)) {
                itemImage.setVisibility(View.GONE);
                itemText.setVisibility(View.VISIBLE);
                if (preview == null) {
                    setDefaultTextPreview();
                } else {
                    itemText.setText(preview.getData().toStringUtf8());
                }
            } else if (SpaceUtils.isImage(type)) {
                itemImage.setVisibility(View.VISIBLE);
                itemText.setVisibility(View.GONE);
                if (preview == null) {
                    setDefaultImagePreview(shared);
                } else {
                    Bitmap bitmap = BitmapFactory.decodeStream(preview.getData().newInput());
                    if (bitmap == null) {
                        setDefaultImagePreview(shared);
                    } else {
                        itemImage.setImageBitmap(bitmap);
                    }
                }
            } else if (SpaceUtils.isVideo(type)) {
                itemImage.setVisibility(View.VISIBLE);
                itemText.setVisibility(View.GONE);
                if (preview == null) {
                    setDefaultVideoPreview(shared);
                } else {
                    Bitmap bitmap = BitmapFactory.decodeStream(preview.getData().newInput());
                    if (bitmap == null) {
                        setDefaultVideoPreview(shared);
                    } else {
                        itemImage.setImageBitmap(bitmap);
                    }
                }
            } else if (SpaceUtils.isAudio(type)) {
                itemImage.setVisibility(View.VISIBLE);
                itemText.setVisibility(View.GONE);
                if (preview == null) {
                    setDefaultAudioPreview(shared);
                } else {
                    // TODO decode preview.getData().newInput() into audio visualization
                    //Bitmap bitmap = BitmapFactory.decodeStream(preview.getData().newInput());
                    //if (bitmap == null) {
                        setDefaultAudioPreview(shared);
                    //} else {
                    //    itemImage.setImageBitmap(bitmap);
                    //}
                }
            } else {
                itemImage.setVisibility(View.VISIBLE);
                itemText.setVisibility(View.GONE);
                if (preview == null) {
                    setDefaultFilePreview(shared);
                } else {
                    Bitmap bitmap = BitmapFactory.decodeStream(preview.getData().newInput());
                    if (bitmap == null) {
                        setDefaultFilePreview(shared);
                    } else {
                        itemImage.setImageBitmap(bitmap);
                    }
                }
            }
            if (shared) {
                itemText.setTextColor(ContextCompat.getColor(itemText.getContext(), R.color.highlight));
                itemTime.setTextColor(ContextCompat.getColor(itemTitle.getContext(), R.color.highlight));
                itemTitle.setTextColor(ContextCompat.getColor(itemTitle.getContext(), R.color.highlight));
            } else {
                itemText.setTextColor(ContextCompat.getColor(itemText.getContext(), R.color.text_primary));
                itemTime.setTextColor(ContextCompat.getColor(itemTitle.getContext(), R.color.text_primary));
                itemTitle.setTextColor(ContextCompat.getColor(itemTitle.getContext(), R.color.text_primary));
            }
            itemTitle.setText(meta.getName());
            itemTime.setText(CommonUtils.timeToString(time));
        }

        private void setDefaultTextPreview() {
            itemText.setText(itemText.getContext().getString(R.string.default_text_preview));
        }

        private void setDefaultImagePreview(boolean shared) {
            if (shared) {
                itemImage.setImageDrawable(ContextCompat.getDrawable(itemImage.getContext(), R.drawable.bc_image_shared));
            } else {
                itemImage.setImageDrawable(ContextCompat.getDrawable(itemImage.getContext(), R.drawable.bc_image));
            }
        }

        private void setDefaultVideoPreview(boolean shared) {
            if (shared) {
                itemImage.setImageDrawable(ContextCompat.getDrawable(itemImage.getContext(), R.drawable.bc_video_shared));
            } else {
                itemImage.setImageDrawable(ContextCompat.getDrawable(itemImage.getContext(), R.drawable.bc_video));
            }
        }

        private void setDefaultAudioPreview(boolean shared) {
            if (shared) {
                itemImage.setImageDrawable(ContextCompat.getDrawable(itemImage.getContext(), R.drawable.bc_audio_shared));
            } else {
                itemImage.setImageDrawable(ContextCompat.getDrawable(itemImage.getContext(), R.drawable.bc_audio));
            }
        }

        private void setDefaultFilePreview(boolean shared) {
            if (shared) {
                itemImage.setImageDrawable(ContextCompat.getDrawable(itemImage.getContext(), R.drawable.file_shared));
            } else {
                itemImage.setImageDrawable(ContextCompat.getDrawable(itemImage.getContext(), R.drawable.file));
            }
        }

        ByteString getHash() {
            return hash;
        }

        void setEmptyView() {
            hash = null;
            itemTitle.setText(R.string.empty_detail_list);
        }
    }
}
