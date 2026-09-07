package com.ptmoc.server;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Same encoding logic as backend TrajectoryEncoder (no Spring dependency).
 */
public class TrajectoryEncoder {

    public Map<Integer, BigInteger> encodeTrajectoryDeviations(
            List<TrajectoryPoint> userTrajectory,
            List<TrajectoryPoint> referenceTrajectory) {

        int numPoints = Math.min(userTrajectory.size(), referenceTrajectory.size());
        if (numPoints == 0) {
            Map<Integer, BigInteger> messages = new LinkedHashMap<Integer, BigInteger>();
            messages.put(1, BigInteger.ZERO);
            messages.put(2, BigInteger.ZERO);
            messages.put(3, BigInteger.ZERO);
            messages.put(4, BigInteger.ZERO);
            return messages;
        }

        double totalSpatialDeviation = 0;
        double maxSpatialDeviation = 0;
        double totalTimeDeviation = 0;
        double maxTimeDeviation = 0;

        for (int i = 0; i < numPoints; i++) {
            TrajectoryPoint up = userTrajectory.get(i);
            TrajectoryPoint rp = referenceTrajectory.get(i);

            double latDiff = up.getLat() - rp.getLat();
            double lngDiff = up.getLng() - rp.getLng();
            double distance = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111000;
            totalSpatialDeviation += distance;
            maxSpatialDeviation = Math.max(maxSpatialDeviation, distance);

            double timeDiff = Math.abs(up.getTimestamp() - rp.getTimestamp()) / 1000.0;
            totalTimeDeviation += timeDiff;
            maxTimeDeviation = Math.max(maxTimeDeviation, timeDiff);
        }

        double avgSpatialDeviation = totalSpatialDeviation / numPoints;
        double avgTimeDeviation = totalTimeDeviation / numPoints;

        Map<Integer, BigInteger> messages = new LinkedHashMap<Integer, BigInteger>();
        messages.put(1, BigInteger.valueOf(Math.round(avgSpatialDeviation * 100)));
        messages.put(2, BigInteger.valueOf(Math.round(maxSpatialDeviation * 100)));
        messages.put(3, BigInteger.valueOf(Math.round(avgTimeDeviation)));
        messages.put(4, BigInteger.valueOf(Math.round(maxTimeDeviation)));
        return messages;
    }

    public String buildVerificationFunction() {
        return "100000 - 2*x1 - x2 - 5*x3 - x4";
    }
}
