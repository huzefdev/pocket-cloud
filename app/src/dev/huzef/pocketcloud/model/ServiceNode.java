package dev.huzef.pocketcloud.model;

public class ServiceNode {
    public final String id;
    public final String title;
    public final String subtitle;
    public final String scheduleSlot;
    public final int port;
    public final String credentialsHint;
    public boolean online;
    public long latencyMs;

    public ServiceNode(String id, String title, String subtitle, String scheduleSlot, int port, String credentialsHint) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.scheduleSlot = scheduleSlot;
        this.port = port;
        this.credentialsHint = credentialsHint;
        this.online = false;
        this.latencyMs = -1;
    }
}
