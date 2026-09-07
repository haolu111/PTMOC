package com.ptmoc.server;

import java.util.List;

public class VerifyRequest {
    private List<TrajectoryPoint> userTrajectory;
    private List<TrajectoryPoint> referenceTrajectory;
    private int thresholdK = 3;
    private boolean timeAnomaly;
    private String anomalyDesc;

    public List<TrajectoryPoint> getUserTrajectory() { return userTrajectory; }
    public void setUserTrajectory(List<TrajectoryPoint> userTrajectory) { this.userTrajectory = userTrajectory; }
    public List<TrajectoryPoint> getReferenceTrajectory() { return referenceTrajectory; }
    public void setReferenceTrajectory(List<TrajectoryPoint> referenceTrajectory) { this.referenceTrajectory = referenceTrajectory; }
    public int getThresholdK() { return thresholdK; }
    public void setThresholdK(int thresholdK) { this.thresholdK = thresholdK; }
    public boolean isTimeAnomaly() { return timeAnomaly; }
    public void setTimeAnomaly(boolean timeAnomaly) { this.timeAnomaly = timeAnomaly; }
    public String getAnomalyDesc() { return anomalyDesc; }
    public void setAnomalyDesc(String anomalyDesc) { this.anomalyDesc = anomalyDesc; }
}
