package top.jionjion.agentdesk.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.agent.AgentPool;
import top.jionjion.agentdesk.dto.AddMemoryRequest;
import top.jionjion.agentdesk.dto.MemoryItemDto;
import top.jionjion.agentdesk.dto.MemorySettingsDto;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.MemoryService;
import top.jionjion.agentdesk.service.SettingsService;

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

    private final SettingsService settingsService;
    private final MemoryService memoryService;
    private final AgentPool agentPool;

    public MemoryController(SettingsService settingsService,
                            MemoryService memoryService,
                            AgentPool agentPool) {
        this.settingsService = settingsService;
        this.memoryService = memoryService;
        this.agentPool = agentPool;
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
    public List<MemoryItemDto> listMemories() {
        return memoryService.listMemories(UserContext.getUserId());
    }

    /**
     * 手动添加一条记忆
     */
    @PostMapping
    public Map<String, String> addMemory(@RequestBody AddMemoryRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容不能为空");
        }
        if (request.content().length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容最长2048字符");
        }
        memoryService.addMemory(UserContext.getUserId(), request.content());
        return Map.of("message", "记忆添加成功");
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
    public Map<String, String> updateMemory(@PathVariable String memoryId,
                                             @RequestBody AddMemoryRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容不能为空");
        }
        if (request.content().length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容最长2048字符");
        }
        memoryService.updateMemory(UserContext.getUserId(), memoryId, request.content());
        return Map.of("message", "记忆已更新");
    }

    /**
     * 清空所有记忆
     */
    @DeleteMapping("/all")
    public Map<String, String> deleteAllMemories() {
        memoryService.deleteAllMemories(UserContext.getUserId());
        return Map.of("message", "所有记忆已清空");
    }
}
