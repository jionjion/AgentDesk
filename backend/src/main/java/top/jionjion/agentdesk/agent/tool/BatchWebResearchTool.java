package top.jionjion.agentdesk.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 批量并行联网研究工具。
 * <p>
 * 将研究主题拆解为多个差异化子查询，并行调用 Tavily API 搜索，
 * 合并去重后返回精简结果。适用于开放性调研场景。
 *
 * @author Jion
 */
public class BatchWebResearchTool {

    private static final Logger log = LoggerFactory.getLogger(BatchWebResearchTool.class);

    private static final String TAVILY_SEARCH_URL = "https://api.tavily.com/search";
    private static final MediaType JSON_TYPE = MediaType.get("application/json");
    private static final int MAX_QUERIES = 5;
    private static final int SEARCH_RESULTS_PER_QUERY = 5;
    private static final int MAX_OUTPUT_LENGTH = 1000;
    private static final Duration DECOMPOSE_TIMEOUT = Duration.ofSeconds(15);
    private static final long PARALLEL_TIMEOUT_SECONDS = 45;

    private static final String DECOMPOSE_PROMPT = """
            你是搜索查询分解器。将研究主题拆解为 3-5 个差异化的搜索子查询。
            要求:
            - 每个子查询从不同角度切入，避免语义重复
            - 简洁（5-15字），适合搜索引擎
            - 可混合中英文关键词
            - 每行输出一个查询，不要编号或其他内容

            研究主题: %s""";

    private final String apiKey;
    private final DashScopeChatModel model;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final GenerateOptions decomposeOptions;

    public BatchWebResearchTool(String apiKey, DashScopeChatModel model) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        AtomicInteger threadCounter = new AtomicInteger(0);
        this.executor = Executors.newFixedThreadPool(5, r -> {
            Thread t = new Thread(r, "batch-research-" + threadCounter.getAndIncrement());
            t.setDaemon(true);
            return t;
        });

        this.decomposeOptions = GenerateOptions.builder()
                .executionConfig(ExecutionConfig.builder()
                        .timeout(DECOMPOSE_TIMEOUT)
                        .maxAttempts(1)
                        .build())
                .build();
    }

    @Tool(name = ToolDefinitions.BATCH_WEB_RESEARCHER, description = ToolDefinitions.BATCH_WEB_RESEARCHER_DESC)
    public String batchResearch(
            @ToolParam(name = "topic", description = "研究主题或问题") String topic) {

        long startTime = System.currentTimeMillis();
        log.info("开始批量研究, 主题: {}", topic);

        // 1. 拆解子查询
        List<String> queries = decomposeQuery(topic);
        log.info("子查询分解完成, 数量: {}, 内容: {}", queries.size(), queries);

        // 2. 并行搜索
        List<CompletableFuture<SearchResult>> futures = queries.stream()
                .map(query -> CompletableFuture.supplyAsync(() -> executeSingleSearch(query), executor))
                .collect(Collectors.toList());

        // 3. 等待所有完成（带超时）
        List<SearchResult> results = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(PARALLEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (CompletableFuture<SearchResult> future : futures) {
                SearchResult result = future.get();
                if (result != null && !result.items.isEmpty()) {
                    results.add(result);
                }
            }
        } catch (TimeoutException e) {
            log.warn("批量搜索超时, 收集已完成的结果");
            for (CompletableFuture<SearchResult> future : futures) {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    try {
                        SearchResult result = future.get();
                        if (result != null && !result.items.isEmpty()) {
                            results.add(result);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.error("批量搜索异常: {}", e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // 4. 合并去重
        if (results.isEmpty()) {
            log.warn("批量搜索无结果, 耗时: {}ms", elapsed);
            return "搜索失败，未找到相关结果。请尝试换个角度描述问题。";
        }

        String merged = mergeResults(topic, results, queries.size());
        log.info("批量研究完成, 耗时: {}ms, 子查询: {}, 有效结果组: {}", elapsed, queries.size(), results.size());
        return merged;
    }

    /**
     * 使用 LLM 将研究主题拆解为多个差异化子查询
     */
    private List<String> decomposeQuery(String topic) {
        try {
            Msg systemMsg = Msg.builder()
                    .role(MsgRole.SYSTEM)
                    .textContent(String.format(DECOMPOSE_PROMPT, topic))
                    .build();
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(topic)
                    .build();

            StringBuilder sb = new StringBuilder();
            List<ChatResponse> responses = model.stream(
                    List.of(systemMsg, userMsg), Collections.emptyList(), decomposeOptions
            ).collectList().block();

            if (responses != null) {
                for (ChatResponse resp : responses) {
                    if (resp.getContent() != null) {
                        for (ContentBlock block : resp.getContent()) {
                            if (block instanceof TextBlock tb) {
                                sb.append(tb.getText());
                            }
                        }
                    }
                }
            }

            List<String> queries = Arrays.stream(sb.toString().split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .limit(MAX_QUERIES)
                    .collect(Collectors.toList());

            if (queries.size() < 2) {
                log.warn("LLM 分解结果不足, 回退为原始主题");
                return List.of(topic);
            }
            return queries;

        } catch (Exception e) {
            log.warn("查询分解失败, 回退为原始主题: {}", e.getMessage());
            return List.of(topic);
        }
    }

    /**
     * 执行单次 Tavily 搜索
     */
    private SearchResult executeSingleSearch(String query) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    new SearchRequest(apiKey, query, SEARCH_RESULTS_PER_QUERY));
            Request request = new Request.Builder()
                    .url(TAVILY_SEARCH_URL)
                    .post(RequestBody.create(requestBody, JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("搜索失败 [{}]: HTTP {}", query, response.code());
                    return new SearchResult(query, List.of());
                }
                return parseSearchResponse(query, response.body().string());
            }
        } catch (IOException e) {
            log.warn("搜索异常 [{}]: {}", query, e.getMessage());
            return new SearchResult(query, List.of());
        }
    }

    private SearchResult parseSearchResponse(String query, String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.get("results");

        if (results == null || results.isEmpty()) {
            return new SearchResult(query, List.of());
        }

        List<SearchItem> items = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JsonNode item = results.get(i);
            String title = item.has("title") ? item.get("title").asText() : "";
            String url = item.has("url") ? item.get("url").asText() : "";
            String content = item.has("content") ? item.get("content").asText() : "";
            if (!url.isEmpty()) {
                items.add(new SearchItem(title, url, content));
            }
        }
        return new SearchResult(query, items);
    }

    /**
     * 合并去重，格式化输出
     */
    private String mergeResults(String topic, List<SearchResult> results, int totalQueries) {
        // 按 URL 去重
        LinkedHashMap<String, SearchItem> uniqueItems = new LinkedHashMap<>();
        for (SearchResult result : results) {
            for (SearchItem item : result.items) {
                uniqueItems.putIfAbsent(item.url, item);
            }
        }

        // 取前 8 条
        List<SearchItem> topItems = uniqueItems.values().stream()
                .limit(8)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("研究主题: ").append(topic).append("\n");
        sb.append("（并行搜索 ").append(totalQueries).append(" 个角度，")
                .append("找到 ").append(uniqueItems.size()).append(" 条不重复结果）\n\n");

        for (int i = 0; i < topItems.size(); i++) {
            SearchItem item = topItems.get(i);
            String snippet = item.content;
            // 截断过长的摘要
            if (snippet.length() > 100) {
                snippet = snippet.substring(0, 100) + "...";
            }
            sb.append(i + 1).append(". **").append(item.title).append("**\n");
            sb.append("   ").append(snippet).append("\n");
            sb.append("   来源: ").append(item.url).append("\n\n");

            // 检查总长度
            if (sb.length() > MAX_OUTPUT_LENGTH - 50) {
                sb.append("...(更多结果已省略)\n");
                break;
            }
        }

        String output = sb.toString();
        if (output.length() > MAX_OUTPUT_LENGTH) {
            output = output.substring(0, MAX_OUTPUT_LENGTH) + "\n...(已截断)";
        }
        return output;
    }

    // ─── 内部数据结构 ───

    private record SearchRequest(String api_key, String query, int max_results) {
    }

    private record SearchItem(String title, String url, String content) {
    }

    private record SearchResult(String query, List<SearchItem> items) {
    }
}
