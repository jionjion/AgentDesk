package top.jionjion.agentdesk.agent.tool;

/**
 * 工具名称和描述的集中定义。
 * 所有 @Tool 注解中的 name/description 都引用此处的常量，避免硬编码字符串散落各处。
 *
 * @author Jion
 */
public final class ToolDefinitions {

    private ToolDefinitions() {
    }

    // ==================== 基础工具 ====================

    public static final String GET_CURRENT_TIME = "get_current_time";
    public static final String GET_CURRENT_TIME_DESC = "获取当前时间, 可以指定时区";

    public static final String CALCULATE = "calculate";
    public static final String CALCULATE_DESC = "计算一个数学表达式, 支持加减乘除";

    public static final String READ_FILE = "read_file";
    public static final String READ_FILE_DESC = "读取用户上传的文件内容。传入 fileId 获取文件的文本内容。" +
            "支持的文件类型: txt, md, csv, json, xml, log, java, py, js, ts, html, css, yaml, yml, properties, sql。" +
            "文件大小上限 500KB。";

    // ==================== Web 工具（子代理使用） ====================

    public static final String WEB_SEARCH = "web_search";
    public static final String WEB_SEARCH_DESC = "搜索互联网, 返回与查询相关的搜索结果列表。每条结果包含标题、摘要和链接。";

    public static final String URL_FETCH = "url_fetch";
    public static final String URL_FETCH_DESC = "抓取指定 URL 的网页正文内容, 返回提取后的纯文本。用于深入了解搜索结果中的某个链接。";

    // ==================== 子代理工具名 ====================

    public static final String WEB_RESEARCHER = "web_researcher";
    public static final String WEB_RESEARCHER_DESC = "联网研究助手。当用户需要搜索互联网、查询网页内容、获取最新资讯时，" +
            "将任务委派给此子代理。传入清晰的任务描述，子代理会搜索并返回精简的结果摘要。";

    public static final String DEEP_RESEARCHER = "deep_researcher";
    public static final String DEEP_RESEARCHER_DESC = "深度研究助手。当用户提出复杂问题需要多角度调研、交叉验证、综合分析时，" +
            "将任务委派给此子代理。子代理会进行多轮搜索，对比不同来源，输出结构化的研究报告。";

    public static final String TRANSLATOR = "translator";
    public static final String TRANSLATOR_DESC = "翻译助手。当用户需要翻译文本、文档或技术内容时，将任务委派给此子代理。" +
            "传入待翻译文本和目标语言，子代理会返回准确、自然的翻译结果。";

    public static final String CODE_REVIEWER = "code_reviewer";
    public static final String CODE_REVIEWER_DESC = "代码审查助手。当用户提交代码要求审查、或上传文件需要代码分析时，" +
            "将任务委派给此子代理。子代理会从安全性、性能、正确性、可维护性等维度审查代码，返回结构化的审查意见。";
}
