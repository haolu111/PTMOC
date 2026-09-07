package com.ptmoc.dto;

public class LocationSuggestion {
    private String id;
    private String name;
    private String district;
    private String address;
    private double lng;
    private double lat;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
}
