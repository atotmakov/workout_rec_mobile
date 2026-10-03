// Tests for apps-script/Logic.gs (contracts/apps-script.md "Pure functions").
import { test } from 'node:test';
import assert from 'node:assert/strict';
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

test('dailyWorkoutRows adds one three-value row per new day with duration in minutes', () => {
  const log = [
    [d('2026-01-12T10:00:00Z'), 'жим', 20, 10],
    [d('2026-01-12T11:31:00Z'), 'тяга', 30, 8],
    [d('2026-01-14T09:00:00Z'), 'жим', 22, 10],
    [d('2026-01-14T10:29:30Z'), 'жим', 22, 9],
  ];
  const rows = logic.dailyWorkoutRows(log, new Set(), dayKey);
  assert.equal(rows.length, 2);
  assert.deepEqual(rows.map((r) => r.length), [3, 3]);
  assert.deepEqual(rows.map((r) => dayKey(r[0])), ['2026-01-12', '2026-01-14']);
  assert.deepEqual(rows.map((r) => r[1]), [91, 90]);
  assert.deepEqual(rows.map((r) => r[2]), [0, 0]);
});

test('dailyWorkoutRows skips days already in workout and non-date rows', () => {
  const log = [
    [d('2026-01-12T10:00:00Z'), 'жим', 20, 10],
    ['', 'пусто', '', ''],
    [d('2026-01-16T10:00:00Z'), 'жим', 20, 10],
  ];
  const rows = logic.dailyWorkoutRows(log, new Set(['2026-01-12']), dayKey);
  assert.deepEqual(rows.map((r) => dayKey(r[0])), ['2026-01-16']);
  assert.equal(rows[0][1], 0);
});

test('dailyWorkoutRows orders rows by date even when the log is unsorted', () => {
  const log = [
    [d('2026-02-02T10:00:00Z'), 'a', 1, 1],
    [d('2026-01-20T10:00:00Z'), 'b', 1, 1],
  ];
  assert.deepEqual(logic.dailyWorkoutRows(log, new Set(), dayKey).map((r) => dayKey(r[0])), ['2026-01-20', '2026-02-02']);
});

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
