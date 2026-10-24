// Tests for apps-script/Logic.gs (contracts/apps-script.md "Pure functions").
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { loadGs, plain } from './load-gs.mjs';

const vmLogic = loadGs(['Logic.gs']);
// Call Logic.gs functions and copy results into this realm (see plain()).
const logic = new Proxy({}, { get: (_, name) => (...args) => plain(vmLogic[name](...args)) });
const d = (iso) => new Date(iso);
const dayKey = (date) => date.toISOString().slice(0, 10);

test('autoFillPlan fills consecutive empty rows above the edited row', () => {
  // rows:        1 header, 2 filled, 3 empty, 4 empty, 5 edited
  const columnB = ['Drill', 'жим лежа', '', '', 'тяга'];
  assert.deepEqual(logic.autoFillPlan(columnB, 5), [4, 3]);
});

test('autoFillPlan stops at the header row', () => {
  assert.deepEqual(logic.autoFillPlan(['Drill', '', '', 'тяга'], 4), [3, 2]);
});

test('autoFillPlan returns nothing when the row above is filled', () => {
  assert.deepEqual(logic.autoFillPlan(['Drill', 'a', 'b'], 3), []);
});

test('autoFillPlan treats null and undefined as empty', () => {
  assert.deepEqual(logic.autoFillPlan(['Drill', 'a', null, undefined, 'x'], 5), [4, 3]);
});

// Feature 006 contracts/apps-script.md: workoutRowsPlan replaces dailyWorkoutRows.
const day = (iso, minutes, flag) => [d(iso + 'T00:00:00Z'), minutes, flag];

// Feature 007 contracts/apps-script.md: appends carry [first set, flag]; the formula is added by dailyJob.
test('workoutRowsPlan adds one row per new day: its first set and the flag', () => {
  const log = [
    [d('2026-01-12T11:31:00Z'), 'тяга', 30, 8],
    [d('2026-01-12T10:00:00Z'), 'жим', 20, 10],
    [d('2026-01-14T09:00:00Z'), 'жим', 22, 10],
  ];
  const plan = logic.workoutRowsPlan(log, [], dayKey);
  assert.deepEqual(plan.appends.map((r) => r.length), [2, 2]);
  assert.deepEqual(plan.appends.map((r) => r[0].toISOString()), ['2026-01-12T10:00:00.000Z', '2026-01-14T09:00:00.000Z']);
  assert.deepEqual(plan.removals, []);
  assert.deepEqual(plan.formulaRows, []);
});

test('workoutRowsPlan skips days already in workout and non-date rows', () => {
  const log = [
    [d('2026-01-12T10:00:00Z'), 'жим', 20, 10],
    ['', 'пусто', '', ''],
    [d('2026-01-16T10:00:00Z'), 'жим', 20, 10],
  ];
  const plan = logic.workoutRowsPlan(log, [day('2026-01-12', 45, 0)], dayKey);
  assert.deepEqual(plan.appends.map((r) => dayKey(r[0])), ['2026-01-16']);
  assert.equal(plan.appends[0][1], 0);
});

test('workoutRowsPlan orders new rows by date even when the log is unsorted', () => {
  const log = [
    [d('2026-02-02T10:00:00Z'), 'a', 1, 1],
    [d('2026-01-20T10:00:00Z'), 'b', 1, 1],
  ];
  assert.deepEqual(logic.workoutRowsPlan(log, [], dayKey).appends.map((r) => dayKey(r[0])), ['2026-01-20', '2026-02-02']);
});

test('workoutRowsPlan: the first workout ever is free', () => {
  const plan = logic.workoutRowsPlan([[d('2026-01-12T10:00:00Z'), 'a', 1, 1]], [], dayKey);
  assert.equal(plan.appends[0][1], 1);
});

test('workoutRowsPlan: a new day copies the latest earlier day flag', () => {
  const log = [[d('2026-01-20T10:00:00Z'), 'a', 1, 1]];
  // Every day keeps its sets, so no row is removed.
  const all = [[d('2026-01-10T10:00:00Z'), 'a', 1, 1], [d('2026-01-12T10:00:00Z'), 'a', 1, 1], ...log];
  assert.equal(logic.workoutRowsPlan(all, [day('2026-01-10', 30, 1), day('2026-01-12', 30, 0)], dayKey).appends.at(-1)[1], 0);
  assert.equal(logic.workoutRowsPlan(all, [day('2026-01-12', 30, 0), day('2026-01-10', 30, '1')], dayKey).appends.at(-1)[1], 0);
  assert.equal(logic.workoutRowsPlan(all, [day('2026-01-12', 30, '1')], dayKey).appends.at(-1)[1], 1);
  assert.equal(logic.workoutRowsPlan(all, [day('2026-01-12', 30, '')], dayKey).appends.at(-1)[1], 0);
});

test('workoutRowsPlan: later days do not count for the default', () => {
  const log = [[d('2026-01-12T10:00:00Z'), 'a', 1, 1], [d('2026-01-20T10:00:00Z'), 'a', 1, 1]];
  assert.equal(logic.workoutRowsPlan(log, [day('2026-01-20', 30, 0)], dayKey).appends[0][1], 1);
});

test('workoutRowsPlan: a second new day copies the first new day flag', () => {
  const log = [
    [d('2026-01-14T10:00:00Z'), 'a', 1, 1],
    [d('2026-01-13T10:00:00Z'), 'a', 1, 1],
  ];
  const plan = logic.workoutRowsPlan([[d('2026-01-12T10:00:00Z'), 'a', 1, 1], ...log], [day('2026-01-12', 30, 0)], dayKey);
  assert.deepEqual(plan.appends.map((r) => r[1]), [0, 0]);
});

test('workoutRowsPlan removes rows without sets bottom-up and numbers formula rows after them', () => {
  const log = [
    [d('2026-01-12T10:00:00Z'), 'a', 1, 1],
    [d('2026-01-13T10:00:00Z'), 'a', 1, 1],
  ];
  // Sheet rows 2..6 (header is row 1).
  const rows = [day('2026-01-11', '', 0), day('2026-01-12', 45, 1), day('2026-01-10', 30, 0), day('2026-01-13', '', 0), ['', '', '']];
  const plan = logic.workoutRowsPlan(log, rows, dayKey);
  assert.deepEqual(plan.removals, [4, 2]);
  // Row 5 (Jan 13, empty B) becomes row 3 once rows 2 and 4 are gone.
  assert.deepEqual(plan.formulaRows, [3]);
  assert.deepEqual(plan.appends, []);
});

test('workoutRowsPlan with an empty log removes nothing', () => {
  const plan = logic.workoutRowsPlan([['', 'x', '', '']], [day('2026-01-12', '', 0)], dayKey);
  assert.deepEqual(plan.removals, []);
  assert.deepEqual(plan.formulaRows, [2]);
});

// Feature 007 research R4: the same cases as the app's DayRowsFixtureTest.
const cases = JSON.parse(readFileSync(new URL('./fixtures/day-rows.json', import.meta.url), 'utf8'));
const asDate = (text) => new Date(text.includes('T') ? text + 'Z' : text + 'T00:00:00Z');

for (const c of cases) {
  test(`shared case: ${c.name}`, () => {
    const log = c.logDays.map((day) => [new Date(day + 'T12:00:00Z'), 'squat', 20, 10]);
    const rows = c.workoutRows.map((r) => [asDate(r.date), '', r.free ? 1 : 0]);
    const plan = logic.workoutRowsPlan(log, rows, dayKey);
    assert.deepEqual(plan.appends.map((a) => ({ date: dayKey(a[0]), free: a[1] === 1 })), c.expected.appends);
    const removed = plan.removals.map((row) => dayKey(rows[row - 2][0])).sort();
    assert.deepEqual(removed, [...c.expected.removals].sort());
  });
}

test('balance is paid workouts minus counted workouts', () => {
  // 24 + 35 paid; 3 workouts, one of them alone (flag 1) -> 59 - 2
  assert.equal(logic.balance([24, 35], [0, 1, 0]), 57);
});

test('balance ignores non-numeric money values and treats "1" as alone', () => {
  assert.equal(logic.balance(['24', '', 'x', 11], ['1', 0, '', '0']), 32);
});

test('balance can be negative', () => {
  assert.equal(logic.balance([], [0, 0]), -2);
});

test('formatMetadata prints sorted workout_rec entries only', () => {
  const text = logic.formatMetadata([
    { key: 'workout_rec.script_id', value: 'abc' },
    { key: 'other.key', value: 'x' },
    { key: 'workout_rec.automation_enabled_at', value: '2026-10-03T09:12:44Z' },
  ]);
  assert.equal(text, 'workout_rec.automation_enabled_at = 2026-10-03T09:12:44Z\nworkout_rec.script_id = abc');
});

test('formatMetadata says so when there is nothing', () => {
  assert.equal(logic.formatMetadata([{ key: 'other', value: '1' }]), '(no workout_rec metadata)');
  assert.equal(logic.formatMetadata([]), '(no workout_rec metadata)');
});
