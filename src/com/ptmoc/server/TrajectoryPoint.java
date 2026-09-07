package com.ptmoc.server;

/**
 * Trajectory point DTO for the JDK8 demo HTTP server (same shape as backend API).
 */
public class TrajectoryPoint {
    private double lat;
    private double lng;
    private long timestamp;
    private String time;

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}
