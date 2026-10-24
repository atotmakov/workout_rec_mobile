// Pure business rules for the attached script. No Google services here, so the
// functions can be unit-tested under Node (apps-script/tests/logic.test.mjs).

function isEmpty_(value) {
  return value === '' || value === null || value === undefined;
}

/**
 * Rows to fill when column B of row `editedRow` is edited: the consecutive empty rows
 * directly above it, going up until the first non-empty row or the header row (row 1).
 * `columnB[i]` is the value of row i + 1. Returns 1-based row numbers, nearest first.
 */
function autoFillPlan(columnB, editedRow) {
  const rows = [];
  for (let row = editedRow - 1; row >= 2; row--) {
    if (!isEmpty_(columnB[row - 1])) break;
    rows.push(row);
  }
  return rows;
}

/**
 * Feature 007 (contracts/apps-script.md): what the nightly job changes in the workout tab, so it has
 * one row per log day and only those. `workoutRows` are its data rows ([date, duration, flag],
 * sheet row 2 first). Returns
 * `appends`: [first set, flag] per log day without a row, in date order; flag copies the latest
 * earlier remaining day's "work alone" (1 stays 1, anything else 0; 1 when there is none);
 * `removals`: sheet rows whose date has no log day, highest first (none when the log has no sets);
 * `formulaRows`: rows that stay and have an empty duration, numbered as after the removals.
 * `toDayKey(date)` returns 'yyyy-MM-dd' in the spreadsheet's time zone. The app's DayRowsPlan
 * follows the same rule (tests/fixtures/day-rows.json).
 */
function workoutRowsPlan(logRows, workoutRows, toDayKey) {
  const days = {};
  logRows.forEach(function (row) {
    const when = row[0];
    if (!(when instanceof Date)) return;
    const key = toDayKey(when);
    const day = days[key];
    if (!day || when.getTime() < day.getTime()) days[key] = when;
  });
  const logEmpty = Object.keys(days).length === 0;

  const flags = {}; // day key -> 0 or 1, from remaining rows and planned appends
  const removals = [];
  const kept = [];
  workoutRows.forEach(function (row, i) {
    if (!(row[0] instanceof Date)) return;
    const key = toDayKey(row[0]);
    const sheetRow = i + 2;
    if (!logEmpty && !days[key]) {
      removals.push(sheetRow);
      return;
    }
    if (!(key in flags)) flags[key] = String(row[2]) === '1' ? 1 : 0;
    if (row[1] === '') kept.push(sheetRow);
  });
  removals.sort(function (a, b) { return b - a; });
  const formulaRows = kept.map(function (row) {
    return row - removals.filter(function (removed) { return removed < row; }).length;
  });

  const appends = Object.keys(days)
    .filter(function (key) { return !(key in flags); })
    .sort()
    .map(function (key) {
      const earlier = Object.keys(flags).filter(function (k) { return k < key; }).sort();
      const flag = earlier.length ? flags[earlier[earlier.length - 1]] : 1;
      flags[key] = flag;
      return [days[key], flag];
    });
  return { appends: appends, removals: removals, formulaRows: formulaRows };
}

/**
 * Balance = total of numeric money "workouts" values minus the number of workouts not done
 * alone. `workoutAloneFlags` has one entry per non-empty workout row; "1" means alone.
 * Feature 007: only for a spreadsheet the app has not converted yet; afterwards balance!A1 is a
 * formula with the same rule.
 */
function balance(moneyWorkouts, workoutAloneFlags) {
  const paid = moneyWorkouts.reduce(function (sum, value) {
    const n = parseFloat(value);
    return isNaN(n) ? sum : sum + n;
  }, 0);
  const counted = workoutAloneFlags.filter(function (flag) { return String(flag) !== '1'; }).length;
  return paid - counted;
}

/** One "key = value" line per workout_rec.* metadata entry, sorted by key. */
function formatMetadata(entries) {
  const lines = entries
    .filter(function (entry) { return String(entry.key).indexOf('workout_rec.') === 0; })
    .sort(function (a, b) { return a.key < b.key ? -1 : a.key > b.key ? 1 : 0; })
    .map(function (entry) { return entry.key + ' = ' + entry.value; });
  return lines.length ? lines.join('\n') : '(no workout_rec metadata)';
}
