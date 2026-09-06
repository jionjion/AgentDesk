package top.jionjion.agentdesk.service.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class Mem0ClientContractTest {
    private HttpServer server;
    private Queue<ResponseSpec> responses;
    private AtomicInteger requestCount;
    private Mem0Client client;

    @BeforeEach
    void setUp() throws IOException {
        responses = new ConcurrentLinkedQueue<>();
        requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/memories", this::handleRequest);
        server.start();
        client = new Mem0Client(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void parsesArrayAndResultsEnvelopeWithoutLeakingProviderShape() throws Exception {
        enqueue(200, "[{\"id\":\"a\",\"memory\":\"偏好中文\"}]");
        enqueue(200, "{\"results\":[{\"id\":\"b\",\"memory\":\"偏好简洁\"}]}");

        assertEquals("偏好中文", client.list(1L).getFirst().content());
        assertEquals("偏好简洁", client.list(1L).getFirst().content());
    }

    @Test
    void invalidJsonAndNon2xxAreVisibleFailures() {
        enqueue(200, "not-json");
        enqueue(503, "");

        assertThrows(IOException.class, () -> client.list(1L));
        assertThrows(IOException.class, () -> client.list(1L));
    }

    @Test
    void circuitOpensAfterThreeConsecutiveFailures() {
        enqueue(503, "");
        enqueue(503, "");
        enqueue(503, "");
        for (int i = 0; i < 3; i++) assertThrows(IOException.class, () -> client.list(1L));

        IOException fourth = assertThrows(IOException.class, () -> client.list(1L));

        assertTrue(fourth.getMessage().contains("circuit breaker"));
        assertEquals(3, requestCount.get());
        assertEquals("OPEN", client.circuitStatus().state());
        assertEquals(3, client.circuitStatus().consecutiveFailures());
    }

    private void enqueue(int status, String body) {
        responses.add(new ResponseSpec(status, body));
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        ResponseSpec response = responses.poll();
        if (response == null) response = new ResponseSpec(500, "");
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private record ResponseSpec(int status, String body) {}
}
