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

    public static final String API_CALL = "api_call";
    public static final String API_CALL_DESC = "向第三方 API 发送 HTTP 请求。支持 GET、POST、PUT、DELETE 方法。" +
            "可自定义请求头和请求体。返回响应状态码和响应内容（截断到 4000 字符）。";

    public static final String IP_LOCATION = "ip_location";
    public static final String IP_LOCATION_DESC = "查询 IP 地址的地理位置信息。不传 IP 则查询当前服务器的公网 IP 位置。" +
            "返回国家、省份、城市、区域、邮编、区号、运营商等信息。";

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
    public static final String CODE_REVIEWER_DESC = "代码分析助手。当用户提交代码要求审查、分析、优化、找bug、看看有没有问题时，" +
            "将任务委派给此子代理。支持直接分析消息中的代码片段，也支持通过 read_file 读取上传的代码文件。" +
            "子代理会从安全性、性能、正确性、可维护性等维度分析代码，返回结构化的分析意见。";

    public static final String SUMMARIZER = "summarizer";
    public static final String SUMMARIZER_DESC = "摘要助手。当用户需要对长文本、长文档、长对话、日志等内容进行总结、提炼、归纳要点时，" +
            "将任务委派给此子代理。传入需要摘要的内容，子代理会返回精简的结构化摘要。";

    public static final String PLANNER = "planner";
    public static final String PLANNER_DESC = "任务规划助手。当用户提出一个复杂目标、项目计划、学习路线、或需要拆解执行步骤时，" +
            "将任务委派给此子代理。子代理会输出结构化的步骤清单，包含优先级、依赖关系和注意事项。";

    // ==================== 动态子代理工具 ====================

    public static final String CREATE_AGENT = "create_agent";
    public static final String CREATE_AGENT_DESC = "动态创建临时子代理。自定义 system prompt 和工具组合，执行指定任务后返回结果。" +
            "适用于需要特定角色视角、专业分析、或组合多种工具完成复杂子任务的场景。" +
            "可选工具: web_search, url_fetch, get_current_time, calculate, read_file, api_call, ip_location。";

    // ==================== 批量研究工具 ====================

    public static final String BATCH_WEB_RESEARCHER = "batch_web_researcher";
    public static final String BATCH_WEB_RESEARCHER_DESC = "批量并行联网研究工具。对一个研究主题自动拆解为多个子查询并行搜索，合并去重后返回精简结果。" +
            "适用于开放性调研问题（如'XX的现状和趋势'），不适用于已知具体URL的抓取。";

    // ==================== 远程执行工具 ====================

    public static final String REMOTE_EXEC = "remote_exec";
    public static final String REMOTE_EXEC_DESC = "在用户的本地机器上执行 shell 命令并返回结果。" +
            "命令会通过网络发送到用户的桌面客户端执行, 适用于需要访问用户本地文件系统、运行项目命令、查看系统状态等场景。" +
            "重要: 执行结果会包含客户端操作系统信息, 请根据目标系统使用正确的命令语法 " +
            "(Windows 使用 cmd/PowerShell 命令如 dir, type, del; macOS/Linux 使用 Unix 命令如 ls, cat, rm)。" +
            "低风险命令（如 ls/dir, cat/type, git status）会自动执行, 高风险命令（如 rm/del, npm install, git push）需要用户确认。";
}
