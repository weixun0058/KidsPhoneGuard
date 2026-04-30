# KidsPhoneGuard Test Site Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a lightweight website for small-scope family testing that explains the product, distributes the Android package, collects structured feedback, and answers common setup questions.

**Architecture:** Use a static single-page site so deployment stays simple and fast. Keep download information and feedback submission behavior configurable through a local JSON file, and implement a no-backend fallback that exports feedback locally when no API endpoint is configured.

**Tech Stack:** HTML, CSS, vanilla JavaScript, JSON config, Python `http.server` for local preview

---

### Task 1: Define site structure and content scope

**Files:**
- Create: `docs/plans/2026-04-27-kidsphoneguard-test-site-plan.md`
- Create: `site/test-portal/index.html`
- Create: `site/test-portal/styles.css`
- Create: `site/test-portal/script.js`
- Create: `site/test-portal/site-config.json`

**Step 1: Define the sections**

- Hero section for the test invitation and current phase.
- Product overview for what the app can and cannot promise.
- Download and installation section for APK distribution.
- Structured feedback form for device, system, issue time, and failure path.
- FAQ section for permission setup and known limitations.

**Step 2: Define the configuration boundary**

- Store the visible app version, download URL, support contact, and feedback endpoint in `site-config.json`.
- If `feedbackEndpoint` is empty, save form data to `localStorage` and let the tester export JSON manually.

### Task 2: Build the static website

**Files:**
- Create: `site/test-portal/index.html`
- Create: `site/test-portal/styles.css`
- Create: `site/test-portal/script.js`
- Create: `site/test-portal/site-config.json`

**Step 1: Build semantic HTML**

- Add sections for product value, testing scope, first-run steps, feedback, FAQ, and contact.
- Add placeholders that can be filled from the config file.

**Step 2: Build visual design**

- Use a clean dark-on-light editorial style with strong contrast and clear scanning hierarchy.
- Make the layout mobile-friendly because parents will often open the page on phones.

**Step 3: Build interactive behavior**

- Load configuration on page start.
- Disable the download button if no package URL is configured.
- Submit feedback to the configured endpoint when present.
- Fall back to local export when no endpoint is configured.

### Task 3: Add usage notes

**Files:**
- Create: `site/test-portal/README.md`

**Step 1: Document configuration**

- Explain how to replace the APK link.
- Explain how to connect a real feedback API later.
- Explain the local fallback behavior.

**Step 2: Document preview**

- Show the local preview command for Windows PowerShell:
  `python -m http.server 8080`
- Show the preview URL:
  `http://localhost:8080/site/test-portal/`

### Task 4: Verify locally

**Files:**
- Modify: `site/test-portal/index.html`
- Modify: `site/test-portal/styles.css`
- Modify: `site/test-portal/script.js`

**Step 1: Run a local preview**

Run: `python -m http.server 8080`
Expected: Browser can open `http://localhost:8080/site/test-portal/`

**Step 2: Validate core flows**

- Download button state changes with config.
- Feedback form validates required fields.
- Fallback export works without a backend.
- Layout remains usable on narrow mobile width.
