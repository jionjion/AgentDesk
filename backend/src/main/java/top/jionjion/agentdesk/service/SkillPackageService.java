package top.jionjion.agentdesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能包安装服务: 处理 ZIP 包的上传、解压、验证
 * <p>
 * 技能包结构要求:
 * <pre>
 * skill-name/
 * ├── SKILL.md          # 必须: 入口文件, 含 YAML frontmatter
 * ├── references/       # 可选: 参考文档
 * ├── examples/         # 可选: 示例文件
 * └── scripts/          # 可选: 可执行脚本
 * </pre>
 *
 * @author Jion
 */
@Service
public class SkillPackageService {

    private static final Logger log = LoggerFactory.getLogger(SkillPackageService.class);

    /** 最大 ZIP 文件大小: 20MB */
    private static final long MAX_ZIP_SIZE = 20 * 1024 * 1024;
    /** 最大解压后大小: 100MB */
    private static final long MAX_UNCOMPRESSED_SIZE = 100 * 1024 * 1024;
    private static final int MAX_ENTRIES = 1000;
    private static final int MAX_SKILL_ID_LENGTH = 64;
    private static final String SKILL_MANIFEST = "SKILL.md";
    private static final String ZIP_EXTENSION = ".zip";
    private static final String FRONTMATTER_DELIMITER = "---";
    private static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    private final String skillsBaseDir;

    public SkillPackageService(@Value("${agentdesk.skills.base-dir}") String skillsBaseDir) {
        this.skillsBaseDir = skillsBaseDir;
    }

    /**
     * 安装技能包: 验证 ZIP -> 解压到用户技能目录 -> 返回技能元数据
     *
     * @param file   上传的 ZIP 文件
     * @param userId 用户 ID
     * @return 安装结果（技能 ID 和名称）
     */
    public SkillInstallResult install(MultipartFile file, Long userId) {
        validateUpload(file);
        try {
            return installArchive(file.getInputStream(), file.getSize(), file.getOriginalFilename(), userId);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取技能包", e);
        }
    }

    /**
     * 从输入流安装技能包。社区下载与本地上传共用同一套验证和安全解压逻辑。
     */
    public SkillInstallResult installArchive(InputStream inputStream, long archiveSize,
                                             String fileName, Long userId) {
        validateArchive(inputStream, archiveSize, fileName);

        Path userSkillsDir = Path.of(skillsBaseDir, String.valueOf(userId));
        Path tempDir = null;
        try {
            Files.createDirectories(userSkillsDir);
            tempDir = Files.createTempDirectory(userSkillsDir, ".installing-");

            // 按实际读取的压缩字节数二次限流，防止上游未报告 Content-Length（如 chunked 传输）时绕过大小校验
            extractZip(new LimitedInputStream(inputStream, MAX_ZIP_SIZE), tempDir);
            Path skillDir = locateSkillDirectory(tempDir);

            SkillMetadata metadata = parseSkillMetadata(skillDir);
            String skillId = metadata.name();

            if (skillId == null || !SKILL_NAME_PATTERN.matcher(skillId).matches()
                    || skillId.length() > MAX_SKILL_ID_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "SKILL.md 中的 name 格式错误: 只能包含小写字母、数字和连字符, 最长 64 字符");
            }

            Path targetDir = userSkillsDir.resolve(skillId);
            if (Files.exists(targetDir)) {
                deleteDirectory(targetDir);
            }
            Files.move(skillDir, targetDir, StandardCopyOption.REPLACE_EXISTING);

            List<String> resources = listResources(targetDir);
            log.info("技能包安装成功: userId={}, skillId={}, resources={}", userId, skillId, resources.size());
            return new SkillInstallResult(skillId, metadata.name(), metadata.description(), resources);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("技能包安装失败: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "技能包安装失败: " + e.getMessage());
        } finally {
            // 清理临时文件
            if (tempDir != null) {
                try {
                    if (Files.exists(tempDir)) {
                        deleteDirectory(tempDir);
                    }
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 卸载技能包: 删除用户技能目录中的技能
     */
    public void uninstall(String skillId, Long userId) {
        Path skillDir = Path.of(skillsBaseDir, String.valueOf(userId), skillId);
        if (!Files.exists(skillDir)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "技能不存在: " + skillId);
        }
        try {
            deleteDirectory(skillDir);
            log.info("技能包卸载成功: userId={}, skillId={}", userId, skillId);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "卸载失败: " + e.getMessage());
        }
    }

    /**
     * 获取技能资源文件列表
     */
    public List<String> getSkillResources(String skillId, Long userId) {
        Path skillDir = Path.of(skillsBaseDir, String.valueOf(userId), skillId);
        if (!Files.exists(skillDir)) {
            return List.of();
        }
        try {
            return listResources(skillDir);
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * 读取技能资源文件内容
     */
    public String readResource(String skillId, Long userId, String resourcePath) {
        Path skillDir = Path.of(skillsBaseDir, String.valueOf(userId), skillId);
        Path resource = skillDir.resolve(resourcePath).normalize();
        // 防止路径遍历攻击
        if (!resource.startsWith(skillDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法路径");
        }
        if (!Files.exists(resource)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资源文件不存在: " + resourcePath);
        }
        try {
            return Files.readString(resource, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取失败: " + e.getMessage());
        }
    }

    // ─── 内部方法 ───

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > MAX_ZIP_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP 文件过大, 最大 20MB");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(ZIP_EXTENSION)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 .zip 格式的技能包");
        }
    }

    private void validateArchive(InputStream inputStream, long archiveSize, String fileName) {
        if (inputStream == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        if (archiveSize > MAX_ZIP_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ZIP 文件过大, 最大 20MB");
        }
        if (fileName == null || !fileName.toLowerCase().endsWith(ZIP_EXTENSION)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 .zip 格式的技能包");
        }
    }

    /**
     * 安全解压 ZIP。
     */
    private void extractZip(InputStream inputStream, Path targetDir) throws IOException {
        long totalSize = 0;
        int entryCount = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "技能包文件数量超出限制 (" + MAX_ENTRIES + ")");
                }

                String entryName = entry.getName();

                if (entryName.startsWith("/") || entryName.startsWith("\\")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "技能包包含非法路径: " + entryName);
                }

                // 跳过 macOS 系统文件
                if (entryName.startsWith("__MACOSX") || entryName.contains(".DS_Store")) {
                    continue;
                }

                Path entryPath = targetDir.resolve(entryName).normalize();
                if (!entryPath.startsWith(targetDir)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "技能包包含非法路径: " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (OutputStream output = Files.newOutputStream(entryPath,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            totalSize += read;
                            if (totalSize > MAX_UNCOMPRESSED_SIZE) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "技能包解压后过大, 最大 100MB");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private Path locateSkillDirectory(Path tempDir) throws IOException {
        if (Files.isRegularFile(tempDir.resolve(SKILL_MANIFEST))) {
            return tempDir;
        }
        try (var stream = Files.list(tempDir)) {
            List<Path> candidates = stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve(SKILL_MANIFEST)))
                    .toList();
            if (candidates.size() == 1) {
                return candidates.getFirst();
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "技能包结构错误: 根目录中必须包含且只能包含一个 SKILL.md");
    }

    private SkillMetadata parseSkillMetadata(Path skillDir) throws IOException {
        String content = Files.readString(skillDir.resolve(SKILL_MANIFEST), StandardCharsets.UTF_8);
        if (!content.startsWith(FRONTMATTER_DELIMITER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "SKILL.md 缺少 YAML frontmatter");
        }
        int end = content.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (end < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "SKILL.md 的 YAML frontmatter 未闭合");
        }
        String frontmatter = content.substring(FRONTMATTER_DELIMITER.length(), end);
        String name = readFrontmatterValue(frontmatter, "name");
        String description = readFrontmatterValue(frontmatter, "description");
        return new SkillMetadata(name, description);
    }

    private String readFrontmatterValue(String frontmatter, String key) {
        String[] lines = frontmatter.replace("\r\n", "\n").split("\n");
        String prefix = key + ":";
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith(prefix)) {
                continue;
            }
            String value = line.substring(prefix.length()).trim();
            if ("|".equals(value) || ">".equals(value)) {
                StringBuilder block = new StringBuilder();
                for (int j = i + 1; j < lines.length; j++) {
                    String continuation = lines[j];
                    if (!continuation.isBlank() && !Character.isWhitespace(continuation.charAt(0))) {
                        break;
                    }
                    if (!continuation.isBlank()) {
                        if (!block.isEmpty()) {
                            block.append(' ');
                        }
                        block.append(continuation.trim());
                    }
                }
                return block.toString();
            }
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
        return null;
    }

    private List<String> listResources(Path skillDir) throws IOException {
        List<String> resources = new ArrayList<>();
        try (var stream = Files.walk(skillDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String relative = skillDir.relativize(path).toString().replace('\\', '/');
                        resources.add(relative);
                    });
        }
        return resources;
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            // 反序删除, 先文件后目录
            stream.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("删除文件失败: {}", path);
                        }
                    });
        }
    }

    // ─── 内部记录 ───

    /**
     * 技能安装结果
     *
     * @param id          技能ID
     * @param name        技能名称
     * @param description 技能描述
     * @param resources   资源文件列表
     */
    public record SkillInstallResult(String id, String name, String description, List<String> resources) {
    }

    /**
     * 技能元数据
     *
     * @param name        技能名称
     * @param description 技能描述
     */
    private record SkillMetadata(String name, String description) {
    }

    /**
     * 限制累计读取字节数的输入流。超过上限即拒绝，用于压缩包大小的实际字节兜底校验。
     */
    private static class LimitedInputStream extends java.io.FilterInputStream {
        private final long maxBytes;
        private long readBytes;

        LimitedInputStream(InputStream in, long maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = super.read(b, off, len);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(long n) {
            readBytes += n;
            if (readBytes > maxBytes) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP 文件过大, 最大 20MB");
            }
        }
    }
}
