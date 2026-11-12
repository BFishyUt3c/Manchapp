import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, readdirSync, rmdirSync, unlinkSync } from 'node:fs';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { createApp } from './server.mjs';
import { saleKey } from './booking.mjs';
import { time } from './schedule.mjs';
import { loadState } from './storage.mjs';

async function fixture(t) {
  mkdirSync(join(process.cwd(), 'tmp'), { recursive: true });
  const dataDir = mkdtempSync(join(process.cwd(), 'tmp', 'connected-'));
  let clock = time('2026-11-05T10:00');
  const app = createApp({ dataDir, now: () => clock });
  Object.assign(app.state.settings, { saleDate: '2026-11-06', pickupFrom: '2026-11-06T16:00', pickupUntil: '2026-11-06T19:00', reserveUntil: '2026-11-05T20:00', salesOpen: true });
  await new Promise(resolve => app.server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => app.server.close(resolve)));
  const base = `http://127.0.0.1:${app.server.address().port}`;
  const call = async (path, body, admin = false, token) => {
    const response = await fetch(base + path, { method: body ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json', ...(admin ? { 'X-Manchapp-Key': app.state.adminKey } : {}), ...(token ? { 'X-Order-Token': token } : {}) }, body: body ? JSON.stringify(body) : undefined });
    return { status: response.status, data: await response.json() };
  };
  const request = () => ({ requestId: randomUUID(), storeId: app.state.storeId, customer: 'Prueba de conexión', phone: '900000000', fulfillment: 'pickup', payment: 'cash', items: [{ productId: 'papa', quantity: 1 }], expected: { saleKey: saleKey(app.state.settings), pickup: app.state.settings.pickup, subtotal: 6.5, fee: 0, total: 6.5 } });
  return { ...app, dataDir, call, request, clock: value => { clock = time(value); } };
}

test('dos compradores disputan el último cupo y los reintentos no duplican', async t => {
  const app = await fixture(t); app.state.products[0].stock = 1;
  const requests = [app.request(), app.request()];
  const results = await Promise.all(requests.map(body => app.call('/api/orders', body)));
  assert.deepEqual(results.map(result => result.status).sort(), [201, 409]);
  assert.equal(app.state.products[0].stock, 0); assert.equal(app.state.orders.length, 1);
  const index = results.findIndex(result => result.status === 201), accepted = results[index];
  const again = await app.call('/api/orders', requests[index]);
  assert.equal(again.status, 200); assert.equal(again.data.order.id, accepted.data.order.id); assert.equal(app.state.orders.length, 1);
  assert.equal((await app.call('/api/orders', { ...requests[index], customer: 'Otro contenido' })).status, 409);
  assert.equal((await app.call('/api/orders/' + accepted.data.order.id, null, false, accepted.data.token)).status, 200);
  assert.equal(accepted.data.order.requestHash, undefined);
});

test('fecha, precios y catálogo requieren una revisión explícita', async t => {
  const app = await fixture(t); const before = app.state.products[0].stock;
  assert.equal((await app.call('/api/orders', { ...app.request(), expected: { ...app.request().expected, total: 5 } })).status, 409);
  assert.equal((await app.call('/api/orders', { ...app.request(), expected: { ...app.request().expected, saleKey: 'otra-fecha' } })).status, 409);
  assert.equal((await app.call('/api/orders', { ...app.request(), storeId: randomUUID() })).status, 409);
  assert.equal((await app.call('/api/orders', { ...app.request(), items: [{ productId: 'papa', quantity: 1, price: 0 }] })).status, 409);
  assert.equal(app.state.products[0].stock, before); assert.equal(app.state.orders.length, 0);
  const cached = await app.call(`/api/catalog?since=${app.state.revision}&storeId=${app.state.storeId}`);
  assert.equal(cached.data.notModified, true); assert.equal(cached.data.products, undefined);
  assert.equal((await app.call(`/api/catalog?since=${app.state.revision}&storeId=otro`)).data.notModified, undefined);
});

test('pedido manual y cambios consecutivos conservan versiones, pago y reparto', async t => {
  const app = await fixture(t); app.clock('2026-11-06T16:00');
  assert.equal((await app.call('/api/orders', app.request())).status, 409);
  const input = { ...app.request(), id: randomUUID(), source: 'whatsapp' };
  const creation = { id: randomUUID(), type: 'manualOrder', data: input };
  assert.equal((await app.call('/api/admin/operations', creation)).status, 401);
  assert.equal((await app.call('/api/admin/operations', creation, true)).status, 200);
  assert.equal((await app.call('/api/admin/operations', creation, true)).status, 200);
  assert.equal(app.state.orders.length, 1); assert.equal(app.state.orders[0].id, input.id); assert.equal(app.state.orders[0].source, 'whatsapp');
  for (const data of [{ version: 1, paid: true }, { version: 2, status: 'sale_day' }, { version: 3, status: 'pickup_available' }, { version: 4, status: 'delivered' }]) {
    assert.equal((await app.call('/api/admin/operations', { id: randomUUID(), type: 'order', data: { id: input.id, ...data } }, true)).status, 200);
  }
  assert.equal(app.state.orders[0].status, 'delivered'); assert.equal(app.state.orders[0].paid, true); assert.equal(app.state.orders[0].version, 5);
});

test('asignación previa al reparto y datos visibles solo con acceso al pedido', async t => {
  const app = await fixture(t); const request = { ...app.request(), fulfillment: 'delivery', zoneId: 'cercana', address: 'Pasaje de prueba 10', reference: 'Frente a la bodega', expected: { ...app.request().expected, fee: 3, total: 9.5 } };
  const created = await app.call('/api/orders', request); assert.equal(created.status, 201); const id = created.data.order.id;
  const change = data => app.call('/api/admin/operations', { id: randomUUID(), type: 'order', data: { id, ...data } }, true);
  assert.equal((await change({ version: 1, driver: 'Responsable de prueba', deliveryWindow: 'De 5 a 6 p. m.' })).status, 200);
  app.clock('2026-11-06T16:00'); assert.equal((await change({ version: 2, status: 'sale_day' })).status, 200); assert.equal((await change({ version: 3, status: 'delivering' })).status, 200);
  const received = await app.call('/api/orders/' + id, null, false, created.data.token); assert.equal(received.data.order.deliveryWindow, 'De 5 a 6 p. m.'); assert.equal(received.data.order.status, 'delivering');
  assert.equal((await app.call('/api/orders/' + id)).status, 404); assert.equal((await app.call('/api/catalog')).data.orders, undefined);
});

test('fallo de escritura revierte el cupo y la copia recupera datos sin reiniciar', async t => {
  const app = await fixture(t), input = app.request(), before = app.state.products[0].stock;
  mkdirSync(join(app.dataDir, 'state.json.tmp'));
  assert.equal((await app.call('/api/orders', input)).status, 500); assert.equal(app.state.orders.length, 0); assert.equal(app.state.products[0].stock, before);
  rmdirSync(join(app.dataDir, 'state.json.tmp'));
  assert.equal((await app.call('/api/orders', input)).status, 201);
  assert.equal((await app.call('/api/orders', app.request())).status, 201);
  const backup = JSON.parse(readFileSync(join(app.dataDir, 'state.json.bak'), 'utf8'));
  writeFileSync(join(app.dataDir, 'state.json'), '{invalid');
  const recovered = createApp({ dataDir: app.dataDir });
  assert.equal(recovered.state.orders.length, backup.orders.length); assert.equal(recovered.state.storeId, app.state.storeId);
  assert.ok(readdirSync(app.dataDir).some(name => name.startsWith('state.json.damaged-')));
});

test('recupera una copia existente si falta el archivo principal sin crear otra olla', async t => {
  const app = await fixture(t);const file=join(app.dataDir,'state.json');const backup=JSON.parse(readFileSync(file+'.bak','utf8'));unlinkSync(file);
  assert.equal(createApp({dataDir:app.dataDir}).state.storeId,backup.storeId);
  unlinkSync(file);writeFileSync(file+'.bak','{invalid');
  assert.throws(()=>loadState(file,()=>{throw new Error('No debe crear datos nuevos');}),/JSON|Unexpected|property/i);
});

test('cancelar un pedido anterior no repone cupos de otra jornada', async t => {
  const app = await fixture(t); const created = await app.call('/api/orders', app.request());
  app.state.settings.saleDate = '2026-11-13'; app.state.settings.pickupFrom = '2026-11-13T16:00'; app.state.settings.pickupUntil = '2026-11-13T19:00'; app.state.settings.reserveUntil = '2026-11-12T20:00';
  const before = app.state.products[0].stock;
  assert.equal((await app.call('/api/admin/operations', { id: randomUUID(), type: 'order', data: { id: created.data.order.id, version: 1, status: 'cancelled' } }, true)).status, 200);
  assert.equal(app.state.products[0].stock, before);
});
