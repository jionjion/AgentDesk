package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.McpServer;

import java.util.List;
import java.util.Optional;

/**
 * MCP 服务器配置仓库
 *
 * @author Jion
 */
public interface McpServerRepository extends JpaRepository<McpServer, Long> {

    List<McpServer> findByUserId(Long userId);

    List<McpServer> findByUserIdAndEnabledTrue(Long userId);

    Optional<McpServer> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);
}
