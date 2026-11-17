package dev.huzef.pocketcloud.server;

import android.content.Context;
import android.os.Environment;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import dev.huzef.pocketcloud.core.HardwareTelemetry;
import dev.huzef.pocketcloud.service.PocketCloudService;

public class JellyfinEngineManager {

    public interface InstallProgressListener {
        void onProgress(int percent, String statusText);
        void onComplete(boolean success, String message);
    }

    private final Context context;
    private Process jellyfinProcess;
    private volatile boolean installing = false;
    private volatile int currentPercent = 0;
    private volatile String currentStatus = "Initializing...";
    private volatile String lastProcessLine = "";
    private volatile InstallProgressListener activeListener;

    public String getLastProcessLine() {
        return lastProcessLine;
    }

    public JellyfinEngineManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public File getEngineDir() {
        File dir = new File(context.getFilesDir(), "jellyfin_real");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public boolean isInstalled() {
        File dir = getEngineDir();
        File flag = new File(dir, ".installed_ok");
        File bin = findBinaryRecursive(dir, "jellyfin", 4);
        return (flag.exists() || (bin != null && bin.exists() && bin.length() > 1024 * 1024)) && bin != null;
    }

    public boolean isInstalling() {
        return installing;
    }

    public void attachListener(InstallProgressListener listener) {
        this.activeListener = listener;
        if (installing && listener != null) {
            listener.onProgress(currentPercent, currentStatus);
        }
    }

    private File resolveProotLoader(File engineDir) {
        File nativeLoader = new File(context.getApplicationInfo().nativeLibraryDir, "libproot_loader.so");
        if (nativeLoader.exists() && nativeLoader.canExecute()) {
            return nativeLoader;
        }
        File localLoader = new File(engineDir, "proot_loader");
        if (!localLoader.exists() || localLoader.length() < 1024) {
            try {
                InputStream is = context.getAssets().open("proot_loader");
                FileOutputStream fos = new FileOutputStream(localLoader);
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                fos.close();
                is.close();
            } catch (Exception e) {
                PocketCloudService.appendLog("[8096] Loader asset extract err: " + e.getMessage());
            }
        }
        localLoader.setExecutable(true, false);
        return localLoader;
    }

    private File findLdLinuxInRootfs(File rootfs) {
        String[] candidates = new String[]{
                "lib/ld-linux-aarch64.so.1",
                "usr/lib/ld-linux-aarch64.so.1",
                "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
                "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
        };
        for (String c : candidates) {
            File f = new File(rootfs, c);
            if (f.exists() && f.length() > 10000) return f;
        }
        return findBinaryRecursive(rootfs, "ld-linux-aarch64.so.1", 4);
    }

    private void ensureLinkerSymlink(File rootfs) {
        try {
            File foundLd = findLdLinuxInRootfs(rootfs);
            if (foundLd != null && foundLd.exists()) {
                File libDir = new File(rootfs, "lib");
                libDir.mkdirs();
                File targetLd = new File(libDir, "ld-linux-aarch64.so.1");
                if (!targetLd.exists() || targetLd.length() < 10000) {
                    if (targetLd.exists()) targetLd.delete();
                    FileInputStream fis = new FileInputStream(foundLd);
                    FileOutputStream fos = new FileOutputStream(targetLd);
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = fis.read(buf)) != -1) fos.write(buf, 0, r);
                    fos.close();
                    fis.close();
                    targetLd.setExecutable(true, false);
                }
                PocketCloudService.appendLog("[8096] Glibc dynamic linker ready: " + targetLd.length() + " bytes");
            }
        } catch (Exception e) {
            PocketCloudService.appendLog("[8096] Linker copy err: " + e.getMessage());
        }
    }

    private boolean ensureGlibcRootfs(File dir, InstallProgressListener listener) {
        File rootfs = new File(dir, "rootfs");
        File targetLd = new File(rootfs, "lib/ld-linux-aarch64.so.1");
        File targetCpp = new File(rootfs, "lib/libstdc++.so.6");
        if (targetLd.exists() && targetLd.length() > 10000 && targetCpp.exists() && targetCpp.length() > 100000) {
            return true;
        }
        try {
            File libDir = new File(rootfs, "lib");
            File usrLibDir = new File(rootfs, "usr/lib");
            libDir.mkdirs();
            usrLibDir.mkdirs();
            notifyProgress(listener, 88, "Installing embedded ARM64 glibc runtime...");
            String[] soFiles = context.getAssets().list("glibc_arm64");
            if (soFiles != null) {
                byte[] buf = new byte[32768];
                for (String name : soFiles) {
                    InputStream is = context.getAssets().open("glibc_arm64/" + name);
                    File out1 = new File(libDir, name);
                    FileOutputStream fos1 = new FileOutputStream(out1);
                    int r;
                    while ((r = is.read(buf)) != -1) fos1.write(buf, 0, r);
                    fos1.close();
                    is.close();
                    out1.setExecutable(true, false);

                    File out2 = new File(usrLibDir, name);
                    FileInputStream fis2 = new FileInputStream(out1);
                    FileOutputStream fos2 = new FileOutputStream(out2);
                    while ((r = fis2.read(buf)) != -1) fos2.write(buf, 0, r);
                    fos2.close();
                    fis2.close();
                    out2.setExecutable(true, false);
                }
            }
            PocketCloudService.appendLog("[8096] Embedded glibc extracted: ld.so=" + targetLd.length() + " bytes");
            return targetLd.exists() && targetLd.length() > 10000;
        } catch (Exception e) {
            PocketCloudService.appendLog("[8096] Embedded glibc err: " + e.getMessage());
            return false;
        }
    }

    private void extractTarGzSelective(File tarGzFile, File destDir) throws Exception {
        InputStream fis = new BufferedInputStream(new FileInputStream(tarGzFile), 65536);
        GZIPInputStream gzis = new GZIPInputStream(fis, 65536);
        byte[] header = new byte[512];
        byte[] copyBuf = new byte[65536];

        while (true) {
            int n = readFully(gzis, header, 0, 512);
            if (n < 512) break;
            boolean allZero = true;
            for (int i = 0; i < 512; i++) {
                if (header[i] != 0) { allZero = false; break; }
            }
            if (allZero) break;

            String name = parseTarString(header, 0, 100);
            String prefix = parseTarString(header, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            while (name.startsWith("./")) name = name.substring(2);
            long size = parseTarOctal(header, 124, 12);
            byte typeFlag = header[156];

            boolean needed = name.startsWith("lib") || name.startsWith("usr/lib") || name.equals("usr");

            if (needed && !name.isEmpty()) {
                File outFile = new File(destDir, name);
                if (typeFlag == '5' || name.endsWith("/")) {
                    outFile.mkdirs();
                } else if (typeFlag == '2') {
                    String linkTarget = parseTarString(header, 157, 100);
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                            java.nio.file.Files.deleteIfExists(outFile.toPath());
                            java.nio.file.Files.createSymbolicLink(outFile.toPath(), java.nio.file.Paths.get(linkTarget));
                        }
                    } catch (Exception ignored) {}
                } else if (typeFlag == '0' || typeFlag == 0) {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    FileOutputStream fos = new FileOutputStream(outFile);
                    long remaining = size;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(copyBuf.length, remaining);
                        int r = gzis.read(copyBuf, 0, toRead);
                        if (r == -1) break;
                        fos.write(copyBuf, 0, r);
                        remaining -= r;
                    }
                    fos.close();
                    outFile.setExecutable(true, false);
                } else {
                    skipBytes(gzis, size);
                }
            } else {
                skipBytes(gzis, size);
            }

            long pad = (512 - (size % 512)) % 512;
            if (pad > 0) skipBytes(gzis, pad);
        }
        gzis.close();
    }

    public synchronized boolean startIfInstalled() {
        if (HardwareTelemetry.pingLocalPort(8096, 150) > 0) {
            PocketCloudService.appendLog("[8096] Official Jellyfin Server ONLINE on :8096");
            return true;
        }
        File dir = getEngineDir();
        File jfBin = findBinaryRecursive(dir, "jellyfin", 4);
        if (jfBin == null || !jfBin.exists()) {
            PocketCloudService.appendLog("[8096] Jellyfin engine awaiting 1-tap setup inside Jellyfin tab.");
            return false;
        }

        ensureGlibcRootfs(dir, activeListener);

        File rootfs = new File(dir, "rootfs");
        File prootBin = new File(context.getApplicationInfo().nativeLibraryDir, "libproot.so");
        File dataDir = new File(dir, "data");
        File configDir = new File(dir, "config");
        File cacheDir = new File(dir, "cache");
        File logDir = new File(dir, "log");
        File tmpDir = new File(dir, "tmp");
        dataDir.mkdirs();
        configDir.mkdirs();
        cacheDir.mkdirs();
        logDir.mkdirs();
        tmpDir.mkdirs();

        File webDir = findWebDirRecursive(dir, 4);
        File ffmpegBin = findBinaryRecursive(dir, "ffmpeg", 4);
        String sdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath();

        try {
            java.util.List<String> cmd = new java.util.ArrayList<String>();
            if (prootBin.exists() && rootfs.exists()) {
                new File(rootfs, "dev").mkdirs();
                new File(rootfs, "proc").mkdirs();
                new File(rootfs, "sys").mkdirs();
                new File(rootfs, "media").mkdirs();
                new File(rootfs, "jf_engine").mkdirs();
                new File(rootfs, "tmp").mkdirs();

                cmd.add(prootBin.getAbsolutePath());
                cmd.add("--kill-on-exit");
                cmd.add("-0");
                cmd.add("-k");
                cmd.add("5.10.0");
                cmd.add("-r");
                cmd.add(rootfs.getAbsolutePath());
                cmd.add("-b");
                cmd.add("/dev:/dev");
                cmd.add("-b");
                cmd.add("/proc:/proc");
                cmd.add("-b");
                cmd.add("/sys:/sys");
                cmd.add("-b");
                cmd.add(tmpDir.getAbsolutePath() + ":/tmp");
                cmd.add("-b");
                cmd.add(dir.getAbsolutePath() + ":/jf_engine");
                cmd.add("-b");
                cmd.add(sdcardPath + ":/media");
                cmd.add("-w");
                cmd.add("/jf_engine");

                String relJf = jfBin.getAbsolutePath().replace(dir.getAbsolutePath(), "/jf_engine");
                String relJfDir = jfBin.getParentFile().getAbsolutePath().replace(dir.getAbsolutePath(), "/jf_engine");
                cmd.add("/lib/ld-linux-aarch64.so.1");
                cmd.add("--library-path");
                cmd.add("/usr/lib/aarch64-linux-gnu:/lib/aarch64-linux-gnu:/usr/lib:/lib:" + relJfDir);
                cmd.add(relJf);
                cmd.add("--datadir");
                cmd.add("/jf_engine/data");
                cmd.add("--configdir");
                cmd.add("/jf_engine/config");
                cmd.add("--cachedir");
                cmd.add("/jf_engine/cache");
                cmd.add("--logdir");
                cmd.add("/jf_engine/log");
                if (webDir != null && webDir.exists()) {
                    cmd.add("--webdir");
                    cmd.add(webDir.getAbsolutePath().replace(dir.getAbsolutePath(), "/jf_engine"));
                }
                if (ffmpegBin != null && ffmpegBin.exists()) {
                    cmd.add("--ffmpeg");
                    cmd.add(ffmpegBin.getAbsolutePath().replace(dir.getAbsolutePath(), "/jf_engine"));
                }
            } else {
                cmd.add(jfBin.getAbsolutePath());
                cmd.add("--datadir");
                cmd.add(dataDir.getAbsolutePath());
                cmd.add("--configdir");
                cmd.add(configDir.getAbsolutePath());
                cmd.add("--cachedir");
                cmd.add(cacheDir.getAbsolutePath());
                cmd.add("--logdir");
                cmd.add(logDir.getAbsolutePath());
                if (webDir != null && webDir.exists()) {
                    cmd.add("--webdir");
                    cmd.add(webDir.getAbsolutePath());
                }
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            pb.environment().put("HOME", "/jf_engine");
            pb.environment().put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
            pb.environment().put("TMPDIR", tmpDir.getAbsolutePath());
            File loaderFile = new File(context.getApplicationInfo().nativeLibraryDir, "libproot_loader.so");
            PocketCloudService.appendLog("[8096] Loader path=" + loaderFile.getAbsolutePath() + " exists=" + loaderFile.exists());
            if (loaderFile.exists()) {
                pb.environment().put("PROOT_LOADER", loaderFile.getAbsolutePath());
                pb.environment().put("PROOT_LOADER_32", loaderFile.getAbsolutePath());
            }
            pb.environment().put("PROOT_TMP_DIR", tmpDir.getAbsolutePath());
            pb.environment().put("PROOT_NO_SECCOMP", "1");
            pb.environment().put("DOTNET_SYSTEM_GLOBALIZATION_INVARIANT", "1");
            pb.environment().put("DOTNET_EnableWriteXorExecute", "0");
            pb.environment().put("DOTNET_EnableIoUring", "0");
            pb.environment().put("DOTNET_GCHeapHardLimit", "300000000");
            pb.environment().put("COMPlus_EnableDiagnostics", "0");
            String nativeDir = context.getApplicationInfo().nativeLibraryDir;
            pb.environment().put("LD_LIBRARY_PATH", nativeDir + ":/usr/lib/aarch64-linux-gnu:/lib/aarch64-linux-gnu:/usr/lib:/lib:/jf_engine");
            pb.environment().put("JELLYFIN_PublishedServerUrl", "http://" + HardwareTelemetry.detectLocalIpv4() + ":8096");
            pb.environment().put("ASPNETCORE_URLS", "http://0.0.0.0:8096");
            pb.environment().put("JELLYFIN_WEB_OPT", "true");
            pb.redirectErrorStream(true);

            jellyfinProcess = pb.start();
            pipeLogs(jellyfinProcess);
            PocketCloudService.appendLog("[8096] Official Jellyfin PRoot-Rootfs Daemon SPAWNED");
            return true;
        } catch (Exception e) {
            PocketCloudService.appendLog("[8096] Jellyfin Boot ERR: " + e.getMessage());
            return false;
        }
    }

    public synchronized void stop() {
        if (jellyfinProcess != null) {
            jellyfinProcess.destroy();
            jellyfinProcess = null;
        }
    }

    public void installOfficialJellyfinAsync(final InstallProgressListener listener) {
        this.activeListener = listener;
        if (installing) {
            if (listener != null) listener.onProgress(currentPercent, currentStatus);
            return;
        }
        installing = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    notifyProgress(listener, 5, "Resolving official Jellyfin ARM64 release...");
                    String downloadUrl = resolveLatestJellyfinArm64TarUrl();
                    PocketCloudService.appendLog("[8096] Fetching: " + downloadUrl);

                    File dir = getEngineDir();
                    File archive = new File(dir, "jellyfin-arm64.tar.gz");
                    downloadWithProgress(downloadUrl, archive, 10, 68, "Downloading Official Jellyfin ARM64", listener);

                    notifyProgress(listener, 72, "Extracting Jellyfin Server & Web UI...");
                    extractTarGz(archive, dir);
                    archive.delete();

                    // Also fetch static ARM64 ffmpeg for hardware/software transcoding
                    File existingFfmpeg = findBinaryRecursive(dir, "ffmpeg", 4);
                    if (existingFfmpeg == null) {
                        try {
                            notifyProgress(listener, 82, "Fetching static ARM64 FFmpeg transcoder...");
                            String ffUrl = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-linux-arm64";
                            File ffOut = new File(dir, "ffmpeg");
                            downloadWithProgress(ffUrl, ffOut, 82, 94, "Downloading FFmpeg ARM64", listener);
                            ffOut.setExecutable(true, false);
                        } catch (Exception ffErr) {
                            PocketCloudService.appendLog("[8096] Optional FFmpeg fetch skipped: " + ffErr.getMessage());
                        }
                    }

                    File jfBin = findBinaryRecursive(dir, "jellyfin", 4);
                    if (jfBin == null || !jfBin.exists()) {
                        installing = false;
                        if (listener != null) listener.onComplete(false, "Could not locate jellyfin binary after extraction.");
                        return;
                    }
                    jfBin.setExecutable(true, false);
                    try { new File(dir, ".installed_ok").createNewFile(); } catch (Exception ignored) {}

                    notifyProgress(listener, 96, "Warming up Jellyfin .NET Engine (:8096)...");
                    boolean booted = startIfInstalled();

                    boolean portReady = false;
                    for (int i = 0; i < 80; i++) {
                        if (HardwareTelemetry.pingLocalPort(8096, 200) > 0) {
                            portReady = true;
                            break;
                        }
                        Thread.sleep(500L);
                    }

                    installing = false;
                    InstallProgressListener targetL = activeListener != null ? activeListener : listener;
                    if (targetL != null) {
                        targetL.onComplete(booted && portReady, portReady ? "Official Jellyfin Server is LIVE!" : "Jellyfin booted — tap Refresh in 5s if still warming up.");
                    }
                } catch (Exception e) {
                    installing = false;
                    PocketCloudService.appendLog("[8096] Install Error: " + e.getMessage());
                    if (listener != null) {
                        listener.onComplete(false, "Install failed: " + e.getMessage());
                    }
                }
            }
        }, "Jellyfin-Installer").start();
    }

    private String resolveLatestJellyfinArm64TarUrl() {
        String[] indexPages = new String[]{
                "https://repo.jellyfin.org/files/server/portable/latest-stable/arm64/",
                "https://repo.jellyfin.org/files/server/linux/latest-stable/arm64/"
        };
        for (String baseDir : indexPages) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(baseDir).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder html = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) html.append(line);
                    br.close();
                    String page = html.toString();
                    int pos = 0;
                    String foundFile = null;
                    while ((pos = page.indexOf("href=\"", pos)) != -1) {
                        int qStart = pos + 6;
                        int qEnd = page.indexOf("\"", qStart);
                        if (qEnd > qStart) {
                            String href = page.substring(qStart, qEnd);
                            if (href.startsWith("jellyfin") && href.contains("arm64") && href.endsWith(".tar.gz")) {
                                foundFile = href;
                            }
                        }
                        pos = qStart;
                    }
                    if (foundFile != null) return baseDir + foundFile;
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }

        try {
            URL api = new URL("https://api.github.com/repos/jellyfin/jellyfin/releases/latest");
            HttpURLConnection conn = (HttpURLConnection) api.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject rel = new JSONObject(sb.toString());
            JSONArray assets = rel.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                String name = a.getString("name");
                if (name.contains("arm64") && name.endsWith(".tar.gz") && !name.contains("debug")) {
                    return a.getString("browser_download_url");
                }
            }
        } catch (Exception ignored) {}

        return "https://repo.jellyfin.org/files/server/linux/latest-stable/arm64/jellyfin_12.4-arm64.tar.gz";
    }

    private void downloadWithProgress(String urlStr, File dest, int startPct, int endPct, String label, InstallProgressListener listener) throws Exception {
        String currentUrl = urlStr;
        HttpURLConnection conn = null;
        for (int redirects = 0; redirects < 6; redirects++) {
            URL u = new URL(currentUrl);
            conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "PocketCloud-Android");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                currentUrl = conn.getHeaderField("Location");
                conn.disconnect();
                continue;
            }
            break;
        }
        if (conn == null) throw new Exception("Connection failed");

        long totalBytes = conn.getContentLengthLong();
        InputStream is = new BufferedInputStream(conn.getInputStream(), 65536);
        FileOutputStream fos = new FileOutputStream(dest);
        byte[] buf = new byte[65536];
        long downloaded = 0;
        int read;
        long lastUiUpdate = 0;

        while ((read = is.read(buf)) != -1) {
            fos.write(buf, 0, read);
            downloaded += read;
            long now = System.currentTimeMillis();
            if (now - lastUiUpdate > 350L) {
                lastUiUpdate = now;
                long mb = downloaded / (1024L * 1024L);
                if (totalBytes > 0) {
                    long totalMb = totalBytes / (1024L * 1024L);
                    int pct = startPct + (int) (((endPct - startPct) * downloaded) / totalBytes);
                    notifyProgress(listener, pct, label + " (" + mb + " / " + totalMb + " MB)");
                } else {
                    notifyProgress(listener, (startPct + endPct) / 2, label + " (" + mb + " MB)");
                }
            }
        }
        fos.flush();
        fos.close();
        is.close();
        conn.disconnect();
    }

    // Pure Java POSIX TAR.GZ extractor (Zero external tar dependency required)
    private void extractTarGz(File tarGzFile, File destDir) throws Exception {
        InputStream fis = new BufferedInputStream(new FileInputStream(tarGzFile), 65536);
        GZIPInputStream gzis = new GZIPInputStream(fis, 65536);
        byte[] header = new byte[512];
        byte[] copyBuf = new byte[65536];

        while (true) {
            int n = readFully(gzis, header, 0, 512);
            if (n < 512) break;

            boolean allZero = true;
            for (int i = 0; i < 512; i++) {
                if (header[i] != 0) { allZero = false; break; }
            }
            if (allZero) break;

            String name = parseTarString(header, 0, 100);
            String prefix = parseTarString(header, 345, 155);
            if (!prefix.isEmpty()) {
                name = prefix + "/" + name;
            }
            long size = parseTarOctal(header, 124, 12);
            byte typeFlag = header[156];

            File outFile = new File(destDir, name);
            if (typeFlag == '5' || name.endsWith("/")) {
                outFile.mkdirs();
            } else if (typeFlag == '0' || typeFlag == 0) {
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                FileOutputStream fos = new FileOutputStream(outFile);
                long remaining = size;
                while (remaining > 0) {
                    int toRead = (int) Math.min(copyBuf.length, remaining);
                    int r = gzis.read(copyBuf, 0, toRead);
                    if (r == -1) break;
                    fos.write(copyBuf, 0, r);
                    remaining -= r;
                }
                fos.close();
                outFile.setExecutable(true, false);
            } else {
                skipBytes(gzis, size);
            }

            long pad = (512 - (size % 512)) % 512;
            if (pad > 0) skipBytes(gzis, pad);
        }
        gzis.close();
    }

    private int readFully(InputStream is, byte[] buf, int off, int len) throws Exception {
        int total = 0;
        while (total < len) {
            int r = is.read(buf, off + total, len - total);
            if (r == -1) break;
            total += r;
        }
        return total;
    }

    private void skipBytes(InputStream is, long count) throws Exception {
        long skipped = 0;
        while (skipped < count) {
            long s = is.skip(count - skipped);
            if (s <= 0) {
                if (is.read() == -1) break;
                s = 1;
            }
            skipped += s;
        }
    }

    private String parseTarString(byte[] buf, int off, int len) {
        int end = off;
        int max = off + len;
        while (end < max && buf[end] != 0) end++;
        return new String(buf, off, end - off).trim();
    }

    private long parseTarOctal(byte[] buf, int off, int len) {
        String s = parseTarString(buf, off, len).trim();
        if (s.isEmpty()) return 0L;
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private File findBinaryRecursive(File dir, String targetName, int maxDepth) {
        if (dir == null || !dir.exists() || maxDepth < 0) return null;
        File direct = new File(dir, targetName);
        if (direct.exists() && direct.isFile()) return direct;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File c : children) {
            if (c.isDirectory()) {
                File found = findBinaryRecursive(c, targetName, maxDepth - 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private File findWebDirRecursive(File dir, int maxDepth) {
        if (dir == null || !dir.exists() || maxDepth < 0) return null;
        File direct = new File(dir, "jellyfin-web");
        if (direct.exists() && direct.isDirectory()) return direct;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File c : children) {
            if (c.isDirectory()) {
                File found = findWebDirRecursive(c, maxDepth - 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void pipeLogs(final Process p) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (!line.contains("--help")) {
                            lastProcessLine = line;
                        }
                        PocketCloudService.appendLog("[8096] " + line);
                    }
                } catch (Exception ignored) {}
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void notifyProgress(InstallProgressListener l, int pct, String msg) {
        currentPercent = pct;
        currentStatus = msg;
        InstallProgressListener target = activeListener != null ? activeListener : l;
        if (target != null) target.onProgress(pct, msg);
    }
}
