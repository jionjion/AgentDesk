/**
 * Pyodide Web Worker
 * 在隔离的 Worker 线程中运行 Python 代码
 */

/// <reference lib="webworker" />

import type { WorkerMessage, WorkerResponse, ExecuteResult } from '../types/sandbox'

let pyodide: any = null

/** 发送消息到主线程 */
function postMsg(msg: WorkerResponse) {
  self.postMessage(msg)
}

/** 注入到 Pyodide 的辅助 Python 代码（所有内部变量使用 __ 前缀防止用户代码访问） */
const SETUP_CODE = `
import sys
from io import StringIO, BytesIO
import re
from js import self as __js_self, Object as __js_Object
from pyodide.ffi import to_js as __to_js

# stdout/stderr 捕获
class __OutputCapture:
    def __init__(self, max_size=1024*1024, stream_callback=None):
        self.buffer = StringIO()
        self.max_size = max_size
        self.stream_callback = stream_callback

    def write(self, text):
        if self.buffer.tell() < self.max_size:
            self.buffer.write(text)
        # 实时流式回传
        if self.stream_callback and text:
            self.stream_callback(text)

    def flush(self):
        pass

    def getvalue(self):
        return self.buffer.getvalue()

    def reset(self):
        self.buffer = StringIO()

def __send_stdout(text):
    __js_self.postMessage(__to_js({"type": "stdout", "data": text}, dict_converter=__js_Object.fromEntries))

__stdout_capture = __OutputCapture(stream_callback=__send_stdout)
__stderr_capture = __OutputCapture()

# matplotlib 图表捕获
__figures = []

def __setup_matplotlib():
    try:
        import matplotlib
        matplotlib.use('agg')
        import matplotlib.pyplot as plt
        import base64

        def _capture_show(*args, **kwargs):
            for fig_num in plt.get_fignums():
                fig = plt.figure(fig_num)
                buf = BytesIO()
                fig.savefig(buf, format='png', dpi=100, bbox_inches='tight')
                buf.seek(0)
                __figures.append(base64.b64encode(buf.read()).decode())
            plt.close('all')

        plt.show = _capture_show
    except ImportError:
        pass

# 不支持操作的友好提示
__UNSUPPORTED_MODULES = {
    'requests': '沙箱环境不支持网络请求（requests）',
    'urllib3': '沙箱环境不支持网络请求（urllib3）',
    'httpx': '沙箱环境不支持网络请求（httpx）',
    'subprocess': '沙箱环境不支持调用系统命令（subprocess）',
    'socket': '沙箱环境不支持网络连接（socket）',
    'ctypes': '沙箱环境不支持底层 C 调用（ctypes）',
    'multiprocessing': '沙箱环境不支持多进程（multiprocessing）',
}

# 错误信息过滤
def __format_error(exc):
    import traceback
    tb_lines = traceback.format_exception(type(exc), exc, exc.__traceback__)
    filtered = [line for line in tb_lines
                if '/lib/python' not in line
                and 'pyodide/_base' not in line
                and 'pyodide/code' not in line]
    return ''.join(filtered) if filtered else ''.join(tb_lines)

# 用户代码的隔离全局命名空间（只暴露标准内置和安全模块）
__user_globals = {'__builtins__': __builtins__}

# 确保输出目录存在
import os as _os
_os.makedirs('/data/output', exist_ok=True)
del _os
`

/** Pyodide 包 CDN 地址 */
const PYODIDE_CDN = 'https://cdn.jsdelivr.net/pyodide/v0.26.4/full/'

/** 自动 import 处理的 Python 代码 */
const AUTO_IMPORT_CODE = `
# Pyodide CDN 用于加载有 WASM 特殊构建的包
_PYODIDE_CDN = '${PYODIDE_CDN}'

# Pyodide CDN 上的包（WASM 构建 + 配套纯 Python 包）
_CDN_PACKAGES = {
    'numpy': 'numpy-1.26.4-cp312-cp312-pyodide_2024_0_wasm32.whl',
    'pandas': 'pandas-2.2.0-cp312-cp312-pyodide_2024_0_wasm32.whl',
    'matplotlib': 'matplotlib-3.5.2-cp312-cp312-pyodide_2024_0_wasm32.whl',
    'pillow': 'pillow-10.2.0-cp312-cp312-pyodide_2024_0_wasm32.whl',
    'regex': 'regex-2024.4.16-cp312-cp312-pyodide_2024_0_wasm32.whl',
    'pyyaml': 'PyYAML-6.0.1-cp312-cp312-pyodide_2024_0_wasm32.whl',
    'lxml': 'lxml-5.2.1-cp312-cp312-pyodide_2024_0_wasm32.whl',
    'cffi': 'cffi-1.16.0-cp312-cp312-pyodide_2024_0_wasm32.whl',
    'markupsafe': 'MarkupSafe-2.1.5-cp312-cp312-pyodide_2024_0_wasm32.whl',
    'kiwisolver': 'kiwisolver-1.4.5-cp312-cp312-pyodide_2024_0_wasm32.whl',
    'pytz': 'pytz-2024.1-py2.py3-none-any.whl',
    'six': 'six-1.16.0-py2.py3-none-any.whl',
    'python-dateutil': 'python_dateutil-2.9.0.post0-py2.py3-none-any.whl',
    'cycler': 'cycler-0.12.1-py3-none-any.whl',
    'fonttools': 'fonttools-4.51.0-py3-none-any.whl',
    'pyparsing': 'pyparsing-3.1.2-py3-none-any.whl',
    'matplotlib-pyodide': 'matplotlib_pyodide-0.2.2-py3-none-any.whl',
    'mpmath': 'mpmath-1.3.0-py3-none-any.whl',
    'typing-extensions': 'typing_extensions-4.11.0-py3-none-any.whl',
    'pycparser': 'pycparser-2.22-py3-none-any.whl',
    'sympy': 'sympy-1.12-py3-none-any.whl',
    'jinja2': 'Jinja2-3.1.3-py3-none-any.whl',
}

# 依赖关系
_CDN_DEPS = {
    'pandas': ['numpy', 'python-dateutil', 'pytz'],
    'matplotlib': ['numpy', 'pillow', 'kiwisolver', 'cycler',
                   'fonttools', 'pyparsing', 'python-dateutil',
                   'pytz', 'matplotlib-pyodide'],
    'cffi': ['pycparser'],
    'sympy': ['mpmath'],
    'python-dateutil': ['six'],
}

# import 名 → 包名映射
_IMPORT_TO_PACKAGE = {
    'PIL': 'pillow',
    'bs4': 'beautifulsoup4',
    'yaml': 'pyyaml',
    'sklearn': 'scikit-learn',
    'dateutil': 'python-dateutil',
}

_installed_packages = set()

async def _install_cdn_pkg(name):
    """从 CDN 安装包及其依赖"""
    if name in _installed_packages:
        return
    _installed_packages.add(name)
    # 先装依赖
    for dep in _CDN_DEPS.get(name, []):
        await _install_cdn_pkg(dep)
    # 装自身
    import micropip
    url = _PYODIDE_CDN + _CDN_PACKAGES[name]
    await micropip.install(url, deps=False)

async def _auto_import(code):
    """扫描代码中的 import，自动加载未安装的包"""
    import re
    imports = re.findall(r'^(?:import|from)\\s+(\\w+)', code, re.MULTILINE)
    for pkg in imports:
        if pkg in __UNSUPPORTED_MODULES:
            continue
        try:
            __import__(pkg)
        except ImportError:
            actual_pkg = _IMPORT_TO_PACKAGE.get(pkg, pkg)
            try:
                if actual_pkg in _CDN_PACKAGES:
                    await _install_cdn_pkg(actual_pkg)
                else:
                    import micropip
                    await micropip.install(actual_pkg)
            except Exception:
                pass
`

/** 执行用户代码的 Python 包装 */
function buildExecuteCode(userCode: string): string {
  return `
import sys
__stdout_capture.reset()
__stderr_capture.reset()
__figures.clear()
sys.stdout = __stdout_capture
sys.stderr = __stderr_capture

_exec_result = None
_output_files = {}
try:
    await _auto_import(${JSON.stringify(userCode)})
    __setup_matplotlib()

    # 检查不支持的模块
    import re as _re
    _imports = _re.findall(r'^(?:import|from)\\s+(\\w+)', ${JSON.stringify(userCode)}, _re.MULTILINE)
    for _mod in _imports:
        if _mod in __UNSUPPORTED_MODULES:
            raise ImportError(__UNSUPPORTED_MODULES[_mod])

    _code_to_run = ${JSON.stringify(userCode)}
    _compiled = compile(_code_to_run, '<sandbox>', 'exec')
    exec(_compiled, __user_globals)

    # 捕获 result 变量
    if 'result' in __user_globals:
        _exec_result = __user_globals['result']

    # 收集输出文件（/data/output/ 目录）
    import os as _os
    _output_dir = '/data/output'
    if _os.path.isdir(_output_dir):
        for _fname in _os.listdir(_output_dir):
            _fpath = _os.path.join(_output_dir, _fname)
            if _os.path.isfile(_fpath):
                import base64 as _b64
                with open(_fpath, 'rb') as _f:
                    _output_files[_fname] = _b64.b64encode(_f.read()).decode()
except Exception as _e:
    sys.stderr.write(__format_error(_e))
finally:
    sys.stdout = sys.__stdout__
    sys.stderr = sys.__stderr__

{
    "success": __stderr_capture.getvalue() == "",
    "result": _exec_result,
    "stdout": __stdout_capture.getvalue(),
    "stderr": __stderr_capture.getvalue(),
    "figures": list(__figures),
    "outputFiles": _output_files if _output_files else None,
}
`
}

/** 初始化 Pyodide */
async function initPyodide(pyodideUrl: string) {
  postMsg({ type: 'status', status: 'loading' })

  try {
    // 动态加载 pyodide 核心（本地）
    const { loadPyodide } = await import(/* @vite-ignore */ `${pyodideUrl}/pyodide.mjs`)

    pyodide = await loadPyodide({
      indexURL: pyodideUrl
    })

    // micropip 和 packaging 从本地加载（已复制到 public/pyodide/）
    await pyodide.loadPackage(['micropip', 'packaging'])

    // 注入辅助代码
    await pyodide.runPythonAsync(SETUP_CODE)
    await pyodide.runPythonAsync(AUTO_IMPORT_CODE)

    postMsg({ type: 'status', status: 'ready' })
  } catch (error) {
    postMsg({ type: 'status', status: 'error' })
    console.error('Pyodide 初始化失败:', error)
  }
}

/** 执行 Python 代码 */
async function executeCode(id: string, code: string, files?: Record<string, ArrayBuffer>, globals?: Record<string, unknown>, _timeout?: number) {
  if (!pyodide) {
    postMsg({ type: 'error', id, error: '沙箱未初始化' })
    return
  }

  postMsg({ type: 'status', status: 'running' })
  const startTime = Date.now()

  try {
    // 写入文件到 MEMFS
    if (files) {
      for (const [name, data] of Object.entries(files)) {
        const path = `/data/${name}`
        ensureDir(path)
        pyodide.FS.writeFile(path, new Uint8Array(data))
      }
    }

    // 注入全局变量到用户命名空间
    if (globals) {
      for (const [key, value] of Object.entries(globals)) {
        pyodide.globals.get('__user_globals').set(key, pyodide.toPy(value))
      }
    }

    // 执行代码
    const wrappedCode = buildExecuteCode(code)
    const rawResult = await pyodide.runPythonAsync(wrappedCode)
    const result = rawResult.toJs({ dict_converter: Object.fromEntries })

    const executeResult: ExecuteResult = {
      success: result.success,
      result: result.result,
      stdout: result.stdout || '',
      stderr: result.stderr || '',
      figures: result.figures?.length > 0 ? Array.from(result.figures) : undefined,
      outputFiles: result.outputFiles ? Object.fromEntries(Object.entries(result.outputFiles)) : undefined,
      duration: Date.now() - startTime
    }

    postMsg({ type: 'result', id, result: executeResult })
  } catch (error: any) {
    const executeResult: ExecuteResult = {
      success: false,
      stdout: '',
      stderr: error.message || String(error),
      duration: Date.now() - startTime
    }
    postMsg({ type: 'result', id, result: executeResult })
  } finally {
    postMsg({ type: 'status', status: 'ready' })
  }
}

/** 通过 Pyodide FS API 确保目录存在（避免路径注入） */
function ensureDir(filePath: string) {
  if (!pyodide) return
  const parts = filePath.substring(1).split('/') // 去掉开头的 /
  parts.pop() // 去掉文件名
  let current = ''
  for (const part of parts) {
    current += '/' + part
    try {
      pyodide.FS.mkdir(current)
    } catch {
      // 目录已存在，忽略
    }
  }
}

/** 写入文件到 MEMFS */
function writeFileToMemfs(path: string, data: ArrayBuffer) {
  if (!pyodide) return
  ensureDir(path)
  pyodide.FS.writeFile(path, new Uint8Array(data))
}

/** 消息处理 */
self.onmessage = async (event: MessageEvent<WorkerMessage>) => {
  const msg = event.data

  switch (msg.type) {
    case 'init':
      await initPyodide(msg.pyodideUrl)
      break
    case 'execute':
      await executeCode(msg.id, msg.code, msg.files, msg.globals, msg.timeout)
      break
    case 'writeFile':
      writeFileToMemfs(msg.path, msg.data)
      break
  }
}
