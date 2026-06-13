package top.jionjion.agentdesk.websocket.dto;

import java.util.List;
import java.util.Map;

/**
 * 远程命令执行请求
 *
 * @param command    要执行的 shell 命令
 * @param workingDir 工作目录（可选）
 * @param riskLevel  风险等级: LOW / HIGH
 * @param timeoutMs  超时时间（毫秒）
 * @param env        额外环境变量（可选）
 * @param policy     执行隔离策略（可选，由后端下发资源限制等服务端可控项）
 * @author Jion
 */
public record CommandRequest(
        String command,
        String workingDir,
        String riskLevel,
        long timeoutMs,
        Map<String, String> env,
        ExecPolicy policy
) {

    public static final String RISK_LOW = "LOW";
    public static final String RISK_HIGH = "HIGH";

    /**
     * 执行隔离策略。
     * <p>
     * 注意: allowedRoots（目录边界）由客户端依据用户授权的工作目录自行掌握, 后端不下发,
     * 此处仅承载服务端可控的资源限制与隔离档位。
     *
     * @param allowedRoots   允许执行的根目录树（通常为 null, 由客户端决定）
     * @param resourceLimits 资源限制
     * @param isolationLevel 隔离档位: boundary（目录边界+资源兜底）
     */
    public record ExecPolicy(
            List<String> allowedRoots,
            ResourceLimits resourceLimits,
            String isolationLevel
    ) {

        public static final String LEVEL_BOUNDARY = "boundary";
    }

    /**
     * 资源限制。
     *
     * @param timeoutMs      超时毫秒（超时后客户端 kill 整个进程组）
     * @param maxOutputChars stdout/stderr 各自的字符上限
     * @param memMB          进程内存上限（MB），由客户端 Windows Job Object 强制
     * @param maxProcesses   活动进程数上限，由客户端 Windows Job Object 强制
     */
    public record ResourceLimits(
            Long timeoutMs,
            Integer maxOutputChars,
            Integer memMB,
            Integer maxProcesses
    ) {
    }
}
