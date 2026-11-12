import { mkdirSync, mkdtempSync, writeFileSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { createApp } from '../server/server.mjs';

mkdirSync(resolve('tmp'), { recursive: true });
const dataDir = mkdtempSync(join(resolve('tmp'), 'android-connected-'));
const clockFile = resolve('tmp/qa-clock.txt');
const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const tomorrow = new Date(`${day}T12:00:00Z`); tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
const saleDate = tomorrow.toISOString().slice(0, 10), beforeSale = Date.parse(`${day}T12:00:00-05:00`), duringSale = Date.parse(`${saleDate}T12:00:00-05:00`);
writeFileSync(clockFile, String(beforeSale));
const app = createApp({ dataDir, now: () => Number(readFileSync(clockFile, 'utf8')) });
Object.assign(app.state.settings, { saleDate, pickupFrom: `${saleDate}T00:00`, pickupUntil: `${saleDate}T23:59`, reserveUntil: `${day}T23:59`, salesOpen: true, phone: '900000000', pickup: 'Punto de prueba', name: 'Emprendimiento de prueba' });
for (const product of app.state.products) { product.stock = 100; product.available = true; }
app.server.listen(4181, '127.0.0.1', () => {
  writeFileSync(resolve('tmp/qa-connection.json'), JSON.stringify({ endpoint: 'http://10.0.2.2:4181', adminKey: app.state.adminKey, storeId: app.state.storeId, dataDir, beforeSale, duringSale }));
  console.log('Servidor aislado de comprobaciones disponible en el puerto 4181.');
});
app.server.on('error', error => { console.error(error.message); process.exitCode = 1; });
