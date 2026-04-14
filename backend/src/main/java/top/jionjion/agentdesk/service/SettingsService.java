package top.jionjion.agentdesk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.*;
import top.jionjion.agentdesk.entity.User;
import top.jionjion.agentdesk.entity.UserSettings;
import top.jionjion.agentdesk.repository.UserRepository;
import top.jionjion.agentdesk.repository.UserSettingsRepository;
import top.jionjion.agentdesk.security.UserContext;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * 设置业务逻辑
 *
 * @author Jion
 */
@Service
public class SettingsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final OssService ossService;
    private final ModelRegistryService modelRegistryService;

    public SettingsService(UserRepository userRepository,
                           UserSettingsRepository userSettingsRepository,
                           PasswordEncoder passwordEncoder,
                           OssService ossService,
                           ModelRegistryService modelRegistryService) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.ossService = ossService;
        this.modelRegistryService = modelRegistryService;
    }

    /**
     * 获取当前用户的全部设置, 不存在则返回默认值
     */
    public SettingsResponse getAllSettings() {
        Long userId = UserContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        ProfileDto profile = buildProfileDto(user);

        UserSettings entity = userSettingsRepository.findByUserId(userId).orElse(null);
        ModelSettingsDto model = (entity != null) ? parseModel(entity.getSettings()) : ModelSettingsDto.defaults();
        AppSettingsDto app = (entity != null) ? parseApp(entity.getSettings()) : AppSettingsDto.defaults();

        return new SettingsResponse(profile, model, app);
    }

    /**
     * 修改个人资料 (昵称/头像)
     */
    @Transactional
    public ProfileDto updateProfile(UpdateProfileRequest request) {
        if (request.nickname() == null || request.nickname().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "昵称不能为空");
        }
        if (request.nickname().length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "昵称最长128字符");
        }
        if (request.avatar() != null && request.avatar().length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像URL最长512字符");
        }

        Long userId = UserContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        user.setNickname(request.nickname());
        user.setAvatar(request.avatar());
        user.setUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);

        return buildProfileDto(user);
    }

    /**
     * 上传头像到 OSS
     */
    @Transactional
    public ProfileDto uploadAvatar(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PNG/JPEG/GIF/WebP 格式");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像文件不能超过 2MB");
        }

        Long userId = UserContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        // 生成 OSS key
        String ext = extractExtension(file.getOriginalFilename());
        String ossKey = "avatar/" + userId + "/" + System.currentTimeMillis()
                + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

        try {
            ossService.upload(ossKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "头像上传失败");
        }

        // 删除旧头像
        String oldAvatar = user.getAvatar();
        if (oldAvatar != null && oldAvatar.startsWith("avatar/")) {
            try {
                ossService.delete(oldAvatar);
            } catch (Exception ignored) {
            }
        }

        user.setAvatar(ossKey);
        user.setUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);

        return buildProfileDto(user);
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        if (request.oldPassword() == null || request.oldPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码不能为空");
        }
        if (request.newPassword() == null || request.newPassword().length() < 6 || request.newPassword().length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码长度需为6-64字符");
        }

        Long userId = UserContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);
    }

    /**
     * 修改模型配置
     */
    @Transactional
    public ModelSettingsDto updateModelSettings(ModelSettingsDto dto) {
        validateModelSettings(dto);

        Long userId = UserContext.getUserId();
        UserSettings entity = getOrCreateSettings(userId);

        ObjectNode root = parseSettingsJson(entity.getSettings());
        root.set("model", MAPPER.valueToTree(dto));
        entity.setSettings(toJson(root));
        entity.setUpdatedAt(System.currentTimeMillis());
        userSettingsRepository.save(entity);

        return dto;
    }

    /**
     * 修改应用偏好
     */
    @Transactional
    public AppSettingsDto updateAppSettings(AppSettingsDto dto) {
        validateAppSettings(dto);

        Long userId = UserContext.getUserId();
        UserSettings entity = getOrCreateSettings(userId);

        ObjectNode root = parseSettingsJson(entity.getSettings());
        root.set("app", MAPPER.valueToTree(dto));
        entity.setSettings(toJson(root));
        entity.setUpdatedAt(System.currentTimeMillis());
        userSettingsRepository.save(entity);

        return dto;
    }

    /**
     * 获取指定用户的模型配置 (供 AgentFactory 调用)
     */
    public ModelSettingsDto getModelSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .map(entity -> parseModel(entity.getSettings()))
                .orElse(ModelSettingsDto.defaults());
    }

    /**
     * 获取用户的 DashScope API Key。
     * 优先返回用户自定义 Key, 未配置则返回 null (由 ChatModelFactory fallback 到系统 Key)。
     */
    public String getDashScopeApiKey(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .map(entity -> parseDashScopeKey(entity.getSettings()))
                .orElse(null);
    }

    /**
     * 更新用户的 DashScope API Key
     */
    @Transactional
    public void updateDashScopeApiKey(Long userId, String apiKey) {
        UserSettings entity = getOrCreateSettings(userId);
        ObjectNode root = parseSettingsJson(entity.getSettings());

        ObjectNode providerKeys = root.has("providerKeys")
                ? (ObjectNode) root.get("providerKeys")
                : root.putObject("providerKeys");
        providerKeys.put("dashscope", apiKey);

        entity.setSettings(toJson(root));
        entity.setUpdatedAt(System.currentTimeMillis());
        userSettingsRepository.save(entity);
    }

    /**
     * 获取脱敏的 DashScope API Key（前端展示用）
     */
    public String getMaskedDashScopeKey(Long userId) {
        String key = getDashScopeApiKey(userId);
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 5) + "****" + key.substring(key.length() - 3);
    }

    // ==================== 内部方法 ====================

    /**
     * 构建 ProfileDto, 如果 avatar 是 OSS key 则生成预签名 URL
     */
    private ProfileDto buildProfileDto(User user) {
        String avatar = user.getAvatar();
        if (avatar != null && avatar.startsWith("avatar/")) {
            avatar = ossService.generatePresignedUrl(avatar, 60);
        }
        return new ProfileDto(user.getId(), user.getUsername(), user.getNickname(), avatar);
    }

    private static String extractExtension(String filename) {
        if (filename == null) return ".jpg";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".jpg";
    }

    private UserSettings getOrCreateSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> new UserSettings(userId, "{}"));
    }

    private ObjectNode parseSettingsJson(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node instanceof ObjectNode objectNode) {
                return objectNode;
            }
            return MAPPER.createObjectNode();
        } catch (JsonProcessingException e) {
            return MAPPER.createObjectNode();
        }
    }

    private ModelSettingsDto parseModel(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode modelNode = root.get("model");
            if (modelNode != null) {
                ModelSettingsDto dto = MAPPER.treeToValue(modelNode, ModelSettingsDto.class);
                // 向后兼容: 旧数据中可能只有 provider+modelName, 没有 modelId
                if (dto.modelId() == null || dto.modelId().isBlank()) {
                    // 旧数据使用 modelName 字段 (如 "qwen-plus"), 直接作为 modelId
                    String legacyModelName = modelNode.has("modelName")
                            ? modelNode.get("modelName").asText() : null;
                    if (legacyModelName != null && !legacyModelName.isBlank()
                            && modelRegistryService.isValidModelId(legacyModelName)) {
                        return new ModelSettingsDto(
                                legacyModelName,
                                dto.temperature(), dto.maxTokens(), dto.topP(),
                                dto.enableThinking(), dto.systemPrompt()
                        );
                    }
                    return ModelSettingsDto.defaults();
                }
                return dto;
            }
        } catch (JsonProcessingException ignored) {
        }
        return ModelSettingsDto.defaults();
    }

    private AppSettingsDto parseApp(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode appNode = root.get("app");
            if (appNode != null) {
                return MAPPER.treeToValue(appNode, AppSettingsDto.class);
            }
        } catch (JsonProcessingException ignored) {
        }
        return AppSettingsDto.defaults();
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String parseDashScopeKey(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode keysNode = root.get("providerKeys");
            if (keysNode != null && keysNode.has("dashscope")) {
                String key = keysNode.get("dashscope").asText();
                return key.isBlank() ? null : key;
            }
        } catch (JsonProcessingException ignored) {
        }
        return null;
    }

    private void validateModelSettings(ModelSettingsDto dto) {
        if (dto.modelId() == null || dto.modelId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型ID不能为空");
        }
        if (!modelRegistryService.isValidModelId(dto.modelId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的模型: " + dto.modelId());
        }
        if (dto.temperature() != null && (dto.temperature() < 0.0 || dto.temperature() > 2.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "temperature 范围: 0.0 ~ 2.0");
        }
        // maxTokens 上限从模型定义中获取
        if (dto.maxTokens() != null) {
            int maxAllowed = modelRegistryService.findById(dto.modelId())
                    .map(ModelDefinition::maxOutputTokens)
                    .orElse(65536);
            if (dto.maxTokens() < 1 || dto.maxTokens() > maxAllowed) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "maxTokens 范围: 1 ~ " + maxAllowed);
            }
        }
        if (dto.topP() != null && (dto.topP() < 0.0 || dto.topP() > 1.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topP 范围: 0.0 ~ 1.0");
        }
        if (dto.systemPrompt() != null && dto.systemPrompt().length() > 4096) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统提示词最长4096字符");
        }
    }

    private void validateAppSettings(AppSettingsDto dto) {
        if (dto.theme() != null && !dto.theme().matches("^(light|dark|auto)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "theme 可选值: light, dark, auto");
        }
        if (dto.language() != null && !dto.language().matches("^(zh-CN|en-US)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "language 可选值: zh-CN, en-US");
        }
        if (dto.sendKey() != null && !dto.sendKey().matches("^(Enter|Ctrl\\+Enter)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sendKey 可选值: Enter, Ctrl+Enter");
        }
        if (dto.fontSize() != null && (dto.fontSize() < 12 || dto.fontSize() > 24)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fontSize 范围: 12 ~ 24");
        }
    }
}
