package top.jionjion.agentdesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.jionjion.agentdesk.dto.knowledge.RetrievalResultDto;
import top.jionjion.agentdesk.repository.KnowledgeSettingsRepository;

import java.util.List;

/**
 * 知识检索服务 - 封装检索逻辑 + 上下文构建
 * <p>
 * 供 ChatController 调用, 在聊天流程中集成知识检索
 *
 * @author Jion
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final KnowledgeService knowledgeService;
    private final KnowledgeSettingsRepository settingsRepo;
    private final int maxContextTokens;

    public KnowledgeRetrievalService(
            KnowledgeService knowledgeService,
            KnowledgeSettingsRepository settingsRepo,
            @Value("${agentdesk.knowledge.retrieve.max-context-tokens:2000}") int maxContextTokens) {
        this.knowledgeService = knowledgeService;
        this.settingsRepo = settingsRepo;
        this.maxContextTokens = maxContextTokens;
    }

    /**
     * 检查用户是否启用了知识检索
     */
    public boolean isEnabled(Long userId) {
        return settingsRepo.findById(userId)
                .map(s -> s.isEnabled())
                .orElse(true);
    }

    /**
     * 执行知识检索 (使用用户设置的 topK 和 scoreThreshold, 支持指定知识库)
     *
     * @param kbIds 指定的知识库 ID 列表, 为空则检索用户所有知识库
     */
    public List<RetrievalResultDto> retrieve(Long userId, String query, List<Long> kbIds) {
        try {
            var settings = settingsRepo.findById(userId).orElse(null);
            int k = settings != null ? settings.getTopK() : 5;
            double t = settings != null ? settings.getScoreThreshold() : 0.8;
            if (kbIds != null && !kbIds.isEmpty()) {
                return knowledgeService.retrieveFromBases(userId, query, kbIds, k, t);
            }
            return knowledgeService.retrieve(userId, query, k, t);
        } catch (Exception e) {
            log.warn("知识检索失败, 降级为无检索模式: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 构建增强消息 (将检索结果注入到用户消息前面)
     */
    public String buildAugmentedMessage(String originalMessage, List<RetrievalResultDto> results) {
        if (results.isEmpty()) return originalMessage;

        StringBuilder context = new StringBuilder();
        context.append("以下是来自知识库的参考资料, 请结合这些信息回答问题。如果参考资料与问题无关, 请忽略并直接回答:\n\n");

        int totalChars = 0;
        for (RetrievalResultDto result : results) {
            int chunkChars = result.content().length();
            if (totalChars + chunkChars > maxContextTokens) break;

            context.append(String.format("[来源: %s | 相关度: %.2f]\n", result.documentName(), result.score()));
            context.append(result.content());
            context.append("\n\n");
            totalChars += chunkChars;
        }

        context.append("---\n\n用户问题: ").append(originalMessage);
        return context.toString();
    }
}
