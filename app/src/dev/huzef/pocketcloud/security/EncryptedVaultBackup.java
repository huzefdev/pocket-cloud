package dev.huzef.pocketcloud.security;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import dev.huzef.pocketcloud.core.GlassTheme;

public final class EncryptedVaultBackup {

    private static final int PBKDF2_ITERS = 100000;
    private static final int KEY_LEN_BITS = 256;

    private EncryptedVaultBackup() {}

    public static File exportEncryptedBackup(Context ctx, String masterPassphrase) throws Exception {
        if (masterPassphrase == null || masterPassphrase.trim().length() < 6) {
            throw new IllegalArgumentException("Master passphrase must be at least 6 characters");
        }

        JSONObject root = new JSONObject();
        root.put("format", "POCKETCLOUD_VAULT_V1");
        root.put("exportedAt", System.currentTimeMillis());
        root.put("theme", GlassTheme.getCurrentThemeId());

        int[] ports = new int[]{8080, 8096, 8384};
        for (int p : ports) {
            SecurityVaultManager.ServerCredential c = SecurityVaultManager.getCredential(ctx, p);
            if (c != null && c.configured) {
                JSONObject item = new JSONObject();
                item.put("user", c.username);
                item.put("pass", c.password);
                root.put("port_" + p, item);
            }
        }

        byte[] plainBytes = root.toString().getBytes(StandardCharsets.UTF_8);
        SecureRandom rng = new SecureRandom();
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        rng.nextBytes(salt);
        rng.nextBytes(iv);

        SecretKey key = deriveKey(masterPassphrase.trim(), salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] cipherBytes = cipher.doFinal(plainBytes);

        JSONObject envelope = new JSONObject();
        envelope.put("v", 1);
        envelope.put("kdf", "PBKDF2-HMAC-SHA256");
        envelope.put("iters", PBKDF2_ITERS);
        envelope.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        envelope.put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP));

        File dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dlDir.exists()) dlDir.mkdirs();
        File outFile = new File(dlDir, "PocketCloud_Vault.pcvault");
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(envelope.toString(2).getBytes(StandardCharsets.UTF_8));
        fos.close();
        return outFile;
    }

    public static int importEncryptedBackup(Context ctx, String masterPassphrase) throws Exception {
        if (masterPassphrase == null || masterPassphrase.trim().length() < 6) {
            throw new IllegalArgumentException("Master passphrase must be at least 6 characters");
        }
        File dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File inFile = new File(dlDir, "PocketCloud_Vault.pcvault");
        if (!inFile.exists()) {
            throw new IllegalStateException("No PocketCloud_Vault.pcvault found in Downloads");
        }

        FileInputStream fis = new FileInputStream(inFile);
        byte[] raw = new byte[(int) inFile.length()];
        fis.read(raw);
        fis.close();

        JSONObject envelope = new JSONObject(new String(raw, StandardCharsets.UTF_8));
        byte[] salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP);
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
        byte[] cipherBytes = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP);

        SecretKey key = deriveKey(masterPassphrase.trim(), salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] plainBytes = cipher.doFinal(cipherBytes);

        JSONObject root = new JSONObject(new String(plainBytes, StandardCharsets.UTF_8));
        if (!"POCKETCLOUD_VAULT_V1".equals(root.optString("format"))) {
            throw new SecurityException("Invalid .pcvault signature");
        }

        if (root.has("theme")) {
            GlassTheme.setTheme(ctx, root.getString("theme"));
        }

        int restored = 0;
        int[] ports = new int[]{8080, 8096, 8384};
        for (int p : ports) {
            if (root.has("port_" + p)) {
                JSONObject item = root.getJSONObject("port_" + p);
                String u = item.optString("user", "");
                String pw = item.optString("pass", "");
                if (!u.isEmpty() && pw.length() >= 6) {
                    SecurityVaultManager.saveCredential(ctx, p, u, pw, true);
                    restored++;
                }
            }
        }
        return restored;
    }

    private static SecretKey deriveKey(String passphrase, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERS, KEY_LEN_BITS);
        byte[] secret = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(secret, "AES");
    }
}
