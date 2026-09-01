#!/usr/bin/env python3
"""
Unit tests for analyze_traces.py.
"""

import unittest
import tempfile
import os
import json
from analyze_traces import (
    nearest_rank_percentile,
    calculate_metric_stats,
    check_sanitization,
    TraceLogParser,
    process_benchmark_data,
    generate_markdown_summary
)

class TestAnalyzeTraces(unittest.TestCase):

    def test_nearest_rank_percentile_ten_items(self):
        values = [10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0]
        # p50: ceil(50/100 * 10) = 5 -> index 4 -> 50.0
        self.assertEqual(nearest_rank_percentile(values, 50), 50.0)
        # p95: ceil(95/100 * 10) = 10 -> index 9 -> 100.0
        self.assertEqual(nearest_rank_percentile(values, 95), 100.0)
        # p25: ceil(25/100 * 10) = 3 -> index 2 -> 30.0
        self.assertEqual(nearest_rank_percentile(values, 25), 30.0)

    def test_sanitizer_catches_sensitive_tokens(self):
        self.assertIsNotNone(check_sanitization("Authorization: Bearer ya29.a0AfH6SMA..."))
        self.assertIsNotNone(check_sanitization("User email: personal.john@secret.org"))
        self.assertIsNotNone(check_sanitization("query: access_token=xyz123456"))
        self.assertIsNotNone(check_sanitization("code=4/0AbCdEfGh123"))

        # Clean line
        self.assertIsNone(check_sanitization("MailOpenTrace: mail=1a2b3c4d section=EmailOpen.Total durationMs=120"))
        # Neutral fixture email allowed
        self.assertIsNone(check_sanitization("From: user@example.com"))

    def test_parser_and_warmup_exclusion(self):
        sample_log_lines = []
        # Generate 3 warmups + 10 measured = 13 sessions
        for i in range(1, 14):
            mail_hex = f"a{i:02x}"
            t_start = 1000 * i
            dur = 200 + i * 10
            sample_log_lines.append(f"D MailOpenTrace: [PERF_SESSION] START sessionId={i} mail={mail_hex} t={t_start} section=EmailOpen.Total")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] section=EmailOpen.Resolve mail={mail_hex} durationMs={20 + i}")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] section=EmailOpen.BodyFetch mail={mail_hex} durationMs={100 + i}")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] section=EmailOpen.NetworkFull mail={mail_hex} durationMs={90 + i}")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] section=EmailOpen.HtmlBuild mail={mail_hex} durationMs={15 + i}")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] section=EmailOpen.WebViewVisual mail={mail_hex} durationMs={50 + i}")
            sample_log_lines.append(f"D MailOpenTrace: [PERF_SESSION] READY sessionId={i} mail={mail_hex} durationMs={dur} outcome=COMPLETED")

        parser = TraceLogParser()
        sessions = parser.parse_lines(sample_log_lines)
        self.assertEqual(len(sessions), 13)

        runs, summary, network_counts = process_benchmark_data(sessions, "testScenario")
        self.assertEqual(len(runs), 10)
        self.assertEqual(summary["sampleCount"], 10)

        # First measured run should be session 4 (warmups 1, 2, 3 excluded)
        self.assertEqual(runs[0]["mailKey"], "a04")
        self.assertEqual(runs[0]["totalMs"], 240.0)
        self.assertEqual(runs[0]["networkFullCount"], 1)

        # p50 of measured runs: durations are 240, 250, 260, 270, 280, 290, 300, 310, 320, 330
        # sorted index 4 (5th item) is 280.0
        self.assertEqual(summary["EmailOpen.Total"]["p50"], 280.0)
        # p95 (10th item) is 330.0
        self.assertEqual(summary["EmailOpen.Total"]["p95"], 330.0)

        md = generate_markdown_summary(summary)
        self.assertIn("EmailOpen.Total", md)
        self.assertIn("p95", md)

    def test_parser_fails_on_insufficient_samples(self):
        sample_log_lines = [
            "D MailOpenTrace: [PERF_SESSION] START sessionId=1 mail=a01 t=100 section=EmailOpen.Total",
            "D MailOpenTrace: [PERF_SESSION] READY sessionId=1 mail=a01 durationMs=150 outcome=COMPLETED"
        ]
        parser = TraceLogParser()
        sessions = parser.parse_lines(sample_log_lines)
        with self.assertRaises(ValueError):
            process_benchmark_data(sessions, "testScenario")

if __name__ == "__main__":
    unittest.main()
