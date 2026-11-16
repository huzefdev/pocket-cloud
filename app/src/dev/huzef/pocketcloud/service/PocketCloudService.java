package dev.huzef.pocketcloud.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import dev.huzef.pocketcloud.core.HardwareTelemetry;
import dev.huzef.pocketcloud.core.VaultConfigManager;
import dev.huzef.pocketcloud.server.PocketMediaServer;
import dev.huzef.pocketcloud.ui.MainActivity;

public class PocketCloudService extends Service {

    public static final String ACTION_START_ENGINE = "dev.huzef.pocketcloud.START_ENGINE";
    public static final String ACTION_STOP_ENGINE = "dev.huzef.pocketcloud.STOP_ENGINE";
    public static final String ACTION_REINIT_FILEBROWSER = "dev.huzef.pocketcloud.REINIT_FB";
    private static final String CHANNEL_ID = "pocketcloud_engine_channel";
    private static final int NOTIF_ID = 8080;

    public static volatile boolean isEngineActive = false;
    public static volatile boolean isWakeLockActive = false;
    private static final List<String> ENGINE_LOGS = Collections.synchronizedList(new ArrayList<String>());

    private PowerManager.WakeLock wakeLock;
    private Process fileBrowserProcess;
    private Process syncthingProcess;
    private PocketMediaServer mediaServer;

    public static List<String> getLogsSnapshot() {
        synchronized (ENGINE_LOGS) {
            return new ArrayList<String>(ENGINE_LOGS);
        }
    }

    public static void appendLog(String msg) {
        synchronized (ENGINE_LOGS) {
            if (ENGINE_LOGS.size() > 150) {
                ENGINE_LOGS.remove(0);
            }
            ENGINE_LOGS.add(msg);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        mediaServer = new PocketMediaServer(8096);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START_ENGINE;
        if (ACTION_STOP_ENGINE.equals(action)) {
            stopAllDaemons();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_REINIT_FILEBROWSER.equals(action)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    restartFileBrowserWithVaultCreds();
                }
            }, "PocketCloud-FB-Reinit").start();
            return START_STICKY;
        }

        startForeground(NOTIF_ID, buildNotification("Booting Bare-Metal Cloud Daemons..."));
        startAllDaemons();
        return START_STICKY;
    }

    private File resolveNativeBinary(String libName) {
        String nativeDir = getApplicationInfo().nativeLibraryDir;
        File direct = new File(nativeDir, libName);
        if (direct.exists() && direct.length() > 1024) {
            return direct;
        }
        try {
            File fallbackDir = new File(getFilesDir(), "native_bin");
            if (!fallbackDir.exists()) fallbackDir.mkdirs();
            File outBin = new File(fallbackDir, libName);
            if (outBin.exists() && outBin.length() > 1024) {
                outBin.setExecutable(true, false);
                return outBin;
            }
            ZipFile zip = new ZipFile(getApplicationInfo().sourceDir);
            ZipEntry entry = zip.getEntry("lib/arm64-v8a/" + libName);
            if (entry != null) {
                InputStream is = zip.getInputStream(entry);
                FileOutputStream fos = new FileOutputStream(outBin);
                byte[] buf = new byte[32768];
                int r;
                while ((r = is.read(buf)) != -1) {
                    fos.write(buf, 0, r);
                }
                fos.close();
                is.close();
                zip.close();
                outBin.setExecutable(true, false);
                return outBin;
            }
            zip.close();
        } catch (Exception e) {
            appendLog("[BIN] Extract fallback err: " + e.getMessage());
        }
        return null;
    }

    private void startAllDaemons() {
        if (isEngineActive) return;
        isEngineActive = true;
        acquireWakeLock();

        new Thread(new Runnable() {
            @Override
            public void run() {
                appendLog("[SYS] Initializing PocketCloud Zero-Termux Engine...");
                startNativeFileBrowser(false);

                if (HardwareTelemetry.pingLocalPort(8096, 120) < 0) {
                    mediaServer.start();
                    appendLog("[8096] PocketMedia HTTP Range Server ONLINE");
                }

                startNativeSyncthing();

                String ip = HardwareTelemetry.detectLocalIpv4();
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.notify(NOTIF_ID, buildNotification("Node Online • http://" + ip + ":8080"));
                }
            }
        }, "PocketCloud-Boot").start();
    }

    private synchronized void restartFileBrowserWithVaultCreds() {
        appendLog("[8080] Applying custom user vault credentials...");
        if (fileBrowserProcess != null) {
            fileBrowserProcess.destroy();
            try { fileBrowserProcess.waitFor(); } catch (Exception ignored) {}
            fileBrowserProcess = null;
        }
        File workDir = new File(getFilesDir(), "filebrowser");
        File dbFile = new File(workDir, "filebrowser.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
        isEngineActive = true;
        acquireWakeLock();
        startNativeFileBrowser(true);
    }

    private void startNativeFileBrowser(boolean forceRecreateDb) {
        try {
            if (!forceRecreateDb && HardwareTelemetry.pingLocalPort(8080, 120) > 0) {
                appendLog("[8080] FileBrowser already listening on :8080");
                return;
            }
            File bin = resolveNativeBinary("libfilebrowser.so");
            if (bin == null || !bin.exists()) {
                appendLog("[8080] ERR: libfilebrowser.so missing from APK!");
                return;
            }

            File workDir = new File(getFilesDir(), "filebrowser");
            if (!workDir.exists()) workDir.mkdirs();
            File dbFile = new File(workDir, "filebrowser.db");
            String storageRoot = Environment.getExternalStorageDirectory().getAbsolutePath();

            String adminUser = VaultConfigManager.getUsername(this);
            String adminPass = VaultConfigManager.getPassword(this);
            if (adminPass == null || adminPass.length() < 6) {
                adminPass = "pocketcloud123";
            }

            if (!dbFile.exists() || forceRecreateDb) {
                if (dbFile.exists()) dbFile.delete();
                runOneShot(new String[]{
                        bin.getAbsolutePath(), "config", "init",
                        "-a", "0.0.0.0",
                        "-p", "8080",
                        "-r", storageRoot,
                        "-d", dbFile.getAbsolutePath()
                }, workDir);
                runOneShot(new String[]{
                        bin.getAbsolutePath(), "users", "add",
                        adminUser, adminPass,
                        "--perm.admin=true",
                        "-d", dbFile.getAbsolutePath()
                }, workDir);
                appendLog("[8080] Vault Admin provisioned for user: " + adminUser);
            }

            ProcessBuilder pb = new ProcessBuilder(
                    bin.getAbsolutePath(),
                    "-a", "0.0.0.0",
                    "-p", "8080",
                    "-r", storageRoot,
                    "-d", dbFile.getAbsolutePath()
            );
            pb.directory(workDir);
            pb.environment().put("HOME", workDir.getAbsolutePath());
            pb.redirectErrorStream(true);
            fileBrowserProcess = pb.start();
            pipeProcessLogs("[8080]", fileBrowserProcess);
            appendLog("[8080] FileBrowser LIVE on http://0.0.0.0:8080");
        } catch (Exception e) {
            appendLog("[8080] FATAL: " + e.getMessage());
        }
    }

    private void startNativeSyncthing() {
        try {
            if (HardwareTelemetry.pingLocalPort(8384, 120) > 0) {
                appendLog("[8384] Syncthing already active on :8384");
                return;
            }
            File bin = resolveNativeBinary("libsyncthing.so");
            if (bin == null || !bin.exists()) {
                appendLog("[8384] Standby (libsyncthing.so queued for next step)");
                return;
            }
            File homeDir = new File(getFilesDir(), "syncthing");
            if (!homeDir.exists()) homeDir.mkdirs();
            File configXml = new File(homeDir, "config.xml");

            if (!configXml.exists()) {
                runOneShot(new String[]{
                        bin.getAbsolutePath(), "generate", "--home=" + homeDir.getAbsolutePath()
                }, homeDir);
                patchSyncthingBindAddress(configXml);
            }

            ProcessBuilder pb = new ProcessBuilder(
                    bin.getAbsolutePath(),
                    "-no-browser",
                    "-gui-address=0.0.0.0:8384",
                    "-home=" + homeDir.getAbsolutePath()
            );
            pb.environment().put("HOME", homeDir.getAbsolutePath());
            pb.environment().put("STNORESTART", "1");
            pb.directory(homeDir);
            pb.redirectErrorStream(true);
            syncthingProcess = pb.start();
            pipeProcessLogs("[8384]", syncthingProcess);
            appendLog("[8384] Syncthing P2P Node SPAWNED");
        } catch (Exception e) {
            appendLog("[8384] ERR: " + e.getMessage());
        }
    }

    private void patchSyncthingBindAddress(File configXml) {
        try {
            if (!configXml.exists()) return;
            FileInputStream fis = new FileInputStream(configXml);
            byte[] data = new byte[(int) configXml.length()];
            fis.read(data);
            fis.close();
            String xml = new String(data, StandardCharsets.UTF_8);
            xml = xml.replace("<address>127.0.0.1:8384</address>", "<address>0.0.0.0:8384</address>");
            FileOutputStream fos = new FileOutputStream(configXml);
            fos.write(xml.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception ignored) {}
    }

    private void runOneShot(String[] cmd, File dir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            pb.environment().put("HOME", dir.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                appendLog("[INIT] " + line);
            }
            p.waitFor();
        } catch (Exception e) {
            appendLog("[INIT-ERR] " + e.getMessage());
        }
    }

    private void pipeProcessLogs(final String tag, final Process p) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = br.readLine()) != null) {
                        appendLog(tag + " " + line);
                    }
                } catch (Exception ignored) {}
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void stopAllDaemons() {
        isEngineActive = false;
        if (mediaServer != null) mediaServer.stop();
        if (fileBrowserProcess != null) {
            fileBrowserProcess.destroy();
            fileBrowserProcess = null;
        }
        if (syncthingProcess != null) {
            syncthingProcess.destroy();
            syncthingProcess = null;
        }
        releaseWakeLock();
        appendLog("[SYS] All PocketCloud daemons stopped.");
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketCloud::NodeEngineLock");
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
                isWakeLockActive = true;
            }
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {}
        isWakeLockActive = false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "PocketCloud Bare-Metal Engine",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String content) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("POCKET-CLOUD-01")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        stopAllDaemons();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
