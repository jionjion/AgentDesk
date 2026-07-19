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

    // ==================== 本地执行工具 ====================

    public static final String SHELL_EXEC = "shell_exec";
    public static final String SHELL_EXEC_DESC = "在用户本地机器上执行 shell 命令。" +
            "命令通过网络发送到用户桌面客户端执行，拥有完整能力：访问真实文件系统、联网、安装依赖、运行项目命令、查看系统状态。" +
            "重要：执行结果会包含客户端操作系统信息，请按目标系统使用正确语法 " +
            "(Windows 用 cmd/PowerShell 如 dir, type, del；macOS/Linux 用 Unix 命令如 ls, cat, rm)。" +
            "低风险命令（如 ls/dir, cat/type, git status）自动执行，高风险命令（如 rm/del, npm install, git push）需用户确认。" +
            "运行 Python 代码时优先使用 python_exec 工具；读写/检索本地文件优先使用 local_read_file/local_write_file/local_edit_file/local_list_files/local_search_files。" +
            "【工作目录规则】working_dir 是逐次调用参数，不跨工具调用保持；默认使用当前项目根目录。" +
            "单独执行 `cd subdir` 只影响该次短生命周期 shell，下一次调用不会继承；" +
            "需要在子目录运行时，传 working_dir=\"subdir\"（相对路径按项目根解析），或在同一条命令中使用 `cd subdir && command`。";

    public static final String PYTHON_EXEC = "python_exec";
    public static final String PYTHON_EXEC_DESC = "在用户本地机器上用项目配置的真实 Python 解释器执行代码。" +
            "code 与 script_path 二选一：code 为内联代码（通过 stdin 传给解释器），script_path 为要执行的现有脚本路径。" +
            "解释器由项目本地配置决定，可导入本机已安装的任意包（含 C 扩展包）、访问真实文件系统与网络。" +
            "cwd 为空时使用项目根目录；相对 cwd 按项目根解析。执行需要用户确认（高风险）。" +
            "缺少依赖包时会返回真实报错，此时可提出安装命令并通过 shell_exec 执行（需审批），不要假设包已安装。";

    public static final String LOCAL_READ_FILE = "local_read_file";
    public static final String LOCAL_READ_FILE_DESC = "读取用户本地项目中的文本文件（UTF-8）。" +
            "相对路径按项目根解析，绝对路径直接使用。支持 offset/limit 按行分块读取大文件；" +
            "返回内容超限时带截断标记与可继续读取的 offset。二进制文件返回元数据错误而非内容。";

    public static final String LOCAL_WRITE_FILE = "local_write_file";
    public static final String LOCAL_WRITE_FILE_DESC = "在用户本地写入文本文件（UTF-8，整文件覆盖或新建）。" +
            "相对路径按项目根解析。单次内容上限 2MB，超限请分块或改用脚本。" +
            "项目目录外的写入会提升为高风险需用户确认。";

    public static final String LOCAL_EDIT_FILE = "local_edit_file";
    public static final String LOCAL_EDIT_FILE_DESC = "对用户本地文本文件做精确文本替换。" +
            "old_text 必须在文件中精确出现预期次数（默认 1 次），0 次或次数不符将报错并不修改文件；" +
            "replace_all=true 时替换全部出现。保留原文件的换行风格（CRLF/LF）与 BOM，原子写入。" +
            "适合小范围修改；大规模重写请用 local_write_file。";

    public static final String LOCAL_LIST_FILES = "local_list_files";
    public static final String LOCAL_LIST_FILES_DESC = "列出用户本地目录内容或按 glob 模式匹配文件。" +
            "相对路径按项目根解析。pattern 为空时列出目录直接子项；非空时按 glob（如 src/**/*.ts）递归匹配。" +
            "结果超过条目上限时返回 truncated 标记。";

    public static final String LOCAL_SEARCH_FILES = "local_search_files";
    public static final String LOCAL_SEARCH_FILES_DESC = "在用户本地项目文件内容中按正则/文本搜索（类似 grep）。" +
            "相对路径按项目根解析。可用 filePattern 限定文件范围（glob）。" +
            "返回匹配行及行号，超过匹配数上限时返回 truncated 标记与实际扫描统计。";
}
