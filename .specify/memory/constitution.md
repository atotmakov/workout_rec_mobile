# Workout Rec Mobile Constitution

## Core Principles

### I. Native Android App

- The Android app MUST be written in Kotlin.
- There is no iOS app.
- Cross-platform UI frameworks (Flutter, React Native, Kotlin Multiplatform UI, etc.) MUST NOT
  be used for app code.
- The app MUST follow Android platform conventions (navigation, lifecycle, permissions).

Rationale: native code gives the best access to platform health/sensor APIs, performance, and
platform UX.

### II. Test-First (NON-NEGOTIABLE)

- Tests MUST be written before implementation and MUST be observed failing before the
  implementing code is written (Red → Green → Refactor).
- This applies to the Android app (JUnit-based tests) and to any Google Apps Script code.
- Bug fixes MUST start with a failing test that reproduces the bug.
- Code without tests MUST NOT be merged.

Rationale: workout logging and progress history are the product's core value; regressions there
are silent and costly, so correctness is proven before code exists.

### III. Hybrid Data Architecture

- Workout logging data MUST be based on Google Sheets; all settings such as the exercise list,
  top records, and workouts by day MUST be stored in the Google Sheet; it MUST be possible to
  edit the sheet via the Google Sheets web UI.
- Workout logging MUST work on-device without network connectivity; logged data MUST be
  persisted locally first and synchronized to the Google Sheet when connectivity is available.
- Synchronization MUST make a best effort to minimize data loss and conflicts, without
  guaranteeing their absence; the app MUST sync data with the Google Sheet as often as possible
  without degrading the user experience.
- Each feature plan MUST define conflict resolution between app and sheet edits.

Rationale: users log workouts in gyms with unreliable connectivity, and the spreadsheet must
remain editable via the web UI.

### IV. Maximize Use of Google Services

- All communication between the app and the Google Sheet MUST go through Google APIs; a custom
  backend MUST NOT be introduced.
- Google Apps Script MAY be used to implement backend logic.

Rationale: avoiding a custom backend keeps the service simple.

## Technology Constraints

- Android: Kotlin. Specific UI frameworks, persistence libraries, and minimum OS
  versions are chosen in each feature's implementation plan and MUST stay consistent across
  features once chosen.
- New third-party dependencies MUST be justified in the implementation plan.

## Development Workflow & Quality Gates

- Work follows the Spec Kit flow: specify → (clarify) → plan → tasks → implement, one feature
  branch per feature.
- Each implementation plan MUST include a Constitution Check confirming compliance with all
  principles; any deviation MUST be documented with justification.
- A change is mergeable only when: all tests pass for the Android app and any Google Apps Script
  code, as applicable; and new behavior was developed test-first.

## Governance

- This constitution supersedes all other development practices for this project.
- Amendments are made via `/speckit-constitution`, MUST be documented in the Sync Impact Report,
  and MUST be committed on their own.
- Versioning follows semantic versioning: MAJOR for removing or redefining principles, MINOR for
  adding principles or materially expanding guidance, PATCH for clarifications and wording.
- Every spec, plan, and code review MUST verify compliance with these principles; complexity or
  deviations MUST be justified in writing.

**Version**: 1.0.0 | **Ratified**: 2026-09-29 | **Last Amended**: 2026-09-29
