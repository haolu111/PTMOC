package com.ptmoc.dto;

import java.util.ArrayList;
import java.util.List;

public class RoutePlanDto {
    private String routeId;
    private String name;
    private int strategyCode;
    private long distanceMeters;
    private long durationSeconds;
    private int trafficLights;
    private String trafficSummary;
    private List<GeoPointDto> polyline = new ArrayList<>();

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getStrategyCode() { return strategyCode; }
    public void setStrategyCode(int strategyCode) { this.strategyCode = strategyCode; }
    public long getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(long distanceMeters) { this.distanceMeters = distanceMeters; }
    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
    public int getTrafficLights() { return trafficLights; }
    public void setTrafficLights(int trafficLights) { this.trafficLights = trafficLights; }
    public String getTrafficSummary() { return trafficSummary; }
    public void setTrafficSummary(String trafficSummary) { this.trafficSummary = trafficSummary; }
    public List<GeoPointDto> getPolyline() { return polyline; }
    public void setPolyline(List<GeoPointDto> polyline) { this.polyline = polyline; }
}
