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
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
                onClickProviderRegistration(provider, registrationIds.get(provider));
            }
        });
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String provider = holder.getProvider();
                onClickProviderRemove(provider);
                return true;
            }
        });
        final AppCompatImageButton subscriptionStorageButton = view.findViewById(R.id.provider_subscription_storage_button);
        subscriptionStorageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String provider = holder.getProvider();
                onClickProviderStorage(provider, registrationIds.get(provider), subscriptionStorageIds.get(provider));
            }
        });
        final AppCompatImageButton subscriptionMiningButton = view.findViewById(R.id.provider_subscription_mining_button);
        subscriptionMiningButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String provider = holder.getProvider();
                onClickProviderMining(provider, registrationIds.get(provider), subscriptionMiningIds.get(provider));
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
            holder.set(provider, registrationIds.get(provider), subscriptionStorageIds.get(provider), subscriptionMiningIds.get(provider));
        }
    }

    @Override
    public int getItemCount() {
        if (sorted.isEmpty()) {
            return 1;// For empty view
        }
        return sorted.size();
    }

    public abstract void onClickProviderRemove(String provider);

    public abstract void onClickProviderRegistration(String provider, String registrationId);

    public abstract void onClickProviderStorage(String provider, String registrationId, String subscriptionId);

    public abstract void onClickProviderMining(String provider, String registrationId, String subscriptionId);

    public boolean isEmpty() {
        return sorted.isEmpty();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private String provider;

        // TODO private ImageView providerImage;
        private TextView providerNameText;
        private TextView registrationIdText;
        private TextView subscriptionStorageIdText;
        private TextView subscriptionMiningIdText;
        private AppCompatImageButton subscriptionStorageButton;
        private AppCompatImageButton subscriptionMiningButton;

        ViewHolder(LinearLayout view) {
            super(view);
            providerNameText = view.findViewById(R.id.provider_name);
            registrationIdText = view.findViewById(R.id.provider_registration_id);
            subscriptionStorageIdText = view.findViewById(R.id.provider_subscription_storage_id);
            subscriptionMiningIdText = view.findViewById(R.id.provider_subscription_mining_id);
            subscriptionStorageButton = view.findViewById(R.id.provider_subscription_storage_button);
            subscriptionMiningButton = view.findViewById(R.id.provider_subscription_mining_button);
        }

        void set(String provider, String registrationId, String subscriptionStorageId, String subscriptionMiningId) {
            Log.d(SpaceUtils.TAG, "Setting " + provider + " " + registrationId + " " + subscriptionStorageId + " " + subscriptionMiningId);
            this.provider = provider;
            providerNameText.setVisibility(View.VISIBLE);
            providerNameText.setText(provider);
            if (registrationId == null || registrationId.isEmpty()) {
                registrationIdText.setVisibility(View.GONE);
                subscriptionStorageButton.setVisibility(View.GONE);
                subscriptionMiningButton.setVisibility(View.GONE);
            } else {
                registrationIdText.setVisibility(View.VISIBLE);
                registrationIdText.setText(registrationId);
                subscriptionStorageButton.setVisibility(View.VISIBLE);
                subscriptionMiningButton.setVisibility(View.VISIBLE);
            }
            if (subscriptionStorageId == null || subscriptionStorageId.isEmpty()) {
                subscriptionStorageIdText.setVisibility(View.GONE);
                subscriptionStorageButton.setBackgroundResource(R.color.primary);
            } else {
                subscriptionStorageIdText.setVisibility(View.VISIBLE);
                subscriptionStorageIdText.setText(subscriptionStorageId);
                subscriptionStorageButton.setBackgroundResource(R.color.accent);
            }
            if (subscriptionMiningId == null || subscriptionMiningId.isEmpty()) {
                subscriptionMiningIdText.setVisibility(View.GONE);
                subscriptionMiningButton.setBackgroundResource(R.color.primary);
            } else {
                subscriptionMiningIdText.setVisibility(View.VISIBLE);
                subscriptionMiningIdText.setText(subscriptionMiningId);
                subscriptionMiningButton.setBackgroundResource(R.color.accent);
            }
        }

        String getProvider() {
            return provider;
        }

        void setEmptyView() {
            set(SpaceAndroidUtils.getSpaceHostname(), null, null, null);
        }
    }
}
