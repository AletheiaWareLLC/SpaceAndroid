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
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.aletheiaware.bc.BC;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DatabaseAdapter extends RecyclerView.Adapter<DatabaseAdapter.ViewHolder> implements BC.Channel.RecordCallback {

    private final Activity activity;
    private final LayoutInflater inflater;
    private final Map<ByteString, Long> timestamps = new HashMap<>();
    private final Map<ByteString, Record> records = new HashMap<>();
    private final Map<ByteString, Meta> metas = new HashMap<>();
    private final List<ByteString> sorted = new ArrayList<>();

    DatabaseAdapter(Activity activity) {
        this.activity = activity;
        this.inflater = activity.getLayoutInflater();
    }

    @Override
    public void onRecord(ByteString blockHash, Block block, BlockEntry entry, byte[] key, byte[] payload) {
        try {
            ByteString recordHash = entry.getRecordHash();
            Record record = entry.getRecord();
            Meta meta = Meta.newBuilder().mergeFrom(payload).build();
            Log.d(SpaceUtils.TAG, "Adding file: " + meta);
            records.put(recordHash, record);
            if (metas.put(recordHash, meta) == null) {
                sorted.add(recordHash);// Only add if new
                timestamps.put(recordHash, record.getTimestamp());
                SpaceUtils.sort(sorted, timestamps);
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
            holder.set(hash, metas.get(hash));
        }
    }

    @Override
    public int getItemCount() {
        if (sorted.isEmpty()) {
            return 1;// For empty view
        }
        return sorted.size();
    }

    public abstract void onSelection(ByteString metaRecordHash, Meta meta);

    static class ViewHolder extends RecyclerView.ViewHolder {

        private ByteString hash;
        private Meta meta;

        private ImageView itemImage;
        private TextView itemText;
        private TextView itemTitle;
        private TextView itemSize;

        ViewHolder(LinearLayout view) {
            super(view);
            itemImage = view.findViewById(R.id.list_item_image_view);
            itemText = view.findViewById(R.id.list_item_text_view);
            itemTitle = view.findViewById(R.id.list_item_title);
            itemSize = view.findViewById(R.id.list_item_size);
        }

        void set(ByteString hash, Meta meta) {
            this.hash = hash;
            this.meta = meta;
            if (SpaceUtils.isText(meta.getType())) {
                itemImage.setVisibility(View.INVISIBLE);
                itemText.setVisibility(View.VISIBLE);
                Preview preview = meta.getPreview();
                if (preview == null) {
                    itemText.setText("Text");
                } else {
                    itemText.setText(preview.getData().toStringUtf8());
                }
            } else if (SpaceUtils.isImage(meta.getType())) {
                itemImage.setVisibility(View.VISIBLE);
                itemText.setVisibility(View.INVISIBLE);
                Preview preview = meta.getPreview();
                if (preview == null) {
                    itemImage.setImageDrawable(itemView.getContext().getDrawable(R.drawable.bc_image));
                    itemImage.setBackgroundResource(R.color.black);
                } else {
                    itemImage.setImageBitmap(BitmapFactory.decodeStream(preview.getData().newInput()));
                }
            } else if (SpaceUtils.isVideo(meta.getType())) {
                itemImage.setVisibility(View.VISIBLE);
                itemText.setVisibility(View.INVISIBLE);
                Preview preview = meta.getPreview();
                if (preview == null) {
                    itemImage.setImageDrawable(itemView.getContext().getDrawable(R.drawable.bc_video));
                    itemImage.setBackgroundResource(R.color.black);
                } else {
                    itemImage.setImageBitmap(BitmapFactory.decodeStream(preview.getData().newInput()));
                }
            }
            itemTitle.setText(meta.getName());
            itemSize.setText(BCUtils.sizeToString(meta.getSize()));
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
