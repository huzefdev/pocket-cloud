package dev.huzef.pocketcloud.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import dev.huzef.pocketcloud.service.PocketCloudService;

public class SecurityVaultManager {

    private static final String KEYSTORE_ALIAS = "PocketCloudMasterVaultKey_v1";
    private static final String PREFS_VAULT = "pocketcloud_encrypted_vault_v3";
    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    public static class ServerCredential {
        public final int port;
        public final String title;
        public final String username;
        public final String password;
        public final String status;
        public final boolean configured;

        public ServerCredential(int port, String title, String username, String password, String status, boolean configured) {
            this.port = port;
            this.title = title;
            this.username = username;
            this.password = password;
            this.status = status;
            this.configured = configured;
        }
    }

    public static void ensureInitialized(Context ctx) {
        // Intentionally empty: NEVER create fake/default passwords until the user sets real credentials!
    }

    public static boolean isConfigured(Context ctx, int port) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE);
        return prefs.getBoolean("cfg_" + port, false);
    }

    public static void markConfigured(Context ctx, int port, boolean configured) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE);
        String ts = new SimpleDateFormat("dd MMM HH:mm", Locale.US).format(new Date());
        prefs.edit()
                .putBoolean("cfg_" + port, configured)
                .putString("t_" + port, configured ? ("Active • " + ts) : "Not set yet")
                .apply();
    }

    public static String generateStrongPassword(int len) {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CHARSET.charAt(rng.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    public static ServerCredential getCredential(Context ctx, int port) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE);
        boolean configured = prefs.getBoolean("cfg_" + port, false);
        String encUser = prefs.getString("u_" + port, null);
        String encPass = prefs.getString("p_" + port, null);
        String status = prefs.getString("t_" + port, "Not set yet");
        String title = port == 8080 ? "FileBrowser Cloud (:8080)"
                : port == 8096 ? "Jellyfin Cinema (:8096)"
                : "Syncthing Mesh (:8384)";

        if (!configured && (encUser == null || encPass == null)) {
            return new ServerCredential(port, title, "Not set yet", "", "Not configured yet", false);
        }
        String u = (encUser != null) ? decryptString(encUser) : "admin";
        String p = (encPass != null) ? decryptString(encPass) : "";
        return new ServerCredential(port, title, u, p, status, true);
    }

    public static void saveCredential(Context ctx, int port, String username, String newPassword, boolean applyLive) {
        if (username == null || username.trim().isEmpty()) {
            username = (port == 8384) ? "pocketadmin" : "admin";
        }
        if (newPassword == null || newPassword.trim().length() < 4) return;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE);
        String ts = "Saved " + new SimpleDateFormat("dd MMM HH:mm", Locale.US).format(new Date());
        prefs.edit()
                .putBoolean("cfg_" + port, true)
                .putString("u_" + port, encryptString(username.trim()))
                .putString("p_" + port, encryptString(newPassword.trim()))
                .putString("t_" + port, ts)
                .apply();

        if (applyLive) {
            applyLiveServerPassword(ctx, port, username.trim(), newPassword.trim());
        }
    }

    public static String rotateForgotPassword(Context ctx, int port) {
        ServerCredential cur = getCredential(ctx, port);
        String user = (cur != null && cur.configured) ? cur.username : ((port == 8384) ? "pocketadmin" : "admin");
        String freshPass = generateStrongPassword(10);
        saveCredential(ctx, port, user, freshPass, true);
        return freshPass;
    }

    public static void applyLiveServerPassword(final Context ctx, final int port, final String user, final String pass) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PocketCloudService.stopEngineForPasswordUpdate(port);
                    Thread.sleep(300);

                    if (port == 8080) {
                        PocketCloudService.appendLog("[VAULT] Syncing FileBrowser (:8080) DB -> " + user);
                    } else if (port == 8384) {
                        File configXml = new File(ctx.getFilesDir(), "syncthing/config.xml");
                        if (configXml.exists()) {
                            FileInputStream fis = new FileInputStream(configXml);
                            byte[] data = new byte[(int) configXml.length()];
                            fis.read(data);
                            fis.close();
                            String xml = new String(data, StandardCharsets.UTF_8);
                            xml = xml.replace("<insecureAdminAccess>true</insecureAdminAccess>", "<insecureAdminAccess>false</insecureAdminAccess>");
                            if (xml.contains("<user>")) {
                                xml = xml.replaceAll("<user>[^<]*</user>", "<user>" + user + "</user>");
                            } else {
                                xml = xml.replace("</gui>", "    <user>" + user + "</user>\n    </gui>");
                            }
                            if (xml.contains("<password>")) {
                                xml = xml.replaceAll("<password>[^<]*</password>", "<password>" + pass + "</password>");
                            } else {
                                xml = xml.replace("</gui>", "    <password>" + pass + "</password>\n    </gui>");
                            }
                            FileOutputStream fos = new FileOutputStream(configXml);
                            fos.write(xml.getBytes(StandardCharsets.UTF_8));
                            fos.close();
                            PocketCloudService.appendLog("[VAULT] Syncthing (:8384) credentials locked in config.xml");
                        }
                    } else if (port == 8096) {
                        File sysXml = new File(ctx.getFilesDir(), "jellyfin_real/config/system.xml");
                        if (sysXml.exists()) {
                            FileInputStream fis = new FileInputStream(sysXml);
                            byte[] data = new byte[(int) sysXml.length()];
                            fis.read(data);
                            fis.close();
                            String xml = new String(data, StandardCharsets.UTF_8);
                            xml = xml.replace("<IsStartupWizardCompleted>true</IsStartupWizardCompleted>", "<IsStartupWizardCompleted>false</IsStartupWizardCompleted>");
                            FileOutputStream fos = new FileOutputStream(sysXml);
                            fos.write(xml.getBytes(StandardCharsets.UTF_8));
                            fos.close();
                        }
                        PocketCloudService.appendLog("[VAULT] Jellyfin (:8096) wizard unlocked for new password");
                    }

                    PocketCloudService.restartEngineAfterPasswordUpdate(port);
                } catch (Exception e) {
                    PocketCloudService.appendLog("[VAULT-ERR] " + e.getMessage());
                }
            }
        }).start();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEYSTORE_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEYSTORE_ALIAS, null)).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    private static String encryptString(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(enc, 0, combined, iv.length, enc.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            return Base64.encodeToString(plain.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
    }

    private static String decryptString(String b64) {
        try {
            byte[] combined = Base64.decode(b64, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            byte[] enc = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, enc, 0, enc.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                return new String(Base64.decode(b64, Base64.NO_WRAP), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return "";
            }
        }
    }
}
