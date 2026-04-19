# PC Forensics Watch Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a PC-side Python watcher that uses `adb` to detect KidsPhoneGuard accessibility drops and automatically save a forensics bundle when state degrades.

**Architecture:** The watcher runs on Windows, polls a small set of low-cost `adb shell` commands, appends each snapshot to a timeline file, and triggers a full evidence export when the monitored state changes or becomes unhealthy. Evidence is stored outside the Android apps so collection still works even when the app process dies or accessibility has already been revoked.

**Tech Stack:** Python 3.12, `adb`, JSONL, Markdown docs

---

### Task 1: Define the watcher layout

**Files:**
- Create: `docs/plans/2026-04-17-pc-forensics-watch-implementation.md`
- Create: `scripts/pc_forensics_watch.py`
- Modify: `.gitignore`

**Step 1: Define output directories**

- Use `forensics/pc-watch/` as the default root output directory.
- Write session files directly under the root.
- Write incident bundles under `forensics/pc-watch/incidents/<timestamp>-<reason>/`.

**Step 2: Define the minimum state model**

- Poll `accessibility_enabled`.
- Poll `enabled_accessibility_services`.
- Poll `pidof com.kidsphoneguard`.
- Poll `pidof com.kidsphoneguard.observer`.
- Classify snapshots as `healthy`, `degraded`, or `device_offline`.

### Task 2: Implement the Python watcher

**Files:**
- Create: `scripts/pc_forensics_watch.py`

**Step 1: Implement argument parsing**

- Add flags for `--poll-seconds`, `--serial`, `--output-dir`, `--clear-logcat-on-start`, `--capture-on-start`, and `--bugreport-on-incident`.

**Step 2: Implement low-cost polling**

- Resolve the active device.
- Collect state with lightweight `adb` commands.
- Write each snapshot to `timeline.jsonl`.
- Update `latest_state.json`.

**Step 3: Implement transition detection**

- Trigger evidence capture when health changes.
- Trigger evidence capture when the service listing changes.
- Trigger evidence capture when accessibility flips from enabled to disabled.

**Step 4: Implement evidence export**

- Save command output files for package state, accessibility state, secure settings, running services, and logcat buffers.
- Optionally run `adb bugreport` only when the user requests it.

### Task 3: Add targeted tests

**Files:**
- Create: `scripts/test_pc_forensics_watch.py`

**Step 1: Test service parsing**

- Verify target service matching for short and full component names.

**Step 2: Test health classification**

- Verify `healthy`, `degraded`, and `device_offline` cases.

### Task 4: Document usage

**Files:**
- Modify: `README.md`

**Step 1: Add a focused section**

- Explain what the watcher solves.
- Show how to start it on Windows.
- Explain where incident bundles are saved.

**Step 2: Add verification notes**

- Document what a healthy state looks like.
- Document what files appear when an incident is captured.
