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

import com.aletheiaware.space.SpaceProto.Preview;

import java.io.InputStream;

import androidx.fragment.app.Fragment;

public abstract class ContentFragment extends Fragment {

    public abstract String getName(Activity parent);

    public abstract String getType(Activity parent);

    public abstract long getSize(Activity parent);

    public abstract InputStream getInputStream(Activity parent);

    public abstract Preview getPreview(Activity parent);

}
