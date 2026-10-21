# Redesign brief

Agreed point by point; feeds the mock-ups (step 3) and then the spec (step 4).
Screen numbers refer to the screen gallery (`ScreenGalleryTest`).

## Overall

- New design features and a rethink of how screens work, not only new colours and fonts.
- Dark and light themes, following the system setting.
- Full rethink is allowed: navigation and flows may change.

## 1. Today (11–17)

### 1a. Sync status icon (replaces the text in 13–15)

- The text lines "N sets not in the spreadsheet yet" and "Background sync: …" are removed from Today.
- An icon sits in the top bar, left of the account avatar, on every screen with a top bar
  (Today, picker, logging, history).
- Three statuses:
  - **All synced**: cloud with a check, muted colour; no sets waiting.
  - **Unsynced data**: cloud with an up arrow and a count badge (sets waiting).
  - **Sync in process**: spinning sync arrows while a sync runs.
- Stuck sync (no connection, spreadsheet or tab missing, sign-in needed) shows the
  **unsynced** icon in a warning colour.
- Tapping the icon opens a small popup: how many sets wait, the reason when stuck,
  the last sync time, a **Sync now** button, and **Sign in** when sign-in is needed.
- The technical background-sync line moves out of the UI; it stays in the shared sync log
  (account menu → share log).

### 1b. No sync label on single sets (logging 26, day detail 29)

- The per-set labels "In the spreadsheet" and "Not in the spreadsheet yet" are removed;
  the sync icon (1a) already tells whether anything waits.
- A done set shows only weight × reps; tapping it still opens the edit dialog.
