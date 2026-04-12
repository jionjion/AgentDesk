package top.jionjion.agentdesk.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.jionjion.agentdesk.dto.FileResponse;
import top.jionjion.agentdesk.service.FileService;

import java.util.List;

/**
 * 文件上传/下载/删除 API
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 上传文件, 可选关联 sessionId
     */
    @PostMapping("/upload")
    public FileResponse upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(value = "sessionId", required = false) String sessionId) {
        return fileService.upload(file, sessionId);
    }

    /**
     * 获取文件信息 + 预签名下载 URL
     */
    @GetMapping("/{id}")
    public FileResponse getFile(@PathVariable Long id) {
        return fileService.getFile(id);
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        fileService.delete(id);
    }

    /**
     * 列出会话下所有文件
     */
    @GetMapping("/session/{sessionId}")
    public List<FileResponse> listBySession(@PathVariable String sessionId) {
        return fileService.listBySession(sessionId);
    }
}
