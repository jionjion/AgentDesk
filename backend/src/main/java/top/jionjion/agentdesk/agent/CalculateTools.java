package top.jionjion.agentdesk.agent;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 计算工具, 供子代理使用
 *
 * @author Jion
 */
public class CalculateTools {

    private static final char CHAR_PLUS = '+';
    private static final char CHAR_MINUS = '-';
    private static final char CHAR_LPAREN = '(';
    private static final char CHAR_RPAREN = ')';
    private static final char CHAR_DOT = '.';
    private static final char CHAR_ZERO = '0';
    private static final char CHAR_NINE = '9';

    @Tool(name = "calculate", description = "计算一个数学表达式, 支持加减乘除")
    public String calculate(@ToolParam(name = "expression", description = "数学表达式, 例如: 1+2*3") String expression) {
        try {
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
        if (ch[0] == CHAR_PLUS) {
            nextChar.run();
            return parseFactor(expr, pos, ch, nextChar);
        }
        if (ch[0] == CHAR_MINUS) {
            nextChar.run();
            return -parseFactor(expr, pos, ch, nextChar);
        }
        double x;
        if (ch[0] == CHAR_LPAREN) {
            nextChar.run();
            x = parseExpression(expr, pos, ch, nextChar);
            if (ch[0] == CHAR_RPAREN) {
                nextChar.run();
            }
        } else {
            int startPos = pos[0];
            while (ch[0] >= CHAR_ZERO && ch[0] <= CHAR_NINE || ch[0] == CHAR_DOT) {
                nextChar.run();
            }
            x = Double.parseDouble(expr.substring(startPos, pos[0]));
        }
        return x;
    }
}
