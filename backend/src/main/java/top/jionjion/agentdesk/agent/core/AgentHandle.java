package top.jionjion.agentdesk.agent.core;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import top.jionjion.agentdesk.agent.runtime.AgentEventBridge;
import top.jionjion.agentdesk.agent.runtime.AgentInput;
import top.jionjion.agentdesk.agent.runtime.AgentRunContext;
import top.jionjion.agentdesk.agent.tool.RemoteExecTool;
import top.jionjion.agentdesk.entity.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Runtime handle for an AgentScope Java v2 Harness agent. */
public final class AgentHandle implements AutoCloseable {

    private final HarnessAgent agent;
    private final AgentEventBridge eventBridge;
    private final RemoteExecTool remoteExecTool;

    public AgentHandle(HarnessAgent agent, AgentEventBridge eventBridge, RemoteExecTool remoteExecTool) {
        this.agent = Objects.requireNonNull(agent);
        this.eventBridge = Objects.requireNonNull(eventBridge);
        this.remoteExecTool = remoteExecTool;
    }

    public Flux<AgentEvent> stream(AgentInput input, AgentRunContext context) {
        Objects.requireNonNull(context, "context");
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(context.userId() == null ? null : String.valueOf(context.userId()))
                .sessionId(context.sessionId())
                .putAll(context.attributes())
                .build();
        return agent.streamEvents(toUserMessage(input), runtimeContext)
                .doOnNext(eventBridge::accept);
    }

    public void attachEmitter(SseEmitter emitter) {
        eventBridge.attach(emitter);
    }

    public void detachEmitter() {
        eventBridge.detach();
    }

    public void markClientDisconnected() {
        eventBridge.markDisconnected();
    }

    public boolean isClientDisconnected() {
        return eventBridge.isClientDisconnected();
    }

    public void setWorkingDirectory(String workingDirectory) {
        if (remoteExecTool != null && workingDirectory != null) {
            remoteExecTool.setWorkingDir(workingDirectory);
        }
    }

    public String lastReply() {
        return eventBridge.getLastReply();
    }

    public void interrupt(Long userId, String sessionId) {
        agent.getDelegate().interrupt(userId == null ? null : String.valueOf(userId), sessionId);
    }

    /** Rebuilds a clean v2 conversation state from the durable chat history. */
    public void restoreHistory(Long userId, String sessionId, List<ChatMessage> history) {
        String uid = userId == null ? null : String.valueOf(userId);
        var state = agent.getDelegate().getAgentState(uid, sessionId);
        state.contextMutable().clear();
        for (ChatMessage message : history) {
            if ("user".equals(message.getRole())) {
                state.contextMutable().add(new UserMessage(message.getContent()));
            } else if ("assistant".equals(message.getRole())) {
                state.contextMutable().add(AssistantMessage.builder()
                        .name("assistant")
                        .content(TextBlock.builder()
                                .text(message.getContent() == null ? "" : message.getContent())
                                .build())
                        .build());
            }
        }
        agent.getDelegate().saveAgentState(uid, sessionId);
    }

    @Override
    public void close() {
        agent.close();
    }

    private UserMessage toUserMessage(AgentInput input) {
        Objects.requireNonNull(input, "input");
        if (!input.isMultimodal()) {
            return new UserMessage(input.text());
        }
        List<ContentBlock> blocks = new ArrayList<>();
        blocks.add(TextBlock.builder().text(input.text()).build());
        for (String imageUrl : input.imageUrls()) {
            blocks.add(ImageBlock.builder()
                    .source(URLSource.builder().url(imageUrl).build())
                    .build());
        }
        return new UserMessage(blocks);
    }
}
