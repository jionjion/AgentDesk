package top.jionjion.agentdesk.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import top.jionjion.agentdesk.dto.FileResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件上传集成测试 - 直接调用阿里云 OSS 验证上传/查询/删除
 */
@SpringBootTest
class FileServiceTest {

    @Autowired
    private FileService fileService;

    @Test
    void testUploadAndDelete() throws IOException {
        // 读取本地图片
        Path imagePath = Path.of("C:/Users/JionJion/Pictures/Saved Pictures/15853.jpg");
        byte[] content = Files.readAllBytes(imagePath);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "15853.jpg",
                "image/jpeg",
                content
        );

        // 1. 上传
        FileResponse uploaded = fileService.upload(file, null, 1L);
        assertNotNull(uploaded.id());
        assertNotNull(uploaded.downloadUrl());
        assertEquals("15853.jpg", uploaded.originalName());
        assertEquals("image/jpeg", uploaded.contentType());
        assertTrue(uploaded.size() > 0);
        assertNull(uploaded.sessionId());

        System.out.println("上传成功, ID: " + uploaded.id());
        System.out.println("下载链接: " + uploaded.downloadUrl());

        // 2. 查询
        FileResponse queried = fileService.getFile(uploaded.id(), 1L);
        assertEquals(uploaded.id(), queried.id());
        assertNotNull(queried.downloadUrl());

        System.out.println("查询成功, 文件大小: " + queried.size() + " bytes");

        // 3. 删除
        fileService.delete(uploaded.id(), 1L);
        System.out.println("删除成功");
    }
}
