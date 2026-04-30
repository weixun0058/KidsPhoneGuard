# KidsPhoneGuard Test Site Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign the small-scope test website into a modern, friendly landing page that feels trustworthy for parents while preserving download, guidance, feedback, and FAQ flows.

**Architecture:** Keep the existing static-site architecture and feedback logic, but rebuild the information hierarchy and visual system. Move from dense equal-weight sections to a guided narrative: hero, quick understanding, start testing, structured feedback, and common questions.

**Tech Stack:** HTML, CSS, vanilla JavaScript, JSON config

---

### Task 1: Rework information architecture

**Files:**
- Modify: `site/test-portal/index.html`
- Modify: `site/test-portal/site-config.json`

**Step 1: Update product naming**

- Use a main title + subtitle pattern:
  `拉钩守护`
  `儿童防沉迷家长管理`

**Step 2: Reduce visible density**

- Keep the hero focused on value, phase, and one primary action.
- Convert long explanation blocks into concise grouped cards.
- Move supporting details into softer secondary sections and FAQ.

### Task 2: Redesign visual system

**Files:**
- Modify: `site/test-portal/styles.css`

**Step 1: Define a friendlier aesthetic**

- Use a warm, modern palette with more whitespace.
- Replace heavy editorial density with lighter cards and clearer spacing.
- Make primary actions and section hierarchy more obvious.

**Step 2: Improve scanning**

- Add visual rhythm between sections.
- Use short cards, step blocks, and highlighted summary panels.
- Ensure mobile layout remains readable and calm.

### Task 3: Keep behavior but update labels

**Files:**
- Modify: `site/test-portal/script.js`

**Step 1: Preserve functional behavior**

- Keep config loading, download URL switching, feedback submit, and local export behavior.

**Step 2: Sync new naming**

- Support the new product title and subtitle from config.
- Update page title and visible labels without changing the data flow.

### Task 4: Verify the redesign

**Files:**
- Modify: `site/test-portal/index.html`
- Modify: `site/test-portal/styles.css`
- Modify: `site/test-portal/script.js`

**Step 1: Run diagnostics**

- Check HTML, CSS, and JS for editor diagnostics.

**Step 2: Preview locally**

- Open `http://localhost:8080/site/test-portal/`
- Verify hierarchy, spacing, CTA visibility, and form usability on desktop and mobile widths.
