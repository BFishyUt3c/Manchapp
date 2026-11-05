from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.util import Inches, Pt


NAVY = RGBColor(0x12, 0x1A, 0x24)
INK = RGBColor(0x1A, 0x23, 0x32)
CREAM = RGBColor(0xF6, 0xF0, 0xE6)
PAPER = RGBColor(0xFF, 0xFC, 0xF7)
CLAY = RGBColor(0xE0, 0x7A, 0x3D)
CLAY_DEEP = RGBColor(0xC4, 0x5A, 0x24)
MOSS = RGBColor(0x2F, 0x7D, 0x5C)
SAND = RGBColor(0xE8, 0xDD, 0xC8)
MUTED = RGBColor(0x5C, 0x64, 0x6E)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
LINE = RGBColor(0xD7, 0xCB, 0xB6)

W = Inches(13.333)
H = Inches(7.5)


def set_run(run, text, size, color, bold=False, name="Calibri"):
    run.text = text
    run.font.size = Pt(size)
    run.font.color.rgb = color
    run.font.bold = bold
    run.font.name = name


def add_run_para(tf, text, size, color, bold=False, align=PP_ALIGN.LEFT, space_after=6, first=False):
    p = tf.paragraphs[0] if first else tf.add_paragraph()
    p.alignment = align
    p.space_after = Pt(space_after)
    run = p.add_run()
    set_run(run, text, size, color, bold)
    return p


def box(slide, left, top, width, height, text, size, color, bold=False, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP):
    shape = slide.shapes.add_textbox(left, top, width, height)
    tf = shape.text_frame
    tf.word_wrap = True
    tf.auto_size = None
    try:
        tf._txBody.bodyPr.set("anchor", "t" if anchor == MSO_ANCHOR.TOP else "ctr")
    except Exception:
        pass
    p = tf.paragraphs[0]
    p.alignment = align
    run = p.add_run()
    set_run(run, text, size, color, bold)
    return shape


def fill(shape, color):
    shape.fill.solid()
    shape.fill.fore_color.rgb = color
    shape.line.fill.background()


def rect(slide, left, top, width, height, color):
    shape = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, left, top, width, height)
    fill(shape, color)
    return shape


def rrect(slide, left, top, width, height, color):
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, height)
    fill(shape, color)
    return shape


def line(slide, left, top, width, color=LINE):
    shape = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, left, top, width, Pt(1.5))
    fill(shape, color)
    return shape


def footer(slide, n, total=8, dark=False):
    color = RGBColor(0x9A, 0xA3, 0xAD) if dark else MUTED
    box(slide, Inches(0.7), Inches(7.12), Inches(8), Inches(0.28), "ManchApp  ·  PI III  ·  Olla Mujeres Emprendedoras", 11, color)
    box(slide, Inches(11.4), Inches(7.12), Inches(1.3), Inches(0.28), f"{n:02d} / {total:02d}", 11, color, True, PP_ALIGN.RIGHT)


def canvas(slide, bg=PAPER):
    rect(slide, 0, 0, W, H, bg)


def accent_bar(slide, color=CLAY):
    rect(slide, 0, 0, Inches(0.14), H, color)


def kicker(slide, text, top=Inches(0.38), color=CLAY):
    box(slide, Inches(0.7), top, Inches(12), Inches(0.32), text.upper(), 12, color, True)


def heading(slide, text, top=Inches(0.68)):
    box(slide, Inches(0.7), top, Inches(12.2), Inches(0.7), text, 30, NAVY, True)


def make():
    prs = Presentation()
    prs.slide_width = W
    prs.slide_height = H
    blank = prs.slide_layouts[6]
    total = 8

    # 1 PORTADA CONCEPTUAL
    s = prs.slides.add_slide(blank)
    canvas(s, NAVY)
    rect(s, 0, 0, Inches(0.18), H, CLAY)
    rect(s, Inches(8.9), 0, Inches(4.45), H, RGBColor(0x18, 0x23, 0x30))
    rrect(s, Inches(9.35), Inches(1.5), Inches(3.5), Inches(2.1), RGBColor(0x1E, 0x2C, 0x3A))
    box(s, Inches(9.55), Inches(1.7), Inches(3.1), Inches(0.4), "LÍNEA SOCIAL", 12, MOSS, True)
    box(s, Inches(9.55), Inches(2.1), Inches(3.1), Inches(1.2), "Menú S/ 3.00\n52 empadronados\n8 casos sociales", 16, CREAM)
    rrect(s, Inches(9.35), Inches(3.9), Inches(3.5), Inches(2.1), RGBColor(0x1E, 0x2C, 0x3A))
    box(s, Inches(9.55), Inches(4.1), Inches(3.1), Inches(0.4), "LÍNEA COMERCIAL", 12, CLAY, True)
    box(s, Inches(9.55), Inches(4.5), Inches(3.1), Inches(1.2), "Papa rellena, causa,\ncachangas, postres.\nCaja chica de la olla.", 16, CREAM)
    box(s, Inches(0.7), Inches(1.15), Inches(8), Inches(0.35), "PROYECTOS INTERDISCIPLINARIOS III  ·  UTEC / UNACEM", 13, CLAY, True)
    box(s, Inches(0.7), Inches(1.7), Inches(8), Inches(1.6), "ManchApp", 60, WHITE, True)
    box(
        s,
        Inches(0.7),
        Inches(3.4),
        Inches(7.8),
        Inches(1.6),
        "Canal de comercialización para la Olla Común\nMujeres Emprendedoras — Manchay, Pachacámac.",
        22,
        CREAM,
    )
    box(
        s,
        Inches(0.7),
        Inches(5.4),
        Inches(7.8),
        Inches(1.2),
        "No es una tienda genérica. Es el cuaderno, el afiche y el mototaxi\npuestos en un solo flujo, para que la venta comercial sostenga el menú social.",
        16,
        RGBColor(0xB7, 0xC0, 0xC8),
    )
    footer(s, 1, total, dark=True)

    # 2 EL PROBLEMA COMO FLUJO
    s = prs.slides.add_slide(blank)
    canvas(s)
    accent_bar(s)
    kicker(s, "El caso de campo")
    heading(s, "La olla no falla por falta de cocina. Falla el canal.")
    box(
        s,
        Inches(0.7),
        Inches(1.45),
        Inches(12),
        Inches(0.7),
        "En Manchay, la Sra. Delia Burgos y el Sr. León Zárate alimentan a 52 personas empadronadas y 8 casos sociales. El MIDIS se retrasa meses. Sin ferias y sin un canal ordenado, la línea comercial (papa rellena, causa, cachangas) no alcanza a cubrir el hueco.",
        16,
        MUTED,
    )
    steps = [
        ("01", "Cocinan", "Ración diaria y antojitos en el Módulo Carrió."),
        ("02", "Anotan", "Cuentas a mano. No saben cuánto entra por producto."),
        ("03", "Esperan", "El cliente tiene que pasar frente al módulo."),
        ("04", "Se corta", "Sin delivery ni afiche, la venta muere al caer la tarde."),
    ]
    for i, (n, t, b) in enumerate(steps):
        left = Inches(0.7) + Inches(i * 3.15)
        rrect(s, left, Inches(2.4), Inches(2.95), Inches(3.55), WHITE)
        rect(s, left, Inches(2.4), Inches(2.95), Inches(0.08), CLAY if i < 3 else MOSS)
        box(s, left + Inches(0.2), Inches(2.65), Inches(2.5), Inches(0.4), n, 14, CLAY, True)
        box(s, left + Inches(0.2), Inches(3.1), Inches(2.5), Inches(0.55), t, 22, NAVY, True)
        box(s, left + Inches(0.2), Inches(3.75), Inches(2.5), Inches(1.8), b, 15, MUTED)
    footer(s, 2, total)

    # 3 QUE SI ABARCAMOS
    s = prs.slides.add_slide(blank)
    canvas(s)
    accent_bar(s, MOSS)
    kicker(s, "Recorte del desafío", Inches(0.38), MOSS)
    heading(s, "Abarcamos el canal. No el análisis de precios.")
    box(
        s,
        Inches(0.7),
        Inches(1.45),
        Inches(12),
        Inches(0.65),
        "En campo vimos varios dolores. El tablero de costos y ganancias lo deja otro frente: ya hay quien analizó precios y no hacemos sugerencias económicas. Este equipo cierra dos desafíos: cómo se ve y se publica el producto, y cómo llega a casa.",
        16,
        MUTED,
    )
    rrect(s, Inches(0.7), Inches(2.3), Inches(6.05), Inches(4.3), NAVY)
    box(s, Inches(0.95), Inches(2.5), Inches(5.55), Inches(0.4), "SÍ ENTRA EN ESTE PLAN", 13, CLAY, True)
    yes = [
        "Fotos malas: cocina oscura, fondo saturado, plato poco apetecible.",
        "No saben de marketing: no hay combo, horario ni mensaje listo.",
        "No saben publicar: WhatsApp se usa, pero sin afiche ni texto.",
        "No hay delivery formal: el pedido informal no llega ordenado.",
        "El prototipo debe demostrar ese flujo, no un ERP financiero.",
    ]
    tb = s.shapes.add_textbox(Inches(0.95), Inches(3.0), Inches(5.55), Inches(3.3))
    tf = tb.text_frame
    tf.word_wrap = True
    for i, item in enumerate(yes):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(10)
        run = p.add_run()
        set_run(run, "▸  " + item, 15, CREAM)
    rrect(s, Inches(7.0), Inches(2.3), Inches(5.6), Inches(4.3), SAND)
    box(s, Inches(7.25), Inches(2.5), Inches(5.15), Inches(0.4), "QUEDA FUERA (OTRO EQUIPO)", 13, CLAY_DEEP, True)
    no = [
        "Calculadora de costo unitario y margen neto.",
        "Sugerir precios del menú social o de la línea comercial.",
        "Tablero de deudas MIDIS o fondo de casos sociales.",
        "Reemplazar el análisis económico ya hecho en campo.",
    ]
    tb = s.shapes.add_textbox(Inches(7.25), Inches(3.05), Inches(5.15), Inches(3.2))
    tf = tb.text_frame
    tf.word_wrap = True
    for i, item in enumerate(no):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(12)
        run = p.add_run()
        set_run(run, "–  " + item, 15, INK)
    footer(s, 3, total)

    # 4 FLUJO DE LA SOLUCION
    s = prs.slides.add_slide(blank)
    canvas(s)
    accent_bar(s)
    kicker(s, "ManchApp como flujo, no como lista de pantallas")
    heading(s, "De la olla al vecino, en un solo recorrido")
    box(
        s,
        Inches(0.7),
        Inches(1.42),
        Inches(12),
        Inches(0.5),
        "La app no es un menú de módulos sueltos. Es una secuencia: se ve el plato, se publica, se pide, se cobra y se entrega. El prototipo actual es el esqueleto de esa secuencia.",
        16,
        MUTED,
    )
    flow = [
        ("Ver", "Catálogo dual: menú social S/ 3.00 y línea comercial (salados + antojitos). Ofertas tipo combo merienda, familiar, hora del antojo."),
        ("Mostrar", "Foto del plato (IA o banco precargado) + flyer en 3 datos: plato, precio, horario. Un toque a Estados de WhatsApp."),
        ("Pedir", "La clienta filtra, arma pedido y elige recojo en Módulo Carrió o delivery por zona."),
        ("Cobrar", "QR Yape/Plin unificado o efectivo contra entrega. Sin pasarela bancaria pesada."),
        ("Llegar", "Zona cercana / intermedia / ampliada. Mototaxi aliada. Estados: recibido, preparación, camino, entregado."),
    ]
    for i, (t, b) in enumerate(flow):
        top = Inches(2.05) + Inches(i * 0.92)
        rrect(s, Inches(0.7), top, Inches(12.0), Inches(0.82), WHITE)
        rrect(s, Inches(0.85), top + Inches(0.18), Inches(1.55), Inches(0.46), CLAY if i % 2 == 0 else MOSS)
        box(s, Inches(0.85), top + Inches(0.24), Inches(1.55), Inches(0.38), t.upper(), 12, WHITE, True, PP_ALIGN.CENTER)
        box(s, Inches(2.6), top + Inches(0.16), Inches(9.8), Inches(0.55), b, 15, INK)
    footer(s, 4, total)

    # 5 MARKETING + DELIVERY + PROTOTIPO
    s = prs.slides.add_slide(blank)
    canvas(s)
    accent_bar(s, CLAY)
    kicker(s, "Los dos desafíos que sí resolvemos")
    heading(s, "Marketing visual + delivery, anclados al prototipo")
    cols = [
        (
            "Marketing que sí pueden usar",
            "No les pedimos Canva ni Instagram. Completan plato, precio y horario; sale un afiche vertical. Si la foto de cocina es mala, la IA recorta el plato o usan el banco (cachanga, papa rellena, causa, mazamorra). Publicar en Estados y grupos de WhatsApp, que ya es su canal. Combos: merienda, familiar, hora del antojo y promo delivery por monto mínimo. Público: vecinos, trabajadores, estudiantes y transeúntes. Horarios: almuerzo salado / tarde-noche antojitos.",
            CLAY,
        ),
        (
            "Delivery ManchApp",
            "Hoy el pedido informal no escala. El flujo es: pedido → zona de cobertura → recojo o domicilio → preparación → mototaxi → entrega. Cobertura inicial cerca de la olla; luego intermedia y ampliada (Portada, Huertos, Virgen de Chapi, Oasis, etc.). La “mamá intermedia” o mototaxis aliadas cubren cuadrantes. El prototipo debe mostrar estados del pedido y la tarifa por zona, aunque al inicio sea simulado.",
            MOSS,
        ),
        (
            "Qué demuestra el prototipo ahora",
            "Bosquejo en celular: login, vista clienta (catálogo, oferta desplegable, Pedir ya) y vista admin (subir producto/oferta con foto obligatoria, guía de foto, QR Yape/Plin, descarga de afiche). Sirve para validar el recorrido. Aún no hay IA real, mapa de zonas ni mototaxi. Eso entra en el plan de las siguientes semanas, no se finge como producto terminado.",
            NAVY,
        ),
    ]
    for i, (t, b, c) in enumerate(cols):
        left = Inches(0.55) + Inches(i * 4.2)
        rrect(s, left, Inches(1.55), Inches(4.0), Inches(5.2), NAVY if i == 2 else WHITE)
        rect(s, left, Inches(1.55), Inches(4.0), Inches(0.1), c)
        title_c = WHITE if i == 2 else NAVY
        body_c = CREAM if i == 2 else MUTED
        box(s, left + Inches(0.22), Inches(1.8), Inches(3.55), Inches(0.85), t, 18, title_c, True)
        box(s, left + Inches(0.22), Inches(2.7), Inches(3.55), Inches(3.7), b, 13, body_c)
    footer(s, 5, total)

    # 6 PLAN DE ACCION SEMANAS
    s = prs.slides.add_slide(blank)
    canvas(s)
    accent_bar(s, MOSS)
    kicker(s, "De bosquejo a prototipo defendible", Inches(0.32), MOSS)
    heading(s, "Plan de acción — próximas 4 semanas", Inches(0.58))
    weeks = [
        ("Semana 1", "Cerrar estructura", "Separar catálogo dual (social / comercial). Banco de fotos de productos. Flyer con 3 campos. Dejar el marco de celular y los dos roles estables."),
        ("Semana 2", "Marketing usable", "Botón Publicar en WhatsApp (afiche + texto). Combos del PDF. Guía de foto + simulación de mejora. Probar con 5 productos reales de la olla."),
        ("Semana 3", "Delivery", "Pedido con recojo o domicilio. Tres zonas. Estados del pedido. Promo delivery por monto. Aún puede ser simulado, pero el flujo tiene que verse entero."),
        ("Semana 4", "Campo y cierre", "QR de pago en el afiche. Recorrido con Sra. Delia: ¿entiende? ¿publica en <1 min? Ajustar textos. Ensayo de defensa: problema → flujo → demo → qué falta."),
    ]
    for i, (w, t, b) in enumerate(weeks):
        top = Inches(1.4) + Inches(i * 1.32)
        rrect(s, Inches(0.7), top, Inches(12.0), Inches(1.18), WHITE)
        rrect(s, Inches(0.85), top + Inches(0.28), Inches(1.9), Inches(0.62), MOSS if i % 2 == 0 else CLAY)
        box(s, Inches(0.85), top + Inches(0.38), Inches(1.9), Inches(0.45), w.upper(), 12, WHITE, True, PP_ALIGN.CENTER)
        box(s, Inches(3.0), top + Inches(0.16), Inches(9.4), Inches(0.35), t, 18, NAVY, True)
        box(s, Inches(3.0), top + Inches(0.52), Inches(9.4), Inches(0.55), b, 14, MUTED)
    footer(s, 6, total)

    # 7 HITOS Y CRITERIO DE LISTO
    s = prs.slides.add_slide(blank)
    canvas(s)
    accent_bar(s)
    kicker(s, "Cómo sabemos que el proyecto está completo")
    heading(s, "Criterio de “listo” para las siguientes semanas")
    box(
        s,
        Inches(0.7),
        Inches(1.42),
        Inches(12),
        Inches(0.55),
        "No medimos el éxito por cantidad de pantallas. Lo medimos porque una socia puede vender sin diseñador, sin cuaderno y sin que el cliente esté parado en el módulo.",
        16,
        MUTED,
    )
    criteria = [
        ("Foto", "Toda publicación sale con imagen usable: banco o mejora. Cero fotos oscuras en el catálogo que se comparte."),
        ("Afiche", "De plato + precio + horario a imagen lista en menos de un minuto. Un botón a WhatsApp."),
        ("Pedido", "La clienta pide. La olla ve recojo o delivery y el estado hasta “entregado”."),
        ("Pago", "Se ve el QR o el efectivo. No hay que salir a otra app a inventar el mensaje."),
        ("Campo", "Delia o una socia completa el recorrido sin que el equipo le dicte cada toque."),
        ("Límite", "No entregamos análisis de precios. Si preguntan costos, redirigimos al otro frente."),
    ]
    for i, (t, b) in enumerate(criteria):
        col = i % 3
        row = i // 3
        left = Inches(0.7) + Inches(col * 4.15)
        top = Inches(2.15) + Inches(row * 2.25)
        rrect(s, left, top, Inches(3.95), Inches(2.05), WHITE)
        box(s, left + Inches(0.25), top + Inches(0.22), Inches(3.45), Inches(0.4), t, 18, CLAY, True)
        box(s, left + Inches(0.25), top + Inches(0.7), Inches(3.45), Inches(1.15), b, 14, MUTED)
    footer(s, 7, total)

    # 8 CIERRE
    s = prs.slides.add_slide(blank)
    canvas(s, NAVY)
    rect(s, 0, 0, Inches(0.18), H, MOSS)
    box(s, Inches(0.7), Inches(1.5), Inches(12), Inches(0.35), "CIERRE", 13, CLAY, True)
    box(s, Inches(0.7), Inches(2.0), Inches(12), Inches(1.5), "Que el menú social no dependa\nde que alguien pase caminando.", 32, WHITE, True)
    box(
        s,
        Inches(0.7),
        Inches(3.8),
        Inches(11.5),
        Inches(1.4),
        "ManchApp ordena lo que ya hacen: vender papa rellena y cachangas para sostener el almuerzo de S/ 3.00.\nEl prototipo muestra el camino. Las próximas cuatro semanas lo vuelven defendible en campo:\nmejor foto, mejor afiche, pedido con delivery.",
        18,
        CREAM,
    )
    box(s, Inches(0.7), Inches(5.7), Inches(12), Inches(0.5), "Gracias  —  preguntas sobre el flujo, no sobre el precio.", 20, CLAY, True)
    footer(s, 8, total, dark=True)

    out = Path(__file__).resolve().parents[1] / "Ollas-Manchay-Presentacion.pptx"
    prs.save(out)
    print(out)


if __name__ == "__main__":
    make()
