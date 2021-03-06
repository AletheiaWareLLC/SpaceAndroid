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
import android.os.Bundle;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.aletheiaware.space.android.R;
import com.aletheiaware.space.utils.SpaceUtils;

import androidx.annotation.NonNull;

public class EditTextFragment extends TextContentFragment {

    private TextWatcher watcher;
    private EditText contentEditText;

    public EditTextFragment() {
        super();
    }

    public void setup(String text, TextWatcher watcher) {
        super.setup(text);
        this.watcher = watcher;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        contentEditText = (EditText) inflater.inflate(R.layout.fragment_edit_text, container, false);
        if (watcher != null) {
            contentEditText.addTextChangedListener(watcher);
        }
        contentEditText.setText(getText());
        return contentEditText;
    }

    @Override
    public String getText() {
        return contentEditText.getText().toString();
    }

    @Override
    public String getName(Activity parent) {
        // Generate name
        return parent.getString(R.string.default_document_name, System.currentTimeMillis());
    }

    @Override
    public String getType(Activity parent) {
        return SpaceUtils.TEXT_PLAIN_TYPE;
    }

    @Override
    public long getSize(Activity parent) {
        return getText().length();
    }
}
