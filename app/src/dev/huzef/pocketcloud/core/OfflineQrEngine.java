package dev.huzef.pocketcloud.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Enumeration;

public final class OfflineQrEngine {

    private OfflineQrEngine() {}

    public static String getOfflineLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                String name = nif.getName().toLowerCase();
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                for (InetAddress addr : Collections.list(addrs)) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && (name.contains("wlan") || name.contains("ap") || name.contains("rndis") || ip.startsWith("192.168.") || ip.startsWith("10."))) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "192.168.43.1";
    }

    /**
     * Generates a clean high-contrast QR-styled visual matrix badge for offline LAN endpoint inspection.
     */
    public static Bitmap renderEndpointMatrix(String payload, int sizePx, int accentColor) {
        int grid = 25;
        boolean[][] modules = new boolean[grid][grid];

        // 3 Finder patterns (top-left, top-right, bottom-left)
        placeFinder(modules, 0, 0);
        placeFinder(modules, grid - 7, 0);
        placeFinder(modules, 0, grid - 7);

        byte[] stream;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h1 = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            byte[] h2 = md.digest(h1);
            stream = new byte[64];
            System.arraycopy(h1, 0, stream, 0, 32);
            System.arraycopy(h2, 0, stream, 32, 32);
        } catch (Exception e) {
            stream = payload.getBytes(StandardCharsets.UTF_8);
        }

        int bitIdx = 0;
        for (int y = 0; y < grid; y++) {
            for (int x = 0; x < grid; x++) {
                if (isFinderZone(x, y, grid)) continue;
                int b = stream[(bitIdx / 8) % stream.length] & 0xFF;
                modules[y][x] = ((b >> (bitIdx % 8)) & 1) == 1;
                bitIdx++;
            }
        }

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.parseColor("#F8FAFC"));

        Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkPaint.setColor(Color.parseColor("#0B0B0D"));

        Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        accentPaint.setColor(accentColor);

        float margin = sizePx * 0.08f;
        float cell = (sizePx - (margin * 2f)) / grid;

        for (int y = 0; y < grid; y++) {
            for (int x = 0; x < grid; x++) {
                if (!modules[y][x]) continue;
                float left = margin + x * cell;
                float top = margin + y * cell;
                boolean isCenterAccent = (x >= 10 && x <= 14 && y >= 10 && y <= 14);
                canvas.drawRoundRect(
                        left + 0.6f, top + 0.6f,
                        left + cell - 0.6f, top + cell - 0.6f,
                        cell * 0.22f, cell * 0.22f,
                        isCenterAccent ? accentPaint : darkPaint
                );
            }
        }
        return bmp;
    }

    private static void placeFinder(boolean[][] m, int ox, int oy) {
        for (int dy = 0; dy < 7; dy++) {
            for (int dx = 0; dx < 7; dx++) {
                boolean border = (dx == 0 || dx == 6 || dy == 0 || dy == 6);
                boolean core = (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4);
                m[oy + dy][ox + dx] = border || core;
            }
        }
    }

    private static boolean isFinderZone(int x, int y, int g) {
        return (x < 8 && y < 8) || (x >= g - 8 && y < 8) || (x < 8 && y >= g - 8);
    }
}
