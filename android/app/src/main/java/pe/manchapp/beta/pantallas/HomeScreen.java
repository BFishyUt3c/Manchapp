package pe.manchapp.beta;

import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONObject;

final class HomeScreen {

    static void showHome(AppShell shell) {
        JSONObject data = shell.data();
        JSONObject settings = data.optJSONObject("settings");
        String name = settings != null && !settings.optString("name").isEmpty()
                ? settings.optString("name")
                : "tu olla";

        shell.title("Tu espacio de venta", "Programa, prepara y comparte cada jornada de " + name + ".");

        // Tarjeta de resumen de la próxima jornada
        LinearLayout summary = shell.card();
        boolean hasSchedule = settings != null && SaleSchedule.invalid(settings).isEmpty();
        if (hasSchedule) {
            boolean open = settings.optBoolean("salesOpen", false);
            shell.add(summary, shell.text(open ? "PRÓXIMA VENTA ABIERTA" : "VENTA PAUSADA", 12,
                    open ? NativeUi.GREEN : NativeUi.MUTED, true), 0);
            shell.add(summary, shell.text(SaleSchedule.day(settings), 18, NativeUi.WINE, true), 4);
            shell.add(summary, shell.text(SaleSchedule.shortWindow(settings), 14, NativeUi.INK, false), 4);
        } else {
            shell.add(summary, shell.text("SIN JORNADA PROGRAMADA", 12, NativeUi.MUTED, true), 0);
            shell.add(summary, shell.text("Todavía no hay una fecha de venta activa.", 16, NativeUi.INK, true), 4);
            shell.add(summary, shell.text("Programa tu próxima jornada para empezar a recibir pedidos.", 13, NativeUi.MUTED, false), 4);
        }
        shell.add(shell.content(), summary, 14);

        // Aviso rápido si hay pedidos por confirmar
        int pendingOrders = countPendingOrders(data.optJSONArray("orders"));
        if (pendingOrders > 0) {
            shell.menu(
                    pendingOrders + (pendingOrders == 1 ? " pedido por confirmar" : " pedidos por confirmar"),
                    "Toca aquí para revisar y confirmar tus ventas",
                    "orders",
                    () -> shell.go("orders", "")
            );
        }

        // 3 acciones principales
        shell.menu("Programar una venta", "Elige productos, fecha, cierre de reservas y horario de recojo", "clock",
                () -> ScheduleScreen.openSchedule(shell, false));
        shell.menu("Compartir catálogo por WhatsApp", "Envía tu lista de productos y link en un toque", "share",
                () -> FlyerStudioScreen.shareCatalogWhatsApp(shell));
        shell.menu("Anunciar próxima venta", "Prepara un Story Flash para avisar tu siguiente jornada", "publish",
                () -> FlyerStudioScreen.openNextSalePromo(shell));
    }

    private static int countPendingOrders(JSONArray orders) {
        int count = 0;
        for (int i = 0; orders != null && i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order != null && "registered".equals(order.optString("status"))) {
                count++;
            }
        }
        return count;
    }

    static void showPublications(AppShell shell) {
        shell.title("Tus imágenes", "Edita fotos y arma afiches para tus redes o WhatsApp.");
        shell.menu("Editar una foto", "Recorta, corrige el fondo, combina productos y guarda el resultado", "photo",
                shell::openStudio);
        shell.menu("Crear un afiche", "Combina hasta cuatro productos y prepara una publicación", "publish",
                () -> ComboEditorScreen.selectProduct(shell, false));
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