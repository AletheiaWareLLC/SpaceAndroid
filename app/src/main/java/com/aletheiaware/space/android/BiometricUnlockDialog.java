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

package com.aletheiaware.space.android;

import android.annotation.TargetApi;
import android.app.Activity;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.android.utils.BiometricCallback;
import com.aletheiaware.space.android.utils.BiometricUtils;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public abstract class BiometricUnlockDialog {

    private final Activity activity;
    private final String alias;

    public BiometricUnlockDialog(Activity activity, String alias) {
        this.activity = activity;
        this.alias = alias;
    }

    @TargetApi(Build.VERSION_CODES.P)
    public void create() {
        try {
            // Load keystore
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(BiometricUtils.BIOMETRIC_KEY + alias, null);

            File f = BiometricUtils.getBiometricFile(activity, alias);
            try (FileInputStream in = new FileInputStream(f)) {
                // Read initialization vector
                byte[] iv = new byte[BCUtils.AES_IV_SIZE_BYTES];
                if (in.read(iv) != BCUtils.AES_IV_SIZE_BYTES) {
                    throw new Exception("Could not read IV");
                }

                // Read encrypted password
                int size = in.available();
                final byte[] data = new byte[size];
                if (in.read(data) != size) {
                    throw new Exception("Could not read encrypted password");
                }

                GCMParameterSpec gcmSpec = new GCMParameterSpec(BCUtils.GCM_TAG_SIZE_BITS, iv);

                // Create AES cipher
                Cipher cipher = Cipher.getInstance(BCUtils.AES_CIPHER);
                cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
                BiometricPrompt.CryptoObject crypto = new BiometricPrompt.CryptoObject(cipher);
                CancellationSignal cancel = new CancellationSignal();
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
                        Cipher cipher = result.getCryptoObject().getCipher();
                        try {
                            onUnlock(new String(cipher.doFinal(data)).toCharArray());
                        } catch (Exception e) {
                            onError(e);
                        }
                    }
                };
                BiometricPrompt prompt = BiometricUtils.showBiometricPrompt(activity, callback);
                prompt.authenticate(crypto, cancel, activity.getMainExecutor(), callback);
            }
        } catch (Exception e) {
            onError(e);
        }
    }

    public abstract void onUnlock(char[] password);

    public abstract void onError(Exception e);
}
