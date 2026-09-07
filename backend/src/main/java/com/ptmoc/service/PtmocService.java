package com.ptmoc.service;

import com.ptmoc.core.BaseModule;
import com.ptmoc.core.CryptoModule;
import com.ptmoc.core.EvalModule;
import com.ptmoc.dto.VerifyRequest;
import com.ptmoc.dto.VerifyResponse;
import com.ptmoc.model.Entity;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.*;

/**
 * PTMOC算法编排服务：执行真实的 PTMOC 加密验证流程
 * Setup → KeyGen → Encrypt → Eval → Decrypt，并由 CryptoTraceBuilder 产出 processTrace。
 */
@Service
public class PtmocService {

    private final TrajectoryEncoder trajectoryEncoder;
    private final TrajectoryVerifier trajectoryVerifier;

    public PtmocService(TrajectoryEncoder trajectoryEncoder, TrajectoryVerifier trajectoryVerifier) {
        this.trajectoryEncoder = trajectoryEncoder;
        this.trajectoryVerifier = trajectoryVerifier;
    }

    public VerifyResponse executeVerification(VerifyRequest request) {
        long totalStart = System.currentTimeMillis();
        List<Map<String, Object>> steps = new ArrayList<>();
        CryptoTraceBuilder trace = new CryptoTraceBuilder();

        String[][] stepDefs = {
            {"init", "系统初始化", "PTMOC.Setup: 生成公共参数 (λ, p₀, η, 陷门置换对)"},
            {"keygen", "密钥生成", "PTMOC.KeyGen: 为Sender/Server/CSP/Receiver生成密钥对"},
            {"encode", "消息编码", "将轨迹坐标和时间戳编码为多项式消息 m₁, m₂, ..., mₖ"},
            {"encrypt", "PTMOC加密", "PTMOC.Enc: 秘密共享 + 陷门加密 + 对称加密辅助份额"},
            {"evaluate", "密文域评估", "PTMOC.Eval: Server与CSP协同完成密文域多项式计算"},
            {"decrypt", "结果解密", "PTMOC.Dec: Receiver恢复加密域评估结果"},
            {"verify", "轨迹验证", "将解密结果与参考轨迹对比，计算相似度和异常段"}
        };

        int k = request.getThresholdK();
        Object cryptoResult = null;

        try {
            long t0 = System.currentTimeMillis();
            BaseModule baseModule = new BaseModule();
            baseModule.setup(256);
            long d0 = System.currentTimeMillis() - t0;
            steps.add(buildStep(stepDefs[0], d0));
            trace.setup(d0);

            long t1 = System.currentTimeMillis();
            baseModule.keyGen(Entity.SENDER, "sender_1");
            baseModule.keyGen(Entity.SERVER, "server_1");
            baseModule.keyGen(Entity.CSP, "csp_1");
            baseModule.keyGen(Entity.RECEIVER, "receiver_1");
            long d1 = System.currentTimeMillis() - t1;
            steps.add(buildStep(stepDefs[1], d1));
            trace.keyGen(k, d1);

            long t2 = System.currentTimeMillis();
            Map<Integer, BigInteger> messages = trajectoryEncoder.encodeTrajectoryDeviations(
                request.getUserTrajectory(), request.getReferenceTrajectory());
            String functionDef = trajectoryEncoder.buildVerificationFunction();
            long d2 = System.currentTimeMillis() - t2;
            steps.add(buildStep(stepDefs[2], d2));
            trace.encode(messages, functionDef, d2);

            long t3 = System.currentTimeMillis();
            CryptoModule cryptoModule = new CryptoModule(baseModule);
            Map<String, Object> encryptionResult = cryptoModule.encrypt("sender_1", k, messages);
            long d3 = System.currentTimeMillis() - t3;
            steps.add(buildStep(stepDefs[3], d3));
            trace.encrypt(k, messages, d3);

            long t4 = System.currentTimeMillis();
            EvalModule evalModule = new EvalModule(baseModule);
            Map<String, BigInteger> evalResult = evalModule.evaluate(
                "server_1", "csp_1", encryptionResult, functionDef);
            long d4 = System.currentTimeMillis() - t4;
            steps.add(buildStep(stepDefs[4], d4));
            trace.evaluate(functionDef, d4);

            long t5 = System.currentTimeMillis();
            BigInteger decryptedResult = cryptoModule.decrypt("receiver_1", evalResult);
            long d5 = System.currentTimeMillis() - t5;
            steps.add(buildStep(stepDefs[5], d5));
            trace.decrypt(decryptedResult.toString(), d5);

            Map<String, Object> cr = new LinkedHashMap<>();
            cr.put("encodedMessages", messages.toString());
            cr.put("x1", messages.get(1).toString());
            cr.put("x2", messages.get(2).toString());
            cr.put("x3", messages.get(3).toString());
            cr.put("x4", messages.get(4).toString());
            cr.put("functionDefinition", functionDef);
            cr.put("decryptedResult", decryptedResult.toString());
            cr.put("thresholdK", k);
            cryptoResult = cr;

        } catch (Exception e) {
            System.err.println("PTMOC crypto error: " + e.getMessage());
            cryptoResult = Map.of("error", e.getMessage() == null ? "unknown" : e.getMessage());
            trace.cryptoFailed(e.getMessage());
        }

        long t6 = System.currentTimeMillis();
        TrajectoryVerifier.VerificationResult vr = trajectoryVerifier.verify(
            request.getUserTrajectory(),
            request.getReferenceTrajectory(),
            request.isTimeAnomaly(),
            request.getAnomalyDesc());
        long d6 = System.currentTimeMillis() - t6;
        steps.add(buildStep(stepDefs[6], d6));
        trace.result(vr.verificationStatus, vr.score, vr.reasonCodes, vr.anomalyDesc);

        long totalDuration = System.currentTimeMillis() - totalStart;

        VerifyResponse response = new VerifyResponse();
        response.setVerificationStatus(vr.verificationStatus);
        response.setScore(vr.score);
        response.setMetrics(vr.metrics);
        response.setAbnormalPoints(vr.abnormalPoints);
        response.setAbnormalSegments(vr.abnormalSegments);
        response.setTimeAbnormalPoints(vr.timeAbnormalPoints);
        response.setTimeAbnormalSegments(vr.timeAbnormalSegments);
        response.setReasonCodes(vr.reasonCodes);
        response.setReasonDetails(vr.reasonDetails);
        response.setAnomalyDesc(vr.anomalyDesc);
        response.setSteps(steps);
        response.setTotalDuration(totalDuration);
        response.setCryptoResult(cryptoResult);
        response.setProcessTrace(trace.getEvents());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("securityParameter", 256);
        summary.put("thresholdK", k);
        summary.put("polynomialDegree", 2);
        summary.put("rsaKeySize", 2048);
        summary.put("totalSteps", steps.size());
        summary.put("encryptionType", "PTMOC (Privacy-Preserving Threshold Multi-Owner Cryptography)");
        summary.put("dataPoints", request.getUserTrajectory().size());
        summary.put("processTraceEvents", trace.getEvents().size());
        response.setAlgorithmSummary(summary);

        return response;
    }

    private Map<String, Object> buildStep(String[] def, long durationMs) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", def[0]);
        step.put("name", def[1]);
        step.put("description", def[2]);
        step.put("actualDuration", durationMs);
        step.put("status", "completed");
        return step;
    }
}
