package com.ptmoc.dto;

public class RoutePlanRequest {
    private GeoPointDto origin;
    private GeoPointDto destination;

    public GeoPointDto getOrigin() { return origin; }
    public void setOrigin(GeoPointDto origin) { this.origin = origin; }
    public GeoPointDto getDestination() { return destination; }
    public void setDestination(GeoPointDto destination) { this.destination = destination; }
}
