# Contratos observables de EmailBodyWebView

## Estado del documento

- Plan: baseline y red de seguridad de `EmailBodyWebView` previo a la extracción de código.
- Etapa: 1 — Congelar el punto de partida.
- Subfase: 1.2 — Inventario de contratos observables.
- Estado de la subfase: documento creado; el commit documental queda reservado para el paquete de handoff (Subfase 4.2).
- Captura realizada: 2026-08-11 19:57:32 -0600 (CST).
- Alcance: solo documentación; no se modificó producción, pruebas ni configuración Gradle; no se ejecutaron builds ni tests.
- Este documento es independiente y complementa `registro-tecnico.md` (Subfase 1.1). No debe mezclarse con el inventario técnico inicial.

---

## 1.2.1 — Encabezado y trazabilidad

- HEAD de referencia: `2a433a67af4ea6f4f27e320133c963a408e80453`.
- SHA-256 actual de `EmailBodyWebView.kt` (669 líneas, 26,087 bytes): `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`.
- Fecha de captura: `2026-08-11 19:57:32 -0600`.
- Archivos observados para este inventario:
  - `app/src/main/java/com/david/mailapp/feature/emaildetail/components/EmailBodyWebView.kt` — fuente principal.
  - `app/src/main/java/com/david/mailapp/feature/emaildetail/components/EmailDetailContent.kt` — consumidor directo.
  - `app/src/main/java/com/david/mailapp/feature/emaildetail/components/EmailHtmlCleaner.kt` — limpieza HTML invocada en `buildHtml()`.
  - `app/src/main/java/com/david/mailapp/feature/emaildetail/EmailRenderTrace.kt` — infraestructura de trazas.

Ninguno de estos archivos se modificó para este inventario. Todos los datos provienen de lectura estática del código vigente en el commit de referencia.

---

## 1.2.2 — Contrato de entrada y consumidor

### Firma pública congelada

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

### Semántica observable de cada parámetro

- **`body`** (`String?`): cuerpo HTML del correo (ya decodificado de Base64URL). `null` indica que el cuerpo aún no está disponible; el WebView permanece montado pero no recibe contenido. La transición de `null` a un valor no nulo dispara la preparación y carga.
- **`showImages`** (`Boolean`, default `true`): habilita cargas de red de imágenes remotas. Cuando `false`, se bloquean en `WebSettings` (`blockNetworkImage` y `blockNetworkLoads`) y se inyecta una regla CSS que oculta imágenes que no sean `data:`.
- **`isDark`** (`Boolean`): modo oscuro de la app. Controla la inyección CSS de esquema de color (`--text`, `color-scheme`), el darkening algorítmico del WebView y el `background` del WebView.
- **`traceMail`** (`String`): identificador anónimo (`emailId.hashCode().toUInt().toString(16)`) usado como prefijo en todas las trazas de `EmailRenderTrace`. No es observable desde la UI; solo para diagnóstico.
- **`onPageRendered`** (`(() -> Unit)?`, default `null`): callback invocado cuando el documento está visualmente completo (tras `postVisualStateCallback`). Solo se dispara para la clave activa; se rechaza si el componente fue liberado (`released`) o si la clave activa cambió.
- **`onImageLongPress`** (`((imageUrl: String) -> Unit)?`, default `null`): callback invocado al hacer long-press sobre una imagen con URL no vacía. Solo se dispara para `HitTestResult.IMAGE_TYPE` o `SRC_IMAGE_ANCHOR_TYPE`.
- **`modifier`** (`Modifier`, default `Modifier`): propagado al `Box` raíz que contiene el `AndroidView`.

### Consumidor: `EmailDetailContent`

`EmailDetailContent` consume `EmailBodyWebView` con los siguientes parámetros fijos observados:

- `showImages = true` — constante local en `EmailDetailContent`; no expone toggle al usuario.
- `isDark = LocalThemeConfig.current.darkTheme` — sincronizado con el tema de la app.
- `onPageRendered`: actualiza `isBodyRendered = true`, lo que oculta el `CircularProgressIndicator` overlay (loader).
- `onImageLongPress`: recibido como parámetro de `EmailDetailContent` y pasado sin procesar al flujo de imagen del detalle.
- `modifier = Modifier.fillMaxSize().zIndex(0f)` — ocupa todo el espacio disponible, detrás del loader overlay (`zIndex(1f)`).

No hay otros consumidores de `EmailBodyWebView` en producción. Cualquier refactor debe conservar compatibilidad exacta con esta llamada.

---

## 1.2.3 — Contrato de clave, preparación y carga

### Fórmula de `buildLoadKey`

```kotlin
private fun buildLoadKey(
    body: String,
    showImages: Boolean,
    isDark: Boolean,
    surfaceArgb: Int,
    onSurfaceArgb: Int,
    primaryArgb: Int
): String =
    "${body.hashCode()}_${showImages}_${isDark}_${surfaceArgb}_${onSurfaceArgb}_${primaryArgb}"
```

Formato literal: `{body.hashCode()}_{showImages}_{isDark}_{surfaceArgb}_{onSurfaceArgb}_{primaryArgb}`.

Los campos se concatenan con guion bajo `_` como separador; los valores booleanos se representan como `true`/`false` (representación `toString()` de Kotlin).

### Dependencias de `currentKey`

```kotlin
val currentKey = remember(
    body,
    showImages,
    isDark,
    surfaceArgb,
    onSurfaceArgb,
    primaryArgb
) {
    body?.let {
        buildLoadKey(it, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb)
    }
}
```

`currentKey` es `null` cuando `body == null`; en otro caso es el resultado de `buildLoadKey`. Depende de los seis valores listados arriba: cualquier cambio en `body`, `showImages`, `isDark`, `surfaceArgb`, `onSurfaceArgb` o `primaryArgb` produce una nueva clave y por tanto una nueva preparación.

### Flujo wait / load / skip

**1. Rama wait — Preparación pendiente (`LaunchedEffect(currentKey)`):**

- Si `body == null || currentKey == null`: traza `HTML_BUILD_WAITING reason=body_pending` y retorna. El WebView permanece montado sin contenido.
- Si `body != null && currentKey != null`: ejecuta `buildHtml(...)` en `Dispatchers.Default`, traza `HTML_BUILD_START` y `HTML_BUILD_END` (con `loadKey`, `bodyLen`, `htmlLen`, `durationMs`), asigna `preparedDocument = PreparedDocument(loadKey, html)` y traza `HTML_BUILD_READY`.

**2. Rama load — Carga de documento nuevo (`AndroidView.update`):**

- Si `preparedDocument == null`: traza `WV_UPDATE action=wait reason=body_pending` (si `currentKey == null`) o `reason=html_pending:{currentKey}` (si el documento aún no se preparó). No carga nada.
  - La traza de wait solo se emite una vez por estado distinto, mediante la variable `loggedWaitingState`.
- Si `preparedDocument != null && lastLoaded != document.key`: traza `WV_UPDATE action=load previousKey={lastLoaded} loadKey={document.key} htmlLen={document.html.length}`. Establece `lastLoaded = document.key`, `activeLoadKey = document.key`, limpia `loggedSkippedKey` y `loggedWaitingState`, resetea `released = false`, reaplica `setBackgroundColor(surfaceArgb)` y `settings.applyHardening(showImages, isDark)`, crea `TraceWebChromeClient` y `CustomTabsWebViewClient` (con el callback `onPageReady`), traza `WV_LOAD_DATA` y llama `webView.loadDataWithBaseURL(null, document.html, "text/html", "UTF-8", null)`.
  - El `baseUrl` es `null` (decisión de diseño D2): no hay resolución de recursos relativos.

**3. Rama skip — Clave ya cargada (`AndroidView.update`):**

- Si `preparedDocument != null && lastLoaded == document.key`: traza `WV_UPDATE action=skip loadKey={document.key} reason=already_loaded` (solo una vez por clave, mediante `loggedSkippedKey`). No se recarga ni se reaplica nada.

### Rechazo de callbacks obsoletos

La carga es asíncrona: entre `loadDataWithBaseURL` y `postVisualStateCallback` puede cambiar la clave activa o liberarse el componente. El rechazo se implementa en tres puntos:

- **Callback `onPageReady` del `WebViewClient`:** verifica `!released.value && activeLoadKey == document.key`. Si la condición falla, traza `WV_PAGE_RENDERED_IGNORED reason=stale_or_released` (con `activeLoadKey` y `released` en el payload). Si pasa, programa un `post` para restaurar scroll y disparar `onPageRendered`.
- **Dentro del `post` (scroll restore):** vuelve a verificar `released.value || activeLoadKey != document.key`. Si falla, traza `WV_PAGE_RENDERED_IGNORED reason=stale_after_post` y no restaura scroll ni invoca `onPageRendered`.
- **Callback visual de reanudación (lifecycle `ON_RESUME`):** verifica `released.value || activeLoadKey != resumeLoadKey`. Si falla, retorna silenciosamente (sin traza adicional). No se usa `postVisualStateCallback` si `activeLoadKey == null` (traza `WV_RESUME_VISUAL_SKIPPED reason=no_active_document`).

---

## 1.2.4 — Contrato HTML/CSS

### Pipeline de construcción HTML

La función `buildHtml(...)` ejecuta el siguiente pipeline:

```
body (String)
   → Jsoup.parseBodyFragment(body)
   → EmailHtmlCleaner.isSimpleHtml(doc)
   → EmailHtmlCleaner.clean(doc)
   → wrapper condicional (HTML simple)
   → template HTML completo con CSS inyectado
```

### Clasificación de HTML simple vs. complejo

`EmailHtmlCleaner.isSimpleHtml(doc)` retorna `true` cuando:
```kotlin
doc.select("table table").isEmpty()
```
Es decir, cuando no hay tablas anidadas (indicador heurístico de email compuesto en Gmail vs. newsletter/transaccional con layout tabular).

### Wrapper de HTML simple

Cuando `isSimple` es `true`:
```kotlin
"""<div style="margin:0 16px; padding-top: 20px;">$cleanedBody</div>"""
```
- Margen horizontal: `16px` (sin margen vertical adicional).
- Padding superior: `20px`.

Cuando `isSimple` es `false` (HTML complejo), el cuerpo limpio se inserta sin wrapper adicional.

### Conversión ARGB → CSS RGB

```kotlin
private fun toCssRgb(argb: Int): String {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgb($r,$g,$b)"
}
```

No se usa el canal Alpha; solo R, G, B. Esto es consistente con el `setBackgroundColor(surfaceArgb)` del WebView (que usa el ARGB completo para el fondo nativo) y con el CSS del body (que usa solo la porción RGB).

### Colores inyectados

| Variable CSS | Origen | Ejemplo dark | Ejemplo light |
|---|---|---|---|
| `--bg` | `toCssRgb(surfaceArgb)` | `rgb(28,27,31)` (M3 surface dark) | `rgb(255,251,254)` (M3 surface light) |
| `--text` | código fijo | `rgb(224,224,224)` | `rgb(33,33,33)` |
| `--link` | `toCssRgb(primaryArgb)` | `rgb(208,188,255)` (M3 primary dark) | `rgb(103,80,164)` (M3 primary light) |

Nótese que `--text` no usa ARGB ni ningún color del tema: es una constante fija en el código (`"rgb(224, 224, 224)"` para dark, `"rgb(33, 33, 33)"` para light).

### Política CSS de imágenes

Cuando `showImages == false`:
```css
img:not([src^="data:"]){display:none!important}
```
Cuando `showImages == true`: cadena vacía (sin regla inyectada).

### Template HTML completo

```html
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
<style>
  :root {
    color-scheme: {light|dark};
    --bg: rgb(R,G,B);
    --text: rgb(224,224,224)|rgb(33,33,33);
    --link: rgb(R,G,B);
  }
  * {
    -webkit-tap-highlight-color: transparent;
    color: var(--text) !important;
    opacity: 1 !important;
    text-shadow: none !important;
  }
  a, a * { color: var(--link) !important; }
  body {
    background-color: var(--bg);
    color: var(--text);
    font-family: -apple-system, Roboto, sans-serif;
    font-size: 15px;
    line-height: 1.5;
    margin: 0;
    padding: 8px 0;
    word-wrap: break-word;
    overflow-wrap: break-word;
  }
  img, table { max-width: 100%; height: auto; }
  blockquote {
    border-left: 3px solid var(--link);
    margin-left: 0;
    padding-left: 12px;
    color: var(--text);
  }
  pre, code {
    white-space: pre-wrap;
    word-break: break-all;
  }
  {img:not([src^="data:"]){display:none!important} | (vacío)}
</style>
</head>
<body>
{wrappedBody}
</body>
</html>
```

- `color-scheme` sigue `isDark` para que el WebView use su modo nativo apropiado.
- `user-scalable=yes` en el viewport meta permite zoom.
- `font-size: 15px` es fijo; no escala con accesibilidad del sistema.
- `-webkit-tap-highlight-color: transparent` elimina el resaltado azul/verde del toque en WebView.
- `color: var(--text) !important` y `opacity: 1 !important` fuerzan el color de texto del tema en todos los elementos (excepto enlaces, que usan `--link`). Esto es parte del hardening visual, no una omisión.

### Limpieza HTML (`EmailHtmlCleaner.clean`)

Propiedades eliminadas de `style` inline y de bloques `<style>`:
```
background, background-color, color, -webkit-text-fill-color, opacity
```

Adicionalmente:
- Atributos `bgcolor`, `background` en cualquier elemento.
- Atributo `color` en elementos `<font>`.
- Atributos `text`, `link`, `vlink`, `alink` en `<body>`.
- Meta tags `color-scheme` y `supported-color-schemes`.
- Atributos `color-scheme` y `supported-color-schemes` en elemento `<html>`.
- Bloques `@media` que contengan `prefers-color-scheme` (eliminados por completo).
- Comentarios CSS (`/* ... */`).
- En el overload `EmailHtmlCleaner.clean(html: String)`, un error de parseo retorna el HTML original (`fail-open`). `EmailBodyWebView.buildHtml()` no usa ese overload: primero ejecuta `Jsoup.parseBodyFragment(body)` y luego llama `EmailHtmlCleaner.clean(doc: Document)`.

---

## 1.2.5 — Contrato de WebSettings y plataforma

### `applyHardening(showImages: Boolean, isDark: Boolean)`

Aplicado en dos momentos: durante `factory` (creación del WebView) y al cargar un nuevo documento (`update action=load`). Los valores son idénticos en ambas llamadas para un mismo `(showImages, isDark)`.

| Setting | Valor | Nota |
|---|---|---|
| `javaScriptEnabled` | `false` | D4 — siempre deshabilitado |
| `domStorageEnabled` | `false` | Previene almacenamiento local |
| `allowFileAccess` | `false` | Sin acceso al sistema de archivos |
| `allowContentAccess` | `false` | Sin acceso a content providers |
| `allowFileAccessFromFileURLs` | `false` | Seguridad adicional |
| `allowUniversalAccessFromFileURLs` | `false` | Seguridad adicional |
| `mediaPlaybackRequiresUserGesture` | `true` | Previene autoplay |
| `cacheMode` | `LOAD_NO_CACHE` | No usa caché de red |
| `blockNetworkImage` | `!showImages` | Bloquea imágenes remotas si showImages=false |
| `blockNetworkLoads` | `!showImages` | D3 — bloquea todas las cargas de red si showImages=false |
| `useWideViewPort` | `true` | Adaptación a ancho móvil |
| `loadWithOverviewMode` | `true` | Zoom inicial para ajustar contenido |
| `textZoom` | `100` | Sin zoom de texto adicional |
| `builtInZoomControls` | `true` | Permite zoom del usuario |
| `displayZoomControls` | `false` | Oculta los controles de zoom en pantalla |
| `setSupportZoom(true)` | `true` | Habilita zoom por gestos |

### Política de red

La política de red está gobernada exclusivamente por `showImages`:
- `blockNetworkImage = !showImages`: bloquea carga de imágenes de red (pero no imágenes `data:`).
- `blockNetworkLoads = !showImages`: bloquea **todas** las cargas de red (imágenes, recursos, trackers).
- Cuando `showImages = true`, ambas flags están en `false` y el WebView puede cargar cualquier recurso de red.

En producción, `showImages` siempre es `true` (el consumidor `EmailDetailContent` no expone toggle). El código de `EmailBodyWebView` soporta `false` como parte de su API pública.

### Darkening algorítmico

```kotlin
if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
    WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, isDark)
}
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
    isAlgorithmicDarkeningAllowed = isDark
}
```

Dos paths (no mutuamente excluyentes):
1. AndroidX `WebSettingsCompat.setAlgorithmicDarkeningAllowed` cuando el feature está soportado.
2. API nativa `isAlgorithmicDarkeningAllowed` en API 33+ (`TIRAMISU`).

Ambos se activan cuando `isDark = true` y se desactivan cuando `false`. El darkening algorítmico aplica a contenido que no define su propio fondo.

---

## 1.2.6 — Contrato de interacción, lifecycle y liberación

### Creación del WebView (`factory`)

```kotlin
WebView(ctx).apply {
    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    setBackgroundColor(surfaceArgb)
    settings.applyHardening(showImages, isDark)
    // ... attach/detach listeners, long-click listener
}
```

- Layout: `MATCH_PARENT` × `MATCH_PARENT` — ocupa todo el espacio del padre (`Box` con `Modifier.fillMaxSize()`).
- Scrollbars: deshabilitadas (vertical y horizontal).
- Fondo nativo: `surfaceArgb` (color de superficie del tema M3).
- Hardening inicial aplicado con los valores del primer renderizado.
- Listener `OnAttachStateChangeListener`: emite trazas `WV_ATTACHED` y `WV_DETACHED` con `instance` (hash de identidad hexadecimal) y dimensiones.

### Long-press sobre imágenes

```kotlin
setOnLongClickListener {
    val hitTestResult = this.hitTestResult
    if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
        hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
    ) {
        val imageUrl = hitTestResult.extra
        if (!imageUrl.isNullOrBlank()) {
            onImageLongPress?.invoke(imageUrl)
            return@setOnLongClickListener true
        }
    }
    false
}
```

- Solo consume (`return true`) cuando el long-press cae sobre una imagen o un anchor de imagen con URL no vacía.
- Si el tipo no es imagen o anchor de imagen, o si `hitTestResult.extra` está vacío/nulo, retorna `false`. Si el hit es imagen con URL no vacía, ejecuta `onImageLongPress?.invoke(imageUrl)` y retorna `true`; por tanto consume el evento aunque el callback sea `null`.
- **No emite traza propia** dentro de `EmailBodyWebView`; la traza depende del callback suministrado por el consumidor.

### Enlaces externos (Custom Tabs)

Ambos overloads de `shouldOverrideUrlLoading` siguen el mismo patrón:

```kotlin
CustomTabsIntent.Builder()
    .setShowTitle(true)
    .build()
    .launchUrl(ctx, android.net.Uri.parse(url))
```

- El overload moderno (`WebResourceRequest`) y el legacy deprecado (`String`) abren el enlace en Chrome Custom Tabs con título visible.
- Retornan `true` siempre (no se navega dentro del WebView).
- Si la URL es `null`, retornan `true` sin abrir nada.
- Errores (ej. sin navegador compatible) se registran con `Log.w(TAG, ...)`; no se muestran al usuario ni se propagan al callback.

### Pausa y reanudación (lifecycle)

**`ON_PAUSE`:**
1. Si `webViewRef` es nulo → traza `WV_ON_PAUSE hasWebView=false`, retorna.
2. Guarda `savedScrollY = webView.scrollY`.
3. Traza `WV_ON_PAUSE hasWebView=true scrollY={scrollY}`.
4. Llama `webView.onPause()`.

**`ON_RESUME`:**
1. Si `webViewRef` es nulo → traza `WV_ON_RESUME hasWebView=false`, retorna.
2. Traza `WV_ON_RESUME hasWebView=true savedScrollY={savedScrollY}`.
3. Llama `webView.onResume()`.
4. Si `activeLoadKey == null` → traza `WV_RESUME_VISUAL_SKIPPED reason=no_active_document`, retorna.
5. Traza `WV_RESUME_VISUAL_REQUESTED`.
6. `webView.postVisualStateCallback(0L, object : WebView.VisualStateCallback { ... })`:
   - `onComplete`: traza `WV_RESUME_VISUAL_CALLBACK` con `loadKey`, `activeLoadKey`, `released`, `requestId`.
   - Si `released || activeLoadKey != resumeLoadKey` → retorna sin restaurar scroll.
   - `webView.post { ... }`: vuelve a verificar liberación/clave, luego `webView.scrollTo(0, savedScrollY)`, `webView.invalidate()`, traza `WV_RESUME_SCROLL_APPLIED`.

**`WV_LIFECYCLE_OBSERVER_DISPOSE`:** emitida al remover el observer en `onDispose`.

### Liberación (`onRelease` de `AndroidView`)

```kotlin
onRelease = { webView ->
    EmailRenderTrace.d(traceMail, "WV", "WV_RELEASE", "loadKey=${activeLoadKey} scrollY=${webView.scrollY}")
    savedScrollY.intValue = webView.scrollY
    released.value = true
    activeLoadKey = null
    webViewRef.value = null
    webView.stopLoading()
    webView.destroy()
}
```

Orden de operaciones en liberación:
1. Traza `WV_RELEASE` (con `loadKey` y `scrollY`).
2. Guarda `scrollY` (último intento de preservar posición).
3. Marca `released = true`.
4. Limpia `activeLoadKey = null` y `webViewRef = null`.
5. `stopLoading()` — detiene cualquier carga en progreso.
6. `destroy()` — libera recursos nativos del WebView.

Una vez liberado, los callbacks `onPageReady` y de resume rechazan cualquier acción porque `released == true` y `activeLoadKey == null`.

---

## 1.2.7 — Contrato de trazas

Todos los eventos usan `EmailRenderTrace.d(traceMail, layer, event, details?)` y solo se emiten en builds `DEBUG` (`if (!BuildConfig.DEBUG) return`).

### Eventos de capa WV (EmailBodyWebView)

| Evento | Disparador | Payload | Orden esperado |
|---|---|---|---|
| `HTML_BUILD_WAITING` | `LaunchedEffect(currentKey)` con `body==null` o `currentKey==null` | `reason=body_pending` | Antes de cualquier build |
| `HTML_BUILD_START` | Inicio de `buildHtml()` en `Dispatchers.Default` | `loadKey`, `bodyLen` | Después de WAITING, antes de END |
| `HTML_BUILD_END` | Fin de `buildHtml()` | `loadKey`, `htmlLen`, `durationMs` | Después de START, antes de READY |
| `HTML_BUILD_READY` | `preparedDocument` asignado | `loadKey`, `htmlLen` | Después de END |
| `WV_COMPOSABLE_ENTER` | `DisposableEffect(traceMail)` al componer | `loadKey`/`none`, `bodyLen` | Evento de entrada de composición; su orden relativo frente a `LaunchedEffect`/`AndroidView` no debe tratarse como absoluto fuera de una captura concreta |
| `WV_COMPOSABLE_DISPOSE` | `onDispose` del `DisposableEffect(traceMail)` | `loadKey`/`none` | Último evento WV al salir de composición |
| `WV_FACTORY` | `factory` de `AndroidView` | `instance` (hash hex), `loadKey`/`none` | Después de COMPOSABLE_ENTER |
| `WV_ATTACHED` | `onViewAttachedToWindow` | `instance`, `width`, `height` | Después de FACTORY |
| `WV_DETACHED` | `onViewDetachedFromWindow` | `instance`, `width`, `height` | Antes de RELEASE o al desmontar |
| `WV_UPDATE` (wait) | `update` con `preparedDocument==null` | `action=wait`, `reason=body_pending` o `html_pending:{key}` | Una vez por estado distinto; entre build y load |
| `WV_LOAD_DATA` | `update` con documento nuevo | `loadKey`, `htmlLen` | Inmediatamente antes de `loadDataWithBaseURL` |
| `WV_UPDATE` (load) | `update` con `lastLoaded != document.key` | `action=load`, `previousKey`, `loadKey`, `htmlLen` | Después de READY, antes de LOAD_DATA |
| `WV_UPDATE` (skip) | `update` con `lastLoaded == document.key` | `action=skip`, `loadKey`, `reason=already_loaded` | Una vez por clave repetida |
| `WV_PAGE_STARTED` | `onPageStarted` de `CustomTabsWebViewClient` | `loadKey` | Después de LOAD_DATA |
| `WV_PROGRESS` | `onProgressChanged` de `TraceWebChromeClient` (milestone 0/25/50/75/100) | `loadKey`, `progress`, `milestone` | Entre PAGE_STARTED y PAGE_FINISHED |
| `WV_COMMIT_VISIBLE` | `onPageCommitVisible` | `loadKey` | Después de PAGE_STARTED |
| `WV_PAGE_FINISHED` | `onPageFinished` | `loadKey` | Después de COMMIT_VISIBLE |
| `WV_VISUAL_REQUESTED` | `postVisualStateCallback` tras `onPageFinished` | `loadKey` | Después de PAGE_FINISHED |
| `WV_VISUAL_CALLBACK` | `onComplete` del `VisualStateCallback` | `loadKey`, `requestId` | Después de VISUAL_REQUESTED |
| `WV_SCROLL_RESTORE_POSTED` | `onPageReady` aceptado (callback válido) | `loadKey`, `scrollY` | Después de VISUAL_CALLBACK |
| `WV_SCROLL_RESTORE_APPLIED` | `scrollTo` + `invalidate` dentro del `post` | `loadKey`, `scrollY` | Después de SCROLL_RESTORE_POSTED |
| `WV_PAGE_RENDERED_DISPATCH` | `onPageRendered?.invoke()` dentro del `post` | `loadKey` | Último evento de carga exitosa |
| `WV_PAGE_RENDERED_IGNORED` | Callback rechazado (stale o released) | `loadKey`, `reason=stale_or_released`/`stale_after_post`, `activeLoadKey` (opcional), `released` | En lugar de SCROLL_RESTORE_POSTED/APPLIED + RENDERED_DISPATCH |
| `WV_ON_PAUSE` | `Lifecycle.Event.ON_PAUSE` | `hasWebView`, `scrollY` | Durante pausa |
| `WV_ON_RESUME` | `Lifecycle.Event.ON_RESUME` | `hasWebView`, `savedScrollY` | Durante reanudación |
| `WV_RESUME_VISUAL_SKIPPED` | Resume sin `activeLoadKey` | `reason=no_active_document` | En lugar de los eventos de resume visual |
| `WV_RESUME_VISUAL_REQUESTED` | `postVisualStateCallback` en resume | — (sin payload extra) | Después de ON_RESUME |
| `WV_RESUME_VISUAL_CALLBACK` | `onComplete` del `VisualStateCallback` de resume | `loadKey`, `activeLoadKey`, `released`, `requestId` | Después de RESUME_VISUAL_REQUESTED |
| `WV_RESUME_SCROLL_APPLIED` | `scrollTo` + `invalidate` en resume | `scrollY` | Después de RESUME_VISUAL_CALLBACK |
| `WV_LIFECYCLE_OBSERVER_DISPOSE` | `onDispose` del lifecycle observer | — (sin payload extra) | Al remover el observer |
| `WV_RELEASE` | `onRelease` de `AndroidView` | `loadKey`/`none`, `scrollY` | Último evento WV (junto con COMPOSABLE_DISPOSE) |

**Nota:** los eventos `WV_ATTACHED`/`WV_DETACHED` pueden repetirse sin `WV_FACTORY` intermedio si el WebView es re-attached (recomposición).

### Eventos de capa UI (EmailDetailContent)

| Evento | Disparador | Payload |
|---|---|---|
| `UI_CONTENT_ENTER` | `DisposableEffect(traceMail, email.id)` | — |
| `UI_CONTENT_DISPOSE` | `onDispose` del mismo | — |
| `UI_BODY_INPUT` | `LaunchedEffect(bodyKey)` | `present`, `bodyLen`, `bodyKey` |
| `UI_RENDER_STATE_RESET` | `LaunchedEffect(bodyKey)` | `bodyKey` |
| `UI_LOADER_SHOWN` | `LaunchedEffect(showLoader, bodyKey)` con `showLoader==true` | `reason` (`body_missing`/`awaiting_visual_callback`), `bodyKey` |
| `UI_LOADER_HIDDEN` | `LaunchedEffect(showLoader, bodyKey)` con `showLoader==false` | `reason=rendered`, `bodyKey` |
| `UI_FRAME` | `withFrameNanos` en `LaunchedEffect(showLoader, bodyKey)` | `loaderVisible`, `bodyKey`, `frameNanos` |
| `UI_BODY_LAYOUT` | `onGloballyPositioned` (solo si cambió) | `x`, `y`, `width`, `height` |
| `UI_WEBVIEW_SLOT_ENTER` | `DisposableEffect(traceMail)` dentro del slot del WebView | `bodyKey` |
| `UI_WEBVIEW_SLOT_DISPOSE` | `onDispose` del mismo | `bodyKey` |
| `UI_RENDER_CALLBACK` | `onPageRendered` del WebView | `bodyKey`, `wasRendered` |

### Eventos no trazados por diseño

- **Long-press exitoso sobre imagen:** no emite traza propia dentro de `EmailBodyWebView`. La invocación de `onImageLongPress` puede producir trazas en el consumidor, pero eso está fuera del alcance del componente.
- **`shouldOverrideUrlLoading` exitoso:** solo emite `Log.w` en caso de error; no usa `EmailRenderTrace`.
- **Apertura exitosa de Custom Tab:** no tiene traza.
- **Cambios de `modifier`:** no son trazables ni observables desde el WebView.
- **Colores ARGB individuales:** van incluidos en `loadKey` como parte de la fórmula; no se emiten como eventos separados.

---

## 1.2.8 — Matriz de bloqueo para fases posteriores

Cada invariante listado abajo **bloquea** cualquier subfase posterior si cambia sin justificación documentada explícita en el plan de refactor.

| Invariante | Estado de verificación | Nota |
|---|---|---|
| Fórmula de `buildLoadKey` | 🔧 Automatizable (JVM) | El orden y los campos son fijos; un cambio rompe la unicidad de clave |
| `currentKey` depende de `(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb)` | 🔧 Automatizable (JVM) | Recomposición ante cambio de cualquiera de los seis |
| Flujo wait/load/skip (3 ramas de `AndroidView.update`) | 🔧 Automatizable (instrumentado) | `action=wait/load/skip` en trazas + `lastLoaded` |
| Rechazo de callbacks por `released` y `activeLoadKey` | 🔧 Automatizable (instrumentado) | `WV_PAGE_RENDERED_IGNORED` con `reason=stale` |
| `body == null` mantiene WebView montado sin contenido | 🔧 Automatizable (instrumentado) | `HTML_BUILD_WAITING` + `WV_UPDATE action=wait` |
| Uso de `Jsoup.parseBodyFragment` + `EmailHtmlCleaner.clean` | 🔧 Automatizable (JVM) | El pipeline HTML no depende de Android |
| `EmailHtmlCleaner.isSimpleHtml`: `table table` vacío | 🔧 Automatizable (JVM) | Regla heurística fija |
| Wrapper HTML simple: `margin:0 16px; padding-top: 20px` | 🔧 Automatizable (JVM) | Literal en `buildHtml` |
| Colores CSS: `--text` fijo (`224,224,224` dark / `33,33,33` light), `--bg` y `--link` desde ARGB | 🔧 Automatizable (JVM) | Constantes en `buildHtml` |
| CSS `hideRemoteImages`: `img:not([src^="data:"]){display:none!important}` | 🔧 Automatizable (JVM) | Solo presente si `showImages==false` |
| `toCssRgb`: (`shr 16`, `shr 8`, `and 0xFF`) → `rgb(R,G,B)` | 🔧 Automatizable (JVM) | Sin canal Alpha |
| WebView `layoutParams`: `MATCH_PARENT` × `MATCH_PARENT` | 🔧 Automatizable (instrumentado) | Verificable en el árbol de vistas |
| WebView scrollbars deshabilitadas | 🔧 Automatizable (instrumentado) | `isVerticalScrollBarEnabled=false`, `isHorizontalScrollBarEnabled=false` |
| `setBackgroundColor(surfaceArgb)` en factory y load | 🔧 Automatizable (instrumentado) | Reaplicado en cada carga |
| `WebSettings.javaScriptEnabled = false` | 🔧 Automatizable (instrumentado) | D4 — siempre |
| `WebSettings.domStorageEnabled = false` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.allowFileAccess = false` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.allowContentAccess = false` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.allowFileAccessFromFileURLs = false` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.allowUniversalAccessFromFileURLs = false` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.mediaPlaybackRequiresUserGesture = true` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.cacheMode = LOAD_NO_CACHE` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.blockNetworkImage = !showImages` | 🔧 Automatizable (instrumentado) | Política de red — gobernada por `showImages` |
| `WebSettings.blockNetworkLoads = !showImages` | 🔧 Automatizable (instrumentado) | D3 — política de red total |
| `WebSettings.useWideViewPort = true` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.loadWithOverviewMode = true` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.textZoom = 100` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.builtInZoomControls = true` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.displayZoomControls = false` | 🔧 Automatizable (instrumentado) | — |
| `WebSettings.setSupportZoom(true)` | 🔧 Automatizable (instrumentado) | — |
| Darkening algorítmico: AndroidX + API 33 nativa | 🔧 Automatizable (instrumentado) | Ambos paths según `isDark` |
| `loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)` | 🔧 Automatizable (instrumentado) | D2 — `baseUrl` siempre `null` |
| Long-press: solo `IMAGE_TYPE`/`SRC_IMAGE_ANCHOR_TYPE` con URL no vacía | 👤 Manual | `HitTestResult` difícil de mockear de forma estable |
| Enlaces externos: ambos overloads → Custom Tabs con `setShowTitle(true)`, retorno `true` | 👤 Manual | Custom Tabs no se mockea en tests instrumentados |
| `ON_PAUSE`: `webView.onPause()`, guarda `scrollY` | 🔧 Automatizable (instrumentado) + trazas | Verificable vía lifecycle + logcat |
| `ON_RESUME`: `webView.onResume()`, `postVisualStateCallback` → `scrollTo` | 🔧 Automatizable (instrumentado) + trazas | Scroll restaurado solo con documento activo |
| `onRelease`: `stopLoading()` + `destroy()` + `released=true` + `activeLoadKey=null` | 🔧 Automatizable (instrumentado) | Liberación verificable en el árbol de vistas |
| Secuencia de trazas WV documentada | 🔧 Automatizable (logcat) | Payload y capa congelados; el orden se valida por flujo, no como lista global absoluta |
| Secuencia de trazas UI documentada | 🔧 Automatizable (logcat) | Payload y capa congelados; el orden se valida por flujo, no como lista global absoluta |
| Long-press exitoso sin traza propia en `EmailBodyWebView` | 👤 Manual | Ausencia verificable en logcat, difícil de automatizar |

**Leyenda:**
- 🔧 Automatizable: puede verificarse con tests JVM, instrumentados o análisis de logcat.
- 👤 Manual: requiere verificación humana en emulador o dispositivo físico; no es observable de forma estable en tests automatizados.
- Ningún invariante se clasifica como "no observable estable" porque todos producen trazas o son verificables en el árbol de vistas con las herramientas existentes.

---

## Verificación

- SHA-256 de `EmailBodyWebView.kt`: verificado al cierre (debe coincidir con `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`).
- `git -c core.fsmonitor=false status --short`: solo los cambios ajenos conocidos (`ComposeScreen.kt`, `MainNavHost.kt`) más `docs/verification/emailbody-webview-baseline/`.
- `git diff --check`: sin salida (limpio).
- No se ejecutaron builds ni tests en esta subfase; la Etapa 2 los inicia.
