"""
代码执行辅助脚本 - 安全执行用户提供的 Python 代码片段
用法: python3 run_code.py <code_file>
"""
import sys
import json
import subprocess
from pathlib import Path


def run_python_code(code_file: str) -> dict:
    """在子进程中执行 Python 代码，捕获输出"""
    result = {"success": False, "stdout": "", "stderr": "", "returncode": -1}
    try:
        proc = subprocess.run(
            [sys.executable, code_file],
            capture_output=True,
            text=True,
            timeout=30
        )
        result["success"] = proc.returncode == 0
        result["stdout"] = proc.stdout[:4096]
        result["stderr"] = proc.stderr[:2048]
        result["returncode"] = proc.returncode
    except subprocess.TimeoutExpired:
        result["stderr"] = "执行超时 (30秒)"
    except Exception as e:
        result["stderr"] = str(e)
    return result


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "请提供代码文件路径"}, ensure_ascii=False))
        sys.exit(1)
    code_file = sys.argv[1]
    if not Path(code_file).exists():
        print(json.dumps({"error": f"文件不存在: {code_file}"}, ensure_ascii=False))
        sys.exit(1)
    result = run_python_code(code_file)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
