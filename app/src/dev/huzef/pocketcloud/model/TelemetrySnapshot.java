package dev.huzef.pocketcloud.model;

public class TelemetrySnapshot {
    public String localIp = "127.0.0.1";
    public float batteryTempC = 31.0f;
    public int batteryLevel = 85;
    public boolean isCharging = false;
    public int ramUsedPercent = 40;
    public long ramUsedMb = 2400;
    public long ramTotalMb = 6144;
    public long rxKbps = 0;
    public long txKbps = 0;
    public boolean engineRunning = false;
    public boolean wakeLockHeld = false;
    public int activeDaemons = 0;
}
