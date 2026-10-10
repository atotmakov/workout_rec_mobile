# Quickstart results: Google Account Setup

Manual validation of [quickstart.md](quickstart.md) on a real device (T070).

**Device**: Android phone, default browser Brave, two Google accounts signed in to the browser
(atotmakov.nexus@gmail.com first, atotmakov@gmail.com second). App account: atotmakov@gmail.com.

**Google Cloud (T007)**: project `workout-rec` (`workout-rec-510520`, owner atotmakov.dev@gmail.com);
Drive, Sheets and Apps Script APIs enabled; consent screen External, **In production**
(unverified, 100-user cap), scopes `drive.file`, `script.projects`, `script.deployments`;
home page and privacy policy on GitHub Pages (`atotmakov.github.io` authorized); Android OAuth
client (package `com.workoutrec`, SHA-1 of the shared CI debug key) and Web OAuth client
(`GOOGLE_WEB_CLIENT_ID` secret).

## Results (2026-10-04)

| # | Scenario | Build | Result |
|---|----------|-------|--------|
| 1 | First launch, choose account, avatar top-right | 16 | **Pass** – timing not measured (SC-001) |
| 2 | Step 1 (Apps Script API setting) | 16 | **Pass** – setting was already on or accepted; the script was attached |
| 3 | Step 2 (Enable automation) | 16, 20 | **Fail → fixed in build 24** – see issues 1–2 |
| 3 | Step 2 (Enable automation) | 24 / incognito | **Pass** – page showed "automation is on"; app shows the main screen with avatar and no reminder |
| 4 | Spreadsheet structure on the web | 16 | **Partial** – six tabs present; the reference's **Google Sheets tables** (typed columns) are missing – see issue 3 |
| 4 | Spreadsheet structure on the web | 36 | **Pass** – tables log, drills, payments, workouts with typed columns; log!B drop-down |
| 5 | Auto-fill on editing log column B | – | Not yet run |
| 6 | Nightly job (workout rows, balance) | – | Pending (first run the night after setup) |
| 6a | `logMetadata` in the script editor | – | Not yet run |
| 7 | Relaunch opens the main screen directly | – | Not yet run |
| 8 | Reinstall reuses the spreadsheet | – | Not yet run |
| 9 | Rewrite question and backup | 32, 36 | **Pass** – via Switch account → same account; old spreadsheet kept as `workout_rec_database_backup_2026-10-04` (and `_2`), new one created |
| 10 | No network on first launch | – | Not yet run |
| 11 | Russian UI | – | Not yet run |
| 12 | Sign out | 89, 98 | **Fail → fixed in build 98** – signing in again asked to rewrite a correct spreadsheet; see issue 4. Build 98: the existing spreadsheet is reused (2026-10-10) |
| 13 | Automation stopped reminder | – | Not yet run |

## Issues found

1. **Step 2 page opened as the browser's default account** (build 16): "Couldn't open file.
   Check the address". Build 20 added `authuser=<app account>`; it selected the right account
   but the page still failed.
2. **Root cause**: an Apps Script web app only works for the browser's **first** signed-in
   account; opening the plain link in an incognito tab signed in only as the owner works.
   Build 24 opens step 2 in a private tab when supported, otherwise offers *Copy link* for an
   incognito tab (research R14).
3. **Tables missing**: the reference spreadsheet uses Google Sheets tables with typed columns;
   the created spreadsheet only has header rows. Spec gap – to be added to the reference
   structure (version 2, build 32). Build 32 still created none: `spreadsheets.create` silently
   ignores `Sheet.tables`. Build 36 adds them with `addTable` right after create – **fixed**.
4. **Rewrite question on a correct spreadsheet** (2026-10-10): the structure check compared
   formulas as text, and Google returns them in the spreadsheet locale's form – `ru_RU` uses `;`
   – so the rec rule `=AND($C2=MAX($C:$C), …)` never matched and every existing spreadsheet got
   the rewrite question (also behind the backups in scenario 9). Build 98 treats `,` and `;` as
   the same separator – **fixed**.

**2026-10-04: the user accepted feature 001 as complete.** Scenarios still marked *Not yet run*
were not executed before acceptance.

## Confirmed by the device test (T008)

Creating the spreadsheet, attaching the script (`projects.create` with `parentId`), uploading
its files, creating a version and deploying the web app all work with only `drive.file`,
`script.projects` and `script.deployments`. No extra Drive access was needed.
