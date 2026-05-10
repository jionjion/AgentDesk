"""
文件分析辅助脚本 - 提供 CSV 统计分析能力
用法: python3 analyze.py <file_path>
"""
import sys
import csv
import json
from pathlib import Path


def analyze_csv(file_path: str) -> dict:
    """分析 CSV 文件，返回结构化信息"""
    result = {"file": file_path, "columns": [], "row_count": 0, "issues": []}
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            reader = csv.reader(f)
            headers = next(reader, None)
            if not headers:
                result["issues"].append("文件为空或没有表头")
                return result
            result["columns"] = headers
            rows = list(reader)
            result["row_count"] = len(rows)
            # 检测空值
            for col_idx, col_name in enumerate(headers):
                empty_count = sum(1 for row in rows if col_idx < len(row) and not row[col_idx].strip())
                if empty_count > 0:
                    result["issues"].append(f"列 '{col_name}' 有 {empty_count} 个空值")
    except Exception as e:
        result["issues"].append(f"读取失败: {str(e)}")
    return result


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "请提供文件路径"}, ensure_ascii=False))
        sys.exit(1)
    file_path = sys.argv[1]
    path = Path(file_path)
    if not path.exists():
        print(json.dumps({"error": f"文件不存在: {file_path}"}, ensure_ascii=False))
        sys.exit(1)
    if path.suffix.lower() == ".csv":
        result = analyze_csv(file_path)
    else:
        result = {"file": file_path, "type": path.suffix, "size": path.stat().st_size}
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
