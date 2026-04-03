package top.jionjion.agentdesk.agent;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 示例工具类, 提供给 Agent 调用
 */
public class SimpleTools {

    @Tool(name = "get_current_time", description = "获取当前时间, 可以指定时区")
    public String getCurrentTime(@ToolParam(name = "timezone", description = "时区, 例如: Asia/Shanghai, America/New_York") String timezone) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            zoneId = ZoneId.of("Asia/Shanghai");
        }
        return LocalDateTime.now(zoneId)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool(name = "calculate", description = "计算一个数学表达式, 支持加减乘除")
    public String calculate(@ToolParam(name = "expression", description = "数学表达式, 例如: 1+2*3") String expression) {
        try {
            // 简单的四则运算支持
            double result = evalExpression(expression);
            return String.valueOf(result);
        } catch (Exception e) {
            return "计算出错: " + e.getMessage();
        }
    }

    private double evalExpression(String expression) {
        final String expr = expression.replaceAll("\\s+", "");
        final int[] pos = {-1};
        final int[] ch = {0};

        Runnable nextChar = () -> ch[0] = (++pos[0] < expr.length()) ? expr.charAt(pos[0]) : -1;

        // 启动解析
        nextChar.run();
        return parseExpression(expr, pos, ch, nextChar);
    }

    private double parseExpression(String expr, int[] pos, int[] ch, Runnable nextChar) {
        double x = parseTerm(expr, pos, ch, nextChar);
        for (; ; ) {
            if (ch[0] == '+') {
                nextChar.run();
                x += parseTerm(expr, pos, ch, nextChar);
            } else if (ch[0] == '-') {
                nextChar.run();
                x -= parseTerm(expr, pos, ch, nextChar);
            } else {
                return x;
            }
        }
    }

    private double parseTerm(String expr, int[] pos, int[] ch, Runnable nextChar) {
        double x = parseFactor(expr, pos, ch, nextChar);
        for (; ; ) {
            if (ch[0] == '*') {
                nextChar.run();
                x *= parseFactor(expr, pos, ch, nextChar);
            } else if (ch[0] == '/') {
                nextChar.run();
                x /= parseFactor(expr, pos, ch, nextChar);
            } else {
                return x;
            }
        }
    }

    private double parseFactor(String expr, int[] pos, int[] ch, Runnable nextChar) {
        if (ch[0] == '+') {
            nextChar.run();
            return parseFactor(expr, pos, ch, nextChar);
        }
        if (ch[0] == '-') {
            nextChar.run();
            return -parseFactor(expr, pos, ch, nextChar);
        }
        double x;
        if (ch[0] == '(') {
            nextChar.run();
            x = parseExpression(expr, pos, ch, nextChar);
            if (ch[0] == ')') {
                nextChar.run();
            }
        } else {
            int startPos = pos[0];
            while (ch[0] >= '0' && ch[0] <= '9' || ch[0] == '.') {
                nextChar.run();
            }
            x = Double.parseDouble(expr.substring(startPos, pos[0]));
        }
        return x;
    }
}
