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

    /**
     * 根据用户ID查询所有MCP服务器
     *
     * @param userId 用户ID
     * @return MCP服务器列表
     */
    List<McpServer> findByUserId(Long userId);

    /**
     * 根据用户ID查询已启用的MCP服务器
     *
     * @param userId 用户ID
     * @return 已启用的MCP服务器列表
     */
    List<McpServer> findByUserIdAndEnabledTrue(Long userId);

    /**
     * 根据ID和用户ID查询MCP服务器
     *
     * @param id     MCP服务器ID
     * @param userId 用户ID
     * @return MCP服务器Optional
     */
    Optional<McpServer> findByIdAndUserId(Long id, Long userId);

    /**
     * 检查指定用户和名称的MCP服务器是否存在
     *
     * @param userId 用户ID
     * @param name   MCP服务器名称
     * @return 是否存在
     */
    boolean existsByUserIdAndName(Long userId, String name);
}
