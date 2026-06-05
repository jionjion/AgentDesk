package top.jionjion.agentdesk.agent.exec;

/**
 * 客户端执行异常: 客户端未连接、超时、被拒绝等执行失败场景。
 *
 * @author Jion
 */
public class ClientExecException extends Exception {

    public ClientExecException(String message) {
        super(message);
    }
}
