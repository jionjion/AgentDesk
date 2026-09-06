package top.jionjion.agentdesk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.memory.MemoryRecallResult;
import top.jionjion.agentdesk.dto.settings.MemorySettingsDto;
import top.jionjion.agentdesk.entity.MemoryEntry;
import top.jionjion.agentdesk.repository.MemoryEntryRepository;
import top.jionjion.agentdesk.repository.MemoryRevisionRepository;
import top.jionjion.agentdesk.repository.MemorySourceRepository;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.repository.MemoryUsageEventRepository;
import top.jionjion.agentdesk.service.memory.Mem0Client;
import top.jionjion.agentdesk.service.memory.MemoryPolicyEngine;
import top.jionjion.agentdesk.entity.Project;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryServiceTest {
    private final MemoryEntryRepository entries = mock(MemoryEntryRepository.class);
    private final MemorySourceRepository sources = mock(MemorySourceRepository.class);
    private final MemoryRevisionRepository revisions = mock(MemoryRevisionRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final SettingsService settings = mock(SettingsService.class);
    private final Mem0Client mem0 = mock(Mem0Client.class);
    private final MemoryUsageEventRepository usageEvents = mock(MemoryUsageEventRepository.class);
    private MemoryService service;

    @BeforeEach
    void setUp() {
        service = new MemoryService(entries, sources, revisions, projects, settings, mem0,
                new MemoryPolicyEngine(), usageEvents);
        when(settings.getMemorySettings(1L)).thenReturn(new MemorySettingsDto(true, true, true, true));
        when(sources.countByMemoryId(any())).thenReturn(1L);
        Project project = new Project();
        project.setId("p1");
        project.setUserId(1L);
        when(projects.findByIdAndUserId("p1", 1L)).thenReturn(Optional.of(project));
    }

    @Test
    void recallOnlyUsesGlobalAndCurrentProjectScope() {
        MemoryEntry global = entry(1L, "USER", null, "用户偏好 Spring Boot");
        MemoryEntry currentProject = entry(2L, "PROJECT", "p1", "项目使用 Spring Boot 和 Java");
        MemoryEntry otherProject = entry(3L, "PROJECT", "p2", "另一个项目使用 Spring Boot");
        when(entries.findTop500ByUserIdAndStatusOrderByPinnedDescUpdatedAtDesc(1L, "ACTIVE"))
                .thenReturn(List.of(global, currentProject, otherProject));

        MemoryRecallResult result = service.recall(1L, "p1", "Spring Boot 项目", "NORMAL");

        assertEquals("USED", result.status());
        assertEquals(List.of("2", "1"), result.items().stream().map(item -> item.id()).toList());
        assertTrue(result.items().stream().noneMatch(item -> "p2".equals(item.scopeId())));
    }

    @Test
    void noMemoryModeDoesNotTouchStorage() {
        MemoryRecallResult result = service.recall(1L, "p1", "anything", "NO_MEMORY");

        assertEquals("DISABLED", result.status());
        verifyNoInteractions(entries);
    }

    @Test
    void disabledMemoryDoesNotReadCanonicalStore() {
        when(settings.getMemorySettings(1L)).thenReturn(new MemorySettingsDto(false, true, true, true));

        MemoryRecallResult result = service.recall(1L, "p1", "Spring Boot", "NORMAL");

        assertEquals("DISABLED", result.status());
        verifyNoInteractions(entries);
        verifyNoInteractions(mem0);
    }

    @Test
    void storageFailureDegradesRecallInsteadOfFailingChat() {
        when(entries.findTop500ByUserIdAndStatusOrderByPinnedDescUpdatedAtDesc(1L, "ACTIVE"))
                .thenThrow(new IllegalStateException("database unavailable"));

        MemoryRecallResult result = service.recall(1L, null, "Spring Boot", "NORMAL");

        assertEquals("DEGRADED", result.status());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void projectMemoryDisabledDoesNotRecallOrLearnProjectData() {
        when(settings.getMemorySettings(1L)).thenReturn(new MemorySettingsDto(true, true, false, true));
        MemoryEntry global = entry(1L, "USER", null, "用户偏好 Spring Boot");
        MemoryEntry project = entry(2L, "PROJECT", "p1", "项目使用 Spring Boot");
        when(entries.findTop500ByUserIdAndStatusOrderByPinnedDescUpdatedAtDesc(1L, "ACTIVE"))
                .thenReturn(List.of(global, project));

        MemoryRecallResult result = service.recall(1L, "p1", "Spring Boot", "NORMAL");

        assertEquals(List.of("1"), result.items().stream().map(item -> item.id()).toList());
        assertFalse(service.shouldAutoLearn(1L, "p1"));
    }

    @Test
    void manualWriteRequiresEnabledMemory() {
        when(settings.getMemorySettings(1L)).thenReturn(new MemorySettingsDto(false, true, true, true));

        assertThrows(ResponseStatusException.class,
                () -> service.addMemory(1L, "请记住这条信息"));
        verifyNoInteractions(entries);
    }

    @Test
    void projectWriteRejectsUnownedScope() {
        when(projects.findByIdAndUserId("other-project", 1L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> service.addMemory(1L, "项目事实", "PROJECT", "other-project", "PROJECT_FACT"));
        verify(entries, never()).save(any());
    }

    @Test
    void invalidScopeIsRejectedInsteadOfBecomingGlobal() {
        assertThrows(ResponseStatusException.class,
                () -> service.addMemory(1L, "项目事实", "PROJETC", "p1", "PROJECT_FACT"));
        verify(entries, never()).save(any());
    }

    @Test
    void exactLookupCannotReadAnotherProjectMemory() {
        MemoryEntry otherProject = entry(3L, "PROJECT", "p2", "另一个项目的机密事实");
        when(entries.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(otherProject));

        assertTrue(service.findAccessibleMemory(1L, "3", "p1").isEmpty());
        assertTrue(service.findAccessibleMemory(1L, "3", "p2").isPresent());
    }

    @Test
    void extractionReceivesOnlyOriginalUserTextAndCreatesProjectMemory() throws Exception {
        when(mem0.extract(1L, "这个项目以后都使用 Java 21"))
                .thenReturn(List.of(new Mem0Client.ExtractedMemory("provider-1", "项目偏好使用 Java 21")));
        when(entries.findActiveDuplicate(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(entries.save(any())).thenAnswer(invocation -> {
            MemoryEntry saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        int count = service.extractAndStore(1L, "s1", "p1", 99L, "这个项目以后都使用 Java 21");

        assertEquals(1, count);
        verify(mem0).extract(1L, "这个项目以后都使用 Java 21");
        verify(entries).save(argThat(entry -> "PROJECT".equals(entry.getScopeType())
                && "p1".equals(entry.getScopeId())
                && !entry.getContent().contains("assistant")));
        verify(sources).save(argThat(source -> "s1".equals(source.getSessionId())
                && "99".equals(source.getSourceId())
                && "项目偏好使用 Java 21".equals(source.getEvidenceExcerpt())));
    }

    @Test
    void automaticSecretCandidateIsFilteredBeforeCanonicalStorage() throws Exception {
        when(mem0.extract(1L, "这是原始消息"))
                .thenReturn(List.of(new Mem0Client.ExtractedMemory("provider-secret", "api_key: sk-abcdefghijklmnop")));

        int count = service.extractAndStore(1L, "s1", null, 100L, "这是原始消息");

        assertEquals(0, count);
        verify(entries, never()).save(any());
        verify(sources, never()).save(any());
    }

    @Test
    void hypotheticalOriginalRejectsRewrittenCandidateAndCleansProviderEntry() throws Exception {
        // 探针场景: 原文是假设句, provider 改写为肯定句后也不得入库, 且需删除抽取时创建的 provider 条目
        when(mem0.extract(1L, "假如我喜欢英文回答就好了"))
                .thenReturn(List.of(new Mem0Client.ExtractedMemory("provider-7", "用户喜欢英文回答")));

        int count = service.extractAndStore(1L, "s1", null, 102L, "假如我喜欢英文回答就好了");

        assertEquals(0, count);
        verify(entries, never()).save(any());
        verify(sources, never()).save(any());
        verify(mem0).delete("provider-7");
    }

    @Test
    void candidateWithoutLexicalSupportInOriginalIsRejected() throws Exception {
        when(mem0.extract(1L, "帮我写一段周报"))
                .thenReturn(List.of(new Mem0Client.ExtractedMemory("provider-8", "用户住在北京朝阳区")));

        int count = service.extractAndStore(1L, "s1", null, 103L, "帮我写一段周报");

        assertEquals(0, count);
        verify(entries, never()).save(any());
        verify(mem0).delete("provider-8");
    }

    @Test
    void negatedPreferenceCannotBeFlippedIntoAffirmativeMemory() throws Exception {
        // 探针场景: "我不喜欢英文回答" 被 provider 改写为肯定句, 否定极性冲突必须拒绝
        when(mem0.extract(1L, "我不喜欢英文回答"))
                .thenReturn(List.of(new Mem0Client.ExtractedMemory("provider-10", "用户喜欢英文回答")));

        int count = service.extractAndStore(1L, "s1", null, 105L, "我不喜欢英文回答");

        assertEquals(0, count);
        verify(entries, never()).save(any());
        verify(mem0).delete("provider-10");
    }

    @Test
    void inventedNumberNotPresentInOriginalIsRejected() throws Exception {
        // 探针场景: 原文 Java 21, 候选臆造为 Java 17, 数字必须逐一出现在原文
        when(mem0.extract(1L, "这个项目以后都使用 Java 21"))
                .thenReturn(List.of(new Mem0Client.ExtractedMemory("provider-11", "项目使用 Java 17")));

        int count = service.extractAndStore(1L, "s1", "p1", 106L, "这个项目以后都使用 Java 21");

        assertEquals(0, count);
        verify(entries, never()).save(any());
        verify(mem0).delete("provider-11");
    }

    @Test
    void failedProviderCleanupLeavesRejectedTombstoneForReconciliation() throws Exception {
        when(mem0.extract(1L, "这是原始消息"))
                .thenReturn(List.of(new Mem0Client.ExtractedMemory("provider-9", "password: hunter2secret")));
        doThrow(new java.io.IOException("mem0 unavailable")).when(mem0).delete("provider-9");

        int count = service.extractAndStore(1L, "s1", null, 104L, "这是原始消息");

        assertEquals(0, count);
        verify(entries).save(argThat(saved -> "DELETED".equals(saved.getStatus())
                && "REJECTED".equals(saved.getWritePolicy())
                && "provider-9".equals(saved.getProviderRef())
                && !saved.getContent().contains("hunter2secret")));
        verify(sources, never()).save(any());
    }

    @Test
    void reconciliationPhysicallyRemovesRejectedTombstoneAfterProviderDeletion() throws Exception {
        MemoryEntry tombstone = entry(60L, "USER", null, "[已拒绝的抽取候选]");
        tombstone.setStatus("DELETED");
        tombstone.setWritePolicy("REJECTED");
        tombstone.setProviderRef("provider-9");
        when(entries.findTop100ByStatusAndProviderRefIsNotNullOrderByUpdatedAtAsc("DELETED"))
                .thenReturn(List.of(tombstone));
        when(entries.findTop100ByStatusAndProviderRefIsNotNullOrderByUpdatedAtAsc("SUPERSEDED"))
                .thenReturn(List.of());

        assertEquals(1, service.reconcileProviderDeletions());

        verify(mem0).delete("provider-9");
        verify(entries).delete(tombstone);
    }

    @Test
    void explicitChatMemoryKeepsRealSessionAndMessageSource() {
        when(entries.findActiveDuplicate(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(entries.save(any())).thenAnswer(invocation -> {
            MemoryEntry saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(70L);
            return saved;
        });

        service.addExplicitChatMemory(1L, "记住我喜欢简洁回答", "USER", null, null, "s1", 88L);

        verify(entries).save(argThat(saved -> "EXPLICIT".equals(saved.getWritePolicy())));
        verify(sources).save(argThat(source -> "CHAT".equals(source.getSourceType())
                && "88".equals(source.getSourceId())
                && "s1".equals(source.getSessionId())
                && "EXPLICIT".equals(source.getTrustLevel())));
    }

    @Test
    void nonActiveMemoryCanStillBeDeletedIndividually() {
        MemoryEntry expired = entry(80L, "USER", null, "过期记忆");
        expired.setStatus("EXPIRED");
        when(entries.findByIdAndUserId(80L, 1L)).thenReturn(Optional.of(expired));

        service.deleteMemory(1L, "80");

        assertEquals("DELETED", expired.getStatus());
        verify(revisions).save(argThat(revision -> "DELETE".equals(revision.getReason())));
    }

    @Test
    void conflictingAutomaticPreferenceWaitsForConfirmation() throws Exception {
        MemoryEntry previous = entry(20L, "USER", null, "用户偏好英文回答");
        previous.setCategory("PREFERENCE");
        previous.setSubjectKey("PREFERENCE:USER:output-language");
        when(mem0.extract(1L, "以后请用中文回答"))
                .thenReturn(List.of(new Mem0Client.ExtractedMemory("provider-2", "用户偏好中文回答")));
        when(entries.findActiveDuplicate(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(entries.findByUserIdAndScopeTypeAndScopeIdAndSubjectKeyAndStatusIn(
                1L, "USER", null, "PREFERENCE:USER:output-language", List.of("ACTIVE", "CONFLICTED")))
                .thenReturn(List.of(previous));
        when(entries.save(any())).thenAnswer(invocation -> {
            MemoryEntry saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(21L);
            return saved;
        });

        assertEquals(1, service.extractAndStore(1L, "s1", null, 101L, "以后请用中文回答"));

        verify(entries).save(argThat(saved -> Long.valueOf(21L).equals(saved.getId())
                && "CONFLICTED".equals(saved.getStatus())
                && Long.valueOf(20L).equals(saved.getSupersedesId())));
    }

    @Test
    void deletingLastSourceRetiresTheMemory() {
        MemoryEntry memory = entry(8L, "USER", null, "用户偏好中文回答");
        var source = new top.jionjion.agentdesk.entity.MemorySource();
        source.setId(9L);
        source.setMemoryId(8L);
        source.setUserId(1L);
        source.setSessionId("s1");
        when(sources.findByUserIdAndSessionId(1L, "s1")).thenReturn(List.of(source));
        when(sources.countByMemoryId(8L)).thenReturn(0L);
        when(entries.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(memory));

        service.invalidateSessionSources(1L, "s1");

        assertEquals("DELETED", memory.getStatus());
        verify(revisions).save(argThat(revision -> "SOURCE_DELETED".equals(revision.getReason())));
    }

    @Test
    void dueMemoryIsExpiredAndNoLongerEligibleForRecall() {
        MemoryEntry due = entry(30L, "PROJECT", "p1", "项目仍处于需求阶段");
        due.setValidUntil(System.currentTimeMillis() - 1);
        when(entries.findByStatusAndValidUntilLessThanEqual(eq("ACTIVE"), anyLong()))
                .thenReturn(List.of(due));

        assertEquals(1, service.expireDueMemories());
        assertEquals("EXPIRED", due.getStatus());
        verify(revisions).save(argThat(revision -> "EXPIRED".equals(revision.getReason())));
    }

    @Test
    void clearAllRetiresEveryRetainedStateButDoesNotCountExistingTombstones() {
        MemoryEntry active = entry(40L, "USER", null, "有效记忆");
        MemoryEntry expired = entry(41L, "USER", null, "过期记忆");
        expired.setStatus("EXPIRED");
        MemoryEntry deleted = entry(42L, "USER", null, "已删除记忆");
        deleted.setStatus("DELETED");
        when(entries.findByUserIdOrderByPinnedDescUpdatedAtDesc(1L))
                .thenReturn(List.of(active, expired, deleted));

        assertEquals(2, service.deleteAllMemories(1L));
        assertEquals("DELETED", active.getStatus());
        assertEquals("DELETED", expired.getStatus());
        verify(revisions, times(2)).save(any());
    }

    @Test
    void deletingProjectRetiresActiveAndConflictedMemories() {
        MemoryEntry active = entry(50L, "PROJECT", "p1", "有效项目事实");
        MemoryEntry conflicted = entry(51L, "PROJECT", "p1", "待确认项目事实");
        conflicted.setStatus("CONFLICTED");
        when(entries.findByUserIdAndScopeTypeAndScopeIdAndStatus(1L, "PROJECT", "p1", "ACTIVE"))
                .thenReturn(List.of(active));
        when(entries.findByUserIdAndScopeTypeAndScopeIdAndStatus(1L, "PROJECT", "p1", "CONFLICTED"))
                .thenReturn(List.of(conflicted));

        service.deleteProjectScope(1L, "p1");

        assertEquals("DELETED", active.getStatus());
        assertEquals("DELETED", conflicted.getStatus());
    }

    private MemoryEntry entry(Long id, String scopeType, String scopeId, String content) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setUserId(1L);
        entry.setScopeType(scopeType);
        entry.setScopeId(scopeId);
        entry.setCategory("PROJECT_FACT");
        entry.setContent(content);
        entry.setCanonicalContent(content.toLowerCase());
        entry.setContentHash("hash-" + id);
        entry.setStatus("ACTIVE");
        entry.setConfidence(0.9d);
        entry.setImportance(0.7d);
        entry.setCreatedAt(System.currentTimeMillis());
        entry.setUpdatedAt(System.currentTimeMillis());
        return entry;
    }
}
