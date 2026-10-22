package com.ptmoc.service;

import com.ptmoc.dto.VerifyRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Availability control for the single-process logical-node demonstration. */
public final class ThresholdPolicy {
    public static final int SERVER_COUNT = 5;
    private ThresholdPolicy() { }

    public static List<Integer> selectNodes(VerifyRequest request) {
        int k = request.getThresholdK();
        List<Integer> online = request.getOnlineServerIds();
        if (k < 2 || k > SERVER_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码学门限 k 必须在 2 到 5 之间");
        }
        if (online == null || online.stream().anyMatch(id -> id == null || id < 1 || id > SERVER_COUNT)
                || new HashSet<>(online).size() != online.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点编号必须是 1 到 5 之间不重复的整数");
        }
        if (online.size() < k) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "在线节点 " + online.size() + "/5，不足密码学门限 k=" + k + "；禁止执行验证与结果恢复");
        }
        if (request.getUserTrajectory() == null || request.getReferenceTrajectory() == null
                || request.getUserTrajectory().size() < 2 || request.getReferenceTrajectory().size() < 2
                || request.getUserTrajectory().size() > 5000 || request.getReferenceTrajectory().size() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "两条轨迹各需 2 到 5000 个已采集点");
        }
        var points = new ArrayList<>(request.getUserTrajectory());
        points.addAll(request.getReferenceTrajectory());
        if (points.stream().anyMatch(p -> p == null || !Double.isFinite(p.getLat()) || !Double.isFinite(p.getLng())
                || Math.abs(p.getLat()) > 90 || Math.abs(p.getLng()) > 180)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "轨迹包含无效坐标");
        }
        return online.stream().sorted().limit(k).toList();
    }
}
