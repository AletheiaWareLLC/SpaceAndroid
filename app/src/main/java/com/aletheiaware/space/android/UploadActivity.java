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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.aletheiaware.space.Space.StorageRequest;
import com.aletheiaware.space.Space.StorageResponse;
import com.aletheiaware.space.utils.SpaceUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class UploadActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "Upload Channel";
    private static final int UPLOAD_NOTIFICATION_ID = 1;
    private static final int UPLOAD_NOTIFICATION_TIMEOUT = 2 * 60 * 1000;// 2 minutes

    private Toolbar toolbar;
    private EditText nameEditText;
    private TextView typeTextView;
    private TextView sizeTextView;
    private TextView previewTextView;
    private ImageView previewImageView;
    private FloatingActionButton fab;

    private byte[] data;
    private byte[] preview;
    private String type;
    private int size;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel(this);

        // Setup UI
        setContentView(R.layout.activity_upload);

        // Toolbar
        toolbar = findViewById(R.id.upload_toolbar);
        setSupportActionBar(toolbar);

        // Name EditText
        nameEditText = findViewById(R.id.upload_name);

        // Type TextView
        typeTextView = findViewById(R.id.upload_type);

        // Size TextView
        sizeTextView = findViewById(R.id.upload_size);

        // Preview TextView
        previewTextView = findViewById(R.id.upload_text_view);

        // Preview ImageView
        previewImageView = findViewById(R.id.upload_image_view);

        // FloatingActionButton
        fab = findViewById(R.id.upload_fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fab.setVisibility(View.INVISIBLE);
                final String name = nameEditText.getText().toString();
                // Show notification
                NotificationCompat.Builder builder = createUploadNotification(UploadActivity.this, name);
                final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(UploadActivity.this);
                notificationManager.notify(UPLOAD_NOTIFICATION_ID, builder.build());
                // Start thread to perform upload
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            final InetAddress address = InetAddress.getByName("space.aletheiaware.com");
                            Log.d(SpaceUtils.TAG, "Address: " + address);
                            final KeyPair keys = SpaceAndroidUtils.getKeys();
                            String customerId = SpaceAndroidUtils.getCustomerId(address, keys);
                            if (customerId != null) {
                                upload(address, keys, customerId, null, name);
                            } else {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Intent intent = new Intent(UploadActivity.this, StripeActivity.class);
                                        startActivityForResult(intent, SpaceAndroidUtils.PAYMENT_ACTIVITY);
                                    }
                                });
                            }
                        } catch (SocketException | SocketTimeoutException e) {
                            SpaceAndroidUtils.showErrorDialog(UploadActivity.this, R.string.error_connection, e);
                        } catch (BadPaddingException | IOException | IllegalBlockSizeException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | SignatureException e) {
                            SpaceAndroidUtils.showErrorDialog(UploadActivity.this, R.string.error_uploading, e);
                        } finally {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    fab.setVisibility(View.VISIBLE);
                                    notificationManager.cancel(UPLOAD_NOTIFICATION_ID);
                                }
                            });
                        }
                    }
                }.start();
            }
        });
        fab.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            fab.setVisibility(View.VISIBLE);

            Intent intent = getIntent();
            if (intent != null) {
                String action = intent.getAction();
                if (action == null) {
                    action = "";
                }
                Log.d(SpaceUtils.TAG, "Action " + action);
                Uri data = intent.getData();
                Log.d(SpaceUtils.TAG, "Data " + data);
                type = intent.getType();
                Log.d(SpaceUtils.TAG, "Type " + type);
                if (type == null && data != null) {
                    type = getContentResolver().getType(data);
                    Log.i(SpaceUtils.TAG, "Type: " + type);
                }
                if (type == null) {
                    type = "?/?";
                }
                switch (action) {
                    case Intent.ACTION_SEND:
                        if (type.startsWith("text/")) {
                            handleText(intent.getStringExtra(Intent.EXTRA_TEXT));
                        } else if (type.startsWith("image/")) {
                            if (data == null) {
                                data = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                            }
                            handleImage(data);
                        } else {
                            Log.d(SpaceUtils.TAG, "Unhandled type: " + type);
                        }
                        break;
                    case Intent.ACTION_SEND_MULTIPLE:
                        if (type.startsWith("image/")) {
                            handleImages(intent.<Uri>getParcelableArrayListExtra(Intent.EXTRA_STREAM));
                        } else {
                            Log.d(SpaceUtils.TAG, "Unhandled type: " + type);
                        }
                        break;
                    default:
                        Log.d(SpaceUtils.TAG, "Unhandled action: " + action);
                        break;
                }
            }
        } else {
            fab.setVisibility(View.INVISIBLE);
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
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
            case SpaceAndroidUtils.PAYMENT_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        final String name = nameEditText.getText().toString();
                        Log.d(SpaceUtils.TAG, "Name: " + name);
                        final String paymentId = data.getStringExtra(SpaceAndroidUtils.STRIPE_TOKEN_EXTRA);
                        Log.d(SpaceUtils.TAG, "PaymentId: " + paymentId);
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    InetAddress address = InetAddress.getByName("space.aletheiaware.com");
                                    Log.d(SpaceUtils.TAG, "Address: " + address);
                                    KeyPair keys = SpaceAndroidUtils.getKeys();
                                    upload(address, keys, null, paymentId, name);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                } catch (InvalidKeyException e) {
                                    e.printStackTrace();
                                } catch (SignatureException e) {
                                    e.printStackTrace();
                                } catch (InvalidAlgorithmParameterException e) {
                                    e.printStackTrace();
                                } catch (NoSuchPaddingException e) {
                                    e.printStackTrace();
                                } catch (BadPaddingException e) {
                                    e.printStackTrace();
                                } catch (IllegalBlockSizeException e) {
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
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void upload(InetAddress address, KeyPair keys, String customerId, String paymentId, String name) throws IOException, BadPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, SignatureException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        StorageRequest request = SpaceUtils.createRequest(keys, customerId, paymentId, name, type, data, preview);
        Log.d(SpaceUtils.TAG, "Request: " + request);
        StorageResponse response = SpaceUtils.sendRequest(address, request);
        if (response == null) {
            Log.d(SpaceUtils.TAG, "Server did not respond");
        } else {
            Log.d(SpaceUtils.TAG, "Response: " + response);
            // Upload successful, finish
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setResult(RESULT_OK);
                    finish();
                }
            });
        }
    }

    public void handleText(final String text) {
        data = text.getBytes();
        size = data.length;
        preview = SpaceAndroidUtils.createPreview(type, data);

        // EditText
        String generatedName = System.currentTimeMillis() + ".txt";
        nameEditText.setText(generatedName);

        // Type TextView
        typeTextView.setText(type);

        // Size TextView
        sizeTextView.setText(SpaceUtils.sizeToString(size));

        // Preview TextView
        if (preview != null && preview.length > 0) {
            previewTextView.setText(new String(preview));
        } else {
            previewTextView.setText(text);
        }
        previewTextView.setVisibility(View.VISIBLE);
    }

    public void handleImage(final Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int count;
            byte[] buffer = new byte[1024];
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            data = out.toByteArray();
        } catch (IOException e) {
            SpaceAndroidUtils.showErrorDialog(this, R.string.error_reading_image, e);
        }
        size = data.length;
        if (type.equals("image/*")) {
            // Default to PNG
            type = SpaceUtils.IMAGE_PNG_TYPE;
        }
        preview = SpaceAndroidUtils.createPreview(type, data);

        // Name EditText
        nameEditText.setText(getName(uri));

        // Type TextView
        typeTextView.setText(type);

        // Size TextView
        sizeTextView.setText(SpaceUtils.sizeToString(size));

        // Preview ImageView
        Bitmap image;
        if (preview != null && preview.length > 0) {
            image = BitmapFactory.decodeByteArray(preview, 0, preview.length);
        } else {
            image = BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        if (image != null) {
            previewImageView.setImageBitmap(image);
        }
        previewImageView.setVisibility(View.VISIBLE);
    }

    public void handleImages(ArrayList<Uri> uris) {
        // TODO
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(context.getString(R.string.notification_channel_description));
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private static NotificationCompat.Builder createUploadNotification(Context context, String name) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.cloud_upload)
                .setContentTitle(context.getString(R.string.title_notification_upload))
                .setContentText(name)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setProgress(0, 0, true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(UPLOAD_NOTIFICATION_TIMEOUT);
    }

    private String getName(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
            cursor.close();
        }
        // Return last part of URI path
        String path = uri.getPath();
        if (path != null) {
            path = path.substring(path.lastIndexOf('/') + 1);
        }
        return path;
    }
}
