package top.jionjion.agentdesk.websocket.dto;

import java.util.Map;

/**
 * 远程命令执行请求
 *
 * @param command    要执行的 shell 命令
 * @param workingDir 工作目录（可选）
 * @param riskLevel  风险等级: LOW / HIGH
 * @param timeoutMs  超时时间（毫秒）
 * @param env        额外环境变量（可选）
 * @author Jion
 */
public record CommandRequest(
        String command,
        String workingDir,
        String riskLevel,
        long timeoutMs,
        Map<String, String> env
) {

    public static final String RISK_LOW = "LOW";
    public static final String RISK_HIGH = "HIGH";
}
