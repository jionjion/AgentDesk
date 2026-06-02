package top.jionjion.agentdesk.service.chat;

import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.dto.chat.ChatRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PromptContextBuilder 纯函数单测: 校验 buildToolDescriptions 输出与前端
 * sandbox-tools.ts:getToolDescriptions() 模板逐字符等价。
 *
 * @author Jion
 */
class PromptContextBuilderTest {

    private final PromptContextBuilder builder = new PromptContextBuilder();

    @Test
    void buildToolDescriptions_emptyReturnsBlank() {
        assertEquals("", builder.buildToolDescriptions(null));
        assertEquals("", builder.buildToolDescriptions(List.of()));
    }

    @Test
    void buildToolDescriptions_matchesFrontendTemplateCharByChar() {
        List<ChatRequest.SandboxTool> tools = List.of(
                new ChatRequest.SandboxTool(
                        "list_files(dir: str = \"/data\") -> list[str]",
                        "列出 /data/ 目录下的文件"),
                new ChatRequest.SandboxTool(
                        "read_pdf(path: str) -> list[str]",
                        "读取 PDF 文件文本内容")
        );

        // 与前端 getToolDescriptions() 模板逐字符对齐的期望输出。
        // 注意: 故意用字符串拼接而非文本块——文本块的闭合 """ 独占一行会引入末尾换行,
        // 与 buildToolDescriptions 以 ``` 结尾(无末尾换行)不一致, 会破坏逐字符断言。
        String expected = """
                ## 沙箱可用工具函数

                以下工具函数已预装在沙箱环境中，可直接通过 `tools.` 命名空间调用：

                - `tools.list_files(dir: str = "/data") -> list[str]` — 列出 /data/ 目录下的文件
                - `tools.read_pdf(path: str) -> list[str]` — 读取 PDF 文件文本内容

                使用示例：
                ```python
                files = tools.list_files()
                df = tools.to_dataframe('/data/sales.xlsx')
                tools.save_file(df, 'result.csv')
                ```
                """;

        assertEquals(expected, builder.buildToolDescriptions(tools));
    }

    @Test
    void buildSandboxAugmentedMessage_nullContextReturnsOriginal() {
        assertEquals("你好", builder.buildSandboxAugmentedMessage("你好", null));
    }

    @Test
    void buildSandboxAugmentedMessage_includesFilesToolsGuideAndMessage() {
        ChatRequest.SandboxContext ctx = new ChatRequest.SandboxContext(
                List.of(new ChatRequest.SandboxTool(
                        "list_files() -> list[str]", "列出文件")),
                List.of("sales.csv")
        );
        String result = builder.buildSandboxAugmentedMessage("分析数据", ctx);

        assertTrue(result.contains("[本地 Python 沙箱文件]"));
        assertTrue(result.contains("- /data/sales.csv"));
        assertTrue(result.contains("[沙箱中可用的 Python 工具函数]"));
        assertTrue(result.contains("- `tools.list_files() -> list[str]` — 列出文件"));
        assertTrue(result.contains("[使用方式]"));
        assertTrue(result.endsWith("分析数据"));
    }
}
