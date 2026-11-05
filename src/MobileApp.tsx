import { useEffect, useMemo, useState, type ChangeEvent, type ReactNode } from 'react';
import {
  BookOpen,
  Camera,
  CheckCircle,
  ChevronLeft,
  ChevronRight,
  CreditCard,
  Download,
  Image as ImageIcon,
  Loader2,
  PackagePlus,
  Plus,
  QrCode,
  Send,
  Store,
  Tag,
  UploadCloud,
  WifiOff,
  XCircle
} from 'lucide-react';

type Role = 'client' | 'admin';
type AdminTab = 'ofertas' | 'fotos' | 'catalogo';
type PhotoState = 'guide' | 'camera' | 'processing' | 'success';

type Product = {
  id: number;
  name: string;
  price: string;
  categories: string[];
  image?: string;
};

type Promo = {
  id: number;
  title: string;
  content: string;
  price: string;
  note: string;
  image?: string;
};

type ProductDraft = Omit<Product, 'id'>;
type PromoDraft = Omit<Promo, 'id'>;

const CATEGORIES = ['Dulce', 'Salado', 'Amargo', 'Grande', 'Pequeno'];
const OFFER_NOTE = 'Valida hasta agotar stock.';

const INITIAL_PRODUCTS: Product[] = [
  { id: 1, name: 'Papa rellena', price: 'S/ 6.50', categories: ['Salado', 'Grande'] },
  { id: 2, name: 'Cachanga', price: 'S/ 3.00', categories: ['Salado', 'Pequeno'] },
  { id: 3, name: 'Mazamorra', price: 'S/ 4.50', categories: ['Dulce', 'Grande'] },
];

const INITIAL_PROMOS: Promo[] = [
  {
    id: 1,
    title: 'Menu + Refresco',
    content: 'Combo especial para el almuerzo de hoy.',
    price: 'S/ 10.00',
    note: OFFER_NOTE,
  },
  {
    id: 2,
    title: '2x1 en postres',
    content: 'Lleva dos postres por el precio de uno los fines de semana.',
    price: 'S/ 8.00',
    note: OFFER_NOTE,
  },
];

export default function MobileApp() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [role, setRole] = useState<Role | null>(null);
  const [isOffline, setIsOffline] = useState(false);
  const [products, setProducts] = useState<Product[]>(INITIAL_PRODUCTS);
  const [promos, setPromos] = useState<Promo[]>(INITIAL_PROMOS);

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

  const addProduct = (product: ProductDraft) => {
    setProducts((currentProducts) => [
      { ...product, id: Date.now() },
      ...currentProducts,
    ]);
  };

  const addPromo = (promo: PromoDraft) => {
    setPromos((currentPromos) => [
      { ...promo, id: Date.now() },
      ...currentPromos,
    ]);
  };

  return (
    <PhoneFrame>
      {!isLoggedIn && (
        <LoginScreen onLogin={() => setIsLoggedIn(true)} isOffline={isOffline} />
      )}
      {isLoggedIn && !role && (
        <RoleScreen onChoose={setRole} />
      )}
      {isLoggedIn && role === 'client' && (
        <ClientApp
          products={products}
          promos={promos}
          isOffline={isOffline}
          onChangeRole={() => setRole(null)}
        />
      )}
      {isLoggedIn && role === 'admin' && (
        <AdminApp
          products={products}
          promos={promos}
          isOffline={isOffline}
          onAddProduct={addProduct}
          onAddPromo={addPromo}
          onChangeRole={() => setRole(null)}
        />
      )}
    </PhoneFrame>
  );
}

function PhoneFrame({ children }: { children: ReactNode }) {
  return (
    <div className="phone-page">
      <div className="phone-shell">
        <div className="phone-speaker" aria-hidden="true" />
        {children}
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

function LoginScreen({ onLogin, isOffline }: { onLogin: () => void, isOffline: boolean }) {
  return (
    <div className="app-surface flex flex-col items-center justify-center p-8 bg-white">
      <div className="w-24 h-24 bg-orange-50 rounded-full flex items-center justify-center mb-6">
        <BookOpen className="text-[#F2994A] w-12 h-12" />
      </div>
      <h1 className="text-3xl font-extrabold text-center mb-2 text-gray-800">Ollas Manchay</h1>
      <p className="text-lg text-gray-500 text-center mb-2">Comunidad y Progreso</p>
      <p className="text-xs font-bold text-gray-400 text-center mb-8">Bosquejo de estructura</p>

      {isOffline && (
        <div className="w-full mb-5 rounded-xl bg-green-50 border border-green-100 text-[#27AE60] px-4 py-3 flex items-center gap-2 text-sm font-bold">
          <WifiOff className="w-5 h-5 shrink-0" />
          Puedes revisar tu catalogo guardado sin internet.
        </div>
      )}

      <div className="w-full space-y-4">
        <div>
          <label className="block text-sm font-bold text-gray-700 mb-2">Celular o DNI</label>
          <input
            type="tel"
            inputMode="numeric"
            className="w-full h-14 text-lg px-4 rounded-xl bg-gray-50 border border-gray-200 focus:border-[#F2994A] focus:bg-white focus:outline-none transition-colors"
            placeholder="Ingrese su numero"
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

function RoleScreen({ onChoose }: { onChoose: (role: Role) => void }) {
  return (
    <div className="app-surface flex flex-col justify-center p-6 bg-white gap-4">
      <div className="mb-2">
        <h1 className="text-2xl font-extrabold text-gray-800">Como quieres verlo</h1>
        <p className="text-sm text-gray-500 mt-1">Mismo bosquejo, dos vistas. Lo que se sube en admin se ve en clienta.</p>
      </div>
      <button
        type="button"
        onClick={() => onChoose('client')}
        className="w-full min-h-28 rounded-2xl border border-gray-200 bg-orange-50 p-5 text-left active:scale-[0.99] transition-transform"
      >
        <Store className="w-7 h-7 text-[#F2994A] mb-2" />
        <p className="text-lg font-extrabold text-gray-800">Ver como clienta</p>
        <p className="text-sm text-gray-500 mt-1">Catalogo, ofertas y Pedir ya.</p>
      </button>
      <button
        type="button"
        onClick={() => onChoose('admin')}
        className="w-full min-h-28 rounded-2xl border border-gray-200 bg-green-50 p-5 text-left active:scale-[0.99] transition-transform"
      >
        <PackagePlus className="w-7 h-7 text-[#27AE60] mb-2" />
        <p className="text-lg font-extrabold text-gray-800">Administrar olla</p>
        <p className="text-sm text-gray-500 mt-1">Productos, ofertas y fotos.</p>
      </button>
    </div>
  );
}

function SwitchRoleButton({ onChangeRole }: { onChangeRole: () => void }) {
  return (
    <button
      type="button"
      onClick={onChangeRole}
      className="text-xs font-extrabold text-[#F2994A]"
    >
      Cambiar vista
    </button>
  );
}

function ClientApp({ products, promos, isOffline, onChangeRole }: {
  products: Product[],
  promos: Promo[],
  isOffline: boolean,
  onChangeRole: () => void,
}) {
  return (
    <div className="app-surface bg-gray-50 font-sans text-gray-900 relative flex flex-col overflow-hidden">
      {isOffline && <OfflineBanner />}
      <div className="flex-1 overflow-y-auto">
        <ClientCatalog
          products={products}
          promos={promos}
          onChangeRole={onChangeRole}
        />
      </div>
    </div>
  );
}

function AdminApp({ products, promos, isOffline, onAddProduct, onAddPromo, onChangeRole }: {
  products: Product[],
  promos: Promo[],
  isOffline: boolean,
  onAddProduct: (product: ProductDraft) => void,
  onAddPromo: (promo: PromoDraft) => void,
  onChangeRole: () => void,
}) {
  const [currentTab, setCurrentTab] = useState<AdminTab>('catalogo');

  return (
    <div className="app-surface bg-gray-50 font-sans text-gray-900 relative flex flex-col overflow-hidden">
      {isOffline && <OfflineBanner />}
      <div className="flex-1 overflow-y-auto pb-20">
        {currentTab === 'catalogo' && (
          <CatalogAdmin
            products={products}
            onAddProduct={onAddProduct}
            onChangeRole={onChangeRole}
          />
        )}
        {currentTab === 'ofertas' && (
          <PromosAdmin
            promos={promos}
            onAddPromo={onAddPromo}
            onChangeRole={onChangeRole}
          />
        )}
        {currentTab === 'fotos' && <PhotoGuide onChangeRole={onChangeRole} />}
      </div>
      <nav className="absolute bottom-0 w-full bg-white border-t border-gray-200 flex justify-around items-center h-16 px-2 z-50">
        <NavItem
          icon={<BookOpen />}
          label="Catalogo"
          isActive={currentTab === 'catalogo'}
          onClick={() => setCurrentTab('catalogo')}
        />
        <NavItem
          icon={<Tag />}
          label="Ofertas"
          isActive={currentTab === 'ofertas'}
          onClick={() => setCurrentTab('ofertas')}
        />
        <NavItem
          icon={<Camera />}
          label="Fotos"
          isActive={currentTab === 'fotos'}
          onClick={() => setCurrentTab('fotos')}
        />
      </nav>
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

function TopBar({ title, subtitle, extra }: { title: string, subtitle?: string, extra?: ReactNode }) {
  return (
    <header className="bg-white px-5 pt-8 pb-4 flex flex-col border-b border-gray-100 sticky top-0 z-10">
      <div className="flex items-start justify-between gap-3">
        <h1 className="text-2xl font-extrabold text-gray-800">{title}</h1>
        {extra}
      </div>
      {subtitle && <p className="text-sm text-gray-500 mt-1">{subtitle}</p>}
    </header>
  );
}

function ClientCatalog({ products, promos, onChangeRole }: {
  products: Product[],
  promos: Promo[],
  onChangeRole: () => void,
}) {
  const [filters, setFilters] = useState<string[]>([]);
  const [promoIndex, setPromoIndex] = useState(0);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [orderToast, setOrderToast] = useState('');

  const visibleProducts = useMemo(() => {
    if (filters.length === 0) {
      return products;
    }

    return products.filter((product) =>
      product.categories.some((category) => filters.includes(category))
    );
  }, [filters, products]);

  const activePromo = promos[promoIndex] ?? promos[0];

  return (
    <div className="flex flex-col min-h-full">
      <TopBar
        title="Catalogo"
        subtitle="Vista clienta"
        extra={<SwitchRoleButton onChangeRole={onChangeRole} />}
      />
      <main className="p-5 space-y-5">
        {activePromo && (
          <section className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="relative h-40 bg-orange-50">
              <button
                type="button"
                onClick={() => setDetailsOpen((open) => !open)}
                className="w-full h-full flex items-center justify-center"
                aria-expanded={detailsOpen}
              >
                {activePromo.image ? (
                  <img src={activePromo.image} alt={activePromo.title} className="w-full h-full object-contain pointer-events-none" />
                ) : (
                  <Tag className="w-12 h-12 text-[#F2994A] pointer-events-none" />
                )}
              </button>
              {promos.length > 1 && (
                <>
                  <button
                    type="button"
                    onClick={() => {
                      setPromoIndex((currentIndex) => (currentIndex - 1 + promos.length) % promos.length);
                      setDetailsOpen(false);
                    }}
                    className="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-white/90 text-gray-700 flex items-center justify-center shadow-sm"
                    aria-label="Oferta anterior"
                  >
                    <ChevronLeft className="w-5 h-5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setPromoIndex((currentIndex) => (currentIndex + 1) % promos.length);
                      setDetailsOpen(false);
                    }}
                    className="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-white/90 text-gray-700 flex items-center justify-center shadow-sm"
                    aria-label="Oferta siguiente"
                  >
                    <ChevronRight className="w-5 h-5" />
                  </button>
                </>
              )}
            </div>
            <div className="px-4 pb-4 pt-3">
              <div className="flex justify-center gap-1.5 mb-2">
                {promos.map((promo, index) => (
                  <span
                    key={promo.id}
                    className={`h-1.5 rounded-full ${index === promoIndex ? 'w-5 bg-[#F2994A]' : 'w-1.5 bg-gray-200'}`}
                  />
                ))}
              </div>
              <p className="text-center text-xs font-bold text-gray-400">Toca la foto para ver el detalle</p>
              <div className={`promo-details ${detailsOpen ? 'open' : ''}`}>
                <h3 className="text-base font-extrabold text-gray-800">{activePromo.title}</h3>
                <p className="text-sm text-gray-500 mt-1 leading-snug">{activePromo.content}</p>
                <p className="text-lg font-extrabold text-[#F2994A] mt-2">{activePromo.price}</p>
                <p className="text-xs text-gray-400 font-bold mt-1">{activePromo.note}</p>
              </div>
            </div>
          </section>
        )}

        <section>
          <p className="text-xs font-extrabold text-gray-500 uppercase mb-2">Filtrar catalogo</p>
          <CategoryChips selected={filters} onToggle={(category) => setFilters((current) => toggleValue(current, category))} />
        </section>

        <section className="space-y-3">
          {visibleProducts.length === 0 && (
            <div className="text-center text-sm font-bold text-gray-500 py-5">
              No hay productos con esa categoria.
            </div>
          )}
          {visibleProducts.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </section>

        <button
          type="button"
          onClick={() => flashMessage(setOrderToast, 'Pedido iniciado.')}
          className="w-full h-12 bg-[#F2994A] text-white font-extrabold rounded-xl active:bg-orange-600 transition-colors flex items-center justify-center gap-2"
        >
          <Send className="w-5 h-5" /> Pedir ya
        </button>
        <p className="min-h-5 text-center text-sm font-bold text-[#27AE60]">{orderToast}</p>
      </main>
    </div>
  );
}

function CatalogAdmin({ products, onAddProduct, onChangeRole }: {
  products: Product[],
  onAddProduct: (product: ProductDraft) => void,
  onChangeRole: () => void,
}) {
  const [productImage, setProductImage] = useState('');
  const [productName, setProductName] = useState('');
  const [productPrice, setProductPrice] = useState('');
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [formToast, setFormToast] = useState('');
  const [downloadToast, setDownloadToast] = useState('');

  const submitProduct = () => {
    if (!productImage || !productName.trim() || !productPrice.trim() || selectedCategories.length === 0) {
      flashMessage(setFormToast, 'Completa imagen, nombre, precio y categoria.');
      return;
    }

    onAddProduct({
      name: productName.trim(),
      price: productPrice.trim(),
      categories: selectedCategories,
      image: productImage,
    });

    setProductImage('');
    setProductName('');
    setProductPrice('');
    setSelectedCategories([]);
    flashMessage(setFormToast, 'Producto agregado al catalogo.');
  };

  return (
    <div className="flex flex-col min-h-full">
      <TopBar
        title="Config catalogo"
        subtitle="Se refleja en la vista clienta"
        extra={<SwitchRoleButton onChangeRole={onChangeRole} />}
      />
      <main className="p-5 space-y-4">
        <ImagePicker
          image={productImage}
          label="Imagen del producto"
          onChange={(event) => readFileAsDataUrl(event, setProductImage)}
        />
        <TextField label="Nombre" value={productName} onChange={setProductName} placeholder="Ej. Papa rellena" />
        <TextField label="Precio" value={productPrice} onChange={setProductPrice} placeholder="Ej. S/ 6.50" />
        <div>
          <p className="text-xs font-extrabold text-gray-500 uppercase mb-2">Categorias</p>
          <CategoryChips selected={selectedCategories} onToggle={(category) => setSelectedCategories((current) => toggleValue(current, category))} />
        </div>
        <button
          type="button"
          onClick={submitProduct}
          className="w-full h-12 bg-[#27AE60] text-white font-extrabold rounded-xl active:bg-green-700 transition-colors flex items-center justify-center gap-2"
        >
          <Plus className="w-5 h-5" /> Subir producto
        </button>
        <p className="min-h-5 text-center text-sm font-bold text-[#27AE60]">{formToast}</p>
        <button
          type="button"
          onClick={() => {
            void downloadCatalogImage(products).then((ok) => {
              flashMessage(setDownloadToast, ok ? 'Catalogo descargado.' : 'No se pudo descargar.');
            });
          }}
          className="w-full h-11 rounded-xl border border-gray-200 bg-white text-sm font-extrabold text-gray-700 flex items-center justify-center gap-2"
        >
          <Download className="w-4 h-4" /> Descargar catalogo
        </button>
        <p className="min-h-5 text-center text-sm font-bold text-[#27AE60]">{downloadToast}</p>
      </main>
    </div>
  );
}

function PromosAdmin({ promos, onAddPromo, onChangeRole }: {
  promos: Promo[],
  onAddPromo: (promo: PromoDraft) => void,
  onChangeRole: () => void,
}) {
  const [promoImage, setPromoImage] = useState('');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [price, setPrice] = useState('');
  const [formToast, setFormToast] = useState('');
  const [downloadToast, setDownloadToast] = useState('');

  const submitPromo = () => {
    if (!promoImage || !title.trim() || !content.trim() || !price.trim()) {
      flashMessage(setFormToast, 'Completa imagen, titulo, contenido y precio.');
      return;
    }

    onAddPromo({
      title: title.trim(),
      content: content.trim(),
      price: price.trim(),
      image: promoImage,
      note: OFFER_NOTE,
    });

    setPromoImage('');
    setTitle('');
    setContent('');
    setPrice('');
    flashMessage(setFormToast, 'Oferta agregada al banner.');
  };

  return (
    <div className="flex flex-col min-h-full">
      <TopBar
        title="Admin ofertas"
        subtitle="Se ven en el carrusel de la clienta"
        extra={<SwitchRoleButton onChangeRole={onChangeRole} />}
      />
      <main className="p-5 space-y-4">
        <ImagePicker
          image={promoImage}
          label="Imagen de oferta"
          onChange={(event) => readFileAsDataUrl(event, setPromoImage)}
        />
        <TextField label="Titulo" value={title} onChange={setTitle} placeholder="Ej. Promo de temporada" />
        <TextAreaField label="Contenido" value={content} onChange={setContent} placeholder="Describe en que consiste la oferta" />
        <TextField label="Precio" value={price} onChange={setPrice} placeholder="Ej. S/ 12.00" />
        <button
          type="button"
          onClick={submitPromo}
          className="w-full h-12 bg-[#27AE60] text-white font-extrabold rounded-xl active:bg-green-700 transition-colors flex items-center justify-center gap-2"
        >
          <Plus className="w-5 h-5" /> Subir oferta
        </button>
        <p className="min-h-5 text-center text-sm font-bold text-[#27AE60]">{formToast}</p>
        <button
          type="button"
          onClick={() => {
            void downloadPromosImage(promos).then((ok) => {
              flashMessage(setDownloadToast, ok ? 'Ofertas descargadas.' : 'No se pudo descargar.');
            });
          }}
          className="w-full h-11 rounded-xl border border-gray-200 bg-white text-sm font-extrabold text-gray-700 flex items-center justify-center gap-2"
        >
          <Download className="w-4 h-4" /> Descargar ofertas
        </button>
        <p className="min-h-5 text-center text-sm font-bold text-[#27AE60]">{downloadToast}</p>

        <div className="space-y-3 pt-2">
          {promos.map((promo) => (
            <div key={promo.id} className="bg-white p-4 rounded-xl border border-gray-100 flex gap-3">
              <div className="w-14 h-14 rounded-lg bg-orange-50 flex items-center justify-center overflow-hidden shrink-0">
                {promo.image ? (
                  <img src={promo.image} alt={promo.title} className="w-full h-full object-cover" />
                ) : (
                  <Tag className="w-6 h-6 text-[#F2994A]" />
                )}
              </div>
              <div className="min-w-0 flex-1">
                <h3 className="text-base font-bold text-gray-800">{promo.title}</h3>
                <p className="text-sm text-gray-500 mt-1 leading-snug">{promo.content}</p>
                <p className="text-lg font-extrabold text-[#F2994A] mt-1">{promo.price}</p>
              </div>
            </div>
          ))}
        </div>

        <section className="space-y-3">
          <h2 className="text-lg font-extrabold text-gray-800">Pagos (bosquejo)</h2>
          {[
            { title: 'Yape', desc: 'QR listo para mostrar', icon: <QrCode className="w-5 h-5" /> },
            { title: 'Plin', desc: 'Pago por celular', icon: <CreditCard className="w-5 h-5" /> },
            { title: 'Efectivo', desc: 'Cambio anotado', icon: <Tag className="w-5 h-5" /> },
          ].map((method) => (
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
        </section>
      </main>
    </div>
  );
}

function PhotoGuide({ onChangeRole }: { onChangeRole: () => void }) {
  const [photoState, setPhotoState] = useState<PhotoState>('guide');

  const startProcessing = () => {
    setPhotoState('processing');
    window.setTimeout(() => {
      setPhotoState('success');
      window.setTimeout(() => setPhotoState('guide'), 1400);
    }, 1200);
  };

  return (
    <div className="flex flex-col min-h-full">
      <TopBar
        title="Imagenes"
        subtitle="Guia y camara simulada"
        extra={<SwitchRoleButton onChangeRole={onChangeRole} />}
      />
      <main className="p-5 space-y-6">
        <section className="bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
          {photoState === 'guide' && (
            <div className="space-y-5">
              <h2 className="text-lg font-bold text-gray-800">Guia para tomar foto</h2>
              <div className="space-y-3">
                {[
                  'Producto centrado y ocupando el encuadre.',
                  'Buena iluminacion, sin sombras fuertes.',
                  'Fondo liso, limpio y sin objetos alrededor.',
                ].map((tip, index) => (
                  <div key={tip} className="flex gap-3 items-start">
                    <span className="w-7 h-7 rounded-full border-2 border-gray-800 flex items-center justify-center text-sm font-extrabold shrink-0">
                      {index + 1}
                    </span>
                    <p className="text-sm font-bold text-gray-700 pt-1">{tip}</p>
                  </div>
                ))}
              </div>
              <button
                type="button"
                onClick={() => setPhotoState('camera')}
                className="w-full h-12 bg-[#F2994A] text-white font-extrabold rounded-xl active:bg-orange-600 transition-colors flex items-center justify-center gap-2"
              >
                <Camera className="w-5 h-5" /> Tomar foto
              </button>
            </div>
          )}

          {photoState === 'camera' && (
            <div className="space-y-5">
              <div className="h-72 rounded-2xl bg-gray-950 text-gray-500 flex flex-col items-center justify-center gap-3">
                <Camera className="w-10 h-10" />
                <p className="text-sm font-bold">Vista de camara simulada</p>
              </div>
              <button
                type="button"
                onClick={startProcessing}
                className="mx-auto w-16 h-16 rounded-full bg-white border-4 border-gray-900 flex items-center justify-center active:scale-95 transition-transform"
                aria-label="Tomar foto"
              >
                <span className="w-10 h-10 rounded-full bg-[#F2994A]" />
              </button>
            </div>
          )}

          {photoState === 'processing' && (
            <div className="h-72 flex flex-col items-center justify-center gap-4 text-center">
              <Loader2 className="w-10 h-10 animate-spin text-[#F2994A]" />
              <p className="font-extrabold text-gray-800">Mejorando imagen con IA</p>
            </div>
          )}

          {photoState === 'success' && (
            <div className="h-72 flex flex-col items-center justify-center gap-4 text-center">
              <CheckCircle className="w-12 h-12 text-[#27AE60]" />
              <p className="font-extrabold text-gray-800">Imagen guardada</p>
            </div>
          )}
        </section>

        <section>
          <h3 className="text-base font-bold text-gray-800 mb-3">Guia rapida</h3>
          <div className="grid grid-cols-2 gap-3">
            <div className="bg-white rounded-xl overflow-hidden border border-gray-100">
              <div className="aspect-square bg-gray-100 flex items-center justify-center">
                <ImageIcon className="w-8 h-8 text-gray-300" />
              </div>
              <div className="p-2 bg-red-50 flex items-center justify-center gap-1.5">
                <XCircle className="text-red-500 w-4 h-4" />
                <span className="text-xs font-bold text-red-700">Oscuro</span>
              </div>
            </div>
            <div className="bg-white rounded-xl overflow-hidden border border-green-200">
              <div className="aspect-square bg-orange-50 flex items-center justify-center">
                <ImageIcon className="w-8 h-8 text-[#F2994A]" />
              </div>
              <div className="p-2 bg-green-50 flex items-center justify-center gap-1.5">
                <CheckCircle className="text-[#27AE60] w-4 h-4" />
                <span className="text-xs font-bold text-green-700">Claro y cerca</span>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}

function ImagePicker({ image, label, onChange }: {
  image: string,
  label: string,
  onChange: (event: ChangeEvent<HTMLInputElement>) => void,
}) {
  return (
    <label className="min-h-32 rounded-2xl border-2 border-dashed border-gray-300 bg-white flex flex-col items-center justify-center gap-2 cursor-pointer overflow-hidden active:bg-gray-50">
      <input type="file" accept="image/png,image/jpeg,image/webp,image/*" className="sr-only" onChange={onChange} />
      {image ? (
        <img src={image} alt={label} className="w-full h-32 object-contain bg-gray-50" />
      ) : (
        <>
          <UploadCloud className="w-8 h-8 text-[#F2994A]" />
          <span className="text-sm font-extrabold text-gray-500">{label}</span>
        </>
      )}
    </label>
  );
}

function TextField({ label, value, onChange, placeholder }: {
  label: string,
  value: string,
  onChange: (value: string) => void,
  placeholder: string,
}) {
  return (
    <label className="block">
      <span className="block text-xs font-extrabold text-gray-500 uppercase mb-2">{label}</span>
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="w-full h-12 px-4 rounded-xl bg-white border border-gray-200 focus:border-[#F2994A] focus:outline-none transition-colors text-sm font-bold"
      />
    </label>
  );
}

function TextAreaField({ label, value, onChange, placeholder }: {
  label: string,
  value: string,
  onChange: (value: string) => void,
  placeholder: string,
}) {
  return (
    <label className="block">
      <span className="block text-xs font-extrabold text-gray-500 uppercase mb-2">{label}</span>
      <textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="w-full min-h-24 px-4 py-3 rounded-xl bg-white border border-gray-200 focus:border-[#F2994A] focus:outline-none transition-colors text-sm font-bold resize-none"
      />
    </label>
  );
}

function CategoryChips({ selected, onToggle }: {
  selected: string[],
  onToggle: (category: string) => void,
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {CATEGORIES.map((category) => {
        const isActive = selected.includes(category);
        return (
          <button
            key={category}
            type="button"
            onClick={() => onToggle(category)}
            aria-pressed={isActive}
            className={`px-3 py-2 rounded-full text-xs font-extrabold border transition-colors ${
              isActive
                ? 'bg-gray-900 text-white border-gray-900'
                : 'bg-white text-gray-500 border-gray-200 active:bg-gray-50'
            }`}
          >
            {category}
          </button>
        );
      })}
    </div>
  );
}

function ProductCard({ product }: { product: Product }) {
  return (
    <div className="bg-white p-3 rounded-xl border border-gray-100 flex items-center gap-3">
      <div className="w-14 h-14 rounded-lg bg-orange-50 flex items-center justify-center overflow-hidden shrink-0">
        {product.image ? (
          <img src={product.image} alt={product.name} className="w-full h-full object-cover" />
        ) : (
          <BookOpen className="w-6 h-6 text-[#F2994A]" />
        )}
      </div>
      <div className="min-w-0 flex-1">
        <h3 className="text-base font-extrabold text-gray-800 truncate">{product.name}</h3>
        <p className="text-sm font-extrabold text-[#27AE60] mt-0.5">{product.price}</p>
        <p className="text-xs text-gray-500 font-bold mt-0.5">{product.categories.join(' / ')}</p>
      </div>
    </div>
  );
}

function toggleValue(values: string[], value: string) {
  return values.includes(value)
    ? values.filter((currentValue) => currentValue !== value)
    : [...values, value];
}

function readFileAsDataUrl(event: ChangeEvent<HTMLInputElement>, onRead: (value: string) => void) {
  const file = event.target.files?.[0];
  if (!file) {
    return;
  }

  const reader = new FileReader();
  reader.onload = () => onRead(String(reader.result));
  reader.readAsDataURL(file);
}

function flashMessage(setMessage: (message: string) => void, message: string) {
  setMessage(message);
  window.setTimeout(() => setMessage(''), 2200);
}

function loadImage(src?: string) {
  if (!src) {
    return Promise.resolve(null);
  }

  return new Promise<HTMLImageElement | null>((resolve) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => resolve(null);
    image.src = src;
  });
}

async function downloadCatalogImage(products: Product[]) {
  return downloadSheet('catalogo.png', 'Nuestro Catalogo', await Promise.all(
    products.map(async (product) => ({
      image: await loadImage(product.image),
      title: product.name,
      line: product.price,
      extra: product.categories.join(' / '),
    }))
  ));
}

async function downloadPromosImage(promos: Promo[]) {
  return downloadSheet('ofertas.png', 'Ofertas del dia', await Promise.all(
    promos.map(async (promo) => ({
      image: await loadImage(promo.image),
      title: promo.title,
      line: promo.price,
      extra: promo.content,
    }))
  ));
}

async function downloadSheet(filename: string, heading: string, rows: {
  image: HTMLImageElement | null,
  title: string,
  line: string,
  extra: string,
}[]) {
  const scale = 2;
  const width = 720;
  const rowHeight = 150;
  const height = Math.max(420, 130 + rows.length * rowHeight + 40);
  const canvas = document.createElement('canvas');
  canvas.width = width * scale;
  canvas.height = height * scale;

  const context = canvas.getContext('2d');
  if (!context) {
    return false;
  }

  context.scale(scale, scale);
  context.fillStyle = '#FFFFFF';
  context.fillRect(0, 0, width, height);
  context.fillStyle = '#111827';
  context.font = '800 34px Nunito, Arial, sans-serif';
  context.fillText(heading, 40, 64, width - 80);

  rows.forEach((row, index) => {
    const y = 104 + index * rowHeight;
    context.fillStyle = '#F9FAFB';
    context.fillRect(40, y, width - 80, 126);
    context.strokeStyle = '#E5E7EB';
    context.strokeRect(40, y, width - 80, 126);

    context.fillStyle = '#FED7AA';
    context.fillRect(56, y + 18, 90, 90);
    if (row.image) {
      context.drawImage(row.image, 56, y + 18, 90, 90);
    }

    context.fillStyle = '#111827';
    context.font = '800 24px Nunito, Arial, sans-serif';
    context.fillText(row.title, 164, y + 42, 470);

    context.fillStyle = '#F2994A';
    context.font = '800 22px Nunito, Arial, sans-serif';
    context.fillText(row.line, 164, y + 74, 470);

    context.fillStyle = '#6B7280';
    context.font = '700 16px Nunito, Arial, sans-serif';
    context.fillText(row.extra, 164, y + 102, 470);
  });

  return new Promise<boolean>((resolve) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        resolve(false);
        return;
      }

      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      resolve(true);
    }, 'image/png');
  });
}
