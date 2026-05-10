package top.jionjion.agentdesk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * DashScope Embedding 服务
 * <p>
 * 调用 text-embedding-v3 模型将文本转化为 1024 维向量
 *
 * @author Jion
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private static final String DASHSCOPE_EMBEDDING_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";

    private static final int BATCH_SIZE = 10;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json");

    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final int dimensions;

    public EmbeddingService(
            @Value("${DASHSCOPE_API_KEY:}") String apiKey,
            @Value("${agentdesk.knowledge.embedding-model:text-embedding-v3}") String model,
            @Value("${agentdesk.knowledge.embedding-dimensions:1024}") int dimensions) {
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 单条文本向量化 (document 类型)
     */
    public float[] embed(String text) {
        List<float[]> results = callEmbeddingApi(List.of(text), "document");
        return results.isEmpty() ? new float[dimensions] : results.getFirst();
    }

    /**
     * 查询文本向量化 (query 类型, 检索效果更好)
     */
    public float[] embedQuery(String query) {
        List<float[]> results = callEmbeddingApi(List.of(query), "query");
        return results.isEmpty() ? new float[dimensions] : results.getFirst();
    }

    /**
     * 批量向量化 (自动分批, 每批最多 25 条)
     */
    public List<float[]> batchEmbed(List<String> texts) {
        List<float[]> allResults = new ArrayList<>(texts.size());

        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            List<float[]> batchResults = callEmbeddingApi(batch, "document");
            allResults.addAll(batchResults);

            // 批间延迟, 避免 DashScope 限流
            if (i + BATCH_SIZE < texts.size()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Embedding 被中断", e);
                }
            }
        }

        return allResults;
    }

    /**
     * 调用 DashScope Embedding API
     */
    private List<float[]> callEmbeddingApi(List<String> texts, String textType) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", Map.of("texts", texts),
                    "parameters", Map.of(
                            "dimension", dimensions,
                            "text_type", textType
                    )
            );

            String jsonBody = OBJECT_MAPPER.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(DASHSCOPE_EMBEDDING_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, JSON_MEDIA))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown";
                    throw new RuntimeException("Embedding API 调用失败: HTTP " + response.code() + " - " + errorBody);
                }

                String responseBody = response.body().string();
                JsonNode root = OBJECT_MAPPER.readTree(responseBody);
                JsonNode embeddings = root.path("output").path("embeddings");

                if (!embeddings.isArray()) {
                    throw new RuntimeException("Embedding API 响应格式异常");
                }

                // 按 text_index 排序还原顺序
                float[][] results = new float[texts.size()][];
                for (JsonNode item : embeddings) {
                    int index = item.path("text_index").asInt();
                    JsonNode embeddingArray = item.path("embedding");
                    float[] vector = new float[dimensions];
                    for (int j = 0; j < Math.min(dimensions, embeddingArray.size()); j++) {
                        vector[j] = (float) embeddingArray.get(j).asDouble();
                    }
                    if (index < results.length) {
                        results[index] = vector;
                    }
                }

                List<float[]> resultList = new ArrayList<>(texts.size());
                for (float[] v : results) {
                    resultList.add(v != null ? v : new float[dimensions]);
                }
                return resultList;
            }
        } catch (IOException e) {
            log.error("Embedding API 请求失败", e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }

    public int getDimensions() {
        return dimensions;
    }
}
