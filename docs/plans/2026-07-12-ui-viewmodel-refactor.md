# ISS-009 UI and ViewModel Refactor Implementation Plan

> **For Codex:** Execute this plan in small, verified phases; do not change user-visible rule behavior while moving UI code.

**Goal:** Split the oversized Compose activities by feature domain and introduce lifecycle-owned state for the parent configuration screen.

**Architecture:** The first phase keeps the existing repositories and rule semantics intact. `ConfigViewModel` becomes the single owner of observable rule/usage state and repository mutations, while composables retain only transient dialog and presentation state. Stateless app-picker UI is moved to its own source file. Later phases will apply the same separation to batch-rule editing and `MainActivity`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AndroidX ViewModel, StateFlow, Room Flow, coroutines.

---

### Task 1: Establish a testable configuration state owner

**Files:**
- Create: `app/src/main/java/com/kidsphoneguard/ui/config/ConfigViewModel.kt`
- Create: `app/src/test/java/com/kidsphoneguard/ui/config/ConfigViewModelTest.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/ui/ConfigActivity.kt`

1. Define a `ConfigUiState` containing rules, today usage, and temporary-bonus maps.
2. Inject repositories and a bonus source into `ConfigViewModel`; combine their refreshes in `viewModelScope`.
3. Move save, delete, batch-apply, and today-bonus mutations behind ViewModel methods.
4. Make `ConfigScreen` observe one `StateFlow`, retaining only dialog visibility and layout mode in Compose.
5. Run the focused JVM test and the debug build.

### Task 2: Extract app-selection presentation

**Files:**
- Create: `app/src/main/java/com/kidsphoneguard/ui/config/AppSelectorComponents.kt`
- Modify: `app/src/main/java/com/kidsphoneguard/ui/ConfigActivity.kt`

1. Move `AppSelectorDialog`, `AppListItem`, and `AppGridSelectItem` unchanged into the config UI package.
2. Keep the public function signatures unchanged so `AddRuleDialog` requires no behavior change.
3. Remove now-unused imports from `ConfigActivity`.
4. Build and run the existing unit-test suite.

### Task 3: Extract remaining feature domains incrementally

**Files:**
- Create: `ui/config/RuleEditorComponents.kt`
- Create: `ui/config/BatchRuleComponents.kt`
- Modify: `ConfigActivity.kt`

1. Move time-window, single-rule editor, and batch-rule components as separate reviewable commits.
2. Add composable/UI tests only where existing behavior is difficult to protect with ViewModel tests.
3. Preserve all public navigation, texts, and rule payloads.

### Task 4: Refactor MainActivity independently

**Files:**
- Create: `ui/main/MainDashboardComponents.kt`
- Create: `ui/main/SetupWizardComponents.kt`
- Create: `ui/main/MainViewModel.kt` when a real shared state boundary is identified
- Modify: `MainActivity.kt`

1. First move stateless dashboard and setup-wizard cards.
2. Introduce a ViewModel only for shared lifecycle state, not merely to relocate local UI booleans.
3. Run debug build, focused tests, and a real-device smoke test of dashboard, wizard, and parent-password entry.

### Task 5: Closeout

**Files:**
- Modify: `docs/ISSUES.md`
- Modify: `docs/项目综合评价_2026-07-09.md`

1. Record measured line counts and the ViewModel boundary.
2. Mark ISS-009 DONE only after both activities are decomposed, builds/tests pass, and the user completes a device smoke test.
3. Commit each independently verified phase.
