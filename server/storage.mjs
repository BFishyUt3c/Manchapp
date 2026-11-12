import { existsSync, readFileSync, writeFileSync, renameSync, copyFileSync } from 'node:fs';

export function loadState(file, initial) {
  if (!existsSync(file) && !existsSync(file + '.bak')) return initial();
  const parse = path => {
    const value = JSON.parse(readFileSync(path, 'utf8'));
    if (!Array.isArray(value.products) || !Array.isArray(value.orders) || !value.settings || !value.adminKey) throw new Error('Archivo de datos incompleto.');
    return value;
  };
  try { return parse(file); }
  catch {
    if (existsSync(file)) copyFileSync(file, `${file}.damaged-${Date.now()}`);
    if (!existsSync(file + '.bak')) throw new Error('Los datos necesitan recuperación. El archivo original se ha conservado.');
    const recovered = parse(file + '.bak');
    writeFileSync(file + '.tmp', JSON.stringify(recovered));
    renameSync(file + '.tmp', file);
    return recovered;
  }
}

export function saveState(file, state) {
  const content = JSON.stringify(state, null, 2);
  writeFileSync(file + '.tmp', content);
  if (existsSync(file)) {
    copyFileSync(file, file + '.bak.tmp');
    renameSync(file + '.bak.tmp', file + '.bak');
  }
  renameSync(file + '.tmp', file);
  if (!existsSync(file + '.bak')) copyFileSync(file, file + '.bak');
}
