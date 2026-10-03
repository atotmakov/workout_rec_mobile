# Contract: Google API calls made by the app

All calls use `Authorization: Bearer <access token>` obtained per research R3. Each call has a
MockWebServer contract test (request method, path, query, and body shape, plus parsing of a
recorded response fixture).

| # | Purpose | Call | Key parameters / body | Used in state |
|---|---------|------|-----------------------|---------------|
| 1 | Find spreadsheet | `GET https://www.googleapis.com/drive/v3/files` | `q=name='workout_rec_database_' and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false`, `orderBy=modifiedTime desc`, `fields=files(id,name,modifiedTime)` | FindingSpreadsheet |
| 2 | Check backup name taken | `GET .../drive/v3/files` | `q=name='<backup name>' and trashed=false`, `fields=files(id)` | RenamingToBackup |
| 3 | Rename to backup | `PATCH .../drive/v3/files/{fileId}` | `{"name": "<backup name>"}` | RenamingToBackup |
| 4 | Create spreadsheet | `POST https://sheets.googleapis.com/v4/spreadsheets` | Full spreadsheet resource per [spreadsheet.md](spreadsheet.md) | Creating |
| 4b | Fallback structure | `POST .../v4/spreadsheets/{id}:batchUpdate` | Same content as requests (only if 4 rejects a part) | Creating |
| 5 | Read structure + metadata | `GET .../v4/spreadsheets/{id}` | `ranges=log!1:1, drills!1:1, money!1:1, workout!1:1, rec!A1:D2`, `fields=sheets(properties.title,conditionalFormats,data.rowData.values(userEnteredValue,dataValidation)),developerMetadata` | CheckingStructure, status refresh |
| 6 | Write app metadata | `POST .../v4/spreadsheets/{id}:batchUpdate` | `createDeveloperMetadata` / `updateDeveloperMetadata` for `script_id`, `enable_url` | AttachingScript |
| 7 | Create attached script | `POST https://script.googleapis.com/v1/projects` | `{"title":"workout_rec_automation","parentId":"<spreadsheetId>"}` | AttachingScript |
| 8 | Upload script files | `PUT .../v1/projects/{scriptId}/content` | files: `appsscript` (JSON), `Config`, `Code`, `Logic` (SERVER_JS), `Enable` (HTML) | AttachingScript |
| 9 | Create version | `POST .../v1/projects/{scriptId}/versions` | `{"description":"workout_rec v<SCRIPT_VERSION>"}` | AttachingScript |
| 10 | Deploy web app | `POST .../v1/projects/{scriptId}/deployments` | `{"versionNumber":N,"manifestFileName":"appsscript","description":"enable"}` → read `entryPoints[type=WEB_APP].webApp.url` | AttachingScript |

## Error mapping

| Response | App error | User-facing result |
|----------|-----------|--------------------|
| No network / timeout | `Offline` | "Internet connection needed", Retry (FR-011) |
| 401 | `TokenExpired` | Silent re-authorize once, then retry the call |
| 403 from call 7 with message containing "Apps Script API" and "enable" | `AppsScriptApiDisabled` | Step 1 guide, opens `https://script.google.com/home/usersettings` |
| 403 otherwise | `AccessDenied` | "Access was not granted", choose account again |
| 404 on a stored spreadsheet ID | `SpreadsheetGone` | Back to FindingSpreadsheet |
| 429 / 5xx | `ServiceUnavailable` | Message + Retry; automatic retry with backoff up to 3 times |
