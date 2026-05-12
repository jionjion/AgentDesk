package top.jionjion.agentdesk.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Web 工具类, 基于 Tavily API 提供联网搜索和网页内容提取能力。
 *
 * @author Jion
 */
public class WebTools {

    private static final Logger log = LoggerFactory.getLogger(WebTools.class);

    private static final String TAVILY_SEARCH_URL = "https://api.tavily.com/search";
    private static final String TAVILY_EXTRACT_URL = "https://api.tavily.com/extract";
    private static final MediaType JSON_TYPE = MediaType.get("application/json");

    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebTools(String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Tool(name = "web_search", description = "搜索互联网, 返回与查询相关的搜索结果列表。每条结果包含标题、摘要和链接。")
    public String webSearch(
            @ToolParam(name = "query", description = "搜索关键词") String query,
            @ToolParam(name = "count", description = "返回结果数量, 默认5") Integer count) {

        int resultCount = (count == null || count <= 0) ? 5 : Math.min(count, 10);

        try {
            String requestBody = objectMapper.writeValueAsString(new SearchRequest(apiKey, query, resultCount));
            Request request = new Request.Builder()
                    .url(TAVILY_SEARCH_URL)
                    .post(RequestBody.create(requestBody, JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return "搜索失败: HTTP " + response.code();
                }
                return formatSearchResults(response.body().string(), query);
            }
        } catch (IOException e) {
            log.error("Tavily search 调用失败: {}", e.getMessage());
            return "搜索失败: " + e.getMessage();
        }
    }

    @Tool(name = "url_fetch", description = "抓取指定 URL 的网页正文内容, 返回提取后的纯文本。用于深入了解搜索结果中的某个链接。")
    public String urlFetch(
            @ToolParam(name = "url", description = "要抓取的网页 URL") String url,
            @ToolParam(name = "maxLength", description = "返回内容的最大字符数, 默认3000") Integer maxLength) {

        int limit = (maxLength == null || maxLength <= 0) ? 3000 : Math.min(maxLength, 5000);

        try {
            String requestBody = objectMapper.writeValueAsString(new ExtractRequest(apiKey, url));
            Request request = new Request.Builder()
                    .url(TAVILY_EXTRACT_URL)
                    .post(RequestBody.create(requestBody, JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return "抓取失败: HTTP " + response.code();
                }
                return formatExtractResult(response.body().string(), url, limit);
            }
        } catch (IOException e) {
            log.error("Tavily extract 调用失败: {}", e.getMessage());
            return "抓取失败: " + e.getMessage();
        }
    }

    private String formatSearchResults(String responseBody, String query) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.get("results");

        StringBuilder sb = new StringBuilder();
        sb.append("搜索关键词: ").append(query).append("\n\n");

        if (results == null || results.isEmpty()) {
            sb.append("未找到相关结果。");
            return sb.toString();
        }

        for (int i = 0; i < results.size(); i++) {
            JsonNode item = results.get(i);
            String title = item.has("title") ? item.get("title").asText() : "无标题";
            String itemUrl = item.has("url") ? item.get("url").asText() : "";
            String content = item.has("content") ? item.get("content").asText() : "";

            sb.append(i + 1).append(". **").append(title).append("**\n");
            sb.append("   链接: ").append(itemUrl).append("\n");
            sb.append("   摘要: ").append(content).append("\n\n");
        }

        return sb.toString();
    }

    private String formatExtractResult(String responseBody, String url, int limit) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.get("results");

        StringBuilder sb = new StringBuilder();
        sb.append("URL: ").append(url).append("\n---\n\n");

        if (results == null || results.isEmpty()) {
            sb.append("无法提取该页面内容。");
            return sb.toString();
        }

        JsonNode first = results.get(0);
        String rawContent = first.has("raw_content") ? first.get("raw_content").asText() : "";

        if (rawContent.isEmpty()) {
            rawContent = first.has("content") ? first.get("content").asText() : "无内容";
        }

        sb.append(rawContent);

        String result = sb.toString();
        if (result.length() > limit) {
            result = result.substring(0, limit) + "\n...(内容已截断)";
        }
        return result;
    }

    // Tavily API 请求体
    private record SearchRequest(String api_key, String query, int max_results) {}
    private record ExtractRequest(String api_key, String[] urls) {
        ExtractRequest(String apiKey, String url) {
            this(apiKey, new String[]{url});
        }
    }
}
