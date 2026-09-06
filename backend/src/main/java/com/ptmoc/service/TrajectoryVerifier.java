package com.ptmoc.service;

import com.ptmoc.dto.TrajectoryPointDto;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 轨迹验证服务：对比用户轨迹与参考轨迹，识别异常段
 * 逻辑与前端 ptmocSimulator.js 的 generateVerificationResult 保持一致
 */
@Service
public class TrajectoryVerifier {

    private static final double SPATIAL_THRESHOLD = 50;   // 米
    private static final double TIME_THRESHOLD_MIN = 5;    // 分钟

    public VerificationResult verify(
            List<TrajectoryPointDto> userTrajectory,
            List<TrajectoryPointDto> referenceTrajectory,
            boolean timeAnomaly,
            String anomalyDesc) {

        int numPoints = Math.min(userTrajectory.size(), referenceTrajectory.size());

        // 1. 空间偏移
        List<Double> deviations = new ArrayList<>();
        List<Map<String, Object>> spatialAbnormalPoints = new ArrayList<>();
        List<Map<String, Object>> abnormalSegments = new ArrayList<>();
        List<Map<String, Object>> matchedSegments = new ArrayList<>();

        boolean inAbnormalSegment = false;
        int segmentStart = -1;

        for (int i = 0; i < numPoints; i++) {
            double latDiff = userTrajectory.get(i).getLat() - referenceTrajectory.get(i).getLat();
            double lngDiff = userTrajectory.get(i).getLng() - referenceTrajectory.get(i).getLng();
            double distance = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111000;
            deviations.add(Math.round(distance * 100.0) / 100.0);

            boolean isAbnormal = distance > SPATIAL_THRESHOLD;
            if (isAbnormal) {
                Map<String, Object> pt = new LinkedHashMap<>();
                pt.put("index", i);
                pt.put("lat", userTrajectory.get(i).getLat());
                pt.put("lng", userTrajectory.get(i).getLng());
                pt.put("deviation", Math.round(distance * 100.0) / 100.0);
                pt.put("timestamp", userTrajectory.get(i).getTimestamp());
                pt.put("type", "spatial");
                spatialAbnormalPoints.add(pt);
                if (!inAbnormalSegment) {
                    segmentStart = i;
                    inAbnormalSegment = true;
                }
            } else {
                if (inAbnormalSegment) {
                    Map<String, Object> seg = new LinkedHashMap<>();
                    seg.put("start", segmentStart);
                    seg.put("end", i - 1);
                    seg.put("type", "spatial");
                    abnormalSegments.add(seg);
                    inAbnormalSegment = false;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("start", i);
                m.put("end", i);
                matchedSegments.add(m);
            }
        }
        if (inAbnormalSegment) {
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("start", segmentStart);
            seg.put("end", numPoints - 1);
            seg.put("type", "spatial");
            abnormalSegments.add(seg);
        }

        // 2. 时间偏差
        List<Double> timeDeviations = new ArrayList<>();
        List<Map<String, Object>> timeAbnormalPoints = new ArrayList<>();
        List<Map<String, Object>> timeAbnormalSegments = new ArrayList<>();
        boolean inTimeAbnormalSegment = false;
        int timeSegStart = -1;

        for (int i = 0; i < numPoints; i++) {
            double timeDiff = Math.abs(userTrajectory.get(i).getTimestamp() - referenceTrajectory.get(i).getTimestamp());
            double timeDiffMin = timeDiff / 60000.0;
            timeDeviations.add(Math.round(timeDiffMin * 100.0) / 100.0);

            boolean isTimeAbnormal = timeDiffMin > TIME_THRESHOLD_MIN;
            if (isTimeAbnormal) {
                Map<String, Object> pt = new LinkedHashMap<>();
                pt.put("index", i);
                pt.put("lat", userTrajectory.get(i).getLat());
                pt.put("lng", userTrajectory.get(i).getLng());
                pt.put("timeDeviation", Math.round(timeDiffMin * 100.0) / 100.0);
                pt.put("userTime", userTrajectory.get(i).getTime());
                pt.put("refTime", referenceTrajectory.get(i).getTime());
                pt.put("type", "time");
                timeAbnormalPoints.add(pt);
                if (!inTimeAbnormalSegment) {
                    timeSegStart = i;
                    inTimeAbnormalSegment = true;
                }
            } else {
                if (inTimeAbnormalSegment) {
                    Map<String, Object> seg = new LinkedHashMap<>();
                    seg.put("start", timeSegStart);
                    seg.put("end", i - 1);
                    seg.put("type", "time");
                    timeAbnormalSegments.add(seg);
                    inTimeAbnormalSegment = false;
                }
            }
        }
        if (inTimeAbnormalSegment) {
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("start", timeSegStart);
            seg.put("end", numPoints - 1);
            seg.put("type", "time");
            timeAbnormalSegments.add(seg);
        }

        // 合并
        List<Map<String, Object>> abnormalPoints = new ArrayList<>();
        abnormalPoints.addAll(spatialAbnormalPoints);
        abnormalPoints.addAll(timeAbnormalPoints);
        List<Map<String, Object>> allAbnormalSegments = new ArrayList<>();
        allAbnormalSegments.addAll(abnormalSegments);
        allAbnormalSegments.addAll(timeAbnormalSegments);

        // 合并 matched
        List<Map<String, Object>> mergedMatched = new ArrayList<>();
        Map<String, Object> current = null;
        for (Map<String, Object> seg : matchedSegments) {
            if (current != null && (int) seg.get("start") == (int) current.get("end") + 1) {
                current.put("end", seg.get("end"));
            } else {
                if (current != null) mergedMatched.add(current);
                current = new LinkedHashMap<>(seg);
            }
        }
        if (current != null) mergedMatched.add(current);

        // 3. 评分
        double avgDeviation = deviations.stream().mapToDouble(d -> d).average().orElse(0);
        double maxDeviation = deviations.stream().mapToDouble(d -> d).max().orElse(0);
        double spatialAbnormalRatio = (double) spatialAbnormalPoints.size() / numPoints;
        double spatialCoverageRatio = 1 - spatialAbnormalRatio;

        double avgTimeDeviation = timeDeviations.stream().mapToDouble(d -> d).average().orElse(0);
        double maxTimeDeviation = timeDeviations.stream().mapToDouble(d -> d).max().orElse(0);
        double timeAbnormalRatio = (double) timeAbnormalPoints.size() / numPoints;
        double timeCoverageRatio = 1 - timeAbnormalRatio;

        double spatialScore = spatialCoverageRatio * 40 + Math.max(0, 1 - avgDeviation / 200) * 20;
        double timeScore = timeCoverageRatio * 25 + Math.max(0, 1 - avgTimeDeviation / 30) * 15;
        double score = Math.round((spatialScore + timeScore) * 10.0) / 10.0;

        String verificationStatus;
        if (score >= 80) verificationStatus = "通过";
        else if (score >= 60) verificationStatus = "部分通过";
        else verificationStatus = "不通过";

        // 原因
        List<String> reasonCodes = new ArrayList<>();
        List<String> reasonDetails = new ArrayList<>();
        if (spatialAbnormalRatio > 0.3) {
            reasonCodes.add("DEVIATION_TOO_LARGE");
            reasonDetails.add("空间/时间偏移过大");
        }
        if (timeAbnormalRatio > 0.2) {
            reasonCodes.add("TIME_DEVIATION");
            reasonDetails.add("空间/时间偏移过大：" + (anomalyDesc != null ? anomalyDesc : String.format("平均时间偏差%.1f分钟", avgTimeDeviation)));
        }
        if (spatialAbnormalRatio > 0.1 && spatialAbnormalRatio <= 0.3) {
            reasonCodes.add("PARTIAL_DEVIATION");
        }
        if (reasonCodes.isEmpty() && score < 80) {
            reasonCodes.add("PARTIAL_DEVIATION");
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("avgDeviation", Math.round(avgDeviation * 100.0) / 100.0);
        metrics.put("maxDeviation", Math.round(maxDeviation * 100.0) / 100.0);
        metrics.put("coverageRatio", Math.round(spatialCoverageRatio * 1000.0) / 10.0);
        metrics.put("abnormalRatio", Math.round(spatialAbnormalRatio * 1000.0) / 10.0);
        metrics.put("avgTimeDeviation", Math.round(avgTimeDeviation * 100.0) / 100.0);
        metrics.put("maxTimeDeviation", Math.round(maxTimeDeviation * 100.0) / 100.0);
        metrics.put("timeCoverageRatio", Math.round(timeCoverageRatio * 1000.0) / 10.0);
        metrics.put("totalPoints", numPoints);
        metrics.put("spatialAbnormalCount", spatialAbnormalPoints.size());
        metrics.put("timeAbnormalCount", timeAbnormalPoints.size());

        VerificationResult result = new VerificationResult();
        result.verificationStatus = verificationStatus;
        result.score = score;
        result.matchedSegments = mergedMatched;
        result.abnormalSegments = allAbnormalSegments;
        result.abnormalPoints = abnormalPoints;
        result.timeAbnormalSegments = timeAbnormalSegments;
        result.timeAbnormalPoints = timeAbnormalPoints;
        result.reasonCodes = reasonCodes;
        result.reasonDetails = reasonDetails;
        result.anomalyDesc = anomalyDesc;
        result.metrics = metrics;
        return result;
    }

    public static class VerificationResult {
        public String verificationStatus;
        public double score;
        public List<Map<String, Object>> matchedSegments;
        public List<Map<String, Object>> abnormalSegments;
        public List<Map<String, Object>> abnormalPoints;
        public List<Map<String, Object>> timeAbnormalSegments;
        public List<Map<String, Object>> timeAbnormalPoints;
        public List<String> reasonCodes;
        public List<String> reasonDetails;
        public String anomalyDesc;
        public Map<String, Object> metrics;
    }
}
