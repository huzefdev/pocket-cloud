package dev.huzef.pocketcloud.core;

import android.content.Context;
import dev.huzef.pocketcloud.security.SecurityVaultManager;

public class VaultConfigManager {

    public static boolean isVaultConfigured(Context ctx) {
        return SecurityVaultManager.isConfigured(ctx, 8080);
    }

    public static boolean isMediaConfigured(Context ctx) {
        return SecurityVaultManager.isConfigured(ctx, 8096);
    }

    public static void setMediaConfigured(Context ctx, boolean configured) {
        SecurityVaultManager.markConfigured(ctx, 8096, configured);
    }

    public static String getUsername(Context ctx) {
        SecurityVaultManager.ServerCredential c = SecurityVaultManager.getCredential(ctx, 8080);
        return (c != null && c.configured && !c.username.isEmpty()) ? c.username : "admin";
    }

    public static String getPassword(Context ctx) {
        SecurityVaultManager.ServerCredential c = SecurityVaultManager.getCredential(ctx, 8080);
        return (c != null && c.configured) ? c.password : "";
    }

    public static void saveCredentials(Context ctx, String username, String password) {
        SecurityVaultManager.saveCredential(ctx, 8080, username, password, true);
    }

    public static void setCredentials(Context ctx, String username, String password) {
        SecurityVaultManager.saveCredential(ctx, 8080, username, password, true);
    }
}
