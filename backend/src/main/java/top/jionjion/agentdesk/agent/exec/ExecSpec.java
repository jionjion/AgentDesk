package top.jionjion.agentdesk.agent.exec;

import java.util.List;

/**
 * 统一本地执行请求规格 (见开发计划 8.2)。
 * <p>
 * kind = shell 时使用 {@code command}; kind = python 时使用 {@code code} 或 {@code scriptPath} (二选一)。
 *
 * @param kind       执行类型: shell / python
 * @param command    shell 命令 (kind=shell)
 * @param code       Python 代码, 通过 stdin 传给 {@code python -} (kind=python)
 * @param scriptPath Python 脚本路径 (kind=python)
 * @param args       脚本参数 (kind=python, 可选)
 * @param cwd        本次执行的工作目录 (可为 null, 客户端使用默认)
 * @param riskLevel  风险等级: LOW / HIGH
 * @param projectId  项目 ID (可为 null)
 * @param deviceId   目标设备 ID (可为 null)
 * @param timeoutMs  超时毫秒 (null 使用服务端默认)
 * @author Jion
 */
public record ExecSpec(
        String kind,
        String command,
        String code,
        String scriptPath,
        List<String> args,
        String cwd,
        String riskLevel,
        String projectId,
        String deviceId,
        Long timeoutMs
) {

    public static final String KIND_SHELL = "shell";
    public static final String KIND_PYTHON = "python";

    /** shell 执行请求 */
    public static ExecSpec shell(String command, String cwd, String riskLevel,
                                 String projectId, String deviceId) {
        return new ExecSpec(KIND_SHELL, command, null, null, null, cwd, riskLevel,
                projectId, deviceId, null);
    }

    /** python 执行请求 */
    public static ExecSpec python(String code, String scriptPath, List<String> args, String cwd,
                                  String riskLevel, String projectId, String deviceId, Long timeoutMs) {
        return new ExecSpec(KIND_PYTHON, null, code, scriptPath, args, cwd, riskLevel,
                projectId, deviceId, timeoutMs);
    }
}
