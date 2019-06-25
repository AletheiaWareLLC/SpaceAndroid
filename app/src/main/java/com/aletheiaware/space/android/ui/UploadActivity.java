/*
 * Copyright 2018 Aletheia Ware LLC
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
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.UiThread;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.aletheiaware.bc.Cache;
import com.aletheiaware.bc.Network;
import com.aletheiaware.bc.android.ui.AccessActivity;
import com.aletheiaware.bc.android.utils.BCAndroidUtils;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.finance.FinanceProto.Registration;
import com.aletheiaware.finance.FinanceProto.Subscription;
import com.aletheiaware.finance.utils.FinanceUtils;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.R;
import com.aletheiaware.space.android.utils.MinerUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils.ProviderCallback;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils.RegistrationCallback;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils.SubscriptionCallback;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class UploadActivity extends AppCompatActivity {

    private EditText nameEditText;
    private TextView typeTextView;
    private TextView sizeTextView;
    private FrameLayout contentFrame;
    private ContentFragment contentFragment;

    private Button uploadButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SpaceAndroidUtils.createNotificationChannels(this);

        // TODO add menu items
        // TODO - Preview - choose which preview sizes to generate and mine after meta. Consider adding default preview sizes to settings
        // TODO - Tag - choose which tags to apply and mine after meta.

        // Setup UI
        setContentView(R.layout.activity_upload);

        // Name EditText
        nameEditText = findViewById(R.id.upload_name);

        // Type TextView
        typeTextView = findViewById(R.id.upload_type);

        // Size TextView
        sizeTextView = findViewById(R.id.upload_size);

        // Content Frame
        contentFrame = findViewById(R.id.upload_content_frame);

        // TODO Add access spinner - choose from private, public, or a list of recipients with whom to grant access and mine after meta.

        // Upload Button
        uploadButton = findViewById(R.id.upload_button);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setEditable(false);
                String alias = BCAndroidUtils.getAlias();
                KeyPair keys = BCAndroidUtils.getKeyPair();
                Cache cache = BCAndroidUtils.getCache();
                String name = nameEditText.getText().toString();
                String type = typeTextView.getText().toString();
                InputStream in = contentFragment.getInputStream(UploadActivity.this);
                Preview preview = contentFragment.getPreview(UploadActivity.this);
                String provider = SpaceAndroidUtils.getRemoteMinerPreference(UploadActivity.this, alias);
                if (provider == null || provider.isEmpty()) {
                    showProviderPicker(alias, keys, cache, name, type, preview, in);
                } else {
                    upload(alias, keys, cache, provider, name, type, preview, in);
                }
            }
        });
        uploadButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                setEditable(false);
                String alias = BCAndroidUtils.getAlias();
                KeyPair keys = BCAndroidUtils.getKeyPair();
                Cache cache = BCAndroidUtils.getCache();
                String name = nameEditText.getText().toString();
                String type = typeTextView.getText().toString();
                InputStream in = contentFragment.getInputStream(UploadActivity.this);
                Preview preview = contentFragment.getPreview(UploadActivity.this);
                showProviderPicker(alias, keys, cache, name, type, preview, in);
                return true;
            }
        });
    }

    private void upload(final String alias, final KeyPair keys, final Cache cache, final String provider, final String name, final String type, final Preview preview, final InputStream in) {
        new Thread() {
            @Override
            public void run() {
                try {
                    final Network network = SpaceAndroidUtils.getStorageNetwork(UploadActivity.this, alias);
                    final Registration registration = FinanceUtils.getRegistration(cache, network, provider, null, alias, keys);
                    Log.d(SpaceUtils.TAG, "Registration: " + registration);
                    final Subscription subscriptionStorage = FinanceUtils.getSubscription(cache, network, provider, null, alias, keys, getString(R.string.stripe_subscription_storage_product), getString(R.string.stripe_subscription_storage_plan));
                    Log.d(SpaceUtils.TAG, "Storage Subscription: " + subscriptionStorage);
                    final Subscription subscriptionMining = FinanceUtils.getSubscription(cache, network, provider, null, alias, keys, getString(R.string.stripe_subscription_mining_product), getString(R.string.stripe_subscription_mining_plan));
                    Log.d(SpaceUtils.TAG, "Mining Subscription: " + subscriptionMining);
                    if (registration == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                SpaceAndroidUtils.registerSpaceCustomer(UploadActivity.this, provider, alias, new RegistrationCallback() {
                                    @Override
                                    public void onRegistered(final String customerId) {
                                        Log.d(SpaceUtils.TAG, "Space Customer ID: " + customerId);
                                        upload(alias, keys, cache, provider, name, type, preview, in);
                                    }
                                });
                            }
                        });
                    } else if (subscriptionStorage == null) {
                        SpaceAndroidUtils.subscribeSpaceStorageCustomer(UploadActivity.this, provider, alias, registration.getCustomerId(), new SubscriptionCallback() {
                            @Override
                            public void onSubscribed(String subscriptionId) {
                                Log.d(SpaceUtils.TAG, "Space Storage Subscription ID: " + subscriptionId);
                                upload(alias, keys, cache, provider, name, type, preview, in);
                            }
                        });
                    } else if (subscriptionMining == null) {
                        SpaceAndroidUtils.subscribeSpaceMiningCustomer(UploadActivity.this, provider, alias, registration.getCustomerId(), new SubscriptionCallback() {
                            @Override
                            public void onSubscribed(String subscriptionId) {
                                Log.d(SpaceUtils.TAG, "Space Mining Subscription ID: " + subscriptionId);
                                upload(alias, keys, cache, provider, name, type, preview, in);
                            }
                        });
                    } else {
                        MinerUtils.mineFile(UploadActivity.this, "https://" + provider, name, type, preview, in);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setResult(Activity.RESULT_OK);
                                finish();
                            }
                        });
                    }
                } catch (IOException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException | BadPaddingException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    @UiThread
    private void showProviderPicker(final String alias, final KeyPair keys, final Cache cache, final String name, final String type, final Preview preview, final InputStream in) {
        SpaceAndroidUtils.showProviderPicker(UploadActivity.this, alias, new ProviderCallback() {
            @Override
            public void onProviderSelected(String provider) {
                upload(alias, keys, cache, provider, name, type, preview, in);
            }

            @Override
            public void onCancelSelection() {
                setEditable(true);
            }
        });
    }

    private void setEditable(boolean editable) {
        uploadButton.setVisibility(editable ? View.VISIBLE : View.GONE);
        uploadButton.setEnabled(editable);
        nameEditText.setEnabled(editable);
        typeTextView.setEnabled(editable);
        sizeTextView.setEnabled(editable);
        contentFrame.setEnabled(editable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (BCAndroidUtils.isInitialized()) {
            Intent intent = getIntent();
            if (intent != null) {
                String action = intent.getAction();
                if (action == null) {
                    action = "";
                }
                Log.d(SpaceUtils.TAG, "Action:" + action);
                Uri uri = intent.getData();
                if (uri == null) {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                }
                Log.d(SpaceUtils.TAG, "URI:" + uri);
                String type = intent.getType();
                Log.d(SpaceUtils.TAG, "Type:" + type);
                if (type == null && uri != null) {
                    type = getContentResolver().getType(uri);
                    Log.i(SpaceUtils.TAG, "Type: " + type);
                    if (type == null) {
                        type = SpaceUtils.getTypeByExtension(uri.toString());
                        Log.i(SpaceUtils.TAG, "Type: " + type);
                    }
                }
                if (type == null) {
                    type = SpaceUtils.UNKNOWN_TYPE;
                } else if (type.equals("image/*")) {
                    type = SpaceUtils.DEFAULT_IMAGE_TYPE;
                } else if (type.equals("video/*")) {
                    type = SpaceUtils.DEFAULT_VIDEO_TYPE;
                }
                typeTextView.setText(type);

                if (SpaceUtils.isText(type)) {
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    EditTextFragment fragment = new EditTextFragment();
                    fragment.setup(text, new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            updateSize(s.length() * 2);// 16bit char == 2 * 8bit byte
                        }
                    });
                    setContentFragment(fragment);
                } else if (SpaceUtils.isImage(type)) {
                    ImageViewFragment fragment = new ImageViewFragment();
                    fragment.setup(uri);
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        fragment.bitmap = (Bitmap) extras.get(SpaceAndroidUtils.DATA_EXTRA);
                    }
                    setContentFragment(fragment);
                } else if (SpaceUtils.isVideo(type)) {
                    VideoViewFragment fragment = new VideoViewFragment();
                    fragment.setup(uri);
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        fragment.bitmap = (Bitmap) extras.get(SpaceAndroidUtils.DATA_EXTRA);
                    }
                    setContentFragment(fragment);
                } else {
                    BCAndroidUtils.showErrorDialog(this, getString(R.string.error_unsupported_type, type));
                    return;
                }
                uploadButton.setVisibility(View.VISIBLE);
            }
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    private void setContentFragment(ContentFragment fragment) {
        contentFragment = fragment;
        nameEditText.setText(contentFragment.getName(this));
        typeTextView.setText(contentFragment.getType(this));
        updateSize(contentFragment.getSize(this));
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.upload_content_frame, fragment);
        ft.commit();
    }

    public void updateSize(long size) {
        sizeTextView.setText(BCUtils.sizeToString(size));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SpaceAndroidUtils.ACCESS_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        break;
                    case RESULT_CANCELED:
                        setResult(RESULT_CANCELED);
                        finish();
                        break;
                    default:
                        break;
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, intent);
                break;
        }
    }
}
