// Minimal fakes of the Apps Script services used by Code.gs. Each fake records calls so
// tests can assert order and that read-only paths never write.

export function createFakes({ timeZone = 'Etc/UTC' } = {}) {
  const calls = []; // ordered log of writes and notable reads: ['setValue', 'rec!A1', 'x'], ...

  class FakeRange {
    constructor(sheet, row, col, numRows = 1, numCols = 1) {
      Object.assign(this, { sheet, row, col, numRows, numCols });
    }
    getSheet() { return this.sheet; }
    getRow() { return this.row; }
    getColumn() { return this.col; }
    getValue() { return this.sheet.cell(this.row, this.col); }
    getValues() {
      const out = [];
      for (let r = 0; r < this.numRows; r++) {
        const line = [];
        for (let c = 0; c < this.numCols; c++) line.push(this.sheet.cell(this.row + r, this.col + c));
        out.push(line);
      }
      return out;
    }
    setValue(value) {
      calls.push(['setValue', `${this.sheet.name}!${a1(this.row, this.col)}`, value]);
      this.sheet.setCell(this.row, this.col, value);
      return this;
    }
  }

  class FakeSheet {
    constructor(name, rows = []) {
      this.name = name;
      this.rows = rows.map((r) => [...r]);
    }
    getName() { return this.name; }
    cell(row, col) {
      const line = this.rows[row - 1];
      return line && line[col - 1] !== undefined ? line[col - 1] : '';
    }
    setCell(row, col, value) {
      while (this.rows.length < row) this.rows.push([]);
      const line = this.rows[row - 1];
      while (line.length < col) line.push('');
      line[col - 1] = value;
    }
    getRange(rowOrA1, col, numRows, numCols) {
      if (typeof rowOrA1 === 'string') {
        const { row, col: c } = parseA1(rowOrA1);
        return new FakeRange(this, row, c);
      }
      return new FakeRange(this, rowOrA1, col, numRows ?? 1, numCols ?? 1);
    }
    getLastRow() { return this.rows.length; }
    getLastColumn() { return Math.max(0, ...this.rows.map((r) => r.length)); }
    getDataRange() { return new FakeRange(this, 1, 1, this.getLastRow(), Math.max(1, this.getLastColumn())); }
    appendRow(values) {
      calls.push(['appendRow', this.name, [...values]]);
      this.rows.push([...values]);
      return this;
    }
  }

  class FakeMetadata {
    constructor(key, value) { Object.assign(this, { key, value }); }
    getKey() { return this.key; }
    getValue() { return this.value; }
    setValue(value) {
      calls.push(['setMetadata', this.key, value]);
      this.value = value;
      return this;
    }
  }

  class FakeSpreadsheet {
    constructor(sheets) {
      this.sheets = Object.fromEntries(sheets.map((s) => [s.name, s]));
      this.metadata = [];
    }
    getSheetByName(name) { return this.sheets[name] ?? null; }
    getSpreadsheetTimeZone() { return timeZone; }
    getDeveloperMetadata() {
      calls.push(['getDeveloperMetadata']);
      return [...this.metadata];
    }
    addDeveloperMetadata(key, value, visibility) {
      calls.push(['addMetadata', key, value, visibility]);
      this.metadata.push(new FakeMetadata(key, value));
      return this;
    }
    createDeveloperMetadataFinder() {
      let key;
      const finder = {
        withKey(k) { key = k; return finder; },
        find: () => this.metadata.filter((m) => m.key === key),
      };
      return finder;
    }
    metadataValue(key) {
      const m = this.metadata.find((x) => x.key === key);
      return m ? m.value : undefined;
    }
  }

  const triggers = [];
  const ScriptApp = {
    getProjectTriggers: () => [...triggers],
    newTrigger(handler) {
      const spec = { handler };
      const builder = {
        timeBased() { spec.timeBased = true; return builder; },
        everyDays(n) { spec.everyDays = n; return builder; },
        atHour(h) { spec.atHour = h; return builder; },
        create() {
          calls.push(['createTrigger', spec]);
          const trigger = { ...spec, getHandlerFunction: () => handler };
          triggers.push(trigger);
          return trigger;
        },
      };
      return builder;
    },
  };

  let spreadsheet;
  const openByIdCalls = [];
  const SpreadsheetApp = {
    DeveloperMetadataVisibility: { DOCUMENT: 'DOCUMENT', PROJECT: 'PROJECT' },
    openById(id) {
      openByIdCalls.push(id);
      return spreadsheet;
    },
    getActiveSpreadsheet: () => spreadsheet,
  };

  const templates = [];
  const HtmlService = {
    createTemplateFromFile(name) {
      const template = {
        file: name,
        evaluate() {
          const output = {
            template,
            title: undefined,
            setTitle(t) { output.title = t; return output; },
          };
          return output;
        },
      };
      templates.push(template);
      return template;
    },
  };

  const Utilities = {
    // Enough for tests running in UTC.
    formatDate(date, tz, pattern) {
      if (pattern !== 'yyyy-MM-dd') throw new Error(`unsupported pattern ${pattern}`);
      return date.toISOString().slice(0, 10);
    },
  };

  const logged = [];
  const fakeConsole = { log: (...args) => logged.push(args.join(' ')) };

  return {
    calls,
    triggers,
    templates,
    logged,
    openByIdCalls,
    FakeSheet,
    FakeSpreadsheet,
    useSpreadsheet(ss) { spreadsheet = ss; return ss; },
    globals: { SpreadsheetApp, ScriptApp, HtmlService, Utilities, console: fakeConsole },
  };
}

export function a1(row, col) {
  return String.fromCharCode(64 + col) + row;
}

function parseA1(ref) {
  const m = /^([A-Z])(\d+)$/.exec(ref);
  if (!m) throw new Error(`unsupported A1 ref ${ref}`);
  return { col: m[1].charCodeAt(0) - 64, row: Number(m[2]) };
}
