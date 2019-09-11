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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.aletheiaware.space.SpaceProto.Registrar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RegistrarsAdapter extends RecyclerView.Adapter<RegistrarsAdapter.ViewHolder> {

    private final Activity activity;
    private final LayoutInflater inflater;
    private final Map<String, Registrar> registrars = new HashMap<>();
    private final Set<String> selections = new HashSet<>();
    private final List<String> sorted = new ArrayList<>();

    public RegistrarsAdapter(Activity activity) {
        this.activity = activity;
        inflater = activity.getLayoutInflater();
    }

    public void setRegistrars(Map<String, Registrar> registrars, Set<String> preferences) {
        this.registrars.putAll(registrars);
        sorted.addAll(registrars.keySet());
        selections.addAll(preferences);
        sort();
    }

    private synchronized void sort() {
        Collections.sort(sorted, new Comparator<String>() {
            @Override
            public int compare(String m1, String m2) {
                boolean s1 = selections.contains(m1);
                boolean s2 = selections.contains(m2);
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

    public Map<String, Registrar> getSelectedRegistrars() {
        Map<String, Registrar> ss = new HashMap<>();
        for (String alias : selections) {
            Registrar r = registrars.get(alias);
            if (r != null) {
                ss.put(alias, r);
            }
        }
        return ss;
    }

    @NonNull
    @Override
    public RegistrarsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final CheckBox view = (CheckBox) inflater.inflate(R.layout.registrar_list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view);
        view.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                String alias = holder.getAlias();
                if (alias != null) {
                    if (isChecked) {
                        selections.add(alias);
                    } else {
                        selections.remove(alias);
                    }
                }
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RegistrarsAdapter.ViewHolder holder, int position) {
        if (sorted.isEmpty()) {
            holder.setEmptyView();
        } else {
            final String alias = sorted.get(position);
            holder.set(alias, selections.contains(alias));
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

        private final CheckBox itemCheck;

        ViewHolder(@NonNull View view) {
            super(view);
            itemCheck = (CheckBox) view;
        }

        public String getAlias() {
            return alias;
        }

        void set(String alias, boolean selected) {
            this.alias = alias;
            itemCheck.setText(alias);
            itemCheck.setChecked(selected);
        }

        void setEmptyView() {
            alias = null;
            itemCheck.setText(R.string.empty_registrars_list);
            itemCheck.setChecked(false);
        }
    }
}
