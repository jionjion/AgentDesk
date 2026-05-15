package top.jionjion.agentdesk.agent.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.websocket.dto.CommandRequest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 命令风险分级器: 根据命令内容判断风险等级。
 * <p>
 * LOW 风险命令自动执行, HIGH 风险命令需要用户确认。
 * 未在白名单中的命令默认为 HIGH（安全优先）。
 *
 * @author Jion
 */
@Component
public class CommandRiskClassifier {

    private final Set<String> lowRiskCommands;

    public CommandRiskClassifier(
            @Value("${agentdesk.remote-exec.low-risk-commands:ls,cat,head,tail,find,grep,wc,pwd,echo,date,whoami,which,env,printenv,type,file,dir,where,hostname,ver,systeminfo,set,cd,tree}") String lowRiskCommandsStr) {
        this.lowRiskCommands = Arrays.stream(lowRiskCommandsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * 对命令进行风险分级
     *
     * @param command 完整的 shell 命令字符串
     * @return LOW 或 HIGH
     */
    public String classify(String command) {
        if (command == null || command.isBlank()) {
            return CommandRequest.RISK_HIGH;
        }

        String trimmed = command.trim();

        // 包含管道、重定向、后台执行、子shell 的命令, 默认 HIGH
        if (containsDangerousOperators(trimmed)) {
            return CommandRequest.RISK_HIGH;
        }

        // 包含命令链接符 (&&, ||, ;) 时, 拆分每个子命令单独判断
        if (containsChainOperators(trimmed)) {
            return classifyChainedCommand(trimmed);
        }

        return classifySingleCommand(trimmed);
    }

    /**
     * 拆分链式命令, 每个子命令都是 LOW 才返回 LOW
     */
    private String classifyChainedCommand(String command) {
        // 按 &&, ||, ; 拆分
        String[] subCommands = command.split("&&|\\|\\||;");
        for (String sub : subCommands) {
            String trimmedSub = sub.trim();
            if (trimmedSub.isEmpty()) continue;
            if (containsDangerousOperators(trimmedSub)) {
                return CommandRequest.RISK_HIGH;
            }
            if (!CommandRequest.RISK_LOW.equals(classifySingleCommand(trimmedSub))) {
                return CommandRequest.RISK_HIGH;
            }
        }
        return CommandRequest.RISK_LOW;
    }

    /**
     * 对单个命令（不含链接符）进行分级
     */
    private String classifySingleCommand(String command) {
        // 提取第一个 token（命令名）
        String firstToken = extractFirstToken(command);

        // 检查是否在低风险白名单中
        if (lowRiskCommands.contains(firstToken)) {
            if (hasHighRiskArgs(firstToken, command)) {
                return CommandRequest.RISK_HIGH;
            }
            return CommandRequest.RISK_LOW;
        }

        // 特殊处理: git 子命令
        if ("git".equals(firstToken)) {
            return classifyGitCommand(command);
        }

        // 特殊处理: npm/pip 子命令
        if ("npm".equals(firstToken) || "pip".equals(firstToken) || "pip3".equals(firstToken)) {
            return classifyPackageManagerCommand(command);
        }

        // 特殊处理: python/node 版本查询
        if (isVersionQuery(command)) {
            return CommandRequest.RISK_LOW;
        }

        // 默认 HIGH
        return CommandRequest.RISK_HIGH;
    }

    /**
     * 危险操作符: 管道、重定向、后台执行、子shell（这些无法安全拆分）
     */
    private boolean containsDangerousOperators(String command) {
        return command.contains("|") || command.contains(">") || command.contains("<")
                || command.contains("`") || command.contains("$(");
    }

    /**
     * 链接操作符: &&, ||, ; （可以安全拆分为子命令）
     */
    private boolean containsChainOperators(String command) {
        return command.contains("&&") || command.contains("||") || command.contains(";");
    }

    private String extractFirstToken(String command) {
        // 跳过环境变量前缀 (如 KEY=value cmd)
        String[] parts = command.split("\\s+");
        for (String part : parts) {
            if (!part.contains("=")) {
                return part;
            }
        }
        return parts[0];
    }

    private boolean hasHighRiskArgs(String command, String fullCommand) {
        // find 命令带 -delete 或 -exec 是高风险
        if ("find".equals(command)) {
            return fullCommand.contains("-delete") || fullCommand.contains("-exec");
        }
        return false;
    }

    private String classifyGitCommand(String command) {
        // git 只读命令: status, log, diff, branch, show, remote -v, tag
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            return CommandRequest.RISK_LOW;
        }
        String subCommand = parts[1];
        Set<String> readOnlyGitCommands = Set.of(
                "status", "log", "diff", "branch", "show", "remote", "tag",
                "stash", "blame", "shortlog", "describe", "rev-parse"
        );
        if (readOnlyGitCommands.contains(subCommand)) {
            return CommandRequest.RISK_LOW;
        }
        return CommandRequest.RISK_HIGH;
    }

    private String classifyPackageManagerCommand(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            return CommandRequest.RISK_LOW;
        }
        String subCommand = parts[1];
        // 只读子命令
        Set<String> readOnlyCommands = Set.of("list", "show", "info", "search", "outdated", "ls", "view", "help");
        if (readOnlyCommands.contains(subCommand)) {
            return CommandRequest.RISK_LOW;
        }
        return CommandRequest.RISK_HIGH;
    }

    private boolean isVersionQuery(String command) {
        return command.endsWith("--version") || command.endsWith("-v") || command.endsWith("-V");
    }
}
