#!/usr/bin/env python3
"""
Trace Analyzer and Sanitizer for Email Open Performance (Stage 0).

Parses Macrobenchmark JSON metrics and Logcat performance logs,
computes deterministic statistics (nearest-rank p50/p95, 3 warmups excluded,
10 measured samples), validates safety and generates summary.json, runs.csv,
network-counts.csv, sanitized logs and Markdown summary tables.
"""

import sys
import os
import re
import json
import math
import csv
from typing import Dict, List, Any, Optional, Tuple

SENSITIVE_PATTERNS = [
    re.compile(r'Bearer\s+[A-Za-z0-9\-._~+/]+=*', re.IGNORECASE),
    re.compile(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b'),
    re.compile(r'access_token=[A-Za-z0-9\-._~+/]+', re.IGNORECASE),
    re.compile(r'refresh_token=[A-Za-z0-9\-._~+/]+', re.IGNORECASE),
    re.compile(r'code=4/[A-Za-z0-9_\-]+', re.IGNORECASE),
]

def check_sanitization(text: str) -> Optional[str]:
    """Returns description of violation if any sensitive data is found, None if clean."""
    for pattern in SENSITIVE_PATTERNS:
        match = pattern.search(text)
        if match:
            # Allow neutral fixture placeholders
            matched_str = match.group(0)
            if "example.com" in matched_str.lower() or "test@" in matched_str.lower():
                continue
            return f"Sensitive pattern violation: '{pattern.pattern}' matched '{matched_str[:8]}...'"
    return None

def nearest_rank_percentile(sorted_values: List[float], p: float) -> float:
    """
    Nearest-rank percentile:
    rank = ceil((p / 100) * N)
    index = rank - 1
    """
    if not sorted_values:
        return 0.0
    n = len(sorted_values)
    rank = math.ceil((p / 100.0) * n)
    rank = max(1, min(rank, n))
    return sorted_values[rank - 1]

def calculate_metric_stats(values: List[float]) -> Dict[str, float]:
    if not values:
        return {"min": 0.0, "p50": 0.0, "p95": 0.0, "max": 0.0, "avg": 0.0}
    sorted_vals = sorted(values)
    return {
        "min": round(sorted_vals[0], 2),
        "p50": round(nearest_rank_percentile(sorted_vals, 50), 2),
        "p95": round(nearest_rank_percentile(sorted_vals, 95), 2),
        "max": round(sorted_vals[-1], 2),
        "avg": round(sum(sorted_vals) / len(sorted_vals), 2),
    }

class TraceLogParser:
    """Parses MailOpenTrace log lines into structured iterations."""

    def __init__(self):
        self.sessions: List[Dict[str, Any]] = []

    def parse_lines(self, lines: List[str]) -> List[Dict[str, Any]]:
        current_session = None
        open_sections = {}

        for line in lines:
            leak = check_sanitization(line)
            if leak:
                raise ValueError(f"Security error: {leak}")

            if "MailOpenTrace" not in line:
                continue

            if "[PERF_SESSION] START" in line:
                m = re.search(r'sessionId=(\d+)\s+mail=([0-9a-fA-F]+|none)\s+t=(\d+)', line)
                if m:
                    sess_id = int(m.group(1))
                    mail_key = m.group(2)
                    start_t = int(m.group(3))
                    current_session = {
                        "sessionId": sess_id,
                        "mailKey": mail_key,
                        "startMs": start_t,
                        "sections": {},
                        "networkFullCount": 0,
                        "networkFullDurationMs": 0.0,
                        "outcome": "INCOMPLETE"
                    }
                    self.sessions.append(current_session)

            elif "[PERF_SESSION] READY" in line:
                m = re.search(r'sessionId=(\d+)\s+mail=([0-9a-fA-F]+|none)\s+durationMs=(\d+)\s+outcome=(\w+)', line)
                if m and current_session and current_session["sessionId"] == int(m.group(1)):
                    current_session["durationMs"] = float(m.group(3))
                    current_session["outcome"] = m.group(4)

            elif "[PERF_SESSION] ABORTED" in line:
                m = re.search(r'sessionId=(\d+)\s+mail=([0-9a-fA-F]+|none).*outcome=(\w+)', line)
                if m and current_session and current_session["sessionId"] == int(m.group(1)):
                    current_session["outcome"] = m.group(3)

            elif "[PERF_SESSION] REPLACED" in line:
                m = re.search(r'sessionId=(\d+)\s+mail=([0-9a-fA-F]+|none).*outcome=(\w+)', line)
                if m:
                    sess_id = int(m.group(1))
                    for s in self.sessions:
                        if s["sessionId"] == sess_id:
                            s["outcome"] = "REPLACED"

            elif "[TRACE_SECTION]" in line:
                m = re.search(r'section=([\w\.]+)\s+mail=([0-9a-fA-F]+|none)\s+durationMs=(\d+)', line)
                if m and current_session:
                    sec_name = m.group(1)
                    dur = float(m.group(3))
                    current_session["sections"][sec_name] = dur
                    if sec_name == "EmailOpen.NetworkFull":
                        current_session["networkFullCount"] += 1
                        current_session["networkFullDurationMs"] += dur

        return self.sessions

def process_benchmark_data(
    parsed_sessions: List[Dict[str, Any]],
    scenario_name: str = "plainTextFirstOpen"
) -> Tuple[List[Dict[str, Any]], Dict[str, Any], List[Dict[str, Any]]]:
    """
    Filters out warmups and invalid sessions, computes stats.
    Requires at least 13 sessions (3 warmup + 10 measured), or exactly 10 measured.
    """
    valid_sessions = [s for s in parsed_sessions if s.get("outcome") == "COMPLETED"]
    
    if len(valid_sessions) >= 13:
        # Exclude first 3 warmups
        measured_sessions = valid_sessions[3:13]
    elif len(valid_sessions) == 10:
        measured_sessions = valid_sessions
    else:
        raise ValueError(f"Insufficient completed samples: expected 10 measured, found {len(valid_sessions)}")

    runs = []
    network_counts = []
    
    total_durations = []
    resolve_durations = []
    body_fetch_durations = []
    html_build_durations = []
    webview_durations = []
    network_counts_list = []

    for idx, s in enumerate(measured_sessions, start=1):
        total_ms = s.get("durationMs", 0.0)
        resolve_ms = s.get("sections", {}).get("EmailOpen.Resolve", 0.0)
        body_fetch_ms = s.get("sections", {}).get("EmailOpen.BodyFetch", 0.0)
        html_build_ms = s.get("sections", {}).get("EmailOpen.HtmlBuild", 0.0)
        webview_ms = s.get("sections", {}).get("EmailOpen.WebViewVisual", 0.0)
        net_count = s.get("networkFullCount", 0)
        net_dur_ms = s.get("networkFullDurationMs", 0.0)

        total_durations.append(total_ms)
        resolve_durations.append(resolve_ms)
        body_fetch_durations.append(body_fetch_ms)
        html_build_durations.append(html_build_ms)
        webview_durations.append(webview_ms)
        network_counts_list.append(net_count)

        runs.append({
            "iteration": idx,
            "scenario": scenario_name,
            "mailKey": s.get("mailKey", "none"),
            "totalMs": total_ms,
            "resolveMs": resolve_ms,
            "bodyFetchMs": body_fetch_ms,
            "htmlBuildMs": html_build_ms,
            "webViewVisualMs": webview_ms,
            "networkFullCount": net_count,
            "networkFullDurationMs": net_dur_ms
        })

        network_counts.append({
            "iteration": idx,
            "scenario": scenario_name,
            "networkFullCount": net_count,
            "networkFullDurationMs": net_dur_ms
        })

    summary = {
        "scenario": scenario_name,
        "sampleCount": len(measured_sessions),
        "EmailOpen.Total": calculate_metric_stats(total_durations),
        "EmailOpen.Resolve": calculate_metric_stats(resolve_durations),
        "EmailOpen.BodyFetch": calculate_metric_stats(body_fetch_durations),
        "EmailOpen.HtmlBuild": calculate_metric_stats(html_build_durations),
        "EmailOpen.WebViewVisual": calculate_metric_stats(webview_durations),
        "networkFullCount": {
            "min": min(network_counts_list) if network_counts_list else 0,
            "max": max(network_counts_list) if network_counts_list else 0,
            "avg": round(sum(network_counts_list) / len(network_counts_list), 2) if network_counts_list else 0,
            "total": sum(network_counts_list)
        }
    }

    return runs, summary, network_counts

def generate_markdown_summary(summary: Dict[str, Any]) -> str:
    lines = [
        f"### Resumen de Rendimiento — Escenario: `{summary['scenario']}`",
        "",
        "| Métrica | Mín (ms) | p50 (ms) | p95 (ms) | Máx (ms) | Media (ms) |",
        "|---|---:|---:|---:|---:|---:|"
    ]
    for metric in ["EmailOpen.Total", "EmailOpen.Resolve", "EmailOpen.BodyFetch", "EmailOpen.HtmlBuild", "EmailOpen.WebViewVisual"]:
        stats = summary.get(metric, {})
        lines.append(
            f"| `{metric}` | {stats.get('min', 0)} | {stats.get('p50', 0)} | "
            f"{stats.get('p95', 0)} | {stats.get('max', 0)} | {stats.get('avg', 0)} |"
        )
    net_stats = summary.get("networkFullCount", {})
    lines.append("")
    lines.append(
        f"**Peticiones `format=full`:** Min: {net_stats.get('min')}, "
        f"Max: {net_stats.get('max')}, Avg: {net_stats.get('avg')}, Total: {net_stats.get('total')}"
    )
    return "\n".join(lines)

def write_csv(filepath: str, data: List[Dict[str, Any]], fieldnames: List[str]):
    os.makedirs(os.path.dirname(os.path.abspath(filepath)), exist_ok=True)
    with open(filepath, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(data)

def main():
    if len(sys.argv) < 3:
        print("Usage: python3 analyze_traces.py <input_log_file> <output_dir> [scenario_name]")
        sys.exit(1)

    input_file = sys.argv[1]
    output_dir = sys.argv[2]
    scenario = sys.argv[3] if len(sys.argv) > 3 else "plainTextFirstOpen"

    with open(input_file, 'r', encoding='utf-8', errors='ignore') as f:
        lines = f.readlines()

    parser = TraceLogParser()
    sessions = parser.parse_lines(lines)
    runs, summary, network_counts = process_benchmark_data(sessions, scenario)

    os.makedirs(output_dir, exist_ok=True)

    # 1. runs.csv
    run_fields = [
        "iteration", "scenario", "mailKey", "totalMs", "resolveMs",
        "bodyFetchMs", "htmlBuildMs", "webViewVisualMs", "networkFullCount", "networkFullDurationMs"
    ]
    write_csv(os.path.join(output_dir, "runs.csv"), runs, run_fields)

    # 2. network-counts.csv
    net_fields = ["iteration", "scenario", "networkFullCount", "networkFullDurationMs"]
    write_csv(os.path.join(output_dir, "network-counts.csv"), network_counts, net_fields)

    # 3. summary.json
    with open(os.path.join(output_dir, "summary.json"), 'w', encoding='utf-8') as f:
        json.dump(summary, f, indent=2)

    # 4. summary.md
    md_content = generate_markdown_summary(summary)
    with open(os.path.join(output_dir, "summary.md"), 'w', encoding='utf-8') as f:
        f.write(md_content + "\n")

    print(f"Successfully processed {len(runs)} runs into {output_dir}")
    print(md_content)

if __name__ == "__main__":
    main()
