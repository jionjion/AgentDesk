package top.jionjion.agentdesk.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session + Chat Controller Test
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Create session")
    void testCreateSession() throws Exception {
        String requestBody = """
                {"title": "test session"}
                """;

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("test session"));
    }

    @Test
    @DisplayName("List sessions")
    void testListSessions() throws Exception {
        mockMvc.perform(get("/api/sessions"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SSE stream chat")
    void testStreamChat() throws Exception {
        // Create a session first
        String sessionResult = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"chat test\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = com.fasterxml.jackson.databind.ObjectMapper
                .class.getDeclaredConstructor().newInstance()
                .readTree(sessionResult).get("id").asText();

        // Test SSE endpoint returns event stream
        mockMvc.perform(get("/api/chat/stream")
                        .param("sessionId", sessionId)
                        .param("message", "hello"))
                .andDo(print())
                .andExpect(status().isOk());
    }
}
