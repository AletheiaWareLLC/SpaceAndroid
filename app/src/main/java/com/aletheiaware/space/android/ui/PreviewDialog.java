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

package com.aletheiaware.space.android.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.View;
import android.widget.TextView;

import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.AliasAdapter;
import com.aletheiaware.space.android.R;

import androidx.appcompat.app.AlertDialog;

public abstract class PreviewDialog {

    private final Activity activity;
    private final AliasAdapter adapter;
    private final byte[] metaRecordHash;
    private final Meta meta;
    private final boolean shared;
    private AlertDialog dialog;

    public PreviewDialog(Activity activity, Cache cache, Network network, byte[] metaRecordHash, Meta meta, boolean shared) {
        this.activity = activity;
        adapter = new AliasAdapter(activity, cache, network);
        this.metaRecordHash = metaRecordHash;
        this.meta = meta;
        this.shared = shared;
    }

    public void create() {
        View previewView = View.inflate(activity, R.layout.dialog_preview, null);
        // Name TextView
        final TextView nameText = previewView.findViewById(R.id.preview_name);
        nameText.setText(meta.getName());
        // TODO add spinner to select preview size
        // TODO add option to share preview
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.title_dialog_preview);
        ab.setView(previewView);
        ab.setPositiveButton(R.string.preview_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                onPreview();
            }
        });
        dialog = ab.show();
    }

    protected abstract void onPreview();

    public AlertDialog getDialog() {
        return dialog;
    }
}
