import { useEffect, useState, type ReactNode } from 'react';
import {
  BookOpen,
  CalendarCheck,
  Camera,
  CheckCircle,
  CreditCard,
  Image as ImageIcon,
  Loader2,
  Plus,
  QrCode,
  Sparkles,
  Tag,
  TrendingUp,
  UploadCloud,
  WifiOff,
  XCircle
} from 'lucide-react';

type Tab = 'catalog' | 'promos' | 'photos';

export default function App() {
  const [currentTab, setCurrentTab] = useState<Tab>('catalog');
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isOffline, setIsOffline] = useState(false);

  useEffect(() => {
    const updateStatus = () => setIsOffline(!navigator.onLine);
    updateStatus();
    window.addEventListener('online', updateStatus);
    window.addEventListener('offline', updateStatus);

    return () => {
      window.removeEventListener('online', updateStatus);
      window.removeEventListener('offline', updateStatus);
    };
  }, []);

  if (!isLoggedIn) {
    return <LoginScreen onLogin={() => setIsLoggedIn(true)} isOffline={isOffline} />;
  }

  return (
    <div className="min-h-screen bg-gray-50 font-sans text-gray-900 flex justify-center">
      <div className="w-full max-w-md bg-gray-50 min-h-screen relative flex flex-col shadow-none sm:shadow-xl overflow-hidden">
        {isOffline && <OfflineBanner />}

        <div className="flex-1 overflow-y-auto pb-20">
          {currentTab === 'catalog' && <CatalogScreen />}
          {currentTab === 'promos' && <PromosScreen />}
          {currentTab === 'photos' && <PhotoGuide />}
        </div>

        <nav className="absolute bottom-0 w-full bg-white border-t border-gray-200 flex justify-around items-center h-16 px-2 z-50">
          <NavItem
            icon={<BookOpen />}
            label="Catálogo"
            isActive={currentTab === 'catalog'}
            onClick={() => setCurrentTab('catalog')}
          />
          <NavItem
            icon={<Tag />}
            label="Promos"
            isActive={currentTab === 'promos'}
            onClick={() => setCurrentTab('promos')}
          />
          <NavItem
            icon={<Camera />}
            label="Fotos"
            isActive={currentTab === 'photos'}
            onClick={() => setCurrentTab('photos')}
          />
        </nav>
      </div>
    </div>
  );
}

function OfflineBanner() {
  return (
    <div className="absolute top-0 left-0 right-0 z-50 bg-[#27AE60] text-white h-8 flex items-center justify-center gap-2 text-xs font-extrabold">
      <WifiOff className="w-4 h-4" />
      Modo sin internet activo
    </div>
  );
}

function NavItem({ icon, label, isActive, onClick }: { icon: ReactNode, label: string, isActive: boolean, onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex flex-col items-center justify-center w-full h-full space-y-1 transition-colors ${
        isActive ? 'text-[#F2994A]' : 'text-gray-400'
      }`}
      aria-pressed={isActive}
    >
      <div className={isActive ? 'scale-110 transition-transform' : ''}>
        {icon}
      </div>
      <span className="text-[10px] font-bold tracking-wide">{label}</span>
    </button>
  );
}

function LoginScreen({ onLogin, isOffline }: { onLogin: () => void, isOffline: boolean }) {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-8 bg-white">
      <div className="w-24 h-24 bg-orange-50 rounded-full flex items-center justify-center mb-6">
        <BookOpen className="text-[#F2994A] w-12 h-12" />
      </div>
      <h1 className="text-3xl font-extrabold text-center mb-2 text-gray-800">Ollas Manchay</h1>
      <p className="text-lg text-gray-500 text-center mb-8">Comunidad y Progreso</p>

      {isOffline && (
        <div className="w-full mb-5 rounded-xl bg-green-50 border border-green-100 text-[#27AE60] px-4 py-3 flex items-center gap-2 text-sm font-bold">
          <WifiOff className="w-5 h-5 shrink-0" />
          Puedes revisar tu catálogo guardado sin internet.
        </div>
      )}

      <div className="w-full space-y-4">
        <div>
          <label className="block text-sm font-bold text-gray-700 mb-2">Celular o DNI</label>
          <input
            type="tel"
            inputMode="numeric"
            className="w-full h-14 text-lg px-4 rounded-xl bg-gray-50 border border-gray-200 focus:border-[#F2994A] focus:bg-white focus:outline-none transition-colors"
            placeholder="Ingrese su número"
          />
        </div>
        <button
          type="button"
          onClick={onLogin}
          className="w-full h-14 mt-4 bg-[#F2994A] text-white text-lg font-bold rounded-xl active:bg-orange-600 transition-colors"
        >
          Ingresar
        </button>
      </div>
    </div>
  );
}

function TopBar({ title, subtitle }: { title: string, subtitle?: string }) {
  return (
    <header className="bg-white px-5 pt-8 pb-4 flex flex-col border-b border-gray-100 sticky top-0 z-10">
      <h1 className="text-2xl font-extrabold text-gray-800">{title}</h1>
      {subtitle && <p className="text-sm text-gray-500 mt-1">{subtitle}</p>}
    </header>
  );
}

function CatalogScreen() {
  const catalogs = [
    { name: 'Menú Semanal - Lun a Vie', date: 'Actualizado hoy', tag: 'Activo' },
    { name: 'Catálogo Fiestas Patrias', date: 'Hace 2 semanas', tag: 'Guardado' },
    { name: 'Especial Fin de Semana', date: 'Hace 1 mes', tag: 'Guardado' },
  ];

  return (
    <div className="flex flex-col min-h-full">
      <TopBar title="Diseño de Catálogo" subtitle="Así verán tus productos los clientes" />
      <main className="p-5 space-y-6">
        <section className="bg-gradient-to-r from-orange-100 to-orange-50 rounded-2xl p-5 border border-orange-200 flex justify-between items-center gap-4">
          <h2 className="text-base font-extrabold text-gray-800 flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-[#F2994A] shrink-0" /> Generar nuevo catálogo
          </h2>
          <button type="button" className="bg-[#F2994A] text-white px-4 py-2 rounded-xl text-sm font-bold shadow-sm active:scale-95 transition-transform">
            Nuevo
          </button>
        </section>

        <section>
          <div className="flex justify-between items-center mb-4 gap-3">
            <h2 className="text-lg font-extrabold text-gray-800">Tus catálogos</h2>
            <button type="button" className="flex items-center gap-1 text-sm font-bold text-[#27AE60] bg-green-50 px-3 py-1.5 rounded-lg active:bg-green-100">
              <Plus className="w-4 h-4" /> Agregar
            </button>
          </div>
          <div className="space-y-3">
            {catalogs.map((catalog) => (
              <div key={catalog.name} className="bg-white p-4 rounded-xl border border-gray-100 flex justify-between items-center active:bg-gray-50 transition-colors">
                <div className="flex gap-4 items-center">
                  <div className="w-12 h-12 bg-orange-50 rounded-lg flex items-center justify-center shrink-0">
                    <BookOpen className="w-5 h-5 text-[#F2994A]" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-gray-800">{catalog.name}</h3>
                    <div className="flex items-center gap-2 mt-1 flex-wrap">
                      <p className="text-sm font-medium text-gray-500">{catalog.date}</p>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md uppercase tracking-wide ${catalog.tag === 'Activo' ? 'bg-green-50 text-[#27AE60]' : 'bg-gray-100 text-gray-500'}`}>
                        {catalog.tag}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="bg-white p-4 rounded-xl border border-gray-100">
          <h2 className="text-lg font-extrabold text-gray-800 mb-3">Publicación rápida</h2>
          <div className="grid grid-cols-2 gap-3">
            <button type="button" className="h-12 rounded-xl bg-green-50 text-[#27AE60] text-sm font-extrabold active:bg-green-100">
              WhatsApp
            </button>
            <button type="button" className="h-12 rounded-xl bg-orange-50 text-[#F2994A] text-sm font-extrabold active:bg-orange-100">
              Afiche simple
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}

function PromosScreen() {
  const promos = [
    { title: 'Menú + Refresco', price: 'S/ 10.00', active: true },
    { title: '2x1 en Postres', price: 'S/ 5.00', active: false },
  ];

  const tips = [
    { title: 'Sonríe siempre', desc: 'Una actitud positiva invita a comprar.' },
    { title: 'Precios claros', desc: 'Escribe los precios grandes y visibles.' },
  ];

  const paymentMethods = [
    { title: 'Yape', desc: 'QR listo para mostrar', icon: <QrCode className="w-5 h-5" /> },
    { title: 'Plin', desc: 'Pago por celular', icon: <CreditCard className="w-5 h-5" /> },
    { title: 'Efectivo', desc: 'Cambio anotado', icon: <Tag className="w-5 h-5" /> },
  ];

  return (
    <div className="flex flex-col min-h-full">
      <TopBar title="Promos y Marketing" subtitle="Atrae más clientes todos los días" />
      <main className="p-5 space-y-8">
        <section>
          <div className="flex justify-between items-center mb-4 gap-3">
            <h2 className="text-lg font-extrabold text-gray-800 flex items-center gap-2">
              <Tag className="w-5 h-5 text-[#F2994A]" /> Mis Promociones
            </h2>
            <button type="button" className="flex items-center gap-1 text-sm font-bold text-[#27AE60] bg-green-50 px-3 py-1.5 rounded-lg active:bg-green-100">
              <Plus className="w-4 h-4" /> Nueva
            </button>
          </div>
          <div className="space-y-3">
            {promos.map((promo) => (
              <div key={promo.title} className="bg-white p-4 rounded-xl border border-gray-100 flex justify-between items-center gap-3">
                <div>
                  <h3 className="text-base font-bold text-gray-800">{promo.title}</h3>
                  <p className="text-lg font-extrabold text-[#F2994A] mt-0.5">{promo.price}</p>
                </div>
                <div className={`px-3 py-1 rounded-full text-xs font-bold ${promo.active ? 'bg-green-50 text-[#27AE60]' : 'bg-gray-100 text-gray-500'}`}>
                  {promo.active ? 'Activa' : 'Inactiva'}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section>
          <h2 className="text-lg font-extrabold text-gray-800 flex items-center gap-2 mb-4">
            <CreditCard className="w-5 h-5 text-[#27AE60]" /> Pasarelas de pago
          </h2>
          <div className="grid gap-3">
            {paymentMethods.map((method) => (
              <div key={method.title} className="bg-white p-4 rounded-xl border border-gray-100 flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-green-50 text-[#27AE60] flex items-center justify-center shrink-0">
                  {method.icon}
                </div>
                <div>
                  <h3 className="text-base font-bold text-gray-800">{method.title}</h3>
                  <p className="text-sm text-gray-500">{method.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        <section>
          <h2 className="text-lg font-extrabold text-gray-800 flex items-center gap-2 mb-4">
            <CalendarCheck className="w-5 h-5 text-[#F2994A]" /> Plan de marketing
          </h2>
          <div className="bg-white rounded-xl border border-gray-100 divide-y divide-gray-100">
            {['Lunes: publicar menú semanal', 'Miércoles: activar promo', 'Viernes: tomar nuevas fotos'].map((task) => (
              <div key={task} className="p-4 flex items-center gap-3">
                <CheckCircle className="w-5 h-5 text-[#27AE60] shrink-0" />
                <span className="text-sm font-bold text-gray-700">{task}</span>
              </div>
            ))}
          </div>
        </section>

        <section>
          <h2 className="text-lg font-extrabold text-gray-800 flex items-center gap-2 mb-4">
            <TrendingUp className="w-5 h-5 text-[#27AE60]" /> Tips de Marketing
          </h2>
          <div className="grid gap-3">
            {tips.map((tip, idx) => (
              <div key={tip.title} className="bg-white p-4 rounded-xl border border-gray-100 flex gap-3">
                <div className="w-8 h-8 rounded-full bg-orange-50 text-[#F2994A] flex items-center justify-center text-sm font-bold shrink-0">
                  {idx + 1}
                </div>
                <div>
                  <h3 className="text-base font-bold text-gray-800">{tip.title}</h3>
                  <p className="text-sm text-gray-500 mt-1 leading-snug">{tip.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

function PhotoGuide() {
  const [uploadState, setUploadState] = useState<'idle' | 'uploading' | 'success'>('idle');

  const handleSimulateUpload = () => {
    setUploadState('uploading');
    setTimeout(() => {
      setUploadState('success');
      setTimeout(() => setUploadState('idle'), 3000);
    }, 2000);
  };

  return (
    <div className="flex flex-col min-h-full">
      <TopBar title="Mejorar Fotos" subtitle="Atrae a los clientes por la vista" />
      <main className="p-5 space-y-6">
        <section className="bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
          <div className="text-center mb-4">
            <h2 className="text-lg font-bold text-gray-800">Herramienta de Mejora</h2>
            <p className="text-sm text-gray-500">Sube una foto y la mejoraremos</p>
          </div>
          <button
            type="button"
            onClick={handleSimulateUpload}
            disabled={uploadState === 'uploading'}
            className={`w-full h-14 rounded-xl font-bold flex items-center justify-center gap-2 transition-colors ${
              uploadState === 'idle'
                ? 'bg-[#F2994A] text-white active:bg-orange-600'
                : uploadState === 'uploading'
                ? 'bg-orange-100 text-[#F2994A]'
                : 'bg-[#27AE60] text-white'
            }`}
          >
            {uploadState === 'idle' && (
              <>
                <UploadCloud className="w-5 h-5" /> Subir y Mejorar Imagen
              </>
            )}
            {uploadState === 'uploading' && (
              <>
                <Loader2 className="w-5 h-5 animate-spin" /> Procesando...
              </>
            )}
            {uploadState === 'success' && (
              <>
                <CheckCircle className="w-5 h-5" /> Imagen mejorada
              </>
            )}
          </button>
        </section>

        <section>
          <h3 className="text-base font-bold text-gray-800 mb-3">Guía Rápida</h3>
          <div className="grid grid-cols-2 gap-3 mb-4">
            <div className="bg-white rounded-xl overflow-hidden border border-gray-100">
              <div className="aspect-square bg-gray-100 relative flex items-center justify-center">
                <ImageIcon className="w-8 h-8 text-gray-300" />
              </div>
              <div className="p-2 bg-red-50 flex items-center justify-center gap-1.5">
                <XCircle className="text-red-500 w-4 h-4" />
                <span className="text-xs font-bold text-red-700">Oscuro</span>
              </div>
            </div>
            <div className="bg-white rounded-xl overflow-hidden border border-green-200">
              <div className="aspect-square bg-orange-50 relative flex items-center justify-center">
                <ImageIcon className="w-8 h-8 text-[#F2994A]" />
              </div>
              <div className="p-2 bg-green-50 flex items-center justify-center gap-1.5">
                <CheckCircle className="text-[#27AE60] w-4 h-4" />
                <span className="text-xs font-bold text-green-700">Claro y de cerca</span>
              </div>
            </div>
          </div>
          <ul className="space-y-2">
            <li className="flex items-center gap-2 text-sm text-gray-600">
              <CheckCircle className="w-4 h-4 text-[#27AE60] shrink-0" /> Luz de día, no flashes fuertes.
            </li>
            <li className="flex items-center gap-2 text-sm text-gray-600">
              <CheckCircle className="w-4 h-4 text-[#27AE60] shrink-0" /> Limpia el fondo y acerca el plato.
            </li>
          </ul>
        </section>
      </main>
    </div>
  );
}
