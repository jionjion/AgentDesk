package top.jionjion.agentdesk.service.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.jionjion.agentdesk.entity.MemoryJob;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.dto.memory.MemoryJobDto;
import top.jionjion.agentdesk.repository.MemoryJobRepository;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.service.MemoryService;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Durable outbox-style worker for post-turn memory extraction. */
@Service
public class MemoryJobService {
    private static final Logger log = LoggerFactory.getLogger(MemoryJobService.class);
    private static final int MAX_ATTEMPTS = 4;

    private final MemoryJobRepository jobs;
    private final MemoryService memoryService;
    private final ChatMessageRepository chatMessages;
    private final ProjectRepository projects;

    public MemoryJobService(MemoryJobRepository jobs, MemoryService memoryService,
                            ChatMessageRepository chatMessages, ProjectRepository projects) {
        this.jobs = jobs;
        this.memoryService = memoryService;
        this.chatMessages = chatMessages;
        this.projects = projects;
    }

    @Transactional
    public void enqueue(Long userId, String sessionId, String projectId,
                        Long userMessageId, String originalUserMessage) {
        if (userId == null || userMessageId == null || originalUserMessage == null || originalUserMessage.isBlank()) {
            return;
        }
        String key = "chat-message:" + userMessageId;
        if (jobs.existsByIdempotencyKey(key)) {
            return;
        }
        long now = System.currentTimeMillis();
        // Native ON CONFLICT keeps duplicate regenerate/concurrent completion safe even when
        // this call participates in the assistant-message transaction.
        jobs.insertPending(userId, sessionId, projectId, userMessageId, key, now);
    }

    @Transactional
    public void cancelUserMessages(Long userId, List<Long> userMessageIds) {
        if (userId == null || userMessageIds == null || userMessageIds.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<MemoryJob> matched = jobs.findByUserIdAndUserMessageIdIn(userId, userMessageIds);
        List<MemoryJob> cancelled = new java.util.ArrayList<>();
        for (MemoryJob job : matched) {
            if (List.of("PENDING", "RETRY").contains(job.getStatus())) {
                job.setStatus("CANCELLED");
                job.setUpdatedAt(now);
                cancelled.add(job);
            }
        }
        if (!cancelled.isEmpty()) jobs.saveAll(cancelled);
    }

    @Scheduled(fixedDelayString = "${agentdesk.memory.worker-delay-ms:3000}")
    public void processDueJobs() {
        List<MemoryJob> due = jobs.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                List.of("PENDING", "RETRY", "PROCESSING"), System.currentTimeMillis(), PageRequest.of(0, 10));
        for (MemoryJob job : due) {
            process(job);
        }
    }

    @Scheduled(fixedDelayString = "${agentdesk.memory.maintenance-delay-ms:60000}",
            initialDelayString = "${agentdesk.memory.maintenance-initial-delay-ms:15000}")
    public void expireDueMemories() {
        int expired = memoryService.expireDueMemories();
        if (expired > 0) log.info("Expired {} long-term memories", expired);
    }

    @Scheduled(fixedDelayString = "${agentdesk.memory.provider-cleanup-delay-ms:60000}",
            initialDelayString = "${agentdesk.memory.provider-cleanup-initial-delay-ms:30000}")
    public void reconcileProviderDeletions() {
        int cleared = memoryService.reconcileProviderDeletions();
        int updated = memoryService.reconcileProviderUpdates();
        if (cleared > 0) log.info("Reconciled {} provider memory deletions", cleared);
        if (updated > 0) log.info("Reconciled {} provider memory updates", updated);
    }

    public List<MemoryJobDto> listJobs(Long userId, String status) {
        List<MemoryJob> result = status == null || status.isBlank()
                ? jobs.findTop100ByUserIdOrderByUpdatedAtDesc(userId)
                : jobs.findTop100ByUserIdAndStatusOrderByUpdatedAtDesc(userId, status.toUpperCase());
        return result.stream().map(this::toDto).toList();
    }

    public Map<String, Long> jobCounts(Long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : List.of("PENDING", "PROCESSING", "RETRY", "DONE", "DEAD", "CANCELLED")) {
            counts.put(status, jobs.countByUserIdAndStatus(userId, status));
        }
        return counts;
    }

    @Transactional
    public MemoryJobDto retryDeadJob(Long userId, Long jobId) {
        MemoryJob job = jobs.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "记忆任务不存在"));
        if (!List.of("DEAD", "CANCELLED").contains(job.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "只有 DEAD 或 CANCELLED 任务可以重放");
        }
        job.setStatus("PENDING");
        job.setAttempts(0);
        job.setLastError(null);
        job.setNextAttemptAt(System.currentTimeMillis());
        job.setUpdatedAt(System.currentTimeMillis());
        return toDto(jobs.save(job));
    }

    private MemoryJobDto toDto(MemoryJob job) {
        return new MemoryJobDto(job.getId(), job.getSessionId(), job.getProjectId(),
                job.getUserMessageId(), job.getStatus(), job.getAttempts(), job.getNextAttemptAt(),
                job.getLastError(), job.getCreatedAt(), job.getUpdatedAt());
    }

    private void process(MemoryJob job) {
        ChatMessage sourceMessage = chatMessages.findByIdAndSessionId(job.getUserMessageId(), job.getSessionId())
                .orElse(null);
        if (sourceMessage == null || !projectStillValid(job)) {
            job.setStatus("CANCELLED");
            job.setUpdatedAt(System.currentTimeMillis());
            jobs.save(job);
            return;
        }
        job.setStatus("PROCESSING");
        // Lease: a process crash leaves the job recoverable after five minutes.
        job.setNextAttemptAt(System.currentTimeMillis() + 300_000L);
        job.setUpdatedAt(System.currentTimeMillis());
        try {
            job = jobs.saveAndFlush(job);
        } catch (ObjectOptimisticLockingFailureException ex) {
            // Another worker instance already claimed this job.
            return;
        }
        try {
            memoryService.extractAndStore(job.getUserId(), job.getSessionId(), job.getProjectId(),
                    job.getUserMessageId(), sourceMessage.getContent());
            if (sourceStillValid(job)) {
                job.setStatus("DONE");
            } else {
                memoryService.invalidateMessageSources(job.getUserId(), List.of(job.getUserMessageId()));
                job.setStatus("CANCELLED");
            }
            job.setLastError(null);
        } catch (Exception ex) {
            int attempts = job.getAttempts() + 1;
            job.setAttempts(attempts);
            job.setLastError(truncate(ex.getMessage(), 2000));
            if (attempts >= MAX_ATTEMPTS) {
                job.setStatus("DEAD");
                log.warn("Memory job {} exhausted retries: {}", job.getId(), ex.getMessage());
            } else {
                job.setStatus("RETRY");
                job.setNextAttemptAt(System.currentTimeMillis() + retryDelay(attempts));
                log.info("Memory job {} will retry (attempt {}): {}", job.getId(), attempts, ex.getMessage());
            }
        }
        job.setUpdatedAt(System.currentTimeMillis());
        jobs.save(job);
    }

    private boolean sourceStillValid(MemoryJob job) {
        return chatMessages.existsByIdAndSessionId(job.getUserMessageId(), job.getSessionId())
                && projectStillValid(job);
    }

    private boolean projectStillValid(MemoryJob job) {
        return job.getProjectId() == null
                || projects.findByIdAndUserId(job.getProjectId(), job.getUserId()).isPresent();
    }

    private long retryDelay(int attempts) {
        return Math.min(300_000L, 5_000L * (1L << Math.min(attempts, 6)));
    }

    private String truncate(String value, int max) {
        if (value == null) return "unknown error";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
