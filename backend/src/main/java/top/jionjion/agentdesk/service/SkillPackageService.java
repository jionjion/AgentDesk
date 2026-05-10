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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
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

    private static final long MAX_ZIP_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_UNCOMPRESSED_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MAX_ENTRIES = 100;
    private static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");
    private static final Pattern FRONTMATTER_NAME = Pattern.compile("^name:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern FRONTMATTER_DESC = Pattern.compile("^description:\\s*(.+)$", Pattern.MULTILINE);

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
        // 1. 基本验证
        validateUpload(file);

        // 2. 解压到临时目录, 验证结构
        Path userSkillsDir = Path.of(skillsBaseDir, String.valueOf(userId));
        Path tempDir = null;
        try {
            Files.createDirectories(userSkillsDir);
            tempDir = Files.createTempDirectory(userSkillsDir, ".installing-");

            // 3. 安全解压 ZIP
            String skillId = extractZip(file.getInputStream(), tempDir);

            // 4. 验证技能包结构
            Path skillDir = tempDir.resolve(skillId);
            if (!Files.exists(skillDir)) {
                // ZIP 内容可能直接是文件（没有外层目录）
                if (Files.exists(tempDir.resolve("SKILL.md"))) {
                    skillDir = tempDir;
                    skillId = inferSkillId(skillDir);
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "技能包结构错误: 缺少 SKILL.md 入口文件");
                }
            }

            validateSkillStructure(skillDir);

            // 5. 从 SKILL.md 提取元数据
            SkillMetadata metadata = parseSkillMetadata(skillDir);
            if (metadata.name() != null && !metadata.name().isBlank()) {
                skillId = metadata.name();
            }

            // 6. 验证技能 ID 格式
            if (!SKILL_NAME_PATTERN.matcher(skillId).matches() || skillId.length() > 64) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "技能 ID 格式错误: 只能包含小写字母、数字和连字符, 不能以连字符开头, 最长 64 字符");
            }

            // 7. 移动到最终位置
            Path targetDir = userSkillsDir.resolve(skillId);
            if (Files.exists(targetDir)) {
                // 删除旧版本
                deleteDirectory(targetDir);
            }
            Files.move(skillDir, targetDir, StandardCopyOption.REPLACE_EXISTING);

            // 8. 收集资源文件列表
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ZIP 文件过大, 最大 10MB, 当前 " + (file.getSize() / 1024 / 1024) + "MB");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".zip")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 .zip 格式的技能包");
        }
    }

    /**
     * 安全解压 ZIP, 返回技能根目录名
     */
    private String extractZip(InputStream inputStream, Path targetDir) throws IOException {
        String rootDirName = null;
        long totalSize = 0;
        int entryCount = 0;

        try (ZipInputStream zis = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "技能包文件数量超出限制 (" + MAX_ENTRIES + ")");
                }

                String entryName = entry.getName();

                // 安全检查: 防止 Zip Slip 攻击
                if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "技能包包含非法路径: " + entryName);
                }

                // 跳过 macOS 系统文件
                if (entryName.startsWith("__MACOSX") || entryName.contains(".DS_Store")) {
                    continue;
                }

                // 提取根目录名
                if (rootDirName == null) {
                    int sep = entryName.indexOf('/');
                    if (sep > 0) {
                        rootDirName = entryName.substring(0, sep);
                    }
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
                    totalSize += entry.getSize();
                    if (totalSize > MAX_UNCOMPRESSED_SIZE) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "技能包解压后过大, 最大 50MB");
                    }
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        return rootDirName != null ? rootDirName : "";
    }

    private void validateSkillStructure(Path skillDir) {
        Path skillMd = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillMd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "技能包结构错误: 缺少 SKILL.md 入口文件");
        }
    }

    private SkillMetadata parseSkillMetadata(Path skillDir) throws IOException {
        String content = Files.readString(skillDir.resolve("SKILL.md"), StandardCharsets.UTF_8);
        String name = null;
        String description = null;

        // 解析 YAML frontmatter
        if (content.startsWith("---")) {
            int end = content.indexOf("---", 3);
            if (end > 0) {
                String frontmatter = content.substring(3, end);
                Matcher nameMatcher = FRONTMATTER_NAME.matcher(frontmatter);
                if (nameMatcher.find()) {
                    name = nameMatcher.group(1).trim();
                }
                Matcher descMatcher = FRONTMATTER_DESC.matcher(frontmatter);
                if (descMatcher.find()) {
                    description = descMatcher.group(1).trim();
                }
            }
        }

        return new SkillMetadata(name, description);
    }

    private String inferSkillId(Path skillDir) throws IOException {
        SkillMetadata metadata = parseSkillMetadata(skillDir);
        if (metadata.name() != null && !metadata.name().isBlank()) {
            return metadata.name();
        }
        return skillDir.getFileName().toString();
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
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)) // 反序删除, 先文件后目录
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

    public record SkillInstallResult(String id, String name, String description, List<String> resources) {
    }

    private record SkillMetadata(String name, String description) {
    }
}
