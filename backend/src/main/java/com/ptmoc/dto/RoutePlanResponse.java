package com.ptmoc.dto;

import java.util.ArrayList;
import java.util.List;

public class RoutePlanResponse {
    private List<RoutePlanDto> routes = new ArrayList<>();

    public List<RoutePlanDto> getRoutes() { return routes; }
    public void setRoutes(List<RoutePlanDto> routes) { this.routes = routes; }
}
