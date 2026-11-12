import { randomUUID, randomBytes, createHash } from 'node:crypto';
import { bookingError, scheduleError, scheduleSnapshot, time } from './schedule.mjs';
import { deliveryDestination } from './destination.mjs';

const requireThat = (condition, message, status = 400) => { if (!condition) throw Object.assign(new Error(message), { status }); };
const clean = (value, max = 200) => typeof value === 'string' ? value.trim().slice(0, max) : '';
const money = value => Math.round(value * 100) / 100;
const canonical = value => Array.isArray(value) ? value.map(canonical) : value && typeof value === 'object' ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
export const saleKey = settings => `${settings.saleDate || ''}/${settings.pickupFrom || ''}/${settings.pickupUntil || ''}`;

export function createBooking(state, input, now, manual = false) {
  requireThat(typeof input.requestId === 'string' && /^[\w-]{8,100}$/.test(input.requestId), 'No se pudo identificar el pedido. Intenta nuevamente.');
  const requestHash = createHash('sha256').update(JSON.stringify(canonical(Object.fromEntries(['customer', 'phone', 'fulfillment', 'payment', 'items', 'zoneId', 'address', 'reference', 'meeting', 'deliveryLocation', 'expected', 'storeId'].filter(key => Object.hasOwn(input, key)).map(key => [key, input[key]]))))).digest('hex');
  const previous = state.orders.find(order => order.requestId === input.requestId);
  if (previous) {
    requireThat(!previous.requestHash || previous.requestHash === requestHash, 'Esta solicitud ya fue enviada con otros datos. Revisa tu pedido.', 409);
    return { order: previous, reused: true };
  }
  requireThat(!input.storeId || input.storeId === state.storeId, 'Esta solicitud pertenece a otro catálogo. Vuelve a revisar los productos.', 409);
  if (manual) {
    requireThat(!scheduleError(state.settings), 'Programa primero una jornada válida.', 409);
    requireThat(state.settings.salesOpen && now <= time(state.settings.pickupUntil), 'La jornada está pausada o terminó. Revisa la fecha de venta.', 409);
  } else { const closed = bookingError(state.settings, now); requireThat(!closed, closed, 409); }
  requireThat(Array.isArray(input.items) && input.items.length > 0 && input.items.length <= 30, 'Agrega entre uno y treinta productos.');
  const customer = clean(input.customer, 80), phone = clean(input.phone, 30);
  requireThat(customer.length >= 2 && /^9\d{8}$/.test(phone), 'Completa el nombre y un celular peruano de 9 dígitos.');
  requireThat(['pickup', 'delivery'].includes(input.fulfillment), 'Elige recojo o delivery.');
  requireThat(['cash', 'yape', 'plin'].includes(input.payment), 'Elige cómo pagarás.');
  const quantities = new Map();
  for (const item of input.items) {
    requireThat(typeof item.productId === 'string' && Number.isInteger(item.quantity) && item.quantity > 0 && item.quantity <= 99, 'Revisa las cantidades; máximo 99 de cada producto.');
    quantities.set(item.productId, (quantities.get(item.productId) || 0) + item.quantity);
  }
  const items = [...quantities].map(([productId, quantity]) => {
    const product = state.products.find(value => value.id === productId);
    requireThat(quantity <= 99 && product && product.line === 'commercial' && product.available && product.stock >= quantity, 'Cambió la cantidad disponible. Revisa los productos de tu solicitud.', 409);
    return { productId, name: product.name, price: product.price, quantity };
  });
  const zone = input.fulfillment === 'delivery' ? state.settings.zones.find(value => value.id === input.zoneId && value.enabled) : null;
  const destination = input.fulfillment === 'delivery' ? deliveryDestination(input, state.settings.saleDate) : { address: '', reference: '' };
  requireThat(!destination.error, destination.error);
  if (input.fulfillment === 'delivery') requireThat(zone && destination.address.length >= 4 && destination.reference.length >= 3, 'Completa sector, dirección y referencias para la entrega.');
  const subtotal = money(items.reduce((sum, item) => sum + item.price * item.quantity, 0));
  const deliveryOffer = Boolean(zone && state.settings.deliveryFreeFrom > 0 && subtotal >= state.settings.deliveryFreeFrom);
  const fee = zone && !deliveryOffer ? zone.fee : 0, total = money(subtotal + fee);
  if (input.expected) {
    const quote = input.expected;
    requireThat(input.items.every(item => { const current = items.find(value => value.productId === item.productId); return (item.price === undefined || item.price === current.price) && (item.name === undefined || item.name === current.name); }), 'Cambió un producto o su precio. Revisa los datos antes de enviar otra vez.', 409);
    requireThat(quote.saleKey === saleKey(state.settings) && (!quote.pickup || quote.pickup === state.settings.pickup), 'La fecha, el horario o el lugar de entrega cambió. Revisa antes de enviar otra vez.', 409);
    requireThat([['subtotal', subtotal], ['fee', fee], ['total', total]].every(([key, value]) => typeof quote[key] === 'number' && Math.abs(quote[key] - value) < .005), 'El precio o la tarifa cambió. Revisa el nuevo total antes de confirmar.', 409);
  }
  const id = manual ? input.id : randomUUID();
  requireThat(typeof id === 'string' && /^[\w-]{8,100}$/.test(id) && !state.orders.some(order => order.id === id), 'El identificador del pedido no es válido.', 409);
  const order = {
    id, number: state.nextNumber++, requestId: input.requestId, requestHash, token: randomBytes(20).toString('hex'),
    customer, phone, items, subtotal, fee, deliveryOffer, total, fulfillment: input.fulfillment,
    zoneId: zone?.id || '', zone: zone?.name || '', address: zone ? destination.address : '', reference: zone ? destination.reference : '',
    payment: input.payment, paid: false, status: 'registered', driver: '', deliveryWindow: '',
    source: manual ? ['whatsapp', 'presencial', 'telefono'].includes(input.source) ? input.source : 'presencial' : 'app',
    createdAt: new Date(now).toISOString(), version: 1,
    ...scheduleSnapshot(state.settings),
  };
  if (destination.deliveryLocation) order.deliveryLocation = structuredClone(destination.deliveryLocation);
  for (const item of items) { const product = state.products.find(value => value.id === item.productId); product.stock -= item.quantity; product.version++; }
  state.orders.unshift(order); state.revision++;
  return { order, reused: false };
}
