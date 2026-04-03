package top.jionjion.agentdesk.controller;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 对话接口
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ReActAgent assistant;

    public AgentController(ReActAgent assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");

        Msg userMsg = Msg.builder()
                .textContent(message)
                .build();

        Msg response = assistant.call(userMsg).block();

        return Map.of("reply", response != null ? response.getTextContent() : "无响应");
    }
}
