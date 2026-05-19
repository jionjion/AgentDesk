package top.jionjion.agentdesk.websocket.dto;

/**
 * 沙箱代码执行结果
 *
 * @param success    是否执行成功
 * @param stdout     标准输出
 * @param stderr     标准错误
 * @param result     result 变量的值（字符串化）
 * @param figureCount 生成的图表数量
 * @param durationMs 执行耗时（毫秒）
 */
public record SandboxResult(
        boolean success,
        String stdout,
        String stderr,
        String result,
        int figureCount,
        long durationMs
) {
}
