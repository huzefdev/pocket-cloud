package dev.huzef.pocketcloud.core;

import android.content.Context;
import android.content.SharedPreferences;

public final class VaultConfigManager {

    private static final String PREFS = "pocketcloud_vault_prefs";
    private static final String KEY_CONFIGURED = "fb_vault_configured";
    private static final String KEY_USER = "fb_admin_user";
    private static final String KEY_PASS = "fb_admin_pass";
    private static final String KEY_MEDIA_CONFIGURED = "jf_media_configured";

    private VaultConfigManager() {}

    public static boolean isVaultConfigured(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CONFIGURED, false);
    }

    public static boolean isMediaConfigured(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_MEDIA_CONFIGURED, false);
    }

    public static void setMediaConfigured(Context ctx, boolean done) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MEDIA_CONFIGURED, done)
                .apply();
    }

    public static String getUsername(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_USER, "admin");
    }

    public static String getPassword(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PASS, "");
    }

    public static void saveCredentials(Context ctx, String user, String pass) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CONFIGURED, true)
                .putString(KEY_USER, user.trim())
                .putString(KEY_PASS, pass)
                .apply();
    }
}
