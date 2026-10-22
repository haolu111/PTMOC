package com.ptmoc.dto;

import java.util.List;

public class VerifyRequest {
    private List<TrajectoryPointDto> userTrajectory;
    private List<TrajectoryPointDto> referenceTrajectory;
    private int thresholdK = 3;
    private List<Integer> onlineServerIds = List.of(1, 2, 3, 4, 5);
    private boolean timeAnomaly;
    private String anomalyDesc;

    public List<TrajectoryPointDto> getUserTrajectory() { return userTrajectory; }
    public void setUserTrajectory(List<TrajectoryPointDto> v) { this.userTrajectory = v; }
    public List<TrajectoryPointDto> getReferenceTrajectory() { return referenceTrajectory; }
    public void setReferenceTrajectory(List<TrajectoryPointDto> v) { this.referenceTrajectory = v; }
    public int getThresholdK() { return thresholdK; }
    public void setThresholdK(int v) { this.thresholdK = v; }
    public List<Integer> getOnlineServerIds() { return onlineServerIds; }
    public void setOnlineServerIds(List<Integer> v) { this.onlineServerIds = v; }
    public boolean isTimeAnomaly() { return timeAnomaly; }
    public void setTimeAnomaly(boolean v) { this.timeAnomaly = v; }
    public String getAnomalyDesc() { return anomalyDesc; }
    public void setAnomalyDesc(String v) { this.anomalyDesc = v; }
}
