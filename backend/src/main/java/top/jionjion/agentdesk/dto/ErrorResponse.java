package top.jionjion.agentdesk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一错误响应体
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path
) {
}
