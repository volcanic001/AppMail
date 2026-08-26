# Resultados — Subfase 2.3: pruebas de caracterización sin hooks de producción

## 1. Estado

**COMPLETADA.** La nueva suite `EmailBodyWebViewBaselineTest` (6 casos) quedó
verde en tres corridas consecutivas (18/18) y la suite completa pasó 290/290
(284 previas + 6 nuevas), con cero failures, errors, skipped o crashes. No se
modificó producción, Gradle ni los ajustes test-only de 2.2-R.

## 2. Identidad

- **Fecha local**: ejecuciones 2026-08-25 (CST, `-0600`).
- **Rama**: `main`.
- **HEAD**: `2a433a67af4ea6f4f27e320133c963a408e80453`.
- **Hashes protegidos** (verificados al inicio y al cierre):
  - `EmailBodyWebView.kt`: `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`;
  - `ComposeScreen.kt`: `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69`;
  - `MainNavHost.kt`: `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.

## 3. Precondiciones verificadas

- HEAD y los tres hashes protegidos coinciden con el baseline registrado.
- `EmailBodyWebView.kt`, `ComposeScreen.kt` y `MainNavHost.kt` sin cambios
  (hashes idénticos; `ComposeScreen.kt`/`MainNavHost.kt` conservan solo los
  cambios ajenos congelados).
- 2.2 conserva su cierre `GO` documentado en `resultados-subfase-2.2.md`
  (G21 focal estable 102/102; G22 completa 284/284).
- Los ajustes test-only de 2.2-R (`EmailDetailCancellationTest.kt` y
  `EmailDetailPresentationTest.kt`) permanecen sin revertir, como parte de la
  red de seguridad vigente.

## 4. Dispositivo

- **Serial**: `emulator-5554` (único dispositivo en `adb devices` durante toda
  la serie; el Pixel 9 físico no apareció y quedó excluido).
- **AVD**: `Medium_Phone_API_36.1` (Android release 16, SDK `36`,
  `sys.boot_completed=1`).
- **Arranque**: `emulator @Medium_Phone_API_36.1 -port 5554 -no-snapshot-load
  -no-boot-anim -no-audio` (proceso persistente; log en
  `/tmp/mailapp-emailbody-2.3-emulator.log`). El AVD no se reinició ni se
  limpió entre corridas.

## 5. Cambios de implementación

- **Suite nueva**:
  `app/src/androidTest/java/com/david/mailapp/feature/emaildetail/components/EmailBodyWebViewBaselineTest.kt`
  (paquete `feature.emaildetail.components`; 6 casos exactos según el plan
  técnico; solo API pública + árbol Compose/Espresso; cero parámetros,
  interfaces, test tags, flags o hooks en producción).
- **Assets** (copias literales de las fixtures canónicas):
  `app/src/androidTest/assets/emailbody-webview/{01-html-simple,02-newsletter-tabla,03-imagen-remota,04-imagen-data,05-enlace-externo}.html`.
  Cada copia validada con `cmp` y SHA-256 contra
  `docs/verification/emailbody-webview-baseline/fixtures/`.

| Fixture | SHA-256 (canónica = copia) |
|---|---|
| `01-html-simple.html` | `d8d8427586dcf15f51eea0a3a71da210a9bdaa6fdea4a4d9682cc976c4b16e76` |
| `02-newsletter-tabla.html` | `686f5b5bb51385d2deba3acf81c892b1a07902a9e4e2e34f29b44ae1c23251d1` |
| `03-imagen-remota.html` | `06124fab2eb3c3cd0701542c48843752ed2ddd53f71db05c129e04d41a7d39db` |
| `04-imagen-data.html` | `2d0648beee697bf4f4715cc2d0a38a97f71b1b3967f5d86255b51914d6ae548d` |
| `05-enlace-externo.html` | `9da27b6ba5389a1f58627e5018a5ed33a0f84c1f2762afdb983023b70442a610` |

- **Documentación y reportes**: este documento y
  `reportes-subfase-2.3/{focal-corrida-1.xml,focal-corrida-2.xml,focal-corrida-3.xml,completa.xml}`.

## 6. Compilación AndroidTest

- Comando: `./gradlew compileDebugAndroidTestKotlin --rerun-tasks --console=plain`.
- Resultado final: `BUILD SUCCESSFUL in 32s`; solo warnings de deprecación
  preexistentes en otras clases.
- Nota de autoría: la primera compilación de la suite nueva reportó dos
  errores de código de test, corregidos en el propio archivo antes de cualquier
  corrida: `settings.supportZoom` → `settings.supportZoom()` (getter es método,
  no propiedad) y `Espresso.onAllViews` (eliminado en espresso-core 3.7.0) →
  resolución de WebView único vía `Espresso.onView`, que lanza excepción ante 0
  o 2+ coincidencias. No se tocó producción ni Gradle.

## 7. G23 — Serie focal (EmailBodyWebViewBaselineTest)

Comando literal (idéntico en las tres corridas):

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.emaildetail.components.EmailBodyWebViewBaselineTest
```

| Corrida | Resultado | Fallos | Errores | Omitidas | Duración Gradle | SHA-256 XML |
|---:|---:|---:|---:|---:|---:|---|
| 1 | 6/6 | 0 | 0 | 0 | 2m01s | `5d5198c7369f4d834d3bb045d4889217044fb8d28029240218c6695f7b729d3d` |
| 2 | 6/6 | 0 | 0 | 0 | 1m52s | `c47d92bf599f55da0d78f7c0b8087310431c8ca9f37cf6ac51458cf97130405c` |
| 3 | 6/6 | 0 | 0 | 0 | 2m08s | `cac12c1d4fd55cbacf00cf3192d0f21a4fd368aae87f96429fdcf0b734a4b3b1` |

Total: **18/18**, cero flakiness. Dispositivo reportado en los tres XML:
`Medium_Phone_API_36.1(AVD) - 16`. Sin reintentos necesarios.

Duración de cada caso en la corrida 3 (evidencia por caso):

| Caso | Tiempo |
|---|---:|
| `bodyPending_keepsWebViewMounted_andLoadsWhenBodyArrives` | 9.517 s |
| `algorithmicDarkening_followsIsDark` | 8.225 s |
| `canonicalFixtures_loadSequentially_inSameWebView` | 6.916 s |
| `hardeningViewportAndZoomSettings_matchBaseline` | 5.149 s |
| `networkBlocking_followsShowImagesAcrossRecomposition` | 5.222 s |
| `replacedDocument_doesNotDispatchStaleCallback` | 4.830 s |

## 8. G24 — Suite completa

- Comando: `ANDROID_SERIAL=emulator-5554 ./gradlew
  connectedDebugAndroidTest --rerun-tasks --console=plain`.
- Resultado: `BUILD SUCCESSFUL in 9m 11s`.
- XML: **290/290**, 27 clases, 0 failures, 0 errors y 0 skipped.
- Dispositivo reportado: `Medium_Phone_API_36.1(AVD) - 16`.
- El conteo coincide con la referencia esperada de esta subfase (284 previas
  + 6 de `EmailBodyWebViewBaselineTest`, confirmada por el conteo de
  `classname` en el XML agregado).

## 9. Reportes conservados

| Reporte | Tests | SHA-256 |
|---|---:|---|
| `reportes-subfase-2.3/focal-corrida-1.xml` | 6 | `5d5198c7369f4d834d3bb045d4889217044fb8d28029240218c6695f7b729d3d` |
| `reportes-subfase-2.3/focal-corrida-2.xml` | 6 | `c47d92bf599f55da0d78f7c0b8087310431c8ca9f37cf6ac51458cf97130405c` |
| `reportes-subfase-2.3/focal-corrida-3.xml` | 6 | `cac12c1d4fd55cbacf00cf3192d0f21a4fd368aae87f96429fdcf0b734a4b3b1` |
| `reportes-subfase-2.3/completa.xml` | 290 | `29f61c22b9ebc5c17b2c8dc4b3aabf0a54fcdd09fe2d028117a95f880bb5f0f8` |

Los reportes son copias exactas de los XML Gradle aceptados. La búsqueda de
`Authorization`, `Bearer`, `access_token`, `refresh_token`, `@gmail.com` y
`@outlook.com` en los cuatro XML no produjo resultados.

## 10. Notas técnicas de la suite

- **Espera observable**: `WaitForRenderedDocument` exige simultáneamente
  `progress == 100`, URL no nula y callback recibido para el ID de fixture
  activo (equivalente a `WaitForWebViewProgress` ampliado); el bucle usa
  `uiController.loopMainThreadForAtLeast(50)`. Prohibidos sleeps y delays
  ciegos.
- **Instancia única**: la identidad del `WebView` se captura y se compara con
  `assertSame` a lo largo de cada transición; `Espresso.onView` sobre
  `WebView::class.java` garantiza exactamente una instancia (ambiguo o ausente
  lanza excepción de matcher).
- **IDs estables**: la lambda `onPageRendered` de cada composición captura el
  ID de fixture estable de esa composición (`val stableFixtureId =
  fixtureId.value`); nunca infiere el ID desde estado mutable global al
  invocarse.
- **Caso 4 (darkening)**: en API 36 se verifica el getter nativo
  `isAlgorithmicDarkeningAllowed` con `false → true → false`, una carga nueva
  por cambio y la misma instancia; además `WebSettingsCompat.isAlgorithmicDarkeningAllowed`
  (feature soportado en el AVD, comprobado con `WebViewFeature`). El test
  aserta `SDK_INT == 36`, por lo que la ruta nativa siempre es observable y un
  dispositivo distinto (p. ej. el Pixel 9, API 37) fallaría en lugar de omitir.
- **Caso 5**: la fixture remota se carga con `showImages=false` (sin
  dependencia de Internet); las cinco cargas reutilizan el mismo `WebView` y
  emiten exactamente un callback por fixture, en orden, sin duplicados. No se
  pulsó el enlace externo ni se ejecutó long-press: permanecen manuales para
  la Subfase 3.2.
- **Caso 6**: se verifica la sustitución activa del `WebViewClient` (la carga
  nueva instala una instancia distinta) y, desde ese punto, la ausencia de
  callbacks del documento anterior (el conteo de `F02-long` no crece tras la
  sustitución). La carrera profunda —callback visual del documento anterior
  completándose después de iniciarse la carga nueva— no se fuerza con hooks:
  la ruta de rechazo de producción (`activeLoadKey != document.key`) es la que
  la hace imposible de observar; se conserva como verificación manual
  obligatoria, sin relajar expectativas ni añadir código de prueba a
  producción.
- **S14 (derivación)**: el newsletter largo se construye en código de test
  repitiendo 20 veces el bloque de tabla interior de la fixture 02 (extraído
  literalmente del archivo, no reescrito).

## 11. Integridad final

- Hashes finales de los tres archivos protegidos: idénticos a la sección 2.
- `git diff --check`: sin salida (limpio).
- Estado Git: únicamente los dos cambios ajenos congelados, los ajustes
  test-only de 2.2-R, la suite nueva, los cinco assets y la documentación/
  reportes del baseline. Se eliminó `.kotlin/sessions/` (artefacto transitorio
  del compilador Kotlin generado durante los builds; `.kotlin/errors/` es
  contenido trackeado previo y no se tocó). Producción y configuración Gradle
  sin cambios.
- Sin commit: el commit documental queda reservado para el paquete de handoff
  (Subfase 4.2).
- AVD y emulador dejados activos conforme al plan (no se cerró ni borró el
  dispositivo).

## 12. Conclusión

**GO — Subfase 2.3 cerrada; autorizado continuar con la Subfase 3.1.**
