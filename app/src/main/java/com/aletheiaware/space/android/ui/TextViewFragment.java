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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.aletheiaware.bc.android.utils.CopyToClipboardListener;
import com.aletheiaware.space.android.R;

public class TextViewFragment extends TextContentFragment {

    private TextView contentTextView;
    private String name;
    private String type;
    private long size;

    public TextViewFragment() {
        super();
    }

    public void setup(String name, String type, long size, String text) {
        this.name = name;
        this.type = type;
        this.size = size;
        super.setup(text);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_text_view, container, false);
        contentTextView = view.findViewById(R.id.fragment_text_view);
        contentTextView.setOnClickListener(new CopyToClipboardListener(contentTextView, getName()));
        contentTextView.setText(getText());
        return view;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public long getSize() {
        return size;
    }
}
