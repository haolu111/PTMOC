package com.ptmoc.server;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从真实 PTMOC 执行结果构建前端可播放的 processTrace（不侵入密码核心）。
 */
public class CryptoTraceBuilder {

    private final List<Map<String, Object>> events = new ArrayList<Map<String, Object>>();
    private int order = 0;

    public List<Map<String, Object>> getEvents() {
        return events;
    }

    public void setup(long durationMs) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("lambda", "256");
        data.put("rsa", "2048");
        data.put("note", "公共参数分发至四方");
        data.put("durationMs", durationMs);
        add("SETUP", "SYSTEM", null, "系统初始化 Setup",
                "生成公共参数 (λ, p₀, η, 陷门置换)，分发给 Sender / Server / CSP / Receiver", data);
    }

    public void keyGen(int thresholdK, long durationMs) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("entities", "Sender / Server / CSP / Receiver");
        data.put("thresholdK", String.valueOf(thresholdK));
        data.put("secretHint", "各方持有密钥材料（界面不展示完整私钥）");
        data.put("durationMs", durationMs);
        add("KEYGEN", "SYSTEM", null, "密钥生成 KeyGen",
                "为四个角色生成公私钥对；门限参数 k=" + thresholdK, data);
    }

    public void encode(Map<Integer, BigInteger> messages, String functionDef, long durationMs) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("messageCount", messages == null ? 0 : messages.size());
        if (messages != null) {
            data.put("x1", str(messages.get(1)));
            data.put("x2", str(messages.get(2)));
            data.put("x3", str(messages.get(3)));
            data.put("x4", str(messages.get(4)));
        }
        data.put("x1Label", "平均空间偏差(cm)");
        data.put("x2Label", "最大空间偏差(cm)");
        data.put("x3Label", "平均时间偏差(s)");
        data.put("x4Label", "最大时间偏差(s)");
        data.put("function", functionDef == null ? "" : functionDef);
        data.put("durationMs", durationMs);
        add("ENCODE", "SENDER", null, "轨迹编码 Encode",
                "Sender 将轨迹偏差编码为整数消息 x₁…x₄（明文仅留在 Sender）", data);
    }

    public void encrypt(int thresholdK, Map<Integer, BigInteger> messages, long durationMs) {
        Map<String, Object> shamir = new LinkedHashMap<String, Object>();
        shamir.put("thresholdK", String.valueOf(thresholdK));
        shamir.put("shareHint", "多项式秘密共享 → share₁…shareₖ");
        shamir.put("plaintextOnServer", "false");
        shamir.put("durationMs", durationMs);
        add("ENCRYPT", "SENDER", null, "Shamir 分片 + 加密",
                "Sender 对消息做秘密共享并加密；原始轨迹明文不离开 Sender", shamir);

        Map<String, Object> toServer = new LinkedHashMap<String, Object>();
        toServer.put("payload", "Encrypted Shares");
        toServer.put("plaintext", "不可见");
        add("ENCRYPT", "SENDER", "SERVER", "密文送达 Server",
                "加密份额发送至雾计算节点 Server（看不到明文轨迹）", toServer);

        Map<String, Object> toCsp = new LinkedHashMap<String, Object>();
        toCsp.put("payload", "Auxiliary Ciphertext");
        toCsp.put("plaintext", "不可见");
        add("ENCRYPT", "SENDER", "CSP", "辅助密文送达 CSP",
                "辅助份额/陷门密文发送至 CSP，用于后续协同评估", toCsp);
    }

    public void evaluate(String functionDef, long durationMs) {
        Map<String, Object> server = new LinkedHashMap<String, Object>();
        server.put("action", "Function Evaluation");
        server.put("function", functionDef == null ? "f(x)" : functionDef);
        server.put("domain", "密文域");
        server.put("durationMs", durationMs);
        add("EVAL", "SERVER", null, "Server 密文评估",
                "Server 在密文域对加密份额做函数评估，仍看不到明文", server);

        Map<String, Object> csp = new LinkedHashMap<String, Object>();
        csp.put("action", "Random Mask");
        csp.put("role", "辅助随机化 / 掩码");
        add("EVAL", "CSP", null, "CSP 掩码处理",
                "CSP 提供辅助数据与随机掩码，与 Server 协同", csp);

        Map<String, Object> joint = new LinkedHashMap<String, Object>();
        joint.put("result", "Encrypted Function Result");
        joint.put("plaintext", "不可见");
        add("EVAL", "SERVER", "RECEIVER", "密文结果送往 Receiver",
                "Server + CSP 产出加密函数结果，交给 Receiver 解密", joint);
    }

    public void decrypt(String decryptedResult, long durationMs) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("decryptedResult", decryptedResult == null ? "" : decryptedResult);
        data.put("note", "恢复的是函数评估值，不是原始轨迹明文");
        data.put("durationMs", durationMs);
        add("DECRYPT", "RECEIVER", null, "Receiver 解密",
                "Receiver 去掩码并解密，得到验证用函数值", data);
    }

    public void result(String verificationStatus, double score,
                       List<String> reasonCodes, String anomalyDesc) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("verificationStatus", verificationStatus);
        data.put("score", score);
        data.put("resultBadge", toBadge(verificationStatus, reasonCodes));
        data.put("privacyNote", "原始轨迹明文未发送给 Server / CSP");
        if (reasonCodes != null && !reasonCodes.isEmpty()) {
            data.put("reasonCodes", reasonCodes);
        }
        if (anomalyDesc != null && anomalyDesc.length() > 0) {
            data.put("anomalyDesc", anomalyDesc);
        }
        add("RESULT", "RECEIVER", null, "验证结论",
                "根据轨迹对比与密码流程输出最终验证结果", data);
    }

    public void cryptoFailed(String error) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("error", error == null ? "unknown" : error);
        add("RESULT", "SYSTEM", null, "密码流程异常",
                "真实 PTMOC 执行中断，前端将结合明文轨迹校验结果展示", data);
    }

    private void add(String stage, String actor, String target,
                     String title, String description, Map<String, Object> displayData) {
        Map<String, Object> ev = new LinkedHashMap<String, Object>();
        ev.put("stage", stage);
        ev.put("actor", actor);
        ev.put("target", target);
        ev.put("title", title);
        ev.put("description", description);
        ev.put("order", Integer.valueOf(++order));
        ev.put("displayData", displayData);
        events.add(ev);
    }

    private static String str(BigInteger v) {
        return v == null ? "—" : v.toString();
    }

    private static String toBadge(String status, List<String> reasonCodes) {
        if (status != null && status.contains("通过") && (reasonCodes == null || reasonCodes.isEmpty())) {
            return "PASS";
        }
        if (reasonCodes != null) {
            for (int i = 0; i < reasonCodes.size(); i++) {
                String c = reasonCodes.get(i);
                if ("TIME_DEVIATION".equals(c)) return "TIME ANOMALY";
                if ("DEVIATION_TOO_LARGE".equals(c) || "PARTIAL_DEVIATION".equals(c)) {
                    return "SPATIAL DEVIATION";
                }
            }
        }
        if (status != null && status.contains("不通过")) return "FAIL";
        return status == null ? "UNKNOWN" : status;
    }
}
