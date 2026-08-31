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

---

## 16. Subfase 3.1 — Configuración WebView

Fecha de ejecución: 2026-08-27T23:30:00-0600 (CST)
Ejecutor: DeepSeek V4 Flash
Revisión: DeepSeek V4 Pro (obligatoria)
Naturaleza: extracción de `WebSettings.applyHardening`.

### 16.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `58bda1d6cbf7fca5dc7c2b411bd33a6756d9153f` (coincide con el plan cerrado) |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 2 (commits de 2.2 y 2.3) |
| Staging | vacío |

### 16.2 Hashes de los tres archivos ajenos protegidos (SHA-256)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 16.3 Cambios de implementación

- Creado `EmailBodyWebViewSettings.kt` en
  `com.david.mailapp.feature.emaildetail.components` con
  `internal fun WebSettings.applyHardening(showImages, isDark)`.
- Conservados nombre, firma, orden de parámetros, comentario interno y cuerpo;
  únicamente se normalizó el whitespace final de una línea en blanco (no
  funcional) para mantener `git diff --check` limpio.
- Movidos al nuevo archivo los imports `android.webkit.WebSettings`,
  `androidx.webkit.WebSettingsCompat` y `androidx.webkit.WebViewFeature`.
- En `EmailBodyWebView.kt` se retiraron esos tres imports y se convirtieron a
  texto literal con backticks las tres referencias KDoc que ya solo quedaban en
  la documentación (`WebSettings.blockNetworkLoads`,
  `WebSettings.javaScriptEnabled`, `WebSettingsCompat.setAlgorithmicDarkeningAllowed`).
- Las dos llamadas `applyHardening(...)` (factory y update) permanecen sin cambios.
- Ningún valor de settings cambió (C15–C31 de la matriz).

### 16.4 Pruebas

- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- Instrumentación focal (emulador `Medium_Phone_API_36.1`, API 36):
  ```
  ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
    -Pandroid.testInstrumentationRunnerArguments.class=...hardeningViewportAndZoomSettings_matchBaseline,...networkBlocking_followsShowImagesAcrossRecomposition,...algorithmicDarkening_followsIsDark
  ```
  → **BUILD SUCCESSFUL, 3/3** (0 fallos, 0 errores, 0 omitidas;
  XML `tests="3"`).

### 16.5 Verificación

- `git diff --check`: sin salida.
- Diff de producción: solo mueve `applyHardening`, sus imports y las referencias
  KDoc asociadas; ningún valor de settings cambió.
- `EmailBodyWebViewSettings.kt` contiene la extensión `internal`.
- `EmailBodyWebView.kt` sigue llamando `applyHardening` en factory y update.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.

### 16.6 Resultado

**GO** — compilación verde, pruebas focales instrumentadas 3/3 verdes, hashes
protegidos intactos, staging limpio tras commit y ningún cambio observable en
hardening/settings. Commit aislado creado con pathspecs explícitos. Siguiente
subfase: 3.2 (cliente de progreso).

---

## 17. Subfase 3.2 — Cliente de progreso

Fecha de ejecución: 2026-08-29T17:21:00-0600 (CST)
Ejecutor: DeepSeek V4 Flash
Revisión: DeepSeek V4 Pro (obligatoria)
Naturaleza: extracción de `TraceWebChromeClient`.

### 17.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `9eb65c2193eb97998e5b46c93a38d6554340c29f` |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 3 |
| Staging | vacío |

> **Nota de HEAD:** el plan fijó `9eb65c2cc068d80f196e6a7d969a7d6b7e6c2ee3`; el
> HEAD real es `9eb65c2193eb97998e5b46c93a38d6554340c29f`. Coinciden en el
> prefijo corto `9eb65c2`; la diferencia corresponde a la predicción del hash en
> el plan cerrado, no a un commit distinto. Se documenta y no bloquea.

### 17.2 Hashes de los tres archivos ajenos protegidos (SHA-256)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 17.3 Cambios de implementación

- Creado `EmailBodyWebViewClients.kt` en
  `com.david.mailapp.feature.emaildetail.components` con
  `internal class TraceWebChromeClient(traceMail, loadKey)`.
- Conservados nombre, constructor, `lastMilestone = -1`, override
  `onProgressChanged`, llamada `super`, cálculo de milestone y traza
  `WV_PROGRESS` exacta.
- Movidos al nuevo archivo los imports `android.webkit.WebChromeClient`,
  `android.webkit.WebView` y `com.david.mailapp.feature.emaildetail.EmailRenderTrace`.
- Retirado `import android.webkit.WebChromeClient` de `EmailBodyWebView.kt`
  (ya no se usa).
- `CustomTabsWebViewClient` permanece en `EmailBodyWebView.kt` (se moverá en 3.3).
- La llamada `webView.webChromeClient = TraceWebChromeClient(traceMail, document.key)`
  permanece intacta.
- Sin throttling, deduplicación adicional ni eventos nuevos.

### 17.4 Pruebas

- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- Instrumentación focal (emulador `Medium_Phone_API_36.1`, API 36):
  ```
  ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
    -Pandroid.testInstrumentationRunnerArguments.class=...s02_simpleLight_initial,...s16_release_andReopen_createsNewInstance
  ```
  → **BUILD SUCCESSFUL, 2/2** (0 fallos, 0 errores, 0 omitidas; XML `tests="2"`).

### 17.5 Validación manual de trazas WV_PROGRESS

Confirmado en logcat y en la evidencia publicada (`/data/local/tmp/emailbody-3.2/`):

- `WV_PROGRESS` aparece con payload `loadKey=`, `progress=` y `milestone=`.
- Aparecen al menos `milestone=0` (progress 10) y `milestone=100` (progress 100).
- S02: hitos observados 0 y 100.
- S16: primera carga emite hitos 0/50/75/100; tras `WV_RELEASE` y reapertura con
  instancia nueva (`WV_FACTORY instance=23b4609`), el progreso vuelve a emitir
  para la nueva carga (0 y 100).

### 17.6 Verificación

- `git diff --check`: sin salida.
- Diff de producción: solo mueve `TraceWebChromeClient` y sus imports.
- `CustomTabsWebViewClient` sigue en `EmailBodyWebView.kt`.
- Ningún milestone ni payload de `WV_PROGRESS` cambió.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.

### 17.7 Resultado

**GO** — compilación verde, pruebas focales instrumentadas 2/2 verdes, trazas
`WV_PROGRESS` preservadas (milestones y payloads), hashes protegidos intactos,
staging limpio tras commit y sin cambios observables en navegación, visual
callbacks o lifecycle. Commit aislado creado con pathspecs explícitos. Siguiente
subfase: 3.3 (cliente de navegación y página lista).

---

## 18. Subfase 3.3 — Cliente de navegación y página lista

Fecha de ejecución: 2026-08-30T16:16:00-0600 (CST)
Ejecutor: DeepSeek V4 Flash
Revisión: DeepSeek V4 Pro (obligatoria)
Naturaleza: extracción de `CustomTabsWebViewClient`.

### 18.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `3252921dd9b87d869eea897c9e9f823a71f81c34` (commit de 3.2) |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 4 |
| Staging | vacío |

### 18.2 Hashes de los tres archivos ajenos protegidos (SHA-256)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 18.3 Cambios de implementación

- Movido `CustomTabsWebViewClient` a `EmailBodyWebViewClients.kt`, junto a
  `TraceWebChromeClient`; visibilidad `private` → `internal`.
- Conservados `onPageStarted`, `onPageCommitVisible`, `onPageFinished`,
  `postVisualStateCallback`, ambos overloads de `shouldOverrideUrlLoading`,
  retorno `true`, apertura con `CustomTabsIntent`, captura de `Exception`,
  mensajes `Log.w` y callback `onPageReady`.
- Añadidos en `EmailBodyWebViewClients.kt` los imports: `android.content.Context`,
  `android.graphics.Bitmap`, `android.net.Uri`, `android.util.Log`,
  `android.webkit.WebResourceRequest`, `android.webkit.WebView`,
  `android.webkit.WebViewClient`, `androidx.browser.customtabs.CustomTabsIntent`.
  Las referencias calificadas `android.content.Context` y `android.net.Uri`
  quedaron como `Context` y `Uri`.
- Retirados de `EmailBodyWebView.kt` los imports `Bitmap`, `Log`,
  `WebResourceRequest`, `WebViewClient` y `CustomTabsIntent`.
- La referencia KDoc `[WebViewClient.shouldOverrideUrlLoading]` se convirtió a
  texto literal con backticks (precedente de 3.1) por quedar huérfana.
- La llamada `CustomTabsWebViewClient(context, traceMail, document.key) { ... }`
  permanece intacta; la lógica del lambda `onPageReady` no se extrajo.
- Normalizada una línea en blanco final al mover el bloque (para mantener
  `git diff --check` limpio).

### 18.4 Pruebas

- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- Instrumentación focal (emulador `Medium_Phone_API_36.1`, API 36):
  ```
  ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
    -Pandroid.testInstrumentationRunnerArguments.class=...s02_simpleLight_initial,...s16_release_andReopen_createsNewInstance
  ```
  → **BUILD SUCCESSFUL, 2/2** (0 fallos, 0 errores, 0 omitidas; XML `tests="2"`).

### 18.5 Verificación estática

- `CustomTabsWebViewClient` ya no existe en `EmailBodyWebView.kt`.
- `CustomTabsWebViewClient` existe en `EmailBodyWebViewClients.kt` como `internal`.
- `WV_PAGE_STARTED`, `WV_COMMIT_VISIBLE`, `WV_PAGE_FINISHED`,
  `WV_VISUAL_REQUESTED` y `WV_VISUAL_CALLBACK` se conservan textualmente.
- Ambos overloads de `shouldOverrideUrlLoading` siguen presentes.
- `git diff --check`: sin salida (tras normalizar el blank line final).
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.

### 18.6 Resultado

**GO** — compilación verde, pruebas focales instrumentadas 2/2 verdes, sin
cambios funcionales, hashes protegidos intactos y staging limpio tras el commit.
Commit aislado creado con pathspecs explícitos. Siguiente subfase: 4.1 (estado
runtime).

---

## 19. Subfase 4.1 — Estado runtime

Fecha de ejecución: 2026-08-30T16:39:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro
Naturaleza: extracción estructural del estado runtime recordado.

### 19.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `1ea56ec...` (commit de 3.3) |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 5 |
| Staging | vacío |

### 19.2 Hashes de los tres archivos ajenos protegidos (SHA-256)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 19.3 Cambios de implementación

- Creado `EmailBodyWebViewRuntime.kt` en
  `com.david.mailapp.feature.emaildetail.components` con:
  - `internal class EmailBodyWebViewRuntimeState` con exactamente los siete
    estados (mismos tipos Compose): `lastLoaded`, `activeLoadKey`,
    `loggedSkippedKey`, `loggedWaitingState`, `savedScrollY` (`MutableIntState`),
    `webViewRef` (`MutableState<WeakReference<WebView>?>`), `released`.
  - `@Composable internal fun rememberEmailBodyWebViewRuntimeState()` que
    recuerda el contenedor con `remember { }` (sin claves).
- En `EmailBodyWebView.kt` las siete variables sueltas fueron reemplazadas por
  `val runtimeState = rememberEmailBodyWebViewRuntimeState()` y todas las
  lecturas/escrituras pasan por `runtimeState.<campo>.value/.intValue`,
  preservando el orden lógico (load: lastLoaded → activeLoadKey → reset logs →
  released=false → webViewRef → configuración → carga; release: scroll →
  released=true → activeLoadKey=null → webViewRef=null → stop → destroy).
- Retirados los imports `getValue`, `setValue`, `mutableStateOf` y
  `mutableIntStateOf` de `EmailBodyWebView.kt` (quedaron sin uso).
- No se extrajo lifecycle, AndroidView factory/update/release, long-press,
  clientes ni restauración de scroll (corresponden a 4.2–4.4).
- Payloads de trazas `WV_*` conservados textualmente (claves `activeLoadKey=`,
  `previousKey=`, `loadKey=`, `scrollY=`, `released=` intactas).

### 19.4 Pruebas

- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- Instrumentación focal (emulador `Medium_Phone_API_36.1`, API 36):
  ```
  ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
    -Pandroid.testInstrumentationRunnerArguments.class=...s02_simpleLight_initial,...s16_release_andReopen_createsNewInstance
  ```
  → **BUILD SUCCESSFUL, 2/2** (0 fallos, 0 errores, 0 omitidas; XML `tests="2"`).

### 19.5 Verificación estática

- `EmailBodyWebView.kt` ya no declara los siete estados runtime como variables
  sueltas.
- El contenedor mantiene los mismos tipos Compose (`MutableState`/`MutableIntState`).
- `WebView` sigue guardado como `WeakReference`.
- Todas las trazas `WV_*` se conservan textualmente.
- `git diff --check`: sin salida.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.

### 19.6 Resultado

**Subfase 4.1 CERRADA — GO.** Compilación verde, pruebas focales instrumentadas
2/2 verdes, extracción estructural sin cambios funcionales, hashes protegidos
intactos y staging limpio tras el commit. Commit aislado creado con pathspecs
explícitos. Siguiente subfase: 4.2 (lifecycle y restauración de scroll).

---

## 20. Subfase 4.2 — Lifecycle y restauración de scroll

Fecha de ejecución: 2026-08-30T18:38:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro
Naturaleza: extracción estructural del binding de lifecycle.

### 20.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `c6acc6f5f9d5deb482f489edda135ced1fb3e483` (commit de 4.1) |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 6 |
| Staging | vacío |

### 20.2 Hashes de los tres archivos ajenos protegidos (SHA-256)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 20.3 Cambios de implementación

- Creado `EmailBodyWebViewLifecycle.kt` en
  `com.david.mailapp.feature.emaildetail.components` con
  `@Composable internal fun BindEmailBodyWebViewLifecycle(lifecycleOwner, runtimeState, traceMail)`.
- Movido íntegro el `DisposableEffect(lifecycleOwner)` a la función, con la
  clave `DisposableEffect(lifecycleOwner)` intacta.
- Conservados: `ON_PAUSE` (leer WebView débil → guardar scrollY → `WV_ON_PAUSE` →
  `onPause()`), `ON_RESUME` (`WV_ON_RESUME` → `onResume()` →
  `WV_RESUME_VISUAL_SKIPPED` si no hay clave activa → `postVisualStateCallback`),
  las dos protecciones `released || activeLoadKey != resumeLoadKey` (en
  `onComplete` y en `webView.post`), `scrollTo` + `invalidate`, registro/retiro
  del `LifecycleEventObserver` y `WV_LIFECYCLE_OBSERVER_DISPOSE`.
- `EmailBodyWebView.kt` ahora invoca `BindEmailBodyWebViewLifecycle(...)` tras
  crear `runtimeState`.
- Retirados de `EmailBodyWebView.kt` los imports `Lifecycle` y
  `LifecycleEventObserver`; la referencia KDoc `[LifecycleEventObserver]` se
  convirtió a texto literal con backticks.
- No se movieron factory, attach/detach, long-press, update, carga ni release
  (pertenecen a 4.3/4.4).

### 20.4 Pruebas

- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- Instrumentación focal (emulador `Medium_Phone_API_36.1`, API 36):
  ```
  ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
    -Pandroid.testInstrumentationRunnerArguments.class=...s14_longNewsletter_scrollAndLifecycle_restoresScrollWithoutReload,...s09_externalLink_opensCustomTab_andDetailSurvives
  ```
  - Corrida 1: **1/2** — `s14` pasó; `s09` falló con
    `NoMatchingViewException: No views in hierarchy found matching WebView`
    (actividad con contenido vacío `child-count=0` tras el round-trip de Custom
    Tab). Diagnóstico: **flakiness de infraestructura** de la Custom Tab, no
    regresión del refactor (el binding de lifecycle es byte a byte idéntico al
    original, solo relocalizado).
  - Re-ejecución `s09` en solitario: **1/1** verde.
  - Corrida combinada final: **2/2** verdes (0 fallos, 0 errores, 0 omitidas;
    XML `tests="2"`).
- El único `LifecycleEventObserver` del código queda en
  `EmailBodyWebViewLifecycle.kt` (la mención en `EmailBodyWebView.kt` es solo
  KDoc).

### 20.5 Verificación

- `git diff --check`: sin salida.
- Sin cambios en nombres/orden/payload de `WV_ON_PAUSE`, `WV_ON_RESUME`,
  `WV_RESUME_VISUAL_*` y `WV_LIFECYCLE_OBSERVER_DISPOSE`.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.

### 20.6 Resultado

**Subfase 4.2 CERRADA — GO.** Compilación verde, pruebas focales instrumentadas
2/2 verdes (s14 + s09 en corrida combinada final), sin cambios funcionales,
hashes protegidos intactos y staging limpio tras el commit. Commit aislado
creado con pathspecs explícitos. Siguiente subfase: 4.3 (factory, attach y
long-press).

---

## 21. Subfase 4.3 — Factory, attach y long-press

Fecha de ejecución: 2026-08-30T20:03:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro
Naturaleza: extracción estructural de la creación inicial del WebView.

### 21.1 Corrección documental de 4.2

El plan exigía corregir en el registro de 4.2 el SHA mal transcrito de
`gradle.properties` (truncado a 61 caracteres). Corregido al valor completo:
`3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476`
(verificado con `shasum -a 256` en esta subfase).

### 21.2 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `e86385c986061d6757cdb6fd9d37ac56a3044b20` (commit de 4.2) |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 7 |
| Staging | vacío |

### 21.3 Hashes de los tres archivos ajenos protegidos (SHA-256, regenerados)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 21.4 Cambios de implementación

- Creado `EmailBodyWebViewHost.kt` en
  `com.david.mailapp.feature.emaildetail.components` con
  `internal fun EmailBodyWebViewHost(context, runtimeState, traceMail, currentKey, surfaceArgb, showImages, isDark, onImageLongPress): WebView`.
- Movida íntegra la creación del WebView desde `AndroidView.factory`:
  - `instance` + traza `WV_FACTORY`;
  - `MATCH_PARENT`, scrollbars desactivadas, `setBackgroundColor(surfaceArgb)` y
    `applyHardening(showImages, isDark)`;
  - listener attach/detach con payloads literales de `WV_ATTACHED`/`WV_DETACHED`;
  - long-press: solo `IMAGE_TYPE`/`SRC_IMAGE_ANCHOR_TYPE`, rechaza URL nula/vacía,
    entrega `hitTestResult.extra` y retorna `true` solo al despachar;
  - `runtimeState.webViewRef.value = WeakReference(...)`.
- `EmailBodyWebView.kt` sustituyó el cuerpo del factory por la llamada a
  `EmailBodyWebViewHost(...)`; `AndroidView`, `update`, `onRelease`, lifecycle y
  `Box` no se movieron.
- Retirados de `EmailBodyWebView.kt` los imports `android.view.View`,
  `android.view.ViewGroup` y `android.webkit.WebView` (quedaron sin uso).

### 21.5 Pruebas

- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- Instrumentación focal (emulador `Medium_Phone_API_36.1`, API 36):
  ```
  ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
    -Pandroid.testInstrumentationRunnerArguments.class=...s01_bodyNullToSimple_light_loadsAndTraces,...s15_longPress_onDataImage_deliversExactDataUrl,...s16_release_andReopen_createsNewInstance
  ```
  → **BUILD SUCCESSFUL, 3/3** (0 fallos, 0 errores, 0 omitidas; XML `tests="3"`).

### 21.6 Verificación estática

- `WebView(...)`, `setOnLongClickListener` y `addOnAttachStateChangeListener`
  solo aparecen en `EmailBodyWebViewHost.kt`.
- `EmailBodyWebView.kt` conserva el mismo `AndroidView` y no altera `update` ni
  `onRelease`.
- Las trazas `WV_FACTORY`, `WV_ATTACHED` y `WV_DETACHED` mantienen nombres,
  orden y payloads.
- `git diff --check`: sin salida.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.

### 21.7 Resultado

**Subfase 4.3 CERRADA — GO.** Compilación verde, corrida focal 3/3 verde (S01,
S15, S16), sin cambios funcionales, hashes protegidos intactos y staging limpio
tras el commit. Commit aislado creado con pathspecs explícitos. Siguiente
subfase: 4.4 (update, carga y release).

---

## 22. Subfase 4.4 — Update, carga y release

Fecha de ejecución: 2026-08-30T21:43:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro
Naturaleza: extracción estructural de `AndroidView.update` y `onRelease`.

### 22.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `8936637eb4077b0fd2747afc0614d966b9fb7b1b` (commit de 4.3) |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 8 |
| Staging | vacío |

### 22.2 Hashes de los tres archivos ajenos protegidos (SHA-256, regenerados)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 22.3 Cambios de implementación

- Creado `EmailBodyWebViewUpdate.kt` en
  `com.david.mailapp.feature.emaildetail.components` con:
  - `internal fun updateEmailBodyWebView(webView, document, currentKey, context, surfaceArgb, showImages, isDark, runtimeState, traceMail, onPageRendered)`.
  - `internal fun releaseEmailBodyWebView(webView, runtimeState, traceMail)`.
- Movida íntegra la lógica de `update`: ramas wait (body_pending /
  html_pending:$currentKey con deduplicación por `loggedWaitingState`), load
  (orden lastLoaded → activeLoadKey → reset logs → released=false →
  WeakReference → fondo/settings → clientes → `loadDataWithBaseURL`) y skip
  (deduplicación por `loggedSkippedKey`).
- Conservados los guardas stale (antes del `post` y dentro del `webView.post`),
  trazas `WV_UPDATE`, `WV_LOAD_DATA`, `WV_SCROLL_RESTORE_*`,
  `WV_PAGE_RENDERED_*` y `loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)`.
- Movido íntegro el release con su orden: traza → guardar scroll → released=true
  → activeLoadKey=null → referencia nula → stopLoading() → destroy().
- `EmailBodyWebView.kt` ahora delega `update` y `onRelease` a los helpers; no se
  movieron `AndroidView`, factory/host, lifecycle, `Box` ni preparación async.
- Retirado de `EmailBodyWebView.kt` el import `java.lang.ref.WeakReference`
  (quedó sin uso). La única referencia restante de carga en ese archivo es el
  KDoc `[loadDataWithBaseURL]` (comentario).

### 22.4 Pruebas — EmailBodyWebViewBaselineTest completo, 3 corridas consecutivas

Comando (emulador `Medium_Phone_API_36.1`, API 36):
```
env ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.emaildetail.components.EmailBodyWebViewBaselineTest
```

| Corrida | Resultado | XML |
|---|---|---|
| 1 | BUILD SUCCESSFUL — **22/22** | `tests="22" failures="0" errors="0" skipped="0"` |
| 2 | BUILD SUCCESSFUL — **22/22** | `tests="22" failures="0" errors="0" skipped="0"` |
| 3 | BUILD SUCCESSFUL — **22/22** | `tests="22" failures="0" errors="0" skipped="0"` |

- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- Focales validados presentes en las corridas (cada uno 1/1 por corrida): T1
  `bodyPending_keepsWebViewMounted_andLoadsWhenBodyArrives`, T6
  `replacedDocument_doesNotDispatchStaleCallback`, S01, S09, S10, S11, S12,
  S13, S14 y S16 — cubren wait/load/skip, callbacks stale, cambios de
  body/tema/imágenes, lifecycle y release/reapertura.

### 22.5 Verificación

- `EmailBodyWebView.kt` ya no contiene lógica wait/load/skip/release (solo el
  KDoc `[loadDataWithBaseURL]`).
- Los helpers contienen una única implementación de cada rama sin cambiar
  literales de carga ni trazas.
- `git diff --check`: sin salida.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.

### 22.6 Resultado

**Subfase 4.4 CERRADA — GO.** Compilación verde, **tres corridas consecutivas
22/22** de la suite completa focal, sin cambios funcionales, hashes protegidos
intactos y staging limpio tras el commit. Commit aislado creado con pathspecs
explícitos. Etapa 4 completada; siguiente subfase: 5.1 (fachada pública).

---

## 23. Subfase 5.1 — Fachada pública

Fecha de ejecución: 2026-08-30T23:47:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro
Naturaleza: conversión de `EmailBodyWebView` en fachada pública de 80–140 líneas
con montaje delegado a un host composable.

### 23.1 Preflight

El acta externa de 4.4 fue completada con la evidencia de la sección 22 antes
del cierre documental de 5.1. La documentación externa y este registro quedan
alineados; no hay una acción pendiente atribuida al usuario.

### 23.2 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `89f27d17f01bb5758c2eb29a448bd899234bd37d` (commit de 4.4) |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 9 |
| Staging | vacío |

### 23.3 Hashes de los tres archivos ajenos protegidos (SHA-256, regenerados)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 23.4 Cambios de implementación

- `EmailBodyWebViewHost.kt`:
  - El factory se renombró a `internal fun createEmailBodyWebViewHost(...)`.
  - Se creó `@Composable internal fun EmailBodyWebViewHost(context, currentKey, preparedDocument, surfaceArgb, showImages, isDark, runtimeState, traceMail, onPageRendered, onImageLongPress, modifier)` que contiene el `Box(modifier)` y el `AndroidView` completo: factory → `createEmailBodyWebViewHost`, update → `updateEmailBodyWebView`, onRelease → `releaseEmailBodyWebView`, y `Modifier.fillMaxSize()`.
- `EmailBodyWebView.kt` (fachada pública, **121 líneas**, dentro de 80–140):
  - Conserva firma, defaults, KDoc y `@Composable`.
  - Obtiene `context`, `lifecycleOwner`, `MaterialTheme` y los tres ARGB.
  - `remember(body, showImages, isDark, surfaceArgb, onSurfaceArgb, primaryArgb)` para `currentKey`.
  - `rememberPreparedEmailBodyDocument(...)`, `rememberEmailBodyWebViewRuntimeState()`.
  - `DisposableEffect(traceMail)` exactamente antes de `BindEmailBodyWebViewLifecycle`.
  - Delega el montaje a `EmailBodyWebViewHost(...)`.
- Retirados de la fachada los imports `Box`, `fillMaxSize` y `AndroidView`.

### 23.5 Verificación estática

- `EmailBodyWebView` es la única declaración pública del paquete; todos los
  demás símbolos extraídos son `internal`/`private`.
- `EmailDetailContent.kt` conserva su hash baseline
  (`1b48e82b9af0f1322a20253741eda167c7c867bd4fb4d65425a54c161fde1002`).
- El host conserva un único `Box`, un único `AndroidView`, factory/update/onRelease
  y los mismos modificadores.
- `git diff --check`: sin salida.

### 23.6 Pruebas

- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- Instrumentación focal (emulador `Medium_Phone_API_36.1`, API 36):
  ```
  ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
    -Pandroid.testInstrumentationRunnerArguments.class=...s01,...s10,...s11,...s12,...s13,...s14,...s16
  ```
  → **BUILD SUCCESSFUL, 7/7** (0 fallos, 0 errores, 0 omitidas; XML `tests="7"`).

### 23.7 Resultado

**Subfase 5.1 CERRADA — GO.** Fachada pública de 121 líneas (rango 80–140),
compilación verde, pruebas focales 7/7 verdes en una corrida, sin cambios de
firma ni comportamiento, hashes protegidos intactos y staging limpio tras el
commit. Commit aislado creado con pathspecs explícitos. Siguiente subfase: 5.2
(consolidación estructural).

---

## 24. Subfase 5.2 — Consolidación estructural

Fecha de ejecución: 2026-08-31T00:10:00-0600 (CST)
Ejecutor: DeepSeek V4 Pro
Revisión: DeepSeek V4 Pro
Naturaleza: auditoría y limpieza estructural sin cambios de comportamiento.
Commit: `7a4087263a7d7b4ad02c533e65d97f6f7dd3cd19`

### 24.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `f0fb89a...` (commit de 5.1) |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 10 |
| Staging | vacío |

### 24.2 Hashes de los tres archivos ajenos protegidos (SHA-256, regenerados)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 24.3 Inventario de símbolos, referencias y visibilidad

| Archivo | Símbolos | Visibilidad |
|---|---|---|
| `EmailBodyWebView.kt` | `EmailBodyWebView(...)` | **pública** (única) |
| `EmailBodyDocument.kt` | `PreparedDocument`, `buildLoadKey`, `toCssRgb`, `buildHtml` | internal |
| `EmailBodyDocumentPreparation.kt` | `rememberPreparedEmailBodyDocument` | internal |
| `EmailBodyWebViewRuntime.kt` | `EmailBodyWebViewRuntimeState`, `rememberEmailBodyWebViewRuntimeState` | internal |
| `EmailBodyWebViewLifecycle.kt` | `BindEmailBodyWebViewLifecycle` | internal |
| `EmailBodyWebViewHost.kt` | `EmailBodyWebViewHost` (composable), `createEmailBodyWebViewHost` | internal |
| `EmailBodyWebViewSettings.kt` | `WebSettings.applyHardening` | internal |
| `EmailBodyWebViewClients.kt` | `CustomTabsWebViewClient`, `TraceWebChromeClient` | internal |
| `EmailBodyWebViewUpdate.kt` | `updateEmailBodyWebView`, `releaseEmailBodyWebView` | internal |

- **16 símbolos** declarados una sola vez cada uno; sin helpers duplicados ni
  antiguos.
- Una sola declaración pública: `EmailBodyWebView`.

### 24.4 Limpieza aplicada

- Imports sin uso: **ninguno** (todos los imports de los nueve archivos están
  referenciados; el `getValue`/`setValue` de `EmailBodyDocumentPreparation.kt`
  se usa vía delegado `by`).
- Comentarios separadores obsoletos: **ninguno** (no quedan separadores `──` en
  los archivos extraídos; los huérfanos se eliminaron en las subfases 2.1–3.3).
- **Resultado: auditoría sin diff funcional** — no se modificó producción (válido
  según plan).

### 24.5 Equivalencia confirmada contra la matriz (1.3)

- Claves Compose y orden de efectos sin cambios.
- Fórmula de `buildLoadKey` literal
  (`${body.hashCode()}_...` con `onSurfaceArgb`).
- `loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)` intacto.
- Literales HTML: wrapper `margin:0 16px; padding-top: 20px`, regla
  `img:not([src^="data:"]){display:none!important}`, colores fijos
  `rgb(224, 224, 224)` / `rgb(33, 33, 33)`, `LOAD_NO_CACHE`.
- Trazas `HTML_BUILD_*`, `WV_*` y comportamiento de callbacks sin cambios.
- `EmailDetailContent.kt` conserva su hash baseline
  (`1b48e82b9af0f1322a20253741eda167c7c867bd4fb4d65425a54c161fde1002`).

### 24.6 Pruebas

- `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.
- `./gradlew testDebugUnitTest` → **BUILD SUCCESSFUL, 593/593** (0 fallos, 0
  errores, 0 omitidas).
- Instrumentación (emulador `Medium_Phone_API_36.1`, API 36):
  ```
  env ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
    -Pandroid.testInstrumentationRunnerArguments.class=...EmailBodyWebViewBaselineTest
  ```
  - Corrida 1: 21/22 — `s09` falló con la **flakiness de infraestructura** de la
    Custom Tab ya documentada en 4.2 (`NoMatchingViewException`). No es
    regresión (sin diff de producción en esta subfase).
  - Corrida 2: **22/22** verde (0 fallos, 0 errores, 0 omitidas; XML `tests="22"`).

### 24.7 Verificación

- Búsqueda estática: sin helpers antiguos/duplicados; una sola declaración
  pública de `EmailBodyWebView`; referencias únicas a factory/update/release.
- `git diff --check`: sin salida.
- SHA-256 de los tres archivos ajenos: idénticos a los registrados.
- Working tree: solo los tres archivos ajenos protegidos modificados (cambios
  previos del usuario, intactos).

### 24.8 Resultado

**Subfase 5.2 CERRADA — GO.** Auditoría estructural completa: una única API
pública, 16 símbolos con implementación única, sin imports/comentarios
obsoletos que eliminar, equivalencia confirmada contra la matriz, compilación y
JVM verdes, y corrida focal 22/22 verde (tras la flakiness S09 transitoria).
Commit aislado documental creado con pathspecs explícitos. Siguiente subfase:
6.1 (JVM, build y lint).

---

## 25. Subfase 6.1 — JVM, build y lint

Fecha de ejecución: 2026-08-31T00:15:00-0600 (CST)
Ejecutor: DeepSeek V4 Flash
Revisión y decisión final: DeepSeek V4 Pro
Naturaleza: verificación local no instrumentada; sin modificar producción.
Commit: `68e5a775a7ae8406639146798b7e96baa8a67a03`

### 25.1 Estado inicial verificado

| Campo | Valor |
|---|---|
| Rama | `main` |
| HEAD | `c620ccfa2ef1bab23154f6882d3c5e796f66a5f0` |
| origin/main | `de48b270521144d7927bbda92c01aeac86c3904d` |
| Divergencia | `main` adelante 13 |
| Staging | vacío |
| Working tree | solo los tres archivos ajenos protegidos (M) |

### 25.2 Comandos y resultados

| # | Comando | Resultado | Duración |
|---|---|---|---|
| 1 | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL | 2s |
| 2 | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL | 1s |
| 3 | `./gradlew compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL | 1s |
| 4 | `./gradlew assembleDebug` | BUILD SUCCESSFUL | 1s |
| 5 | `./gradlew assembleRelease` | BUILD SUCCESSFUL | 1m 35s |
| 6 | `./gradlew lintDebug` | BUILD SUCCESSFUL | 1m 42s |
| 7 | `git diff --check` | Sin salida (limpio) | — |

- Tests JVM: **593/593** (0 fallos, 0 errores, 0 omitidas).

### 25.3 Artefactos (no versionados)

| Artefacto | Tamaño | SHA-256 |
|---|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 25 642 958 bytes | `d87eccaeb1b236d9cd6d7251a494bbb9f3d08014e0f126173dcb383f95f54e9b` |
| `app/build/outputs/apk/release/app-release.apk` | 4 952 662 bytes | `e32532aca0ee28b46a35ee8be98c799de699e7a24510198bb2dfa5e8f85b8743` |

### 25.4 Lint (`app/build/reports/lint-results-debug.xml`)

- **0 errores, 66 warnings**, 0 fatales, 0 information.

| ID | Cantidad | Severidad | Clasificación |
|---|---|---|---|
| GradleDependency | 17 | Warning | Preexistente/externo |
| NewerVersionAvailable | 15 | Warning | Preexistente/externo |
| ModifierParameter | 15 | Warning | Preexistente (1 de ellos en `EmailBodyWebView.kt` = firma pública congelada) |
| FrequentlyChangingValue | 6 | Warning | Preexistente |
| UnusedResources | 3 | Warning | Preexistente |
| UseKtx | 3 | Warning | Preexistente (1 de ellos en `EmailBodyWebViewClients.kt` = `Uri.parse` movido verbatim del baseline) |
| AndroidGradlePluginVersion | 2 | Warning | Preexistente/externo |
| OldTargetApi | 1 | Warning | Preexistente |
| ConfigurationScreenWidthHeight | 1 | Warning | Preexistente |
| ObsoleteSdkInt | 1 | Warning | Preexistente |
| UseOfNonLambdaOffsetOverload | 1 | Warning | Preexistente |
| IconLocation | 1 | Warning | Preexistente |
| **Total** | **66** | | **0 atribuibles al refactor** |

Los dos hallazgos que apuntan a archivos `EmailBody*` corresponden a patrones
preexistentes: la firma congelada `modifier: Modifier = Modifier` como último
parámetro (contrato inmodificable) y `Uri.parse` en `CustomTabsWebViewClient`
(movido verbatim). No hay hallazgos nuevos introducidos por la lógica extraída.

### 25.5 Hashes de los tres archivos ajenos protegidos (regenerados)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 25.6 Resultado

**GO** — los seis comandos Gradle terminan correctamente, tests JVM sin fallos
(593/593), lint sin errores y sin hallazgos atribuibles al refactor (66 warnings
preexistentes/externos), `git diff --check` limpio y hashes protegidos intactos.
Commit documental aislado creado con pathspecs explícitos. Siguiente subfase:
6.2 (baseline focal en emulador).

---

## 26. Subfase 6.2 — Baseline focal en emulador

Fecha de ejecución: 2026-08-31T01:20:00-0600 (CST)
Ejecutor: DeepSeek V4 Flash
Evaluación y diagnóstico: DeepSeek V4 Pro
Naturaleza: revalidación del comportamiento completo de `EmailBodyWebView` en
emulador (sin cambios de producción).

### 26.1 Entorno

| Campo | Valor |
|---|---|
| AVD | `Medium_Phone_API_36.1` |
| Serial | `emulator-5554` |
| API | 36 |
| Estado | `device`, `sys.boot_completed=1` |
| Evidencia persistente | `/data/local/tmp/emailbody-3.2/` (emulador) |

### 26.2 Tres corridas consecutivas

Comando (idéntico en las tres):
```
env ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.emaildetail.components.EmailBodyWebViewBaselineTest
```

| Corrida | Resultado | Duración | XML |
|---|---|---|---|
| 1 | BUILD SUCCESSFUL — **22/22** | 6m 58s | `tests="22" failures="0" errors="0" skipped="0"` |
| 2 | BUILD SUCCESSFUL — **22/22** | 4m 45s | `tests="22" failures="0" errors="0" skipped="0"` |
| 3 | BUILD SUCCESSFUL — **22/22** | 4m 8s | `tests="22" failures="0" errors="0" skipped="0"` |

Sin intentos fallidos: las tres corridas limpias son consecutivas.

### 26.3 Criterios de equivalencia validados

- **body pending**: S01 — `WV_UPDATE action=wait reason=body_pending` y
  `HTML_BUILD_WAITING` antes de la llegada del cuerpo; luego
  `HTML_BUILD_START/END/READY`, `action=load` y `WV_LOAD_DATA` con payloads
  correctos (`loadKey`, `bodyLen`, `htmlLen`, `durationMs`).
- **HTML/carga y settings**: T1/T2/S01–S08 con capturas y trazas publicadas.
- **Recomposición equivalente**: S10 — un único `action=skip reason=already_loaded`,
  misma instancia y cliente.
- **Cambios body/tema/política**: S11/S12/S13 — dos claves/cargas por cambio.
- **Custom Tab**: S09 — sin `WV_RELEASE`, un dispatch, `WV_ON_RESUME` al volver.
- **Callbacks stale**: T6/S16 — `WV_PAGE_RENDERED_IGNORED` ante claves obsoletas.
- **Lifecycle/scroll**: S14 — `WV_ON_PAUSE scrollY=1000`, `WV_ON_RESUME
  savedScrollY=1000`, `WV_RESUME_VISUAL_REQUESTED/CALLBACK` y
  `WV_RESUME_SCROLL_APPLIED scrollY=1000`.
- **Long-press**: S15 — entrega exacta de la URL `data:`.
- **Release/reapertura**: S16 — 2 `WV_FACTORY` (instancias distintas), 1
  `WV_RELEASE` entre dos dispatches.
- **Progreso**: `WV_PROGRESS` presente (milestones 0/100) en las cargas.
- **Trazas UI_***: pertenecen a `EmailDetailContent` (fuera de alcance, hash
  baseline intacto `1b48e82b...`); no se producen en el baseline focal, que
  monta el componente aislado.
- **Defectos conocidos preservados (no corregidos)**: overflow de F02 y ausencia
  de carga de la imagen remota sintética de S06.

### 26.4 Evidencia producida

- 16 logs `.log` y capturas PNG de S01–S16 (32 archivos) en
  `/data/local/tmp/emailbody-3.2/`, frescos de las corridas (reloj del emulador
  verificado).
- XML de cada corrida conservados fuera del repo (`/tmp/emulator-62/corrida-N.xml`).

### 26.5 Hashes de los tres archivos ajenos protegidos (regenerados)

| Archivo | SHA-256 | ¿Coincide? |
|---|---|---|
| `ComposeScreen.kt` | `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69` | Sí |
| `MainNavHost.kt` | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | Sí |
| `gradle.properties` | `3339808f9445e215b61f0e7a61ccaacc00f97ffc6e7ff0c6581ec0b79c55d476` | Sí |

### 26.6 Verificación

- `git diff --check`: sin salida.
- Staging vacío; working tree con solo los tres archivos ajenos protegidos (M).
- Sin modificación de código, tests, baseline histórico, Gradle ni snapshots.

### 26.7 Resultado

**GO** — tres corridas consecutivas 22/22 en `Medium_Phone_API_36.1` (API 36) con
nombres, orden y payload de `HTML_BUILD_*` y `WV_*` equivalentes al baseline;
sin regresiones; defectos conocidos preservados. Commit documental aislado
creado con pathspecs explícitos. Siguiente subfase: 6.3 (suite completa y
Pixel 9).
