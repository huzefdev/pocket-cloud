package dev.huzef.pocketcloud.server;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PocketMediaServer {

    private final int port;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private final Map<String, byte[]> posterCache = new ConcurrentHashMap<String, byte[]>();
    private final Map<String, String> durationCache = new ConcurrentHashMap<String, String>();

    public PocketMediaServer(int port) {
        this.port = port;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        pool = Executors.newCachedThreadPool();
        Thread acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    while (running) {
                        final Socket client = serverSocket.accept();
                        pool.execute(new Runnable() {
                            @Override
                            public void run() {
                                handleClient(client);
                            }
                        });
                    }
                } catch (Exception ignored) {
                } finally {
                    running = false;
                }
            }
        }, "PocketJellyfin-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
            serverSocket = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    private void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(20000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                socket.close();
                return;
            }

            String[] parts = requestLine.split(" ");
            String rawPath = parts.length > 1 ? parts[1] : "/";
            String rangeHeader = null;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase(Locale.US).startsWith("range:")) {
                    rangeHeader = line.substring(6).trim();
                }
            }

            if (rawPath.startsWith("/stream?file=")) {
                String filePath = URLDecoder.decode(rawPath.substring("/stream?file=".length()), "UTF-8");
                serveMediaFile(new File(filePath), rangeHeader, out);
            } else if (rawPath.startsWith("/poster?file=")) {
                String filePath = URLDecoder.decode(rawPath.substring("/poster?file=".length()), "UTF-8");
                servePosterImage(new File(filePath), out);
            } else if (rawPath.startsWith("/watch?file=")) {
                String filePath = URLDecoder.decode(rawPath.substring("/watch?file=".length()), "UTF-8");
                serveCinemaPlayerHtml(new File(filePath), out);
            } else {
                serveJellyfinHomeHtml(out);
            }
            out.flush();
        } catch (Exception ignored) {
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private static class MediaItem {
        final File file;
        final String category;
        final String duration;
        final long sizeMb;
        final boolean isAudio;

        MediaItem(File file, String category, String duration, long sizeMb, boolean isAudio) {
            this.file = file;
            this.category = category;
            this.duration = duration;
            this.sizeMb = sizeMb;
            this.isAudio = isAudio;
        }
    }

    private List<MediaItem> scanMediaLibrary() {
        List<MediaItem> items = new ArrayList<MediaItem>();
        File root = Environment.getExternalStorageDirectory();
        if (root == null || !root.exists()) return items;

        Object[][] sources = new Object[][]{
                {new File(root, "Movies"), "movies"},
                {new File(root, "Download"), "movies"},
                {new File(root, "Video"), "movies"},
                {new File(root, "DCIM/Camera"), "camera"},
                {new File(root, "Music"), "music"}
        };
        for (Object[] src : sources) {
            scanDirRecursive((File) src[0], (String) src[1], items, 0, 2);
            if (items.size() >= 100) break;
        }
        return items;
    }

    private void scanDirRecursive(File dir, String category, List<MediaItem> out, int depth, int maxDepth) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || depth > maxDepth || out.size() >= 100) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                scanDirRecursive(f, category, out, depth + 1, maxDepth);
            } else if (isMediaFile(f.getName())) {
                boolean audio = isAudioFile(f.getName());
                String cat = audio ? "music" : category;
                long mb = Math.max(1L, f.length() / (1024L * 1024L));
                String dur = getFormattedDuration(f);
                out.add(new MediaItem(f, cat, dur, mb, audio));
            }
        }
    }

    private String getFormattedDuration(File f) {
        String path = f.getAbsolutePath();
        String cached = durationCache.get(path);
        if (cached != null) return cached;
        String result = isAudioFile(f.getName()) ? "AUDIO" : "HD VIDEO";
        MediaMetadataRetriever mmr = null;
        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(path);
            String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durStr != null) {
                long ms = Long.parseLong(durStr);
                long totalSec = ms / 1000L;
                long h = totalSec / 3600L;
                long m = (totalSec % 3600L) / 60L;
                long s = totalSec % 60L;
                if (h > 0) {
                    result = String.format(Locale.US, "%d:%02d:%02d", h, m, s);
                } else {
                    result = String.format(Locale.US, "%02d:%02d", m, s);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (mmr != null) {
                try { mmr.release(); } catch (Exception ignored) {}
            }
        }
        durationCache.put(path, result);
        return result;
    }

    private void servePosterImage(File file, OutputStream out) throws Exception {
        String key = file.getAbsolutePath();
        byte[] jpeg = posterCache.get(key);
        if (jpeg == null && file.exists() && !isAudioFile(file.getName())) {
            try {
                Bitmap bmp = ThumbnailUtils.createVideoThumbnail(key, MediaStore.Images.Thumbnails.MINI_KIND);
                if (bmp != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 78, baos);
                    jpeg = baos.toByteArray();
                    bmp.recycle();
                    if (posterCache.size() > 60) posterCache.clear();
                    posterCache.put(key, jpeg);
                }
            } catch (Exception ignored) {}
        }

        if (jpeg != null && jpeg.length > 0) {
            String hdr = "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nCache-Control: max-age=3600\r\nContent-Length: " + jpeg.length + "\r\nConnection: close\r\n\r\n";
            out.write(hdr.getBytes(StandardCharsets.UTF_8));
            out.write(jpeg);
        } else {
            // 1x1 dark fallback SVG poster
            String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='320' height='180'><rect width='320' height='180' fill='#18181D'/><circle cx='160' cy='90' r='26' fill='#EA580C'/><polygon points='154,78 174,90 154,102' fill='#FFFFFF'/></svg>";
            byte[] b = svg.getBytes(StandardCharsets.UTF_8);
            String hdr = "HTTP/1.1 200 OK\r\nContent-Type: image/svg+xml\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
            out.write(hdr.getBytes(StandardCharsets.UTF_8));
            out.write(b);
        }
    }

    private boolean isMediaFile(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm")
                || n.endsWith(".mov") || n.endsWith(".avi") || isAudioFile(n);
    }

    private boolean isAudioFile(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".wav") || n.endsWith(".flac") || n.endsWith(".ogg");
    }

    private String getMimeType(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mkv")) return "video/x-matroska";
        if (n.endsWith(".mov")) return "video/quicktime";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".m4a")) return "audio/mp4";
        if (n.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    private void serveJellyfinHomeHtml(OutputStream out) throws Exception {
        List<MediaItem> items = scanMediaLibrary();
        MediaItem featured = null;
        for (MediaItem m : items) {
            if (!m.isAudio) { featured = m; break; }
        }
        if (featured == null && !items.isEmpty()) featured = items.get(0);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("<title>Jellyfin // PocketMedia :8096</title>")
          .append("<style>")
          .append("*{box-sizing:border-box;}")
          .append("body{margin:0;background:#0B0B0D;color:#F8FAFC;font-family:system-ui,-apple-system,sans-serif;padding-bottom:40px;}")
          .append(".nav{position:sticky;top:0;z-index:20;display:flex;align-items:center;gap:12px;padding:14px 20px;background:rgba(11,11,13,0.88);backdrop-filter:blur(16px);border-bottom:1px solid rgba(255,255,255,0.08);}")
          .append(".logo{display:flex;align-items:center;gap:8px;font-weight:900;font-size:18px;letter-spacing:0.5px;}")
          .append(".dot{width:10px;height:10px;border-radius:50%;background:#EA580C;box-shadow:0 0 12px #EA580C;}")
          .append(".search{flex:1;max-width:340px;margin-left:auto;background:rgba(255,255,255,0.06);border:1px solid rgba(255,255,255,0.12);border-radius:99px;padding:8px 14px;color:#fff;font-size:13px;outline:none;}")
          .append(".hero{position:relative;margin:18px 20px;border-radius:24px;overflow:hidden;border:1px solid rgba(234,88,12,0.45);background:linear-gradient(135deg,#1C1512,#121216);min-height:200px;display:flex;align-items:flex-end;}")
          .append(".hero img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:0.42;}")
          .append(".hero-grad{position:absolute;inset:0;background:linear-gradient(to top,#0B0B0D 12%,rgba(11,11,13,0.45) 60%,transparent 100%);}")
          .append(".hero-body{position:relative;z-index:2;padding:22px;width:100%;}")
          .append(".pill{display:inline-block;background:rgba(234,88,12,0.25);border:1px solid #EA580C;color:#F97316;font-size:11px;font-weight:700;padding:4px 10px;border-radius:99px;margin-bottom:8px;}")
          .append(".hero-title{font-size:21px;font-weight:800;margin:0 0 12px 0;word-break:break-word;}")
          .append(".btn-play{display:inline-flex;align-items:center;gap:8px;background:#EA580C;color:#fff;text-decoration:none;font-weight:700;font-size:13px;padding:10px 20px;border-radius:99px;box-shadow:0 6px 20px rgba(234,88,12,0.4);}")
          .append(".tabs{display:flex;gap:8px;overflow-x:auto;padding:6px 20px 16px 20px;}")
          .append(".tab{background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.1);color:#9CA3AF;padding:7px 15px;border-radius:99px;font-size:12px;font-weight:700;cursor:pointer;white-space:nowrap;}")
          .append(".tab.active{background:#EA580C;color:#fff;border-color:#F97316;}")
          .append(".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:16px;padding:0 20px;}")
          .append(".card{background:#151519;border:1px solid rgba(255,255,255,0.09);border-radius:18px;overflow:hidden;text-decoration:none;color:#F8FAFC;display:flex;flex-direction:column;transition:transform .15s ease,border-color .15s ease;}")
          .append(".card:hover{transform:translateY(-2px);border-color:#EA580C;}")
          .append(".thumb-wrap{position:relative;aspect-ratio:16/9;background:#1C1C22;overflow:hidden;}")
          .append(".thumb-wrap img{width:100%;height:100%;object-fit:cover;}")
          .append(".dur{position:absolute;right:8px;bottom:8px;background:rgba(0,0,0,0.78);color:#fff;font-size:10px;font-weight:700;padding:3px 7px;border-radius:6px;font-family:monospace;}")
          .append(".cbody{padding:12px 14px;}")
          .append(".ctitle{font-size:13px;font-weight:700;line-height:1.35;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;}")
          .append(".cmeta{font-size:11px;color:#9CA3AF;margin-top:5px;font-family:monospace;}")
          .append("</style></head><body>")
          .append("<div class='nav'><div class='logo'><span class='dot'></span><span>JELLYFIN // POCKET</span></div>")
          .append("<input class='search' id='q' placeholder='Search movies, videos, audio...' oninput='filterCards()'></div>");

        if (featured != null) {
            String fEnc = URLEncoder.encode(featured.file.getAbsolutePath(), "UTF-8");
            sb.append("<div class='hero'>")
              .append("<img src='/poster?file=").append(fEnc).append("' alt=''>")
              .append("<div class='hero-grad'></div>")
              .append("<div class='hero-body'><span class='pill'>FEATURED • ").append(featured.duration).append(" • ").append(featured.sizeMb).append(" MB</span>")
              .append("<div class='hero-title'>").append(escapeHtml(cleanTitle(featured.file.getName()))).append("</div>")
              .append("<a class='btn-play' href='/watch?file=").append(fEnc).append("'>▶ Stream Now</a>")
              .append("</div></div>");
        }

        sb.append("<div class='tabs'>")
          .append("<button class='tab active' onclick=\"setCat('all',this)\">All Media (").append(items.size()).append(")</button>")
          .append("<button class='tab' onclick=\"setCat('movies',this)\">Movies & Downloads</button>")
          .append("<button class='tab' onclick=\"setCat('camera',this)\">Camera & Clips</button>")
          .append("<button class='tab' onclick=\"setCat('music',this)\">Music & Audio</button>")
          .append("</div>");

        if (items.isEmpty()) {
            sb.append("<div style='margin:20px;padding:34px;text-align:center;color:#9CA3AF;background:#151519;border:1px solid rgba(255,255,255,0.08);border-radius:20px;'>")
              .append("Library is empty. Drop MP4/MKV/MP3 files into <b>/sdcard/Movies</b>, <b>/sdcard/Download</b>, or <b>/sdcard/Music</b> and refresh.</div>");
        } else {
            sb.append("<div class='grid' id='grid'>");
            for (MediaItem m : items) {
                String enc = URLEncoder.encode(m.file.getAbsolutePath(), "UTF-8");
                String title = cleanTitle(m.file.getName());
                sb.append("<a class='card' data-cat='").append(m.category).append("' data-name='").append(escapeHtml(title.toLowerCase(Locale.US))).append("' href='/watch?file=").append(enc).append("'>")
                  .append("<div class='thumb-wrap'><img loading='lazy' src='/poster?file=").append(enc).append("' alt=''>")
                  .append("<span class='dur'>").append(m.duration).append("</span></div>")
                  .append("<div class='cbody'><div class='ctitle'>").append(escapeHtml(title)).append("</div>")
                  .append("<div class='cmeta'>").append(m.sizeMb).append(" MB • ").append(m.category.toUpperCase(Locale.US)).append("</div></div></a>");
            }
            sb.append("</div>");
        }

        sb.append("<script>")
          .append("var curCat='all';")
          .append("function setCat(c,el){curCat=c;document.querySelectorAll('.tab').forEach(function(b){b.classList.remove('active');});el.classList.add('active');filterCards();}")
          .append("function filterCards(){var q=document.getElementById('q').value.toLowerCase();document.querySelectorAll('.card').forEach(function(c){var okCat=(curCat==='all'||c.dataset.cat===curCat);var okQ=(!q||c.dataset.name.indexOf(q)!==-1);c.style.display=(okCat&&okQ)?'flex':'none';});}")
          .append("</script></body></html>");

        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(body);
    }

    private void serveCinemaPlayerHtml(File file, OutputStream out) throws Exception {
        String enc = URLEncoder.encode(file.getAbsolutePath(), "UTF-8");
        String title = cleanTitle(file.getName());
        boolean audio = isAudioFile(file.getName());
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + escapeHtml(title) + "</title>"
                + "<style>body{margin:0;background:#0B0B0D;color:#F8FAFC;font-family:system-ui,sans-serif;display:flex;flex-direction:column;height:100vh;}"
                + ".top{padding:14px 18px;background:#141418;border-bottom:1px solid rgba(255,255,255,0.08);display:flex;align-items:center;gap:12px;}"
                + ".back{background:#EA580C;color:#fff;text-decoration:none;font-weight:700;font-size:12px;padding:7px 14px;border-radius:99px;}"
                + ".ttl{font-weight:700;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1;}"
                + ".stage{flex:1;display:flex;align-items:center;justify-content:center;background:#000;}"
                + "video,audio{width:100%;max-height:100%;outline:none;}</style></head><body>"
                + "<div class='top'><a class='back' href='/'>← Library</a><div class='ttl'>" + escapeHtml(title) + "</div></div>"
                + "<div class='stage'>"
                + (audio
                    ? "<audio id='plr' controls autoplay src='/stream?file=" + enc + "'></audio>"
                    : "<video id='plr' controls autoplay playsinline poster='/poster?file=" + enc + "' src='/stream?file=" + enc + "'></video>")
                + "</div>"
                + "<script>"
                + "var v=document.getElementById('plr'),k='jf_pos_'+" + org.json.JSONObject.quote(file.getName()) + ";"
                + "v.addEventListener('loadedmetadata',function(){var s=parseFloat(localStorage.getItem(k)||'0');if(s>5 && s<v.duration-10){v.currentTime=s;}});"
                + "setInterval(function(){if(!v.paused && v.currentTime>3){localStorage.setItem(k,String(v.currentTime));}},3000);"
                + "</script></body></html>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(body);
    }

    private void serveMediaFile(File file, String rangeHeader, OutputStream out) throws Exception {
        if (!file.exists() || !file.isFile()) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            return;
        }
        long fileLen = file.length();
        long start = 0;
        long end = fileLen - 1;
        boolean isPartial = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            isPartial = true;
            String[] bounds = rangeHeader.substring(6).split("-");
            try {
                if (!bounds[0].isEmpty()) start = Long.parseLong(bounds[0]);
                if (bounds.length > 1 && !bounds[1].isEmpty()) end = Long.parseLong(bounds[1]);
            } catch (NumberFormatException ignored) {}
            if (end >= fileLen) end = fileLen - 1;
        }

        long contentLen = Math.max(0, end - start + 1);
        String mime = getMimeType(file.getName());
        StringBuilder hdr = new StringBuilder();
        if (isPartial) {
            hdr.append("HTTP/1.1 206 Partial Content\r\n")
               .append("Content-Range: bytes ").append(start).append("-").append(end).append("/").append(fileLen).append("\r\n");
        } else {
            hdr.append("HTTP/1.1 200 OK\r\n");
        }
        hdr.append("Content-Type: ").append(mime).append("\r\n")
           .append("Accept-Ranges: bytes\r\n")
           .append("Content-Length: ").append(contentLen).append("\r\n")
           .append("Connection: close\r\n\r\n");
        out.write(hdr.toString().getBytes(StandardCharsets.UTF_8));

        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file), 65536);
            long skipped = 0;
            while (skipped < start) {
                long s = bis.skip(start - skipped);
                if (s <= 0) break;
                skipped += s;
            }
            byte[] buf = new byte[65536];
            long remaining = contentLen;
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int read = bis.read(buf, 0, toRead);
                if (read == -1) break;
                out.write(buf, 0, read);
                remaining -= read;
            }
        } finally {
            if (bis != null) try { bis.close(); } catch (Exception ignored) {}
        }
    }

    private String cleanTitle(String filename) {
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base.replace('.', ' ').replace('_', ' ');
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
