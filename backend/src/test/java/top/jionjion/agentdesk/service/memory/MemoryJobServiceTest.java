package top.jionjion.agentdesk.service.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import top.jionjion.agentdesk.entity.MemoryJob;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.repository.MemoryJobRepository;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.service.MemoryService;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class MemoryJobServiceTest {
    private final MemoryJobRepository jobs = mock(MemoryJobRepository.class);
    private final MemoryService memoryService = mock(MemoryService.class);
    private final ChatMessageRepository chatMessages = mock(ChatMessageRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private MemoryJobService service;

    @BeforeEach
    void setUp() {
        service = new MemoryJobService(jobs, memoryService, chatMessages, projects);
        when(jobs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enqueueUsesUserMessageAsIdempotencyKey() {
        when(jobs.existsByIdempotencyKey("chat-message:42")).thenReturn(false);

        service.enqueue(1L, "s1", "p1", 42L, "原始用户消息");

        verify(jobs).insertPending(eq(1L), eq("s1"), eq("p1"), eq(42L),
                eq("chat-message:42"), anyLong());
    }

    @Test
    void duplicateTurnIsNotQueuedAgain() {
        when(jobs.existsByIdempotencyKey("chat-message:42")).thenReturn(true);

        service.enqueue(1L, "s1", null, 42L, "原始用户消息");

        verify(jobs, never()).insertPending(any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void deletedSourceMessageCancelsPendingJob() {
        MemoryJob job = job("PENDING");
        whenDue(job);
        when(chatMessages.findByIdAndSessionId(42L, "s1")).thenReturn(Optional.empty());

        service.processDueJobs();

        assertEquals("CANCELLED", job.getStatus());
        verifyNoInteractions(memoryService);
    }

    @Test
    void staleProcessingLeaseCanBeRecovered() throws Exception {
        MemoryJob job = job("PROCESSING");
        whenDue(job);
        mockSourceMessage();
        when(chatMessages.existsByIdAndSessionId(42L, "s1")).thenReturn(true);
        when(memoryService.extractAndStore(1L, "s1", null, 42L, "原始用户消息"))
                .thenReturn(1);

        service.processDueJobs();

        assertEquals("DONE", job.getStatus());
        verify(memoryService).extractAndStore(1L, "s1", null, 42L, "原始用户消息");
    }

    @Test
    void providerFailureSchedulesRetry() throws Exception {
        MemoryJob job = job("PENDING");
        whenDue(job);
        mockSourceMessage();
        when(chatMessages.existsByIdAndSessionId(42L, "s1")).thenReturn(true);
        when(memoryService.extractAndStore(1L, "s1", null, 42L, "原始用户消息"))
                .thenThrow(new IOException("provider unavailable"));

        service.processDueJobs();

        assertEquals("RETRY", job.getStatus());
        assertEquals(1, job.getAttempts());
        assertTrue(job.getNextAttemptAt() > System.currentTimeMillis());
    }

    @Test
    void deletionDuringExtractionInvalidatesNewSource() throws Exception {
        MemoryJob job = job("PENDING");
        whenDue(job);
        mockSourceMessage();
        when(chatMessages.existsByIdAndSessionId(42L, "s1")).thenReturn(false);
        when(memoryService.extractAndStore(1L, "s1", null, 42L, "原始用户消息"))
                .thenReturn(1);

        service.processDueJobs();

        assertEquals("CANCELLED", job.getStatus());
        verify(memoryService).invalidateMessageSources(1L, List.of(42L));
    }

    private void whenDue(MemoryJob job) {
        when(jobs.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                anyList(), anyLong(), any(Pageable.class))).thenReturn(List.of(job));
    }

    private void mockSourceMessage() {
        ChatMessage message = new ChatMessage("s1", "user", "原始用户消息");
        message.setId(42L);
        when(chatMessages.findByIdAndSessionId(42L, "s1")).thenReturn(Optional.of(message));
    }

    private MemoryJob job(String status) {
        MemoryJob job = new MemoryJob();
        job.setId(7L);
        job.setUserId(1L);
        job.setSessionId("s1");
        job.setUserMessageId(42L);
        job.setUserMessage("原始用户消息");
        job.setStatus(status);
        job.setNextAttemptAt(0L);
        job.setIdempotencyKey("chat-message:42");
        job.setCreatedAt(1L);
        job.setUpdatedAt(1L);
        return job;
    }
}
