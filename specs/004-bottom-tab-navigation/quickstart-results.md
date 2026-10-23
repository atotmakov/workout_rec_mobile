# Quickstart Results: Bottom Tab Navigation

Feature 004 shipped in PRs #30–#32. Bugs found after release are tracked as GitHub issues and
listed here so the feature's history stays in one place.

## Issues found after release

1. **Crash when leaving the tabs for setup** ([#35](https://github.com/atotmakov/workout_rec_mobile/issues/35),
   builds 164–170, found by AppTracer on 2026-10-23): sign out, switch account or a failed
   sign-in from any tab closed the app with "Restore State failed: route tab/today cannot be
   found from the current destination … route=setup". Research R1's "the next account starts on
   fresh tabs" cleared the tabs' saved stacks after navigating to setup, where the nested tab
   graphs can't be found, so it crashed every time, even straight from Today. The stacks are
   now cleared first (`nav.leaveTabs`), covered by `androidTest/nav/LeaveTabsTest.kt`; no UI
   test had left the tabs for setup before.
