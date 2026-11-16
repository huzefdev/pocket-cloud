package dev.huzef.pocketcloud.core;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.SystemClock;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collections;
import java.util.Enumeration;
import dev.huzef.pocketcloud.model.TelemetrySnapshot;

public final class HardwareTelemetry {

    private static long lastRxBytes = -1;
    private static long lastTxBytes = -1;
    private static long lastSampleTimeMs = 0;

    private HardwareTelemetry() {}

    public static TelemetrySnapshot collect(Context ctx, boolean engineRunning, boolean wakeLockHeld, int activeDaemons) {
        TelemetrySnapshot snap = new TelemetrySnapshot();
        snap.localIp = detectLocalIpv4();
        snap.engineRunning = engineRunning;
        snap.wakeLockHeld = wakeLockHeld;
        snap.activeDaemons = activeDaemons;

        // Battery thermal & charge state
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = ctx.registerReceiver(null, filter);
            if (batteryStatus != null) {
                int tempTenths = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310);
                snap.batteryTempC = tempTenths / 10.0f;
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 80);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                snap.batteryLevel = (scale > 0) ? (level * 100 / scale) : level;
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                snap.isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == BatteryManager.BATTERY_STATUS_FULL);
            }
        } catch (Exception ignored) {}

        // Real RAM usage
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long total = mi.totalMem / (1024L * 1024L);
                long avail = mi.availMem / (1024L * 1024L);
                long used = Math.max(0, total - avail);
                snap.ramTotalMb = total;
                snap.ramUsedMb = used;
                snap.ramUsedPercent = total > 0 ? (int) ((used * 100L) / total) : 40;
            }
        } catch (Exception ignored) {}

        // Real Network Throughput (KB/s)
        long now = SystemClock.elapsedRealtime();
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (rx >= 0 && tx >= 0) {
            if (lastSampleTimeMs > 0 && now > lastSampleTimeMs) {
                long dtMs = now - lastSampleTimeMs;
                snap.rxKbps = Math.max(0, ((rx - lastRxBytes) * 1000L) / (dtMs * 1024L));
                snap.txKbps = Math.max(0, ((tx - lastTxBytes) * 1000L) / (dtMs * 1024L));
            }
            lastRxBytes = rx;
            lastTxBytes = tx;
            lastSampleTimeMs = now;
        }

        return snap;
    }

    public static String detectLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return "127.0.0.1";
            String fallbackIp = null;
            for (NetworkInterface intf : Collections.list(interfaces)) {
                if (!intf.isUp() || intf.isLoopback()) continue;
                String name = intf.getName().toLowerCase();
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (name.contains("wlan") || name.contains("ap") || name.contains("eth")) {
                            return ip;
                        }
                        if (fallbackIp == null) fallbackIp = ip;
                    }
                }
            }
            if (fallbackIp != null) return fallbackIp;
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public static long pingLocalPort(int port, int timeoutMs) {
        long start = SystemClock.elapsedRealtime();
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), timeoutMs);
            long elapsed = SystemClock.elapsedRealtime() - start;
            return Math.max(1L, elapsed);
        } catch (Exception e) {
            return -1L;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }
}
