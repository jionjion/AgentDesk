package top.jionjion.agentdesk.agent.tool;

import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;

/**
 * 本地路径解析工具: 供本地执行/文件类工具共享的路径规则 (见开发计划 9.4)。
 * <p>
 * 相对路径以项目根为基准解析; 绝对路径直接使用; 无项目上下文时原样返回。
 *
 * @author Jion
 */
final class LocalPathResolver {

    private LocalPathResolver() {
    }

    /**
     * 解析本次调用的有效工作目录。
     * 显式传入优先 (相对路径以项目根为基准); 其次项目默认 cwd; 均无时为 null (客户端使用默认)。
     */
    static String resolveWorkingDir(String workingDir, ProjectRuntimeContext projectContext) {
        String projectRoot = projectContext != null && projectContext.runtimeOnline()
                ? projectContext.rootPath() : null;
        if (workingDir != null && !workingDir.isBlank()) {
            String dir = workingDir.trim();
            if (projectRoot != null && isRelative(dir)) {
                return joinPath(projectRoot, dir);
            }
            return dir;
        }
        if (projectContext != null && projectContext.runtimeOnline()) {
            return projectContext.effectiveCwd();
        }
        return null;
    }

    /**
     * 解析文件路径: 相对路径以项目根为基准; 绝对路径直接使用。
     * 无项目根时原样返回 (由客户端拒绝或按其默认处理)。
     */
    static String resolveFilePath(String path, ProjectRuntimeContext projectContext) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String p = path.trim();
        String projectRoot = projectContext != null && projectContext.runtimeOnline()
                ? projectContext.rootPath() : null;
        if (projectRoot != null && isRelative(p)) {
            return joinPath(projectRoot, p);
        }
        return p;
    }

    /**
     * 判断路径是否位于项目根目录树之内。项目根缺失或路径为相对路径时返回 true (保守不升级风险)。
     */
    static boolean isInsideProject(String absolutePath, ProjectRuntimeContext projectContext) {
        String projectRoot = projectContext != null && projectContext.runtimeOnline()
                ? projectContext.rootPath() : null;
        if (projectRoot == null || absolutePath == null || isRelative(absolutePath)) {
            return true;
        }
        String root = normalize(projectRoot);
        String target = normalize(absolutePath);
        return target.equals(root) || target.startsWith(root + "/");
    }

    private static String normalize(String path) {
        String p = path.replace('\\', '/');
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        // Windows 路径大小写不敏感
        if (p.length() >= 2 && Character.isLetter(p.charAt(0)) && p.charAt(1) == ':') {
            return p.toLowerCase();
        }
        return p;
    }

    /** 判断路径是否为相对路径 (非 Windows 盘符/UNC/Unix 绝对路径) */
    static boolean isRelative(String path) {
        if (path.startsWith("/") || path.startsWith("\\")) {
            return false;
        }
        // Windows 盘符: C:\ 或 C:/
        return !(path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':');
    }

    /** 以项目根为基准拼接相对路径, 分隔符跟随项目根风格 */
    static String joinPath(String root, String relative) {
        String sep = root.contains("\\") ? "\\" : "/";
        String base = root.endsWith("/") || root.endsWith("\\")
                ? root.substring(0, root.length() - 1) : root;
        return base + sep + relative.replace(sep.equals("\\") ? "/" : "\\", sep);
    }
}
