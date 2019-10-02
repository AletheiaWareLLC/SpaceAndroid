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
import android.widget.TextView;

import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.space.SpaceProto.Registrar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public abstract class RegistrarRecyclerAdapter extends RecyclerView.Adapter<RegistrarRecyclerAdapter.ViewHolder> {

    private final Activity activity;
    private final LayoutInflater inflater;
    private final Map<String, Registrar> registrars = new HashMap<>();
    private final Map<String, Registration> registrations = new HashMap<>();
    private final Map<String, Subscription> subscriptions = new HashMap<>();
    private final List<String> sorted = new ArrayList<>();

    public RegistrarRecyclerAdapter(Activity activity) {
        this.activity = activity;
        inflater = activity.getLayoutInflater();
    }

    public void addRegistrar(Registrar registrar, Registration registration, Subscription subscription) {
        String key = registrar.getMerchant().getAlias();
        if (!registrars.containsKey(key)) {
            registrars.put(key, registrar);
            registrations.put(key, registration);
            subscriptions.put(key, subscription);
            sorted.add(key);
            sort();
        }
    }

    private synchronized void sort() {
        Collections.sort(sorted, new Comparator<String>() {
            @Override
            public int compare(String m1, String m2) {
                boolean r1 = registrations.containsKey(m1);
                boolean r2 = registrations.containsKey(m2);
                if (r1 && !r2) {
                    // Sort m1 before m2
                    return -1;
                } else if (r2 && !r1) {
                    // Sort m2 before m1
                    return 1;
                }
                boolean s1 = subscriptions.containsKey(m1);
                boolean s2 = subscriptions.containsKey(m2);
                if (s1 && !s2) {
                    // Sort m1 before m2
                    return -1;
                } else if (s2 && !s1) {
                    // Sort m2 before m1
                    return 1;
                }
                // Sort alphabetically
                return m1.compareTo(m2);
            }
        });
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    @NonNull
    @Override
    public RegistrarRecyclerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final TextView view = (TextView) inflater.inflate(R.layout.registrar_list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String alias = holder.getAlias();
                if (alias != null) {
                    Registrar registrar = registrars.get(alias);
                    if (registrar != null) {
                        onRegistrarSelected(registrar, registrations.get(alias), subscriptions.get(alias));
                    }
                }
            }
        });
        return holder;
    }

    public abstract void onRegistrarSelected(Registrar registrar, Registration registration, Subscription subscription);

    @Override
    public void onBindViewHolder(@NonNull RegistrarRecyclerAdapter.ViewHolder holder, int position) {
        if (sorted.isEmpty()) {
            holder.setEmptyView();
        } else {
            String alias = sorted.get(position);
            if (alias != null) {
                Registrar registrar = registrars.get(alias);
                if (registrar != null) {
                    holder.set(registrar);
                }
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

    static class ViewHolder extends RecyclerView.ViewHolder {

        private String alias;

        private final TextView itemText;

        ViewHolder(@NonNull View view) {
            super(view);
            itemText = (TextView) view;
        }

        public String getAlias() {
            return alias;
        }

        void set(Registrar registrar) {
            alias = registrar.getMerchant().getAlias();
            itemText.setText(alias);
        }

        void setEmptyView() {
            alias = null;
            itemText.setText(R.string.empty_registrars_list);
        }
    }
}
