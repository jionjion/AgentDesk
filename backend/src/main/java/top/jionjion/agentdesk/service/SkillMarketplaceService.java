package top.jionjion.agentdesk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.skill.MarketplaceSkillDto;
import top.jionjion.agentdesk.dto.skill.MarketplaceSkillPageDto;
import top.jionjion.agentdesk.dto.skill.SkillResponseDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * ModelScope 技能社区适配器。社区只负责发现与下载，安装后的技能仍由本地
 * {@link io.agentscope.core.skill.repository.FileSystemSkillRepository} 加载。
 */
@Service
public class SkillMarketplaceService {

    private static final Pattern MARKETPLACE_ID = Pattern.compile(
            "^@?[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*$");
    private static final int MAX_PAGE_SIZE = 24;

    private final ObjectMapper objectMapper;
    private final SkillPackageService packageService;
    private final SkillService skillService;
    private final OkHttpClient httpClient;
    private final String marketplaceBaseUrl;

    public SkillMarketplaceService(ObjectMapper objectMapper,
                                   SkillPackageService packageService,
                                   SkillService skillService,
                                   @Value("${agentdesk.skills.marketplace.base-url:https://modelscope.cn}")
                                   String marketplaceBaseUrl) {
        this.objectMapper = objectMapper;
        this.packageService = packageService;
        this.skillService = skillService;
        this.marketplaceBaseUrl = marketplaceBaseUrl.replaceAll("/+$", "");
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    public MarketplaceSkillPageDto search(String query, int pageNumber, int pageSize, Long userId) {
        int safePage = Math.max(1, pageNumber);
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        HttpUrl.Builder url = baseUrl("openapi", "v1", "skills").newBuilder()
                .addQueryParameter("page_number", String.valueOf(safePage))
                .addQueryParameter("page_size", String.valueOf(safeSize));
        if (query != null && !query.isBlank()) {
            url.addQueryParameter("search", query.trim());
        }

        JsonNode data = requestJson(url.build());
        Set<String> installedRefs = skillService.getInstalledMarketplaceRefs(userId);
        List<MarketplaceSkillDto> skills = new ArrayList<>();
        for (JsonNode node : data.path("skills")) {
            MarketplaceSkillDto item = toDto(node);
            skills.add(item.withInstalled(installedRefs.contains(item.id())));
        }
        return new MarketplaceSkillPageDto(
                skills,
                data.path("total").asLong(skills.size()),
                data.path("page_number").asInt(safePage),
                data.path("page_size").asInt(safeSize));
    }

    public MarketplaceSkillDto get(String skillId, Long userId) {
        String[] parts = validateAndSplitId(skillId);
        JsonNode data = requestJson(baseUrl("openapi", "v1", "skills", parts[0], parts[1]));
        return toDto(data).withInstalled(skillService.getInstalledMarketplaceRefs(userId).contains(skillId));
    }

    public SkillResponseDto install(String skillId, Long userId) {
        String[] parts = validateAndSplitId(skillId);
        MarketplaceSkillDto metadata = get(skillId, userId);
        HttpUrl archiveUrl = baseUrl("api", "v1", "skills", parts[0], parts[1],
                "archive", "zip", "master");
        Request request = new Request.Builder().url(archiveUrl).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw upstreamError(response, "下载社区技能失败");
            }
            SkillPackageService.SkillInstallResult result = packageService.installArchive(
                    body.byteStream(), body.contentLength(), parts[1] + ".zip", userId);
            return skillService.registerMarketplacePackage(result, metadata, userId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "无法连接技能社区: " + e.getMessage(), e);
        }
    }

    private JsonNode requestJson(HttpUrl url) {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw upstreamError(response, "技能社区请求失败");
            }
            JsonNode root = objectMapper.readTree(body.string());
            if (!root.path("success").asBoolean(false) || root.path("data").isMissingNode()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "技能社区返回了无效数据");
            }
            return root.path("data");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "无法连接技能社区: " + e.getMessage(), e);
        }
    }

    private ResponseStatusException upstreamError(Response response, String fallback) {
        String message = fallback;
        try {
            if (response.body() != null) {
                String text = response.body().string();
                if (!text.isBlank()) {
                    message += ": " + text.substring(0, Math.min(text.length(), 300));
                }
            }
        } catch (IOException ignored) {
            // 保留通用错误信息
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    private MarketplaceSkillDto toDto(JsonNode node) {
        return new MarketplaceSkillDto(
                text(node, "id"),
                firstNonBlank(text(node, "display_name"), skillName(text(node, "id"))),
                text(node, "description"),
                text(node, "developer"),
                text(node, "owner"),
                text(node, "license"),
                text(node, "source_url"),
                text(node, "category"),
                stringList(node.path("tags")),
                text(node, "logo_url"),
                node.path("view_count").asLong(0),
                node.path("downloads").asLong(0),
                text(node, "last_modified"),
                stringList(node.path("install_command")),
                false);
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText());
            }
        });
        return List.copyOf(values);
    }

    private String[] validateAndSplitId(String skillId) {
        if (skillId == null || !MARKETPLACE_ID.matcher(skillId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的社区技能 ID");
        }
        return skillId.split("/", 2);
    }

    private HttpUrl baseUrl(String... pathSegments) {
        HttpUrl parsed = Objects.requireNonNull(HttpUrl.parse(marketplaceBaseUrl),
                "无效的技能社区地址");
        HttpUrl.Builder builder = parsed.newBuilder();
        for (String segment : pathSegments) {
            builder.addPathSegment(segment);
        }
        return builder.build();
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? "" : value.trim();
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private String skillName(String id) {
        if (id == null || id.isBlank()) {
            return "未命名技能";
        }
        int slash = id.lastIndexOf('/');
        return slash >= 0 ? id.substring(slash + 1) : id;
    }
}
