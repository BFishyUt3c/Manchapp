const keys = ['saleDate', 'pickupFrom', 'pickupUntil', 'reserveUntil', 'salesOpen'];
export function time(value, day = false) {
  if (typeof value !== 'string' || !(day ? /^\d{4}-\d{2}-\d{2}$/ : /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/).test(value)) return NaN;
  const stamp = day ? `${value}T00:00` : value;
  const result = Date.parse(`${stamp}:00-05:00`);
  return Number.isFinite(result) && new Date(result - 5 * 3600000).toISOString().slice(0, 16) === stamp ? result : NaN;
}
export function scheduleError(s) {
  if (!keys.slice(0, 4).every(k => Number.isFinite(time(s[k], k === 'saleDate'))) || typeof s.salesOpen !== 'boolean') return 'Completa fechas y horas válidas para la venta.';
  if (s.pickupFrom.slice(0, 10) !== s.saleDate) return 'El recojo debe empezar el día de venta.';
  if (time(s.pickupUntil) <= time(s.pickupFrom)) return 'El fin del recojo debe ser posterior al inicio.';
  if (time(s.reserveUntil) > time(s.pickupFrom)) return 'Las reservas deben cerrar antes o al comenzar el recojo.';
  return '';
}
export function bookingError(s, now) {
  if (!s.saleDate) return ''; // Existing service data stays compatible until the owner configures a day.
  if (scheduleError(s)) return 'La olla anunciará su próxima venta.';
  if (!s.salesOpen) return 'Las reservas están pausadas.';
  if (now > time(s.pickupUntil)) return 'Esta jornada de venta terminó.';
  if (now >= time(s.reserveUntil)) return 'Cerraron las reservas para esta venta.';
  return '';
}
export function scheduleSnapshot(s) { return Object.fromEntries([...keys, 'pickup'].filter(k => Object.hasOwn(s, k)).map(k => [k, s[k]])); }
export function transitionError(order, target, now) {
  if (!['sale_day', 'delivering', 'pickup_available'].includes(target)) return '';
  if (!Number.isFinite(time(order.saleDate, true))) return 'Agrega la fecha de venta de este pedido.';
  if (now < time(order.saleDate, true)) return 'Todavía no es el día de venta de este pedido.';
  if (target === 'pickup_available' && (!Number.isFinite(time(order.pickupFrom)) || !Number.isFinite(time(order.pickupUntil)) || now < time(order.pickupFrom) || now > time(order.pickupUntil))) return 'Revisa el intervalo de recojo de este pedido.';
  return '';
}
