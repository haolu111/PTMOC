package com.ptmoc.dto;

import java.util.List;
import java.util.Map;

public class VerifyResponse {
    private String verificationStatus;
    private double score;
    private Map<String, Object> metrics;
    private List<Map<String, Object>> abnormalPoints;
    private List<Map<String, Object>> abnormalSegments;
    private List<Map<String, Object>> timeAbnormalPoints;
    private List<Map<String, Object>> timeAbnormalSegments;
    private List<String> reasonCodes;
    private List<String> reasonDetails;
    private String anomalyDesc;
    private Map<String, Object> algorithmSummary;
    private List<Map<String, Object>> steps;
    private long totalDuration;
    private Object cryptoResult;

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String v) { this.verificationStatus = v; }
    public double getScore() { return score; }
    public void setScore(double v) { this.score = v; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> v) { this.metrics = v; }
    public List<Map<String, Object>> getAbnormalPoints() { return abnormalPoints; }
    public void setAbnormalPoints(List<Map<String, Object>> v) { this.abnormalPoints = v; }
    public List<Map<String, Object>> getAbnormalSegments() { return abnormalSegments; }
    public void setAbnormalSegments(List<Map<String, Object>> v) { this.abnormalSegments = v; }
    public List<Map<String, Object>> getTimeAbnormalPoints() { return timeAbnormalPoints; }
    public void setTimeAbnormalPoints(List<Map<String, Object>> v) { this.timeAbnormalPoints = v; }
    public List<Map<String, Object>> getTimeAbnormalSegments() { return timeAbnormalSegments; }
    public void setTimeAbnormalSegments(List<Map<String, Object>> v) { this.timeAbnormalSegments = v; }
    public List<String> getReasonCodes() { return reasonCodes; }
    public void setReasonCodes(List<String> v) { this.reasonCodes = v; }
    public List<String> getReasonDetails() { return reasonDetails; }
    public void setReasonDetails(List<String> v) { this.reasonDetails = v; }
    public String getAnomalyDesc() { return anomalyDesc; }
    public void setAnomalyDesc(String v) { this.anomalyDesc = v; }
    public Map<String, Object> getAlgorithmSummary() { return algorithmSummary; }
    public void setAlgorithmSummary(Map<String, Object> v) { this.algorithmSummary = v; }
    public List<Map<String, Object>> getSteps() { return steps; }
    public void setSteps(List<Map<String, Object>> v) { this.steps = v; }
    public long getTotalDuration() { return totalDuration; }
    public void setTotalDuration(long v) { this.totalDuration = v; }
    public Object getCryptoResult() { return cryptoResult; }
    public void setCryptoResult(Object v) { this.cryptoResult = v; }
}
