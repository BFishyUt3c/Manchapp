import { mkdirSync, writeFileSync, existsSync, copyFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { createApp, localAddress } from '../server/server.mjs';

const dataDir = resolve('.local-server');
mkdirSync(join(dataDir, 'public'), { recursive: true });
mkdirSync(join(dataDir, 'backups'), { recursive: true });
const current = join(dataDir, 'state.json');
if (existsSync(current)) copyFileSync(current, join(dataDir, 'backups', `antes-de-iniciar-${Date.now()}.json`));
const port = Number(process.env.PORT || 4173);
const app = createApp({ dataDir, staticDir: join(dataDir, 'public') });
writeFileSync(join(dataDir, 'public', 'index.html'), '<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ManchApp</title><body style="background:#f7ddb8;color:#59152c;font:18px system-ui;padding:32px"><h1>ManchApp está conectado</h1><p>Usa esta dirección en las dos apps Android. La laptop debe permanecer encendida y en la misma red.</p><p>Los pedidos se realizan desde ManchApp Pedidos.</p></body></html>');
app.server.on('error', error => { console.error(error.code === 'EADDRINUSE' ? `El puerto ${port} ya está ocupado. Cierra el servidor anterior o elige otro puerto.` : error.message); process.exitCode = 1; });
app.server.listen(port, '0.0.0.0', () => {
  const address = `http://${localAddress()}:${port}`;
  writeFileSync(join(dataDir, 'vinculacion.txt'), `ManchApp\n\nConecta la laptop y los celulares a la misma red.\n\nDirección para ambas apps: ${address}\n\nSolo en Emprendedora, clave de vinculación:\n${app.state.adminKey}\n\nEn el emulador de esta laptop usa http://10.0.2.2:${port}\n\nPedidos no requiere clave.\nLos datos quedan en .local-server/state.json y se conservan al reiniciar.\n`);
  console.log(`ManchApp disponible en ${address}\nDatos de vinculación: .local-server/vinculacion.txt\nMantén esta terminal abierta. Para detener: Ctrl+C.`);
});
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => app.server.close(() => process.exit(0)));
