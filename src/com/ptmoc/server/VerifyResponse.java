package com.ptmoc.server;

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
    private List<Map<String, Object>> processTrace;
    private String source = "ptmoc-jdk8-demo-server";

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    public List<Map<String, Object>> getAbnormalPoints() { return abnormalPoints; }
    public void setAbnormalPoints(List<Map<String, Object>> abnormalPoints) { this.abnormalPoints = abnormalPoints; }
    public List<Map<String, Object>> getAbnormalSegments() { return abnormalSegments; }
    public void setAbnormalSegments(List<Map<String, Object>> abnormalSegments) { this.abnormalSegments = abnormalSegments; }
    public List<Map<String, Object>> getTimeAbnormalPoints() { return timeAbnormalPoints; }
    public void setTimeAbnormalPoints(List<Map<String, Object>> timeAbnormalPoints) { this.timeAbnormalPoints = timeAbnormalPoints; }
    public List<Map<String, Object>> getTimeAbnormalSegments() { return timeAbnormalSegments; }
    public void setTimeAbnormalSegments(List<Map<String, Object>> timeAbnormalSegments) { this.timeAbnormalSegments = timeAbnormalSegments; }
    public List<String> getReasonCodes() { return reasonCodes; }
    public void setReasonCodes(List<String> reasonCodes) { this.reasonCodes = reasonCodes; }
    public List<String> getReasonDetails() { return reasonDetails; }
    public void setReasonDetails(List<String> reasonDetails) { this.reasonDetails = reasonDetails; }
    public String getAnomalyDesc() { return anomalyDesc; }
    public void setAnomalyDesc(String anomalyDesc) { this.anomalyDesc = anomalyDesc; }
    public Map<String, Object> getAlgorithmSummary() { return algorithmSummary; }
    public void setAlgorithmSummary(Map<String, Object> algorithmSummary) { this.algorithmSummary = algorithmSummary; }
    public List<Map<String, Object>> getSteps() { return steps; }
    public void setSteps(List<Map<String, Object>> steps) { this.steps = steps; }
    public long getTotalDuration() { return totalDuration; }
    public void setTotalDuration(long totalDuration) { this.totalDuration = totalDuration; }
    public Object getCryptoResult() { return cryptoResult; }
    public void setCryptoResult(Object cryptoResult) { this.cryptoResult = cryptoResult; }
    public List<Map<String, Object>> getProcessTrace() { return processTrace; }
    public void setProcessTrace(List<Map<String, Object>> processTrace) { this.processTrace = processTrace; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
