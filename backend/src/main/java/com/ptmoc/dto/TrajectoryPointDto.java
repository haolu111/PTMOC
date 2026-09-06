package com.ptmoc.dto;

public class TrajectoryPointDto {
    private double lat;
    private double lng;
    private long timestamp;
    private String time;

    public double getLat() { return lat; }
    public void setLat(double v) { this.lat = v; }
    public double getLng() { return lng; }
    public void setLng(double v) { this.lng = v; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long v) { this.timestamp = v; }
    public String getTime() { return time; }
    public void setTime(String v) { this.time = v; }
}
