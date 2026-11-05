export const seedProducts = [
  { id: 'papa', name: 'Papa rellena', description: 'Papa doradita, relleno de carne y salsa criolla.', price: 6.5, stock: 20, category: 'Salados', line: 'commercial', hours: 'Desde las 12:00 p. m.', available: true, image: '', illustration: 'papa', version: 1 },
  { id: 'causa', name: 'Causa de pollo', description: 'Una porción fresca para tu almuerzo.', price: 7, stock: 12, category: 'Salados', line: 'commercial', hours: 'Desde las 12:00 p. m.', available: true, image: '', illustration: 'causa', version: 1 },
  { id: 'cachanga', name: 'Cachanga', description: 'Recién hecha para acompañar la tarde.', price: 3, stock: 30, category: 'Antojitos', line: 'commercial', hours: 'Desde las 4:00 p. m.', available: true, image: '', illustration: 'cachanga', version: 1 },
  { id: 'mazamorra', name: 'Mazamorra morada', description: 'El dulce de siempre, servido por porción.', price: 4.5, stock: 15, category: 'Postres', line: 'commercial', hours: 'Desde las 4:00 p. m.', available: true, image: '', illustration: 'mazamorra', version: 1 },
  { id: 'cafe', name: 'Café caliente', description: 'Una taza para acompañar tus antojitos.', price: 2, stock: 25, category: 'Bebidas', line: 'commercial', hours: 'Desde las 4:00 p. m.', available: true, image: '', illustration: 'cafe', version: 1 },
  { id: 'merienda-combo', name: 'Combo de la tarde', description: '3 cachangas + 2 cafés. Reserva para la fecha de venta de la olla.', price: 7, stock: 8, category: 'Combos', line: 'commercial', hours: 'Desde las 4:00 p. m.', available: true, image: '', illustration: 'cachanga', version: 1 },
  { id: 'social', name: 'Menú social del día', description: 'Consulta el menú y la atención con la directiva. Reservado para personas empadronadas.', price: 3, stock: 52, category: 'Menú social', line: 'social', hours: 'Lunes a viernes · consultar horario', available: true, image: '', illustration: 'social', version: 1 },
];
const examplePortions = { papa: 'Una unidad con salsa criolla', causa: 'Una porción individual', cachanga: 'Una unidad', mazamorra: 'Una porción individual', cafe: 'Una taza', 'merienda-combo': '3 cachangas + 2 cafés', social: 'Una ración de atención social' };
for (const product of seedProducts) {
  product.saleUnit = product.category === 'Combos' ? 'combo' : ['Postres', 'Menú social'].includes(product.category) ? 'portion' : product.category === 'Bebidas' ? 'cup' : 'unit';
  product.portion = examplePortions[product.id] || '';
}
// Initial editable selling day, not a recurring weekly commitment.
const limaDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
const part = type => Number(limaDate.find(p => p.type === type).value);
const sale = new Date(Date.UTC(part('year'), part('month') - 1, part('day')));
sale.setUTCDate(sale.getUTCDate() + ((5 - sale.getUTCDay() + 7) % 7 || 7));
const cutoff = new Date(sale); cutoff.setUTCDate(cutoff.getUTCDate() - 1);
if (Date.now() >= Date.parse(`${cutoff.toISOString().slice(0, 10)}T20:00:00-05:00`)) { sale.setUTCDate(sale.getUTCDate() + 7); cutoff.setUTCDate(cutoff.getUTCDate() + 7); }
const saleDate = sale.toISOString().slice(0, 10);
export const seedSettings = {
  saleDate, pickupFrom: `${saleDate}T16:00`, pickupUntil: `${saleDate}T19:00`, reserveUntil: `${cutoff.toISOString().slice(0, 10)}T20:00`, salesOpen: true,
  name: 'Mujeres Emprendedoras', location: 'Manchay, Pachacámac', pickup: 'Olla Común Mujeres Emprendedoras · Manchay',
  phone: '', hours: 'Ventas en fechas programadas por la olla', paymentImage: '', deliveryFreeFrom: 0, catalogUrl: '',
  zones: [
    { id: 'cercana', name: 'Zona cercana', fee: 3, note: 'Cobertura de ejemplo. La olla confirma el acceso y punto de encuentro.', enabled: true },
    { id: 'intermedia', name: 'Zona intermedia', fee: 5, note: 'Ejemplo pendiente de acordar con quien reparte.', enabled: false },
    { id: 'ampliada', name: 'Zona ampliada', fee: 7, note: 'Ejemplo pendiente de validar cobertura y acceso.', enabled: false },
  ],
  demo: true,
};
export const seedPromos = [{ id: 'merienda', title: 'Una pausa con sabor a casa', description: '3 cachangas + 2 cafés', productId: 'merienda-combo', price: 7, hours: 'Desde las 4:00 p. m.', active: true }];
