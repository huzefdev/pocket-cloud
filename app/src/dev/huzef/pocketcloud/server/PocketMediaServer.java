package dev.huzef.pocketcloud.server;

import android.os.Environment;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PocketMediaServer {

    private final int port;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private ExecutorService pool;

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
        }, "PocketMedia-Accept");
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
            socket.setSoTimeout(15000);
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
                String encoded = rawPath.substring("/stream?file=".length());
                String filePath = URLDecoder.decode(encoded, "UTF-8");
                serveMediaFile(new File(filePath), rangeHeader, out);
            } else if (rawPath.startsWith("/watch?file=")) {
                String encoded = rawPath.substring("/watch?file=".length());
                String filePath = URLDecoder.decode(encoded, "UTF-8");
                servePlayerHtml(new File(filePath), out);
            } else {
                serveDashboardHtml(out);
            }
            out.flush();
        } catch (Exception ignored) {
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private List<File> scanMediaLibrary() {
        List<File> media = new ArrayList<File>();
        File root = Environment.getExternalStorageDirectory();
        if (root == null || !root.exists()) return media;

        File[] priorityDirs = new File[]{
                new File(root, "Movies"),
                new File(root, "Download"),
                new File(root, "DCIM/Camera"),
                new File(root, "Music"),
                new File(root, "Video")
        };
        for (File dir : priorityDirs) {
            scanDirRecursive(dir, media, 0, 2);
            if (media.size() >= 80) break;
        }
        return media;
    }

    private void scanDirRecursive(File dir, List<File> out, int depth, int maxDepth) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || depth > maxDepth || out.size() >= 80) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                scanDirRecursive(f, out, depth + 1, maxDepth);
            } else if (isMediaFile(f.getName())) {
                out.add(f);
            }
        }
    }

    private boolean isMediaFile(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm")
                || n.endsWith(".mov") || n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".wav");
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

    private void serveDashboardHtml(OutputStream out) throws Exception {
        List<File> files = scanMediaLibrary();
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("<title>PocketMedia // :8096</title>")
          .append("<style>")
          .append("body{margin:0;background:#0B0B0D;color:#F8FAFC;font-family:system-ui,-apple-system,sans-serif;padding:20px;}")
          .append(".hdr{display:flex;justify-content:space-between;align-items:center;padding:16px 20px;background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.12);border-radius:20px;margin-bottom:20px;}")
          .append(".badge{background:#EA580C;color:#fff;padding:4px 12px;border-radius:99px;font-size:12px;font-weight:700;}")
          .append(".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:14px;}")
          .append(".card{background:linear-gradient(145deg,#16161A,#1E1815);border:1px solid rgba(234,88,12,0.35);border-radius:18px;padding:16px;text-decoration:none;color:#F8FAFC;display:block;}")
          .append(".title{font-weight:700;font-size:15px;margin-bottom:6px;word-break:break-word;}")
          .append(".meta{font-size:12px;color:#9CA3AF;}")
          .append("</style></head><body>")
          .append("<div class='hdr'><div><div style='font-size:18px;font-weight:800;'>PocketMedia Engine</div>")
          .append("<div style='font-size:12px;color:#9CA3AF;'>Zero-PRoot Native Hardware Streamer</div></div>")
          .append("<span class='badge'>").append(files.size()).append(" MEDIA</span></div>");

        if (files.isEmpty()) {
            sb.append("<div style='padding:32px;text-align:center;color:#9CA3AF;background:#16161A;border-radius:20px;'>")
              .append("No media files indexed yet. Place videos or audio in <b>/sdcard/Movies</b> or <b>/sdcard/Download</b>.</div>");
        } else {
            sb.append("<div class='grid'>");
            for (File f : files) {
                String enc = URLEncoder.encode(f.getAbsolutePath(), "UTF-8");
                long mb = Math.max(1L, f.length() / (1024L * 1024L));
                sb.append("<a class='card' href='/watch?file=").append(enc).append("'>")
                  .append("<div class='title'>").append(escapeHtml(f.getName())).append("</div>")
                  .append("<div class='meta'>").append(mb).append(" MB • Direct Range Stream</div></a>");
            }
            sb.append("</div>");
        }
        sb.append("</body></html>");

        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(body);
    }

    private void servePlayerHtml(File file, OutputStream out) throws Exception {
        String enc = URLEncoder.encode(file.getAbsolutePath(), "UTF-8");
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + escapeHtml(file.getName()) + "</title>"
                + "<style>body{margin:0;background:#0B0B0D;color:#fff;font-family:sans-serif;display:flex;flex-direction:column;height:100vh;}"
                + ".top{padding:14px 18px;background:#16161A;display:flex;align-items:center;gap:12px;}"
                + "a{color:#EA580C;text-decoration:none;font-weight:700;}video{width:100%;flex:1;background:#000;}</style></head><body>"
                + "<div class='top'><a href='/'>← Library</a><span>" + escapeHtml(file.getName()) + "</span></div>"
                + "<video controls autoplay playsinline src='/stream?file=" + enc + "'></video></body></html>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(body);
    }

    private void serveMediaFile(File file, String rangeHeader, OutputStream out) throws Exception {
        if (!file.exists() || !file.isFile()) {
            String notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n";
            out.write(notFound.getBytes(StandardCharsets.UTF_8));
            return;
        }
        long fileLen = file.length();
        long start = 0;
        long end = fileLen - 1;
        boolean isPartial = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            isPartial = true;
            String rangeSpec = rangeHeader.substring(6);
            String[] bounds = rangeSpec.split("-");
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

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
