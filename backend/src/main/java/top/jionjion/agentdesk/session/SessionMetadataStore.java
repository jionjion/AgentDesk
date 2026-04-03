package top.jionjion.agentdesk.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话元数据持久化存储 (JSON 文件)
 */
@Component
public class SessionMetadataStore {

    private static final Logger log = LoggerFactory.getLogger(SessionMetadataStore.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Path metadataFile;
    private final CopyOnWriteArrayList<SessionMetadata> metadataList;

    public SessionMetadataStore(
            @Value("${agentdesk.session.path:#{systemProperties['user.home'] + '/.agentdesk/sessions'}}") String sessionPath) {
        this.metadataFile = Path.of(sessionPath, "metadata.json");
        this.metadataList = new CopyOnWriteArrayList<>(loadFromFile());
    }

    public List<SessionMetadata> findAll() {
        return new ArrayList<>(metadataList);
    }

    public Optional<SessionMetadata> findById(String id) {
        return metadataList.stream().filter(m -> m.getId().equals(id)).findFirst();
    }

    public void save(SessionMetadata metadata) {
        // 如果已存在则更新, 否则添加
        boolean updated = false;
        for (int i = 0; i < metadataList.size(); i++) {
            if (metadataList.get(i).getId().equals(metadata.getId())) {
                metadataList.set(i, metadata);
                updated = true;
                break;
            }
        }
        if (!updated) {
            metadataList.add(metadata);
        }
        persistToFile();
    }

    public void deleteById(String id) {
        metadataList.removeIf(m -> m.getId().equals(id));
        persistToFile();
    }

    private List<SessionMetadata> loadFromFile() {
        if (!Files.exists(metadataFile)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(metadataFile.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            log.warn("读取会话元数据失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void persistToFile() {
        try {
            Files.createDirectories(metadataFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(metadataFile.toFile(), metadataList);
        } catch (IOException e) {
            log.error("持久化会话元数据失败: {}", e.getMessage());
        }
    }
}
