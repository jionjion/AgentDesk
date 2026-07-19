package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.Project;

import java.util.List;
import java.util.Optional;

/**
 * 项目 — JPA 持久层
 *
 * @author Jion
 */
public interface ProjectRepository extends JpaRepository<Project, String> {

    /**
     * 根据用户ID查询项目列表, 按更新时间倒序
     *
     * @param userId 用户ID
     * @return 项目列表
     */
    List<Project> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * 根据项目ID和用户ID查询项目 (归属校验)
     *
     * @param id     项目ID
     * @param userId 用户ID
     * @return 项目
     */
    Optional<Project> findByIdAndUserId(String id, Long userId);
}
