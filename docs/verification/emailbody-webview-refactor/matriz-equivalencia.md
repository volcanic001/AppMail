# Matriz de equivalencia — Refactor estructural de EmailBodyWebView

Subfase 1.3 — Matriz de equivalencia
Fecha de ejecución: 2026-08-27T10:59:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro (modo auditoría)
Commit previsto: `docs(emailbody): map webview refactor equivalence`

> Documento **solo documental**. Conecta cada contrato observable del baseline
> con la zona del refactor que puede romperlo, la subfase futura responsable y
> la prueba/evidencia que debe validarlo. No mueve código ni cambia lógica.

---

## 1. Cobertura del baseline

El baseline congelado consta de **40 contratos** (`contratos-observables.md`
1.2.8 y tabla final de `registro-tecnico.md` del baseline), desglosados en:

- **34 automatizados**: verificables por tests JVM, instrumentados o logcat.
- **6 manuales**: inspección estática o revisión visual humana.

Y de **22 tests** en `EmailBodyWebViewBaselineTest.kt`:

- 6 de caracterización directa (Subfase 2.3).
- 12 de la matriz de carga S01–S08 / S10–S13 (Subfase 3.1).
- 4 de interacción y lifecycle S09 / S14–S16 (Subfase 3.2).

La matriz traza los 22 tests hacia los 40 contratos **sin reinterpretarlos ni
ampliar comportamiento**. Un contrato que no pueda mapearse con evidencia
existente queda como **NO-GO** y bloquea el avance a Etapa 2.

---

## 2. Zonas del refactor y subfases responsables

| Zona | Descripción | Subfases futuras que la tocan |
|---|---|---|
| Z1 — Entrada/fachada | Firma pública, consumidor único, `modifier`, `LocalContext`, `LocalLifecycleOwner`, tema, colores, cálculo de `currentKey`, `DisposableEffect(traceMail)` | 5.1 (fachada), 5.2 (consolidación) |
| Z2 — Preparación/documento | `currentKey`, `buildLoadKey`, `PreparedDocument`, `LaunchedEffect`, `Dispatchers.Default`, `buildHtml`, Jsoup, cleaner, `toCssRgb` | 2.1 (modelo/clave/colores), 2.2 (HTML), 2.3 (async) |
| Z3 — Settings/WebView | `applyHardening`: hardening, red, imágenes, viewport, zoom, darkening, background | 3.1 (configuración WebView) |
| Z4 — Runtime/update | `lastLoaded`, `activeLoadKey`, ramas wait/load/skip, no-reload equivalente, cambio de body/tema/imágenes, `loadDataWithBaseURL` | 4.1 (estado runtime), 4.4 (update/carga/release) |
| Z5 — Clients/callbacks | `CustomTabsWebViewClient`, `TraceWebChromeClient`, progreso, visual callbacks, rechazo stale | 3.2 (progreso), 3.3 (navegación/página lista) |
| Z6 — Lifecycle/release | pausa, resume, restauración de scroll, `WeakReference`, `released`, `onRelease`, destrucción, reapertura | 4.2 (lifecycle/scroll), 4.4 (release) |
| Z7 — Host/factory/long-press | `Box`, `AndroidView`, `factory`, attach/detach, layout, scrollbars, fondo, long-press de imágenes | 4.3 (factory/attach/long-press) |
| Z8 — Interacciones transversales | Custom Tabs (S09), long-press `data:` (S15), capturas y trazas | 3.3 + 4.3 (verificadas en 6.2/6.3) |

**Nota sobre Z1/consumidor:** la secuencia de trazas UI (`UI_*`) pertenece a
`EmailDetailContent`, que queda **fuera de alcance**. Ninguna subfase la toca;
solo se vigila en la consolidación 5.2 y la auditoría 6.4.

---

## 3. Matriz de equivalencia por contrato

Leyenda de evidencia: **T** = aserción directa de test, **Tr** = traza logcat,
**Cap** = captura PNG, **XML** = reporte instrumentado, **Ins** = inspección
estática, **Vis** = revisión visual humana.

| ID | Contrato (conducta que debe permanecer igual) | Zona | Subfase | Test / escenario que lo valida | Evidencia | Criterio GO / NO-GO |
|---|---|---|---|---|---|---|
| C01 | Fórmula y orden de `buildLoadKey` (`body.hashCode()` + 6 componentes) | Z2 | 2.1 | S10–S13 + JVM de clave (2.1) | Tr / T | GO si la clave conserva el orden literal; NO-GO si cambia algún componente u orden |
| C02 | `currentKey` depende de `(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb)` | Z1 | 5.1 | S11 (body), S12 (tema), S13 (imágenes), S10 (no cambio) | Tr | GO si cada cambio produce nueva clave y el no-cambio produce skip; NO-GO si se pierde alguna dependencia |
| C03 | Ramas `wait/load/skip` de `AndroidView.update` | Z4 | 4.4 | T1, T5, T6, S01, S10, S11, S16 | Tr / T | GO si las tres ramas emiten `action=wait/load/skip` en el orden documentado; NO-GO si se recarga o se salta indebidamente |
| C04 | Rechazo por `released`/`activeLoadKey` (stale) | Z4/Z5/Z6 | 4.4 + 4.2 | T6, S09, S14 | Tr / T | GO si `WV_PAGE_RENDERED_IGNORED` solo aparece para claves obsoletas; NO-GO si un callback stale despacha |
| C05 | `body == null` mantiene el WebView montado sin contenido | Z2/Z4 | 2.3 + 4.4 | T1, S01 | Tr / T | GO si hay un solo WebView montado y `HTML_BUILD_WAITING`; NO-GO si se desmonta o carga con null |
| C06 | Pipeline Jsoup `parseBodyFragment` + `EmailHtmlCleaner.clean` | Z2 | 2.2 | T5, S04, JVM de caracterización (2.2) | Ins / T | GO si una sola llamada a Jsoup y limpieza; NO-GO si cambia el pipeline |
| C07 | Heurística de HTML simple (`table table` vacío) | Z2 | 2.2 | S04, S05 (newsletter) | Ins / Cap | GO si se conserva la regla; NO-GO si cambia la clasificación |
| C08 | Wrapper simple (`margin:0 16px; padding-top: 20px`) | Z2 | 2.2 | S01–S03 | Ins / Cap | GO si el literal se conserva; NO-GO si cambia |
| C09 | Variables y colores CSS (`--text` fijo, `--bg`/`--link` desde ARGB) | Z2 | 2.2 | S02, S03, S12 | Cap / Vis | GO si los colores inyectados coinciden; NO-GO si varía `--text` o el mapeo ARGB |
| C10 | CSS de ocultación remota (`img:not([src^="data:"])`) solo si `!showImages` | Z2 | 2.2 | S07, S08, S13 | Cap / T | GO si la regla aparece solo con `showImages=false`; NO-GO si se filtra `data:` o aparece con `true` |
| C11 | Conversión ARGB → `rgb(R,G,B)` (sin Alpha) | Z2 | 2.1 | JVM de `toCssRgb` (2.1) | Ins / T | GO si `(shr 16, shr 8, and 0xFF)`; NO-GO si se usa Alpha |
| C12 | `MATCH_PARENT × MATCH_PARENT` | Z7 | 4.3 | T2 | T | GO si layout conserva MATCH_PARENT; NO-GO si cambia |
| C13 | Scrollbars deshabilitadas (vertical y horizontal) | Z7 | 4.3 | T2 | T | GO si ambas `false`; NO-GO si cambia |
| C14 | Fondo `surfaceArgb` aplicado en factory y carga | Z7/Z4 | 4.3 + 4.4 | T2, S02, S03, S12 | T / Cap | GO si se reaplica en cada carga; NO-GO si se omite |
| C15 | `javaScriptEnabled = false` | Z3 | 3.1 | T2 | T | GO si `false`; NO-GO si cambia |
| C16 | `domStorageEnabled = false` | Z3 | 3.1 | T2 | T | GO si `false`; NO-GO si cambia |
| C17 | `allowFileAccess = false` | Z3 | 3.1 | T2 | T | GO si `false`; NO-GO si cambia |
| C18 | `allowContentAccess = false` | Z3 | 3.1 | T2 | T | GO si `false`; NO-GO si cambia |
| C19 | `allowFileAccessFromFileURLs = false` | Z3 | 3.1 | T2 | T | GO si `false`; NO-GO si cambia |
| C20 | `allowUniversalAccessFromFileURLs = false` | Z3 | 3.1 | T2 | T | GO si `false`; NO-GO si cambia |
| C21 | `mediaPlaybackRequiresUserGesture = true` | Z3 | 3.1 | T2 | T | GO si `true`; NO-GO si cambia |
| C22 | `cacheMode = LOAD_NO_CACHE` | Z3 | 3.1 | T2 | T | GO si `LOAD_NO_CACHE`; NO-GO si cambia |
| C23 | `blockNetworkImage = !showImages` | Z3 | 3.1 | T3, S07, S08, S13 | T | GO si sigue a `showImages`; NO-GO si se invierte |
| C24 | `blockNetworkLoads = !showImages` | Z3 | 3.1 | T3, S07, S08, S13 | T | GO si sigue a `showImages`; NO-GO si se invierte |
| C25 | `useWideViewPort = true` | Z3 | 3.1 | T2 | T | GO si `true`; NO-GO si cambia |
| C26 | `loadWithOverviewMode = true` | Z3 | 3.1 | T2 | T | GO si `true`; NO-GO si cambia |
| C27 | `textZoom = 100` | Z3 | 3.1 | T2 | T | GO si `100`; NO-GO si cambia |
| C28 | `builtInZoomControls = true` | Z3 | 3.1 | T2 | T | GO si `true`; NO-GO si cambia |
| C29 | `displayZoomControls = false` | Z3 | 3.1 | T2 | T | GO si `false`; NO-GO si cambia |
| C30 | `setSupportZoom(true)` | Z3 | 3.1 | T2 | T | GO si `true`; NO-GO si cambia |
| C31 | Darkening algorítmico (AndroidX + API 33 nativa) según `isDark` | Z3 | 3.1 | T4, S03, S05, S12 | T | GO si ambos paths siguen `isDark`; NO-GO si se elimina un path |
| C32 | `loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)` | Z4 | 4.4 | S01–S13 (cargas) | Ins / Tr | GO si los argumentos son exactos (baseUrl null); NO-GO si cambia |
| C33 | Long-press solo `IMAGE_TYPE`/`SRC_IMAGE_ANCHOR_TYPE` con URL no vacía | Z7 | 4.3 | S15 | T | GO si entrega exactamente la URL `data:`; NO-GO si consume otro hit |
| C34 | Enlaces externos abren Custom Tab con `setShowTitle(true)`, retornan `true`, capturan `Exception` | Z5 | 3.3 | S09 | T / Tr / Cap | GO si abre Custom Tab y el detalle sobrevive; NO-GO si navega dentro del WebView |
| C35 | `ON_PAUSE` guarda `scrollY` y llama `onPause()` | Z6 | 4.2 | S14 | Tr / T | GO si `WV_ON_PAUSE` registra `scrollY`; NO-GO si no guarda |
| C36 | `ON_RESUME` restaura scroll tras estado visual (`postVisualStateCallback`) | Z6 | 4.2 | S14 | Tr / T / Cap | GO si scroll restaurado y PNG antes/después equivalentes; NO-GO si recarga o no restaura |
| C37 | Release: `stopLoading()` + `destroy()` + `released=true` + `activeLoadKey=null` | Z6/Z4 | 4.4 | S16 | Tr / T | GO si un solo `WV_RELEASE` y reapertura con nueva instancia; NO-GO si doble release o instancia reutilizada |
| C38 | Secuencia de trazas WV (nombres, payload, orden por flujo) | Todas | 5.2 | S01–S16 (32 logs) | Tr | GO si los logs coinciden con el baseline; NO-GO si cambia payload u orden |
| C39 | Secuencia de trazas UI (`UI_*` en `EmailDetailContent`) | Consumidor (fuera de alcance) | vigilada en 5.2/6.4 | S09, S14, S16 | Tr | GO si permanece intacta (no tocada); NO-GO si el refactor la altera |
| C40 | Long-press exitoso no emite traza propia en `EmailBodyWebView` | Z7 | 4.3 | S15 | Tr | GO si el log de S15 no añade eventos propios; NO-GO si aparece traza nueva |

---

## 4. Cobertura de los 22 tests

| # | Test | Contratos que cubre |
|---|---|---|
| T1 | `bodyPending_keepsWebViewMounted_andLoadsWhenBodyArrives` | C03, C04, C05 |
| T2 | `hardeningViewportAndZoomSettings_matchBaseline` | C12–C30 |
| T3 | `networkBlocking_followsShowImagesAcrossRecomposition` | C03, C23, C24 |
| T4 | `algorithmicDarkening_followsIsDark` | C03, C31 |
| T5 | `canonicalFixtures_loadSequentially_inSameWebView` | C03, C06, C09, C10 |
| T6 | `replacedDocument_doesNotDispatchStaleCallback` | C03, C04 |
| S01 | `s01_bodyNullToSimple_light_loadsAndTraces` | C03, C05, C08, C09, C38 |
| S02 | `s02_simpleLight_initial` | C03, C09, C14, C38 |
| S03 | `s03_simpleDark_initial` | C03, C09, C14, C31, C38 |
| S04 | `s04_newsletterLight_initial` | C03, C06, C07, C09, C38 |
| S05 | `s05_newsletterDark_initial` | C03, C07, C09, C31, C38 |
| S06 | `s06_remoteImageEnabled_light` | C03, C09, C38 |
| S07 | `s07_remoteImageBlocked_light` | C03, C10, C23, C24, C38 |
| S08 | `s08_dataImageRemoteBlocked_light` | C03, C10, C23, C24, C38 |
| S09 | `s09_externalLink_opensCustomTab_andDetailSurvives` | C03, C04, C34, C39 |
| S10 | `s10_equivalentRecomposition_noReload` | C01, C02, C03, C38 |
| S11 | `s11_bodyChange_simpleToNewsletter_light` | C01, C02, C03, C38 |
| S12 | `s12_themeChange_lightToDark` | C01, C02, C03, C09, C14, C31, C38 |
| S13 | `s13_imagePolicyChange_enabledToBlocked` | C01, C02, C03, C10, C23, C24, C38 |
| S14 | `s14_longNewsletter_scrollAndLifecycle_restoresScrollWithoutReload` | C03, C04, C35, C36, C38, C39 |
| S15 | `s15_longPress_onDataImage_deliversExactDataUrl` | C33, C40 |
| S16 | `s16_release_andReopen_createsNewInstance` | C03, C04, C37, C38, C39 |

Cada contrato está cubierto por al menos un test o escenario de la sección 3;
no queda ningún contrato sin equivalencia demostrable.

---

## 5. Reglas de equivalencia no negociables

Congeladas en la Subfase 1.2 y reafirmadas aquí:

- Misma firma pública y mismos defaults.
- Misma fórmula de `buildLoadKey`, incluyendo `onSurfaceArgb`.
- Mismas claves Compose: `remember`, `LaunchedEffect`, `DisposableEffect(traceMail)`,
  `DisposableEffect(lifecycleOwner)`.
- Mismo orden observable de `HTML_BUILD_*`, `WV_UPDATE`, `WV_LOAD_DATA`,
  callbacks visuales, scroll restore y release.
- Mismos valores de `WebSettings.applyHardening`.
- Misma política de URL: Custom Tabs, retorno `true`, captura de `Exception`,
  sin validación nueva.
- Mismo comportamiento heredado de callbacks, sin `rememberUpdatedState`.
- Mismo defecto conocido de overflow en F02 (newsletter con tablas); **no
  corregirlo** durante el refactor estructural.

---

## 6. Defectos conocidos preservados (no bloquean, no se corrigen)

- **F02 overflow horizontal**: newsletter con tablas reproduce overflow en
  claro/oscuro, emulador y Pixel 9. Es defecto visual de referencia.
- **S06 imagen remota sintética no cargada**: mismo recurso no cargado en ambos
  dispositivos; S07/S13 verifican bloqueo y S08/S15 preservan `data:`.

Estos defectos se mantienen como comportamiento de referencia y no deben ser
corregidos por ninguna extracción.

---

## 7. Criterio GO global

La subfase 1.3 queda **GO** cuando:

- La matriz cubre los 40 contratos (34 automatizados + 6 manuales).
- Los 22 tests quedan trazados a zonas y subfases futuras.
- Cada fila tiene criterio GO/NO-GO explícito.
- Ningún contrato queda sin equivalencia demostrable.
- La matriz no autoriza cambios de lógica ni cambios de tests para aceptar
  regresiones.

Cualquier contrato sin evidencia existente queda **NO-GO** y bloquea el avance
a Etapa 2.
