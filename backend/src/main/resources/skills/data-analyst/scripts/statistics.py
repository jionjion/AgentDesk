"""
统计计算脚本 - 对数值数据进行基本统计分析
用法: python3 statistics.py <csv_file> [column_name]
"""
import sys
import csv
import json
import math
from pathlib import Path


def compute_stats(values: list[float]) -> dict:
    """计算基本统计量"""
    n = len(values)
    if n == 0:
        return {"error": "无有效数据"}
    sorted_vals = sorted(values)
    mean = sum(values) / n
    median = sorted_vals[n // 2] if n % 2 == 1 else (sorted_vals[n // 2 - 1] + sorted_vals[n // 2]) / 2
    variance = sum((x - mean) ** 2 for x in values) / n
    std_dev = math.sqrt(variance)
    return {
        "count": n,
        "mean": round(mean, 4),
        "median": round(median, 4),
        "std_dev": round(std_dev, 4),
        "min": round(min(values), 4),
        "max": round(max(values), 4),
        "q1": round(sorted_vals[n // 4], 4),
        "q3": round(sorted_vals[3 * n // 4], 4),
    }


def analyze_csv_column(file_path: str, column: str = None) -> dict:
    """分析 CSV 文件中指定列的数值数据"""
    result = {"file": file_path, "columns": {}}
    with open(file_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
    if not rows:
        return {"error": "文件为空"}
    target_cols = [column] if column else list(rows[0].keys())
    for col in target_cols:
        values = []
        for row in rows:
            try:
                values.append(float(row.get(col, "")))
            except (ValueError, TypeError):
                continue
        if values:
            result["columns"][col] = compute_stats(values)
    return result


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "请提供 CSV 文件路径"}, ensure_ascii=False))
        sys.exit(1)
    file_path = sys.argv[1]
    column = sys.argv[2] if len(sys.argv) > 2 else None
    if not Path(file_path).exists():
        print(json.dumps({"error": f"文件不存在: {file_path}"}, ensure_ascii=False))
        sys.exit(1)
    result = analyze_csv_column(file_path, column)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
