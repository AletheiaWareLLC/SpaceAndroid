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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aletheiaware.common.utils.CommonUtils;
import com.aletheiaware.finance.FinanceProto.UsageRecord;
import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public abstract class UsageRecordAdapter extends RecyclerView.Adapter<UsageRecordAdapter.ViewHolder> {

    private final Activity activity;
    private final LayoutInflater inflater;
    private final Map<ByteString, UsageRecord> usageRecordMap = new HashMap<>();
    private final Map<ByteString, Long> timestamps = new HashMap<>();
    private final List<ByteString> sorted = new ArrayList<>();

    public UsageRecordAdapter(Activity activity) {
        this.activity = activity;
        inflater = activity.getLayoutInflater();
    }

    public synchronized void addUsageRecord(ByteString recordHash, long timestamp, UsageRecord usageRecord) {
        if (usageRecord == null) {
            throw new NullPointerException();
        }
        if (usageRecordMap.put(recordHash, usageRecord) == null) {
            sorted.add(recordHash);// Only add if new
            timestamps.put(recordHash, timestamp);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LinearLayout view = (LinearLayout) inflater.inflate(R.layout.usage_record_list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ByteString hash = holder.getHash();
                if (hash != null) {
                    UsageRecord usageRecord = usageRecordMap.get(hash);
                    if (usageRecord != null) {
                        onSelection(hash, usageRecord);
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
            UsageRecord usageRecord = usageRecordMap.get(hash);
            if (time != null && usageRecord != null) {
                holder.set(hash, time, usageRecord);
            }
        }
    }

    @Override
    public int getItemCount() {
        if (sorted.isEmpty()) {
            return 1;// For empty view
        }
        return sorted.size();
    }

    public abstract void onSelection(ByteString recordHash, UsageRecord usageRecord);

    public boolean isEmpty() {
        return sorted.isEmpty();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private ByteString hash;

        private final TextView itemId;
        private final TextView itemTime;
        private final TextView itemAmount;

        ViewHolder(LinearLayout view) {
            super(view);
            itemId = view.findViewById(R.id.usage_record_list_item_id);
            itemTime = view.findViewById(R.id.usage_record_list_item_time);
            itemAmount = view.findViewById(R.id.usage_record_list_item_amount);
        }

        void set(ByteString hash, Long time, UsageRecord usageRecord) {
            this.hash = hash;
            itemId.setText(usageRecord.getUsageRecordId());
            itemTime.setText(CommonUtils.timeToString(time));
            itemTime.setVisibility(View.VISIBLE);
            itemAmount.setText(CommonUtils.binarySizeToString(usageRecord.getQuantity()));
            itemAmount.setVisibility(View.VISIBLE);
        }

        ByteString getHash() {
            return hash;
        }

        void setEmptyView() {
            hash = null;
            itemId.setText(R.string.empty_usage_record_list);
            itemTime.setVisibility(View.GONE);
            itemAmount.setVisibility(View.GONE);
        }
    }
}
