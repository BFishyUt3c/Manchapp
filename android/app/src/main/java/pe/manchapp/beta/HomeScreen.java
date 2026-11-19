package pe.manchapp.beta;

import android.widget.LinearLayout;

final class HomeScreen {

    static void showHome(AppShell shell) {
        shell.title("Tu espacio de venta", "Programa, prepara y comparte cada jornada de Manka Kusi.");
        shell.menu("Programar una venta", "Elige productos, fecha, cierre de reservas y horario de recojo", "clock",
                () -> ScheduleScreen.openSchedule(shell, false));
        shell.menu("Crear un afiche", "Combina hasta cuatro productos y prepara una publicación", "publish",
                () -> ComboEditorScreen.selectProduct(shell, false));
        shell.menu("Crear un combo", "Define contenido, precio, cupos y fecha de venta", "tag",
                () -> ComboEditorScreen.selectProduct(shell, true));
        shell.menu("Anunciar próxima venta", "Prepara un Story Flash para avisar tu siguiente jornada", "publish",
                () -> FlyerStudioScreen.openNextSalePromo(shell));
        shell.menu("Compartir catálogo por WhatsApp", "Envía tu lista de productos y link en un toque", "share",
                () -> FlyerStudioScreen.shareCatalogWhatsApp(shell));
    }

    static void showPublications(AppShell shell) {
        shell.title("Tus imágenes", "Edita y guarda fotos aunque todavía no hayas creado un producto.");
        shell.menu("Editar una foto", "Recorta, corrige el fondo, combina productos y guarda el resultado", "photo",
                shell::openStudio);
        shell.menu("Ayuda para empezar", "Luz, encuadre, fondo y corrección manual paso a paso", "help",
                () -> shell.go("help", ""));
        LinearLayout guide = shell.ui().message("Una foto clara vende mejor",
                "1. Usa luz natural\n2. Muestra el producto completo\n3. Evita fondos con muchos objetos\n4. Revisa el recorte antes de guardar",
                NativeUi.GOLD);
        shell.add(shell.content(), guide, 20);
    }

    static void showHelp(AppShell shell) {
        shell.title("Ayuda para tus fotos", "Consejos simples antes de abrir el editor.");
        LinearLayout content = shell.content();
        NativeUi ui = shell.ui();
        shell.add(content, ui.message("1. Busca buena luz", "Acércate a una ventana o toma la foto de día. Evita colocar el producto frente a una luz fuerte.", NativeUi.GOLD), 16);
        shell.add(content, ui.message("2. Muestra todo el producto", "Deja un pequeño espacio alrededor del plato, vaso o empaque para que el recorte no corte sus bordes.", NativeUi.CARD), 12);
        shell.add(content, ui.message("3. Usa un fondo sencillo", "Una mesa o mantel sin muchos objetos ayuda a separar mejor el producto.", NativeUi.CARD), 12);
        shell.add(content, ui.message("4. Revisa antes de guardar", "Puedes borrar o recuperar partes del recorte manualmente y combinar varios productos en una imagen.", NativeUi.SOFT), 12);
        shell.primary("Editar una foto", shell::openStudio);
    }
}
