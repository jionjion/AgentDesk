package top.jionjion.agentdesk.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 通用 HTTP 请求工具, 允许 Agent 调用第三方 API。
 * 内置 SSRF 防护（禁止访问内网地址）和响应体大小限制。
 *
 * @author Jion
 */
public class ApiCallTool {

    private static final Logger log = LoggerFactory.getLogger(ApiCallTool.class);

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_RESPONSE_LENGTH = 4000;
    /** 最大请求体大小: 1MB */
    private static final int MAX_BODY_SIZE = 1024 * 1024;
    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";

    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "DELETE");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ApiCallTool() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .dns(hostname -> {
                    // SSRF 防护: 解析后检查是否为内网地址
                    var addresses = InetAddress.getAllByName(hostname);
                    for (InetAddress addr : addresses) {
                        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                            throw new java.net.UnknownHostException("禁止访问内网地址: " + hostname);
                        }
                    }
                    return java.util.Arrays.asList(addresses);
                })
                .build();
    }

    @Tool(name = ToolDefinitions.API_CALL, description = ToolDefinitions.API_CALL_DESC)
    public String apiCall(
            @ToolParam(name = "method", description = "HTTP 方法: GET, POST, PUT, DELETE") String method,
            @ToolParam(name = "url", description = "请求 URL, 必须是 https:// 或 http:// 开头") String url,
            @ToolParam(name = "headers", description = "请求头, JSON 格式如 {\"Authorization\":\"Bearer xxx\"}, 可为空") String headers,
            @ToolParam(name = "body", description = "请求体, 用于 POST/PUT 请求, 可为空") String body) {

        // 参数校验
        if (method == null || method.isBlank()) {
            return "错误: method 不能为空";
        }
        String upperMethod = method.trim().toUpperCase();
        if (!ALLOWED_METHODS.contains(upperMethod)) {
            return "错误: 不支持的 HTTP 方法 '" + method + "', 仅支持 GET/POST/PUT/DELETE";
        }

        if (url == null || url.isBlank()) {
            return "错误: url 不能为空";
        }
        if (!url.startsWith(HTTP_PREFIX) && !url.startsWith(HTTPS_PREFIX)) {
            return "错误: url 必须以 http:// 或 https:// 开头";
        }

        try {
            // 构建请求
            Request.Builder requestBuilder = new Request.Builder().url(url);

            // 解析并设置请求头
            if (headers != null && !headers.isBlank()) {
                Map<String, String> headerMap = objectMapper.readValue(headers, new TypeReference<>() {});
                headerMap.forEach(requestBuilder::addHeader);
            }

            // 设置请求体
            RequestBody requestBody = null;
            if (METHOD_POST.equals(upperMethod) || METHOD_PUT.equals(upperMethod)) {
                String content = (body != null && !body.isBlank()) ? body : "";
                requestBody = RequestBody.create(content, JSON_TYPE);
            }

            switch (upperMethod) {
                case "GET" -> requestBuilder.get();
                case "POST" -> requestBuilder.post(requestBody);
                case "PUT" -> requestBuilder.put(requestBody);
                case "DELETE" -> {
                    if (body != null && !body.isBlank()) {
                        requestBuilder.delete(RequestBody.create(body, JSON_TYPE));
                    } else {
                        requestBuilder.delete();
                    }
                }
                default -> throw new IllegalArgumentException("不支持的 HTTP 方法: " + upperMethod);
            }
            // 注意：ALLOWED_METHODS 已经校验过方法，switch 覆盖所有可能值，default 理论上不可达

            Request request = requestBuilder.build();
            log.info("api_call: {} {}", upperMethod, url);

            // 执行请求
            try (Response response = httpClient.newCall(request).execute()) {
                int statusCode = response.code();
                String responseBody = "";

                if (response.body() != null) {
                    // 限制读取大小
                    byte[] bytes = response.body().byteStream().readNBytes(MAX_BODY_SIZE);
                    responseBody = new String(bytes);
                }

                // 格式化输出
                StringBuilder result = new StringBuilder();
                result.append("HTTP ").append(statusCode).append(" ").append(response.message()).append("\n");
                result.append("---\n");

                if (responseBody.length() > MAX_RESPONSE_LENGTH) {
                    result.append(responseBody, 0, MAX_RESPONSE_LENGTH);
                    result.append("\n...(响应已截断, 原始长度: ").append(responseBody.length()).append(" 字符)");
                } else {
                    result.append(responseBody);
                }

                return result.toString();
            }
        } catch (IOException e) {
            log.error("api_call 调用失败: {} {} - {}", upperMethod, url, e.getMessage());
            return "请求失败: " + e.getMessage();
        }
    }
}
