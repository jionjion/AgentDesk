package top.jionjion.agentdesk.agent.tool;

import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import top.jionjion.agentdesk.agent.exec.ClientExecutor;

/**
 * 本地路径解析与本地工具入参校验测试 (见开发计划 9.4/10.3)
 */
class LocalPathResolverTest {

    private static ProjectRuntimeContext online(String rootPath) {
        return new ProjectRuntimeContext(
                "proj-1", "Demo", null, "dev-1",
                rootPath, null, "win32",
                "C:\\Python312\\python.exe", "3.12.4", true);
    }

    @Test
    void relativeWorkingDirResolvesAgainstProjectRoot() {
        assertEquals("W:\\Demo\\src", LocalPathResolver.resolveWorkingDir("src", online("W:\\Demo")));
        assertEquals("/home/u/demo/src",
                LocalPathResolver.resolveWorkingDir("src", new ProjectRuntimeContext(
                        "p", "n", null, "d", "/home/u/demo", null, "linux", null, null, true)));
    }

    @Test
    void absoluteWorkingDirUsedAsIs() {
        assertEquals("D:\\Other", LocalPathResolver.resolveWorkingDir("D:\\Other", online("W:\\Demo")));
    }

    @Test
    void missingWorkingDirFallsBackToEffectiveCwd() {
        assertEquals("W:\\Demo", LocalPathResolver.resolveWorkingDir(null, online("W:\\Demo")));
        assertNull(LocalPathResolver.resolveWorkingDir(null, null));
    }

    @Test
    void insideProjectDetectionIsCaseInsensitiveOnWindowsPaths() {
        ProjectRuntimeContext ctx = online("W:\\Demo");
        assertTrue(LocalPathResolver.isInsideProject("W:\\Demo\\src\\a.txt", ctx));
        assertTrue(LocalPathResolver.isInsideProject("w:\\demo\\a.txt", ctx));
        assertFalse(LocalPathResolver.isInsideProject("C:\\Windows\\a.txt", ctx));
        // 前缀相似但不同目录不误判
        assertFalse(LocalPathResolver.isInsideProject("W:\\Demo2\\a.txt", ctx));
    }

    @Test
    void pythonExecRequiresExactlyOneOfCodeOrScript() {
        ClientExecutor bridge = mock(ClientExecutor.class);
        when(bridge.isConnected(1L)).thenReturn(true);
        PythonExecTool tool = new PythonExecTool(bridge, 1L, "sess-1");

        ProjectRuntimeContext ctx = online("W:\\Demo");
        assertTrue(tool.execute(null, null, null, null, null, ctx).startsWith("错误"));
        assertTrue(tool.execute("print(1)", "a.py", null, null, null, ctx).startsWith("错误"));
    }

    @Test
    void pythonExecRequiresOnlineProjectWithInterpreter() {
        ClientExecutor bridge = mock(ClientExecutor.class);
        when(bridge.isConnected(1L)).thenReturn(true);
        PythonExecTool tool = new PythonExecTool(bridge, 1L, "sess-1");

        // 无项目上下文
        assertTrue(tool.execute("print(1)", null, null, null, null, null).contains("未绑定项目"));
        // 在线但无解释器
        ProjectRuntimeContext noPython = new ProjectRuntimeContext(
                "p", "n", null, "d", "W:\\Demo", null, "win32", null, null, true);
        assertTrue(tool.execute("print(1)", null, null, null, null, noPython).contains("Python"));
    }
}
