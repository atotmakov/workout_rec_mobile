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
 * Feature 006 (contracts/apps-script.md): what the nightly job writes to the workout tab.
 * `workoutRows` are its data rows ([date, duration, flag], sheet row 2 first). Returns
 * `appends`: one [first set, minutes, flag] per log day without a row, in date order, where flag
 * copies the latest earlier day's "work alone" (1 stays 1, anything else 0; 1 when there is none);
 * `durations`: {row, minutes} for rows with an empty duration whose day is in the log (rows the
 * app added). Duration = last minus first logged time that day. `toDayKey(date)` returns
 * 'yyyy-MM-dd' in the spreadsheet's time zone.
 */
function workoutRowsPlan(logRows, workoutRows, toDayKey) {
  const days = {};
  logRows.forEach(function (row) {
    const when = row[0];
    if (!(when instanceof Date)) return;
    const key = toDayKey(when);
    const time = when.getTime();
    const day = days[key];
    if (!day) {
      days[key] = { first: when, min: time, max: time };
    } else {
      if (time < day.min) {
        day.min = time;
        day.first = when;
      }
      if (time > day.max) day.max = time;
    }
  });
  const minutes = function (key) { return Math.round((days[key].max - days[key].min) / 60000); };

  const flags = {}; // day key -> 0 or 1, from existing rows and planned appends
  const durations = [];
  workoutRows.forEach(function (row, i) {
    if (!(row[0] instanceof Date)) return;
    const key = toDayKey(row[0]);
    if (!(key in flags)) flags[key] = String(row[2]) === '1' ? 1 : 0;
    if (row[1] === '' && days[key]) durations.push({ row: i + 2, minutes: minutes(key) });
  });
  const appends = Object.keys(days)
    .filter(function (key) { return !(key in flags); })
    .sort()
    .map(function (key) {
      const earlier = Object.keys(flags).filter(function (k) { return k < key; }).sort();
      const flag = earlier.length ? flags[earlier[earlier.length - 1]] : 1;
      flags[key] = flag;
      return [days[key].first, minutes(key), flag];
    });
  return { appends: appends, durations: durations };
}

/**
 * Balance = total of numeric money "workouts" values minus the number of workouts not done
 * alone. `workoutAloneFlags` has one entry per non-empty workout row; "1" means alone.
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
