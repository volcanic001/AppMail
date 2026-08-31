# Registro técnico — Baseline y red de seguridad de EmailBodyWebView

## Estado del documento

- Plan: baseline y red de seguridad de `EmailBodyWebView` previo a la extracción de código.
- Etapa: 4 — Validación en dispositivo físico y cierre.
- Subfase: 4.2 — Paquete de handoff.
- Estado del plan: **COMPLETADO — GO**; línea base consolidada y apta para el Plan B.
- Este documento conserva debajo el registro inicial de la Subfase 1.1 y añade
  al final el cierre acumulado de la Subfase 4.2. Las decisiones históricas de
  los informes intermedios no se reescriben.
- Captura inicial realizada: 2026-08-11 19:40:09 -0600 (CST).
- Alcance de la captura inicial: solo documentación del estado previo; no se
  ejecutaron builds ni tests y no se modificó producción, pruebas ni
  configuración Gradle.

## Repositorio y punto de partida

- Ruta de trabajo: `/Users/david/Desktop/MailApp 0.3.0 2`.
- Rama: `main`.
- HEAD: `2a433a67af4ea6f4f27e320133c963a408e80453`.
- Último commit: `2a433a67af4ea6f4f27e320133c963a408e80453` — autor `volcanic001` — `2026-08-11 14:25:25 -0600` — asunto `docs(repository): close structural refactor`.
- Las consultas Git se ejecutaron con `git -c core.fsmonitor=false` para evitar el aviso no bloqueante de `fsmonitor`; dicho aviso no representa un cambio de producto.
- `git status --short` en la captura inicial: solo aparecían modificados los dos archivos ajenos (ver sección siguiente); no había archivos sin seguimiento. Al cierre de la subfase aparece además `docs/verification/emailbody-webview-baseline/`, esperado porque contiene este registro técnico.
- `git diff --check`: sin salida (limpio).
- Diff resumido: `ComposeScreen.kt` 4 inserciones / 2 eliminaciones; `MainNavHost.kt` 6 inserciones / 2 eliminaciones.

## Cambios ajenos congelados

Estos cambios pertenecen al usuario y están presentes en el árbol de trabajo. Se congelan tal como están y **no se editarán, formatearán, revertirán ni incluirán en commits del baseline**.

### ComposeScreen.kt

- Ruta: `app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt`.
- SHA-256: `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69`.
- Resumen: 4 inserciones / 2 eliminaciones.
- Diff congelado:

```diff
diff --git a/app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt b/app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt
index 37e71f0..7033482 100644
--- a/app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt
+++ b/app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt
@@ -10,7 +10,7 @@ import androidx.compose.foundation.layout.fillMaxSize
 import androidx.compose.foundation.layout.fillMaxWidth
 import androidx.compose.foundation.layout.height
 import androidx.compose.foundation.layout.padding
-import androidx.compose.foundation.layout.width
+import androidx.compose.foundation.layout.widthIn
 import androidx.compose.foundation.rememberScrollState
 import androidx.compose.foundation.text.KeyboardOptions
 import androidx.compose.foundation.verticalScroll
@@ -399,7 +399,9 @@ private fun FieldRow(
             text = label,
             style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant,
-            modifier = Modifier.width(52.dp)
+            maxLines = 1,
+            softWrap = false,
+            modifier = Modifier.widthIn(min = 52.dp)
         )
         content()
     }
```

### MainNavHost.kt

- Ruta: `app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt`.
- SHA-256: `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.
- Resumen: 6 inserciones / 2 eliminaciones.
- Diff congelado:

```diff
diff --git a/app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt b/app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt
index d435e95..411373f 100644
--- a/app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt
+++ b/app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt
@@ -72,7 +72,9 @@ fun MainNavHost(
     ) {
         composable<MainRoute.Inbox>(
             enterTransition = { fadeIn(spring(dampingRatio = 0.65f, stiffness = 350f)) },
-            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) }
+            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) },
+            popEnterTransition = { EnterTransition.None },
+            popExitTransition = { ExitTransition.None }
         ) { backStackEntry ->
             val inboxListState = rememberLazyListState()
             val highlightedEmailId by backStackEntry.savedStateHandle
@@ -97,7 +99,9 @@ fun MainNavHost(

         composable<MainRoute.Trash>(
             enterTransition = { fadeIn(spring(dampingRatio = 0.65f, stiffness = 350f)) },
-            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) }
+            exitTransition = { fadeOut(spring(dampingRatio = 0.65f, stiffness = 350f)) },
+            popEnterTransition = { EnterTransition.None },
+            popExitTransition = { ExitTransition.None }
         ) { backStackEntry ->
             val trashListState = rememberLazyListState()
             val highlightedEmailId by backStackEntry.savedStateHandle
```

## Inventario técnico del archivo objetivo

### EmailBodyWebView.kt

- Ruta: `app/src/main/java/com/david/mailapp/feature/emaildetail/components/EmailBodyWebView.kt`.
- Tamaño: 26,087 bytes.
- Líneas: 669 (según `wc -l`).
- Fecha de sistema (mtime): `2026-08-03 11:56:17 -0600`.
- SHA-256: `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`.

Este hash será la referencia para demostrar que `EmailBodyWebView.kt` permanece sin cambios durante todo el plan de baseline. El inventario siguiente es estático (estructura); los contratos y el comportamiento se documentan en la Subfase 1.2.

### Paquete

```kotlin
package com.david.mailapp.feature.emaildetail.components
```

### Imports (orden literal)

```kotlin
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import org.jsoup.Jsoup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.david.mailapp.feature.emaildetail.EmailRenderTrace
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

### Firma pública (literal)

```kotlin
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmailBodyWebView(
    body: String?,
    showImages: Boolean = true,
    isDark: Boolean,
    traceMail: String,
    onPageRendered: (() -> Unit)? = null,
    onImageLongPress: ((imageUrl: String) -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

Orden de parámetros: `body`, `showImages` (por defecto `true`), `isDark`, `traceMail`, `onPageRendered` (por defecto `null`), `onImageLongPress` (por defecto `null`), `modifier` (por defecto `Modifier`).

### Símbolos privados

| Símbolo | Línea | Declaración |
|---|---|---|
| `PreparedDocument` | 439 | `private data class PreparedDocument(val key: String, val html: String)` |
| `buildLoadKey` | 441 | `private fun buildLoadKey(...)` |
| `buildHtml` | 453 | `private fun buildHtml(...)` |
| `applyHardening` | 533 | `private fun WebSettings.applyHardening(showImages: Boolean, isDark: Boolean)` |
| `CustomTabsWebViewClient` | 564 | `private class CustomTabsWebViewClient(ctx, traceMail, loadKey, onPageReady)` |
| `TraceWebChromeClient` | 635 | `private class TraceWebChromeClient(traceMail, loadKey)` |
| `toCssRgb` | 664 | `private fun toCssRgb(argb: Int): String` |

La lógica de estos símbolos no se describe aquí; es materia de la Subfase 1.2 (contratos observables).

## Entorno reproducible

- Gradle Wrapper: `9.6.1` (`gradle-9.6.1-bin.zip`).
- AGP: `9.0.0`.
- Kotlin: `2.1.20`; KSP: `2.1.20-1.0.31`.
- JDK del sistema (PATH): OpenJDK Temurin `25.0.2` (`java -version`; `JAVA_HOME` no definido en el shell de captura).
- Gradle Runtime detectado (daemon 9.6.1, pid 24254, iniciado `2026-08-11 15:48:46 CST`): Temurin 25 — `javaHome=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`, `javaVersion=25`, vendor `Eclipse Adoptium`.
- JBR de Android Studio (referencia): Java `21.0.9` (JetBrains).
- SDK de la app (`app/build.gradle.kts`): `minSdk 26`, `targetSdk 36`, `compileSdk 36`; `namespace com.david.mailapp`.
- `sdk.dir`: `/Users/david/Library/Android/sdk` (`local.properties`).
- AVD disponible: `Medium_Phone_API_36.1` (target `android-36.1`; path `/Users/david/.android/avd/Medium_Phone.avd`).
- Dispositivo físico conectado: Pixel 9 — estado `device` (conexión adb TLS, `transport_id 100`), Android `17` / API `37`, product y device `tokay`, model `Pixel_9`, fabricante `Google` confirmado por `adb getprop ro.product.manufacturer`.
- No se registran secretos ni identificadores de cuenta.

## Notas de captura

- El clasificador de seguridad del entorno (auto-mode) bloqueó de forma intermitente consultas de solo lectura (`git diff`, `adb getprop`); las consultas bloqueadas fueron re-ejecutadas y completadas con aprobación explícita del usuario. No se omitió ni se alteró ninguna evidencia.
- `read_file` reporta 670 líneas totales frente a las 669 de `wc -l`; la diferencia corresponde a la línea final sin salto de línea. Se registra 669, valor de `wc -l`, consistente con la captura del plan.

## Verificación y cierre de la subfase

- Verificado al cierre: SHA-256 de `EmailBodyWebView.kt` (`83cf07eb…ddd2d1`), `ComposeScreen.kt` (`2505050c…f5e69`) y `MainNavHost.kt` (`a6840cfc…ea088`) coinciden con la captura inicial.
- `git diff --check` sin salida (limpio).
- Único cambio nuevo del baseline en esta subfase: `docs/verification/emailbody-webview-baseline/registro-tecnico.md`.
- No se crea commit todavía: el commit documental se reserva para el paquete de handoff al cerrar todo el plan de baseline (Subfase 4.2).
- Éxito de la subfase: registro autocontenido, trazable y sin cambios de comportamiento ni de código de producto.

---

## Cierre acumulado — Subfase 4.2

### Identidad final

- Cierre preparado: 2026-08-26, CST (`-0600`).
- Rama: `main`.
- HEAD base del paquete: `2a433a67af4ea6f4f27e320133c963a408e80453`.
- Commit reservado: `docs(emailbody): establish webview baseline`.
- El SHA del commit se comunica después de crearlo; no se inserta en el propio
  commit porque eso produciría una referencia circular inestable.
- No se modifica ni se prepara ningún push como parte de este plan.

### Alcance final reconciliado

El alcance inicial preveía documentación y una nueva prueba de
caracterización. Durante la reapertura autorizada 2.2-R fue necesario
estabilizar dos AndroidTest existentes, y la suite pública necesitó copias de
las cinco fixtures como assets instrumentados. El paquete final autorizado es:

- `EmailBodyWebViewBaselineTest.kt`, con 22 pruebas instrumentadas finales;
- cinco assets HTML, idénticos byte a byte a las fixtures canónicas;
- sincronización observable en `EmailDetailCancellationTest.kt` y
  `EmailDetailPresentationTest.kt`;
- documentación, reportes XML, capturas y trazas bajo este directorio.

No forman parte del paquete `ComposeScreen.kt`, `MainNavHost.kt` ni
`gradle.properties`. Sus cambios se preservan en el working tree, sin editar,
revertir ni preparar. No existe ningún cambio de producción, configuración,
navegación, firma pública o conducta de `EmailBodyWebView` atribuible al
baseline.

### Hashes protegidos finales

| Archivo | SHA-256 final | Resultado |
|---|---|---|
| `EmailBodyWebView.kt` | `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1` | Idéntico al inicio; 669 líneas |
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Cambio ajeno intacto |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Cambio ajeno intacto |
| `EmailDetailCancellationTest.kt` | `33fa11f1a6954354c449223f247d5761563784cba07f475aa94e8c3b5c89433e` | Estabilización 2.2-R |
| `EmailDetailPresentationTest.kt` | `3b278d172eeb655cba55c3c4e70e65bb87e1f86dae35a7014ea880669eec9ce5` | Estabilización 2.2-R |
| `EmailBodyWebViewBaselineTest.kt` | `d526fc1254964e565d6d84acc57bfdf6f1eeaf38189cca6baf40ebfba5bf4f9d` | Suite de baseline |

Los SHA-256 individuales de los 32 PNG, 32 logs y 16 XML están en las tablas
autoritativas de `resultados-subfase-2.2.md` a
`resultados-subfase-4.1.md`. La auditoría 4.2 volvió a calcular las 41 entradas
de capturas/trazas publicadas en esas tablas y no encontró discrepancias.

### Índice reproducible de ejecución

| Puerta | Dispositivo | Resultado aceptado | Evidencia |
|---|---|---:|---|
| 2.1 — JVM/compilación | Host | 5 tareas verdes | `resultados-subfase-2.1.md` |
| 2.2 — focal estabilizada | Emulador API 36 | 3 × 34/34 | `reportes-subfase-2.2/focal.xml` |
| 2.2 — completa | Emulador API 36 | 284/284 | `reportes-subfase-2.2/completa.xml` |
| 2.3 — caracterización | Emulador API 36 | 3 × 6/6; completa 290/290 | `reportes-subfase-2.3/` |
| 3.1 — carga/documento | Emulador API 36 | 3 × 18/18; completa 302/302 | `reportes-subfase-3.1/` |
| 3.2 — interacción/lifecycle | Emulador API 36 | 3 × 22/22; completa 306/306 | `reportes-subfase-3.2/` |
| 4.1 — compatibilidad | Emulador API 36 | 22/22 | `reportes-subfase-4.1/emulador-compatibilidad.xml` |
| 4.1 — física | Pixel 9 API 37 | 22/22 | `reportes-subfase-4.1/fisico.xml` |

Los 16 XML conservados declaran `failures=0`, `errors=0` y `skipped=0`. Los
comandos literales, duración, dispositivo y SHA-256 se conservan en cada
informe de resultados. La Subfase 4.2 no repite suites porque no cambia código
ni configuración después de la puerta física aceptada.

Los XML generados por Gradle conservan sus finales CRLF originales. La puerta
del staging usa `git -c core.whitespace=cr-at-eol diff --cached --check`: así
Git acepta CRLF como fin de línea válido y no obliga a normalizar evidencia
cuya identidad SHA-256 ya fue aprobada.

### Inventario final de evidencia

| Grupo | Cantidad | Verificación 4.2 |
|---|---:|---|
| Fixtures canónicas | 5 | No vacías |
| Assets instrumentados | 5 | `cmp` idéntico contra cada fixture canónica |
| Capturas PNG | 32 | No vacías y firma PNG válida |
| Trazas `.log` | 32 | No vacías y líneas de evidencia con `MailRenderTrace` |
| Reportes XML | 16 | Parseables; conteos y estados verdes |

El barrido de privacidad sobre trazas, XML y assets no encontró valores de
`Authorization`, tokens Bearer, access/refresh tokens ni direcciones Gmail u
Outlook. Las capturas fueron revisadas en 3.1, 3.2 y 4.1 y solo contienen
fixtures sintéticas o `Example Domain`.

### Tabla final de contratos

La columna Estado clasifica la evidencia realmente conservada, no solo la
posibilidad teórica documentada en 1.2.8. `Manual` significa inspección de
fuente o revisión visual humana; los contratos corroborados por ambas vías se
clasifican por la puerta automatizada más fuerte y citan el apoyo manual.

| Contrato congelado | Estado | Evidencia final | Resultado |
|---|---|---|---|
| Fórmula y orden de `buildLoadKey` | Manual | Inspección literal 1.2 + trazas S10–S13 | APROBADO |
| Dependencias de `currentKey` | Automatizado | 2.3 cambios de cuerpo/imágenes/tema + S11–S13 | APROBADO |
| Ramas `wait/load/skip` de `AndroidView.update` | Automatizado | S01, S10 y trazas WV | APROBADO |
| Rechazo por `released`/`activeLoadKey` | Automatizado | `replacedDocument_doesNotDispatchStaleCallback` + S16 | APROBADO |
| `body == null` mantiene el WebView montado | Automatizado | `bodyPending_keepsWebViewMounted_andLoadsWhenBodyArrives` + S01 | APROBADO |
| Pipeline Jsoup + `EmailHtmlCleaner` | Manual | Inspección 1.2 y carga de cinco fixtures | APROBADO |
| Heurística `table table` de HTML simple | Manual | Inspección 1.2 + F02/S04–S05 | APROBADO |
| Wrapper simple y márgenes literales | Manual | Inspección 1.2 + S01–S03 | APROBADO |
| Variables y colores CSS | Automatizado | S02/S03/S12 + revisión visual | APROBADO |
| CSS de ocultación remota | Automatizado | S07/S08/S13 | APROBADO |
| Conversión ARGB a `rgb(R,G,B)` | Manual | Inspección literal 1.2 | APROBADO |
| `MATCH_PARENT × MATCH_PARENT` | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Scrollbars deshabilitadas | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Fondo aplicado en factory y carga | Automatizado | S02/S03/S12 y trazas de carga | APROBADO |
| JavaScript deshabilitado | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| DOM storage deshabilitado | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Acceso a archivos deshabilitado | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Acceso a contenido deshabilitado | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Acceso file-URL cruzado deshabilitado | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Acceso universal desde file-URL deshabilitado | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Media requiere gesto del usuario | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Caché `LOAD_NO_CACHE` | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| `blockNetworkImage = !showImages` | Automatizado | `networkBlocking_followsShowImagesAcrossRecomposition` | APROBADO |
| `blockNetworkLoads = !showImages` | Automatizado | `networkBlocking_followsShowImagesAcrossRecomposition` | APROBADO |
| Wide viewport habilitado | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Overview mode habilitado | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| `textZoom = 100` | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Controles de zoom internos habilitados | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Controles de zoom visuales ocultos | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Soporte de zoom habilitado | Automatizado | `hardeningViewportAndZoomSettings_matchBaseline` | APROBADO |
| Darkening AndroidX y nativo | Automatizado | `algorithmicDarkening_followsIsDark` + S03/S05/S12 | APROBADO |
| `loadDataWithBaseURL` con argumentos congelados | Manual | Inspección literal 1.2 + cargas S01–S13 | APROBADO |
| Long-press solo para imagen y URL no vacía | Automatizado | S15 en emulador y Pixel 9 | APROBADO |
| Enlaces externos abren Custom Tab y retornan `true` | Automatizado | S09 en emulador y Pixel 9 + revisión visual | APROBADO |
| Pausa guarda scroll y llama `onPause()` | Automatizado | S14 y traza `WV_LIFECYCLE_PAUSE` | APROBADO |
| Resume restaura scroll tras estado visual | Automatizado | S14; PNG antes/después idénticos | APROBADO |
| Release detiene y destruye la instancia | Automatizado | S16 y traza `WV_RELEASE` | APROBADO |
| Secuencia de trazas WV | Automatizado | 32 logs y aserciones S01–S16 | APROBADO |
| Secuencia de trazas UI | Automatizado | trazas S09/S14/S16 | APROBADO |
| Long-press exitoso no añade traza propia | Automatizado | S15 + auditoría de su log | APROBADO |

Resumen: 34 contratos automatizados, 6 manuales y 0 no observables; todos
aprobados. La clasificación no convierte la inspección estática en una prueba
automatizada y permite al Plan B distinguir con precisión sus puertas.

### Excepciones conocidas aceptadas

- F02 conserva overflow horizontal en newsletter con tablas, reproducido en
  claro/oscuro, emulador y Pixel 9. Es defecto visual de referencia, no una
  regresión introducida por el baseline.
- La inestabilidad original de 2.2 permanece documentada. Las dos pruebas se
  estabilizaron mediante espera observable y pasaron sus series posteriores.
- 3.1 registró incidencias de infraestructura y una primera suite completa no
  aceptada; la evidencia versionada corresponde exclusivamente a la serie
  verde final.
- 4.1 registró incompatibilidad inicial de firma instalada y una carrera
  transitoria de `ActivityScenario`; la corrida física aceptada posterior fue
  22/22.
- La imagen remota sintética de S06 muestra el mismo recurso no cargado en
  ambos dispositivos; S07/S13 verifican bloqueo y S08/S15 preservan `data:`.

### Decisión de handoff

**GO — baseline reproducible, legible y listo para ser consumido por el Plan
B.** El commit aislado debe contener solo documentación y código/assets de
prueba enumerados en el alcance final. La producción permanece idéntica al
punto de partida.

---

## Subfase 6.3 — Suite completa y puerta Pixel 9

### Ejecución real

- Host: 2026-08-30T19:59:38-0600; HEAD
  `32616dc5970fd1f4decbc3fe6cf13b06da1661d6`.
- Emulador iniciado: `Medium_Phone_API_36.1`, serial `emulator-5554`, estado
  `device`, `sys.boot_completed=1`, API 36.
- Comando ejecutado:

  ```sh
  env ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks --console=plain
  ```

- Resultado: **306/306**, sin fallos, errores ni omitidas; `BUILD SUCCESSFUL`
  en 8m52s. El XML
  `app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml`
  declara `tests="306" failures="0" errors="0" skipped="0"` (tiempo
  `416.037`).
- Los warnings de APIs/opciones deprecadas y de símbolos nativos no
  strippeables no afectaron el resultado. No se modificó producción, tests,
  Gradle, baseline histórico ni snapshots.

### Puerta física y decisión

- La detección ADB tras la corrida mostró únicamente el emulador
  `sdk_gphone64_x86_64`; no había ningún dispositivo físico modelo `Pixel_9`.
- En consecuencia no hubo serial físico válido para comprobar boot, API 37 o
  override de resolución; tampoco se ejecutó la focal de 22 casos, se extrajo
  `/data/local/tmp/emailbody-4.1/` ni se compararon artefactos S01–S16.
- **Estado de Subfase 6.3: NO-GO por infraestructura.** El verde 306/306
  demuestra la puerta de emulador, pero no sustituye la validación contractual
  Pixel 9/API 37. F02 (overflow) y la imagen remota sintética no cargada
  continúan siendo defectos conocidos, no regresiones.

### Integridad

- `git diff --check`: sin salida; staging vacío.
- Cambios ajenos preservados: `ComposeScreen.kt`, `MainNavHost.kt` y
  `gradle.properties`.
- SHA-256 verificados: `ComposeScreen.kt`
  `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69`,
  `MainNavHost.kt`
  `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`, y
  `gradle.properties`
  `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476`.

### Reanudación física y cierre GO

- El Pixel fue conectado después del NO-GO inicial y se detectó dinámicamente
  como `55080DLAQ002CK`: estado `device`, modelo `Pixel 9`, dispositivo
  `tokay`, `sys.boot_completed=1`, API 37 y `wm size` físico `1080x2424` sin
  override de tamaño ni densidad.
- Comando ejecutado, con el serial detectado:

  ```sh
  env ANDROID_SERIAL=55080DLAQ002CK ./gradlew connectedDebugAndroidTest --rerun-tasks --console=plain \
    -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.emaildetail.components.EmailBodyWebViewBaselineTest
  ```

- Resultado físico: **22/22**, `failures=0`, `errors=0`, `skipped=0`,
  `BUILD SUCCESSFUL` en 2m30s. XML:
  `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel 9 - 17-_app-.xml`
  (`time=64.917`).
- Se extrajeron 32 artefactos, sin borrar ni modificar el dispositivo, a
  `/private/tmp/emailbody-63.FXKg4O/`: 16 logs y 16 PNG de S01–S16. Todos los
  PNG son 1080×2424. Los 16 logs son parseables y contienen las trazas
  `HTML_BUILD_*`, `WV_*` y callbacks esperados.
- La comparación con el baseline físico conserva secuencias/payloads
  normalizados en 15 escenarios. S06 presenta la misma secuencia contractual
  con una reordenación temporal permitida: `WV_COMMIT_VISIBLE` antes del
  progreso 100. La prueba verde confirma que no modifica la entrega de página;
  la imagen remota sintética permanece el defecto conocido esperado.
- Las capturas de corridas distintas no son idénticas byte a byte; S14 cumple
  su contrato visual dentro de la corrida: sus PNG antes/después son idénticos
  (`aeb65889c06ddd4d2127ef03fbd8737fc6add4646ebd434307035b4d7763e87a`).
  F02 mantiene su overflow conocido. No hay regresión funcional ni visual
  contractual.

**Subfase 6.3 CERRADA — GO:** emulador API 36, 306/306; Pixel 9 API 37,
22/22. La condición de infraestructura inicial queda resuelta con evidencia,
sin relajar pruebas ni alterar código.
