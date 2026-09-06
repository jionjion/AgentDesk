package top.jionjion.agentdesk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.agent.core.AgentPool;
import top.jionjion.agentdesk.dto.memory.AddMemoryRequest;
import top.jionjion.agentdesk.dto.memory.MemoryItemDto;
import top.jionjion.agentdesk.dto.memory.MemoryJobDto;
import top.jionjion.agentdesk.dto.memory.MemoryOperationsDto;
import top.jionjion.agentdesk.dto.memory.MemoryPageDto;
import top.jionjion.agentdesk.dto.memory.MemoryRevisionDto;
import top.jionjion.agentdesk.dto.memory.MemorySourceDto;
import top.jionjion.agentdesk.dto.memory.MemorySummaryDto;
import top.jionjion.agentdesk.dto.memory.UpdateMemoryRequest;
import top.jionjion.agentdesk.dto.settings.MemorySettingsDto;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.MemoryService;
import top.jionjion.agentdesk.service.SettingsService;
import top.jionjion.agentdesk.service.memory.MemoryJobService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 长期记忆控制器: 记忆配置 + 记忆 CRUD
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private static final int MAX_MEMORY_CONTENT_LENGTH = 2048;

    private final SettingsService settingsService;
    private final MemoryService memoryService;
    private final MemoryJobService memoryJobService;
    private final AgentPool agentPool;
    private final ObjectMapper objectMapper;

    public MemoryController(SettingsService settingsService,
                            MemoryService memoryService,
                            MemoryJobService memoryJobService,
                            AgentPool agentPool,
                            ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.memoryService = memoryService;
        this.memoryJobService = memoryJobService;
        this.agentPool = agentPool;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取记忆配置
     */
    @GetMapping("/settings")
    public MemorySettingsDto getSettings() {
        return settingsService.getMemorySettings(UserContext.getUserId());
    }

    /**
     * 更新记忆配置
     */
    @PutMapping("/settings")
    public MemorySettingsDto updateSettings(@RequestBody MemorySettingsDto request) {
        MemorySettingsDto result = settingsService.updateMemorySettings(request);
        // 记忆配置变更后重建 Agent
        agentPool.invalidateAll(UserContext.getUserId());
        return result;
    }

    /**
     * 获取用户所有记忆条目
     */
    @GetMapping("/list")
    public List<MemoryItemDto> listMemories(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId) {
        return memoryService.listMemories(UserContext.getUserId()).stream()
                .filter(item -> scopeType == null || scopeType.equalsIgnoreCase(item.scopeType()))
                .filter(item -> scopeId == null || scopeId.equals(item.scopeId()))
                .toList();
    }

    @GetMapping("/items")
    public MemoryPageDto listMemoryItems(@RequestParam(required = false) String scopeType,
                                         @RequestParam(required = false) String scopeId,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String query,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        return memoryService.listMemories(UserContext.getUserId(), scopeType, scopeId,
                category, status, query, page, pageSize);
    }

    @GetMapping("/summary")
    public MemorySummaryDto summary(@RequestParam(required = false) String scopeType,
                                    @RequestParam(required = false) String scopeId) {
        return memoryService.summary(UserContext.getUserId(), scopeType, scopeId);
    }

    /**
     * 手动添加一条记忆
     */
    @PostMapping
    public MemoryItemDto addMemory(@RequestBody AddMemoryRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容不能为空");
        }
        if (request.content().length() > MAX_MEMORY_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容最长2048字符");
        }
        return memoryService.addMemory(UserContext.getUserId(), request.content(),
                request.scopeType(), request.scopeId(), request.category(), request.validUntil(),
                request.importance(), Boolean.TRUE.equals(request.sensitiveConfirmed()));
    }

    /**
     * 删除单条记忆
     */
    @DeleteMapping("/{memoryId}")
    public Map<String, String> deleteMemory(@PathVariable String memoryId) {
        memoryService.deleteMemory(UserContext.getUserId(), memoryId);
        return Map.of("message", "记忆已删除");
    }

    /**
     * 修改一条记忆内容
     */
    @PutMapping("/{memoryId}")
    public MemoryItemDto updateMemory(@PathVariable String memoryId,
                                      @RequestBody UpdateMemoryRequest request) {
        if (request.content() != null && !request.content().isBlank()
                && request.content().length() > MAX_MEMORY_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容最长2048字符");
        }
        return memoryService.updateMemory(UserContext.getUserId(), memoryId, request);
    }

    /**
     * 清空所有记忆
     */
    @PutMapping("/{memoryId}/pin")
    public MemoryItemDto pinMemory(@PathVariable String memoryId,
                                    @RequestBody Map<String, Boolean> request) {
        return memoryService.setPinned(UserContext.getUserId(), memoryId,
                Boolean.TRUE.equals(request.get("pinned")));
    }

    @GetMapping("/{memoryId}/sources")
    public List<MemorySourceDto> listSources(@PathVariable String memoryId) {
        return memoryService.listSources(UserContext.getUserId(), memoryId);
    }

    @DeleteMapping("/{memoryId}/sources/{sourceId}")
    public Map<String, String> revokeSource(@PathVariable String memoryId, @PathVariable String sourceId) {
        memoryService.revokeSource(UserContext.getUserId(), memoryId, sourceId);
        return Map.of("message", "记忆来源已撤销");
    }

    @GetMapping("/{memoryId}/revisions")
    public List<MemoryRevisionDto> listRevisions(@PathVariable String memoryId) {
        return memoryService.listRevisions(UserContext.getUserId(), memoryId);
    }

    @PostMapping("/{memoryId}/restore/{revisionId}")
    public MemoryItemDto restoreRevision(@PathVariable String memoryId, @PathVariable String revisionId) {
        return memoryService.restoreRevision(UserContext.getUserId(), memoryId, revisionId);
    }

    @PostMapping("/conflicts/{memoryId}/resolve")
    public MemoryItemDto resolveConflict(@PathVariable String memoryId,
                                         @RequestBody Map<String, String> request) {
        return memoryService.resolveConflict(UserContext.getUserId(), memoryId, request.get("action"));
    }

    @PostMapping("/import-legacy")
    public Map<String, Object> importLegacy() {
        try {
            int imported = memoryService.importLegacy(UserContext.getUserId());
            return Map.of("message", "旧版记忆导入完成", "count", imported);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "无法导入旧版 Mem0 记忆: " + ex.getMessage());
        }
    }

    @DeleteMapping("/all")
    public Map<String, Object> deleteAllMemories(@RequestParam(required = false) String scopeType,
                                                 @RequestParam(required = false) String scopeId) {
        Long userId = UserContext.getUserId();
        // 先按同一范围取消未完成的抽取任务, 阻止清空后旧任务把内容重新写回
        memoryJobService.cancelPendingForScope(userId, scopeType, scopeId);
        int count;
        if (scopeType == null && scopeId == null) {
            count = memoryService.deleteAllMemories(userId);
        } else {
            count = memoryService.deleteMemories(userId, scopeType, scopeId);
        }
        return Map.of("message", "记忆已清空", "count", count);
    }

    @GetMapping("/jobs")
    public List<MemoryJobDto> jobs(@RequestParam(required = false) String status) {
        return memoryJobService.listJobs(UserContext.getUserId(), status);
    }

    @PostMapping("/jobs/{jobId}/retry")
    public MemoryJobDto retryJob(@PathVariable Long jobId) {
        return memoryJobService.retryDeadJob(UserContext.getUserId(), jobId);
    }

    @GetMapping("/operations")
    public MemoryOperationsDto operations() {
        Long userId = UserContext.getUserId();
        var provider = memoryService.providerCircuitStatus();
        return new MemoryOperationsDto(memoryJobService.jobCounts(userId),
                memoryService.countByStatus(userId, "ACTIVE"),
                memoryService.countByStatus(userId, "CONFLICTED"),
                memoryService.countByStatus(userId, "EXPIRED"),
                memoryService.countUsageEvents(userId), provider.state(),
                provider.consecutiveFailures(), provider.openUntil());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "markdown") String format) throws Exception {
        List<MemoryItemDto> items = memoryService.listExportableMemories(UserContext.getUserId());
        byte[] body;
        String filename;
        MediaType mediaType;
        if ("json".equalsIgnoreCase(format)) {
            body = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(items);
            filename = "agentdesk-memories.json";
            mediaType = MediaType.APPLICATION_JSON;
        } else {
            StringBuilder markdown = new StringBuilder("# AgentDesk 记忆导出\n\n");
            for (MemoryItemDto item : items) {
                markdown.append("- [").append(item.scopeType()).append('/').append(item.category())
                        .append("] ").append(item.memory().replace("\n", " ")).append("\n");
            }
            body = markdown.toString().getBytes(StandardCharsets.UTF_8);
            filename = "agentdesk-memories.md";
            mediaType = new MediaType("text", "markdown", StandardCharsets.UTF_8);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentType(mediaType);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
