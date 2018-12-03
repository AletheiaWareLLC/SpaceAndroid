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

package com.aletheiaware.space.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.aletheiaware.bc.BC.Reference;
import com.aletheiaware.space.Space.Meta;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class MainActivity extends AppCompatActivity {

    private DatabaseAdapter adapter;

    private RecyclerView recyclerView;
    private FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Toolbar
        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        //account
        //refresh

        // Appbar
        AppBarLayout appbar = findViewById(R.id.main_appbar);
        appbar.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                // TODO
            }
        });

        // RecyclerView
        recyclerView = findViewById(R.id.main_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // FloatingActionButton
        fab = findViewById(R.id.main_fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                upload();
            }
        });

        // Adapter
        adapter = new DatabaseAdapter(this) {
            @Override
            public void onFileSelected(long timestamp, Meta meta, Reference file, Reference preview) {
                Intent i = new Intent(MainActivity.this, DetailActivity.class);
                i.putExtra(SpaceAndroidUtils.TIMESTAMP_EXTRA, timestamp);
                i.putExtra(SpaceAndroidUtils.META_EXTRA, meta.toByteArray());
                i.putExtra(SpaceAndroidUtils.FILE_REFERENCE_EXTRA, file.toByteArray());
                if (preview != null) {
                    i.putExtra(SpaceAndroidUtils.PREVIEW_REFERENCE_EXTRA, preview.toByteArray());
                }
                startActivityForResult(i, SpaceAndroidUtils.DETAIL_ACTIVITY);
            }
        };
        // TODO visually show files that are still getting mined
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            refresh();
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
            case SpaceAndroidUtils.ACCOUNT_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        break;
                    case RESULT_CANCELED:
                        // TODO
                        break;
                    default:
                        break;
                }
                break;
            case SpaceAndroidUtils.DETAIL_ACTIVITY:
                break;
            case SpaceAndroidUtils.OPEN_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        if (data != null) {
                            Log.i(SpaceUtils.TAG, "Intent: " + data);
                            Uri uri = data.getData();
                            Log.i(SpaceUtils.TAG, "Uri: " + uri);
                            String type = data.getType();
                            Log.i(SpaceUtils.TAG, "Type: " + type);
                            Intent i = new Intent(this, UploadActivity.class);
                            i.setAction(Intent.ACTION_SEND);
                            i.setDataAndType(uri, type);
                            startActivityForResult(i, SpaceAndroidUtils.UPLOAD_ACTIVITY);
                        }
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
                break;
            case SpaceAndroidUtils.PAYMENT_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        final String email = data.getStringExtra(SpaceAndroidUtils.EMAIL_EXTRA);
                        Log.d(SpaceUtils.TAG, "Email: " + email);
                        final String paymentId = data.getStringExtra(SpaceAndroidUtils.STRIPE_TOKEN_EXTRA);
                        Log.d(SpaceUtils.TAG, "PaymentId: " + paymentId);
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    InetAddress address = InetAddress.getByName("space.aletheiaware.com");
                                    Log.d(SpaceUtils.TAG, "Address: " + address);
                                    KeyPair keys = SpaceAndroidUtils.getKeys();
                                    SpaceAndroidUtils.register(keys, email, paymentId);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
            case SpaceAndroidUtils.UPLOAD_ACTIVITY:
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                refresh();
                return true;
            case R.id.menu_account:
                account();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void account() {
        // TODO
    }

    private void refresh() {
        new Thread() {
            @Override
            public void run() {
                try {
                    InetAddress address = InetAddress.getByName("space.aletheiaware.com");
                    Log.d(SpaceUtils.TAG, "Address: " + address);
                    KeyPair keys = SpaceAndroidUtils.getKeys();
                    SpaceAndroidUtils.getFileList(address, keys, adapter);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (NoSuchPaddingException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                } catch (InvalidAlgorithmParameterException e) {
                    e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public void upload() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, SpaceAndroidUtils.OPEN_ACTIVITY);
    }
}
