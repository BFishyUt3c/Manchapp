import { useEffect, useRef, useState, type FormEvent, type InputHTMLAttributes } from 'react';
import { ArrowLeft, ArrowRight, Check, MapPin, Minus, Plus, ShoppingBag, Truck } from 'lucide-react';
import type { Catalog, Product } from './types';

const money = (value: number) => new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(value);
type Props = {
  catalog: Catalog;
  cart: Record<string, number>;
  onChange: (product: Product, delta: number) => void;
  onBack: () => void;
  onSubmit: (details: Record<string, unknown>) => Promise<void>;
  offline: boolean;
};

export function Quantity({ name, value, max, onChange }: { name: string; value: number; max: number; onChange: (delta: number) => void }) {
  return <div className="quantity">
    <button type="button" aria-label={`Quitar uno de ${name}`} onClick={() => onChange(-1)}><Minus size={20} /></button>
    <span aria-live="polite">{value}</span>
    <button type="button" disabled={value >= max} aria-label={`Agregar uno de ${name}`} onClick={() => onChange(1)}><Plus size={20} /></button>
  </div>;
}

export default function Checkout({ catalog, cart, onChange, onBack, onSubmit, offline }: Props) {
  const [step, setStep] = useState(0);
  const [fulfillment, setFulfillment] = useState('pickup');
  const [zoneId, setZoneId] = useState('');
  const [payment, setPayment] = useState('cash');
  const [details, setDetails] = useState({ customer: '', phone: '', address: '', reference: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [errorField, setErrorField] = useState('');
  const heading = useRef<HTMLHeadingElement>(null);
  const form = useRef<HTMLFormElement>(null);
  const { settings, products } = catalog;
  const selected = products.filter(product => cart[product.id] > 0 && product.line === 'commercial');
  const subtotal = selected.reduce((sum, product) => sum + product.price * cart[product.id], 0);
  const zone = settings.zones.find(item => item.id === zoneId && item.enabled);
  const valid = selected.every(product => product.available && product.stock >= cart[product.id]);
  const freeDelivery = fulfillment === 'delivery' && (settings.deliveryFreeFrom || 0) > 0 && subtotal >= (settings.deliveryFreeFrom || 0);
  const total = subtotal + (fulfillment === 'delivery' && !freeDelivery ? zone?.fee || 0 : 0);
  const steps = ['Comida y entrega', 'Tus datos', 'Revisar y enviar'];

  useEffect(() => { heading.current?.focus({ preventScroll: true }); heading.current?.scrollIntoView({ block: 'start' }); }, [step]);
  const changeField = (name: keyof typeof details, value: string) => { setDetails(current => ({ ...current, [name]: value })); setError(''); };
  const problem = (message: string, field?: string) => {
    setError(message);
    setErrorField(field || '');
    if (field) form.current?.querySelector<HTMLInputElement | HTMLSelectElement>(`[name="${field}"]`)?.focus();
  };
  const fieldHelp = (name: string) => error && errorField === name ? <span id={`error-${name}`} className="field-error" role="alert">{error}</span> : null;
  const fieldAccessibility = (name: string) => ({ 'aria-invalid': error && errorField === name ? true : undefined, 'aria-describedby': error && errorField === name ? `error-${name}` : undefined });
  const inputField = (name: keyof typeof details, label: string, placeholder: string, options: InputHTMLAttributes<HTMLInputElement>) => <div className="checkout-field"><label htmlFor={`order-${name}`}>{label}</label><input id={`order-${name}`} name={name} required {...fieldAccessibility(name)} value={details[name]} onChange={event => changeField(name, event.target.value)} placeholder={placeholder} {...options} />{fieldHelp(name)}</div>;
  const deliveryValid = () => {
    if (!valid) { problem('Cambió la cantidad disponible. Revisa los productos antes de continuar.'); return false; }
    if (fulfillment === 'delivery') {
      if (!zone) { problem('Elige el sector donde recibirás tu pedido.', 'zone'); return false; }
      if (details.address.trim().length < 4) { problem('Escribe tu dirección o un punto de encuentro.', 'address'); return false; }
      if (details.reference.trim().length < 3) { problem('Agrega una referencia. Por ejemplo: frente a la bodega.', 'reference'); return false; }
    }
    return true;
  };
  const contactValid = () => {
    if (details.customer.trim().length < 2) { problem('Escribe tu nombre para identificar el pedido.', 'customer'); return false; }
    if (!/^9[0-9]{8}$/.test(details.phone.trim())) { problem('Escribe los 9 números de tu celular, empezando con 9.', 'phone'); return false; }
    return true;
  };
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (busy) return;
    setError('');
    if (step === 0) { if (deliveryValid()) setStep(1); return; }
    if (step === 1) { if (contactValid()) setStep(2); return; }
    if (!valid || (fulfillment === 'delivery' && !zone)) { setStep(0); problem('Revisa la disponibilidad y el sector de entrega antes de enviar.'); return; }
    if (offline) { problem('Necesitas conexión para enviar. Tu selección sigue guardada.'); return; }
    setBusy(true);
    try {
      await onSubmit({ ...details, fulfillment, zoneId, payment });
    } catch (cause) {
      problem(cause instanceof Error ? cause.message : 'No pudimos enviar el pedido. Tus datos siguen aquí; vuelve a intentar.');
    } finally { setBusy(false); }
  };

  return <main className="checkout-page">
    <button className="back-button" disabled={busy} onClick={() => { if (step === 0) onBack(); else { setStep(current => current - 1); setError(''); } }}><ArrowLeft size={20} />{step === 0 ? 'Volver a la comida' : 'Volver al paso anterior'}</button>
    <p className="eyebrow">PASO {step + 1} DE 3</p>
    <h1 ref={heading} tabIndex={-1}>{steps[step]}</h1>
    <ol className="checkout-progress" aria-label="Pasos del pedido">
      {steps.map((label, index) => <li key={label} aria-current={step === index ? 'step' : undefined} className={index <= step ? 'current' : ''}><span aria-hidden="true">{index < step ? <Check size={17} /> : index + 1}</span>{label}</li>)}
    </ol>
    {!selected.length ? <div className="empty-state"><ShoppingBag size={36} /><h2>Aún no agregaste productos</h2><button className="primary" onClick={onBack}>Ver la comida</button></div> : <form ref={form} onSubmit={submit} noValidate>
      <div className="checkout-layout">
        <div className="checkout-sections">
          {step === 0 && <>
            <section className="checkout-section"><h2>¿Está bien tu pedido?</h2><p className="step-help">Usa + para agregar una unidad y − para quitarla.</p>
              {selected.map(product => <div className="checkout-item" key={product.id}>
                <img src={product.image || `/illustrations/${product.illustration}.svg`} alt={product.name} />
                <div><strong>{product.name}</strong><small>{money(product.price)} por unidad</small>{(!product.available || product.stock < cart[product.id]) && <small className="field-error">Quedan {product.available ? product.stock : 0}. Ajusta la cantidad.</small>}</div>
                <Quantity name={product.name} value={cart[product.id]} max={product.available ? product.stock : 0} onChange={delta => onChange(product, delta)} />
              </div>)}
            </section>
            <section className="checkout-section"><h2>¿Cómo lo recibirás?</h2>{(settings.deliveryFreeFrom || 0) > 0 && <p className="delivery-promo">Envío gratis desde {money(settings.deliveryFreeFrom!)} en los sectores habilitados.</p>}<div className="choice-grid">
              <button type="button" aria-pressed={fulfillment === 'pickup'} className={fulfillment === 'pickup' ? 'choice selected' : 'choice'} onClick={() => { setFulfillment('pickup'); setError(''); }}><MapPin size={25} /><strong>Voy a recoger</strong><small>Sin costo de entrega</small></button>
              <button type="button" disabled={!settings.zones.some(item => item.enabled)} aria-pressed={fulfillment === 'delivery'} className={fulfillment === 'delivery' ? 'choice selected' : 'choice'} onClick={() => { setFulfillment('delivery'); setError(''); }}><Truck size={25} /><strong>Enviar a mi domicilio</strong><small>Consulta los sectores disponibles</small></button>
            </div>
              {fulfillment === 'pickup' ? <div className="pickup-note"><MapPin size={21} /><p>{settings.pickup}<small>Espera la confirmación antes de acercarte.</small></p></div> : <div className="form-stack">
                <div className="checkout-field"><label htmlFor="order-zone">¿En qué sector estás?</label><select id="order-zone" name="zone" required {...fieldAccessibility('zone')} value={zoneId} onChange={event => { setZoneId(event.target.value); setError(''); }}><option value="">Elige tu sector</option>{settings.zones.filter(item => item.enabled).map(item => <option key={item.id} value={item.id}>{item.name} · {money(item.fee)}</option>)}</select>{fieldHelp('zone')}</div>
                {zone && <p className="help-text">{zone.note}</p>}
                {inputField('address', 'Dirección o punto de encuentro', 'Ejemplo: pasaje Las Flores, casa 12', { maxLength: 240, autoComplete: 'street-address' })}
                {inputField('reference', 'Una referencia para encontrarte', 'Ejemplo: frente a la bodega, al pie de las escaleras', { maxLength: 240 })}
                <p className="help-text">La olla confirmará si puede llegar hasta ese punto.</p>
              </div>}
              {!settings.zones.some(item => item.enabled) && <p className="help-text">Por ahora los pedidos son para recoger.</p>}
            </section>
          </>}
          {step === 1 && <>
            <section className="checkout-section"><h2>¿A nombre de quién?</h2><p className="step-help">Solo necesitamos estos datos para coordinar tu pedido.</p><div className="form-row">
              {inputField('customer', 'Tu nombre', 'Escribe tu nombre', { maxLength: 80, autoComplete: 'given-name' })}
              {inputField('phone', 'Tu celular', '9 números, empieza con 9', { inputMode: 'tel', type: 'tel', maxLength: 9, autoComplete: 'tel-national' })}
            </div></section>
            <section className="checkout-section"><h2>¿Cómo pagarás?</h2><div className="payment-options">{[['cash', 'Efectivo'], ['yape', 'Yape'], ['plin', 'Plin']].map(([id, label]) => <button type="button" key={id} aria-pressed={payment === id} className={payment === id ? 'selected' : ''} onClick={() => setPayment(id)}>{payment === id && <Check size={19} />}{label}</button>)}</div><p className="help-text">Elegir una opción no realiza ningún cobro. Espera que la olla confirme tu pedido antes de pagar.</p></section>
          </>}
          {step === 2 && <section className="checkout-section review-order"><h2>Revisa antes de enviar</h2><p className="step-help">Todavía puedes volver y cambiar los datos.</p>
            {selected.map(product => <div className="summary-line" key={product.id}><span>{cart[product.id]} × {product.name}</span><strong>{money(product.price * cart[product.id])}</strong></div>)}
            <dl><dt>A nombre de</dt><dd>{details.customer} · {details.phone}</dd><dt>{fulfillment === 'pickup' ? 'Recoger en' : 'Enviar a'}</dt><dd>{fulfillment === 'pickup' ? settings.pickup : `${zone?.name} · ${details.address}`}<br />{fulfillment === 'delivery' && `Referencia: ${details.reference}`}</dd><dt>Forma de pago</dt><dd>{payment === 'cash' ? 'Efectivo' : payment === 'yape' ? 'Yape' : 'Plin'}</dd></dl>
            <p className="pickup-note">Después de enviar, espera la confirmación de la olla.</p>
          </section>}
        </div>
        <aside className="checkout-summary"><div className="aside-card"><h2>Total de tu pedido</h2><div className="summary-line"><span>Comida</span><strong>{money(subtotal)}</strong></div><div className="summary-line"><span>Entrega</span><strong>{fulfillment === 'pickup' ? 'Recojo sin costo' : zone ? freeDelivery ? 'Gratis por promoción' : money(zone.fee) : 'Elige un sector'}</strong></div><div className="summary-line total"><span>Total</span><strong>{fulfillment === 'delivery' && !zone ? 'Por calcular' : money(total)}</strong></div>
          {offline && <p className="field-error" role="status">Sin conexión. Puedes revisar tu pedido y enviarlo cuando vuelva.</p>}
          {error && !errorField && <p className="field-error" role="alert">{error}</p>}
          <button className="primary full" type="submit" disabled={busy || (step === 2 && offline)}>{busy ? 'Enviando tu pedido…' : step === 0 ? 'Continuar con mis datos' : step === 1 ? 'Revisar mi pedido' : 'Enviar mi pedido'}<ArrowRight size={20} /></button>
          <p className="help-text">{step === 2 ? 'Tus datos se usarán para coordinar la atención.' : 'Continuar todavía no envía el pedido.'}</p>
        </div></aside>
      </div>
    </form>}
  </main>;
}
