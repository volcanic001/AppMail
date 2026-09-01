#!/usr/bin/env python3
"""
Unit tests for analyze_traces.py.
Synthetic fixtures used strictly for testing the parser and mathematical functions.
"""

import unittest
from analyze_traces import (
    nearest_rank_percentile,
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

        # Clean line with valid 16-hex mailKey
        self.assertIsNone(check_sanitization("MailOpenTrace: mail=1a2b3c4d5e6f7a8b section=EmailOpen.Total durationMs=120"))
        # Neutral fixture email allowed in test harness
        self.assertIsNone(check_sanitization("From: user@example.com"))

    def test_parser_and_warmup_exclusion_with_valid_capture_id(self):
        sample_log_lines = []
        cap_id = "20260831T220000Z"
        # Generate 3 warmups + 10 measured = 13 sessions
        for i in range(1, 14):
            mail_hex = f"1a2b3c4d5e6f7a{i:02x}"
            dur = 200 + i * 10
            sample_log_lines.append(f"D MailOpenTrace: [PERF_SESSION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.Total event=START")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.Resolve durationMs={20 + i}")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.BodyFetch durationMs={100 + i}")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.NetworkFull durationMs={90 + i}")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.HtmlBuild durationMs={15 + i}")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.WebViewVisual durationMs={50 + i}")
            sample_log_lines.append(f"D MailOpenTrace: [PERF_SESSION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.Total durationMs={dur} outcome=COMPLETED")

        parser = TraceLogParser(expected_capture_id=cap_id)
        sessions = parser.parse_lines(sample_log_lines)
        self.assertEqual(len(sessions), 13)

        runs, summary, network_counts = process_benchmark_data(sessions, "testScenario")
        self.assertEqual(len(runs), 10)
        self.assertEqual(summary["sampleCount"], 10)

        # First measured run should be session 4 (warmups 1, 2, 3 excluded)
        self.assertEqual(runs[0]["mailKey"], "1a2b3c4d5e6f7a04")
        self.assertEqual(runs[0]["totalMs"], 240.0)
        self.assertEqual(runs[0]["networkFullCount"], 1)

        # p50 of measured runs: durations are 240, 250, 260, 270, 280, 290, 300, 310, 320, 330
        # sorted index 4 (5th item) is 280.0
        self.assertEqual(summary["EmailOpen.Total"]["p50"], 280.0)
        self.assertEqual(summary["EmailOpen.Total"]["p95"], 330.0)

        md = generate_markdown_summary(summary)
        self.assertIn("EmailOpen.Total", md)
        self.assertIn("PostHttpToLegible", md)

    def test_parser_fails_on_mismatched_capture_id(self):
        lines = [
            "D MailOpenTrace: [PERF_SESSION] captureId=WRONG_ID sessionId=1 mail=1a2b3c4d5e6f7a01 section=EmailOpen.Total event=START"
        ]
        parser = TraceLogParser(expected_capture_id="EXPECTED_ID")
        with self.assertRaises(ValueError) as ctx:
            parser.parse_lines(lines)
        self.assertIn("Mismatched captureId", str(ctx.exception))

    def test_parser_fails_on_aborted_measured_iteration(self):
        sample_log_lines = []
        cap_id = "20260831T220000Z"
        for i in range(1, 14):
            mail_hex = f"1a2b3c4d5e6f7a{i:02x}"
            sample_log_lines.append(f"D MailOpenTrace: [PERF_SESSION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.Total event=START")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.Resolve durationMs=20")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.HtmlBuild durationMs=15")
            sample_log_lines.append(f"D MailOpenTrace: [TRACE_SECTION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.WebViewVisual durationMs=50")
            if i == 5:
                # Iteration 5 (measured sample 2) aborted
                sample_log_lines.append(f"D MailOpenTrace: [PERF_SESSION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.Total reason=screen_disposed durationMs=100 outcome=ABORTED")
            else:
                sample_log_lines.append(f"D MailOpenTrace: [PERF_SESSION] captureId={cap_id} sessionId={i} mail={mail_hex} section=EmailOpen.Total durationMs=200 outcome=COMPLETED")

        parser = TraceLogParser(expected_capture_id=cap_id)
        sessions = parser.parse_lines(sample_log_lines)
        with self.assertRaises(ValueError) as ctx:
            process_benchmark_data(sessions, "testScenario")
        self.assertIn("was aborted", str(ctx.exception))

if __name__ == "__main__":
    unittest.main()
