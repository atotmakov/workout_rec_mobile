// Loads Apps Script .gs files into one node:vm context, like the Apps Script
// runtime does with all files of a project. Globals (fake Google services,
// Config.gs constants) are injected through `globals`.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import vm from 'node:vm';

const scriptDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export function loadGs(files, globals = {}) {
  const context = vm.createContext({ console, Date, Math, JSON, ...globals });
  for (const file of files) {
    const source = readFileSync(path.join(scriptDir, file), 'utf8');
    vm.runInContext(source, context, { filename: file });
  }
  return context;
}

// Function declarations become context properties, but top-level `const` does not.
// Use this to read a value declared with const/let in the loaded files.
export function evalIn(context, expression) {
  return vm.runInContext(expression, context);
}
