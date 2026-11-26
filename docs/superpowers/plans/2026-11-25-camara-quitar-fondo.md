# Quitar fondo de fotos de cámara Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que “Sin fondo” recorte de verdad una foto tomada con la cámara, con respaldo guiado cuando la inferencia falle.

**Architecture:** El modelo U²-NetP ya está incluido en `src/sellerAssets/models/`; se conserva y se verifica. Se convierte la máscara dudosa en una señal que abre “Toca el objeto” (GrabCut sembrado por un toque); se corrige la cadena de cámara para que el editor reciba 1280 px y una sola compresión; y se evita que el guardado transparente degradue el recorte.

**Tech Stack:** Java 8, Android (minSdk 24, targetSdk 35), OpenCV 4.12.0 (`org.opencv:opencv:4.12.0`, DNN CPU), Robolectric 4.17, Gradle 9.1.0 / AGP 9.0.1.

**Spec:** `docs/superpowers/specs/2026-11-25-camara-quitar-fondo-design.md`

## Global Constraints

- Sin dependencias Gradle nuevas, sin APIs remotas, sin internet en la app.
- Idioma de UI y mensajes: español.
- Resolución de trabajo de la foto: 1280 px; presupuesto de RAM autorizado: hasta ~1 GB.
- No se hace commit ni push sin autorización explícita del usuario.
- Working copy: `C:\Users\Lenovo\Documents\Manchapp-Final` (a menos que el usuario indique otra).
- Todo cambio se valida con `:app:testDebugUnitTest :clientes:testDebugUnitTest` y `:app:assembleDebug :clientes:assembleDebug`.

## Review Focus

- Foto opaca de cámara con un objeto simple sobre fondo contrastante: debe quitar el fondo de verdad → Task 3 test `guidedFallbackOpensTapDialogAndProducesTransparentCutout` + Task 6 verificación manual.
- Modelo incluido con hash distinto o máscara dudosa: debe abrir el modo guiado, no quedarse en un mensaje → Task 1 `ModelAssetTest` + Task 3 test de fallback.
- Foto de la emprendedora sosteniendo el producto: la máscara puede incluir la persona; debe ofrecer pincel y conservar la edición previa → Task 3 test `manualBrushCanOpenEvenWhenAutomaticCutoutFails` (existente) + Task 6 verificación manual.
- Foto grande (1600 px) decodificada para cámara: debe llegar al editor a 1280 px sin perder el archivo de entrada ni OOM → Task 4 `cameraDecodeUses1280AndWritesEditorSourceOnce`.
- Recorte transparente mayor a 670 KB: debe guardarse en PNG conservando al menos 960 px y `alpha=0` en el fondo → Task 5 `transparentEditedPhotoKeepsAtLeast960px`.

> Ajuste posterior (2026-11-26): el recorte automático con `includeObjects=false` usa `FoodPhotoEngine.refineWithGrabCut` (intersección con GrabCut sembrado por la propia máscara) en vez de la unión `completeObjects`, y `PhotoStudioActivity` abre el editor con `includeObjects=false`. `PhotoFlowTest.cameraDecodeUses1280AndWritesEditorSourceOnce` verifica ese valor.

---

### Task 1: Modelo U²-NetP existente y verificado (regresión)

**Files:**
- Verify: `android/app/src/sellerAssets/models/u2netp.onnx` (ya versionado)
- Verify: `android/app/src/sellerAssets/licenses/` (ya versionado)
- Test: `android/app/src/test/java/pe/manchapp/beta/ModelAssetTest.java`

**Interfaces:**
- Consumes: `assets.srcDirs += ['src/sellerAssets']` (`android/app/build.gradle:17`).
- Produces: ningún archivo nuevo; `ModelAssetTest` protege el modelo ya incluido con 4.574.861 bytes y SHA-256 `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8`.

- [ ] **Step 1: Write the test**

`ModelAssetTest` (`RobolectricTestRunner`): abrir `context.getAssets().open("models/u2netp.onnx")`, calcular SHA-256 con `MessageDigest`, afirmar tamaño `4574861` y digest `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8`. (Ya escrito y en verde; queda como guardia de regresión.)

- [ ] **Step 2: Run test**

Run: `./gradlew.bat :app:testDebugUnitTest --tests pe.manchapp.beta.ModelAssetTest`
Expected: PASS (verificado el 2026-11-26). No se agrega ningún binario ni NOTICE.

- [ ] **Step 3: Commit (solo si el usuario lo autoriza)**

`git add android/app/src/test/java/pe/manchapp/beta/ModelAssetTest.java docs/superpowers` y mensaje `test: verificar modelo u2netp incluido`.

---

### Task 2: Semilla geométrica para GrabCut guiado

**Files:**
- Create: `android/app/src/main/java/pe/manchapp/beta/imagenes/GuidedMaskSeed.java`
- Test: `android/app/src/test/java/pe/manchapp/beta/GuidedMaskSeedTest.java`

**Interfaces:**
- Consumes: ninguna.
- Produces: `static boolean GuidedMaskSeed.inside(int width,int height,int x,int y,float nx,float ny)` (píxel dentro del disco del toque); `static boolean GuidedMaskSeed.outer(int width,int height,int x,int y)` (píxel en la banda exterior al 8 % del borde). Coordenadas `nx,ny` normalizadas 0–1. La usa Task 3 desde `FoodPhotoEngine`.

- [ ] **Step 1: Write the failing test**

`GuidedMaskSeedTest`: con 400×400 y toque `(0.5,0.5)`, `inside(200,200,0.5f,0.5f)` es `true`, `inside(20,20,...)` es `false`, `outer(2,200)` es `true`, `outer(200,200)` es `false`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests pe.manchapp.beta.GuidedMaskSeedTest`
Expected: FAIL, clase no encontrada.

- [ ] **Step 3: Implementar `GuidedMaskSeed`**

Dos métodos estáticos puros; radio del disco = `max(6, min(width,height)/12)`; banda exterior = 8 % de cada lado. Sin dependencias de Android.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests pe.manchapp.beta.GuidedMaskSeedTest`
Expected: PASS.

- [ ] **Step 5: Commit (solo si el usuario lo autoriza)**

Mensaje: `feat: semilla geometrica para recorte guiado`.

---

### Task 3: Fallo controlado y botón “Toca el objeto”

**Files:**
- Modify: `android/app/src/main/java/pe/manchapp/beta/imagenes/FoodPhotoEngine.java:62-101`
- Modify: `android/app/src/main/java/pe/manchapp/beta/imagenes/PhotoEditorActivity.java:62-73`
- Test: `android/app/src/test/java/pe/manchapp/beta/PhotoEditorUiTest.java`

**Interfaces:**
- Consumes: `GuidedMaskSeed.inside/outer` (Task 2).
- Produces: `FoodPhotoEngine.MaskFallbackException extends IOException` (señal de “inferencia fallida o máscara no clara”); `Bitmap FoodPhotoEngine.guidedMaskBitmap(float nx,float ny) throws IOException` (máscara lista para `setManualMask`); `PhotoEditorActivity.openGuidedMask()`; tag `editor:guided-mask`; diálogo con tags `guided:stage` y `guided:apply`.

- [ ] **Step 1: Write the failing test**

En `PhotoEditorUiTest`: en el shadow `PreviewRenderer`, hacer que `render(...,"transparent")` lance `new FoodPhotoEngine.MaskFallbackException("fallo simulated")` cuando `failMask=true` (en vez del `IOException` genérico actual), y añadir `@Implementation protected Bitmap guidedMaskBitmap(float nx,float ny)` que guarde `lastGuidedTap` y devuelva una máscara con centro opaco. Nuevo test `guidedFallbackOpensTapDialogAndProducesTransparentCutout`: con `failMask=true`, abrir editor, pestaña Fondo, tocar `transparent`, esperar idle, afirmar que existe el diálogo `guided:stage`, tocar el centro, afirmar `lastGuidedTap` no nulo, resultado con `alpha=0` en `(0,0)` y `alpha=255` en el centro, y que `editor:save` sigue habilitado.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests pe.manchapp.beta.PhotoEditorUiTest`
Expected: FAIL en la aserción del diálogo (no existe).

- [ ] **Step 3: Implementar el fallback y el modo guiado**

En `FoodPhotoEngine`: convertir la excepción de DNN y el rechazo de cobertura en `finishMask` en `MaskFallbackException`; `guidedMaskBitmap` siembra GrabCut con `GuidedMaskSeed` (`GC_FGD` en el toque, `GC_BGD` en `outer`, `GC_PR_BGD` en el resto), 3 iteraciones, reutiliza `finishMask(soft,false)` y devuelve Bitmap. En `PhotoEditorActivity`: botón “Toca el objeto” en la pestaña Fondo; `openGuidedMask()` muestra el diálogo, convierte el toque a normalizadas, llama `guidedMaskBitmap`, aplica `setManualMask` y renderiza `transparent`; si falla, abre `MaskEditorActivity` como hoy. Los demás `catch` conservan la última edición.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests pe.manchapp.beta.PhotoEditorUiTest`
Expected: PASS, incluidos los 4 tests existentes de esa clase.

- [ ] **Step 5: Commit (solo si el usuario lo autoriza)**

Mensaje: `feat: modo guiado para quitar fondo cuando falla la mascara automatica`.

---

### Task 4: Foto de cámara a 1280 px y una sola compresión

**Files:**
- Modify: `android/app/src/main/java/pe/manchapp/beta/imagenes/ImageTools.java:76-83`
- Modify: `android/app/src/main/java/pe/manchapp/beta/imagenes/PhotoStudioActivity.java:58-64`
- Test: `android/app/src/test/java/pe/manchapp/beta/PhotoFlowTest.java`

**Interfaces:**
- Consumes: `ImageTools.decodeFile(File,int)` existente y `PhotoEditorActivity.input(Context)`.
- Produces: `static Bitmap ImageTools.read(Context,Uri,int edge) throws IOException`; `read(Context,Uri)` queda delegando con `edge=960`. `PhotoStudioActivity` escribe el archivo de entrada del editor desde el bitmap decodificado, una sola vez.

- [ ] **Step 1: Write the failing test**

En `PhotoFlowTest`: `cameraDecodeUses1280AndWritesEditorSourceOnce`: registrar un `InputStream` de un PNG 1600×1600 en `content://camera-test/photo.jpg`, invocar `onActivityResult(41,RESULT_OK,null)` sobre `PhotoStudioActivity`, esperar idle, y afirmar que `ImageTools.decodeFile(PhotoEditorActivity.input(context),1280)` da lado mayor 1280 y que el workspace guardó `source`/`image` sin excepción.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests pe.manchapp.beta.PhotoFlowTest`
Expected: FAIL (el decode actual entrega 960).

- [ ] **Step 3: Implementar el overload y la escritura única**

`ImageTools.read(Context,Uri,int)` con el mismo manejo EXIF y `limit(bitmap,edge)`. En `PhotoStudioActivity`,para `request==41||42` decodificar con 1280, comprimir ese bitmap directamente a `PhotoEditorActivity.input()` (JPEG 92; PNG si `ImageTools.hasTransparency`), sin pasar por `encodeEdited` ni el límite de 670 KB; mantener `encodeEdited` solo para persistir `source`/`image`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests pe.manchapp.beta.PhotoFlowTest`
Expected: PASS con sus 4 tests.

- [ ] **Step 5: Commit (solo si el usuario lo autoriza)**

Mensaje: `fix: conservar resolucion de foto de camara en el editor`.

---

### Task 5: Guardado PNG transparente sin degradar el recorte

**Files:**
- Modify: `android/app/src/main/java/pe/manchapp/beta/imagenes/ImageTools.java:92-105`
- Test: `android/app/src/test/java/pe/manchapp/beta/ImageToolsTest.java`

**Interfaces:**
- Consumes: `ImageTools.encodeEdited(Bitmap)` existente.
- Produces: mismo nombre, límite PNG 1.200.000 bytes y piso de 960 px: al llegar a 960 se devuelve el PNG aunque exceda el límite, sin lanzar error; QR sigue en 600 KB (`encodeQr` intacto).

- [ ] **Step 1: Write the failing test**

`ImageToolsTest.transparentEditedPhotoKeepsAtLeast960px`: crear bitmap 1200×1200 ARGB_8888 con ruido (para forzar tamaño), esquina `(0,0)` transparente, centro opaco; `encodeEdited` → `decode`; afirmar lado mayor ≥960, `alpha=0` en `(0,0)` y `alpha=255` en el centro.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests pe.manchapp.beta.ImageToolsTest`
Expected: FAIL (hoy el bucle baja hasta 128 px).

- [ ] **Step 3: Implementar el nuevo límite**

En `encodeEdited`, cambiar `670000` por `1200000`; reemplazar el `throw` por una condición de retorno cuando el lado mayor sea `<=960` (pasos de 0.8), de modo que nunca se baje de 960 ni se falle por tamaño. No tocar `encodeQr`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests pe.manchapp.beta.ImageToolsTest`
Expected: PASS.

- [ ] **Step 5: Commit (solo si el usuario lo autoriza)**

Mensaje: `fix: conservar recortes transparentes al guardar`.

---

### Task 6: Verificación completa y prueba en dispositivo

**Files:** ninguno (solo ejecución y chequeo).

**Interfaces:**
- Consumes: todo lo anterior.

- [ ] **Step 1: Tests completos**

Run: `./gradlew.bat :app:testDebugUnitTest :clientes:testDebugUnitTest`
Expected: todos verde (33 existentes + nuevos).

- [ ] **Step 2: Build de ambas APKs**

Run: `./gradlew.bat :app:assembleDebug :clientes:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Checklist manual obligatoria en dispositivo**

Instalar `:app` con la clave demo. Tomar foto de un objeto simple sobre fondo contrastante → Editar foto → Fondo → Sin fondo → Listo → Guardar → exportar PNG. Criterio: el fondo del PNG es transparente y el objeto completo. Repetir con persona sosteniendo el producto; si la máscara falla, usar “Toca el objeto” y “Borrar fondo a mano”, verificando que la edición anterior se conserva. Si no hay recorte real, el trabajo no está terminado.

- [ ] **Step 4: Reporte**

Reportar 4 líneas: estado de tests, estado de build, resultado de la prueba manual y APKs generadas. No hacer commit ni push sin permiso.
