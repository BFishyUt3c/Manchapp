export function deliveryDestination(input, saleDate) {
  const address = typeof input.address === 'string' ? input.address.trim().slice(0, 240) : '';
  let reference = typeof input.reference === 'string' ? input.reference.trim() : '';
  const meeting = typeof input.meeting === 'string' ? input.meeting.trim() : '';
  if (meeting && !reference.includes(meeting)) reference += `${reference ? '\n' : ''}${meeting}`;
  reference = reference.slice(0, 240);
  const result = { address, reference };
  if (!Object.hasOwn(input, 'deliveryLocation')) return result;
  const p = input.deliveryLocation;
  if (!p || !['latitude', 'longitude', 'accuracy', 'capturedAt'].every(k => typeof p[k] === 'number' && Number.isFinite(p[k])) || p.latitude < -90 || p.latitude > 90 || p.longitude < -180 || p.longitude > 180 || p.accuracy <= 0 || p.accuracy > 10000 || p.capturedAt <= 0) return { ...result, error: 'La ubicación no es válida. Quítala o vuelve a obtenerla.' };
  if (!saleDate || p.saleDate !== saleDate || p.address !== address) return { ...result, error: 'Confirma que el punto corresponde a esta dirección y fecha de entrega.' };
  return { ...result, deliveryLocation: { latitude: p.latitude, longitude: p.longitude, accuracy: p.accuracy, capturedAt: p.capturedAt, saleDate, address } };
}
