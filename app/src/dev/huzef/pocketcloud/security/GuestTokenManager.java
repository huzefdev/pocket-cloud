package dev.huzef.pocketcloud.security;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.security.SecureRandom;
import java.util.Locale;

import dev.huzef.pocketcloud.service.PocketCloudService;

public final class GuestTokenManager {

    private static final String PREFS_GUEST = "pocketcloud_guest_token_v1";
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public static class GuestSession {
        public final String username;
        public final String password;
        public final long expiresAtMs;

        public GuestSession(String username, String password, long expiresAtMs) {
            this.username = username;
            this.password = password;
            this.expiresAtMs = expiresAtMs;
        }

        public boolean isActive() {
            return username != null && !username.isEmpty() && System.currentTimeMillis() < expiresAtMs;
        }

        public long remainingMinutes() {
            long rem = expiresAtMs - System.currentTimeMillis();
            return rem > 0 ? Math.max(1L, rem / 60000L) : 0L;
        }
    }

    private GuestTokenManager() {}

    public static GuestSession getActiveSession(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_GUEST, Context.MODE_PRIVATE);
        String u = prefs.getString("g_user", "");
        String p = prefs.getString("g_pass", "");
        long exp = prefs.getLong("g_exp", 0L);
        if (!u.isEmpty() && System.currentTimeMillis() >= exp) {
            revokeGuest(ctx);
            return null;
        }
        GuestSession s = new GuestSession(u, p, exp);
        return s.isActive() ? s : null;
    }

    public static GuestSession createReadOnlyGuest(final Context ctx, int durationMinutes) throws Exception {
        revokeGuest(ctx);
        SecureRandom rng = new SecureRandom();
        StringBuilder code = new StringBuilder(4);
        StringBuilder pass = new StringBuilder(8);
        for (int i = 0; i < 4; i++) code.append(CHARS.charAt(rng.nextInt(CHARS.length())));
        for (int i = 0; i < 8; i++) pass.append(CHARS.charAt(rng.nextInt(CHARS.length())));

        final String guestUser = "guest_" + code.toString().toLowerCase(Locale.US);
        final String guestPass = pass.toString();
        long expiresAt = System.currentTimeMillis() + (durationMinutes * 60000L);

        File workDir = new File(ctx.getFilesDir(), "filebrowser");
        File dbFile = new File(workDir, "filebrowser.db");
        File bin = resolveFbBinary(ctx);
        if (bin == null || !bin.exists() || !dbFile.exists()) {
            throw new IllegalStateException("Start FileBrowser (:8080) first to initialize DB");
        }

        PocketCloudService.stopEngineForPasswordUpdate(8080);
        Thread.sleep(300);

        Process p = new ProcessBuilder(
                bin.getAbsolutePath(), "users", "add",
                guestUser, guestPass,
                "--perm.admin=false",
                "--perm.create=false",
                "--perm.delete=false",
                "--perm.modify=false",
                "--perm.rename=false",
                "--perm.share=false",
                "--perm.download=true",
                "-d", dbFile.getAbsolutePath()
        ).directory(workDir).start();
        p.waitFor();

        PocketCloudService.restartEngineAfterPasswordUpdate(8080);

        ctx.getSharedPreferences(PREFS_GUEST, Context.MODE_PRIVATE)
                .edit()
                .putString("g_user", guestUser)
                .putString("g_pass", guestPass)
                .putLong("g_exp", expiresAt)
                .apply();

        PocketCloudService.appendLog("[GUEST] Read-only token active: " + guestUser + " (" + durationMinutes + "m)");
        return new GuestSession(guestUser, guestPass, expiresAt);
    }

    public static void revokeGuest(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_GUEST, Context.MODE_PRIVATE);
        String oldUser = prefs.getString("g_user", "");
        prefs.edit().clear().apply();
        if (oldUser == null || oldUser.isEmpty()) return;

        try {
            File workDir = new File(ctx.getFilesDir(), "filebrowser");
            File dbFile = new File(workDir, "filebrowser.db");
            File bin = resolveFbBinary(ctx);
            if (bin != null && bin.exists() && dbFile.exists()) {
                PocketCloudService.stopEngineForPasswordUpdate(8080);
                Thread.sleep(250);
                new ProcessBuilder(
                        bin.getAbsolutePath(), "users", "rm", oldUser,
                        "-d", dbFile.getAbsolutePath()
                ).directory(workDir).start().waitFor();
                PocketCloudService.restartEngineAfterPasswordUpdate(8080);
                PocketCloudService.appendLog("[GUEST] Revoked expired guest account: " + oldUser);
            }
        } catch (Exception ignored) {}
    }

    private static File resolveFbBinary(Context ctx) {
        File direct = new File(ctx.getApplicationInfo().nativeLibraryDir, "libfilebrowser.so");
        if (direct.exists()) return direct;
        File fallback = new File(ctx.getFilesDir(), "native_bin/libfilebrowser.so");
        return fallback.exists() ? fallback : null;
    }
}
