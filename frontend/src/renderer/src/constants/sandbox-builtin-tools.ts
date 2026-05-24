import type { SandboxTool } from '@/types/sandbox-tools'

/**
 * 沙箱内置工具定义
 * 每个工具包含 Python 实现代码，会在 Worker 初始化时注入到 tools.* 命名空间
 */

const now = Date.now()

export const BUILTIN_TOOLS: SandboxTool[] = [
  {
    id: 'list_files',
    name: 'list_files',
    description: '列出 /data/ 目录下的文件',
    signature: 'list_files(dir: str = "/data") -> list[str]',
    code: `
def list_files(dir: str = "/data") -> list:
    """列出指定目录下的文件"""
    import os
    if not os.path.isdir(dir):
        return []
    return sorted(os.listdir(dir))
`,
    dependencies: [],
    builtin: true,
    enabled: true,
    version: '1.0.0',
    createdAt: now,
    updatedAt: now
  },
  {
    id: 'file_info',
    name: 'file_info',
    description: '获取文件大小、类型等信息',
    signature: 'file_info(path: str) -> dict',
    code: `
def file_info(path: str) -> dict:
    """获取文件信息：大小、扩展名"""
    import os
    if not os.path.isfile(path):
        raise FileNotFoundError(f"文件不存在: {path}")
    stat = os.stat(path)
    ext = os.path.splitext(path)[1].lower()
    return {
        "path": path,
        "name": os.path.basename(path),
        "ext": ext,
        "size": stat.st_size,
        "size_human": f"{stat.st_size / 1024:.1f} KB" if stat.st_size < 1024*1024 else f"{stat.st_size / 1024/1024:.1f} MB",
    }
`,
    dependencies: [],
    builtin: true,
    enabled: true,
    version: '1.0.0',
    createdAt: now,
    updatedAt: now
  },
  {
    id: 'read_text',
    name: 'read_text',
    description: '读取文本文件内容',
    signature: 'read_text(path: str, encoding: str = "utf-8") -> str',
    code: `
def read_text(path: str, encoding: str = "utf-8") -> str:
    """读取文本文件，返回字符串内容"""
    with open(path, 'r', encoding=encoding) as f:
        return f.read()
`,
    dependencies: [],
    builtin: true,
    enabled: true,
    version: '1.0.0',
    createdAt: now,
    updatedAt: now
  },
  {
    id: 'to_dataframe',
    name: 'to_dataframe',
    description: '智能读取文件为 DataFrame（自动识别 csv/xlsx/json/parquet/tsv）',
    signature: 'to_dataframe(path: str, **kwargs) -> DataFrame',
    code: `
def to_dataframe(path: str, **kwargs):
    """智能读取文件为 pandas DataFrame，根据扩展名自动选择读取方式"""
    import pandas as pd
    import os
    ext = os.path.splitext(path)[1].lower()
    readers = {
        '.csv': pd.read_csv,
        '.xlsx': pd.read_excel,
        '.xls': pd.read_excel,
        '.json': pd.read_json,
        '.parquet': pd.read_parquet,
        '.tsv': lambda p, **kw: pd.read_csv(p, sep='\\t', **kw),
    }
    reader = readers.get(ext)
    if not reader:
        raise ValueError(f'不支持的文件格式: {ext}，支持: {", ".join(readers.keys())}')
    return reader(path, **kwargs)
`,
    dependencies: ['pandas', 'xlrd', 'openpyxl'],
    builtin: true,
    enabled: true,
    version: '1.0.0',
    createdAt: now,
    updatedAt: now
  },
  {
    id: 'read_pdf',
    name: 'read_pdf',
    description: '读取 PDF 文件文本内容，返回每页文本列表',
    signature: 'read_pdf(path: str) -> list[str]',
    code: `
def read_pdf(path: str) -> list:
    """读取 PDF 文件，返回每页文本内容列表"""
    try:
        from pypdf import PdfReader
    except ImportError:
        raise RuntimeError(
            "pypdf 模块未安装。可能原因：沙箱初始化时网络不通导致依赖安装失败。"
            "请尝试重启沙箱内核，或在代码中手动执行: await micropip.install('pypdf')"
        )
    reader = PdfReader(path)
    return [page.extract_text() or '' for page in reader.pages]
`,
    dependencies: ['pypdf'],
    builtin: true,
    enabled: true,
    version: '1.0.0',
    createdAt: now,
    updatedAt: now
  },
  {
    id: 'save_file',
    name: 'save_file',
    description: '将数据保存到 /data/output/ 目录',
    signature: 'save_file(data, filename: str, encoding: str = "utf-8") -> str',
    code: `
def save_file(data, filename: str, encoding: str = "utf-8") -> str:
    """将数据保存到输出目录，返回保存路径。支持 str/bytes/DataFrame"""
    import os
    output_dir = '/data/output'
    os.makedirs(output_dir, exist_ok=True)
    path = os.path.join(output_dir, filename)
    if hasattr(data, 'to_csv'):
        # pandas DataFrame
        ext = os.path.splitext(filename)[1].lower()
        if ext == '.xlsx':
            data.to_excel(path, index=False)
        elif ext == '.json':
            data.to_json(path, orient='records', force_ascii=False, indent=2)
        else:
            data.to_csv(path, index=False, encoding=encoding)
    elif isinstance(data, bytes):
        with open(path, 'wb') as f:
            f.write(data)
    else:
        with open(path, 'w', encoding=encoding) as f:
            f.write(str(data))
    return path
`,
    dependencies: ['openpyxl'],
    builtin: true,
    enabled: true,
    version: '1.0.0',
    createdAt: now,
    updatedAt: now
  },
  {
    id: 'display',
    name: 'display',
    description: '格式化展示对象（DataFrame 显示为表格，其他打印）',
    signature: 'display(obj, title: str = "") -> None',
    code: `
def display(obj, title: str = "") -> None:
    """格式化展示对象"""
    if title:
        print(f"=== {title} ===")
    if hasattr(obj, 'to_markdown'):
        print(obj.to_markdown(index=False))
    elif hasattr(obj, 'to_string'):
        print(obj.to_string())
    else:
        print(obj)
`,
    dependencies: [],
    builtin: true,
    enabled: true,
    version: '1.0.0',
    createdAt: now,
    updatedAt: now
  }
]
