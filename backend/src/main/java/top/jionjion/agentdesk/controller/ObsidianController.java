package top.jionjion.agentdesk.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.service.ObsidianService;

import java.util.List;
import java.util.Map;

/**
 * Obsidian 知识沉淀控制器
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/obsidian")
public class ObsidianController {

    private final ObsidianService obsidianService;

    public ObsidianController(ObsidianService obsidianService) {
        this.obsidianService = obsidianService;
    }

    /**
     * 导出单条消息到 Obsidian
     */
    @PostMapping("/export-message")
    public Map<String, String> exportMessage(@RequestBody Map<String, Object> body) {
        Object rawId = body.get("messageId");
        if (rawId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messageId 不能为空");
        }
        Long messageId = ((Number) rawId).longValue();
        String category = (String) body.get("category");
        obsidianService.exportMessage(messageId, category);
        return Map.of("message", "已沉淀到 Obsidian");
    }

    /**
     * 导出整个会话到 Obsidian
     * <p>
     * 请求体可携带 auto=true 表示自动触发, 此时会先进行 AI 价值判断
     */
    @PostMapping("/export-session")
    public Map<String, String> exportSession(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId 不能为空");
        }
        Boolean auto = (Boolean) body.getOrDefault("auto", false);
        String result = obsidianService.exportSession(sessionId, Boolean.TRUE.equals(auto));
        return Map.of("message", result);
    }

    /**
     * 验证 Vault 路径是否有效
     */
    @PostMapping("/validate-path")
    public Map<String, Object> validatePath(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        return obsidianService.validatePath(path);
    }

    /**
     * 获取已有的分类目录列表
     */
    @GetMapping("/categories")
    public List<String> getCategories() {
        return obsidianService.listCategories();
    }
}
