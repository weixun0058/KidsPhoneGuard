"""Targeted tests for the PC-side accessibility forensics watcher."""

from __future__ import annotations

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from pc_forensics_watch import (
    TARGET_SERVICE_FULL,
    TARGET_SERVICE_SHORT,
    classify_health,
    is_target_service_enabled,
    parse_enabled_services,
    read_recent_timeline_entries,
)


class ServiceParsingTests(unittest.TestCase):
    """Verify secure-setting parsing for the target accessibility service."""

    def test_parse_enabled_services_splits_and_trims_entries(self) -> None:
        """Split colon-separated entries and drop empty fragments."""

        raw_value = f"  {TARGET_SERVICE_SHORT}  ::other.package/.Service  "
        self.assertEqual(
            parse_enabled_services(raw_value),
            [TARGET_SERVICE_SHORT, "other.package/.Service"],
        )

    def test_is_target_service_enabled_accepts_short_name(self) -> None:
        """Treat the short flattened component name as enabled."""

        self.assertTrue(is_target_service_enabled(TARGET_SERVICE_SHORT))

    def test_is_target_service_enabled_accepts_full_name(self) -> None:
        """Treat the full flattened component name as enabled."""

        self.assertTrue(is_target_service_enabled(TARGET_SERVICE_FULL))

    def test_is_target_service_enabled_rejects_other_services(self) -> None:
        """Reject unrelated accessibility services."""

        self.assertFalse(is_target_service_enabled("other.package/.Service"))


class HealthClassificationTests(unittest.TestCase):
    """Verify the coarse health-state transitions used by the watcher."""

    def test_classify_health_returns_healthy_when_flag_and_service_match(self) -> None:
        """Report a healthy state when the global flag and service entry are present."""

        health_state, reasons = classify_health("device", "1", TARGET_SERVICE_SHORT)
        self.assertEqual(health_state, "healthy")
        self.assertEqual(reasons, ["healthy"])

    def test_classify_health_returns_degraded_when_global_flag_is_off(self) -> None:
        """Report degradation when the accessibility global switch is disabled."""

        health_state, reasons = classify_health("device", "0", TARGET_SERVICE_SHORT)
        self.assertEqual(health_state, "degraded")
        self.assertIn("accessibility_disabled", reasons)

    def test_classify_health_returns_degraded_when_service_is_missing(self) -> None:
        """Report degradation when the target service is missing from enabled services."""

        health_state, reasons = classify_health("device", "1", "")
        self.assertEqual(health_state, "degraded")
        self.assertIn("target_service_not_enabled", reasons)

    def test_classify_health_returns_device_offline_when_adb_state_changes(self) -> None:
        """Report a device-offline state when adb no longer sees an online device."""

        health_state, reasons = classify_health("offline", "1", TARGET_SERVICE_SHORT)
        self.assertEqual(health_state, "device_offline")
        self.assertEqual(reasons, ["device_offline"])


class TimelineContextTests(unittest.TestCase):
    """Verify the recent-timeline slice copied into incident bundles."""

    def test_read_recent_timeline_entries_keeps_only_entries_in_window(self) -> None:
        """Return only timeline rows whose timestamps fall inside the requested window."""

        with TemporaryDirectory() as temp_dir:
            root_dir = Path(temp_dir)
            timeline_path = root_dir / "timeline.jsonl"
            timeline_path.write_text(
                "\n".join(
                    [
                        '{"captured_at_ms": 1000, "health_state": "healthy"}',
                        '{"captured_at_ms": 1200000, "health_state": "degraded"}',
                        '{"captured_at_ms": 1800000, "health_state": "degraded"}',
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            recent_entries = read_recent_timeline_entries(
                root_dir=root_dir,
                window_minutes=10,
                current_time_ms=1800000,
            )

            self.assertEqual(len(recent_entries), 2)
            self.assertEqual(recent_entries[0]["captured_at_ms"], 1200000)
            self.assertEqual(recent_entries[1]["captured_at_ms"], 1800000)

    def test_read_recent_timeline_entries_returns_empty_when_window_is_disabled(self) -> None:
        """Return no entries when incident context copying is explicitly disabled."""

        with TemporaryDirectory() as temp_dir:
            root_dir = Path(temp_dir)
            (root_dir / "timeline.jsonl").write_text('{"captured_at_ms": 1000}\n', encoding="utf-8")

            recent_entries = read_recent_timeline_entries(
                root_dir=root_dir,
                window_minutes=0,
                current_time_ms=1000,
            )

            self.assertEqual(recent_entries, [])


if __name__ == "__main__":
    unittest.main()
