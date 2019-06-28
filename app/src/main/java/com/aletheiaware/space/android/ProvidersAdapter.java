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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ProvidersAdapter extends RecyclerView.Adapter<ProvidersAdapter.ViewHolder> {

    private final Activity activity;
    private final LayoutInflater inflater;
    private final Set<String> providers = new HashSet<>();
    private final List<String> sorted = new ArrayList<>();
    private final Map<String, String> registrationIds = new HashMap<>();
    private final Map<String, String> subscriptionStorageIds = new HashMap<>();
    private final Map<String, String> subscriptionMiningIds = new HashMap<>();

    public ProvidersAdapter(Activity activity) {
        this.activity = activity;
        this.inflater = activity.getLayoutInflater();
    }

    public void addProvider(String provider, String registrationId, String subscriptionStorageId, String subscriptionMiningId) {
        registrationIds.put(provider, registrationId);
        subscriptionStorageIds.put(provider, subscriptionStorageId);
        subscriptionMiningIds.put(provider, subscriptionMiningId);
        if (providers.add(provider)) {
            sorted.add(provider);// Only add if new
            sort();
        }
    }

    public synchronized void sort() {
        SpaceUtils.sort(sorted, registrationIds, subscriptionStorageIds, subscriptionMiningIds);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LinearLayout view = (LinearLayout) inflater.inflate(R.layout.provider_list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String provider = holder.getProvider();
                onClickProvider(provider, registrationIds.get(provider), subscriptionStorageIds.get(provider), subscriptionMiningIds.get(provider));
            }
        });
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String provider = holder.getProvider();
                onLongClickProvider(provider);
                return true;
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (sorted.isEmpty()) {
            holder.setEmptyView();
        } else {
            String provider = sorted.get(position);
            holder.set(provider, registrationIds.containsKey(provider), subscriptionStorageIds.containsKey(provider), subscriptionMiningIds.containsKey(provider));
        }
    }

    @Override
    public int getItemCount() {
        if (sorted.isEmpty()) {
            return 1;// For empty view
        }
        return sorted.size();
    }

    public abstract void onLongClickProvider(String provider);

    public abstract void onClickProvider(String provider, String registrationId, String subscriptionStorageId, String subscriptionMiningId);

    public boolean isEmpty() {
        return sorted.isEmpty();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private String provider;

        private ImageView providerRegistered;
        private ImageView providerSubscribedStorage;
        private ImageView providerSubscribedMining;
        private TextView providerNameText;

        ViewHolder(LinearLayout view) {
            super(view);
            providerRegistered = view.findViewById(R.id.provider_registered);
            providerSubscribedStorage = view.findViewById(R.id.provider_subscribed_storage);
            providerSubscribedMining = view.findViewById(R.id.provider_subscribed_mining);
            providerNameText = view.findViewById(R.id.provider_name);
        }

        void set(String provider, boolean registered, boolean subscribedStorage, boolean subscribedMining) {
            Log.d(SpaceUtils.TAG, "Setting " + provider + " " + registered+ " " + subscribedStorage + " " + subscribedMining);
            this.provider = provider;
            providerNameText.setVisibility(View.VISIBLE);
            providerNameText.setText(provider);
            providerRegistered.setVisibility(registered ? View.VISIBLE : View.GONE);
            providerSubscribedStorage.setVisibility(subscribedStorage ? View.VISIBLE : View.GONE);
            providerSubscribedMining.setVisibility(subscribedMining ? View.VISIBLE : View.GONE);
        }

        String getProvider() {
            return provider;
        }

        void setEmptyView() {
            set(SpaceAndroidUtils.getSpaceHostname(), false, false, false);
        }
    }
}
