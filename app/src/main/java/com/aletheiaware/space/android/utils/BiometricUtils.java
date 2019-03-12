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

package com.aletheiaware.space.android.utils;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.v4.app.ActivityCompat;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;

import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.android.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class BiometricUtils {

    public static final String BIOMETRIC_KEY = "Biometric:";
    private static final String BIOMETRIC_FILE = ".biometric";

    private BiometricUtils() {}

    public static boolean isBiometricUnlockAvailable(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        FingerprintManagerCompat manager = FingerprintManagerCompat.from(context);
        if (!manager.isHardwareDetected()) {
            return false;
        }
        if (!manager.hasEnrolledFingerprints()) {
            return false;
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.USE_BIOMETRIC) == PackageManager.PERMISSION_GRANTED;
    }

    @TargetApi(Build.VERSION_CODES.P)
    public static BiometricPrompt showBiometricPrompt(Context context, final BiometricCallback callback) {
        return new BiometricPrompt.Builder(context)
                .setTitle(context.getString(R.string.access_biometric_unlock_title))
                .setDescription(context.getString(R.string.access_biometric_unlock_description))
                .setNegativeButton(context.getString(android.R.string.cancel), context.getMainExecutor(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        callback.onAuthenticationCancelled();
                    }
                })
                .build();
    }

    public static File getBiometricFile(Context context, String alias) {
        return new File(context.getFilesDir(), alias + BIOMETRIC_FILE);
    }

    public static boolean isBiometricUnlockEnabled(Context context, String alias) {
        return getBiometricFile(context, alias).exists();
    }

    @TargetApi(Build.VERSION_CODES.P)
    public static void enableBiometricUnlock(final Context context, final String alias, final char[] password) throws InvalidAlgorithmParameterException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, NoSuchProviderException {
        // Generate key
        int purposes = KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT;
        KeyGenParameterSpec.Builder keySpecBuilder = new KeyGenParameterSpec.Builder(BIOMETRIC_KEY + alias, purposes)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            keySpecBuilder.setInvalidatedByBiometricEnrollment(true);
        }
        KeyGenParameterSpec keySpec = keySpecBuilder.build();
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(keySpec);
        SecretKey key = keyGenerator.generateKey();

        // Create AES Cipher
        Cipher cipher = Cipher.getInstance(BCUtils.AES_CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        // Create cancellation signal
        CancellationSignal cancel = new CancellationSignal();

        // Create callback
        BiometricCallback callback = new BiometricCallback(cancel) {
            @Override
            public void onAuthenticationFailed() {
                // TODO
            }

            @Override
            public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                // TODO
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                // TODO
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                // Encrypt password and write to file
                File f = getBiometricFile(context, alias);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    Cipher cipher = result.getCryptoObject().getCipher();
                    out.write(cipher.getIV());
                    out.write(cipher.doFinal(new String(password).getBytes(StandardCharsets.UTF_8)));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                }
            }
        };

        // Show prompt
        BiometricPrompt prompt = showBiometricPrompt(context, callback);
        // Authenticate user
        prompt.authenticate(new BiometricPrompt.CryptoObject(cipher), cancel, context.getMainExecutor(), callback);
    }

    public static boolean disableBiometricUnlock(Context context, String alias) {
        return getBiometricFile(context, alias).delete();
    }
}
