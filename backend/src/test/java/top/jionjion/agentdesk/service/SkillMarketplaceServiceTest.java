package top.jionjion.agentdesk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.dto.skill.MarketplaceSkillPageDto;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillMarketplaceServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void searchesMarketplaceAndMarksInstalledSkills() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi/v1/skills", exchange -> {
            byte[] body = """
                    {"success":true,"data":{"skills":[{
                      "id":"@demo/research","display_name":"Research","description":"Find facts",
                      "developer":"demo","category":"developer-tools","tags":["research"],
                      "view_count":20,"downloads":10
                    }],"total":1,"page_number":1,"page_size":12}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        SkillService skillService = mock(SkillService.class);
        when(skillService.getInstalledMarketplaceRefs(9L)).thenReturn(Set.of("@demo/research"));
        SkillMarketplaceService service = new SkillMarketplaceService(
                new ObjectMapper(), mock(SkillPackageService.class), skillService,
                "http://127.0.0.1:" + server.getAddress().getPort());

        MarketplaceSkillPageDto page = service.search("facts", 1, 12, 9L);

        assertEquals(1, page.total());
        assertEquals("@demo/research", page.skills().getFirst().id());
        assertTrue(page.skills().getFirst().installed());
        assertEquals("Research", page.skills().getFirst().displayName());
    }
}
