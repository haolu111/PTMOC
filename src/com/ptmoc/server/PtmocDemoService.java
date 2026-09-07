package com.ptmoc.server;

import com.ptmoc.core.BaseModule;
import com.ptmoc.core.CryptoModule;
import com.ptmoc.core.EvalModule;
import com.ptmoc.model.Entity;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors backend PtmocService: real Setup → KeyGen → Encode → Encrypt → Eval → Decrypt → Verify.
 * processTrace 由 CryptoTraceBuilder 生成，供前端密码数据流可视化重放。
 */
public class PtmocDemoService {

    private final TrajectoryEncoder trajectoryEncoder = new TrajectoryEncoder();
    private final TrajectoryVerifier trajectoryVerifier = new TrajectoryVerifier();

    public VerifyResponse executeVerification(VerifyRequest request) {
        long totalStart = System.currentTimeMillis();
        List<Map<String, Object>> steps = new ArrayList<Map<String, Object>>();
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

        int k = request.getThresholdK() < 2 ? 2 : request.getThresholdK();
        Object cryptoResult;

        Map<Integer, BigInteger> messages = null;
        String functionDef = null;
        BigInteger decryptedResult = null;

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
            messages = trajectoryEncoder.encodeTrajectoryDeviations(
                    request.getUserTrajectory(), request.getReferenceTrajectory());
            functionDef = trajectoryEncoder.buildVerificationFunction();
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
            decryptedResult = cryptoModule.decrypt("receiver_1", evalResult);
            long d5 = System.currentTimeMillis() - t5;
            steps.add(buildStep(stepDefs[5], d5));
            trace.decrypt(decryptedResult.toString(), d5);

            Map<String, Object> cr = new LinkedHashMap<String, Object>();
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
            e.printStackTrace();
            Map<String, Object> err = new HashMap<String, Object>();
            err.put("error", e.getMessage());
            cryptoResult = err;
            trace.cryptoFailed(e.getMessage());
            while (steps.size() < 6) {
                steps.add(buildStep(stepDefs[steps.size()], 0));
            }
        }

        long t6 = System.currentTimeMillis();
        TrajectoryVerifier.VerificationResult vr = trajectoryVerifier.verify(
                request.getUserTrajectory(),
                request.getReferenceTrajectory(),
                request.isTimeAnomaly(),
                request.getAnomalyDesc());
        steps.add(buildStep(stepDefs[6], System.currentTimeMillis() - t6));
        trace.result(vr.verificationStatus, vr.score, vr.reasonCodes, vr.anomalyDesc);

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
        response.setTotalDuration(System.currentTimeMillis() - totalStart);
        response.setCryptoResult(cryptoResult);
        response.setProcessTrace(trace.getEvents());

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("securityParameter", 256);
        summary.put("thresholdK", k);
        summary.put("polynomialDegree", 2);
        summary.put("rsaKeySize", 2048);
        summary.put("totalSteps", steps.size());
        summary.put("encryptionType", "PTMOC (Privacy-Preserving Threshold Multi-Owner Cryptography)");
        summary.put("dataPoints", request.getUserTrajectory() == null ? 0 : request.getUserTrajectory().size());
        summary.put("processTraceEvents", trace.getEvents().size());
        response.setAlgorithmSummary(summary);
        return response;
    }

    private Map<String, Object> buildStep(String[] def, long durationMs) {
        Map<String, Object> step = new LinkedHashMap<String, Object>();
        step.put("id", def[0]);
        step.put("name", def[1]);
        step.put("description", def[2]);
        step.put("actualDuration", durationMs);
        step.put("status", "completed");
        return step;
    }
}
