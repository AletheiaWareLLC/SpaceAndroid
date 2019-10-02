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
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRenderer.Page;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

public class PdfViewFragment extends UriContentFragment {

    private static final String STATE_CURRENT_PAGE_INDEX = "current_page_index";

    private ImageView imageView;
    private Button previousButton;
    private TextView pageCountText;
    private Button nextButton;

    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private Page currentPage;
    private int pageIndex;

    public PdfViewFragment() {
        super();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i(SpaceUtils.TAG, "On Create View");
        return inflater.inflate(R.layout.fragment_pdf_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.i(SpaceUtils.TAG, "On View Created");
        imageView = view.findViewById(R.id.pdf_image);
        previousButton = view.findViewById(R.id.pdf_previous);
        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPage(currentPage.getIndex() - 1);
            }
        });
        pageCountText = view.findViewById(R.id.pdf_page_count);
        nextButton = view.findViewById(R.id.pdf_next);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPage(currentPage.getIndex() + 1);
            }
        });

        pageIndex = 0;
        if (null != savedInstanceState) {
            pageIndex = savedInstanceState.getInt(STATE_CURRENT_PAGE_INDEX, 0);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (null != currentPage) {
            outState.putInt(STATE_CURRENT_PAGE_INDEX, currentPage.getIndex());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            FragmentActivity parent = getActivity();
            if (parent != null) {
                descriptor = parent.getContentResolver().openFileDescriptor(uri, "r");
                if (descriptor != null) {
                    renderer = new PdfRenderer(descriptor);
                    showPage(pageIndex);
                }
            }
        } catch (IOException e) {
            /* Ignored */
            e.printStackTrace();
        }
    }

    @Override
    public void onStop() {
        try {
            if (currentPage != null) {
                currentPage.close();
            }
            renderer.close();
            descriptor.close();
        } catch (IOException e) {
            /* Ignored */
            e.printStackTrace();
        }
        super.onStop();
    }

    private void showPage(int index) {
        int count = renderer.getPageCount();
        if (index >= count) {
            return;
        }
        if (currentPage != null) {
            currentPage.close();
        }
        currentPage = renderer.openPage(index);
        Bitmap bitmap = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
        currentPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
        imageView.setImageBitmap(bitmap);
        boolean hasPrevious = index != 0;
        previousButton.setEnabled(hasPrevious);
        previousButton.setVisibility(hasPrevious ? View.VISIBLE : View.INVISIBLE);
        boolean hasNext = index + 1 < count;
        nextButton.setEnabled(hasNext);
        nextButton.setVisibility(hasPrevious ? View.VISIBLE : View.INVISIBLE);
        pageCountText.setText(getString(R.string.pdf_page_count, index + 1, count));
    }

    @Override
    public String getType(Activity parent) {
        if (type == null) {
            type = SpaceUtils.PDF_TYPE;
        }
        return type;
    }

    @Override
    public Preview getPreview(Activity parent) {
        if (bitmap == null && descriptor != null) {
            try {
                Page page = new PdfRenderer(descriptor).openPage(0);
                bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
            } catch (IOException e) {
                /* Ignored */
                e.printStackTrace();
            }
        }
        return super.getPreview(parent);
    }
}
