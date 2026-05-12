package top.jionjion.agentdesk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一错误响应体
 *
 * @param status  HTTP 状态码
 * @param error   错误类型
 * @param message 错误描述信息
 * @param path    请求路径
 * @author Jion
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path
) {
}
