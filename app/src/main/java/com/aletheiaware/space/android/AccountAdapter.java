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

import com.aletheiaware.bc.BCProto;
import com.google.protobuf.ByteString;

import java.util.List;

public abstract class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {

    private final Activity activity;
    private final LayoutInflater inflater;
    private final List<String> accounts;

    AccountAdapter(Activity activity, List<String> accounts) {
        this.activity = activity;
        this.inflater = activity.getLayoutInflater();
        this.accounts = accounts;
    }

    public void addAlias(String alias) {
        accounts.add(alias);
    }

    public void removeAlias(String alias) {
        accounts.remove(alias);
    }

    public abstract void unlockAccount(final String alias);

    public abstract void deleteAccount(final String alias);

    @NonNull
    @Override
    public AccountAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final TextView view = (TextView) inflater.inflate(R.layout.account_list_item, parent, false);
        final AccountAdapter.ViewHolder holder = new AccountAdapter.ViewHolder(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unlockAccount(holder.getAlias());
            }
        });
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                deleteAccount(holder.getAlias());
                return true;
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull AccountAdapter.ViewHolder holder, int position) {
        holder.setAlias(accounts.get(position));
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView view;
        private String alias;

        ViewHolder(TextView view) {
            super(view);
            this.view = view;
        }

        public void setAlias(String alias) {
            this.alias = alias;
            view.setText(alias);
        }

        public String getAlias() {
            return alias;
        }
    }
}
