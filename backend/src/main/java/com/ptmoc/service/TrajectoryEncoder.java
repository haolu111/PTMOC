package com.ptmoc.service;

import com.ptmoc.dto.TrajectoryPointDto;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轨迹编码服务：将轨迹点数据编码为BigInteger消息，供PTMOC加密使用
 */
@Service
public class TrajectoryEncoder {

    /**
     * 将用户轨迹与参考轨迹的偏差编码为BigInteger消息
     * 消息设计：
     *   x1 = 平均空间偏移 (单位: 厘米)
     *   x2 = 最大空间偏移 (单位: 厘米)
     *   x3 = 平均时间偏差 (单位: 秒)
     *   x4 = 最大时间偏差 (单位: 秒)
     */
    public Map<Integer, BigInteger> encodeTrajectoryDeviations(
            List<TrajectoryPointDto> userTrajectory,
            List<TrajectoryPointDto> referenceTrajectory) {

        int numPoints = Math.min(userTrajectory.size(), referenceTrajectory.size());
        if (numPoints == 0) {
            Map<Integer, BigInteger> messages = new LinkedHashMap<>();
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
            TrajectoryPointDto up = userTrajectory.get(i);
            TrajectoryPointDto rp = referenceTrajectory.get(i);

            // 空间偏移 (米)
            double latDiff = up.getLat() - rp.getLat();
            double lngDiff = up.getLng() - rp.getLng();
            double distance = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111000;
            totalSpatialDeviation += distance;
            maxSpatialDeviation = Math.max(maxSpatialDeviation, distance);

            // 时间偏差 (秒)
            double timeDiff = Math.abs(up.getTimestamp() - rp.getTimestamp()) / 1000.0;
            totalTimeDeviation += timeDiff;
            maxTimeDeviation = Math.max(maxTimeDeviation, timeDiff);
        }

        double avgSpatialDeviation = totalSpatialDeviation / numPoints;
        double avgTimeDeviation = totalTimeDeviation / numPoints;

        Map<Integer, BigInteger> messages = new LinkedHashMap<>();
        messages.put(1, BigInteger.valueOf(Math.round(avgSpatialDeviation * 100))); // 厘米
        messages.put(2, BigInteger.valueOf(Math.round(maxSpatialDeviation * 100)));
        messages.put(3, BigInteger.valueOf(Math.round(avgTimeDeviation)));           // 秒
        messages.put(4, BigInteger.valueOf(Math.round(maxTimeDeviation)));

        return messages;
    }

    /**
     * 构造验证函数定义字符串
     * f(x1,x2,x3,x4) = 100000 - x1*2 - x2 - x3*5 - x4
     * 偏差越小，结果越大（表示越接近）
     */
    public String buildVerificationFunction() {
        return "100000 - 2*x1 - x2 - 5*x3 - x4";
    }
}
