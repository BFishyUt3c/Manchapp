import http from 'node:http';
import { readFileSync, writeFileSync, renameSync, mkdirSync, existsSync, createReadStream } from 'node:fs';
import { join, resolve, extname, sep } from 'node:path';
import { randomUUID, randomBytes, timingSafeEqual } from 'node:crypto';
import { networkInterfaces } from 'node:os';
import { fileURLToPath } from 'node:url';
import { seedProducts, seedPromos, seedSettings } from './seed.mjs';

import { deliveryDestination } from './destination.mjs';
import { scheduleError, bookingError, scheduleSnapshot, transitionError } from './schedule.mjs';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const statuses = ['registered', 'confirmed', 'preparing', 'ready', 'sale_day', 'pickup_available', 'delivering', 'delivered', 'cancelled'];
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.ttf': 'font/ttf', '.woff2': 'font/woff2', '.webmanifest': 'application/manifest+json' };
export class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }
const requireThat = (condition, message, status = 400) => { if (!condition) throw new ApiError(status, message); };
const clean = (value, max = 200) => typeof value === 'string' ? value.trim().slice(0, max) : '';
const money = (value) => Math.round(value * 100) / 100;
const imageOk = (value) => !value || (typeof value === 'string' && value.length < 900_000 && /^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(value));
function publicOrder(order) { const { token: _token, requestId: _requestId, ...visible } = order; return visible; }
function authenticate(req, key) {
  const supplied = Buffer.from(req.headers['x-manchapp-key'] || '');
  const expected = Buffer.from(key);
  requireThat(supplied.length === expected.length && timingSafeEqual(supplied, expected), 'Vuelve a vincular la app con la laptop.', 401);
}
async function body(req) {
  let size = 0; const chunks = [];
  for await (const chunk of req) { size += chunk.length; requireThat(size <= 1_200_000, 'El archivo es demasiado grande.', 413); chunks.push(chunk); }
  try { return JSON.parse(Buffer.concat(chunks).toString() || '{}'); } catch { throw new ApiError(400, 'Los datos enviados no son válidos.'); }
}
export function createApp({ dataDir = join(projectRoot, '.data'), staticDir = join(projectRoot, 'dist'), now = () => Date.now() } = {}) {
  mkdirSync(dataDir, { recursive: true });
  const file = join(dataDir, 'state.json');
  const state = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : { products: structuredClone(seedProducts), promos: structuredClone(seedPromos), settings: structuredClone(seedSettings), orders: [], nextNumber: 1, revision: 1, adminKey: randomBytes(18).toString('hex'), operations: [] };
  const persist = () => { writeFileSync(file + '.tmp', JSON.stringify(state, null, 2)); renameSync(file + '.tmp', file); };
  persist();
  const settingsForClient = () => ({ ...state.settings, deliveryFreeFrom: state.settings.deliveryFreeFrom || 0, catalogUrl: state.settings.catalogUrl || `http://${localAddress()}:${server.address()?.port || 4173}` });
  const snapshot = () => ({ products: state.products, promos: state.promos, settings: settingsForClient(), orders: state.orders.map(publicOrder), revision: state.revision });
  const send = (res, code, value) => { res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); res.end(JSON.stringify(value)); };
  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost');
      const path = url.pathname;
      if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }
      if (path === '/api/health') { send(res, 200, { ok: true }); return; }
      if (req.method === 'GET' && path === '/api/catalog') {
        const promos = state.promos.filter(p => p.active).map(p => ({ ...p, price: state.products.find(product => product.id === p.productId)?.price ?? p.price }));
        send(res, 200, { products: state.products, promos, settings: settingsForClient(), revision: state.revision }); return;
      }
      if (req.method === 'POST' && path === '/api/orders') {
        const input = await body(req);
        requireThat(typeof input.requestId === 'string' && /^[\w-]{8,100}$/.test(input.requestId), 'No se pudo identificar el pedido. Intenta nuevamente.');
        const previous = state.orders.find(o => o.requestId === input.requestId);
        if (previous) { send(res, 200, { order: publicOrder(previous), token: previous.token }); return; }
        const closed = bookingError(state.settings, now()); requireThat(!closed, closed, 409);
        requireThat(Array.isArray(input.items) && input.items.length > 0 && input.items.length <= 30, 'Agrega al menos un producto.');
        const customer = clean(input.customer, 80), phone = clean(input.phone, 30);
        requireThat(customer.length >= 2 && /^9\d{8}$/.test(phone), 'Completa tu nombre y un celular peruano de 9 dígitos.');
        requireThat(['pickup', 'delivery'].includes(input.fulfillment), 'Elige recojo o delivery.');
        requireThat(['cash', 'yape', 'plin'].includes(input.payment), 'Elige cómo pagarás.');
        const quantities = new Map();
        for (const item of input.items) { requireThat(typeof item.productId === 'string' && Number.isInteger(item.quantity) && item.quantity > 0 && item.quantity <= 99, 'Revisa las cantidades del pedido.'); quantities.set(item.productId, (quantities.get(item.productId) || 0) + item.quantity); }
        const items = [...quantities].map(([productId, quantity]) => {
          const product = state.products.find(p => p.id === productId);
          requireThat(product && product.line === 'commercial' && product.available && product.stock >= quantity, 'Un producto ya no tiene esa cantidad disponible. Actualiza tu pedido.', 409);
          return { productId, name: product.name, price: product.price, quantity };
        });
        const zone = input.fulfillment === 'delivery' ? state.settings.zones.find(z => z.id === input.zoneId && z.enabled) : null;
        const destination = input.fulfillment === 'delivery' ? deliveryDestination(input, state.settings.saleDate) : { address: '', reference: '' };
        requireThat(!destination.error, destination.error);
        const { address, reference } = destination;
        if (input.fulfillment === 'delivery') requireThat(zone && address.length >= 4 && reference.length >= 3, 'Completa sector, dirección y una referencia para la entrega.');
        const subtotal = money(items.reduce((sum, item) => sum + item.price * item.quantity, 0));
        const freeFrom = state.settings.deliveryFreeFrom || 0;
        const deliveryOffer = Boolean(zone && freeFrom > 0 && subtotal >= freeFrom);
        const fee = zone && !deliveryOffer ? zone.fee : 0;
        const order = { id: randomUUID(), number: state.nextNumber++, requestId: input.requestId, token: randomBytes(20).toString('hex'), customer, phone, items, subtotal, fee, deliveryOffer, total: money(subtotal + fee), fulfillment: input.fulfillment, zone: zone?.name || '', address: zone ? address : '', reference: zone ? reference : '', payment: input.payment, paid: false, status: 'registered', driver: '', createdAt: new Date().toISOString(), version: 1 };
        Object.assign(order, scheduleSnapshot(state.settings));
        if (destination.deliveryLocation) order.deliveryLocation = destination.deliveryLocation;
        for (const item of items) { const p = state.products.find(p => p.id === item.productId); p.stock -= item.quantity; p.version++; }
        state.orders.unshift(order); state.revision++; persist(); send(res, 201, { order: publicOrder(order), token: order.token }); return;
      }
      if (req.method === 'GET' && /^\/api\/orders\/[^/]+$/.test(path)) {
        const order = state.orders.find(o => o.id === path.split('/').pop());
        requireThat(order && req.headers['x-order-token'] === order.token, 'No se encontró el pedido en este dispositivo.', 404);
        send(res, 200, { order: publicOrder(order) }); return;
      }
      if (path.startsWith('/api/admin/')) {
        authenticate(req, state.adminKey);
        if (req.method === 'GET' && path === '/api/admin/state') { send(res, 200, snapshot()); return; }
        if (req.method === 'POST' && path === '/api/admin/operations') {
          const input = await body(req);
          requireThat(typeof input.id === 'string' && input.id.length < 100, 'Operación no válida.');
          if (state.operations.includes(input.id)) { send(res, 200, snapshot()); return; }
          const data = input.data || {};
          if (input.type === 'product') {
            const old = state.products.find(p => p.id === data.id);
            requireThat(typeof data.id === 'string' && /^[\w-]{1,100}$/.test(data.id), 'Producto no válido.');
            requireThat(clean(data.name, 80).length >= 2 && Number.isFinite(data.price) && data.price > 0 && data.price <= 10000, 'Revisa el nombre y el precio del producto.');
            requireThat(Number.isInteger(data.stock) && data.stock >= 0 && data.stock <= 10000 && ['social', 'commercial'].includes(data.line), 'Revisa la cantidad y el tipo de producto.');
            requireThat(imageOk(data.image), 'Usa una foto JPG, PNG o WebP de menor tamaño.');
            requireThat(data.saleUnit === undefined || ['unit', 'portion', 'combo', 'cup'].includes(data.saleUnit), 'Revisa si vendes por unidad, porción, combo o taza.');
            requireThat(!old || data.version === old.version, 'Este producto cambió mientras estabas sin conexión. Conservamos tu cambio pendiente; revisa la cantidad disponible antes de reenviarlo.', 409);
            const product = { id: data.id, name: clean(data.name, 80), description: clean(data.description, 400), saleUnit: data.saleUnit || old?.saleUnit || 'unit', portion: clean(data.portion ?? old?.portion, 120), price: money(data.price), stock: data.stock, category: clean(data.category, 30), line: data.line, hours: clean(data.hours, 100), available: Boolean(data.available), image: data.image || '', imageReference: Boolean(data.imageReference), illustration: ['papa','causa','cachanga','mazamorra','cafe','social'].includes(data.illustration) ? data.illustration : old?.illustration || 'social', version: (old?.version || 0) + 1 };
            if (old) state.products[state.products.indexOf(old)] = product; else state.products.push(product);
          } else if (input.type === 'order') {
            const order = state.orders.find(o => o.id === data.id);
            requireThat(order, 'No se encontró ese pedido.', 404);
            requireThat(data.version === order.version, 'Este pedido cambió. Actualiza y revisa su estado antes de continuar.', 409);
            const target = data.status || order.status;
            requireThat(statuses.includes(target), 'Estado de pedido no válido.');
            const next = { registered: ['confirmed', 'sale_day', 'cancelled'], confirmed: ['sale_day', 'cancelled'], preparing: ['sale_day', 'cancelled'], ready: order.fulfillment === 'delivery' ? ['delivering', 'cancelled'] : ['pickup_available', 'cancelled'], sale_day: order.fulfillment === 'delivery' ? ['delivering', 'cancelled'] : ['pickup_available', 'cancelled'], pickup_available: ['delivered', 'cancelled'], delivering: ['delivered'], delivered: [], cancelled: [] };
            let dates;
            if (Object.hasOwn(data, 'saleDate')) {
              requireThat(!['delivering', 'delivered', 'cancelled', 'pickup_available'].includes(order.status), 'No cambies la fecha después de iniciar la entrega.', 409);
              dates = { ...scheduleSnapshot(order), ...scheduleSnapshot(data) }; const invalid = scheduleError(dates); requireThat(!invalid, invalid);
            }
            if (target !== order.status) { const invalid = transitionError({ ...order, ...dates }, target, now()); requireThat(!invalid, invalid, 409); }
            requireThat(target === order.status || next[order.status].includes(target), 'Ese cambio no corresponde al estado del pedido.', 409);
            if (target === 'delivering') requireThat(clean(data.driver, 80).length >= 2, 'Anota quién hará el reparto.');
            if (target === 'cancelled' && order.status !== 'cancelled') for (const item of order.items) { const p = state.products.find(p => p.id === item.productId); if (p) { p.stock += item.quantity; p.version++; } }
            if (dates) Object.assign(order, dates);
            order.status = target; order.paid = typeof data.paid === 'boolean' ? data.paid : order.paid; order.driver = clean(data.driver ?? order.driver, 80); order.version++;
          } else if (input.type === 'promo') {
            requireThat(clean(data.title, 80).length >= 3 && clean(data.description, 250).length >= 3 && Number.isFinite(data.price) && data.price > 0, 'Completa título, contenido y precio de la oferta.');
            const promo = { id: clean(data.id, 100) || randomUUID(), title: clean(data.title, 80), description: clean(data.description, 250), price: money(data.price), productId: clean(data.productId, 100), hours: clean(data.hours, 100), active: Boolean(data.active) };
            const index = state.promos.findIndex(p => p.id === promo.id); if (index < 0) state.promos.unshift(promo); else state.promos[index] = promo;
          } else if (input.type === 'settings') {
            requireThat(clean(data.name, 80).length >= 2 && clean(data.pickup, 200).length >= 4, 'Completa nombre y lugar de recojo.');
            requireThat(!data.phone || /^9\d{8}$/.test(data.phone), 'El contacto debe tener 9 dígitos y comenzar con 9.');
            requireThat(imageOk(data.paymentImage), 'El QR debe ser una imagen de menor tamaño.');
            const deliveryFreeFrom = data.deliveryFreeFrom ?? state.settings.deliveryFreeFrom ?? 0;
            requireThat(Number.isFinite(deliveryFreeFrom) && deliveryFreeFrom >= 0 && deliveryFreeFrom <= 10000, 'Revisa el monto mínimo para el envío gratis.');
            const catalogUrl = clean(data.catalogUrl, 300);
            if (catalogUrl) { let parsed; try { parsed = new URL(catalogUrl); } catch { throw new ApiError(400, 'Revisa el enlace de tu catálogo.'); } requireThat(['http:', 'https:'].includes(parsed.protocol) && !parsed.username && !parsed.password, 'Usa un enlace http o https para el catálogo.'); }
            requireThat(Array.isArray(data.zones) && data.zones.length <= 10, 'Revisa los sectores de entrega.');
            const zones = data.zones.map(z => { requireThat(clean(z.id, 60) && clean(z.name, 80) && Number.isFinite(z.fee) && z.fee >= 0 && z.fee < 500, 'Revisa nombre y tarifa de cada sector.'); return { id: clean(z.id, 60), name: clean(z.name, 80), fee: money(z.fee), note: clean(z.note, 240), enabled: Boolean(z.enabled) }; });
            const schedule = { ...scheduleSnapshot(state.settings), ...scheduleSnapshot(data) }; if (schedule.saleDate) { const invalid = scheduleError(schedule); requireThat(!invalid, invalid); }
            state.settings = { ...state.settings, ...schedule, name: clean(data.name, 80), pickup: clean(data.pickup, 200), phone: data.phone || '', hours: clean(data.hours, 120), paymentImage: data.paymentImage || '', deliveryFreeFrom: money(deliveryFreeFrom), catalogUrl, zones };
          } else throw new ApiError(400, 'Acción no reconocida.');
          state.operations.push(input.id); state.operations = state.operations.slice(-2000); state.revision++; persist(); send(res, 200, snapshot()); return;
        }
      }
      if (path.startsWith('/api/')) throw new ApiError(404, 'No se encontró esa opción.');
      requireThat(req.method === 'GET' || req.method === 'HEAD', 'Método no permitido.', 405);
      const base = resolve(staticDir);
      let target = resolve(base, '.' + decodeURIComponent(path));
      requireThat(target === base || target.startsWith(base + sep), 'Ruta no válida.', 403);
      if (path === '/' || !extname(path)) target = join(base, 'index.html');
      if (!existsSync(target)) throw new ApiError(404, 'Archivo no encontrado.');
      res.writeHead(200, { 'Content-Type': mime[extname(target)] || 'application/octet-stream', 'Cache-Control': extname(target) === '.html' ? 'no-cache' : 'public, max-age=3600', 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'same-origin' });
      if (req.method === 'HEAD') res.end(); else createReadStream(target).on('error', () => res.destroy()).pipe(res);
    } catch (error) { if (!res.headersSent) send(res, error.status || 500, { error: error.status ? error.message : 'No se pudo completar la acción. Intenta nuevamente.' }); else res.destroy(); }
  });
  return { server, state };
}
export function localAddress() {
  const candidates = Object.entries(networkInterfaces()).flatMap(([name, entries]) => (entries || []).filter(entry => entry.family === 'IPv4' && !entry.internal && !entry.address.startsWith('169.254.')).map(entry => ({ address: entry.address, score: (/wi-fi|wlan|ethernet/i.test(name) ? 4 : 0) + (entry.address.startsWith('192.168.') ? 3 : 0) - (/virtual|vethernet|vpn|wsl/i.test(name) ? 10 : 0) })));
  return candidates.sort((a, b) => b.score - a.score)[0]?.address || '127.0.0.1';
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 4173);
  const app = createApp();
  app.server.listen(port, '0.0.0.0', () => { console.log(`ManchApp · http://localhost:${port}\nCelular en la misma Wi-Fi: http://${localAddress()}:${port}\nVinculación Android: npm run android:pair`); });
}
