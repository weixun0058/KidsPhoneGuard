#!/usr/bin/env python3
"""PC-side adb watcher that captures evidence when accessibility state degrades."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path

TARGET_PACKAGE = "com.kidsphoneguard"
OBSERVER_PACKAGE = "com.kidsphoneguard.observer"
TARGET_SERVICE_SHORT = "com.kidsphoneguard/.service.GuardAccessibilityService"
TARGET_SERVICE_FULL = "com.kidsphoneguard/com.kidsphoneguard.service.GuardAccessibilityService"


@dataclass(frozen=True)
class Snapshot:
    """Represent one PC-side poll result for the target device."""

    captured_at: str
    captured_at_ms: int
    device_serial: str
    device_state: str
    accessibility_enabled_raw: str
    enabled_services_raw: str
    target_service_enabled: bool
    target_process_running: bool
    observer_process_running: bool
    health_state: str
    reasons: list[str]


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments for the watcher process."""

    parser = argparse.ArgumentParser(
        description="Watch KidsPhoneGuard accessibility state and auto-export forensics when it degrades."
    )
    parser.add_argument(
        "--serial",
        help="Specific adb device serial to monitor. If omitted, the first online device is used.",
    )
    parser.add_argument(
        "--poll-seconds",
        type=int,
        default=30,
        help="Polling interval in seconds. Default: 30.",
    )
    parser.add_argument(
        "--output-dir",
        default="forensics/pc-watch",
        help="Root directory for timeline and incident bundles. Default: forensics/pc-watch.",
    )
    parser.add_argument(
        "--clear-logcat-on-start",
        action="store_true",
        help="Clear logcat buffers once before monitoring starts.",
    )
    parser.add_argument(
        "--no-capture-on-start",
        action="store_true",
        help="Skip the initial baseline evidence capture.",
    )
    parser.add_argument(
        "--bugreport-on-incident",
        action="store_true",
        help="Run adb bugreport for each incident capture. This is slower and produces large files.",
    )
    parser.add_argument(
        "--max-polls",
        type=int,
        default=0,
        help="Stop after N polls. Default: 0, which means run forever.",
    )
    parser.add_argument(
        "--incident-context-minutes",
        type=int,
        default=30,
        help="Copy the recent timeline window into each incident directory. Default: 30 minutes.",
    )
    return parser.parse_args()


def now_text() -> str:
    """Return the current local timestamp in a filename-safe format."""

    return datetime.now().strftime("%Y-%m-%dT%H-%M-%S")


def now_iso_text() -> str:
    """Return the current local timestamp in ISO-like text for logs and JSON."""

    return datetime.now().isoformat(timespec="seconds")


def ensure_output_dir(root_dir: Path) -> Path:
    """Create the root output directory and its incident subdirectory."""

    root_dir.mkdir(parents=True, exist_ok=True)
    (root_dir / "incidents").mkdir(parents=True, exist_ok=True)
    return root_dir


def run_command(command: list[str], timeout_seconds: int = 60) -> subprocess.CompletedProcess[str]:
    """Run a host command and return the completed process without hiding failures."""

    return subprocess.run(
        command,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout_seconds,
        check=False,
    )


def run_adb(serial: str, adb_args: list[str], timeout_seconds: int = 60) -> subprocess.CompletedProcess[str]:
    """Run one adb command for the selected device serial."""

    return run_command(["adb", "-s", serial, *adb_args], timeout_seconds=timeout_seconds)


def list_online_devices() -> list[str]:
    """Return adb serials that are currently reported as online devices."""

    result = run_command(["adb", "devices"], timeout_seconds=20)
    if result.returncode != 0:
        raise RuntimeError(f"adb devices failed: {result.stderr.strip()}")
    devices: list[str] = []
    for line in result.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def resolve_device_serial(requested_serial: str | None) -> str:
    """Resolve the target adb serial from either user input or the first online device."""

    devices = list_online_devices()
    if requested_serial:
        if requested_serial not in devices:
            raise RuntimeError(f"Requested device {requested_serial} is not online. Online devices: {devices}")
        return requested_serial
    if not devices:
        raise RuntimeError("No online adb devices found.")
    return devices[0]


def parse_enabled_services(raw_value: str) -> list[str]:
    """Split the secure setting into normalized component entries."""

    return [entry.strip() for entry in raw_value.split(":") if entry.strip()]


def is_target_service_enabled(raw_value: str) -> bool:
    """Return whether the target accessibility service appears in the enabled list."""

    enabled_services = parse_enabled_services(raw_value)
    return TARGET_SERVICE_SHORT in enabled_services or TARGET_SERVICE_FULL in enabled_services


def process_running(serial: str, package_name: str) -> bool:
    """Check whether a package currently has a running process on the device."""

    result = run_adb(serial, ["shell", "pidof", package_name], timeout_seconds=20)
    if result.returncode != 0:
        return False
    return bool(result.stdout.strip())


def safe_shell_value(serial: str, shell_args: list[str]) -> str:
    """Read one small shell command and return trimmed stdout even if the command is empty."""

    result = run_adb(serial, ["shell", *shell_args], timeout_seconds=20)
    if result.returncode != 0:
        return ""
    return result.stdout.strip()


def classify_health(device_state: str, accessibility_enabled_raw: str, enabled_services_raw: str) -> tuple[str, list[str]]:
    """Classify the current state and list human-readable reasons for degradation."""

    reasons: list[str] = []
    if device_state != "device":
        reasons.append("device_offline")
        return "device_offline", reasons
    if accessibility_enabled_raw != "1":
        reasons.append("accessibility_disabled")
    if not is_target_service_enabled(enabled_services_raw):
        reasons.append("target_service_not_enabled")
    if reasons:
        return "degraded", reasons
    return "healthy", ["healthy"]


def collect_snapshot(serial: str) -> Snapshot:
    """Collect one lightweight adb snapshot that is cheap enough to poll continuously."""

    device_state_result = run_adb(serial, ["get-state"], timeout_seconds=20)
    device_state = device_state_result.stdout.strip() if device_state_result.returncode == 0 else "offline"
    accessibility_enabled_raw = safe_shell_value(serial, ["settings", "get", "secure", "accessibility_enabled"])
    enabled_services_raw = safe_shell_value(serial, ["settings", "get", "secure", "enabled_accessibility_services"])
    target_process_running = process_running(serial, TARGET_PACKAGE)
    observer_process_running = process_running(serial, OBSERVER_PACKAGE)
    health_state, reasons = classify_health(device_state, accessibility_enabled_raw, enabled_services_raw)
    return Snapshot(
        captured_at=now_iso_text(),
        captured_at_ms=int(time.time() * 1000),
        device_serial=serial,
        device_state=device_state,
        accessibility_enabled_raw=accessibility_enabled_raw,
        enabled_services_raw=enabled_services_raw,
        target_service_enabled=is_target_service_enabled(enabled_services_raw),
        target_process_running=target_process_running,
        observer_process_running=observer_process_running,
        health_state=health_state,
        reasons=reasons,
    )


def append_timeline(root_dir: Path, snapshot: Snapshot) -> None:
    """Append one snapshot as JSONL so the timeline is easy to diff and process later."""

    timeline_path = root_dir / "timeline.jsonl"
    with timeline_path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(asdict(snapshot), ensure_ascii=False) + "\n")


def write_latest_state(root_dir: Path, snapshot: Snapshot) -> None:
    """Overwrite the latest-state file with the newest lightweight snapshot."""

    latest_path = root_dir / "latest_state.json"
    latest_path.write_text(json.dumps(asdict(snapshot), ensure_ascii=False, indent=2), encoding="utf-8")


def read_recent_timeline_entries(root_dir: Path, window_minutes: int, current_time_ms: int) -> list[dict[str, object]]:
    """Load timeline entries that fall within the configured incident context window."""

    timeline_path = root_dir / "timeline.jsonl"
    if window_minutes <= 0 or not timeline_path.exists():
        return []
    window_start_ms = current_time_ms - (window_minutes * 60 * 1000)
    recent_entries: list[dict[str, object]] = []
    with timeline_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            stripped = line.strip()
            if not stripped:
                continue
            entry = json.loads(stripped)
            captured_at_ms = int(entry.get("captured_at_ms", 0))
            if captured_at_ms >= window_start_ms:
                recent_entries.append(entry)
    return recent_entries


def write_incident_context(
    incident_dir: Path,
    root_dir: Path,
    snapshot: Snapshot,
    window_minutes: int,
) -> None:
    """Persist a compact recent-timeline slice beside the heavy incident artifacts."""

    recent_entries = read_recent_timeline_entries(root_dir, window_minutes, snapshot.captured_at_ms)
    context_payload = {
        "window_minutes": window_minutes,
        "entry_count": len(recent_entries),
        "window_end_ms": snapshot.captured_at_ms,
        "window_end_at": snapshot.captured_at,
        "entries": recent_entries,
    }
    output_path = incident_dir / "recent_timeline_context.json"
    output_path.write_text(json.dumps(context_payload, ensure_ascii=False, indent=2), encoding="utf-8")


def build_transition_reason(previous: Snapshot | None, current: Snapshot) -> str:
    """Describe why the watcher should export a full evidence bundle for this snapshot."""

    if previous is None:
        return "startup_baseline"
    if current.health_state != previous.health_state:
        return f"health_{previous.health_state}_to_{current.health_state}"
    if current.accessibility_enabled_raw != previous.accessibility_enabled_raw:
        return f"accessibility_flag_{previous.accessibility_enabled_raw}_to_{current.accessibility_enabled_raw}"
    if current.enabled_services_raw != previous.enabled_services_raw:
        return "enabled_services_changed"
    if current.target_process_running != previous.target_process_running:
        return f"target_process_{previous.target_process_running}_to_{current.target_process_running}"
    if current.observer_process_running != previous.observer_process_running:
        return f"observer_process_{previous.observer_process_running}_to_{current.observer_process_running}"
    return ""


def sanitize_fragment(value: str) -> str:
    """Convert free text into a compact filename-safe fragment."""

    safe_chars = []
    for char in value.lower():
        if char.isalnum():
            safe_chars.append(char)
        elif char in {"-", "_"}:
            safe_chars.append(char)
        else:
            safe_chars.append("-")
    sanitized = "".join(safe_chars).strip("-")
    return sanitized or "incident"


def write_process_result(output_path: Path, process_result: subprocess.CompletedProcess[str]) -> None:
    """Persist command metadata and output in a single text file for later inspection."""

    content = [
        f"returncode={process_result.returncode}",
        "",
        "[stdout]",
        process_result.stdout,
        "",
        "[stderr]",
        process_result.stderr,
    ]
    output_path.write_text("\n".join(content), encoding="utf-8")


def capture_command(
    incident_dir: Path,
    serial: str,
    file_name: str,
    adb_args: list[str],
    timeout_seconds: int = 120,
) -> None:
    """Run one adb command and store its full output in the incident directory."""

    result = run_adb(serial, adb_args, timeout_seconds=timeout_seconds)
    write_process_result(incident_dir / file_name, result)


def capture_host_command(
    incident_dir: Path,
    file_name: str,
    command: list[str],
    timeout_seconds: int = 120,
) -> None:
    """Run one host-side command and store its full output in the incident directory."""

    result = run_command(command, timeout_seconds=timeout_seconds)
    write_process_result(incident_dir / file_name, result)


def capture_bugreport(incident_dir: Path, serial: str) -> None:
    """Run adb bugreport and store the host-side command result in the incident directory."""

    bugreport_prefix = incident_dir / "bugreport"
    result = run_command(["adb", "-s", serial, "bugreport", str(bugreport_prefix)], timeout_seconds=900)
    write_process_result(incident_dir / "bugreport_command.txt", result)


def capture_forensics_bundle(
    root_dir: Path,
    serial: str,
    snapshot: Snapshot,
    reason: str,
    include_bugreport: bool,
    incident_context_minutes: int,
) -> Path:
    """Export a full evidence bundle for the current snapshot transition."""

    incident_name = f"{now_text()}-{sanitize_fragment(reason)}"
    incident_dir = root_dir / "incidents" / incident_name
    incident_dir.mkdir(parents=True, exist_ok=True)
    metadata = {
        "reason": reason,
        "snapshot": asdict(snapshot),
    }
    (incident_dir / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    write_incident_context(incident_dir, root_dir, snapshot, incident_context_minutes)
    capture_host_command(incident_dir, "adb_devices.txt", ["adb", "devices", "-l"], timeout_seconds=20)
    capture_command(incident_dir, serial, "device_date.txt", ["shell", "date"], timeout_seconds=20)
    capture_command(incident_dir, serial, "build_fingerprint.txt", ["shell", "getprop", "ro.build.fingerprint"], timeout_seconds=20)
    capture_command(incident_dir, serial, "accessibility_enabled.txt", ["shell", "settings", "get", "secure", "accessibility_enabled"], timeout_seconds=20)
    capture_command(incident_dir, serial, "enabled_accessibility_services.txt", ["shell", "settings", "get", "secure", "enabled_accessibility_services"], timeout_seconds=20)
    capture_command(incident_dir, serial, "secure_settings.txt", ["shell", "settings", "list", "secure"], timeout_seconds=60)
    capture_command(incident_dir, serial, "dumpsys_accessibility.txt", ["shell", "dumpsys", "accessibility"], timeout_seconds=120)
    capture_command(incident_dir, serial, "dumpsys_package_main.txt", ["shell", "dumpsys", "package", TARGET_PACKAGE], timeout_seconds=120)
    capture_command(incident_dir, serial, "dumpsys_package_observer.txt", ["shell", "dumpsys", "package", OBSERVER_PACKAGE], timeout_seconds=120)
    capture_command(incident_dir, serial, "dumpsys_activity_services_main.txt", ["shell", "dumpsys", "activity", "services", TARGET_PACKAGE], timeout_seconds=120)
    capture_command(incident_dir, serial, "target_pidof.txt", ["shell", "pidof", TARGET_PACKAGE], timeout_seconds=20)
    capture_command(incident_dir, serial, "observer_pidof.txt", ["shell", "pidof", OBSERVER_PACKAGE], timeout_seconds=20)
    capture_command(incident_dir, serial, "logcat_main_system_events.txt", ["logcat", "-b", "main", "-b", "system", "-b", "events", "-d", "-v", "time"], timeout_seconds=180)
    if include_bugreport:
        capture_bugreport(incident_dir, serial)
    return incident_dir


def maybe_clear_logcat(serial: str, enabled: bool) -> None:
    """Optionally clear logcat buffers once before the watch loop starts."""

    if not enabled:
        return
    result = run_adb(serial, ["logcat", "-c"], timeout_seconds=30)
    if result.returncode != 0:
        raise RuntimeError(f"adb logcat -c failed: {result.stderr.strip()}")


def print_snapshot(snapshot: Snapshot, reason: str) -> None:
    """Print one concise status line for interactive monitoring on the PC."""

    reasons = ",".join(snapshot.reasons)
    marker = f" trigger={reason}" if reason else ""
    print(
        f"[{snapshot.captured_at}] health={snapshot.health_state} "
        f"ae={snapshot.accessibility_enabled_raw or '<empty>'} "
        f"service={snapshot.target_service_enabled} "
        f"targetProc={snapshot.target_process_running} "
        f"observerProc={snapshot.observer_process_running} "
        f"reasons={reasons}{marker}",
        flush=True,
    )


def write_session_config(root_dir: Path, args: argparse.Namespace, serial: str) -> None:
    """Persist the watcher session configuration for later auditability."""

    config = {
        "started_at": now_iso_text(),
        "device_serial": serial,
        "poll_seconds": args.poll_seconds,
        "bugreport_on_incident": args.bugreport_on_incident,
        "clear_logcat_on_start": args.clear_logcat_on_start,
        "capture_on_start": not args.no_capture_on_start,
        "output_dir": str(Path(args.output_dir).resolve()),
    }
    (root_dir / "session_config.json").write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8")


def run_watch_loop(args: argparse.Namespace) -> int:
    """Run the long-lived polling loop and export evidence when state changes."""

    serial = resolve_device_serial(args.serial)
    root_dir = ensure_output_dir(Path(args.output_dir))
    write_session_config(root_dir, args, serial)
    maybe_clear_logcat(serial, args.clear_logcat_on_start)

    previous_snapshot: Snapshot | None = None
    poll_count = 0
    while True:
        snapshot = collect_snapshot(serial)
        append_timeline(root_dir, snapshot)
        write_latest_state(root_dir, snapshot)
        transition_reason = build_transition_reason(previous_snapshot, snapshot)
        should_capture = bool(transition_reason) and (
            previous_snapshot is None or snapshot.health_state != "healthy" or transition_reason == "startup_baseline"
        )
        if previous_snapshot is None and args.no_capture_on_start:
            should_capture = False
        if should_capture:
            incident_dir = capture_forensics_bundle(
                root_dir=root_dir,
                serial=serial,
                snapshot=snapshot,
                reason=transition_reason,
                include_bugreport=args.bugreport_on_incident,
                incident_context_minutes=args.incident_context_minutes,
            )
            print(f"Captured evidence bundle: {incident_dir}", flush=True)
        print_snapshot(snapshot, transition_reason if should_capture else "")
        previous_snapshot = snapshot
        poll_count += 1
        if args.max_polls > 0 and poll_count >= args.max_polls:
            return 0
        time.sleep(args.poll_seconds)


def main() -> int:
    """Parse arguments, run the watcher, and expose failures clearly to the caller."""

    args = parse_args()
    if args.poll_seconds < 5:
        raise ValueError("--poll-seconds must be at least 5 seconds.")
    if args.max_polls < 0:
        raise ValueError("--max-polls cannot be negative.")
    if args.incident_context_minutes < 0:
        raise ValueError("--incident-context-minutes cannot be negative.")
    try:
        return run_watch_loop(args)
    except KeyboardInterrupt:
        print("Watcher stopped by user.", flush=True)
        return 0


if __name__ == "__main__":
    sys.exit(main())
