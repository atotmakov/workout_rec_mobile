# Quickstart Results: Log New Workout

Device run of [quickstart.md](quickstart.md) "Manual on a device" (task T059). Build: **57**
(GitHub Release `build-57`, from PR #9). Tester: the owner, on an Android phone with Brave as
default browser.

| # | Scenario | Build | Result |
|---|----------|-------|--------|
| 1 | Add exercise → pick → plan 3 sets → confirm each | 57 | **Pass** — list grouped by muscle group, search works, plan dialog and set rows correct; 3 rows in the log tab with the right exercise, weight, reps and confirm times |
| 8 | Enter a past workout: yesterday 18:00, 60 min, 3 sets → Save | 57 | **Pass** — editor showed 18:00 / 18:30 / 19:00; 3 rows in the log tab dated yesterday with those times (the daily-job duration check is pending until the next run) |
| 2 | Same exercise on a later day: last time, record, pre-fill, steppers | 57 | **Pass** — planned count 3 from last time; last time and record shown correctly; rows pre-filled in gray, turn black when changed (+0.5 kg, −1 rep); a pre-filled set is saved with one tap |
| 3 | Airplane mode, log 2 exercises × 2 sets, close the app (swiped away, no restart), airplane mode off, do not open the app | 57 | **Fail (issue 1)** — offline logging works (exercise list offline, "4 sets not in the spreadsheet yet"), but nothing reached the log tab in 10 minutes with the app closed; after opening the app the sets synced within seconds, in order, and showed "In the spreadsheet" |
| 3b | Same, app left with Home (not swiped away); Pixel 10a, battery Unrestricted | 57 | **Fail (issue 1)** — 2 sets did not arrive without opening the app, so the cause is in the app, not the phone |
| 3c | Same as 3b with the diagnostic line (build 61) | 61 | **Fail (issue 1, cause found)** — line showed "BLOCKED, BLOCKED, RUNNING · last run 22:25 → failed: NETWORK": one background run never finished and the queued runs behind it stayed blocked; nothing synced in 5 min; opening the app synced at once |
| 3d | Build 65 (time limits + replace policy): offline then online, app in background | 65 | **Fail (issue 1)** — background run started while offline and hung ~3 min ("started" → "failed: NETWORK"), then retried every ~4 min; with the app open the set synced ~2 min after the network returned. Cause: the CONNECTED constraint is met by a network without internet in airplane mode; fix: require a validated internet network |
| 3e | Build 75 (shared sync log): offline then online, app closed; phone has an always-on VPN | 75 | **Fail (issue 1, cause found)** — log: sets queued 01:16, app process stayed alive, standby "active", yet no background run started for 13 min; on opening the app a new run started in 60 ms and synced. Cause: the network request kept the builder's default "not a VPN" capability, and with an always-on VPN the app's network is the VPN; fix: allow a VPN network |
