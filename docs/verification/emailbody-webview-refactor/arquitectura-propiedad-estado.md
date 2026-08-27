# Arquitectura congelada y propiedad del estado — Refactor estructural de EmailBodyWebView

Subfase 1.2 — Arquitectura y propiedad del estado
Fecha de ejecución: 2026-08-27T10:58:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro (modo auditoría)
Commit previsto: `docs(emailbody): freeze webview refactor architecture`

> Documento **solo documental**. No modifica producción, pruebas, Gradle,
> navegación, DI ni baseline histórico. Congela las decisiones para que la
> Subfase 1.3 pueda construir la matriz de equivalencia sin tomar decisiones
> nuevas.

---

## 1. Firma pública congelada

La firma pública de `EmailBodyWebView(...)` permanece **idéntica**. No cambian
parámetros, orden, valores por defecto ni consumidores.

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

Consumidores (sin cambios):

- Producción: `EmailDetailContent.kt` (línea 134), composable `internal`.
- Pruebas: `EmailBodyWebViewBaselineTest.kt` (línea 901).

Todos los símbolos extraídos serán `internal`. No se añadirá API pública.

---

## 2. Distribución final en siete archivos

| Archivo | Responsabilidad | Contenido |
|---|---|---|
| `EmailBodyWebView.kt` | Fachada pública | Lectura de `LocalContext`, `LocalLifecycleOwner`, `MaterialTheme`; cálculo de colores; cálculo de `currentKey`; `WV_COMPOSABLE_ENTER/DISPOSE`; llamada a preparación; creación de runtime; delegación al host. Objetivo 80–140 líneas. |
| `EmailBodyDocument.kt` | Documento preparado puro | `PreparedDocument`, `buildLoadKey`, `buildHtml`, `toCssRgb`. |
| `EmailBodyDocumentPreparation.kt` | Preparación asíncrona | `rememberPreparedEmailBodyDocument(...)` con `remember(currentKey)`, `LaunchedEffect(currentKey)`, `Dispatchers.Default` y trazas `HTML_BUILD_*`. |
| `EmailBodyWebViewRuntime.kt` | Estado recordado + lifecycle | `rememberEmailBodyWebViewRuntimeState()` (estado sin claves) y el binding `DisposableEffect(lifecycleOwner)`. |
| `EmailBodyWebViewHost.kt` | Host `AndroidView` | `Box(modifier)`, `AndroidView`, `factory`, `update`, `onRelease`, long-press de imágenes, instalación de clients y `loadDataWithBaseURL`. |
| `EmailBodyWebViewSettings.kt` | Hardening de `WebSettings` | `WebSettings.applyHardening(showImages, isDark)` sin cambios de valores. |
| `EmailBodyWebViewClients.kt` | Clientes de WebView | `CustomTabsWebViewClient` y `TraceWebChromeClient`. |

El paquete de todos los archivos permanece
`com.david.mailapp.feature.emaildetail.components`.

---

## 3. Mapa de símbolos: origen → destino

### 3.1 Símbolos ya existentes (movimiento mecánico)

| Símbolo actual | Línea en el baseline | Destino | Visibilidad final |
|---|---|---|---|
| `private data class PreparedDocument(val key, val html)` | 439 | `EmailBodyDocument.kt` | `internal` |
| `private fun buildLoadKey(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb): String` | 441 | `EmailBodyDocument.kt` | `internal` |
| `private fun buildHtml(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb): String` | 453 | `EmailBodyDocument.kt` | `internal` |
| `private fun toCssRgb(argb: Int): String` | 664 | `EmailBodyDocument.kt` | `internal` |
| `private fun WebSettings.applyHardening(showImages, isDark)` | 533 | `EmailBodyWebViewSettings.kt` | `internal` |
| `private class CustomTabsWebViewClient(ctx, traceMail, loadKey, onPageReady)` | 564 | `EmailBodyWebViewClients.kt` | `internal` |
| `private class TraceWebChromeClient(traceMail, loadKey)` | 635 | `EmailBodyWebViewClients.kt` | `internal` |

Los nombres internos se conservan para minimizar riesgo. Si en las fases 2–4 se
renombra alguno, todas las llamadas deben actualizarse en el mismo commit.

### 3.2 Bloques inline que pasan a ser funciones internal

| Bloque inline (baseline) | Líneas | Función extraída | Destino |
|---|---|---|---|
| `var preparedDocument by remember(currentKey) { ... }` + `LaunchedEffect(currentKey) { ... }` | 101–145 | `rememberPreparedEmailBodyDocument(...)` | `EmailBodyDocumentPreparation.kt` |
| `var lastLoaded ... var activeLoadKey ... var loggedSkippedKey ... var loggedWaitingState ... val savedScrollY ... val webViewRef ... val released` | 149–157 | `rememberEmailBodyWebViewRuntimeState()` | `EmailBodyWebViewRuntime.kt` |
| `DisposableEffect(lifecycleOwner) { ... }` | 176–254 | binding de lifecycle interno al runtime | `EmailBodyWebViewRuntime.kt` |
| `Box(modifier) { AndroidView(...) }` | 256–434 | composición del host | `EmailBodyWebViewHost.kt` |

---

## 4. Propiedad del estado mutable

Cada estado mutable tiene exactamente un propietario. No se introduce estado
nuevo ni se convierte un estado Compose en variable ordinaria.

### 4.1 Fachada — `EmailBodyWebView.kt`

- `context`, `lifecycleOwner`: lectura inmutable de `Local*`.
- `scheme`, `surfaceArgb`, `onSurfaceArgb`, `primaryArgb`: derivados de `MaterialTheme`.
- `currentKey`: derivado de `remember(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb)`.
- `DisposableEffect(traceMail)`: emite `WV_COMPOSABLE_ENTER` / `WV_COMPOSABLE_DISPOSE`.

### 4.2 Preparación — `EmailBodyDocumentPreparation.kt`

- `preparedDocument: PreparedDocument?` — `remember(currentKey) { mutableStateOf(null) }`.
- Propiedad del bloque `LaunchedEffect(currentKey)` (solo su corrutina lo asigna).

### 4.3 Runtime — `EmailBodyWebViewRuntime.kt`

Estado recordado **sin claves** (`remember { mutableStateOf(...) }`):

| Estado | Tipo | Estado inicial |
|---|---|---|
| `lastLoaded` | `String?` | `null` |
| `activeLoadKey` | `String?` | `null` |
| `loggedSkippedKey` | `String?` | `null` |
| `loggedWaitingState` | `String?` | `null` |
| `savedScrollY` | `Int` (`mutableIntStateOf`) | `0` |
| `webViewRef` | `WeakReference<WebView>?` | `null` |
| `released` | `Boolean` | `false` |

### 4.4 Callbacks (propiedad y captura)

| Callback | Propietario | Notas |
|---|---|---|
| `onPageRendered` | consumido por el host (`onRelease`/callback del client) | se conserva como captura del factory/update; no se usa `rememberUpdatedState` |
| `onImageLongPress` | `setOnLongClickListener` en el factory del host | captura actual del factory |
| `traceMail` | leído en fachada, preparación, runtime y host | no se re-captura con `rememberUpdatedState` |
| `onPageReady` interno de `CustomTabsWebViewClient` | lambda creada en `update` | cierra sobre `released`, `activeLoadKey`, `savedScrollY`, `onPageRendered` |

---

## 5. Claves Compose congeladas

Se mantienen **exactamente** las siguientes claves y fórmulas. Cualquier
alteración es una mejora incidental prohibida.

| Llamada | Clave(s) |
|---|---|
| `remember(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb)` | seis parámetros de entrada |
| `remember(currentKey)` (preparedDocument) | `currentKey` |
| `LaunchedEffect(currentKey)` | `currentKey` |
| `remember { mutableStateOf<String?>(null) }` (lastLoaded) | sin clave |
| `remember { mutableStateOf<String?>(null) }` (activeLoadKey) | sin clave |
| `remember { mutableStateOf<String?>(null) }` (loggedSkippedKey) | sin clave |
| `remember { mutableStateOf<String?>(null) }` (loggedWaitingState) | sin clave |
| `remember { mutableIntStateOf(0) }` (savedScrollY) | sin clave |
| `remember { mutableStateOf<WeakReference<WebView>?>(null) }` (webViewRef) | sin clave |
| `remember { mutableStateOf(false) }` (released) | sin clave |
| `DisposableEffect(traceMail)` | `traceMail` |
| `DisposableEffect(lifecycleOwner)` | `lifecycleOwner` |

- `preparedDocument` permanece ligado a `currentKey`.
- El runtime se recuerda **sin claves**.
- Se conservan `DisposableEffect(traceMail)` y `DisposableEffect(lifecycleOwner)`.

---

## 6. Fórmula de clave de carga congelada

`buildLoadKey` conserva literalmente `body.hashCode()` y el orden de los seis
componentes, separados por `_`:

```
${body.hashCode()}_${showImages}_${isDark}_${surfaceArgb}_${onSurfaceArgb}_${primaryArgb}
```

`onSurfaceArgb` permanece en la clave aunque actualmente no altere el HTML.

---

## 7. Órdenes temporales congelados

### 7.1 Orden de carga (rama `load` del `update`)

1. Traza `WV_UPDATE action=load`.
2. `lastLoaded = document.key`.
3. `activeLoadKey = document.key`.
4. `loggedSkippedKey = null`.
5. `loggedWaitingState = null`.
6. `released.value = false`.
7. `webViewRef.value = WeakReference(webView)`.
8. `webView.setBackgroundColor(surfaceArgb)`.
9. `webView.settings.applyHardening(showImages, isDark)`.
10. `webView.webChromeClient = TraceWebChromeClient(traceMail, document.key)`.
11. `webView.webViewClient = CustomTabsWebViewClient(...) { ... }`.
12. Traza `WV_LOAD_DATA`.
13. `webView.loadDataWithBaseURL(null, document.html, "text/html", "UTF-8", null)`.

### 7.2 Orden de release (`onRelease`)

1. Traza `WV_RELEASE`.
2. `savedScrollY.intValue = webView.scrollY`.
3. `released.value = true`.
4. `activeLoadKey = null`.
5. `webViewRef.value = null`.
6. `webView.stopLoading()`.
7. `webView.destroy()`.

### 7.3 Lifecycle (binding `DisposableEffect(lifecycleOwner)`)

- `ON_PAUSE`: guardar `scrollY` antes de `webView.onPause()`.
- `ON_RESUME`: `webView.onResume()` antes del callback visual; preservar las dos
  comprobaciones `released/activeLoadKey` (en el callback y dentro de `post`).

### 7.4 Long-press de imágenes

Solo `WebView.HitTestResult.IMAGE_TYPE` y `SRC_IMAGE_ANCHOR_TYPE`, URL no
blank y callback opcional (`onImageLongPress?.invoke`).

### 7.5 Navegación por Custom Tabs

Abrir con `CustomTabsIntent`; sin validar, filtrar ni reescribir URLs. Ambos
`shouldOverrideUrlLoading` retornan `true` y capturan `Exception`.

---

## 8. Trazas congeladas

Se conservan nombres, payloads, orden observable y puntos de emisión de todas
las trazas `EmailRenderTrace.d` con layer `WV`:

`WV_COMPOSABLE_ENTER`, `WV_COMPOSABLE_DISPOSE`, `WV_FACTORY`, `WV_ATTACHED`,
`WV_DETACHED`, `WV_UPDATE` (action=wait/load/skip), `HTML_BUILD_WAITING`,
`HTML_BUILD_START`, `HTML_BUILD_END`, `HTML_BUILD_READY`, `WV_LOAD_DATA`,
`WV_PAGE_STARTED`, `WV_COMMIT_VISIBLE`, `WV_PAGE_FINISHED`,
`WV_VISUAL_REQUESTED`, `WV_VISUAL_CALLBACK`, `WV_SCROLL_RESTORE_POSTED`,
`WV_SCROLL_RESTORE_APPLIED`, `WV_PAGE_RENDERED_DISPATCH`,
`WV_PAGE_RENDERED_IGNORED`, `WV_PROGRESS`, `WV_ON_PAUSE`, `WV_ON_RESUME`,
`WV_RESUME_VISUAL_SKIPPED`, `WV_RESUME_VISUAL_REQUESTED`,
`WV_RESUME_VISUAL_CALLBACK`, `WV_RESUME_SCROLL_APPLIED`,
`WV_LIFECYCLE_OBSERVER_DISPOSE`, `WV_RELEASE`.

---

## 9. Riesgos por zona

| Zona | Riesgo principal | Mitigación congelada |
|---|---|---|
| Preparación async | resultado cancelado u obsoleto reemplaza el documento vigente | `remember(currentKey)` + `LaunchedEffect(currentKey)`; la cancelación evita la asignación final |
| Lifecycle | callback visual stale tras `ON_RESUME` | doble guarda `released/activeLoadKey` (callback + `post`) |
| Runtime | recomposición que rompe el estado recordado | runtime sin claves, `WeakReference` para el `WebView` |
| Host | callback capturado por el factory queda obsoleto | conservar el comportamiento heredado; sin `rememberUpdatedState` |
| Clients | URLs o progreso alterados | `CustomTabsWebViewClient` sin validación; hitos `0/25/50/75/100` con `lastMilestone` por instancia |
| Settings | valores de hardening alterados | copiar `applyHardening` sin cambios de valores ni nuevos settings |

---

## 10. Mejoras incidentales prohibidas

Queda expresamente prohibido introducir en ninguna fase:

- `rememberUpdatedState`, nuevos estados, o cambio de claves Compose.
- Cambios de URL, esquemas permitidos o allowlist.
- Cambios de dark mode, CSS o corrección del overflow de newsletters.
- Cambios de callbacks heredados o de `shouldOverrideUrlLoading`.
- Cambios de pruebas para aceptar otro comportamiento.

---

## 11. Alcance y denylist

- Fuera de alcance: `EmailHtmlCleaner`, `EmailDetailContent` (consumidor),
  Gradle, navegación, DI y baseline histórico
  (`docs/verification/emailbody-webview-baseline/`).
- Archivos ajenos protegidos (sin tocar): `ComposeScreen.kt`, `MainNavHost.kt`,
  `gradle.properties`.
