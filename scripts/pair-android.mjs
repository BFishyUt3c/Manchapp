import { readFileSync } from 'node:fs';
import { localAddress } from '../server/server.mjs';
try { const state = JSON.parse(readFileSync(new URL('../.data/state.json', import.meta.url), 'utf8')); console.log(`Vincula la app Android en Datos de la olla → Conectar con laptop.\nDirección: http://${localAddress()}:${process.env.PORT || 4173}\nClave de esta beta local: ${state.adminKey}\nNo publiques esta clave ni el instalador con vinculación incluida fuera del equipo de demostración.`); } catch { console.error('Inicia primero npm run demo.'); process.exitCode = 1; }
