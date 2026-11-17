package dev.huzef.pocketcloud.security;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

public class BiometricGatekeeper {

    public interface AuthCallback {
        void onUnlocked();
        void onFailed(String reason);
    }

    public static void authenticateVault(final Activity activity, String title, String subtitle, final AuthCallback callback) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                BiometricManager bm = (BiometricManager) activity.getSystemService(Context.BIOMETRIC_SERVICE);
                int authTypes = BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
                if (bm != null && bm.canAuthenticate(authTypes) == BiometricManager.BIOMETRIC_SUCCESS) {
                    BiometricPrompt prompt = new BiometricPrompt.Builder(activity)
                            .setTitle(title)
                            .setSubtitle(subtitle)
                            .setAllowedAuthenticators(authTypes)
                            .build();
                    prompt.authenticate(new CancellationSignal(), activity.getMainExecutor(),
                            new BiometricPrompt.AuthenticationCallback() {
                                @Override
                                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onUnlocked();
                                        }
                                    });
                                }

                                @Override
                                public void onAuthenticationError(int errorCode, final CharSequence errString) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onFailed(errString != null ? errString.toString() : "Auth cancelled");
                                        }
                                    });
                                }
                            });
                    return;
                }
            } catch (Exception ignored) {}
        }

        if (Build.VERSION.SDK_INT >= 28) {
            try {
                BiometricPrompt prompt = new BiometricPrompt.Builder(activity)
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setNegativeButton("Cancel", activity.getMainExecutor(), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                callback.onFailed("Cancelled");
                            }
                        })
                        .build();
                prompt.authenticate(new CancellationSignal(), activity.getMainExecutor(),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onUnlocked();
                                    }
                                });
                            }

                            @Override
                            public void onAuthenticationError(int errorCode, final CharSequence errString) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onFailed(errString != null ? errString.toString() : "Auth cancelled");
                                    }
                                });
                            }
                        });
                return;
            } catch (Exception ignored) {}
        }

        KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && !km.isDeviceSecure()) {
            callback.onUnlocked();
        } else {
            callback.onFailed("Biometric/Lockscreen unavailable");
        }
    }
}
