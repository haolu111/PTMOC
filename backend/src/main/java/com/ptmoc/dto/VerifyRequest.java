package com.ptmoc.dto;

import java.util.List;

public class VerifyRequest {
    private List<TrajectoryPointDto> userTrajectory;
    private List<TrajectoryPointDto> referenceTrajectory;
    private int thresholdK;
    private boolean timeAnomaly;
    private String anomalyDesc;

    public List<TrajectoryPointDto> getUserTrajectory() { return userTrajectory; }
    public void setUserTrajectory(List<TrajectoryPointDto> v) { this.userTrajectory = v; }
    public List<TrajectoryPointDto> getReferenceTrajectory() { return referenceTrajectory; }
    public void setReferenceTrajectory(List<TrajectoryPointDto> v) { this.referenceTrajectory = v; }
    public int getThresholdK() { return thresholdK; }
    public void setThresholdK(int v) { this.thresholdK = v; }
    public boolean isTimeAnomaly() { return timeAnomaly; }
    public void setTimeAnomaly(boolean v) { this.timeAnomaly = v; }
    public String getAnomalyDesc() { return anomalyDesc; }
    public void setAnomalyDesc(String v) { this.anomalyDesc = v; }
}
