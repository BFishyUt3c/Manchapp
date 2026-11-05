import { mkdirSync, writeFileSync, copyFileSync, existsSync } from 'node:fs';
import { seedProducts, seedPromos, seedSettings } from '../server/seed.mjs';
const root = new URL('../android/app/src/main/assets/', import.meta.url);
mkdirSync(root, { recursive: true });
const products = seedProducts.filter(product => product.line === 'commercial');
const productIds = new Set(products.map(product => product.id));
const promos = seedPromos.filter(promo => productIds.has(promo.productId));
writeFileSync(new URL('seed.json', root), JSON.stringify({ products, promos, settings: seedSettings, orders: [], revision: 1, pending: [] }));
mkdirSync(new URL('photos/', root), { recursive: true });
for (const id of ['papa', 'causa', 'cachanga', 'cafe', 'merienda-combo', 'mazamorra']) {
  const photo = new URL(`../public/photos/${id}.png`, import.meta.url);
  if (existsSync(photo)) copyFileSync(photo, new URL(`photos/${id}.png`, root));
}
const font = new URL('../public/fonts/nunito.ttf', import.meta.url);
if (existsSync(font)) {
  for (const name of ['Regular', 'Bold', 'ExtraBold']) {
    const staticFont = new URL(`../public/fonts/Nunito-${name}.ttf`, import.meta.url);
    if (existsSync(staticFont)) copyFileSync(staticFont, new URL(`Nunito-${name}.ttf`, root));
  }
  copyFileSync(new URL('../public/fonts/OFL.txt', import.meta.url), new URL('OFL.txt', root));
}
console.log('Datos iniciales e imágenes locales preparados para Android.');
