package top.jionjion.agentdesk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.MemoryItemDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 长期记忆服务: 封装 Mem0 REST API 调用
 * <p>
 * Mem0 服务地址和 API Key 从应用配置文件读取（agentdesk.mem0.*），
 * 属于后端基础设施配置，不暴露给前端用户。
 *
 * @author Jion
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final String mem0BaseUrl;
    private final String mem0ApiKey;

    public MemoryService(@Value("${agentdesk.mem0.base-url}") String mem0BaseUrl,
                         @Value("${agentdesk.mem0.api-key:}") String mem0ApiKey) {
        this.httpClient = new OkHttpClient();
        // 去掉尾部斜杠
        this.mem0BaseUrl = mem0BaseUrl.endsWith("/") ? mem0BaseUrl.substring(0, mem0BaseUrl.length() - 1) : mem0BaseUrl;
        this.mem0ApiKey = (mem0ApiKey == null || mem0ApiKey.isBlank()) ? null : mem0ApiKey;
    }

    /**
     * 获取用户所有记忆条目
     */
    public List<MemoryItemDto> listMemories(Long userId) {
        String url = mem0BaseUrl + "/memories?user_id=" + userId;

        Request.Builder reqBuilder = new Request.Builder().url(url).get();
        addApiKey(reqBuilder);

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Mem0 获取记忆列表失败: {}", response.code());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mem0 服务请求失败: " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "[]";
            log.debug("Mem0 原始响应: {}", body);
            return parseMemoryList(body);
        } catch (IOException e) {
            log.error("调用 Mem0 API 失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法连接 Mem0 服务: " + e.getMessage());
        }
    }

    /**
     * 手动添加一条记忆
     */
    public void addMemory(Long userId, String content) {
        String url = mem0BaseUrl + "/memories";

        String json;
        try {
            var messages = List.of(Map.of("role", "user", "content", content));
            json = MAPPER.writeValueAsString(new AddMemoryBody(messages, String.valueOf(userId)));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "序列化失败");
        }

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON_TYPE));
        addApiKey(reqBuilder);

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Mem0 添加记忆失败: {}", response.code());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mem0 添加记忆失败: " + response.code());
            }
        } catch (IOException e) {
            log.error("调用 Mem0 API 失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法连接 Mem0 服务: " + e.getMessage());
        }
    }

    /**
     * 删除单条记忆
     */
    public void deleteMemory(Long userId, String memoryId) {
        String url = mem0BaseUrl + "/memories/" + memoryId;

        Request.Builder reqBuilder = new Request.Builder().url(url).delete();
        addApiKey(reqBuilder);

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Mem0 删除记忆失败: {}", response.code());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mem0 删除记忆失败: " + response.code());
            }
        } catch (IOException e) {
            log.error("调用 Mem0 API 失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法连接 Mem0 服务: " + e.getMessage());
        }
    }

    /**
     * 修改一条记忆内容
     */
    public void updateMemory(Long userId, String memoryId, String newContent) {
        String url = mem0BaseUrl + "/memories/" + memoryId;

        String json;
        try {
            json = MAPPER.writeValueAsString(Map.of("memory", newContent));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "序列化失败");
        }

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .put(RequestBody.create(json, JSON_TYPE));
        addApiKey(reqBuilder);

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Mem0 修改记忆失败: {}", response.code());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mem0 修改记忆失败: " + response.code());
            }
        } catch (IOException e) {
            log.error("调用 Mem0 API 失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法连接 Mem0 服务: " + e.getMessage());
        }
    }

    /**
     * 清空用户所有记忆
     */
    public void deleteAllMemories(Long userId) {
        String url = mem0BaseUrl + "/memories?user_id=" + userId;

        Request.Builder reqBuilder = new Request.Builder().url(url).delete();
        addApiKey(reqBuilder);

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Mem0 清空记忆失败: {}", response.code());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mem0 清空记忆失败: " + response.code());
            }
        } catch (IOException e) {
            log.error("调用 Mem0 API 失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法连接 Mem0 服务: " + e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    private void addApiKey(Request.Builder builder) {
        if (mem0ApiKey != null) {
            builder.addHeader("Authorization", "Bearer " + mem0ApiKey);
        }
    }

    /** 安全读取 JSON 字段，过滤 "null" 字符串 */
    private static String nullSafe(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        String val = node.get(field).asText();
        return "null".equals(val) ? null : val;
    }

    private List<MemoryItemDto> parseMemoryList(String json) {
        List<MemoryItemDto> result = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            // 自托管 Mem0 返回格式: {"results": [...]} 或直接数组
            JsonNode items = root.isArray() ? root : root.get("results");
            if (items == null) {
                items = root;
            }
            if (items.isArray()) {
                for (JsonNode item : items) {
                    result.add(new MemoryItemDto(
                            item.has("id") ? item.get("id").asText() : null,
                            item.has("memory") ? item.get("memory").asText() : null,
                            nullSafe(item, "created_at"),
                            nullSafe(item, "updated_at")
                    ));
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("解析 Mem0 响应失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Mem0 添加记忆的请求体
     */
    private record AddMemoryBody(List<Map<String, String>> messages, String user_id) {
    }
}
