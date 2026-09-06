package top.jionjion.agentdesk.service;

import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.memory.MemoryItemDto;
import top.jionjion.agentdesk.dto.memory.MemoryPageDto;
import top.jionjion.agentdesk.dto.memory.MemoryRecallResult;
import top.jionjion.agentdesk.dto.memory.MemoryRevisionDto;
import top.jionjion.agentdesk.dto.memory.MemorySourceDto;
import top.jionjion.agentdesk.dto.memory.MemorySummaryDto;
import top.jionjion.agentdesk.dto.memory.UpdateMemoryRequest;
import top.jionjion.agentdesk.dto.settings.MemorySettingsDto;
import top.jionjion.agentdesk.entity.MemoryEntry;
import top.jionjion.agentdesk.entity.MemoryRevision;
import top.jionjion.agentdesk.entity.MemorySource;
import top.jionjion.agentdesk.entity.MemoryUsageEvent;
import top.jionjion.agentdesk.repository.MemoryEntryRepository;
import top.jionjion.agentdesk.repository.MemoryRevisionRepository;
import top.jionjion.agentdesk.repository.MemorySourceRepository;
import top.jionjion.agentdesk.repository.MemoryUsageEventRepository;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.service.memory.Mem0Client;
import top.jionjion.agentdesk.service.memory.MemoryPolicyEngine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical memory facade. All reads and user CRUD are served from PostgreSQL.
 * Mem0 is intentionally limited to extracting candidate facts from original user text.
 */
@Service
public class MemoryService {
    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final String ACTIVE = "ACTIVE";
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static final int MAX_RECALL_ITEMS = 6;
    private static final int MAX_RECALL_CHARS = 4000;
    private static final int MAX_MEMORY_CHARS = 2048;
    /** Provider 抽取候选中常见的主语/谓词, 不作为原文支持度的判定依据。 */
    private static final Set<String> GENERIC_CANDIDATE_TERMS = Set.of(
            "用户", "喜欢", "偏好", "倾向", "希望", "要求",
            "user", "users", "prefer", "prefers", "like", "likes");
    /** 参与否定极性校验的偏好/使用类动词。 */
    private static final List<String> POLARITY_VERBS_CJK = List.of(
            "喜欢", "偏好", "倾向", "使用", "需要", "要求", "支持", "采用");
    private static final List<String> POLARITY_VERBS_EN = List.of(
            "like", "likes", "prefer", "prefers", "use", "uses", "want", "wants", "need", "needs");

    private final MemoryEntryRepository entries;
    private final MemorySourceRepository sources;
    private final MemoryRevisionRepository revisions;
    private final ProjectRepository projects;
    private final SettingsService settingsService;
    private final Mem0Client mem0Client;
    private final MemoryPolicyEngine policy;
    private final MemoryUsageEventRepository usageEvents;

    public MemoryService(MemoryEntryRepository entries,
                         MemorySourceRepository sources,
                         MemoryRevisionRepository revisions,
                         ProjectRepository projects,
                         SettingsService settingsService,
                         Mem0Client mem0Client,
                         MemoryPolicyEngine policy,
                         MemoryUsageEventRepository usageEvents) {
        this.entries = entries;
        this.sources = sources;
        this.revisions = revisions;
        this.projects = projects;
        this.settingsService = settingsService;
        this.mem0Client = mem0Client;
        this.policy = policy;
        this.usageEvents = usageEvents;
    }

    public boolean isEnabled(Long userId) {
        return userId != null && Boolean.TRUE.equals(settingsService.getMemorySettings(userId).enabled());
    }

    public boolean shouldAutoLearn(Long userId, String projectId) {
        if (userId == null) return false;
        MemorySettingsDto settings = settingsService.getMemorySettings(userId);
        return Boolean.TRUE.equals(settings.enabled())
                && Boolean.TRUE.equals(settings.autoLearningEnabled())
                && (projectId == null || Boolean.TRUE.equals(settings.projectMemoryEnabled()));
    }

    public List<MemoryItemDto> listMemories(Long userId) {
        return entries.findByUserIdAndStatusOrderByPinnedDescUpdatedAtDesc(userId, ACTIVE)
                .stream().map(entry -> toDto(entry, null, 0d)).toList();
    }

    /** Data-portability view: every retained state except deletion tombstones. */
    public List<MemoryItemDto> listExportableMemories(Long userId) {
        return entries.findByUserIdOrderByPinnedDescUpdatedAtDesc(userId).stream()
                .filter(entry -> !"DELETED".equals(entry.getStatus()))
                .map(entry -> toDto(entry, null, 0d))
                .toList();
    }

    public MemoryPageDto listMemories(Long userId, String scopeType, String scopeId, String category,
                                      String status, String query, int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        String normalizedScope = normalizeOptional(scopeType);
        String normalizedCategory = normalizeOptional(category);
        String normalizedStatus = normalizeOptional(status);
        String normalizedQuery = query == null ? null : canonicalize(query);
        Set<String> queryTerms = terms(normalizedQuery);
        List<MemoryItemDto> filtered = entries.findByUserIdOrderByPinnedDescUpdatedAtDesc(userId).stream()
                .filter(entry -> normalizedScope == null || normalizedScope.equals(entry.getScopeType()))
                .filter(entry -> scopeId == null || Objects.equals(scopeId, entry.getScopeId()))
                .filter(entry -> normalizedCategory == null || normalizedCategory.equals(entry.getCategory()))
                .filter(entry -> normalizedStatus == null ? ACTIVE.equals(entry.getStatus())
                        : normalizedStatus.equals(entry.getStatus()))
                .filter(entry -> normalizedQuery == null || normalizedQuery.isBlank()
                        || entry.getCanonicalContent().contains(normalizedQuery)
                        || terms(entry.getCanonicalContent()).stream().anyMatch(queryTerms::contains))
                .map(entry -> toDto(entry, null, 0d))
                .toList();
        int from = (int) Math.min(filtered.size(), (long) safePage * safeSize);
        int to = Math.min(filtered.size(), from + safeSize);
        return new MemoryPageDto(filtered.subList(from, to), filtered.size(), safePage, safeSize);
    }

    public MemorySummaryDto summary(Long userId, String scopeType, String scopeId) {
        String normalizedScope = normalizeOptional(scopeType);
        long now = System.currentTimeMillis();
        List<MemoryItemDto> items = entries.findByUserIdAndStatusOrderByPinnedDescUpdatedAtDesc(userId, ACTIVE).stream()
                .filter(entry -> entry.getValidUntil() == null || entry.getValidUntil() > now)
                .filter(entry -> normalizedScope == null || normalizedScope.equals(entry.getScopeType()))
                .filter(entry -> scopeId == null || Objects.equals(scopeId, entry.getScopeId()))
                .map(entry -> toDto(entry, null, 0d))
                .toList();
        Map<String, Long> categories = items.stream().collect(java.util.stream.Collectors.groupingBy(
                MemoryItemDto::category, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
        Long updatedAt = items.stream().map(MemoryItemDto::updatedAt).filter(Objects::nonNull)
                .map(Long::valueOf).max(Long::compareTo).orElse(null);
        List<MemoryItemDto> highlights = items.stream()
                .sorted(Comparator.comparing(MemoryItemDto::pinned).reversed()
                        .thenComparing(Comparator.comparingDouble(MemoryItemDto::importance).reversed()))
                .limit(6).toList();
        return new MemorySummaryDto(normalizedScope, scopeId, items.size(),
                items.stream().filter(MemoryItemDto::pinned).count(), updatedAt, categories, highlights);
    }

    public long countByStatus(Long userId, String status) {
        return entries.countByUserIdAndStatus(userId, status);
    }

    public long countUsageEvents(Long userId) {
        return usageEvents.countByUserId(userId);
    }

    public Mem0Client.CircuitStatus providerCircuitStatus() {
        return mem0Client.circuitStatus();
    }

    public List<MemoryItemDto> searchMemories(Long userId, String query) {
        return recall(userId, null, query, "NORMAL").items();
    }

    /** Exact lookup for Agent tools, constrained to USER and the current PROJECT scope. */
    public Optional<MemoryItemDto> findAccessibleMemory(Long userId, String memoryId, String projectId) {
        if (userId == null || memoryId == null) return Optional.empty();
        MemorySettingsDto settings = settingsService.getMemorySettings(userId);
        if (!Boolean.TRUE.equals(settings.enabled())) return Optional.empty();
        try {
            return entries.findByIdAndUserId(Long.valueOf(memoryId), userId)
                    .filter(entry -> ACTIVE.equals(entry.getStatus()))
                    .filter(entry -> entry.getValidUntil() == null || entry.getValidUntil() > System.currentTimeMillis())
                    .filter(entry -> "USER".equals(entry.getScopeType())
                            || (Boolean.TRUE.equals(settings.projectMemoryEnabled())
                            && "PROJECT".equals(entry.getScopeType())
                            && Objects.equals(projectId, entry.getScopeId())))
                    .map(entry -> toDto(entry, null, 0d));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /** Scope-safe, bounded recall. USER memories and only the current PROJECT are eligible. */
    public MemoryRecallResult recall(Long userId, String projectId, String query, String memoryMode) {
        long started = System.currentTimeMillis();
        if (userId == null || "NO_MEMORY".equalsIgnoreCase(memoryMode)) {
            return MemoryRecallResult.disabled("temporary_no_memory", System.currentTimeMillis() - started);
        }
        try {
            MemorySettingsDto recallSettings = settingsService.getMemorySettings(userId);
            if (!Boolean.TRUE.equals(recallSettings.enabled())) {
                return MemoryRecallResult.disabled(
                        "memory_disabled", System.currentTimeMillis() - started);
            }
            long now = System.currentTimeMillis();
            Set<String> queryTerms = terms(query);
            List<Scored> scored = entries.findTop500ByUserIdAndStatusOrderByPinnedDescUpdatedAtDesc(userId, ACTIVE)
                    .stream()
                    .filter(e -> e.getValidUntil() == null || e.getValidUntil() > now)
                    .filter(e -> "USER".equals(e.getScopeType()) ||
                            (Boolean.TRUE.equals(recallSettings.projectMemoryEnabled())
                                    && "PROJECT".equals(e.getScopeType())
                                    && Objects.equals(projectId, e.getScopeId())))
                    .map(e -> new Scored(e, score(e, queryTerms)))
                    .filter(s -> s.entry().isPinned() || s.score() >= 0.08d)
                    .sorted(Comparator.comparingDouble(Scored::score).reversed())
                    .toList();

            int chars = 0;
            List<MemoryItemDto> result = new ArrayList<>();
            List<MemoryEntry> recalled = new ArrayList<>();
            for (Scored candidate : scored) {
                if (result.size() >= MAX_RECALL_ITEMS || chars + candidate.entry().getContent().length() > MAX_RECALL_CHARS) {
                    break;
                }
                MemoryEntry entry = candidate.entry();
                String reason = entry.isPinned() ? "pinned" : "relevance_match";
                result.add(toDto(entry, reason, candidate.score()));
                chars += entry.getContent().length();
                entry.setLastRecalledAt(now);
                entry.setRecallCount(entry.getRecallCount() + 1);
                recalled.add(entry);
            }
            if (!recalled.isEmpty()) {
                entries.saveAll(recalled);
            }
            long elapsed = System.currentTimeMillis() - started;
            recordUsage(userId, projectId, result, elapsed);
            return new MemoryRecallResult(result.isEmpty() ? "EMPTY" : "USED", result, elapsed, null);
        } catch (RuntimeException ex) {
            log.warn("Memory recall degraded for userId={}, projectId={}: {}",
                    userId, projectId, ex.getClass().getSimpleName());
            return MemoryRecallResult.degraded(ex.getClass().getSimpleName(), System.currentTimeMillis() - started);
        }
    }

    @Transactional
    public MemoryItemDto addMemory(Long userId, String content) {
        requireMemoryEnabled(userId);
        return addMemory(userId, content, "USER", null, null, "MANUAL", null,
                null, null, content, 1d, null, null, false, true);
    }

    @Transactional
    public MemoryItemDto addMemory(Long userId, String content, String scopeType,
                                   String scopeId, String category) {
        requireMemoryEnabled(userId);
        return addMemory(userId, content, scopeType, scopeId, category,
                "MANUAL", null, null, null, content, 1d, null, null, false, true);
    }

    @Transactional
    public MemoryItemDto addMemory(Long userId, String content, String scopeType,
                                   String scopeId, String category, Long validUntil,
                                   Double importance, boolean sensitiveConfirmed) {
        requireMemoryEnabled(userId);
        return addMemory(userId, content, scopeType, scopeId, category,
                "MANUAL", null, null, null, content, 1d,
                validUntil, importance, sensitiveConfirmed, true);
    }

    /** 对话内显式"记住": 与手工新增同为显式语义, 但保留真实的会话与用户消息来源以支持级联失效。 */
    @Transactional
    public MemoryItemDto addExplicitChatMemory(Long userId, String content, String scopeType,
                                               String scopeId, String category,
                                               String sessionId, Long userMessageId) {
        requireMemoryEnabled(userId);
        return addMemory(userId, content, scopeType, scopeId, category,
                "CHAT", userMessageId == null ? null : String.valueOf(userMessageId),
                sessionId, null, content, 1d, null, null, false, true);
    }

    @Transactional
    public void updateMemory(Long userId, String memoryId, String newContent) {
        updateMemory(userId, memoryId, new UpdateMemoryRequest(
                newContent, null, null, null, null, null, false));
    }

    @Transactional
    public MemoryItemDto updateMemory(Long userId, String memoryId, UpdateMemoryRequest request) {
        MemoryEntry entry = requireOwned(userId, memoryId);
        String old = entry.getContent();
        String newContent = request.content() == null || request.content().isBlank()
                ? old : request.content().trim();
        String requestedScopeId = request.scopeType() == null && request.scopeId() == null
                ? entry.getScopeId() : request.scopeId();
        MemoryPolicyEngine.Decision decision = policy.evaluate(newContent, true,
                Boolean.TRUE.equals(request.sensitiveConfirmed()),
                request.category() == null ? entry.getCategory() : request.category(), requestedScopeId);
        Scope scope = validateScope(userId,
                request.scopeType() == null ? entry.getScopeType() : request.scopeType(), requestedScopeId);
        String canonical = canonicalize(newContent);
        String newHash = hash(canonical);
        entries.findActiveDuplicate(userId, scope.type(), scope.id(), newHash)
                .filter(existing -> !existing.getId().equals(entry.getId()))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "已存在相同记忆");
                });
        entry.setScopeType(scope.type());
        entry.setScopeId(scope.id());
        entry.setContent(newContent);
        entry.setCanonicalContent(canonical);
        entry.setContentHash(newHash);
        entry.setCategory(decision.category());
        entry.setSensitivity(decision.sensitivity());
        entry.setSubjectKey(decision.subjectKey());
        if (request.importance() != null) entry.setImportance(clamp(request.importance(), 0d, 1d));
        if (request.validUntil() != null) entry.setValidUntil(request.validUntil() <= 0 ? null : request.validUntil());
        entry.setUpdatedAt(System.currentTimeMillis());
        entries.save(entry);
        revisions.save(revision(entry, old, entry.getContent(), "EDIT", "USER"));
        if (entry.getProviderRef() != null) {
            try {
                mem0Client.update(entry.getProviderRef(), entry.getContent());
                entry.setProviderSyncPending(false);
                entries.save(entry);
            } catch (Exception ex) {
                entry.setProviderSyncPending(true);
                entries.save(entry);
                log.warn("Provider memory update failed for memoryId={}: {}", entry.getId(), ex.getMessage());
            }
        }
        return toDto(entry, null, 0d);
    }

    @Transactional
    public void deleteMemory(Long userId, String memoryId) {
        // 允许删除 ACTIVE/CONFLICTED/EXPIRED/SUPERSEDED 等所有非删除状态, 与管理页状态筛选一致
        MemoryEntry entry = requireManageable(userId, memoryId);
        softDelete(entry, "DELETE", "USER");
    }

    @Transactional
    public int deleteAllMemories(Long userId) {
        int count = deleteMemories(userId, null, null);
        try {
            mem0Client.deleteAll(userId);
        } catch (Exception ex) {
            log.warn("Provider memory clear failed for userId={}: {}", userId, ex.getMessage());
        }
        return count;
    }

    @Transactional
    public int deleteMemories(Long userId, String scopeType, String scopeId) {
        String normalizedScope = normalizeOptional(scopeType);
        int count = 0;
        for (MemoryEntry entry : entries.findByUserIdOrderByPinnedDescUpdatedAtDesc(userId)) {
            if ("DELETED".equals(entry.getStatus())) continue;
            if (normalizedScope != null && !normalizedScope.equals(entry.getScopeType())) continue;
            if (scopeId != null && !Objects.equals(scopeId, entry.getScopeId())) continue;
            softDelete(entry, "CLEAR_SCOPE", "USER");
            count++;
        }
        return count;
    }

    @Transactional
    public MemoryItemDto setPinned(Long userId, String memoryId, boolean pinned) {
        MemoryEntry entry = requireOwned(userId, memoryId);
        entry.setPinned(pinned);
        entry.setUpdatedAt(System.currentTimeMillis());
        entries.save(entry);
        revisions.save(revision(entry, entry.getContent(), entry.getContent(), pinned ? "PIN" : "UNPIN", "USER"));
        return toDto(entry, null, 0d);
    }

    public List<MemorySourceDto> listSources(Long userId, String memoryId) {
        MemoryEntry entry = requireManageable(userId, memoryId);
        return sources.findByMemoryIdAndUserIdOrderByCreatedAtDesc(entry.getId(), userId).stream()
                .map(source -> new MemorySourceDto(String.valueOf(source.getId()), source.getSourceType(),
                        source.getSourceId(), source.getSessionId(), source.getProjectId(),
                        source.getEvidenceExcerpt(), source.getTrustLevel(), source.getCreatedAt()))
                .toList();
    }

    public List<MemoryRevisionDto> listRevisions(Long userId, String memoryId) {
        MemoryEntry entry = requireManageable(userId, memoryId);
        return revisions.findByMemoryIdAndUserIdOrderByCreatedAtDesc(entry.getId(), userId).stream()
                .map(item -> new MemoryRevisionDto(String.valueOf(item.getId()), item.getOldContent(),
                        item.getNewContent(), item.getReason(), item.getActor(), item.getCreatedAt()))
                .toList();
    }

    @Transactional
    public MemoryItemDto restoreRevision(Long userId, String memoryId, String revisionId) {
        MemoryEntry entry = requireManageable(userId, memoryId);
        MemoryRevision historical = revisions.findByIdAndMemoryIdAndUserId(
                        parseId(revisionId), entry.getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "修订版本不存在"));
        String content = historical.getOldContent();
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该版本没有可恢复的正文");
        }
        if (!ACTIVE.equals(entry.getStatus())) {
            entry.setStatus(ACTIVE);
            entry.setDeletedAt(null);
            entries.save(entry);
        }
        return updateMemory(userId, memoryId,
                new UpdateMemoryRequest(content, null, null, null, null, null, true));
    }

    @Transactional
    public void revokeSource(Long userId, String memoryId, String sourceId) {
        MemoryEntry entry = requireManageable(userId, memoryId);
        MemorySource source = sources.findByIdAndMemoryIdAndUserId(
                        parseId(sourceId), entry.getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记忆来源不存在"));
        sources.delete(source);
        sources.flush();
        if (sources.countByMemoryId(entry.getId()) == 0) {
            softDelete(entry, "SOURCE_REVOKED", "USER");
        } else {
            revisions.save(revision(entry, entry.getContent(), entry.getContent(), "SOURCE_REVOKED", "USER"));
        }
    }

    @Transactional
    public MemoryItemDto resolveConflict(Long userId, String memoryId, String action) {
        MemoryEntry candidate = requireOwnedWithStatus(userId, memoryId, "CONFLICTED");
        MemoryEntry previous = candidate.getSupersedesId() == null ? null
                : entries.findByIdAndUserId(candidate.getSupersedesId(), userId).orElse(null);
        String normalized = action == null ? "" : action.toUpperCase(Locale.ROOT);
        if ("KEEP_NEW".equals(normalized)) {
            candidate.setStatus(ACTIVE);
            if (previous != null && ACTIVE.equals(previous.getStatus())) {
                previous.setStatus("SUPERSEDED");
                previous.setUpdatedAt(System.currentTimeMillis());
                entries.save(previous);
                revisions.save(revision(previous, previous.getContent(), null, "SUPERSEDED", "USER"));
            }
        } else if ("KEEP_OLD".equals(normalized)) {
            softDelete(candidate, "CONFLICT_KEEP_OLD", "USER");
            return toDto(candidate, null, 0d);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "冲突处理只能是 KEEP_NEW 或 KEEP_OLD");
        }
        candidate.setUpdatedAt(System.currentTimeMillis());
        entries.save(candidate);
        revisions.save(revision(candidate, candidate.getContent(), candidate.getContent(),
                "CONFLICT_RESOLVED_" + normalized, "USER"));
        return toDto(candidate, null, 0d);
    }

    @Transactional
    public int expireDueMemories() {
        int count = 0;
        long now = System.currentTimeMillis();
        for (String status : List.of(ACTIVE, "CONFLICTED")) {
            for (MemoryEntry entry : entries.findByStatusAndValidUntilLessThanEqual(status, now)) {
                entry.setStatus("EXPIRED");
                entry.setUpdatedAt(now);
                entries.save(entry);
                revisions.save(revision(entry, entry.getContent(), entry.getContent(), "EXPIRED", "SYSTEM"));
                count++;
            }
        }
        return count;
    }

    /** Called only by the durable job worker. */
    @Transactional
    public int extractAndStore(Long userId, String sessionId, String projectId,
                               Long userMessageId, String originalUserMessage) throws Exception {
        MemorySettingsDto settings = settingsService.getMemorySettings(userId);
        if (!Boolean.TRUE.equals(settings.enabled()) || !Boolean.TRUE.equals(settings.autoLearningEnabled())) {
            return 0;
        }
        if (projectId != null && !Boolean.TRUE.equals(settings.projectMemoryEnabled())) {
            return 0;
        }
        List<Mem0Client.ExtractedMemory> extracted = mem0Client.extract(userId, originalUserMessage);
        String scopeType = projectId != null && Boolean.TRUE.equals(settings.projectMemoryEnabled()) ? "PROJECT" : "USER";
        String scopeId = "PROJECT".equals(scopeType) ? projectId : null;
        // 原文本身为假设/转述/指令型时, 即使 provider 改写为肯定句也不能入库
        boolean stableOriginal = policy.stableEvidence(originalUserMessage);
        int stored = 0;
        for (Mem0Client.ExtractedMemory candidate : extracted) {
            if (!stableOriginal || !supportedByOriginal(candidate.content(), originalUserMessage)) {
                log.debug("Memory candidate rejected: no reliable evidence in original message");
                cleanupProviderRef(userId, candidate.providerRef());
                continue;
            }
            MemoryItemDto saved = addMemory(userId, candidate.content(), scopeType, scopeId, null,
                    "CHAT", String.valueOf(userMessageId), sessionId, candidate.providerRef(),
                    originalUserMessage, 0.85d, null, null, false, false);
            if (saved != null) stored++;
        }
        return stored;
    }

    /** One-time, explicit import path for installations that already used Mem0 directly. */
    @Transactional
    public int importLegacy(Long userId) throws Exception {
        int imported = 0;
        for (Mem0Client.ExtractedMemory item : mem0Client.list(userId)) {
            MemoryItemDto saved = addMemory(userId, item.content(), "USER", null, null,
                    "LEGACY_IMPORT", item.providerRef(), null, item.providerRef(), item.content(), 0.8d,
                    null, null, false, false);
            if (saved != null) imported++;
        }
        return imported;
    }

    /** Removes deleted-chat evidence and retires memories that have no remaining source. */
    @Transactional
    public void invalidateSessionSources(Long userId, String sessionId) {
        List<MemorySource> removed = sources.findByUserIdAndSessionId(userId, sessionId);
        if (removed.isEmpty()) return;
        Set<Long> affected = new HashSet<>();
        removed.forEach(source -> affected.add(source.getMemoryId()));
        sources.deleteAll(removed);
        sources.flush();
        for (Long memoryId : affected) {
            if (sources.countByMemoryId(memoryId) == 0) {
                entries.findByIdAndUserId(memoryId, userId)
                        .filter(entry -> !"DELETED".equals(entry.getStatus()))
                        .ifPresent(entry -> softDelete(entry, "SOURCE_DELETED", "SYSTEM"));
            }
        }
    }

    @Transactional
    public void invalidateMessageSources(Long userId, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return;
        List<String> ids = messageIds.stream().map(String::valueOf).toList();
        List<MemorySource> removed = sources.findByUserIdAndSourceTypeAndSourceIdIn(userId, "CHAT", ids);
        if (removed.isEmpty()) return;
        Set<Long> affected = new HashSet<>();
        removed.forEach(source -> affected.add(source.getMemoryId()));
        sources.deleteAll(removed);
        sources.flush();
        for (Long memoryId : affected) {
            if (sources.countByMemoryId(memoryId) == 0) {
                entries.findByIdAndUserId(memoryId, userId)
                        .filter(entry -> !"DELETED".equals(entry.getStatus()))
                        .ifPresent(entry -> softDelete(entry, "BRANCH_DELETED", "SYSTEM"));
            }
        }
    }

    /** Project-scoped memory cannot silently become global when a project is deleted. */
    @Transactional
    public void deleteProjectScope(Long userId, String projectId) {
        for (String status : List.of(ACTIVE, "CONFLICTED", "EXPIRED", "SUPERSEDED")) {
            for (MemoryEntry entry : entries.findByUserIdAndScopeTypeAndScopeIdAndStatus(
                    userId, "PROJECT", projectId, status)) {
                softDelete(entry, "PROJECT_DELETED", "SYSTEM");
            }
        }
    }

    /** Eventual provider cleanup after a transient outage during local deletion. */
    @Transactional
    public int reconcileProviderDeletions() {
        int cleared = 0;
        for (String status : List.of("DELETED", "SUPERSEDED")) {
            for (MemoryEntry entry : entries.findTop100ByStatusAndProviderRefIsNotNullOrderByUpdatedAtAsc(status)) {
                try {
                    mem0Client.delete(entry.getProviderRef());
                    if ("REJECTED".equals(entry.getWritePolicy())) {
                        // 被策略拒绝候选的清理墓碑: provider 条目删除成功后物理清除, 不留垃圾行
                        entries.delete(entry);
                    } else {
                        entry.setProviderRef(null);
                        entry.setProviderName(null);
                        entries.save(entry);
                    }
                    cleared++;
                } catch (Exception ex) {
                    log.debug("Provider deletion still pending for memoryId={}: {}", entry.getId(), ex.getMessage());
                }
            }
        }
        return cleared;
    }

    @Transactional
    public int reconcileProviderUpdates() {
        int cleared = 0;
        for (MemoryEntry entry : entries
                .findTop100ByStatusAndProviderSyncPendingTrueAndProviderRefIsNotNullOrderByUpdatedAtAsc(ACTIVE)) {
            try {
                mem0Client.update(entry.getProviderRef(), entry.getContent());
                entry.setProviderSyncPending(false);
                entries.save(entry);
                cleared++;
            } catch (Exception ex) {
                log.debug("Provider update still pending for memoryId={}: {}", entry.getId(), ex.getMessage());
            }
        }
        return cleared;
    }

    private MemoryItemDto addMemory(Long userId, String content, String requestedScopeType,
                                    String requestedScopeId, String requestedCategory,
                                    String sourceType, String sourceId, String sessionId,
                                    String providerRef, String evidenceExcerpt, double confidence,
                                    Long requestedValidUntil, Double requestedImportance,
                                    boolean sensitiveConfirmed, boolean explicit) {
        String clean = content == null ? "" : content.trim();
        if (clean.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容不能为空");
        }
        if (clean.length() > MAX_MEMORY_CHARS) {
            if (explicit) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容最长2048字符");
            }
            clean = clean.substring(0, MAX_MEMORY_CHARS);
        }
        Scope scope = validateScope(userId, requestedScopeType, requestedScopeId);
        String scopeType = scope.type();
        String scopeId = scope.id();
        MemoryPolicyEngine.Decision decision = policy.evaluate(clean, explicit, sensitiveConfirmed,
                requestedCategory, scopeId);
        if (!decision.accepted()) {
            log.debug("Memory candidate filtered: reason={}, scope={}", decision.reason(), scopeType);
            cleanupProviderRef(userId, providerRef);
            return null;
        }
        String canonical = canonicalize(clean);
        String contentHash = hash(canonical);
        Optional<MemoryEntry> duplicate = entries.findActiveDuplicate(userId, scopeType, scopeId, contentHash);
        MemoryEntry entry = duplicate.orElseGet(MemoryEntry::new);
        long now = System.currentTimeMillis();
        if (entry.getId() == null) {
            entry.setUserId(userId);
            entry.setScopeType(scopeType);
            entry.setScopeId(scopeId);
            entry.setContent(clean);
            entry.setCanonicalContent(canonical);
            entry.setContentHash(contentHash);
            entry.setCategory(decision.category());
            entry.setStatus(ACTIVE);
            entry.setSensitivity(decision.sensitivity());
            entry.setWritePolicy(explicit ? "EXPLICIT" : ("LEGACY_IMPORT".equals(sourceType) ? "IMPORT" : "AUTO"));
            entry.setSubjectKey(decision.subjectKey());
            entry.setConfidence(confidence);
            entry.setImportance(requestedImportance == null
                    ? (explicit ? 0.8d : 0.5d)
                    : clamp(requestedImportance, 0d, 1d));
            entry.setValidFrom(now);
            entry.setValidUntil(requestedValidUntil != null ? requestedValidUntil : decision.defaultValidUntil());
            entry.setProviderName(providerRef == null ? null : "mem0");
            entry.setProviderRef(providerRef);
            entry.setCreatedAt(now);

            if (decision.subjectKey() != null) {
                Optional<MemoryEntry> previousMatch = entries.findByUserIdAndScopeTypeAndScopeIdAndSubjectKeyAndStatusIn(
                                userId, scopeType, scopeId, decision.subjectKey(), List.of(ACTIVE, "CONFLICTED"))
                        .stream().filter(existing -> !Objects.equals(existing.getContentHash(), contentHash))
                        .findFirst();
                if (previousMatch.isPresent()) {
                    MemoryEntry previous = previousMatch.get();
                    entry.setSupersedesId(previous.getId());
                    if (explicit) {
                        previous.setStatus("SUPERSEDED");
                        previous.setUpdatedAt(now);
                        entries.save(previous);
                        revisions.save(revision(previous, previous.getContent(), null,
                                "SUPERSEDED", "USER"));
                    } else {
                        entry.setStatus("CONFLICTED");
                    }
                }
            }
        } else {
            entry.setConfidence(Math.max(entry.getConfidence(), confidence));
            if (providerRef != null && !Objects.equals(providerRef, entry.getProviderRef())) {
                // 去重合并时丢弃候选自身的 provider 条目, 避免 Mem0 残留孤儿
                cleanupProviderRef(userId, providerRef);
            }
        }
        entry.setUpdatedAt(now);
        entry = entries.save(entry);

        String stableSourceId = sourceId == null ? sourceType + ":" + entry.getId() : sourceId;
        if (!sources.existsByMemoryIdAndSourceTypeAndSourceId(entry.getId(), sourceType, stableSourceId)) {
            MemorySource source = new MemorySource();
            source.setMemoryId(entry.getId());
            source.setUserId(userId);
            source.setSourceType(sourceType);
            source.setSourceId(stableSourceId);
            source.setSessionId(sessionId);
            source.setProjectId(scopeId);
            // Keep only the accepted atomic fact. Persisting the whole originating turn here
            // would duplicate unrelated or sensitive text that the policy intentionally rejected.
            String evidence = clean;
            source.setEvidenceExcerpt(evidence.length() > 500 ? evidence.substring(0, 500) : evidence);
            source.setTrustLevel(explicit ? "EXPLICIT" : "USER");
            source.setCreatedAt(now);
            sources.save(source);
        }
        return toDto(entry, null, 0d);
    }

    private MemoryEntry requireOwned(Long userId, String memoryId) {
        try {
            return entries.findByIdAndUserId(Long.valueOf(memoryId), userId)
                    .filter(e -> ACTIVE.equals(e.getStatus()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记忆不存在"));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记忆不存在");
        }
    }

    private MemoryEntry requireOwnedWithStatus(Long userId, String memoryId, String status) {
        try {
            return entries.findByIdAndUserId(Long.valueOf(memoryId), userId)
                    .filter(entry -> status.equals(entry.getStatus()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记忆不存在"));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记忆不存在");
        }
    }

    private MemoryEntry requireManageable(Long userId, String memoryId) {
        try {
            return entries.findByIdAndUserId(Long.valueOf(memoryId), userId)
                    .filter(entry -> !"DELETED".equals(entry.getStatus()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记忆不存在"));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记忆不存在");
        }
    }

    private void requireMemoryEnabled(Long userId) {
        if (!isEnabled(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先启用长期记忆");
        }
    }

    /**
     * 删除被拒绝或成为孤儿的候选在 Mem0 侧的条目 (标准 Mem0 的抽取接口会同时创建记忆)。
     * 立即删除失败时写入 REJECTED 清理墓碑, 由 {@link #reconcileProviderDeletions()} 定时补偿。
     */
    private void cleanupProviderRef(Long userId, String providerRef) {
        if (providerRef == null || providerRef.isBlank()) return;
        try {
            mem0Client.delete(providerRef);
        } catch (Exception ex) {
            log.warn("Provider cleanup pending for rejected candidate: {}", ex.getMessage());
            saveRejectedTombstone(userId, providerRef);
        }
    }

    /** 只保留脱敏占位符与 provider 引用, 不复制被拒绝的候选原文。 */
    private void saveRejectedTombstone(Long userId, String providerRef) {
        try {
            long now = System.currentTimeMillis();
            String placeholder = "[已拒绝的抽取候选]";
            MemoryEntry tombstone = new MemoryEntry();
            tombstone.setUserId(userId);
            tombstone.setScopeType("USER");
            tombstone.setScopeId(null);
            tombstone.setContent(placeholder);
            tombstone.setCanonicalContent(canonicalize(placeholder));
            tombstone.setContentHash(hash(canonicalize(placeholder) + ":" + providerRef));
            tombstone.setCategory("OTHER");
            tombstone.setStatus("DELETED");
            tombstone.setSensitivity("NORMAL");
            tombstone.setWritePolicy("REJECTED");
            tombstone.setConfidence(0d);
            tombstone.setImportance(0d);
            tombstone.setValidFrom(now);
            tombstone.setProviderName("mem0");
            tombstone.setProviderRef(providerRef);
            tombstone.setCreatedAt(now);
            tombstone.setUpdatedAt(now);
            tombstone.setDeletedAt(now);
            entries.save(tombstone);
        } catch (RuntimeException ex) {
            log.warn("Unable to record provider cleanup tombstone: {}", ex.getClass().getSimpleName());
        }
    }

    /** 候选必须能在原始用户消息中找到词面支持, 防止 provider 改写/臆造后入库。 */
    private boolean supportedByOriginal(String candidate, String original) {
        String canonicalOriginal = canonicalize(original);
        Set<String> originalTerms = terms(original);
        Set<String> candidateTerms = new HashSet<>(terms(candidate));
        // 1) 数字与 ASCII 关键词 (版本号、专名等) 必须逐一出现在原文, 防止 Java 21 -> Java 17 之类的臆造
        for (String term : candidateTerms) {
            if (containsCjk(term) || GENERIC_CANDIDATE_TERMS.contains(term)) continue;
            if (!originalTerms.contains(term) && !canonicalOriginal.contains(term)) {
                return false;
            }
        }
        // 2) 否定极性不得被改反, 防止"我不喜欢英文回答"变成"用户喜欢英文回答"
        if (polarityConflicts(candidate, canonicalOriginal)) {
            return false;
        }
        // 3) 剔除 provider 惯用的主语/谓词与整句级 CJK 长词条, 仅用有区分度的词面单元衡量支持度
        candidateTerms.removeIf(term -> GENERIC_CANDIDATE_TERMS.contains(term)
                || (containsCjk(term) && term.length() > 2));
        if (candidateTerms.isEmpty()) return false;
        long matches = candidateTerms.stream().filter(originalTerms::contains).count();
        return matches >= Math.max(1, Math.ceil(candidateTerms.size() * 0.3d));
    }

    /** 逐个偏好/使用类动词比较候选与原文的肯定/否定极性, 出现相反极性即视为证据不可信。 */
    private boolean polarityConflicts(String candidate, String canonicalOriginal) {
        String canonicalCandidate = canonicalize(candidate);
        for (String verb : POLARITY_VERBS_CJK) {
            Boolean candidatePolarity = polarity(canonicalCandidate, verb, false);
            Boolean originalPolarity = polarity(canonicalOriginal, verb, false);
            if (candidatePolarity != null && originalPolarity != null
                    && !candidatePolarity.equals(originalPolarity)) {
                return true;
            }
        }
        for (String verb : POLARITY_VERBS_EN) {
            Boolean candidatePolarity = polarity(canonicalCandidate, verb, true);
            Boolean originalPolarity = polarity(canonicalOriginal, verb, true);
            if (candidatePolarity != null && originalPolarity != null
                    && !candidatePolarity.equals(originalPolarity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return null=动词未出现; true=存在肯定用法; false=仅出现否定用法。
     * 原文同时含肯定与否定时按肯定处理 (无法可靠区分指向, 交给重合度兜底)。
     */
    private Boolean polarity(String text, String verb, boolean ascii) {
        Pattern negated = ascii
                ? Pattern.compile("(?:not|n't|never)\\s+" + verb + "\\b")
                : Pattern.compile("[不别没勿]" + verb);
        Pattern affirmative = ascii
                ? Pattern.compile("(?<!not )(?<!n't )(?<!never )\\b" + verb + "\\b")
                : Pattern.compile("(?<![不别没勿])" + verb);
        boolean hasNegated = negated.matcher(text).find();
        boolean hasAffirmative = affirmative.matcher(text).find();
        if (!hasNegated && !hasAffirmative) return null;
        return hasAffirmative;
    }

    private void softDelete(MemoryEntry entry, String reason, String actor) {
        long now = System.currentTimeMillis();
        entry.setStatus("DELETED");
        entry.setDeletedAt(now);
        entry.setUpdatedAt(now);
        entries.save(entry);
        revisions.save(revision(entry, entry.getContent(), null, reason, actor));
        if (entry.getProviderRef() != null) {
            try {
                mem0Client.delete(entry.getProviderRef());
                entry.setProviderRef(null);
                entry.setProviderName(null);
                entries.save(entry);
            } catch (Exception ex) {
                log.warn("Provider memory delete pending for memoryId={}: {}", entry.getId(), ex.getMessage());
            }
        }
    }

    private MemoryRevision revision(MemoryEntry entry, String oldContent, String newContent,
                                    String reason, String actor) {
        MemoryRevision revision = new MemoryRevision();
        revision.setMemoryId(entry.getId());
        revision.setUserId(entry.getUserId());
        revision.setOldContent(oldContent);
        revision.setNewContent(newContent);
        revision.setReason(reason);
        revision.setActor(actor);
        revision.setCreatedAt(System.currentTimeMillis());
        return revision;
    }

    private void recordUsage(Long userId, String projectId, List<MemoryItemDto> items, long elapsedMs) {
        if (items.isEmpty()) return;
        try {
            long now = System.currentTimeMillis();
            List<MemoryUsageEvent> events = items.stream().map(item -> {
                MemoryUsageEvent event = new MemoryUsageEvent();
                event.setUserId(userId);
                event.setMemoryId(Long.valueOf(item.id()));
                event.setProjectId(projectId);
                event.setReason(item.recallReason() == null ? "matched" : item.recallReason());
                event.setScore(item.score());
                event.setElapsedMs(elapsedMs);
                event.setCreatedAt(now);
                return event;
            }).toList();
            usageEvents.saveAll(events);
        } catch (RuntimeException ex) {
            log.debug("Unable to record memory usage metadata: {}", ex.getClass().getSimpleName());
        }
    }

    private MemoryItemDto toDto(MemoryEntry entry, String reason, double score) {
        return new MemoryItemDto(String.valueOf(entry.getId()), entry.getContent(),
                String.valueOf(entry.getCreatedAt()), String.valueOf(entry.getUpdatedAt()),
                entry.getScopeType(), entry.getScopeId(), entry.getCategory(), entry.getStatus(),
                entry.getConfidence(), entry.getImportance(), entry.isPinned(),
                sources.countByMemoryId(entry.getId()), reason, Math.round(score * 1000d) / 1000d,
                entry.getSensitivity(), entry.getWritePolicy(), entry.getValidUntil(),
                entry.getSupersedesId(), entry.getSubjectKey());
    }

    private double score(MemoryEntry entry, Set<String> queryTerms) {
        Set<String> memoryTerms = terms(entry.getCanonicalContent());
        if (queryTerms.isEmpty()) {
            return entry.isPinned() ? 1d : entry.getImportance() * 0.25d;
        }
        long matches = queryTerms.stream().filter(memoryTerms::contains).count();
        double lexical = (double) matches / Math.max(1, queryTerms.size());
        if (matches == 0) {
            return entry.isPinned() ? 0.2d : 0d;
        }
        double freshness = 1d / (1d + Math.max(0, System.currentTimeMillis() - entry.getUpdatedAt()) / 86_400_000d / 180d);
        return lexical * 0.65d + entry.getImportance() * 0.15d + entry.getConfidence() * 0.05d
                + freshness * 0.08d + ("PROJECT".equals(entry.getScopeType()) ? 0.12d : 0d)
                + (entry.isPinned() ? 0.2d : 0d);
    }

    private Set<String> terms(String value) {
        if (value == null || value.isBlank()) return Set.of();
        String normalized = canonicalize(value);
        Set<String> result = new HashSet<>();
        Matcher matcher = WORD.matcher(normalized);
        while (matcher.find()) {
            String word = matcher.group();
            result.add(word);
            if (containsCjk(word)) {
                for (int i = 0; i < word.length() - 1; i++) result.add(word.substring(i, i + 2));
            }
        }
        return result;
    }

    private boolean containsCjk(String value) {
        return value.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private Scope validateScope(Long userId, String requestedScopeType, String requestedScopeId) {
        String scopeType = requestedScopeType == null || requestedScopeType.isBlank()
                ? "USER" : requestedScopeType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("USER", "PROJECT").contains(scopeType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆范围只能是 USER 或 PROJECT");
        }
        String scopeId = "PROJECT".equals(scopeType) && requestedScopeId != null && !requestedScopeId.isBlank()
                ? requestedScopeId : null;
        if ("PROJECT".equals(scopeType) && scopeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目记忆必须指定项目");
        }
        if ("PROJECT".equals(scopeType)
                && !Boolean.TRUE.equals(settingsService.getMemorySettings(userId).projectMemoryEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户已关闭项目记忆");
        }
        if ("PROJECT".equals(scopeType) && projects.findByIdAndUserId(scopeId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目不存在或不属于当前用户");
        }
        return new Scope(scopeType, scopeId);
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在");
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String canonicalize(String content) {
        return Normalizer.normalize(content, Normalizer.Form.NFKC)
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Scored(MemoryEntry entry, double score) {
    }

    private record Scope(String type, String id) {
    }
}
