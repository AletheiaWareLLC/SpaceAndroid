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
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aletheiaware.common.utils.CommonUtils;
import com.aletheiaware.finance.FinanceProto.Invoice;
import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.ViewHolder> {

    private final Activity activity;
    private final LayoutInflater inflater;
    private final Map<ByteString, Invoice> invoiceMap = new HashMap<>();
    private final Map<ByteString, Long> timestamps = new HashMap<>();
    private final List<ByteString> sorted = new ArrayList<>();

    public InvoiceAdapter(Activity activity) {
        this.activity = activity;
        inflater = activity.getLayoutInflater();
    }

    public synchronized void addInvoice(ByteString recordHash, long timestamp, Invoice invoice) {
        if (invoice == null) {
            throw new NullPointerException();
        }
        if (invoiceMap.put(recordHash, invoice) == null) {
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
        final LinearLayout view = (LinearLayout) inflater.inflate(R.layout.invoice_list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ByteString hash = holder.getHash();
                if (hash != null) {
                    Invoice invoice = invoiceMap.get(hash);
                    if (invoice != null) {
                        onSelection(hash, invoice);
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
            Invoice invoice = invoiceMap.get(hash);
            if (time != null && invoice != null) {
                holder.set(hash, time, invoice);
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

    public abstract void onSelection(ByteString recordHash, Invoice invoice);

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
            itemId = view.findViewById(R.id.invoice_list_item_id);
            itemTime = view.findViewById(R.id.invoice_list_item_time);
            itemAmount = view.findViewById(R.id.invoice_list_item_amount);
        }

        void set(ByteString hash, Long time, Invoice invoice) {
            this.hash = hash;
            itemId.setText(invoice.getInvoiceId());
            itemTime.setText(CommonUtils.timeToString(time));
            itemTime.setVisibility(View.VISIBLE);
            itemAmount.setText(CommonUtils.moneyToString(invoice.getCurrency(), invoice.getAmountDue()));
            itemAmount.setVisibility(View.VISIBLE);
        }

        ByteString getHash() {
            return hash;
        }

        void setEmptyView() {
            hash = null;
            itemId.setText(R.string.empty_invoice_list);
            itemTime.setVisibility(View.GONE);
            itemAmount.setVisibility(View.GONE);
        }
    }
}
