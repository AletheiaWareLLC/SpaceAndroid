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
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ListView;

import com.aletheiaware.space.SpaceProto.Registrar;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.RegistrarArrayAdapter;
import com.aletheiaware.space.utils.SpaceUtils;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;

public abstract class RegistrarSelectionDialog {

    private final Activity activity;
    private final RegistrarArrayAdapter registrarArrayAdapter;
    private AlertDialog dialog;
    private ListView registrarsList;

    public RegistrarSelectionDialog(Activity activity, RegistrarArrayAdapter registrarArrayAdapter) {
        this.activity = activity;
        this.registrarArrayAdapter = registrarArrayAdapter;
    }

    public AlertDialog getDialog() {
        return dialog;
    }

    public void create() {
        View view = View.inflate(activity, R.layout.dialog_registrar_selection, null);
        registrarsList = view.findViewById(R.id.registrars_list);
        registrarsList.setAdapter(registrarArrayAdapter);
        registrarsList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        AlertDialog.Builder ab = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        ab.setTitle(R.string.title_dialog_registrars_selection);
        ab.setIcon(R.drawable.bc_storage);
        ab.setView(view);
        ab.setPositiveButton(R.string.registrars_selection_action, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                final SparseBooleanArray selections = registrarsList.getCheckedItemPositions();
                final Map<String, Registrar> registrars = new HashMap<>();
                for (int i = 0; i < selections.size(); i++) {
                    final String registrarAlias = registrarArrayAdapter.getItem(selections.keyAt(i));
                    Log.d(SpaceUtils.TAG, "Selected Registrar: " + registrarAlias);
                    if (registrarAlias == null) {
                        // FIXME show error
                    } else {
                        final Registrar registrar = registrarArrayAdapter.get(registrarAlias);
                        if (registrar == null) {
                            // FIXME show error
                        } else {
                            registrars.put(registrarAlias, registrar);
                        }
                    }
                }
                onSelect(registrars);
            }
        });
        ab.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onCancel();
            }
        });
        ab.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                onCancel();
            }
        });
        dialog = ab.show();
    }

    @UiThread
    public abstract void onSelect(Map<String, Registrar> registrars);

    @UiThread
    public abstract void onCancel();
}
