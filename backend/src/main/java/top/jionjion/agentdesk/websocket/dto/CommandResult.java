package top.jionjion.agentdesk.websocket.dto;

/**
 * 远程命令执行结果
 *
 * @param exitCode   退出码
 * @param stdout     标准输出
 * @param stderr     标准错误
 * @param durationMs 执行耗时（毫秒）
 * @author Jion
 */
public record CommandResult(
        int exitCode,
        String stdout,
        String stderr,
        long durationMs
) {

    /**
     * 判断命令是否执行成功
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }
}
