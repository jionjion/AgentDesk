package top.jionjion.agentdesk.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SkillPackageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void installsStandardSkillArchiveAndParsesBlockDescription() throws Exception {
        byte[] archive = zip(
                "community-main/SKILL.md", """
                        ---
                        name: web-research
                        description: |
                          Search authoritative sources.
                          Summarize the findings.
                        ---
                        # Web research
                        """,
                "community-main/references/guide.md", "guide");

        SkillPackageService service = new SkillPackageService(tempDir.toString());
        SkillPackageService.SkillInstallResult result = service.installArchive(
                new ByteArrayInputStream(archive), archive.length, "web-research.zip", 7L);

        assertEquals("web-research", result.id());
        assertEquals("Search authoritative sources. Summarize the findings.", result.description());
        assertTrue(result.resources().contains("SKILL.md"));
        assertEquals("guide", Files.readString(
                tempDir.resolve("7/web-research/references/guide.md"), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsZipSlipPaths() throws Exception {
        byte[] archive = zip("../SKILL.md", """
                ---
                name: unsafe
                description: unsafe
                ---
                """);
        SkillPackageService service = new SkillPackageService(tempDir.toString());

        assertThrows(ResponseStatusException.class, () -> service.installArchive(
                new ByteArrayInputStream(archive), archive.length, "unsafe.zip", 7L));
        assertFalse(Files.exists(tempDir.resolve("SKILL.md")));
    }

    private byte[] zip(String... pathAndContent) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (int i = 0; i < pathAndContent.length; i += 2) {
                zip.putNextEntry(new ZipEntry(pathAndContent[i]));
                zip.write(pathAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
