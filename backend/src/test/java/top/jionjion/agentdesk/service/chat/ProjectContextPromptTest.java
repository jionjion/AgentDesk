package top.jionjion.agentdesk.service.chat;

import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * project_context 提示词注入测试 (见开发计划 9.3)
 */
class ProjectContextPromptTest {

    private final PromptContextBuilder builder = new PromptContextBuilder();

    @Test
    void nullContextReturnsOriginalMessage() {
        assertEquals("你好", builder.buildProjectAugmentedMessage("你好", null));
    }

    @Test
    void onlineContextContainsProjectAndRuntimeInfo() {
        ProjectRuntimeContext ctx = new ProjectRuntimeContext(
                "proj-1", "AgentDesk", null, "dev-1",
                "W:\\AgentDesk", null, "win32",
                "C:\\Python312\\python.exe", "3.12.4", true);

        String result = builder.buildProjectAugmentedMessage("帮我看看代码", ctx);

        assertTrue(result.startsWith("<project_context>"));
        assertTrue(result.contains("项目：AgentDesk"));
        assertTrue(result.contains("项目 ID：proj-1"));
        assertTrue(result.contains("本地根目录：W:\\AgentDesk"));
        assertTrue(result.contains("默认工作目录：W:\\AgentDesk"));
        assertTrue(result.contains("系统：Windows"));
        assertTrue(result.contains("Python：3.12.4 (C:\\Python312\\python.exe)"));
        assertTrue(result.contains("本地运行环境：已连接"));
        assertTrue(result.endsWith("帮我看看代码"));
    }

    @Test
    void offlineContextOmitsPathsAndMarksDisconnected() {
        ProjectRuntimeContext ctx = new ProjectRuntimeContext(
                "proj-1", "AgentDesk", null, null,
                null, null, null, null, null, false);

        String result = builder.buildProjectAugmentedMessage("你好", ctx);

        assertTrue(result.contains("项目：AgentDesk"));
        assertTrue(result.contains("本地运行环境：未连接"));
        assertFalse(result.contains("本地根目录"));
        assertFalse(result.contains("Python："));
    }

    @Test
    void instructionsAreIncludedWhenPresent() {
        ProjectRuntimeContext ctx = new ProjectRuntimeContext(
                "proj-1", "AgentDesk", "回复使用中文", null,
                null, null, null, null, null, false);

        assertTrue(builder.buildProjectAugmentedMessage("hi", ctx).contains("项目指令：回复使用中文"));
    }

    @Test
    void missingPythonIsReportedAsNotDetected() {
        ProjectRuntimeContext ctx = new ProjectRuntimeContext(
                "proj-1", "AgentDesk", null, "dev-1",
                "/home/u/demo", "/home/u/demo/src", "linux", null, null, true);

        String result = builder.buildProjectAugmentedMessage("hi", ctx);

        assertTrue(result.contains("系统：Linux"));
        assertTrue(result.contains("默认工作目录：/home/u/demo/src"));
        assertTrue(result.contains("Python：未检测到"));
    }
}
