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
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.TagAdapter;
import com.aletheiaware.space.utils.SpaceUtils;

import java.security.KeyPair;

public abstract class TagDialog {

    private final Activity activity;
    private final TagAdapter adapter;
    private final byte[] metaRecordHash;
    private final Meta meta;
    private final boolean shared;
    private AlertDialog dialog;

    public TagDialog(Activity activity, String alias, KeyPair keys, byte[] metaRecordHash, Meta meta, boolean shared) {
        this.activity = activity;
        adapter = new TagAdapter(activity, alias, keys, metaRecordHash, shared);
        this.metaRecordHash = metaRecordHash;
        this.meta = meta;
        this.shared = shared;
    }

    public void create() {
        View tagView = View.inflate(activity, R.layout.dialog_tag, null);
        // Name TextView
        final TextView nameText = tagView.findViewById(R.id.tag_name);
        nameText.setText(meta.getName());
        // Value EditText
        final AutoCompleteTextView valueText = tagView.findViewById(R.id.tag_value);
        valueText.setAdapter(adapter);
        valueText.setThreshold(3);
        valueText.setFocusable(true);
        valueText.setFocusableInTouchMode(true);
        // Reason TextView
        final EditText reasonText = tagView.findViewById(R.id.tag_reason);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.title_dialog_tag);
        ab.setView(tagView);
        ab.setPositiveButton(R.string.tag_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                final String value = valueText.getText().toString();
                Log.d(SpaceUtils.TAG, "Value: " + value);
                if (!value.isEmpty()) {
                    onTag(dialog, value, reasonText.getText().toString());
                }
            }
        });
        dialog = ab.show();
    }

    public abstract void onTag(DialogInterface dialog, String value, String reason);

    public AlertDialog getDialog() {
        return dialog;
    }
}
