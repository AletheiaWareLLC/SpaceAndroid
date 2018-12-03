package com.aletheiaware.space.android;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.aletheiaware.bc.BC;
import com.aletheiaware.bc.BC.Reference;
import com.aletheiaware.space.Space.Meta;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayInputStream;
import java.util.AbstractSequentialList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DatabaseAdapter extends RecyclerView.Adapter<DatabaseAdapter.ViewHolder> {

    private final Activity activity;
    private final LayoutInflater inflater;
    private final Map<ByteString, Long> timestamps = new HashMap<>();
    private final Map<ByteString, Meta> metas = new HashMap<>();
    private final Map<ByteString, Reference> fileReferences = new HashMap<>();
    private final Map<ByteString, Reference> previewReferences = new HashMap<>();
    private final Map<ByteString, byte[]> previews = new HashMap<>();
    private final List<ByteString> sorted = new ArrayList<>();

    DatabaseAdapter(Activity activity) {
        this.activity = activity;
        this.inflater = activity.getLayoutInflater();
    }

    public long getTimestamp(int index) {
        return timestamps.get(sorted.get(index));
    }

    public Meta getMeta(int index) {
        return metas.get(sorted.get(index));
    }

    public Reference getFileReference(int index) {
        return fileReferences.get(sorted.get(index));
    }

    public Reference getPreviewReference(int index) {
        return previewReferences.get(sorted.get(index));
    }

    public void onResult(ByteString blockHash, BC.Block block, BC.BlockEntry entry, byte[] key, byte[] payload) {
        try {
            ByteString messageHash = entry.getMessageHash();
            BC.Message message = entry.getMessage();
            Meta meta = Meta.newBuilder().mergeFrom(payload).build();
            Log.d(SpaceUtils.TAG, "Adding file: " + meta);
            timestamps.put(messageHash, message.getTimestamp());
            if (metas.put(messageHash, meta) == null) {
                sorted.add(messageHash);// Only add if new
                SpaceUtils.sort(sorted, timestamps);
            }
            List<Reference> refs = message.getReferenceList();
            if (refs.size() > 0) {
                fileReferences.put(messageHash, refs.get(0));
            }
            if (refs.size() > 1) {
                previewReferences.put(messageHash, refs.get(1));
            }
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    notifyDataSetChanged();
                }
            });
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    public void onPreview(ByteString hash, byte[] preview) {
        previews.put(hash, preview);
        activity.runOnUiThread(new Runnable() {
            public void run() {
                notifyDataSetChanged();
            }
        });
    }
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final RelativeLayout view = (RelativeLayout) inflater.inflate(R.layout.list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ByteString hash = holder.getHash();
                if (hash != null) {
                    long timestamp = timestamps.get(hash);
                    Reference file = fileReferences.get(hash);
                    Reference preview = previewReferences.get(hash);
                    onFileSelected(timestamp, holder.getMeta(), file, preview);
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
            holder.set(hash, metas.get(hash), previews.get(hash));
        }
    }

    @Override
    public int getItemCount() {
        if (sorted.isEmpty()) {
            return 1;// For empty view
        }
        return sorted.size();
    }

    public abstract void onFileSelected(long timestamp, Meta meta, Reference file, Reference preview);

    static class ViewHolder extends RecyclerView.ViewHolder {

        private ByteString hash;
        private Meta meta;

        private ImageView itemImage;
        private TextView itemText;
        private TextView itemTitle;
        private TextView itemSize;

        ViewHolder(RelativeLayout view) {
            super(view);
            itemImage = view.findViewById(R.id.list_item_image_view);
            itemText = view.findViewById(R.id.list_item_text);
            itemTitle = view.findViewById(R.id.list_item_title);
            itemSize = view.findViewById(R.id.list_item_size);
        }

        public void set(ByteString hash, Meta meta, byte[] preview) {
            this.hash = hash;
            this.meta = meta;
            if (preview == null) {
                itemText.setVisibility(View.INVISIBLE);
                itemImage.setVisibility(View.INVISIBLE);
            } else {
                if (meta.getType().startsWith("image/")) {
                    final Bitmap image = BitmapFactory.decodeStream(new ByteArrayInputStream(preview));
                    if (image != null) {
                        itemImage.setImageBitmap(image);
                    }
                    itemImage.setVisibility(View.VISIBLE);
                    itemText.setVisibility(View.INVISIBLE);
                } else if (meta.getType().startsWith("text/")) {
                    itemImage.setVisibility(View.INVISIBLE);
                    itemText.setVisibility(View.VISIBLE);
                    itemText.setText(new String(preview));

                }
            }
            itemTitle.setText(meta.getName());
            itemSize.setText(SpaceUtils.sizeToString(meta.getSize()));
        }

        public ByteString getHash() {
            return hash;
        }

        public Meta getMeta() {
            return meta;
        }

        public void setEmptyView() {
            hash = null;
            meta = null;
            itemTitle.setText(R.string.empty_list);
        }
    }
}
