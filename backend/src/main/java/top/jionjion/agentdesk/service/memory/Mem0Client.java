package top.jionjion.agentdesk.service.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Infrastructure adapter. Mem0 extracts candidates; PostgreSQL remains the source of truth. */
@Component
public class Mem0Client {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper mapper;
    private final OkHttpClient client;
    private final String baseUrl;
    private final String apiKey;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long circuitOpenUntil;

    public Mem0Client(ObjectMapper mapper,
                      @Value("${agentdesk.mem0.base-url}") String baseUrl,
                      @Value("${agentdesk.mem0.api-key:}") String apiKey) {
        this.mapper = mapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(20))
                .callTimeout(Duration.ofSeconds(25))
                .build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
    }

    /** Sends only the original user-authored text, never augmented prompts or assistant output. */
    public List<ExtractedMemory> extract(Long userId, String userMessage) throws IOException {
        String json = mapper.writeValueAsString(new AddRequest(
                List.of(Map.of("role", "user", "content", userMessage)), String.valueOf(userId)));
        return guarded(() -> {
            Request.Builder builder = new Request.Builder()
                    .url(baseUrl + "/memories")
                    .post(RequestBody.create(json, JSON));
            authorize(builder);
            try (Response response = client.newCall(builder.build()).execute()) {
                String body = response.body() == null ? "[]" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new IOException("Mem0 returned HTTP " + response.code());
                }
                return parse(body);
            }
        });
    }

    public List<ExtractedMemory> list(Long userId) throws IOException {
        return guarded(() -> {
            Request.Builder builder = new Request.Builder()
                    .url(baseUrl + "/memories?user_id=" + userId)
                    .get();
            authorize(builder);
            try (Response response = client.newCall(builder.build()).execute()) {
                String body = response.body() == null ? "[]" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new IOException("Mem0 returned HTTP " + response.code());
                }
                return parse(body);
            }
        });
    }

    public void update(String providerRef, String content) throws IOException {
        String json = mapper.writeValueAsString(Map.of("memory", content));
        Request.Builder builder = new Request.Builder().url(baseUrl + "/memories/" + providerRef)
                .put(RequestBody.create(json, JSON));
        guarded(() -> { executeWithoutBody(builder); return null; });
    }

    public void delete(String providerRef) throws IOException {
        Request.Builder builder = new Request.Builder().url(baseUrl + "/memories/" + providerRef).delete();
        guarded(() -> {
            authorize(builder);
            try (Response response = client.newCall(builder.build()).execute()) {
                if (!response.isSuccessful() && response.code() != 404) {
                    throw new IOException("Mem0 returned HTTP " + response.code());
                }
            }
            return null;
        });
    }

    public void deleteAll(Long userId) throws IOException {
        Request.Builder builder = new Request.Builder().url(baseUrl + "/memories?user_id=" + userId).delete();
        guarded(() -> { executeWithoutBody(builder); return null; });
    }

    public CircuitStatus circuitStatus() {
        long openUntil = circuitOpenUntil;
        return new CircuitStatus(openUntil > System.currentTimeMillis() ? "OPEN" : "CLOSED",
                consecutiveFailures.get(), openUntil);
    }

    private void executeWithoutBody(Request.Builder builder) throws IOException {
        authorize(builder);
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("Mem0 returned HTTP " + response.code());
        }
    }

    private List<ExtractedMemory> parse(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        JsonNode items = root.isArray() ? root : root.path("results");
        if (!items.isArray()) {
            return List.of();
        }
        List<ExtractedMemory> result = new ArrayList<>();
        for (JsonNode item : items) {
            String content = item.path("memory").asText("").trim();
            if (!content.isEmpty()) {
                result.add(new ExtractedMemory(item.path("id").asText(null), content));
            }
        }
        return result;
    }

    private void authorize(Request.Builder builder) {
        if (apiKey != null) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
    }

    private <T> T guarded(IoSupplier<T> action) throws IOException {
        long now = System.currentTimeMillis();
        if (circuitOpenUntil > now) {
            throw new IOException("Mem0 circuit breaker is open");
        }
        try {
            T result = action.get();
            consecutiveFailures.set(0);
            circuitOpenUntil = 0L;
            return result;
        } catch (IOException ex) {
            if (consecutiveFailures.incrementAndGet() >= 3) {
                circuitOpenUntil = System.currentTimeMillis() + 30_000L;
            }
            throw ex;
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    private record AddRequest(List<Map<String, String>> messages,
                              @JsonProperty("user_id") String userId) {
    }

    public record ExtractedMemory(String providerRef, String content) {
    }

    public record CircuitStatus(String state, int consecutiveFailures, long openUntil) {
    }
}
