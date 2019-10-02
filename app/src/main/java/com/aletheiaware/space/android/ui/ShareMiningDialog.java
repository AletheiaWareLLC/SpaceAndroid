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
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import com.aletheiaware.alias.AliasProto.Alias;
import com.aletheiaware.space.SpaceProto.Meta;
import com.aletheiaware.space.android.AliasArrayAdapter;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.utils.SpaceUtils;

import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;

public abstract class ShareMiningDialog {

    private final Activity activity;
    private final Meta meta;
    private final AliasArrayAdapter aliasArrayAdapter;
    private AlertDialog dialog;
    private AutoCompleteTextView aliasText;

    public ShareMiningDialog(Activity activity, Meta meta, AliasArrayAdapter aliasArrayAdapter) {
        this.activity = activity;
        this.meta = meta;
        this.aliasArrayAdapter = aliasArrayAdapter;
    }

    public AlertDialog getDialog() {
        return dialog;
    }

    public void create() {
        View shareView = View.inflate(activity, R.layout.dialog_share, null);
        // Name TextView
        TextView nameText = shareView.findViewById(R.id.share_name);
        nameText.setText(meta.getName());
        // Alias EditText
        aliasText = shareView.findViewById(R.id.share_alias);
        aliasText.setAdapter(aliasArrayAdapter);
        aliasText.setThreshold(3);
        aliasText.setFocusable(true);
        aliasText.setFocusableInTouchMode(true);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.title_dialog_share);
        ab.setIcon(R.drawable.share);
        ab.setView(shareView);
        ab.setPositiveButton(R.string.share_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                final String recipient = aliasText.getText().toString();
                Log.d(SpaceUtils.TAG, "Recipient: " + recipient);
                if (recipient.isEmpty()) {
                    // FIXME show error
                } else {
                    final Alias r = aliasArrayAdapter.get(recipient);
                    if (r == null) {
                        // FIXME show error
                    } else {
                        onShare(r);
                    }
                }
            }
        });
        dialog = ab.show();
    }

    @UiThread
    public abstract void onShare(Alias recipient);
}
