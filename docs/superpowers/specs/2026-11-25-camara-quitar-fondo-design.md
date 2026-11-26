# Diseño: quitar fondo de fotos tomadas con la cámara

Fecha: 2026-11-25 · Estado: aprobado (con presupuesto de RAM de hasta ~1 GB)

> Corrección del 2026-11-26: el modelo U²-NetP **ya está incluido** en `android/app/src/sellerAssets/models/u2netp.onnx` (hash correcto y licencias en `sellerAssets/licenses/`), porque `build.gradle:17` fusiona `src/sellerAssets` como assets. No se agrega ningún binario. La causa real está en la cadena de imagen y en el manejo de máscaras dudosas.

## Intento y por qué

En la expo se tomarán fotos reales con la cámara (un objeto simple sostenido o mostrado por la emprendedora) y se debe poder tocar **“Sin fondo”** y obtener un recorte real con transparencia. Hoy la herramienta solo funciona bien con PNG/JPG limpios de catálogo; con una foto opaca de cámara el botón falla y muestra un mensaje, no recorta.

## Objetivos

- Que “Sin fondo” produzca un PNG con cancha alfa real: fondo `alpha=0`, objeto `alpha>0`.
- Que funcione sin internet, sin API keys y sin dependencias nuevas.
- Que corra en celulares de 2 GB sin perder la edición previa si falla.
- Que haya un camino guiado (“toca el objeto”) cuando la inferencia automática no sirva.

## No objetivos

- No matting perfecto de cabello/persona; U²-NetP es el modelo más débil en precisión (MAE ~0,0718).
- No texto sobre fotos ni el bug de zonas de entrega (son trabajos separados).
- No servicios en la nube (skills `remove-background`, ML Kit, etc.).

## Anti-objetivos (qué sería un fracaso)

- Mostrar “listo” o un mensaje de éxito sin haber generado píxeles transparentes.
- Volver a bajar la foto de cámara a 960 px y comprimirla dos veces antes de editarla.
- Que el guardado reduzca un recorte transparente a 128 px por pasar de 670 KB.

## Restricciones

- Java, Android, OpenCV 4.12.0 (`org.opencv:opencv:4.12.0`), `minSdk 24`, `targetSdk 35`.
- Working set de 1280 px por foto; `Core.setNumThreads(2)` ya está puesto. El usuario autorizó hasta ~1 GB de RAM, así que no hace falta bajar la resolución de trabajo para ahorrar memoria.
- Tests Robolectric: `FoodPhotoEngine` se reemplaza con un shadow, así que la inferencia real no se prueba en unit tests; se requiere verificación manual en dispositivo.

## Estado actual (causa raíz)

- `FoodPhotoEngine.modelFile()` (`FoodPhotoEngine.java:114-117`) abre `assets/models/u2netp.onnx` y valida SHA-256 `309c8469…`. Ese archivo **sí existe**, en `android/app/src/sellerAssets/models/u2netp.onnx` (4,6 MB, hash correcto), y Gradle lo fusiona como asset (`android/app/build.gradle:17`). Las licencias ya están en `sellerAssets/licenses/`.
- La foto de cámara se baja a 960 px (`ImageTools.read`, `ImageTools.java:76-83`), se re-codifica a JPEG ≤670 KB (`ImageTools.encodeEdited`, `ImageTools.java:93-105`), se vuelve a decodificar y se escribe otra vez antes del editor (`PhotoStudioActivity.java:53-64`), que finalmente abre a 1280 px (`PhotoEditorActivity.java:40`). Detalle y nitidez ya están perdidos antes de segmentar.
- `finishMask` (`FoodPhotoEngine.java:98-101`) rechaza máscaras con cobertura <1 % o >98,5 % y lanza un `IOException` genérico; `PhotoEditorActivity.renderPhoto` (`PhotoEditorActivity.java:86`) solo muestra un `Toast` y conserva la edición, sin ofrecer una alternativa de recorte. En fotos reales con persona/fondo complejo esto es lo que termina en “no hizo nada”.

## Enfoques considerados

1. **Conservar el modelo + respaldo guiado y arreglar la cámara (elegido).** El modelo ya está incluido; se corrige la entrada de imagen y se abre GrabCut guiado cuando la máscara automática no es clara. Sin dependencias nuevas y sin agregar binarios.
2. **MediaPipe Interactive Image Segmenter.** Mejor interacción “toca el objeto”, pero agrega `com.google.mediapipe:tasks-vision` y otro modelo → requiere aprobación de dependencia nueva.
3. **ML Kit Subject Segmentation.** Descarga el modelo vía Play Services la primera vez (API beta, ~200 KB en la app) → depende de Play/internet; descartado para expo offline.
4. **Solo GrabCut.** Cero dependencias, pero calidad inferior; se conserva como respaldo, no como solución principal.

## Diseño

1. **Modelo existente.** No se copia nada: se mantiene `src/sellerAssets/models/u2netp.onnx` y sus licencias. `ModelAssetTest` verifica que siga accesible con tamaño 4.574.861 y SHA-256 `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8` (regresión).
2. **Fallo controlado.** `FoodPhotoEngine.finishMask()` lanza `MaskFallbackException` cuando la cobertura es dudosa; inferencia fallida y máscara no clara disparan el modo guiado. Si el modo guiado tampoco sirve, se abre el pincel manual. No se puede salir del botón “Sin fondo” sin resultado ni sin ofrecer la alternativa.
3b. **Recorte automático más limpio (ajuste del 2026-11-26).** Cuando no está activo “Incluir plato y acompañamientos”, la máscara de U²-NetP se refina con GrabCut sembrado desde sus propias zonas de alta/baja confianza y se **intersecta** con la máscara original. Antes se hacía una unión (`Core.max`) con GrabCut, lo que conservaba el mantel/encaje alrededor del objeto. El interruptor sigue disponible para el caso real de comida con plato y acompañamientos, pero el flujo desde cámara ahora abre con `includeObjects=false`.
3. **Modo guiado “Toca el objeto”.** Nuevo diálogo en `PhotoEditorActivity` con la foto y un toque del usuario. `FoodPhotoEngine.guidedMask(x,y)` siembra GrabCut: disco pequeño alrededor del toque = `GC_FGD`, borde exterior = `GC_BGD`, resto = `GC_PR_BGD`; 3 iteraciones; suavizado y conversión a máscara float. La máscara se guarda como máscara manual para que sobreviva como el pincel actual. Si aun así falla, se abre `MaskEditorActivity` (pincel existente).
4. **Cadena de cámara.** `ImageTools.read(Context,Uri,int)` con `edge=1280` para cámara (galería sigue en 960). `PhotoStudioActivity` escribe el archivo de entrada del editor **una sola vez** desde el bitmap decodificado (JPEG calidad ~92, PNG si hay alfa), sin el límite de 670 KB; el workspace base64 queda como está. `PhotoEditorActivity` sigue abriendo a 1280.
5. **Guardado.** `ImageTools.encodeEdited`: límite PNG 670 KB → 1.2 MB para fotos editadas con transparencia; si se excede, reducir hasta 960 px de lado y devolver aunque siga por encima del límite (nunca bajar de 960 ni fallar). QR conserva 600 KB.
6. **UI.** Agregar en la pestaña Fondo el botón “Toca el objeto” y mensajes de progreso claros (“Separando el fondo…”, “Toca el objeto…”). `Antes/Después` y el pincel manual quedan intactos.

## Verificación y aceptación

### Automática (unit tests, deben quedar verdes)

- `ModelAssetTest` (nuevo): el asset existe, mide 4.574.861 bytes y su SHA-256 es el esperado (no necesita OpenCV nativo).
- `PhotoEditorUiTest`: con `PreviewRenderer.failMask=true`, al tocar “Sin fondo” se abre el diálogo guiado (no solo un `Toast`); al tocar el centro se aplica una máscara y el resultado tiene `alpha=0` en la esquina y `alpha=255` en el centro.
- `PhotoFlowTest` / nueva prueba en `ImageTools`: decode de cámara a 1280 y guardado PNG transparente sin caer por debajo de 960 px.
- Regresión: los 33 tests actuales siguen pasando; `./gradlew.bat :app:testDebugUnitTest :clientes:testDebugUnitTest`.

### Manual obligatoria en dispositivo (criterio de “sí borra”)

1. Instalar `:app` con la clave demo. Tomar foto con “Tomar foto” de un objeto simple sobre fondo de otro color (ej. taza blanca sobre mesa oscura).
2. “Editar foto” → pestaña Fondo → “Sin fondo”. Esperar a que termine (progress).
3. “Listo” → “Guardar” → exportar PNG. Abrir el PNG: **el fondo debe verse transparente** (cuadros grises en el visor) y el objeto completo. Si el visor no muestra transparencia, revisar con `ImageTools.hasTransparency` en el flujo.
4. Repetir con la emprendedora sosteniendo el producto: si la máscara automática se come el plato o incluye la persona, usar “Toca el objeto” y luego “Borrar fondo a mano”; la edición anterior no debe perderse.
5. Compilar `:app:assembleDebug :clientes:assembleDebug` sin errores.

## Riesgos y mitigación

- **Memoria:** U²-NetP reporta ~0,5 GB pico; hay presupuesto de hasta ~1 GB, así que se mantiene 1280 px y 2 hilos sin recortes agresivos. Aun así se liberan los Mats y, si falla, se conserva la última edición y se ofrece el modo guiado.
- **Precisión del modelo:** es el más débil del benchmark; para la demo se elige un objeto simple de alto contraste. El modo guiado y el pincel cubren los casos difíciles.
- **Modelo ya incluido:** no se modifica el binario ni las licencias; `ModelAssetTest` solo evita regressions. No se hace commit sin permiso explícito.

## Decisiones del autor (sujetas a revisión)

- Se descarta MediaPipe y ML Kit para no agregar dependencias ni descargas en tiempo de ejecución.
- El modo guiado usa GrabCut de OpenCV ya presente, no un modelo nuevo.
- Los textos de UI y mensajes de error se mantienen en español, como el resto de la app.

## Pendiente

- Diseño aprobado; siguiente paso: plan paso a paso (`writing-plans`).
- Ejecutar los cambios en `C:\Users\Lenovo\Documents\Manchapp-Final` (o la carpeta que indiques), sin commit/push salvo autorización explícita.
