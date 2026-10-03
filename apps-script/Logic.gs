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
 * New workout rows from log rows [dateTime, drill, w, r]: one [date, durationMinutes, 0] per
 * day not in `existingDayKeys`, where duration = last minus first logged time that day.
 * `toDayKey(date)` returns 'yyyy-MM-dd' in the spreadsheet's time zone. Ordered by date.
 */
function dailyWorkoutRows(logRows, existingDayKeys, toDayKey) {
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
  return Object.keys(days)
    .filter(function (key) { return !existingDayKeys.has(key); })
    .sort()
    .map(function (key) {
      const day = days[key];
      return [day.first, Math.round((day.max - day.min) / 60000), 0];
    });
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
