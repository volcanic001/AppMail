# Registro técnico — Refactor estructural de EmailBodyWebView

Subfase 1.1 — Estado inicial y protección
Fecha de ejecución: 2026-08-27T08:40:12-0600 (CST)
Fecha de cierre documental: 2026-08-27T08:50:00-0600 (CST)
Commit de cierre: `bb1a415ec4572a884dc60b63c0492d3bb91e9c94`

## 1. Identidad del repositorio y entorno

- Repositorio: `/Users/david/Desktop/MailApp 0.3.0 2`
- Rama: `main`
- HEAD inicial verificado: `8ab343440c2a36a81bbd053439aedfe8790f33e5`
- HEAD posterior al cierre: `bb1a415ec4572a884dc60b63c0492d3bb91e9c94`
- origin/main: `8ab343440c2a36a81bbd053439aedfe8790f33e5` (en sincronía)
- Usuario operativo: `david`
- Sistema: macOS 26.6.2 (Build 25G83); Darwin 25.6.0 x86_64 (iMac-de-David.local)
- Java: OpenJDK 17.0.20.1 (Temurin-17.0.20.1+1)
- Gradle wrapper: 9.6.1; Kotlin del proyecto: 2.1.20; AGP: 9.0.0
- Android: `minSdk 26`, `compileSdk 36`, `targetSdk 36`
- Git ejecutado con `core.fsmonitor=false`

## 2. Estado Git y divergencia respecto de origin/main

- `status --short --branch` inicial: `## main...origin/main` con tres archivos modificados (M).
- `status --short --branch` posterior al cierre: `## main...origin/main [ahead 1]` con los mismos tres archivos modificados (M).
- `branch --show-current`: `main`.
- `rev-parse HEAD` inicial: `8ab343440c2a36a81bbd053439aedfe8790f33e5`.
- `rev-parse HEAD` posterior al cierre: `bb1a415ec4572a884dc60b63c0492d3bb91e9c94`.
- `rev-parse origin/main`: `8ab343440c2a36a81bbd053439aedfe8790f33e5` → **sin divergencia**.
- `log -5 --oneline --decorate`:

  ```
  8ab3434 (HEAD -> main, origin/main, origin/HEAD) docs(emailbody): establish webview baseline
  2a433a6 docs(repository): close structural refactor
  8530fc6 refactor(repository): extract email resolution coordination
  0a3ecff refactor(repository): extract pdf download coordination
  b6058d3 refactor(repository): extract pdf cache validation
  ```

- `diff --stat`: 3 archivos, 13 inserciones, 4 eliminaciones.
- `diff --name-only`: únicamente los tres archivos ajenos protegidos.
- `ls-files --others --exclude-standard`: vacío (sin archivos sin seguimiento).

### Incidencia remota inicial

El plan técnico cerrado fijó «main está un commit delante de origin/main»; en el
momento de ejecución ambos apuntan a `8ab3434` porque el commit del baseline ya
fue empujado. No cambia HEAD ni rama; se registra y no bloquea la subfase.

### Estado remoto posterior al cierre

Después del commit `docs(emailbody): record structural refactor preflight`,
`main` queda un commit delante de `origin/main`:

```
bb1a415 (HEAD -> main) docs(emailbody): record structural refactor preflight
8ab3434 (origin/main, origin/HEAD) docs(emailbody): establish webview baseline
```

Esta divergencia es esperada y corresponde únicamente al commit documental de
la Subfase 1.1.

## 3. Cambios ajenos protegidos

| Archivo | SHA-256 del working tree | Hash del diff (SHA-256) | numstat (+/-) |
|---|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | `5c4c94a32cffbe928f7bed1e2f9dbeff3fc35319ca6afb555e44e4aeb34dfd53` | 4 / 2 |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | `8d7c88bbf84d56d2008d2d3611962bcbf3c97f4c2522e3d16fe72c2f0a81018e` | 6 / 2 |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | `d8e91f25968fd4a404bf40083c5b1d4f35ef4c8f4d1256b9d013a07333848820` | 3 / 0 |

Historial Git confirmado: ninguno pertenece a `feature/emaildetail`
(`ComposeScreen.kt` → commits de protección de Compose/navegación;
`MainNavHost.kt` → commits de navegación/ajustes; `gradle.properties` →
`Initial commit`). Son modificaciones locales sin commit sobre `main`.

Estos cambios no se editarán, formatearán, restaurarán, ocultarán ni incluirán
en commits. Quedan prohibidos expresamente: `git stash`, `git clean`,
`git checkout`, `git reset`, `git add .`, `git add -A` y `git commit -a`.
Antes y después de cada subfase futura se compararán sus tres hashes; una
discrepancia detiene la ejecución.

## 4. Alcance técnico congelado

### Firma pública de EmailBodyWebView (no cambia)

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

### Consumidores

- Producción: `EmailDetailContent.kt` (línea 134), composable `internal`.
- Pruebas: `EmailBodyWebViewBaselineTest.kt` (línea 901).
- Búsqueda en todo el repositorio (`*.kt`): no existe otro `EmailBodyWebView`
  ni otro consumidor.

### Símbolos privados actuales (no se añadirá API pública)

- `private data class PreparedDocument(val key: String, val html: String)`
- `private fun buildLoadKey(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb): String`
- `private fun buildHtml(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb): String`
- `private fun WebSettings.applyHardening(showImages: Boolean, isDark: Boolean)`
- `private class CustomTabsWebViewClient(ctx, traceMail, loadKey, onPageReady) : WebViewClient()`
- `private class TraceWebChromeClient(traceMail, loadKey) : WebChromeClient()`
- `private fun toCssRgb(argb: Int): String`

### Archivos de referencia

| Archivo | Líneas (`wc -l`) | SHA-256 |
|---|---|---|
| `EmailBodyWebView.kt` | 669 | `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1` |
| `EmailDetailContent.kt` | 181 | `1b48e82b9af0f1322a20253741eda167c7c867bd4fb4d65425a54c161fde1002` |
| `EmailHtmlCleaner.kt` | 224 | `db853aa50a6d152e4ad959fb7037d561c66aef4ee1ee93b4d488445c5fb947db` |
| `EmailBodyWebViewBaselineTest.kt` | 1734 | `d526fc1254964e565d6d84acc57bfdf6f1eeaf38189cca6baf40ebfba5bf4f9d` |

### Imports de EmailBodyWebView.kt (congelados)

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

Los imports de `EmailDetailContent.kt`, `EmailHtmlCleaner.kt` y
`EmailBodyWebViewBaselineTest.kt` quedan protegidos por sus SHA-256; ninguno
de los tres será tocado por el refactor.

### Dependencias relevantes

- Compose BOM `2026.06.01` (ui, material3, runtime).
- Lifecycle `2.9.0` (`lifecycle-runtime-compose`, `LifecycleEventObserver`).
- Chrome Custom Tabs `androidx.browser:1.8.0`.
- WebKit `androidx.webkit:1.11.0` (`WebSettingsCompat`, `WebViewFeature`).
- Jsoup `1.18.1` (`EmailHtmlCleaner`).
- Coroutines `1.9.0` (`Dispatchers.Default`).
- Kotlin stdlib 2.1.20; Java 17 (`jvmToolchain(17)`).

## 5. Inventario del baseline

Validado sin volver a ejecutar pruebas:

- `EmailBodyWebView.kt` coincide con el hash aprobado (`83cf07eb…`), 669 líneas.
- **22 métodos `@Test`** en `EmailBodyWebViewBaselineTest.kt` (confirmado por búsqueda).
- **32 capturas PNG** (`docs/verification/emailbody-webview-baseline/capturas/`):
  16 escenarios × emulador/físico.
- **32 trazas** `.log` (`docs/verification/emailbody-webview-baseline/trazas/`).
- **16 XML** parseables y con `failures=0`, `errors=0`, `skipped=0`
  (validación local con `xml.etree`).
- `resultados-subfase-4.2.md`: **COMPLETADA — GO** (paquete de handoff cerrado y
  apto para el Plan B; commit `8ab3434` creado).
- Referencia final registrada:
  - focal emulador: tres corridas de 22/22 (`reportes-subfase-3.2/focal-corrida-1/2/3.xml`);
  - suite completa: 306/306 (`reportes-subfase-3.2/completa.xml`);
  - Pixel 9: 22/22 (`reportes-subfase-4.1/fisico.xml`);
  - 34 contratos automatizados y 6 manuales, 0 no observables, todos aprobados.

### Disponibilidad de ADB

`adb` no está en el PATH del shell, pero existe en
`/Users/david/Library/Android/sdk/platform-tools/adb`; el daemon se inició y no
hay dispositivos conectados en el momento de esta subfase. Se registra como
restricción del entorno: **no es puerta de la Subfase 1.1**. Las etapas 6.2 y
6.3 sí exigirán dispositivos operativos.

## 6. Allowlist y denylist

### Allowlist global del refactor

- Archivos finales previstos por la arquitectura aprobada (7):

  ```
  EmailBodyWebView.kt                (fachada pública)
  EmailBodyDocument.kt               (documento preparado, clave, HTML, CSS, ARGB-RGB)
  EmailBodyDocumentPreparation.kt    (preparación asíncrona)
  EmailBodyWebViewRuntime.kt         (estado recordado, WeakReference, scroll, release, lifecycle)
  EmailBodyWebViewHost.kt            (AndroidView, factory, update, carga, long-press, liberación)
  EmailBodyWebViewSettings.kt        (hardening, red, imágenes, viewport, zoom, darkening)
  EmailBodyWebViewClients.kt         (Custom Tabs, callbacks visuales, progreso)
  ```

- Directorio de evidencia: `docs/verification/emailbody-webview-refactor/`.
- **Allowlist de 1.1:** únicamente
  `docs/verification/emailbody-webview-refactor/registro-tecnico.md`.

### Denylist

- `EmailHtmlCleaner.kt`, `EmailDetailContent.kt` (consumidor), Gradle,
  navegación, DI, modelos y baseline histórico
  (`docs/verification/emailbody-webview-baseline/`).
- Los tres archivos ajenos protegidos (`ComposeScreen.kt`, `MainNavHost.kt`,
  `gradle.properties`).
- Prohibido modificar pruebas para aceptar comportamientos diferentes.

### Documentos externos

El plan maestro y el plan técnico 1.1 ya existen en las notas privadas
(`/Users/david/Documents/Private/Proyecto MailApp/Refactor estructural de
EmailBodyWebView/`). No se duplican en otras rutas; quedan fuera del
repositorio y del staging. El único archivo admitido en el commit de 1.1 es
`registro-tecnico.md`.

## 7. Disciplina de agentes, staging y commits

- Un solo agente escritor a la vez.
- Toda intervención de Flash requiere revisión de Pro antes del commit.
- Staging exclusivamente mediante pathspecs explícitos; prohibido `git add .`
  y `git add -A`.
- Commit previsto de 1.1: `docs(emailbody): record structural refactor preflight`.
- No avanzar a 1.2 sin aprobación explícita y commit aislado.

## 8. Incidencias encontradas

1. **Remoto en paridad**: el plan fijó «main un commit delante de origin/main»;
   en ejecución ambos apuntan a `8ab3434` (el commit del baseline ya fue
   empujado). Sin impacto en HEAD, rama ni hashes.
2. **ADB fuera del PATH**: presente en el SDK; daemon iniciado; sin dispositivos
   conectados. Restricción del entorno, no puerta de 1.1.
3. **Conteo de líneas**: `wc -l` reporta 224 (`EmailHtmlCleaner.kt`) y 1734
   (`EmailBodyWebViewBaselineTest.kt`); el editor muestra una línea más por el
   salto final sin `\n`. Se usan los valores de `wc -l`.
4. **Documentos externos ya existentes**: el plan maestro y el plan técnico 1.1
   estaban cerrados en la carpeta privada del usuario; no se generan copias en
   las rutas provisionales de «Salidas autorizadas».

## 9. Resultado

**GO** — todas las comprobaciones de solo lectura pasan:

- HEAD inicial en `8ab343440c2a36a81bbd053439aedfe8790f33e5`, rama `main`.
- HEAD cerrado en `bb1a415ec4572a884dc60b63c0492d3bb91e9c94`.
- Los siete hashes iniciales permanecen idénticos (4 de referencia + 3 ajenos).
- Baseline íntegro y con decisión GO (`resultados-subfase-4.2.md`).
- Sin modificación de producción, pruebas ni configuración.
- Sin archivos inesperados en el working tree ni en staging.

La Subfase 1.1 queda cerrada en el commit aislado
`bb1a415ec4572a884dc60b63c0492d3bb91e9c94`
(`docs(emailbody): record structural refactor preflight`). El plan técnico
externo contiene el acta de cierre correspondiente.

## 10. Corrección documental menor posterior

Esta sección fue añadida después del cierre para eliminar ambigüedades
detectadas durante la verificación:

- Se registró explícitamente el SHA del commit de cierre.
- Se separó el HEAD inicial del HEAD posterior al cierre.
- Se documentó que `main` queda un commit delante de `origin/main` por el commit
  documental de la Subfase 1.1.
- Se confirmó la ruta externa real de los documentos privados:
  `/Users/david/Documents/Private/Proyecto MailApp/Refactor estructural de EmailBodyWebView/`.
- No se tocó código de producción, pruebas, configuración ni baseline histórico.

---

## 11. Subfase 1.2 — Arquitectura y propiedad del estado

Fecha de ejecución: 2026-08-27T10:58:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro (modo auditoría)
Naturaleza: solo documental (no modifica producción, pruebas, Gradle,
navegación, DI ni baseline histórico).

### 11.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `a21b11c964b8101a126b46786658c21307b3d49c` (coincide con el plan cerrado) |
| origin/main | `8ab343440c2a36a81bbd053439aedfe8790f33e5` |
| Divergencia | `main` adelante 2 commits (`bb1a415`, `a21b11c`), ambos documentales |
| Staging | vacío (`git diff --cached --name-only` sin salida) |
| Untracked | vacío (`git ls-files --others --exclude-standard` sin salida) |

### 11.2 Hashes de los tres archivos ajenos protegidos (SHA-256)

| Archivo | SHA-256 | ¿Coincide con 1.1? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

Los tres permanecen idénticos; no se editan, formatean, restauran ni incluyen
en commits.

### 11.3 Artefactos de la subfase

- Creado: `docs/verification/emailbody-webview-refactor/arquitectura-propiedad-estado.md`.
- Actualizado: `docs/verification/emailbody-webview-refactor/registro-tecnico.md`
  (esta entrada, sin alterar el baseline histórico de 1.1).
- El plan técnico externo `Subfase 1.2.md` ya existía en las notas privadas
  (`/Users/david/Documents/Private/Proyecto MailApp/Refactor estructural de
  EmailBodyWebView/`); no se duplica ni se versiona.

### 11.4 Arquitectura congelada

- Siete archivos objetivo: `EmailBodyWebView.kt` (fachada pública, 80–140
  líneas), `EmailBodyDocument.kt`, `EmailBodyDocumentPreparation.kt`,
  `EmailBodyWebViewRuntime.kt`, `EmailBodyWebViewHost.kt`,
  `EmailBodyWebViewSettings.kt`, `EmailBodyWebViewClients.kt`.
- Símbolos extraídos `internal`; sin API pública nueva.
- `preparedDocument` ligado a `currentKey`; runtime recordado sin claves.
- Claves Compose congeladas: `remember(currentKey)`, `LaunchedEffect(currentKey)`,
  `DisposableEffect(traceMail)`, `DisposableEffect(lifecycleOwner)`.
- Fórmula de `buildLoadKey` congelada (seis componentes, `body.hashCode()`).
- Órdenes de carga y release congelados (secciones 7 del documento de
  arquitectura).
- Prohibidas las mejoras incidentales (`rememberUpdatedState`, cambios de URL,
  CSS, dark mode, callbacks o pruebas).

### 11.5 Verificación

- `git diff --check`: sin salida (sin errores de whitespace).
- `git diff --cached --check`: sin salida.
- `git diff --cached --name-only`: solo los dos documentos de la subfase.
- `git status --short --branch`: `## main...origin/main [ahead 2]` con los tres
  archivos ajenos modificados (M), sin tocar.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados en 1.1.
- Revisión manual: ningún documento autoriza cambios de lógica.

### 11.6 Resultado

**GO** — la arquitectura queda congelada y apta para que la Subfase 1.3
construya la matriz de equivalencia sin tomar decisiones nuevas. Commit aislado
creado únicamente con los dos documentos permitidos
(`arquitectura-propiedad-estado.md` y `registro-tecnico.md`).

---

## 12. Subfase 1.3 — Matriz de equivalencia

Fecha de ejecución: 2026-08-27T10:59:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro (modo auditoría)
Naturaleza: solo documental (no mueve código ni cambia lógica).

### 12.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `1251f06e43e62a349979e0e9233c85ffbc29332a` (coincide con el plan cerrado) |
| origin/main | `8ab343440c2a36a81bbd053439aedfe8790f33e5` |
| Divergencia | `main` adelante 3 commits (`bb1a415`, `a21b11c`, `1251f06`), todos documentales |
| Staging | vacío |
| Untracked | vacío |

### 12.2 Hashes de los tres archivos ajenos protegidos (SHA-256)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 12.3 Artefactos de la subfase

- Creado: `docs/verification/emailbody-webview-refactor/matriz-equivalencia.md`.
- Actualizado: `docs/verification/emailbody-webview-refactor/registro-tecnico.md`
  (esta entrada, sin alterar el baseline histórico).
- El plan técnico externo `Subfase 1.3.md` ya existía en las notas privadas.

### 12.4 Matriz congelada

- **40 contratos** trazados a zonas (Z1–Z8) y subfases futuras:
  34 automatizados + 6 manuales, todos con equivalencia demostrable.
- **22 tests** mapeados a sus contratos (tabla de cobertura de la sección 4).
- 8 zonas con subfase responsable (sección 2): Z1→5.1, Z2→2.1/2.2/2.3,
  Z3→3.1, Z4→4.1/4.4, Z5→3.2/3.3, Z6→4.2/4.4, Z7→4.3, Z8→3.3+4.3.
- Reglas de equivalencia no negociables reafirmadas (sección 5).
- Defectos conocidos preservados: F02 overflow y S06 imagen remota sintética
  (sección 6); no se corrigen.

### 12.5 Verificación

- `git diff --check`: sin salida.
- `git diff --cached --check`: sin salida.
- `git diff --cached --name-only`: solo los dos documentos de la subfase.
- `git status --short --branch`: `## main...origin/main [ahead 3]` con los tres
  archivos ajenos modificados (M), sin tocar.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados en 1.1.
- Revisión manual: la matriz no autoriza cambios de lógica ni de tests para
  aceptar regresiones.

### 12.6 Resultado

**GO** — la matriz queda completa y apta para iniciar la Etapa 2 (primera
extracción real en 2.1). Ningún contrato queda sin equivalencia demostrable.
Commit aislado creado únicamente con los dos documentos permitidos
(`matriz-equivalencia.md` y `registro-tecnico.md`).

---

## 13. Subfase 2.1 — Modelo, clave y colores

Fecha de ejecución: 2026-08-27T19:04:00-0600 (CST)
Ejecutor: DeepSeek V4 Flash
Revisión: DeepSeek V4 Pro (obligatoria)
Naturaleza: primera extracción real del refactor.

### 13.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `2ea6b36c05a3904820225690b9d81dc2fcaf3a7b` (coincide con el plan cerrado) |
| origin/main | `8ab343440c2a36a81bbd053439aedfe8790f33e5` |
| Divergencia | `main` adelante 4 commits, todos documentales |
| Staging | vacío |

### 13.2 Hashes de los tres archivos ajenos protegidos (SHA-256)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 13.3 Cambios de implementación

- Creado `EmailBodyDocument.kt` en
  `com.david.mailapp.feature.emaildetail.components` con:
  - `internal data class PreparedDocument(val key, val html)`.
  - `internal fun buildLoadKey(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb)`.
  - `internal fun toCssRgb(argb): String`.
- Eliminados de `EmailBodyWebView.kt` los tres símbolos (`private` → `internal`)
  y los dos comentarios separadores que quedaron huérfanos (`Keys & Cache` y
  `Helpers`).
- `buildHtml` permanece en `EmailBodyWebView.kt` y sigue llamando a
  `toCssRgb(...)` extraído (mismo paquete).
- Fórmula de `buildLoadKey` intacta, incluyendo `onSurfaceArgb`.
- `currentKey` sin cambios: sigue calculándose en la fachada con
  `remember(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb)`.
- No se tocaron `buildHtml`, `EmailHtmlCleaner`, `EmailDetailContent`, la
  instrumentation baseline, Gradle, navegación ni DI.

### 13.4 Pruebas

- Creado `EmailBodyDocumentTest.kt` (JUnit 4 puro, 4 casos):
  - `buildLoadKey_preservesExactComponentOrder`.
  - `buildLoadKey_changesWhenEachComponentChanges`.
  - `toCssRgb_ignoresAlphaAndFormatsRgb`.
  - `preparedDocument_storesKeyAndHtml`.
- Comando: `./gradlew testDebugUnitTest --tests 'com.david.mailapp.feature.emaildetail.components.EmailBodyDocumentTest'`
  → **BUILD SUCCESSFUL, 4/4** (0 fallos, 0 errores, 0 omitidas; XML `tests="4"`).
- Comando: `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- No se ejecutó instrumentation; S10–S13 quedan como red de seguridad para
  etapas posteriores o auditoría (según plan).

### 13.5 Verificación

- `git diff --check`: sin salida.
- Diff de producción: solo elimina los tres símbolos movidos + dos comentarios
  huérfanos; ningún literal de `buildLoadKey`, `toCssRgb` ni `buildHtml` cambió.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.
- Revisión manual: sin cambios de comportamiento observable; sin wrappers,
  aliases, data classes adicionales ni nombres nuevos.

### 13.6 Resultado

**GO** — unit test nuevo 4/4, compilación debug verde, hashes protegidos
intactos, staging limpio tras el commit y ningún cambio de comportamiento
observable. Commit aislado creado con pathspecs explícitos de los archivos
permitidos. Siguiente subfase: 2.2 (construcción HTML).

---

## 14. Subfase 2.2 — Construcción HTML

Fecha de ejecución: 2026-08-27T22:15:00-0600 (CST)
Ejecutor: DeepSeek V4 Flash
Revisión: DeepSeek V4 Pro (obligatoria)
Naturaleza: extracción de `buildHtml`.

### 14.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `de48b270521144d7927bbda92c01aeac86c3904d` (coincide con el plan cerrado) |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | ninguna (a la par) |
| Staging | vacío |

### 14.2 Hashes de los tres archivos ajenos protegidos (SHA-256)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 14.3 Cambios de implementación

- Movido `buildHtml(...)` completo de `EmailBodyWebView.kt` a
  `EmailBodyDocument.kt`; visibilidad `private` → `internal`, nombre, firma,
  orden de parámetros y cuerpo intactos.
- Movido `import org.jsoup.Jsoup` a `EmailBodyDocument.kt`; `EmailBodyWebView.kt`
  ya no lo importa.
- `EmailBodyDocument.kt` ahora contiene `PreparedDocument`, `buildLoadKey`,
  `toCssRgb` y `buildHtml`.
- `EmailHtmlCleaner.isSimpleHtml(doc)` y `EmailHtmlCleaner.clean(doc)`
  permanecen exactamente en su posición del pipeline (mismo paquete).
- `EmailBodyWebView.kt` sigue llamando `buildHtml(...)` sin wrapper ni alias.
- Ningún literal HTML/CSS cambió: viewport, wrapper simple
  (`margin:0 16px; padding-top: 20px;`), `font-size: 15px`,
  `line-height: 1.5`, `padding: 8px 0`, `img, table { max-width: 100%; height: auto; }`,
  regla `img:not([src^="data:"]){display:none!important}` y colores fijos
  `rgb(224, 224, 224)` / `rgb(33, 33, 33)`.

### 14.4 Pruebas

- Ampliado `EmailBodyDocumentTest.kt` con 5 casos de `buildHtml`:
  - `buildHtml_simpleHtml_wrapsCleanBodyWithSimpleMargins`.
  - `buildHtml_newsletterDoesNotAddSimpleWrapper`.
  - `buildHtml_darkAndLightThemesPreserveTextAndColorScheme`.
  - `buildHtml_whenImagesBlocked_hidesRemoteButNotDataImages`.
  - `buildHtml_whenImagesEnabled_doesNotInjectRemoteHideRule`.
- Comando: `./gradlew testDebugUnitTest --tests 'com.david.mailapp.feature.emaildetail.components.EmailBodyDocumentTest'`
  → **BUILD SUCCESSFUL, 9/9** (4 previos + 5 nuevos; 0 fallos, 0 errores,
  0 omitidas; XML `tests="9"`).
- Comando: `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- No se ejecutó instrumentation; S04, S05, S07, S08, S12 y S13 quedan como red
  de seguridad visual/trace para etapas posteriores.

### 14.5 Verificación

- `git diff --check`: sin salida.
- Diff de producción: solo mueve `buildHtml` y el import `org.jsoup.Jsoup`.
- `EmailBodyWebView.kt` ya no importa `org.jsoup.Jsoup`.
- `EmailBodyDocument.kt` contiene los cuatro símbolos.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.

### 14.6 Resultado

**GO** — tests JVM 9/9, compilación debug verde, hashes protegidos intactos,
staging limpio tras el commit y ninguna diferencia funcional en la construcción
HTML. Commit aislado creado con pathspecs explícitos. Siguiente subfase: 2.3
(preparación asíncrona).

---

## 15. Subfase 2.3 — Preparación asíncrona

Fecha de ejecución: 2026-08-27T22:36:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro (modo auditoría)
Naturaleza: extracción de la preparación asíncrona (corrutinas, cancelación
Compose, estado ligado a clave y orden de trazas).

### 15.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `8e95c03d2166565e8f19fa8e5354a59925963056` (coincide con el plan cerrado) |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 1 por el commit de 2.2 |
| Staging | vacío |

### 15.2 Hashes de los tres archivos ajenos protegidos (SHA-256)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 15.3 Cambios de implementación

- Creado `EmailBodyDocumentPreparation.kt` en
  `com.david.mailapp.feature.emaildetail.components` con:
  `@Composable internal fun rememberPreparedEmailBodyDocument(body, currentKey,
  showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb, traceMail):
  PreparedDocument?`.
- La función conserva `remember(currentKey)`, `mutableStateOf<PreparedDocument?>(null)`,
  `LaunchedEffect(currentKey)`, `val sourceBody = body`, `val loadKey = currentKey`,
  `withContext(Dispatchers.Default)`, `PreparedDocument(loadKey, html)` y las
  cuatro trazas `HTML_BUILD_*` exactas.
- En `EmailBodyWebView.kt` el bloque fue reemplazado por la llamada con
  argumentos nombrados; `currentKey` sigue calculándose en la fachada.
- Movidos al nuevo archivo los imports `LaunchedEffect`, `Dispatchers` y
  `withContext`, que ya no se usan en `EmailBodyWebView.kt`.

### 15.4 Invariantes preservados

- `HTML_BUILD_WAITING reason=body_pending`, `HTML_BUILD_START`,
  `HTML_BUILD_END`, `HTML_BUILD_READY` sin cambios de nombre ni payload.
- Orden WAITING o START → END → asignación `preparedDocument` → READY.
- `buildHtml(...)` llamado con los mismos parámetros y orden.
- `preparedDocument` se reinicia a `null` al cambiar `currentKey`.
- `body == null || currentKey == null` retorna sin construir HTML.
- La cancelación natural de `LaunchedEffect(currentKey)` sigue siendo la única
  defensa contra resultados obsoletos (sin `rememberUpdatedState` ni guards nuevos).

### 15.5 Pruebas

- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- `./gradlew testDebugUnitTest --tests 'com.david.mailapp.feature.emaildetail.components.EmailBodyDocumentTest'`
  → **BUILD SUCCESSFUL** (unit test existente intacto).
- Instrumentación focal (emulador `Medium_Phone_API_36.1`, API 36, serial
  `emulator-5554`):
  ```
  ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
    -Pandroid.testInstrumentationRunnerArguments.class=...EmailBodyWebViewBaselineTest#bodyPending_keepsWebViewMounted_andLoadsWhenBodyArrives,...EmailBodyWebViewBaselineTest#replacedDocument_doesNotDispatchStaleCallback
  ```
  → **BUILD SUCCESSFUL, 2/2** (0 fallos, 0 errores, 0 omitidas;
  XML `tests="2"`).

### 15.6 Verificación

- `git diff --check`: sin salida.
- Diff de producción: solo extrae la preparación asíncrona y mueve los tres
  imports; `EmailBodyWebView.kt` conserva `currentKey` en la fachada.
- El nuevo archivo contiene las trazas `HTML_BUILD_*` sin cambios.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.

### 15.7 Resultado

**GO** — compilación verde, unit test existente verde, pruebas focales
instrumentadas de body pending y stale callback verdes (2/2), staging limpio
tras commit, hashes protegidos intactos y sin cambios de comportamiento
observable. Commit aislado creado con pathspecs explícitos. Siguiente subfase:
3.1 (configuración WebSettings).
