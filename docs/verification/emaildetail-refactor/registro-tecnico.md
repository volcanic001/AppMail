# Registro técnico — Refactor conservador de EmailDetailScreen

Registro acumulativo de las cinco etapas del Plan maestro 1. Este archivo es el
único artefacto permitido en la Subfase 1.1.

---

## 1. Único artefacto permitido

En esta subfase solo se crea este archivo:

```
docs/verification/emaildetail-refactor/registro-tecnico.md
```

No se crea ni modifica ningún otro archivo. Las carpetas de capturas se
crearán únicamente en las subfases 1.3 y 1.4, cuando existan evidencias reales.

---

## 2. Encabezado de trazabilidad

| Campo                    | Valor                                                            |
| ------------------------ | ---------------------------------------------------------------- |
| Plan maestro             | Plan maestro 1 — Refactor conservador de EmailDetailScreen       |
| Ruta del plan            | `/Users/david/Documents/Private Notes/Private/Proyecto MailApp/refactor/Etapa 1 Baseline y protección 4 subfases/Plan maestro 1.md` |
| Fecha del registro       | 2026-08-04                                                       |
| Hora y zona horaria      | 20:38 CST (UTC-06:00)                                            |
| Directorio del proyecto  | `/Users/david/Desktop/Copia de MailApp 0.3.0 2`                  |
| Rama                     | `main...origin/main` (a la par)                                  |
| HEAD completo            | `e9696620073575c1ade1d4ad21048cc1f355703c`                       |
| Commit corto             | `e969662`                                                        |
| Fecha del commit         | `2026-08-04T10:22:40-06:00`                                      |
| Asunto del commit        | `fix: apply email divider toggle to search results screen`       |
| Relación con origin/main | `0 adelante / 0 atrás` (`rev-list --left-right --count` = `0  0`) |

> **Advertencia:** el commit de la Etapa 1 **no** se hará hasta cerrar la
> subfase 1.4. Durante 1.1 no se crea ningún commit.

### Estado de subfases y etapas

| Subfase/Etapa | Descripción                        | Estado     |
| ------------- | ---------------------------------- | ---------- |
| Etapa 1       | Baseline y protección              | Aprobada   |
| Subfase 1.1   | Congelar alcance y estado inicial  | Aprobada   |
| Subfase 1.2   | Baseline JVM, compilación y análisis | Aprobada |
| Subfase 1.3   | Baseline instrumentado en emulador | Aprobada |
| Subfase 1.4   | Baseline manual y visual en dispositivo físico | Aprobada |
| Etapa 2       | Extraer código independiente       | Aprobada   |
| Etapa 3       | Extraer bloques visuales           | Aprobada   |
| Etapa 4       | Separar Route y UI                 | Aprobada   |
| Subfase 4.1   | Fachada pública y Route interna    | Aprobada   |
| Subfase 4.2   | Contrato de presentación y efectos PDF | Aprobada   |
| Subfase 4.3   | Pruebas de caracterización de presentación | Aprobada   |
| Etapa 5       | Cierre integral                    | Pendiente  |

Estados posibles: `Pendiente`, `En curso`, `Aprobada`, `Bloqueada`.

---

## 3. Congelación del estado previo

Resultados literales de los comandos ejecutados el 2026-08-04.

### `git status --short --branch`

```
## main...origin/main
 M app/src/main/java/com/david/mailapp/MainActivity.kt
 M app/src/main/java/com/david/mailapp/feature/search/SearchScreen.kt
```

### `git log -1 --format='%H%n%h%n%cI%n%s'`

```
e9696620073575c1ade1d4ad21048cc1f355703c
e969662
2026-08-04T10:22:40-06:00
fix: apply email divider toggle to search results screen
```

### `git rev-list --left-right --count HEAD...origin/main`

```
0       0
```

### `git diff --name-status`

```
M       app/src/main/java/com/david/mailapp/MainActivity.kt
M       app/src/main/java/com/david/mailapp/feature/search/SearchScreen.kt
```

### `git diff --stat`

```
 .../main/java/com/david/mailapp/MainActivity.kt | 16 ++++++++--------
 .../mailapp/feature/search/SearchScreen.kt      |  8 ++++----
 2 files changed, 12 insertions(+), 12 deletions(-)
```

### `git diff --check`

Sin salida (sin errores de whitespace).

### `git ls-files --others --exclude-standard`

Sin salida (no hay archivos sin seguimiento).

### Diff completo previo de MainActivity.kt y SearchScreen.kt

```diff
diff --git a/app/src/main/java/com/david/mailapp/MainActivity.kt b/app/src/main/java/com/david/mailapp/MainActivity.kt
index 729bb31..6c0c842 100644
--- a/app/src/main/java/com/david/mailapp/MainActivity.kt
+++ b/app/src/main/java/com/david/mailapp/MainActivity.kt
@@ -10,7 +10,7 @@ import androidx.activity.compose.setContent
 import androidx.activity.enableEdgeToEdge
 import androidx.compose.foundation.isSystemInDarkTheme
 import androidx.compose.runtime.LaunchedEffect
-import androidx.compose.runtime.collectAsState
+import androidx.lifecycle.compose.collectAsStateWithLifecycle
 import androidx.compose.runtime.getValue
 import androidx.compose.runtime.mutableStateOf
 import androidx.compose.runtime.remember
@@ -82,13 +82,13 @@ class MainActivity : ComponentActivity() {
         setContent {
             // ... (existing Compose content)
             val systemDark = isSystemInDarkTheme()
-            val savedPalette by AppContainer.appSettingsManager.paletteFlow.collectAsState(initial = null)
-            val savedDarkMode by AppContainer.appSettingsManager.isDarkModeFlow.collectAsState(initial = null)
-            val savedUseCustomFont by AppContainer.appSettingsManager.useCustomFontFlow.collectAsState(initial = null)
-            val savedIsAmoled by AppContainer.appSettingsManager.isAmoledFlow.collectAsState(initial = null)
-            val savedShowEmailDividers by AppContainer.appSettingsManager.showEmailDividersFlow.collectAsState(initial = null)
-            val isSignedIn by isSignedInFlow.collectAsState()
-            val oauthUiState by oauthUiStateFlow.collectAsState()
+            val savedPalette by AppContainer.appSettingsManager.paletteFlow.collectAsStateWithLifecycle(initialValue = null)
+            val savedDarkMode by AppContainer.appSettingsManager.isDarkModeFlow.collectAsStateWithLifecycle(initialValue = null)
+            val savedUseCustomFont by AppContainer.appSettingsManager.useCustomFontFlow.collectAsStateWithLifecycle(initialValue = null)
+            val savedIsAmoled by AppContainer.appSettingsManager.isAmoledFlow.collectAsStateWithLifecycle(initialValue = null)
+            val savedShowEmailDividers by AppContainer.appSettingsManager.showEmailDividersFlow.collectAsStateWithLifecycle(initialValue = null)
+            val isSignedIn by isSignedInFlow.collectAsStateWithLifecycle()
+            val oauthUiState by oauthUiStateFlow.collectAsStateWithLifecycle()
             var isSigningOut by remember { mutableStateOf(false) }

             val scope = androidx.compose.runtime.rememberCoroutineScope()
diff --git a/app/src/main/java/com/david/mailapp/feature/search/SearchScreen.kt b/app/src/main/java/com/david/mailapp/feature/search/SearchScreen.kt
index 97219aa..6b26952 100644
--- a/app/src/main/java/com/david/mailapp/feature/search/SearchScreen.kt
+++ b/app/src/main/java/com/david/mailapp/feature/search/SearchScreen.kt
@@ -21,7 +21,7 @@ import com.david.mailapp.ui.components.ContainedLoadingIndicator
 import androidx.compose.material3.Scaffold
 import androidx.compose.runtime.Composable
 import androidx.compose.runtime.LaunchedEffect
-import androidx.compose.runtime.collectAsState
+import androidx.lifecycle.compose.collectAsStateWithLifecycle
 import androidx.compose.runtime.getValue
 import androidx.compose.runtime.snapshotFlow
 import androidx.compose.ui.Alignment
@@ -59,9 +59,9 @@ fun SearchScreen(
         )
     )

-    val query by viewModel.query.collectAsState()
-    val uiState by viewModel.uiState.collectAsState()
-    val history by viewModel.historyFlow.collectAsState(initial = emptyList())
+    val query by viewModel.query.collectAsStateWithLifecycle()
+    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
+    val history by viewModel.historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())

     val focusManager = LocalFocusManager.current
     val keyboardController = LocalSoftwareKeyboardController.current
```

---

## 4. Protección de cambios ajenos

Fingerprints SHA-256 verificados al congelar el estado (2026-08-04):

| Elemento protegido                | SHA-256 inicial                                                    |
| --------------------------------- | ------------------------------------------------------------------ |
| MainActivity.kt                   | `a8275404afe60158d08616487124020b64d6aa1df2cb0f02f4c56c1d3b52cd55` |
| SearchScreen.kt                   | `3966a9feace5bbae418969414e7a543c26ee4909a0914ddc75eee450401d89b3` |
| Diff conjunto de ambos archivos   | `84adbdbeee0dd263c5b3ab94b56b996dd5adf51c08a2f60c6e2f2f7bbb9c224e` |

Los tres coinciden exactamente con los valores del plan cerrado.

**Regla de protección:**

- No hacer stash, revert, checkout, formateo ni edición sobre esos archivos.
- Comprobar sus tres hashes al final de cada subfase.
- Una variación bloquea el avance hasta determinar su origen.
- Estos archivos seguirán apareciendo como modificados durante todo el
  refactor; no deben interpretarse como cambios del Plan maestro 1.
- No se incluirán en ninguno de los cinco commits del plan.

---

## 5. Línea base de EmailDetailScreen

| Campo         | Valor                                                                 |
| ------------- | --------------------------------------------------------------------- |
| Ruta          | `app/src/main/java/com/david/mailapp/feature/emaildetail/EmailDetailScreen.kt` |
| Líneas        | 1213                                                                  |
| SHA-256       | `52c903597d800c94cbc009b5b0785a4fc2a68c4ab1c525bb9add754524019f10`    |

### Firma pública textual

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailScreen(
    emailId: String,
    onBack: () -> Unit,
    onReply: (String) -> Unit = {},
    onForward: (String) -> Unit = {},
    modifier: Modifier = Modifier
)
```

### Funciones y símbolos contenidos (2026-08-04)

| Línea | Símbolo                                                             |
| ----- | ------------------------------------------------------------------- |
| 125   | `private const val TAG = "EmailDetailScreen"`                       |
| 129   | `fun EmailDetailScreen(...)` — entrada pública                      |
| 601   | `private fun FloatingHeaderPanel(...)`                              |
| 751   | `private fun HeaderDetailRow(...)`                                  |
| 804   | `private fun EmailDetailContent(...)`                               |
| 953   | `private fun EmailDetailLoading(modifier)`                          |
| 970   | `private fun rememberDateFormat(): SimpleDateFormat`                |
| 983   | `private suspend fun handlePdfExternalActionRequest(...)`           |
| 1011  | `private suspend fun openPdfIntent(...)`                            |
| 1052  | `internal fun sanitizeDisplayName(name, defaultName): String`       |
| 1065  | `internal fun buildPdfSuggestedName(displayName, defaultName): String` |
| 1075  | `internal fun copyFileToUri(...)`                                   |
| 1093  | `internal fun copyFileToStream(...)`                                |
| 1118  | `val MaterialSymbolsReply: ImageVector`                             |
| 1151  | `private var _MaterialSymbolsReply: ImageVector?`                   |
| 1153  | `val TablerArrowForwardUpDouble: ImageVector`                       |
| 1201  | `private var _TablerArrowForwardUpDouble: ImageVector?`             |
| 1205  | `internal data class PdfActionLabels(...)`                          |

Además, la composable principal renderiza inline los estados Loading,
ResolutionError, BodyError y Ready (líneas ~292–470), y utiliza
`EmailDetailUiState`, `EmailRenderTrace`, `ImageSaveLabels` y `PdfActionLabels`.

### Responsabilidades congeladas

| Área       | Responsabilidad actual                                              |
| ---------- | ------------------------------------------------------------------- |
| Entrada    | Creación de source/ViewModel y acceso a AppContainer                |
| Lifecycle  | Colección de UI/PDF y efecto de error de lectura                    |
| PDF        | Eventos, caché, apertura externa, SAF, copiado y nombres            |
| Pantalla   | Top bar y renderizado de Loading/ResolutionError/BodyError/Ready    |
| Encabezado | Panel flotante, scrim, animaciones y prioridad de Back              |
| Contenido  | WebView, loader, adjuntos PDF y trazas de layout                    |
| Imágenes   | Menú, pantalla completa y guardado                                  |
| Soporte    | Formato de fecha, etiquetas PDF e iconos vectoriales                |

Esta tabla describe el punto de partida; no propone todavía nuevas APIs.

---

## 6. Matriz cerrada de alcance

### Permitido en 1.1

- Solamente el nuevo registro técnico.

### Permitido en etapas posteriores

- Extracciones mecánicas originadas en EmailDetailScreen.kt.
- Nuevos archivos cohesivos dentro de feature/emaildetail.
- Pruebas de caracterización expresamente previstas en la Etapa 4.
- Actualizaciones al registro técnico y capturas baseline/finales.

### Prohibido

- EmailRepository, AppContainer y EmailDetailViewModel.
- Navegación, rutas y MainActivity.
- EmailBodyWebView, PdfAttachmentSection e ImageUtils.
- HTML, JavaScript, modelos y recursos.
- Dependencias, Gradle, SDK, manifest y configuración.
- Correcciones, optimizaciones o limpiezas incidentales.
- SearchScreen.kt.
- Cualquier archivo no vinculado expresamente al Plan maestro 1.

---

## 7. Índice de evidencia para todo el plan

Tablas preparadas; no se anotará como aprobada ninguna prueba o captura
todavía no ejecutada.

### Comandos ejecutados

| Fecha | Subfase | Comando exacto | Resultado | Duración |
| ----- | ------- | -------------- | --------- | -------- |
| 2026-08-04 | 1.1 (congelación) | `git -c core.fsmonitor=false status --short --branch` | `## main...origin/main` + 2 archivos modificados | — |
| 2026-08-04 | 1.1 (congelación) | `git -c core.fsmonitor=false log -1 --format='%H%n%h%n%cI%n%s'` | HEAD `e969662` registrado | — |
| 2026-08-04 | 1.1 (congelación) | `git -c core.fsmonitor=false rev-list --left-right --count HEAD...origin/main` | `0  0` | — |
| 2026-08-04 | 1.1 (congelación) | `git -c core.fsmonitor=false diff --name-status` | 2 archivos modificados | — |
| 2026-08-04 | 1.1 (congelación) | `git -c core.fsmonitor=false diff --stat` | 12 inserciones / 12 eliminaciones | — |
| 2026-08-04 | 1.1 (congelación) | `git -c core.fsmonitor=false diff --check` | Sin errores | — |
| 2026-08-04 | 1.1 (congelación) | `git -c core.fsmonitor=false ls-files --others --exclude-standard` | Vacío | — |
| 2026-08-04 | 1.1 (verificación) | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL | 12s |
| 2026-08-04 | 1.1 (verificación) | `./gradlew testDebugUnitTest --tests 'com.david.mailapp.feature.emaildetail.EmailDetailContractsTest'` | BUILD SUCCESSFUL | 4s |
| 2026-08-04 | 1.1 (verificación) | `git -c core.fsmonitor=false status --short` | 2 protegidos + registro nuevo | — |
| 2026-08-04 | 1.1 (verificación) | `git -c core.fsmonitor=false diff --check` | Sin errores | — |
| 2026-08-04 | 1.1 (verificación) | `shasum -a 256` de los tres archivos | Coincide con baseline | — |
| 2026-08-04 | 1.1 (verificación) | `git diff ... \| shasum -a 256` (diff conjunto) | Coincide con el plan | — |
| 2026-08-04 | 1.2 (baseline) | `./gradlew testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL (573 pruebas) | 50s |
| 2026-08-04 | 1.2 (baseline) | `./gradlew assembleDebug --rerun-tasks` | BUILD SUCCESSFUL (APK generado) | 28s |
| 2026-08-04 | 1.2 (baseline) | `./gradlew lintDebug --rerun-tasks` | BUILD SUCCESSFUL (0 errores / 64 warnings) | 1m 11s |
| 2026-08-05 | 1.3 (arranque) | `emulator @Medium_Phone_API_36.1 -port 5554 -no-snapshot-load -no-boot-anim -no-audio` | AVD arrancado, boot en ~20s | — |
| 2026-08-05 | 1.3 (limpieza) | `adb -s emulator-5554 shell am force-stop com.david.mailapp` | OK | — |
| 2026-08-05 | 1.3 (limpieza) | `adb -s emulator-5554 shell pm clear com.david.mailapp` / `.test` | `Failed` (no instalado en AVD fresco) | — |
| 2026-08-05 | 1.3 (inicial) | `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks -P...class=<5 clases>` | 54 pruebas, 1 fallo (`BUILD FAILED`) | 1m 51s |
| 2026-08-05 | 1.3 (re-ejecución 1) | ídem con solo `EmailDetailCancellationTest` | 7 pruebas, 1 fallo (mismo contrato) | 1m 8s |
| 2026-08-05 | 1.3 (re-ejecución 2) | ídem | 7 pruebas, 1 fallo (mismo contrato) | 1m 8s |
| 2026-08-05 | 1.3 (corrección autorizada) | `EmailDetailCancellationTest`: espera `fetchBodyCalls >= 1` | Condición de sincronización corregida; producción intacta | — |
| 2026-08-05 | 1.3 (gate de clase) | ídem con solo `EmailDetailCancellationTest` | BUILD SUCCESSFUL; 7/7 | 1m 5s |
| 2026-08-05 | 1.3 (gate final) | ídem con las 5 clases dirigidas | BUILD SUCCESSFUL; 54/54 | 1m 31s |

### Pruebas

| Clase/suite | Dispositivo | Cantidad | Resultado |
| ----------- | ----------- | -------- | --------- |
| `EmailDetailContractsTest` | JVM (host) | 5 | Pasó (0 fallos, 0 errores) |
| Suite JVM completa (`testDebugUnitTest --rerun-tasks`) | JVM (host) | 573 | Pasó (0 fallos, 0 errores, 0 omitidas) |
| Instrumentada dirigida (5 clases, ejecución inicial) | emulator-5554 (`Medium_Phone_API_36.1(AVD) - 16`) | 54 | 53 aprobadas, **1 fallo** (`EmailDetailCancellationTest.bodyCancellationKeepsPreparingStateAndDoesNotWriteRoom`) |
| `EmailDetailCancellationTest` re-ejecución 1 | emulator-5554 | 7 | 6 aprobadas, **1 fallo** (mismo contrato) |
| `EmailDetailCancellationTest` re-ejecución 2 | emulator-5554 | 7 | 6 aprobadas, **1 fallo** (mismo contrato) |
| `EmailDetailCancellationTest` tras corrección de sincronización | emulator-5554 | 7 | **7 aprobadas**, 0 fallos, 0 errores, 0 omitidas |
| Instrumentada dirigida (5 clases, gate final) | emulator-5554 (`Medium_Phone_API_36.1(AVD) - 16`) | 54 | **54 aprobadas**, 0 fallos, 0 errores, 0 omitidas |

### Lint

| Tipo | Cantidad | Clasificación |
| ---- | -------- | ------------- |
| Errores previos | 0 | Ninguno |
| Errores nuevos | 0 | Ninguno |
| Warnings (baseline preexistente) | 64 | GradleDependency 16, NewerVersionAvailable 15, ModifierParameter 15, FrequentlyChangingValue 6, UseKtx 3, UnusedResources 3, UseOfNonLambdaOffsetOverload 1, OldTargetApi 1, ObsoleteSdkInt 1, IconLocation 1, ConfigurationScreenWidthHeight 1, AndroidGradlePluginVersion 1 |

### Capturas

| ID | Escenario | Tema | Dispositivo | Ruta |
| -- | --------- | ---- | ----------- | ---- |
| — | No aplican en 1.1, 1.2 ni 1.3 | — | — | Corresponden a 1.4 |

### Limitaciones o regresiones preexistentes

| ID | Descripción | Estado |
| -- | ----------- | ------ |
| R1 | `EmailDetailCancellationTest.bodyCancellationKeepsPreparingStateAndDoesNotWriteRoom` — timeout reproducible 3/3 porque la espera exigía `fetchBodyCalls == 1` y el contador podía saltar de 0 a 2 antes del sondeo. Producción intacta; la doble llamada queda caracterizada como comportamiento preexistente. | **Harness corregido por excepción autorizada; gate 54/54 verde** |

### Auditoría y commit de cada etapa

| Etapa | Auditoría | Commit | Estado |
| ----- | --------- | ------ | ------ |
| 1 | JVM 573/573; build; lint 0 errores/64 warnings; instrumentación 54/54; matriz manual 8/8 | HEAD de Etapa 1 — `docs(emaildetail): establish conservative refactor baseline` (hash consultable con `git log`) | Aprobada |

---

## 8. Verificación técnica de cierre de 1.1

La regla maestra exige compilación y prueba específica incluso para una
modificación documental. Ejecutado el 2026-08-04:

| Comando | Resultado |
| ------- | --------- |
| `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL in 12s (exit 0) |
| `./gradlew testDebugUnitTest --tests 'com.david.mailapp.feature.emaildetail.EmailDetailContractsTest'` | BUILD SUCCESSFUL in 4s (exit 0) |
| `git status --short` | Solo `MainActivity.kt` y `SearchScreen.kt` (protegidos) + `?? docs/verification/emaildetail-refactor/` |
| `git diff --check` | Sin salida (sin errores) |
| `shasum -a 256` de los tres archivos | Los tres coinciden con el baseline de la sección 4/5 |
| Hash del diff protegido | `84adbdbeee0dd263c5b3ab94b56b996dd5adf51c08a2f60c6e2f2f7bbb9c224e` (coincide) |

---

## 9. Gate de aceptación

La subfase 1.1 queda aprobada solamente si:

- [x] El único cambio nuevo es el registro técnico.
- [x] El registro diferencia estado previo, ámbito permitido y ámbito prohibido.
- [x] El diff previo de los dos archivos del usuario está reproducido y protegido.
- [x] EmailDetailScreen.kt permanece byte a byte intacto.
- [x] Compilación y EmailDetailContractsTest pasan.
- [x] `diff --check` no informa errores nuevos.
- [x] No se crea ningún commit.
- [x] Las subfases 1.2, 1.3 y 1.4 permanecen marcadas como pendientes.

**Cierre 1.1: todos los criterios cumplidos el 2026-08-04. Subfase 1.1 aprobada.**
**Siguiente paso: subfase 1.2 (baseline JVM, compilación y análisis).**

---

## 10. Subfase 1.2 — Baseline JVM, compilación y análisis

### 10.1 Entorno registrado (2026-08-04)

| Componente   | Valor                                   |
| ------------ | --------------------------------------- |
| macOS        | 26.5.2 (Build 25F84)                    |
| Java         | OpenJDK 25.0.2 Temurin (LTS)            |
| Gradle       | 9.6.1 (wrapper)                         |
| AGP          | 9.0.0                                   |
| Kotlin       | 2.1.20                                  |
| KSP          | 2.1.20-1.0.31                           |
| compileSdk   | 36                                      |
| minSdk       | 26                                      |
| targetSdk    | 36                                      |

**Advertencias conocidas (consignadas, no corregidas ni silenciadas):**

- `android.builtInKotlin=false` está deprecado; el default actual es `true`
  y se eliminará en AGP 10.0.
- `android.newDsl=false` está deprecado; el default actual es `true`
  y se eliminará en AGP 10.0.
- Gradle 9.6.1 informa características deprecadas incompatibles con
  Gradle 10 (no se actúa sobre ellas en este plan).
- Warnings del compilador Kotlin (annotations targets, LocalLifecycleOwner
  deprecado, createTempDir deprecado, etc.): preexistentes, no atribuibles
  al refactor.
- `stripDebugDebugSymbols` no puede procesar `libandroidx.graphics.path.so`
  y `libdatastore_shared_counter.so`; los empaqueta tal cual (aviso de
  infraestructura, no error).

### 10.2 Pruebas JVM (baseline)

- Comando: `./gradlew testDebugUnitTest --rerun-tasks`
- Resultado: BUILD SUCCESSFUL in 50s (exit 0)
- Total: **573 pruebas** — 0 fallos, 0 errores, 0 omitidas
- Cantidad de XML de resultados: 57
- Ruta de resultados XML: `app/build/test-results/testDebugUnitTest/`
- Reporte HTML: `app/build/reports/tests/testDebugUnitTest/index.html`

El total de 573 coincide exactamente con la expectativa del plan (530
anteriores + 43 añadidas). No hay filtros ni descubrimiento incompleto.

### 10.3 Build debug (baseline)

- Comando: `./gradlew assembleDebug --rerun-tasks`
- Resultado: BUILD SUCCESSFUL in 28s (exit 0)
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Tamaño: 25.626.574 bytes (~24,4 MiB)
- SHA-256: `882ade65784ac2ba922373e6065d5d5d1dba3883cfe3fcb0c70e2951e5155743`

### 10.4 Lint (baseline)

- Comando: `./gradlew lintDebug --rerun-tasks`
- Resultado: BUILD SUCCESSFUL in 1m 11s (exit 0)
- **0 errores, 0 fatales, 0 information, 64 warnings**
- Referencia histórica: el reporte anterior era de 0 errores / 64 warnings;
  coincide con este baseline. No se convierte en expectativa rígida porque
  los detectores de versiones dependen de la fecha.
- Reportes conservados:
  - HTML: `app/build/reports/lint-results-debug.html`
  - XML: `app/build/reports/lint-results-debug.xml`
  - TXT: `app/build/reports/lint-results-debug.txt`

#### Warnings por ID

| ID                          | Cantidad |
| --------------------------- | -------- |
| GradleDependency            | 16       |
| NewerVersionAvailable       | 15       |
| ModifierParameter           | 15       |
| FrequentlyChangingValue     | 6        |
| UseKtx                      | 3        |
| UnusedResources             | 3        |
| UseOfNonLambdaOffsetOverload | 1       |
| OldTargetApi                | 1        |
| ObsoleteSdkInt              | 1        |
| IconLocation                | 1        |
| ConfigurationScreenWidthHeight | 1     |
| AndroidGradlePluginVersion  | 1        |
| **Total**                   | **64**   |

### 10.5 Clasificación de resultados

- Pruebas: **573 ejecutadas, 0 fallos, 0 errores, 0 omitidas** — cumple.
- Build: exit code 0 y APK debug generado — cumple.
- Lint: exit code 0; todos los hallazgos (64 warnings) registrados como
  baseline preexistente — cumple.
- Capturas: **no aplican en 1.2**.
- Dispositivo: **no aplica en 1.2** (suite JVM en host).
- No se actualizaron dependencias, SDK, Gradle, código ni recursos.

---

## 11. Gate de aceptación — Subfase 1.2

La subfase queda aprobada únicamente si:

- [x] Las 573 pruebas JVM están verdes.
- [x] `assembleDebug` genera un APK válido.
- [x] `lintDebug` termina correctamente; todos sus hallazgos quedan
      registrados como baseline.
- [x] EmailDetailScreen.kt conserva su firma, 1.213 líneas y SHA-256 inicial.
- [x] Los tres fingerprints de MainActivity.kt y SearchScreen.kt permanecen
      idénticos.
- [x] HEAD no cambia y no se crea commit.
- [x] El único cambio nuevo continúa siendo el registro técnico.
- [x] La revisión final incluye status, diff --check, revisión completa del
      registro y recálculo de hashes.
- [x] El registro marca 1.2 Aprobada y mantiene 1.3/1.4 Pendiente.

**Cierre 1.2: todos los criterios cumplidos el 2026-08-04. Subfase 1.2 aprobada.**
**Siguiente paso: subfase 1.3 (baseline instrumentado en emulador).**

---

## 12. Subfase 1.3 — Baseline instrumentado en emulador

**Estado final: APROBADA** (corrección de sincronización de prueba autorizada y gate 54/54 verde, 2026-08-05).

### 12.1 Identidad del AVD y comando exacto

| Campo                 | Valor                                                                  |
| --------------------- | ---------------------------------------------------------------------- |
| Comando de arranque   | `/Users/david/Library/Android/sdk/emulator/emulator @Medium_Phone_API_36.1 -port 5554 -no-snapshot-load -no-boot-anim -no-audio` |
| Serial                | `emulator-5554`                                                        |
| Modelo                | `sdk_gphone64_x86_64` (del fingerprint del build)                      |
| Android               | 16 (API 36, `ro.build.version.sdk` = 36)                               |
| Fingerprint           | `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:user/release-keys` |
| Resolución            | 1080x2400 (ROTATION_0)                                                 |
| Densidad              | 420 dpi                                                               |
| Escalas de animación  | animator: default (null), transition: 1.0, window: 1.0                 |
| ABI                   | x86_64 (confirmado `ro.product.cpu.abi`)                               |
| Estado                | Despierto (`INTERACTIVE_STATE_AWAKE`), keyguard descartado (`showing=false`, `mIsShowing=false`), `SCREEN_STATE_ON` |
| Boot                  | `sys.boot_completed=1` a los ~20s                                      |
| Pixel 9 físico        | Conectado vía TLS (`adb-55080DLAQ002CK-0Wyjbr._adb-tls-connect._tcp`) — **excluido**; toda ejecución usó `ANDROID_SERIAL=emulator-5554` |

### 12.2 Limpieza de paquetes en el emulador

| Comando | Resultado |
| ------- | --------- |
| `adb -s emulator-5554 shell am force-stop com.david.mailapp` | OK |
| `adb -s emulator-5554 shell pm clear com.david.mailapp` | `Failed` (paquete no instalado en AVD fresco — estado limpio esperado) |
| `adb -s emulator-5554 shell pm clear com.david.mailapp.test` | `Failed` (ídem) |

### 12.3 Ejecución instrumentada dirigida (inicial)

```
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.emaildetail.EmailDetailIntegrationTest,com.david.mailapp.feature.emaildetail.EmailDetailCancellationTest,com.david.mailapp.feature.emaildetail.EmailDetailReadFailureEffectTest,com.david.mailapp.data.repository.EmailResolutionContractsTest,com.david.mailapp.ui.navigation.MainNavigationTest
```

Resultado: **54 pruebas, 1 fallo, 0 errores, 0 omitidas** — duración de suite 38.191s; `BUILD FAILED in 1m 51s`.

| Clase | Contrato | Pruebas |
| ----- | -------- | ------- |
| EmailDetailIntegrationTest | Resolución y apertura integrada | 11 |
| EmailDetailCancellationTest | Cancelación y salida durante operaciones | 7 (1 fallo) |
| EmailDetailReadFailureEffectTest | Snackbar localizado de fallo de lectura | 1 |
| EmailResolutionContractsTest | Resolución, caché, sesión y concurrencia | 26 |
| MainNavigationTest | Navegación, regreso y origen real | 9 |
| **Total** | | **54** |

Dispositivo reportado: `Medium_Phone_API_36.1(AVD) - 16` (nunca el Pixel 9).

- XML de la ejecución inicial (hash capturado antes de que las reejecuciones sobrescribieran el reporte): `app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml` — SHA-256 `f816127aa147e4dbce6b2f49e73d6b2efadb26c9bad307e30ea46cf1b4ed916b`
- Reporte HTML: `app/build/reports/androidTests/connected/debug/index.html`
- APK instrumentado: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

### 12.4 Fallo y reejecuciones (política del plan)

- **Contrato fallido:** `EmailDetailCancellationTest.bodyCancellationKeepsPreparingStateAndDoesNotWriteRoom`
- **Síntoma:** `kotlinx.coroutines.TimeoutCancellationException: Timed out waiting for 5000 ms` (tiempo del caso: 5.115–5.14s). Las otras 6 pruebas de la clase pasan.
- **Reejecución 1** (clase completa, sin cambios): mismo contrato, mismo fallo — `BUILD FAILED in 1m 8s`.
- **Reejecución 2** (clase completa, sin cambios): mismo contrato, mismo fallo — `BUILD FAILED in 1m 8s`.
- **Clasificación (literal del plan):** el mismo contrato falló nuevamente → **regresión preexistente reproducible** → **Subfase 1.3 BLOQUEADA**.
- No es fallo de infraestructura: instalación, ADB y arranque funcionaron correctamente (el fallo ocurre dentro de la instrumentación, en un timeout de corrutina).
- Durante estas tres ejecuciones de diagnóstico no se corrigió nada ni se atribuyó al refactor. No se amplió a `connectedDebugAndroidTest` completo. No se usaron credenciales ni datos Gmail reales.

Stack trace completo (idéntico en las tres ejecuciones):

```
kotlinx.coroutines.TimeoutCancellationException: Timed out waiting for 5000 ms
at kotlinx.coroutines.TimeoutKt.TimeoutCancellationException(Timeout.kt:188)
at kotlinx.coroutines.TimeoutCoroutine.run(Timeout.kt:156)
at kotlinx.coroutines.EventLoopImplBase$DelayedRunnableTask.run(EventLoop.common.kt:505)
at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:263)
at kotlinx.coroutines.DefaultExecutor.run(DefaultExecutor.kt:105)
at java.lang.Thread.run(Thread.java:1563)
```

### 12.5 Diagnósticos y limitaciones

- El fallo original fue reproducible 3/3 en el emulador; no era lentitud ni infraestructura.
- Causa confirmada: la primera cancelación inmediata libera `isFetchingRemoteBody` antes de la emisión inicial de Room; esa emisión inicia una segunda llamada y el contador salta de 0 a 2. La espera estricta `== 1` no podía completarse.
- **Capturas:** no hubo; corresponden a la Subfase 1.4.
- **Dispositivo:** AVD x86_64; el Pixel 9 físico permaneció excluido.
- La doble llamada se conserva y documenta como comportamiento preexistente; no se modificó `EmailDetailViewModel` ni ningún archivo de producción.

### 12.6 Excepción autorizada y validación final

Se autorizó una excepción de alcance limitada a una sola condición de sincronización en
`EmailDetailCancellationTest`: `provider.fetchBodyCalls == 1` cambió a
`provider.fetchBodyCalls >= 1`. No se cambió el contrato, el nombre de la prueba, el fake
ni producción.

- Gate de clase: **7 pruebas, 7 aprobadas**, 0 fallos, 0 errores, 0 omitidas; `BUILD SUCCESSFUL in 1m 5s`.
- Gate dirigido final: **54 pruebas, 54 aprobadas**, 0 fallos, 0 errores, 0 omitidas; duración instrumentada 35.698s y `BUILD SUCCESSFUL in 1m 31s`.
- Conteos por clase: 11 / 7 / 1 / 26 / 9, exactamente los esperados.
- XML final: `app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml`.
- SHA-256 XML final: `41580609a0e3fcc8a8683f625db707a79af60f560994c3d60325f6c9c0843520`.
- APK instrumentado: 1.322.159 bytes; SHA-256 `d32eaffdbdf242ae5527825d7f276e7f58ee212bfdde4385e519b3d362e3ae99`.
- Dispositivo reportado: `Medium_Phone_API_36.1(AVD) - 16`; Pixel 9 no utilizado.

---

## 13. Gate de aceptación — Subfase 1.3

La subfase queda aprobada únicamente si:

- [x] Se ejecutan exactamente 54 pruebas: 54 aprobadas, 0 fallos, 0 errores y 0 omitidas.
- [x] Las cinco clases presentan su conteo esperado (11/7/1/26/9).
- [x] Gradle finaliza y genera los informes instrumentados.
- [x] El reporte identifica emulator-5554/Medium_Phone_API_36.1, nunca el Pixel 9.
- [x] El fallo reproducible quedó diagnosticado como defecto de sincronización del harness, corregido sin modificar producción; el gate final es verde.
- [x] EmailDetailScreen.kt y los cambios protegidos conservan sus hashes.
- [x] HEAD permanece sin cambios; los únicos cambios propios son el registro y la corrección de una línea autorizada en la prueba.
- [x] 1.3 queda Aprobada.
- [x] 1.4 continúa Pendiente y no se crea commit.

**Resultado final: Subfase 1.3 APROBADA el 2026-08-05 tras corregir exclusivamente la condición de sincronización de la prueba y obtener 7/7 y 54/54.**
**Siguiente paso: Subfase 1.4 (baseline manual y visual en dispositivo físico).**

---

## 14. Subfase 1.4 — Baseline manual y visual en Pixel 9

**Estado: APROBADA** (2026-08-06).

### 14.1 Dispositivo y configuración cerrada

| Campo | Valor |
| ----- | ----- |
| Dispositivo | Pixel 9 (`tokay`, modelo `Pixel_9`) |
| Serial ADB | `adb-55080DLAQ002CK-0Wyjbr._adb-tls-connect._tcp` (wireless/TLS) |
| Android | 17 / API 37 |
| Resolución | 1080×2424, 420 dpi |
| Configuración cerrada | Tema oscuro, paleta Blue, AMOLED desactivado, fuente personalizada desactivada |

### 14.2 Configuración visual original (2026-08-05, reportada por el usuario)

| Ajuste | Valor original |
| ------ | -------------- |
| Tema | Oscuro |
| Paleta | Gris |
| AMOLED | Apagado |
| Fuente personalizada | Activada (Google Sans) |
| Color dinámico | Apagado |
| Líneas separadoras | Apagadas |

Configuración cerrada aplicada temporalmente: paleta **Blue** y fuente **estándar**; el resto coincide con la original. Las preferencias originales se restaurarán al terminar (ver sección 14.9).

### 14.3 Instalación y fixture

- `assembleDebug` (2026-08-05): BUILD SUCCESSFUL; APK `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `882ade65784ac2ba922373e6065d5d5d1dba3883cfe3fcb0c70e2951e5155743` (25.626.574 bytes), coincide con el baseline de 1.2.
- Instalado con `adb -s <pixel-serial> install -r app/build/outputs/apk/debug/app-debug.apk` → `Success` (datos y sesión preservados; el usuario confirmó sesión activa).
- Fixture `MAILAPP_BASELINE_1_4_20260805` enviado a la cuenta y visible en Inbox tras pull-to-refresh; **no abierto** antes del escenario offline.
  - Cuerpo: HTML identificable (`/tmp/mailapp-baseline-1-4/cuerpo-html.txt`).
  - Imagen inline: `fixture-inline.png` 512×512 (6.328 bytes), contenido neutro.
  - `baseline-small.pdf` (678 bytes, < 250 KiB).
  - `baseline-cancel.pdf` (15.000.693 bytes ≈ 15 MB).
- Archivos del fixture (temporales, fuera del repositorio): `/tmp/mailapp-baseline-1-4/`.
- Script generador (temporal, gitignored): `build/tmp-fixture-1-4/generar_fixture.py`.
- **Corrección 2026-08-06:** el fixture original se había enviado desde la propia cuenta del usuario, por lo que las capturas 01–06 mostraban su correo personal en De/Para. Para cumplir el gate "ninguna captura contiene correo personal": el fixture se reenvió desde la cuenta temporal `appmail.testing0@gmail.com` (remitente "Testemail Anon"), la sesión de MailApp se cambió a esa cuenta temporal, y los grupos 1–3 se re-ejecutaron íntegramente el 2026-08-06 sustituyendo las capturas 01–06 (nuevos SHA-256 en 14.5).

### 14.4 Escenarios manuales — RESULTADOS (8/8 grupos aprobados, 15/15 capturas)

**Matriz ejecutada el 2026-08-05 en Pixel 9 (tokay), tema oscuro, paleta Blue, sin AMOLED, fuente estándar. Grupos 1–3 re-ejecutados el 2026-08-06 (corrección de capturas con fixture de cuenta temporal, ver 14.3).**

| # | Grupo | Procedimiento | Resultado |
| - | ----- | ------------- | --------- |
| 1 | Loading/error/ready | Offline: abrir fixture sin cuerpo cacheado → loader → error recuperable; restaurar red, Reintentar → Ready. La evidencia 02 registra además una señal global de sesión expirada, consignada en 14.7. | **Aprobado como baseline con incidencia documentada** (01, 02, 03) |
| 2 | Encabezado | Cerrado (03); abierto con scrim (04); tocar scrim cierra; Back 1 cierra encabezado conservando Detail; Back 2 regresa al origen | **Aprobado** (03, 04) |
| 3 | Reply/Forward | Responder (05) y Reenviar (06) con correo original; Back regresa a la misma instancia de Detail | **Aprobado** (05, 06) |
| 4 | PDF retry/open | Offline error (07); red + reintento → Downloading (08) → apertura automática (09); reapertura desde caché | **Aprobado** (07, 08, 09) |
| 5 | PDF SAF | Selector (10) cancelado sin inestabilidad; guardado con nombre sugerido y snackbar observados manualmente; el archivo se verificó en Files (11). La captura 11 prueba el archivo guardado, no el snackbar. | **Aprobado; alcance visual aclarado** (10, 11) |
| 6 | Imagen | Menú por pulsación larga (12); pantalla completa (13) cerrada con Back; guardado y snackbar observados manualmente; verificación en galería. La captura 14 conserva Detail tras la acción, pero no alcanzó a registrar el snackbar ni la galería. | **Aprobado manualmente; evidencia visual parcial** (12, 13, 14) |
| 7 | Salida durante descarga | Descarga de baseline-cancel.pdf (15) y salida inmediata; sin archivo final ni `.tmp` (verificado por ADB), sin evento externo, sin Downloading atascado | **Aprobado** (15) |
| 8 | Navegación final | Back a Inbox; fixture visible; sin duplicados de navegación | **Aprobado** (sin captura) |

### 14.5 Evidencias — capturas (tema oscuro, Pixel 9 / API 37, 1080×2424, 420 dpi)

Ruta base: `docs/verification/emaildetail-refactor/capturas/etapa-1/subfase-1.4/pixel9-api37/oscuro/`

| Captura | Escenario | Resultado | Dimensiones | SHA-256 |
| ------- | --------- | --------- | ----------- | ------- |
| 01-loading.png | Loader al abrir offline (frame de video) | Aprobado | 1080×2424 | `e0b2bc5ee8e9dd11f360a799af1a73176ceb9286403e3f0d9914858b76a5509e` |
| 02-error-recuperable.png | Error recuperable offline; también muestra aviso global de sesión expirada (14.7) | Aprobado como baseline con incidencia | 1080×2424 | `d117721b1f61947e3f259dbccb79fe0d500667383dd00ed9bbfa98c17d12d80e` |
| 03-ready-header-cerrado.png | Ready con encabezado cerrado | Aprobado | 1080×2424 | `4ec0fc90d926b25ca4aeb15ca7848505231e13bbeb288bd8298befadc2e7aa2c` |
| 04-header-abierto-scrim.png | Encabezado abierto con scrim | Aprobado | 1080×2424 | `76d58bc0f08ea79689448f6ed517b9ceb65fe79fbe9702b9d26f9a80f8c6c717` |
| 05-reply.png | Responder | Aprobado | 1080×2424 | `68995b26b39b847b37cc52514a7224d6be7220363b526fafb8aca321b25d804c` |
| 06-forward.png | Reenviar | Aprobado | 1080×2424 | `238d5ccc2b96383cf52960a2275d6a58ec8fded7727ec9edb0a5546e22b41551` |
| 07-pdf-error-offline.png | PDF error offline (captura nativa) | Aprobado | 1080×2424 | `50d330309fa45c613dc9b348b4fb9274df2fd879b5324a3eee7ff3c68587e2b4` |
| 08-pdf-downloading.png | PDF descargando (frame de video) | Aprobado | 1080×2424 | `322bd548ff26345b0d2cc5ee24e8984416cc50a23cd79bd010c86eea09fdafdc` |
| 09-pdf-viewer.png | PDF abierto (frame de video) | Aprobado | 1080×2424 | `0fee524555c5c52e754713ba812343c16b1052c75e0c6326b9a12cac34e98e04` |
| 10-pdf-saf-picker.png | Selector SAF | Aprobado | 1080×2424 | `d5ddb743d92b702f508bca4f61e4c169bde8d6df9a255f0f4b36f5850a05b424` |
| 11-pdf-guardado.png | PDF guardado verificado en Files; el snackbar no es visible en este PNG | Aprobado; alcance visual aclarado | 1080×2424 | `154728573f9cdb830b35913a73aadd556d8dc099fc428a380d654b50d3678c4c` |
| 12-image-menu.png | Menú de imagen | Aprobado | 1080×2424 | `cb23890248824f9e5b5fc7d2cee4e69155b88b65a4a7f4f7a3e0325989c1e074` |
| 13-image-fullscreen.png | Pantalla completa imagen | Aprobado | 1080×2424 | `d073f16005d25398ffb6b97cae693ea42d9de63075f1040112911f10470b8905` |
| 14-image-guardada.png | Detail tras guardar la imagen; el snackbar y la galería no son visibles en este PNG | Evidencia visual parcial; verificación manual aprobada | 1080×2424 | `b8c80bdf4a8b34c16758dc5f1aff36ad83d7770c4616c0b9d638c06cbce2e118` |
| 15-salida-durante-descarga.png | Salida durante descarga (frame de video) | Aprobado | 1080×2424 | `bc745af6284dc38c5459ede718007c338c58473836ec1cab53c1ec2a3840ec95` |

Los videos temporales se usaron únicamente para extraer frames de Loading/Downloading/visor; **no se versionan**.

### 14.6 Verificación de caché tras salida durante descarga (ADB run-as)

- `cache/pdf_attachments/f2827c07b129c718ca9df244dd3818db34afa0a8fe3e9de5679e2c261b36c500.pdf` — 678 bytes, mtime 10:45 → **baseline-small.pdf** (descarga válida del grupo 4).
- **Sin** archivo final ni `.tmp` de `baseline-cancel.pdf` → cancelación correcta.

### 14.7 Limitaciones y notas

- ADB usa Wi-Fi (TLS): durante los escenarios offline la conexión ADB se pierde y las capturas se toman con el capturador nativo del Pixel; al restaurar la red se reconecta ADB y se copian las evidencias.
- Los videos temporales solo sirven para extraer frames de Loading/Downloading; no se versionan.
- Las capturas originales 01–06 (2026-08-05) contenían el correo personal del usuario porque el fixture se había enviado desde su propia cuenta; se detectó durante la revisión del gate y se corrigió el 2026-08-06 re-ejecutando los grupos 1–3 con el fixture reenviado desde la cuenta temporal `appmail.testing0@gmail.com` ("Testemail Anon") y sesión de MailApp en esa cuenta. **El contenido de correo visible en las capturas versionadas pertenece al fixture y ninguna muestra la cuenta personal.**
- La captura 02 y el video temporal de Loading/error muestran el aviso global `Tu sesión expiró. Inicia sesión nuevamente.` durante el escenario offline. Posteriormente se obtuvo Ready (03) y se completaron los grupos 2–3 con la cuenta temporal, pero la evidencia no demuestra continuidad ininterrumpida de esa sesión. Se conserva como comportamiento/incidencia del baseline y deberá compararse expresamente en la repetición de la Etapa 5.
- Las capturas 10–11 pertenecen al selector/visor de archivos del sistema y muestran nombres de carpetas o archivos locales ajenos al correo fixture. No muestran direcciones de correo ni contenido personal, pero la expresión "únicamente el fixture" se limita al contenido de MailApp, no a toda la interfaz del proveedor de documentos.
- Los snackbars de guardado PDF e imagen y la comprobación en galería fueron observaciones manuales. Los PNG 11 y 14 no los muestran; sus descripciones en 14.5 se limitan ahora a lo que cada imagen demuestra visualmente.

### 14.8 Estado final del dispositivo y evidencias (2026-08-06)

- **Red:** Wi-Fi/datos reactivados tras el escenario offline; ADB reconectado y verificada la copia de evidencias.
- **Archivos temporales:** el directorio de fixture `/tmp/mailapp-baseline-1-4/` fue eliminado (verificado ausente el 2026-08-06). Las evidencias del 2026-08-06 se copiaron a `/tmp/mailapp-capturas-1-4/` (fuera del repositorio, temporal).
- **Medios del dispositivo:** la grabación nativa del loading permanece en el Pixel; no se elimina sin autorización del usuario.
- **PDF SAF guardado (identificado, no borrar):** `baseline-small.pdf` guardado vía SAF el 2026-08-05 en el almacenamiento del dispositivo; permanece sin borrar a la espera de autorización del usuario.

### 14.9 Restauración de preferencias originales y sesión (2026-08-06)

- Preferencias visuales originales restauradas por el usuario: paleta **Gris**, fuente **Google Sans**; el resto coincide con 14.2 (AMOLED apagado, color dinámico apagado, líneas separadoras apagadas).
- Sesión de MailApp restaurada a la **cuenta personal** del usuario (se había cambiado temporalmente a `appmail.testing0@gmail.com` únicamente para la corrección de capturas del 2026-08-06).
- La cuenta temporal `appmail.testing0@gmail.com` queda disponible como remitente auxiliar de fixtures para etapas futuras.

## 15. Gate de aceptación — Subfase 1.4

La subfase queda aprobada únicamente si:

- [x] Los 8 grupos de la matriz quedan Aprobados y las 15/15 capturas existen con SHA-256 registrado.
- [x] El contenido de correo visible pertenece al fixture y ninguna captura contiene correo personal (corregido el 2026-08-06 re-ejecutando los grupos 1–3 con la cuenta temporal). Los nombres locales visibles en el selector del sistema quedan consignados en 14.7.
- [x] Preferencias del usuario restauradas (14.9).
- [x] Red restablecida y archivos temporales eliminados (14.8).
- [x] PDF SAF guardado identificado; no se borra sin autorización (14.8).
- [x] EmailDetailScreen.kt, MainActivity.kt y SearchScreen.kt conservan sus hashes (verificados el 2026-08-06).
- [x] Únicos cambios propios: registro, capturas y la corrección autorizada de una línea en EmailDetailCancellationTest (revisado con `git status --short` el 2026-08-06).
- [x] Auditoría ampliada (2026-08-06): `./gradlew testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL, **573/573** (0 fallos/errores/omitidas); `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL; `./gradlew lintDebug --rerun-tasks` → BUILD SUCCESSFUL, **0 errores / 64 warnings** (baseline). Se conserva como evidencia el gate instrumentado 54/54 de la subfase 1.3.
- [x] 1.4 y Etapa 1 quedan Aprobadas; `diff --check` limpio y staged diff revisado (17 archivos: registro, 15 capturas y la prueba).
- [x] Stageado selectivo de registro, capturas y corrección de prueba; `MainActivity.kt` y `SearchScreen.kt` quedaron excluidos.
- [x] Commit obligatorio creado: `docs(emaildetail): establish conservative refactor baseline` (17 archivos, +760/−1 según `git show --stat`; 759 líneas del registro y el reemplazo 1/1 de la prueba según `git show --numstat`).
- [x] Verificación post-commit: HEAD es el commit de etapa (confirmado con `git log`); el working tree solo conserva `MainActivity.kt` y `SearchScreen.kt` modificados (cambios previos del usuario, intactos y sin commit).

**Resultado final: Subfase 1.4 APROBADA el 2026-08-06.**
**Etapa 1 (Baseline y protección) COMPLETADA — commit de etapa verificado con `git log --oneline -1`.**
**Siguiente paso: Etapa 2 — Subfase 2.1 (Política de nombres PDF).**

## 16. Subfase 2.1 — Política de nombres PDF

**Estado: APROBADA** (2026-08-06).

### 16.1 Cambios de implementación

- Creado `PdfFileNaming.kt` en `com.david.mailapp.feature.emaildetail` con:
  - `internal fun sanitizeDisplayName(name: String, defaultName: String): String` (textual, incluyendo KDoc)
  - `internal fun buildPdfSuggestedName(displayName: String, defaultName: String): String` (textual, incluyendo KDoc)
- Eliminados ambos bloques de `EmailDetailScreen.kt` (líneas 1052–1066 originales).
- Sin cambios en consumidores, imports ni pruebas (mismo paquete).
- Sin modificar repositorio, ViewModel, navegación, recursos, Gradle, HTML/WebView, MainActivity.kt ni SearchScreen.kt.

### 16.2 Validación

- `./gradlew testDebugUnitTest --tests 'PdfAttachmentFormattingTest' --rerun-tasks` → BUILD SUCCESSFUL, **35/35** (0 fallos, 0 errores, 0 omitidas).
- `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL.
- Búsqueda estática: ambas funciones tienen una sola definición, en `PdfFileNaming.kt`; cero ocurrencias residuales en `EmailDetailScreen.kt`.
- Firma pública de `EmailDetailScreen(emailId, onBack, onReply, onForward, modifier)` intacta.
- Hashes protegidos: MainActivity.kt `a8275404...` ✓, SearchScreen.kt `3966a9fe...` ✓.
- Working tree: solo los cambios de esta subfase (`PdfFileNaming.kt` nuevo, `EmailDetailScreen.kt` reducido, registro actualizado) más los cambios previos ajenos del usuario (MainActivity.kt, SearchScreen.kt).

### 16.3 Criterios de aceptación

- [x] Misma API interna y comportamiento byte por byte de las dos funciones.
- [x] 35/35 pruebas específicas verdes.
- [x] Compilación verde.
- [x] Ningún cambio de producción fuera de EmailDetailScreen.kt y PdfFileNaming.kt.
- [x] Hashes de MainActivity.kt y SearchScreen.kt conservados.
- [x] Firma pública de EmailDetailScreen conservada.
- [x] Registro técnico actualizado.
- [x] Working tree conserva los dos cambios previos del usuario sin stagear.
- [x] No se crea commit hasta cerrar toda la Etapa 2 (Subfase 2.5).

**Resultado final: Subfase 2.1 APROBADA el 2026-08-06.**
**Siguiente paso: Subfase 2.2 — Copiado de archivos PDF.**

## 17. Subfase 2.2 — Copiado de archivos PDF

**Estado: APROBADA** (2026-08-06).

### 17.1 Cambios de implementación

- Creado `PdfFileCopy.kt` en `com.david.mailapp.feature.emaildetail` con:
  - `internal fun copyFileToUri(context, source, destinationUri): Boolean` (textual, incluyendo KDoc y tipos cualificados)
  - `internal fun copyFileToStream(source, output): Boolean` (textual, incluyendo KDoc y tipos cualificados)
- Eliminados ambos bloques de `EmailDetailScreen.kt` (líneas 1049–1097 originales tras 2.1).
- Sin cambios en consumidores, imports ni pruebas (mismo paquete).
- Hash baseline de entrada de `EmailDetailScreen.kt`: `e9b96a5ecb7f76eb6e7906dc469293af8346f1d53b3f3b6fcc04d562286e80f2` (verificado antes de la extracción).

### 17.2 Validación

- `./gradlew testDebugUnitTest --tests 'PdfSaveFileCopyTest' --rerun-tasks` → BUILD SUCCESSFUL, **2/2** (0 fallos, 0 errores, 0 omitidas).
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- Búsqueda estática: ambas funciones solo en `PdfFileCopy.kt`; cero residuales en `EmailDetailScreen.kt`.
- Hashes protegidos: MainActivity.kt `a8275404...` ✓, SearchScreen.kt `3966a9fe...` ✓.
- Cambios propios acumulados Etapa 2: `EmailDetailScreen.kt`, `PdfFileNaming.kt`, `PdfFileCopy.kt`, `registro-tecnico.md`.

### 17.3 Criterios de aceptación

- [x] API interna y comportamiento exactos (buffer 8192, `.use`, `"wt"`, `totalWritten == sourceSize`, captura `catch (_: Exception)`).
- [x] 2/2 pruebas `PdfSaveFileCopyTest` verdes.
- [x] `assembleDebug` verde.
- [x] Ningún cambio de producción fuera de los archivos permitidos.
- [x] Hashes de MainActivity.kt y SearchScreen.kt conservados.
- [x] Registro técnico actualizado.
- [x] Working tree conserva los cambios previos del usuario sin stagear.
- [x] No se crea commit hasta cerrar toda la Etapa 2 (Subfase 2.5).

**Resultado final: Subfase 2.2 APROBADA el 2026-08-06.**
**Siguiente paso: Subfase 2.3 — Etiquetas de acciones PDF.**

## 18. Subfase 2.3 — Etiquetas de acciones PDF

**Estado: APROBADA** (2026-08-06).

### 18.1 Cambios de implementación

- Creado `PdfActionLabels.kt` en `com.david.mailapp.feature.emaildetail` con:
  - `internal data class PdfActionLabels` (7 campos: `cacheExpired`, `saved`, `saveFailed`, `noFilePicker`, `pickerOpenFailed`, `noViewer`, `openFailed` — mismo orden, visibilidad y comentario de sección).
- Eliminado de `EmailDetailScreen.kt` el comentario y la clase (líneas 1134–1145 originales tras 2.2).
- Sin cambios en la construcción de `pdfLabels` (línea 165) ni en consumidores.
- Hash baseline de entrada de `EmailDetailScreen.kt`: `2e6df4d1333a3da67590ffcf54f3f330fb661cd39bffe7253e638aa281769f95` (verificado antes de la extracción).

### 18.2 Validación

- `./gradlew testDebugUnitTest --tests 'EmailDetailContractsTest' --rerun-tasks` → BUILD SUCCESSFUL, **5/5** (0 fallos, 0 errores, 0 omitidas).
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- Búsqueda estática: `PdfActionLabels` definido una sola vez, en `PdfActionLabels.kt`; cero residuales en `EmailDetailScreen.kt`. Siete campos, mismo orden y nombres. Construcción en línea 165 conserva los siete recursos.
- Hashes protegidos: MainActivity.kt `a8275404...` ✓, SearchScreen.kt `3966a9fe...` ✓.
- Cambios propios acumulados Etapa 2: `EmailDetailScreen.kt`, `PdfFileNaming.kt`, `PdfFileCopy.kt`, `PdfActionLabels.kt`, `registro-tecnico.md`.
- Corregidos los pendientes "[ ] No se crea commit…" de 2.1 (16.3) y 2.2 (17.3) → `[x]`.

### 18.3 Criterios de aceptación

- [x] PdfActionLabels conserva visibilidad `internal`, 7 campos, orden y semántica.
- [x] Ningún mensaje ni recurso resuelto cambió.
- [x] 5/5 pruebas `EmailDetailContractsTest` verdes.
- [x] `assembleDebug` verde.
- [x] Ningún cambio de producción fuera de los archivos permitidos.
- [x] Hashes de MainActivity.kt y SearchScreen.kt conservados.
- [x] Registro técnico actualizado.
- [x] Working tree conserva los cambios previos del usuario sin stagear.
- [x] No se crea commit hasta cerrar toda la Etapa 2 (Subfase 2.5).

**Resultado final: Subfase 2.3 APROBADA el 2026-08-06.**
**Siguiente paso: Subfase 2.4 — Iconos de detalle.**

## 19. Subfase 2.4 — Iconos de detalle

**Estado: APROBADA** (2026-08-06).

### 19.1 Cambios de implementación

- Creado `EmailDetailIcons.kt` en `com.david.mailapp.feature.emaildetail` con:
  - `val MaterialSymbolsReply: ImageVector` + caché privada (textual, geometría, viewport, paths, colores)
  - `val TablerArrowForwardUpDouble: ImageVector` + caché privada (textual, geometría, viewport, paths, strokes, caps, joins)
  - Imports exclusivos añadidos: `Color`, `SolidColor`, `StrokeCap`, `StrokeJoin`, `ImageVector`, `vector.path`, `dp`.
- Eliminado de `EmailDetailScreen.kt` el bloque completo de iconos (líneas 1047–1132) y los imports exclusivos (`SolidColor`, `StrokeCap`, `StrokeJoin`, `vector.path`).
- Sin cambios en consumidores de responder/reenviar (mismo paquete).
- Hash baseline de entrada de `EmailDetailScreen.kt`: `ebc5569f7fd173a482f869520bcd7a331deac8df3111d447a441976d980ab0d6` (verificado).

### 19.2 Validación automatizada

- `./gradlew testDebugUnitTest --tests 'EmailDetailContractsTest' --rerun-tasks` → BUILD SUCCESSFUL, **5/5** (0 fallos, 0 errores, 0 omitidas).
- `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL.
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- Búsqueda estática: cada vector tiene una sola definición, en `EmailDetailIcons.kt`; cada caché privada junto a su vector; cero paths/cachés residuales en `EmailDetailScreen.kt`.
- Hashes protegidos: MainActivity.kt `a8275404...` ✓, SearchScreen.kt `3966a9fe...` ✓.
- Cambios propios acumulados Etapa 2: `EmailDetailScreen.kt`, `PdfFileNaming.kt`, `PdfFileCopy.kt`, `PdfActionLabels.kt`, `EmailDetailIcons.kt`, `registro-tecnico.md`.

### 19.3 Gate visual

- [x] APK instalado con `adb install -r` preservando sesión y datos.
- [x] Configuración baseline aplicada temporalmente: Pixel 9, oscuro, Blue, sin AMOLED, fuente estándar.
- [x] Fixture `MAILAPP_BASELINE_1_4_20260805` abierto con encabezado cerrado.
- [x] Iconos de responder/reenviar comparados contra `03-ready-header-cerrado.png`: misma forma, tamaño, color, alineación y separación — sin diferencias.
- [x] Preferencias del usuario restauradas, sesión/red estables confirmadas.

### 19.4 Criterios de aceptación

- [x] Ninguna API pública cambia.
- [x] Vectores y cachés conservan contenido exacto (geometría, paths, colores, strokes, viewports, caches).
- [x] 5/5 pruebas `EmailDetailContractsTest` verdes.
- [x] Compilación y `assembleDebug --rerun-tasks` verdes.
- [x] Comparación visual sin diferencias (gate 19.3).
- [x] Cambios propios acumulados correctos (6 archivos).
- [x] Hashes de MainActivity.kt y SearchScreen.kt conservados.
- [x] Working tree conserva los cambios previos del usuario sin stagear.
- [x] No se crea commit hasta cerrar toda la Etapa 2 (Subfase 2.5).

**Resultado final: Subfase 2.4 APROBADA el 2026-08-06.**
**Siguiente paso: Subfase 2.5 — Helpers de apertura PDF (última de Etapa 2).**

## 20. Subfase 2.5 — Helpers de apertura PDF

**Estado: APROBADA** (2026-08-06).

### 20.1 Cambios de implementación

- Creado `PdfExternalActionHandler.kt` en `com.david.mailapp.feature.emaildetail` con:
  - `internal suspend fun handlePdfExternalActionRequest(...)`, extraída sin alterar la resolución del repositorio, el trabajo en `Dispatchers.IO`, la expiración de caché, el saneamiento del nombre ni los mensajes mostrados.
  - `private suspend fun openPdfIntent(...)`, extraída conservando `FileProvider`, `ACTION_VIEW`, MIME PDF, flags, `ClipData` y manejo de excepciones.
- Eliminados de `EmailDetailScreen.kt` ambos helpers y sus imports exclusivos (`Intent` y `FileProvider`).
- Se conserva `ActivityNotFoundException` en `EmailDetailScreen.kt` porque también lo utiliza el launcher SAF.
- La llamada consumidora permanece sin cambios funcionales; la visibilidad de `handlePdfExternalActionRequest` pasa de `private` a `internal`, el mínimo necesario para compartirla dentro del paquete/módulo.
- Hash baseline de entrada de `EmailDetailScreen.kt`: `4652aedbe20e868275da4304e27323dc69d4c248ea9021897e50d7603b12de7b` (verificado antes de la extracción).

### 20.2 Validación

- Suite PDF focalizada → BUILD SUCCESSFUL, **85/85** (0 fallos, 0 errores, 0 omitidas):
  - `PdfAttachmentFormattingTest`: 35.
  - `PdfSaveFileCopyTest`: 2.
  - `EmailDetailViewModelPdfTest`: 26.
  - `PdfCacheManagerTest`: 22.
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- Búsqueda estática: una sola definición de cada helper; `openPdfIntent` continúa privado; no quedan bloques residuales ni imports exclusivos en `EmailDetailScreen.kt`.
- Hash final de `EmailDetailScreen.kt`: `201e2c0e6131f7f92653a8c424abd4b5df1460280fe2672b58b5ab4a2c85086b`.
- Hash de `PdfExternalActionHandler.kt`: `9cf44133e3107c4859d8d1e972dd4bc858e8503fd2683f3c5c695862fc82219c`.
- Hashes protegidos: MainActivity.kt `a8275404...` ✓, SearchScreen.kt `3966a9fe...` ✓; diff combinado protegido `84adbdbe...` ✓.
- Cambios propios acumulados Etapa 2: `EmailDetailScreen.kt`, `PdfFileNaming.kt`, `PdfFileCopy.kt`, `PdfActionLabels.kt`, `EmailDetailIcons.kt`, `PdfExternalActionHandler.kt`, `registro-tecnico.md`.

### 20.3 Criterios de aceptación

- [x] Flujo de apertura PDF y mensajes conservados sin cambios funcionales.
- [x] La única ampliación de visibilidad es `handlePdfExternalActionRequest`: `private` → `internal`.
- [x] `openPdfIntent` continúa privado.
- [x] Suite PDF focalizada: 85/85 pruebas verdes.
- [x] `assembleDebug --rerun-tasks` verde.
- [x] Ninguna API pública cambia.
- [x] Hashes de MainActivity.kt y SearchScreen.kt conservados.
- [x] Working tree conserva los cambios previos del usuario sin stagear.
- [x] Registro técnico actualizado y Etapa 2 marcada `En curso`.
- [x] No se crea commit antes de la auditoría consolidada de Etapa 2.

**Resultado final: Subfase 2.5 APROBADA el 2026-08-06.**
**Siguiente paso: auditoría consolidada de Etapa 2 y, si todos sus gates pasan, commit de Etapa 2.**

## 21. Auditoría consolidada — Etapa 2

**Estado: APROBADA** (2026-08-06).

### 21.1 Gates automatizados

- `./gradlew testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL, **573/573** pruebas (0 fallos, 0 errores, 0 omitidas).
- Cobertura PDF focalizada incluida y previamente ejecutada de forma aislada: **85/85** pruebas verdes (`PdfAttachmentFormattingTest` 35, `PdfSaveFileCopyTest` 2, `EmailDetailViewModelPdfTest` 26 y `PdfCacheManagerTest` 22).
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL; APK generado correctamente.
- `git diff --check` → sin errores.
- Las advertencias emitidas corresponden a deprecaciones preexistentes; no hubo errores nuevos de compilación ni pruebas.

### 21.2 Auditoría de alcance y comportamiento

- Los cinco bloques previstos fueron extraídos a archivos cohesivos: política de nombres PDF, copiado PDF, etiquetas PDF, iconos de detalle y helpers de apertura PDF.
- Cada función, clase, vector y caché extraídos tiene una sola definición; no existe duplicación residual en `EmailDetailScreen.kt`.
- No cambió ningún archivo de recursos, ViewModel, repositorio, capa de datos ni dominio.
- Se conservaron firmas, lógica, buffers, nombres, orden de campos, strings, geometría vectorial, FileProvider, MIME, flags, `clipData`, errores y snackbars.
- La única ampliación de visibilidad fue la mecánicamente necesaria para `handlePdfExternalActionRequest`: `private` → `internal`; no se introdujo API pública.
- La comparación visual de Subfase 2.4 quedó aprobada sin diferencias en los iconos de responder/reenviar.
- Archivos propios de Etapa 2: `EmailDetailScreen.kt`, `PdfFileNaming.kt`, `PdfFileCopy.kt`, `PdfActionLabels.kt`, `EmailDetailIcons.kt`, `PdfExternalActionHandler.kt` y este registro técnico.

### 21.3 Protección y cierre

- `MainActivity.kt`: `a8275404afe60158d08616487124020b64d6aa1df2cb0f02f4c56c1d3b52cd55` ✓.
- `SearchScreen.kt`: `3966a9feace5bbae418969414e7a543c26ee4909a0914ddc75eee450401d89b3` ✓.
- Diff combinado protegido: `84adbdbeee0dd263c5b3ab94b56b996dd5adf51c08a2f60c6e2f2f7bbb9c224e` ✓.
- Los dos archivos ajenos permanecen fuera del alcance y del commit de Etapa 2.
- Commit de etapa previsto por el plan maestro: `refactor(emaildetail): extract independent pdf and icon support`.

**Resultado final: Etapa 2 APROBADA el 2026-08-06.**
**Siguiente paso después del commit: Etapa 3, Subfase 3.1 — Encabezado flotante.**

## 22. Subfase 3.1 — Encabezado flotante

**Estado: APROBADA** (2026-08-06).

### 22.1 Cambios de implementación

- Creado `components/FloatingHeaderPanel.kt` (`com.david.mailapp.feature.emaildetail.components`) con:
  - `internal fun FloatingHeaderPanel(email, isExpanded, onToggle, traceMail, modifier)` — extraída textualmente con animaciones, offsets, heightIn(360.dp), scroll, formas, colores, elevaciones, espaciados, traza HEADER_LAYOUT y handle.
  - `private fun HeaderDetailRow(icon, label, value, modifier)` — extraída textualmente.
  - `private fun rememberDateFormat(): SimpleDateFormat` — extraída textualmente.
- Trasladados desde `EmailDetailScreen.kt` los tres bloques y el comentario descriptivo original del panel; retirados también sus imports exclusivos, ya sin consumidores.
- Añadido `import FloatingHeaderPanel` en `EmailDetailScreen.kt`.
- Se conservan en `EmailDetailScreen.kt`: `showDetailsPanel` y su estado inicial cerrado, BackHandler cerrando primero el panel, scrim (alpha 0.48f, cierre por toque, zIndex 1f), contenedor del panel (alineado arriba, zIndex 2f), selección del correo solo para PreparingBody y Ready.

### 22.2 Validación automatizada

- `./gradlew testDebugUnitTest --tests 'EmailDetailContractsTest' --rerun-tasks` → BUILD SUCCESSFUL, **5/5**.
- `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL.
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- Búsqueda estática: una sola definición de cada símbolo (`FloatingHeaderPanel`, `HeaderDetailRow`, `rememberDateFormat`) en `FloatingHeaderPanel.kt`; cero residuales en `EmailDetailScreen.kt`.
- Firma pública de `EmailDetailScreen(emailId, onBack, onReply, onForward, modifier)` intacta.
- Hashes protegidos: MainActivity.kt `a8275404...` ✓, SearchScreen.kt `3966a9fe...` ✓.

### 22.3 Gate visual

- [x] APK instalado con `adb install -r` preservando sesión y datos.
- [x] Configuración baseline temporal: Pixel 9, oscuro, Blue, sin AMOLED, fuente estándar.
- [x] Fixture `MAILAPP_BASELINE_1_4_20260805` abierto con encabezado cerrado → idéntico a `03-ready-header-cerrado.png`.
- [x] Encabezado expandido con scrim → idéntico a `04-header-abierto-scrim.png`.
- [x] Verificada animación, flecha, handle, contenido y scroll.
- [x] Scrim tocado: cierra sin activar el WebView.
- [x] Reabierto; primer Back cierra el panel conservando Detail; segundo Back regresa al origen.
- [x] Preferencias del usuario restauradas, sesión/red estables confirmadas.

### 22.4 Criterios de aceptación

- [x] Comportamiento funcional y visual idéntico al baseline.
- [x] Compilación, pruebas y APK verdes.
- [x] FloatingHeaderPanel amplía su visibilidad de `private` a `internal`; sin otras APIs públicas nuevas.
- [x] Ningún cambio fuera de `FloatingHeaderPanel.kt`, `EmailDetailScreen.kt` y el registro.
- [x] Gate visual completado y aprobado.
- [x] Hashes de MainActivity.kt y SearchScreen.kt conservados.
- [x] Working tree conserva los cambios previos del usuario sin stagear.
- [x] No se crea commit hasta cerrar toda la Etapa 3 (Subfase 3.4).

**Resultado final: Subfase 3.1 APROBADA el 2026-08-06.**
**Siguiente paso: Subfase 3.2 — Cuerpo y adjuntos.**

## 23. Subfase 3.2 — Cuerpo y adjuntos

**Estado: APROBADA** (2026-08-06).

### 23.1 Cambios de implementación

- Creado `components/EmailDetailContent.kt` (`com.david.mailapp.feature.emaildetail.components`) con `internal fun EmailDetailContent(...)` — extraída textualmente, visibilidad `private` → `internal`.
- Conservado sin cambios: firma y orden de parámetros, `bodyKey` y dependencias del remember, estado `isBodyRendered`, condición y transición del loader, orden WebView → loader superpuesto → sección PDF, tamaños, `weight`, `fillMaxSize`, `zIndex`, trazas `UI_*`, callbacks `onPageRendered`/PDF/imagen.
- Eliminado el bloque de `EmailDetailScreen.kt` + imports exclusivos (`withFrameNanos`, `onGloballyPositioned`, `positionInRoot`, `PdfDownloadState`, `EmailBodyWebView`, `LocalThemeConfig`).
- Añadido `import EmailDetailContent` en `EmailDetailScreen.kt`.
- Se mantiene el uso directo de `PdfAttachmentSection` en `BodyError` (sin cambios).
- Sin acceso a AppContainer, ViewModel, repositorio ni navegación dentro del nuevo componente.

### 23.2 Validación automatizada

- Suites relevantes (4) → BUILD SUCCESSFUL, **81/81** (0 fallos, 0 errores, 0 omitidas):
  - `EmailDetailContractsTest`: 5.
  - `EmailDetailViewModelResolutionTest`: 15.
  - `EmailDetailViewModelPdfTest`: 26.
  - `PdfAttachmentFormattingTest`: 35.
- `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL.
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- Búsqueda estática: una sola definición de `EmailDetailContent` en `EmailDetailContent.kt`; cero bloque residual en la pantalla.
- Hashes congelados de componentes: EmailBodyWebView.kt `83cf07eb...` ✓, PdfAttachmentSection.kt `e0934ce4...` ✓.
- Hashes protegidos: MainActivity.kt `a8275404...` ✓, SearchScreen.kt `3966a9fe...` ✓.
- Firma pública de `EmailDetailScreen(emailId, onBack, onReply, onForward, modifier)` intacta.

### 23.3 Gate físico

- [x] APK instalado preservando datos.
- [x] Configuración visual baseline temporal (oscuro, Blue, sin AMOLED, fuente estándar).
- [x] Fixture `MAILAPP_BASELINE_1_4_20260805` abierto: cuerpo, loader y sección PDF con las mismas dimensiones y orden.
- [x] MailRenderTrace verificado: `UI_CONTENT_ENTER` → `UI_BODY_LAYOUT` (1080×1481/1491) → `UI_BODY_INPUT` → `UI_LOADER_SHOWN` (awaiting_visual_callback) → `UI_RENDER_CALLBACK` → `UI_LOADER_HIDDEN` (rendered), repetido correctamente para cuerpo corto (992) y completo (9438).
- [x] PDF pulsado → apertura del visor; Guardar como → SAF cancelado → Detail estable.
- [x] Pulsación larga sobre la imagen → menú de acciones mostrado.
- [x] Comparación visual de Ready y adjuntos sin diferencias.
- [x] Preferencias, sesión y red restauradas.

### 23.4 Criterios de aceptación

- [x] Comportamiento, trazas, tamaños, transiciones y callbacks idénticos.
- [x] Ninguna API pública nueva; solo `EmailDetailContent` pasa de `private` a `internal`.
- [x] Componentes WebView/PDF y sus hashes permanecen intactos.
- [x] Pruebas (81/81), compilación y APK verdes.
- [x] Smoke físico verde (gate 23.3).
- [x] MainActivity.kt y SearchScreen.kt sin stagear y con hashes protegidos.
- [x] No se crea commit hasta cerrar toda la Etapa 3 (Subfase 3.4).

**Resultado final: Subfase 3.2 APROBADA el 2026-08-06.**
**Siguiente paso: Subfase 3.3 — Barra superior y estados de pantalla.**

## 24. Subfase 3.3 — Barra superior y estados de pantalla

**Estado: APROBADA** (2026-08-06).

### 24.1 Cambios de implementación

- Creado `components/EmailDetailTopBar.kt` con `internal fun EmailDetailTopBar(uiState, onBack, onReply, onForward)` — título, navegación, iconos, colores y cálculo currentEmail; responder/reenviar habilitados solo en Ready con tint deshabilitado y callbacks con el mismo ID.
- Creado `components/EmailDetailStateContent.kt` con:
  - `internal fun EmailDetailLoading(modifier)` — spinner 36.dp centrado.
  - `internal fun EmailDetailResolutionError(state, onRetry, modifier)` — mensaje tipado + botón Reintentar condicional (`state.retryable`).
  - `internal fun EmailDetailBodyError(state, pdfDownloadStates, onPdfAttachmentClick, onPdfSaveClick, savingStableIds, onRetry, modifier)` — error + PDF condicional.
- Reemplazados los bloques inline en `EmailDetailScreen.kt` por llamadas que pasan datos y callbacks del ViewModel; ninguna referencia a AppContainer/ViewModel/repositorios en los nuevos componentes.
- Eliminado `private fun EmailDetailLoading` del final.
- Retirados los 13 imports exclusivos de los bloques extraídos, ya sin consumidores en `EmailDetailScreen.kt`.
- Se mantienen sin cambios las ramas PreparingBody/Ready, overlays, Scaffold, snackbar y firma pública.

### 24.2 Validación automatizada

- Suites relevantes (4) → BUILD SUCCESSFUL, **81/81**.
- `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL.
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- `EmailDetailStateContentTest` instrumentada → **3/3**: BodyError retryable, no retryable y PDF con callbacks Open/Save.
- Búsqueda estática: una sola definición de cada componente; cero bloques residuales en `EmailDetailScreen.kt`.
- Hashes protegidos: MainActivity.kt `a8275404...` ✓, SearchScreen.kt `3966a9fe...` ✓.
- Firma pública de `EmailDetailScreen(emailId, onBack, onReply, onForward, modifier)` intacta.

### 24.3 Gate funcional y visual

- [x] Loading: spinner idéntico a `01-loading.png`; acciones deshabilitadas.
- [x] ResolutionError: mensaje tipado + botón solo si retryable=true; Reintentar conserva el callback.
- [x] PreparingBody: barra visible, responder/reenviar deshabilitados.
- [x] Ready: acciones habilitadas; Responder/Reenviar abren destinos correctos (coinciden con `03`, `05`, `06`).
- [x] Back, encabezado, PDF y overlays sin regresiones.
- [x] BodyError cubierto de forma determinista por `EmailDetailStateContentTest`: mensaje, visibilidad condicional de Reintentar, PDF y callbacks Open/Save.
- [x] Preferencias, sesión y red restauradas.

### 24.4 Criterios de aceptación

- [x] Matriz Loading/ResolutionError/BodyError/PreparingBody/Ready funcionalmente idéntica.
- [x] Ninguna API pública nueva; solo componentes extraídos con visibilidad `internal`.
- [x] Componentes nuevos sin dependencias de infraestructura.
- [x] Pruebas (81/81), compilación y APK verdes.
- [x] Smoke funcional/visual verde (gate 24.3) y BodyError instrumentado 3/3.
- [x] MainActivity.kt y SearchScreen.kt sin stagear y con hashes protegidos.
- [x] No se crea commit hasta cerrar toda la Etapa 3 (Subfase 3.4).

**Resultado final: Subfase 3.3 APROBADA el 2026-08-06.**
**Siguiente paso: Subfase 3.4 — Overlays de imágenes (última de Etapa 3).**

## 25. Subfase 3.4 — Overlays de imágenes

**Estado: APROBADA** (2026-08-06).

### 25.1 Cambios de implementación

- Creado `components/ImageOverlays.kt` como unidad visual cohesiva con:
  - `internal fun ImageActionSheet(activeImageUrl, onOpenFullscreen, onDismiss)` — menú Abrir/Guardar, forma, espaciado, iconos, colores, etiquetas resueltas y llamada existente a `ImageUtils.saveImageToGallery`.
  - `internal fun FullscreenImageDialog(imageUrl, onDismiss)` — decodificación existente mediante `ImageUtils.decodeDataUriToBitmap`, diálogo sin ancho de plataforma, cierre por Back/toque exterior/toque en contenido, imagen y error tipado.
- `EmailDetailScreen.kt` conserva la propiedad de `activeImageUrl` y `showFullscreenImage`, sus valores iniciales y el mismo orden de transiciones Abrir → fullscreen y Guardar → cierre del menú.
- El menú permanece dentro del `Box` de contenido y el diálogo permanece fuera de ese `Box`, conservando la composición original.
- Retirados de la pantalla únicamente imports y dependencias visuales sin consumidores; no se modificaron recursos, `ImageUtils`, WebView, PDF, ViewModel, repositorio, navegación ni modelos.
- `ImageUtils.kt` permanece intacto con hash `cea09ff8d5d3706929d33ff337979d98c3f8e295aca1309a7656d07306a8abaf`.

### 25.2 Validación automatizada

- Suites JVM relevantes (5) → BUILD SUCCESSFUL, **93/93** (0 fallos, 0 errores, 0 omitidas):
  - `EmailDetailContractsTest`: 5.
  - `EmailDetailViewModelResolutionTest`: 15.
  - `EmailDetailViewModelPdfTest`: 26.
  - `PdfAttachmentFormattingTest`: 35.
  - `ImageUtilsTest`: 12.
- `ImageOverlaysTest` instrumentada en `Medium_Phone_API_36.1` → **4/4**: acciones visibles, Abrir reenvía la misma data URI y cierra el menú, Back descarta el menú, fullscreen válido cierra por toque y formato inválido muestra error y cierra.
- `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL.
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL; APK generado correctamente.
- `git diff --check` → sin errores.

### 25.3 Gate funcional

- [x] Pulsación larga conserva el callback que asigna la data URI a `activeImageUrl`.
- [x] Abrir conserva la URL exacta, muestra el diálogo y descarta el menú.
- [x] Guardar conserva etiquetas, corrutina y llamada exacta a `ImageUtils.saveImageToGallery`; los 12 contratos JVM de formato, almacenamiento, publicación, limpieza y cancelación permanecen verdes.
- [x] Descartar el menú mediante Back invoca el mismo cierre.
- [x] El diálogo conserva cierre por Back, exterior y toque; la decodificación válida y el error de carga están cubiertos de forma determinista.
- [x] No cambian formato, calidad ni destino `Pictures/MailApp`.

### 25.4 Criterios de aceptación

- [x] Menú y diálogo extraídos como una unidad visual, sin duplicación residual en `EmailDetailScreen.kt`.
- [x] Estado visual conservado en la pantalla; ninguna API pública nueva.
- [x] Componentes nuevos sin acceso a AppContainer, ViewModel o repositorios.
- [x] Apertura, guardado, descarte y cierre preservan el comportamiento baseline.
- [x] Pruebas JVM 93/93, instrumentación 4/4, compilación y APK verdes.
- [x] MainActivity.kt y SearchScreen.kt permanecen sin stagear y con hashes protegidos.
- [x] No se crea commit antes de la auditoría consolidada de Etapa 3.

**Resultado final: Subfase 3.4 APROBADA el 2026-08-06.**
**Siguiente paso: auditoría consolidada y commit de Etapa 3.**

## 26. Auditoría consolidada y cierre de Etapa 3

**Estado: APROBADA** (2026-08-06).

### 26.1 Gates automatizados finales

- `./gradlew testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL, **573/573** pruebas (0 fallos, 0 errores, 0 omitidas).
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL; `app-debug.apk` generado correctamente (25.642.958 bytes).
- Auditoría instrumentada consolidada en `Medium_Phone_API_36.1` → BUILD SUCCESSFUL, **26/26** (0 fallos, 0 errores, 0 omitidas):
  - `EmailDetailIntegrationTest`.
  - `EmailDetailCancellationTest`.
  - `EmailDetailReadFailureEffectTest`.
  - `EmailDetailStateContentTest`.
  - `ImageOverlaysTest`.
- `git diff --check` → sin errores.
- Las advertencias corresponden a deprecaciones ya existentes; no aparecieron errores ni advertencias atribuibles a los componentes extraídos.

### 26.2 Auditoría visual y funcional consolidada

- Encabezado cerrado y abierto/scrim fueron comparados en 3.1 contra `03-ready-header-cerrado.png` y `04-header-abierto-scrim.png`, sin diferencias; animación, scroll, toque del scrim y prioridad de Back aprobados.
- Cuerpo, loader, WebView y PDF fueron verificados físicamente en 3.2 con el fixture `MAILAPP_BASELINE_1_4_20260805`; dimensiones, orden, trazas, apertura PDF y cancelación SAF permanecieron iguales.
- Matriz Loading/ResolutionError/BodyError/PreparingBody/Ready, acciones de barra y navegación fueron aprobadas en 3.3, con BodyError cubierto adicionalmente por 3/3 pruebas Compose.
- Menú, fullscreen, descarte y error de imagen quedaron cubiertos por 4/4 pruebas Compose en 3.4; el guardado conserva la llamada exacta a `ImageUtils` y sus 12/12 contratos JVM.
- La evidencia baseline de imagen permanece identificada por hashes: `12-image-menu.png`, `13-image-fullscreen.png` y `14-image-guardada.png`; no se generaron capturas versionadas nuevas.

### 26.3 Alcance, dependencias y protección

- Los bloques visuales de la etapa tienen definiciones únicas en `FloatingHeaderPanel.kt`, `EmailDetailContent.kt`, `EmailDetailTopBar.kt`, `EmailDetailStateContent.kt` e `ImageOverlays.kt`; no existe duplicación residual en `EmailDetailScreen.kt`.
- Ningún componente nuevo referencia AppContainer, ViewModel o repositorios.
- Firma pública `EmailDetailScreen(emailId, onBack, onReply, onForward, modifier)` intacta; no se introdujeron APIs públicas nuevas.
- `EmailBodyWebView.kt` `83cf07eb...` ✓, `PdfAttachmentSection.kt` `e0934ce4...` ✓ e `ImageUtils.kt` `cea09ff8...` ✓ permanecen intactos.
- `MainActivity.kt`: `a8275404afe60158d08616487124020b64d6aa1df2cb0f02f4c56c1d3b52cd55` ✓.
- `SearchScreen.kt`: `3966a9feace5bbae418969414e7a543c26ee4909a0914ddc75eee450401d89b3` ✓.
- Diff combinado protegido: `84adbdbeee0dd263c5b3ab94b56b996dd5adf51c08a2f60c6e2f2f7bbb9c224e` ✓.
- Los dos archivos ajenos permanecen sin stagear y excluidos del commit.

### 26.4 Cierre

- [x] Subfases 3.1, 3.2, 3.3 y 3.4 aprobadas.
- [x] Suite JVM completa, compilación, APK e instrumentación consolidadas verdes.
- [x] Smoke funcional y comparación visual respaldados por los gates de cada subfase y la evidencia baseline congelada.
- [x] Arquitectura conservadora: componentes visuales sin infraestructura y pantalla pública intacta.
- [x] Alcance del commit limitado a los archivos propios de Etapa 3 y este registro.

**Resultado final: Etapa 3 APROBADA el 2026-08-06.**
**Commit previsto: `refactor(emaildetail): extract presentation components`.**
**Siguiente paso después del commit: Etapa 4, Subfase 4.1 — Fachada pública y Route interna.**

---

## 27. Subfase 4.1 — Fachada pública y Route interna

Plan cerrado: `Etapa 4 Separar Route y UI/Subfase 4.1.md` (2026-08-06).

### 27.1 Cambios de implementación

- **`EmailDetailScreen.kt`** reducido a la fachada pública exacta (firma
  idéntica al baseline, incluido `@OptIn(ExperimentalMaterial3Api::class)`);
  delega con argumentos nombrados en `EmailDetailRoute`, sin transformar
  `emailId`, callbacks ni `modifier`.
- **`EmailDetailRoute.kt`** (nuevo, 349 líneas): traslado mecánico 1:1 del
  cuerpo anterior de la pantalla como `internal fun EmailDetailRoute`:
  - Acceso a `AppContainer.emailRepository` y `AppContainer.stringProvider`.
  - Creación de `RepositoryEmailDetailSource` y del ViewModel con
    `key = emailId` y la misma factory.
  - Dos recolecciones `collectAsStateWithLifecycle` (uiState, pdfDownloadStates).
  - `EmailDetailReadFailureEffect`.
  - `traceMail`, `UI_SCREEN_ENTER`, `UI_SCREEN_DISPOSE` y `UI_STATE_CHANGED`.
  - Coordinación PDF (etiquetas, launcher SAF, guardado `savingStableIds`,
    colección de `pdfOpenEvents`), estados visuales, `BackHandler`, `Scaffold`
    y componentes actuales, todavía sin separarlos.
- El `private const val TAG = "EmailDetailScreen"` y los imports requeridos
  se movieron al archivo Route. El import muerto preexistente
  `android.app.Activity` (sin uso en el cuerpo desde las extracciones de
  Etapa 2) no se trasladó; la compilación lo confirma.
- Cero cambios en `MainNavHost.kt`, ViewModel, source, repositorio,
  AppContainer, navegación, UiState, WebView, PDF, componentes, recursos y
  modelos.

### 27.2 Verificación estática (2026-08-06)

- Una única definición pública de `EmailDetailScreen` → `EmailDetailScreen.kt:9`.
- Una única definición interna de `EmailDetailRoute` → `EmailDetailRoute.kt:56`.
- Una única creación de source y ViewModel, dentro de Route →
  `EmailDetailRoute.kt:64-65`.
- `key = emailId` intacta → `EmailDetailRoute.kt:66`.
- Dos recolecciones `collectAsStateWithLifecycle` → `EmailDetailRoute.kt:69-70`.
- Efecto de lectura (`EmailDetailReadFailureEffect`) y trazas
  (`UI_SCREEN_ENTER`/`UI_SCREEN_DISPOSE`/`UI_STATE_CHANGED`) presentes
  únicamente en Route → `EmailDetailRoute.kt:77, 210, 212, 230`.
- La fachada no importa ni referencia AppContainer, ViewModel, lifecycle,
  repositorio ni infraestructura (grep sin coincidencias).
- Cero cambios en navegación y en los componentes extraídos durante Etapa 3
  (`git status`: solo `EmailDetailScreen.kt` modificado + `EmailDetailRoute.kt`
  nuevo, además de los dos archivos ajenos protegidos sin stagear).

### 27.3 Validación automatizada (2026-08-06)

- `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL (solo warnings
  preexistentes de Color.kt y deprecaciones; ninguno atribuible al refactor).
- Traslado mecánico verificado por compilación y por `git diff --numstat`
  (`EmailDetailScreen.kt`: 333 eliminaciones, 6 adiciones y reducción neta
  de 327 líneas; se retiraron el cuerpo, el TAG y los imports trasladados).
- `./gradlew testDebugUnitTest` (filtro detalle/PDF/imágenes) → BUILD
  SUCCESSFUL, **166 pruebas, 0 fallos, 0 errores, 0 omitidas**
  (superconjunto que cubre las 93 relevantes del plan):
  - `EmailDetailViewModelPdfTest` 26, `PdfAttachmentFormattingTest` 35,
    `EmailDetailViewModelResolutionTest` 15, `ImageUtilsTest` 12,
    `EmailHtmlCleanerTest` 9, `EmailDetailViewModelTest` 7,
    `EmailReadOnOpenCoordinatorTest` 6, `EmailDetailContractsTest` 5,
    `EmailReadOnOpenGateTest` 3, `PdfSaveFileCopyTest` 2,
    `PdfCacheManagerTest` 22, `GmailPdfAttachmentParserTest` 15,
    `PdfAttachmentMetadataCodecTest` 7, `EmailEntityPdfMetadataTest` 2.
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- `git diff --check` → sin errores.
- 2026-08-06: `./gradlew testDebugUnitTest` sin filtro → BUILD SUCCESSFUL,
  **573 pruebas, 0 fallos, 0 errores, 0 omitidas** (superconjunto de las 166
  relevantes, sin regresiones en el resto de suites).

### 27.4 Hashes protegidos (2026-08-06)

- `MainActivity.kt`:
  `a8275404afe60158d08616487124020b64d6aa1df2cb0f02f4c56c1d3b52cd55` ✓
  (coincide con baseline).
- `SearchScreen.kt`:
  `3966a9feace5bbae418969414e7a543c26ee4909a0914ddc75eee450401d89b3` ✓
  (coincide con baseline).
- Diff conjunto protegido: `84adbdbeee0dd263c5b3ab94b56b996dd5adf51c08a2f60c6e2f2f7bbb9c224e` ✓
  (verificado 2026-08-06 con `git diff -- MainActivity.kt SearchScreen.kt | shasum -a 256`).

### 27.5 Instrumentación — ejecutada (2026-08-06)

Ejecutada en emulador `Medium_Phone_API_36.1` (`ANDROID_SERIAL=emulator-5554`)
con `connectedDebugAndroidTest` y filtro de clases:

- `EmailDetailIntegrationTest` — 11 tests.
- `EmailDetailCancellationTest` — 7 tests.
- `EmailDetailReadFailureEffectTest` — 1 test.
- `MainNavigationTest` — 9 tests.

Resultado: **28 tests, 0 fallos, 0 errores, 0 omitidos** — BUILD SUCCESSFUL.

### 27.6 Smoke en Pixel 9 — ejecutado (2026-08-06)

- Fixture abierto desde la navegación existente: contenido idéntico al
  baseline (HTML, inline 512×512, 2 PDFs, header) y transición
  Loading → Ready.
- Responder, reenviar y Back: funcionan correctamente.
- Salir y reabrir el mismo correo: traza `MailRenderTrace` confirma la misma
  key (`mail=9070275a`) en ambas aperturas, con ciclo de vida completo
  (`UI_SCREEN_ENTER` → `UI_STATE_CHANGED` Loading/Ready → `UI_SCREEN_DISPOSE`
  → reapertura `UI_SCREEN_ENTER` con el mismo mailKey; `VM_PDF_CACHE_CHECK
  count=2`, inline resuelto y WebView renderizado).

### 27.7 Criterios de aceptación (Subfase 4.1)

- [x] Fachada pública idéntica y delegación con argumentos nombrados.
- [x] Route conserva la coordinación sin cambios (traslado mecánico 1:1).
- [x] Búsqueda estática completa (sección 27.2).
- [x] Suite JVM de detalle/PDF/imágenes verde.
- [x] `compileDebugKotlin`, `assembleDebug --rerun-tasks` y `git diff --check` verdes.
- [x] Instrumentación (4 suites) verde.
- [x] Smoke en Pixel 9 con misma key y ciclo de vida.
- [x] Hash del diff conjunto protegido verificado.

La subfase queda aprobada únicamente cuando los puntos pendientes anteriores
terminen en verde; entonces se actualizará el estado en la sección 2 y se
procederá a la Subfase 4.2. No se crea commit hasta cerrar 4.1–4.3 y la
auditoría de Etapa 4.

---

## 28. Subfase 4.2 — Contrato de presentación y efectos PDF

Plan cerrado: `Etapa 4 Separar Route y UI/Subfase 4.2.md` (2026-08-06).

### 28.1 Cambios de implementación

- **`EmailDetailPresentation.kt`** (nuevo, 172 líneas): `internal fun
  EmailDetailPresentation` — presentación visual pura. Recibe `uiState`,
  `pdfDownloadStates`, `savingStableIds`, `traceMail`, `snackbarHostState`,
  callbacks de navegación/acción y `modifier`. Contiene el estado overlay
  (`activeImageUrl`, `showFullscreenImage`, `showDetailsPanel`), `BackHandler`,
  `Scaffold` con `EmailDetailTopBar`, matriz de estados (`Loading`,
  `ResolutionError`, `BodyError`, `PreparingBody`/`Ready`), encabezado
  flotante con scrim, `ImageActionSheet` y `FullscreenImageDialog`. Cero
  infraestructura: sin AppContainer, ViewModel, repositorio, lifecycle, Intent,
  SAF, ContentResolver ni FileProvider. Tipos importados: `PdfDownloadState`,
  `PdfAttachmentMetadata`.

- **`EmailDetailPdfEffects.kt`** (nuevo, 159 líneas): `internal fun
  rememberEmailDetailPdfEffects(viewModel, lifecycleOwner, snackbarHostState):
  State<Set<String>>` — coordinador PDF como único propietario de:
  - El TAG `"EmailDetailScreen"`.
  - `savingStableIds`, `savingState`, `savedSaveEmailId`,
    `savedSaveStableId`, `savedSaveDisplayName`.
  - `pdfLabels` (7 recursos) y `defaultPdfFilename`.
  - `savePdfLauncher` (`CreateDocument` SAF + copia a URI + limpieza parcial +
    snackbars).
  - `LaunchedEffect` con `repeatOnLifecycle(STARTED)` para colección de
    `pdfOpenEvents` (Open/Save + excepciones `ActivityNotFoundException` /
    `SecurityException`).

- **`EmailDetailRoute.kt`** reducido de 349 a 91 líneas. Conserva:
  - ViewModel con `key = emailId`, 2 `collectAsStateWithLifecycle`.
  - `EmailDetailReadFailureEffect`.
  - Llamada a `rememberEmailDetailPdfEffects` y `EmailDetailPresentation`.
  - Trazas `UI_SCREEN_ENTER` / `UI_SCREEN_DISPOSE` / `UI_STATE_CHANGED`.
  - Sin TAG, sin BackHandler, sin Scaffold, sin overlays, sin infraestructura
    PDF.

- Cero cambios en `EmailDetailScreen.kt`, navegación, ViewModel, source,
  repositorio, AppContainer, componentes de Etapa 3, recursos, WebView ni
  modelos.

### 28.2 Verificación estática (2026-08-06)

- Presentation sin AppContainer/ViewModel/repositorio/lifecycle/Intent/SAF/
  ContentResolver/FileProvider (grep limpio, solo `savingStableIds` como
  parámetro recibido).
- Route con 2 `collectAsStateWithLifecycle`, `key = emailId`,
  `EmailDetailReadFailureEffect` y trazas. Sin BackHandler/Scaffold/overlays.
- PdfEffects único propietario del launcher, `savedSave*`,
  `repeatOnLifecycle`, `pdfOpenEvents`, `savingStableIds`, `pdfLabels`,
  `savePdfLauncher`, `copyFileToUri`, `handlePdfExternalActionRequest`,
  `buildPdfSuggestedName`.
- Una sola composición de `Scaffold` y `BackHandler` (en Presentation).
- API pública (`EmailDetailScreen`) y navegación sin cambios.

### 28.3 Validación automatizada (2026-08-06)

- `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL (solo warning preexistente
  de `LocalLifecycleOwner`).
- `./gradlew testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL,
  **573 pruebas, 0 fallos, 0 errores, 0 omitidas**.
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- `git diff --check` → sin errores.
- Hash del diff conjunto protegido:
  `84adbdbeee0dd263c5b3ab94b56b996dd5adf51c08a2f60c6e2f2f7bbb9c224e` ✓.

### 28.4 Instrumentación (2026-08-06)

Ejecutada en emulador `Medium_Phone_API_36.1` (`ANDROID_SERIAL=emulator-5554`):

| Suite | Tests | Resultado |
|---|---|---|
| EmailDetailIntegrationTest | 11 | ✅ |
| EmailDetailCancellationTest | 7 | ✅ |
| EmailDetailReadFailureEffectTest | 1 | ✅ |
| EmailDetailStateContentTest | 3 | ✅ |
| ImageOverlaysTest | 4 | ✅ |
| MainNavigationTest | 9 | ✅ |
| PdfCancellationContractsTest | 2 | ✅ |
| **Total** | **37** | **0 fallos** |

### 28.5 Smoke en Pixel 9 (2026-08-06)

- **Paso 1**: Loading → Ready, encabezado, cuerpo, imágenes, responder,
  reenviar, Back — funcionan correctamente ✅.
- **Paso 2**: Descarga y apertura de PDF (baseline-small.pdf) ✅.
- **Paso 3**: Guardado SAF exitoso + cancelación SAF sin estado residual ✅.
- **Paso 4**: Expiración manual de caché → estado Idle + snackbar «caché
  expirado» ✅.
- **Paso 5**: «No conservar actividades» → selector SAF se reabre solo tras
  recreación, sin snackbar ni errores; configuración restaurada ✅.
- **Paso 6**: Salir durante descarga (Back) → sin archivo parcial, estado
  Idle ✅.

### 28.6 Criterios de aceptación (Subfase 4.2)

- [x] Contratos internos cerrados sin duplicación residual.
- [x] Presentation sin infraestructura.
- [x] PdfEffects como único propietario de launcher, savedSave*,
  colección de eventos y savingStableIds.
- [x] Route con 2 collectors, key=emailId, ReadFailureEffect y trazas.
- [x] Una sola composición de Scaffold, BackHandler y overlays.
- [x] API pública y navegación sin cambios.
- [x] Suite JVM completa verde (573 tests, 0 fallos).
- [x] `compileDebugKotlin`, `assembleDebug --rerun-tasks` y `git diff --check`
  verdes.
- [x] Instrumentación (7 suites, 37 tests) verde.
- [x] Smoke en Pixel 9 con los 6 pasos completados.
- [x] Hash del diff conjunto protegido verificado.

La subfase queda aprobada. No se crea commit hasta cerrar 4.1–4.3 y la
auditoría de Etapa 4 (commit previsto:
`refactor(emaildetail): separate route from presentation`).

---

## 29. Subfase 4.3 — Pruebas de caracterización de presentación

Plan cerrado: `Etapa 4 Separar Route y UI/Subfase 4.3.md` (2026-08-06).
Subfase implementada por un agente IA; auditoría consolidada de Etapa 4 y
commit reservados para el agente auditor.

### 29.1 Cambios de implementación

- Único archivo creado:
  `app/src/androidTest/java/com/david/mailapp/feature/emaildetail/EmailDetailPresentationTest.kt`
  (467 líneas) con **8 pruebas** que caracterizan directamente
  `EmailDetailPresentation`:
  1. `loading_showsProgressAndDisablesReplyForward`
  2. `retryableResolutionError_showsMessageAndForwardsRetry`
  3. `nonRetryableResolutionError_hidesRetry`
  4. `bodyErrorWithoutAttachments_showsErrorWithoutPdfActions`
  5. `bodyErrorWithAttachment_forwardsPdfCallbacksAndSavingState`
  6. `replyForwardAndBack_areForwardedOnlyInReady`
  7. `expandedHeader_consumesBackBeforeOuterHandler`
  8. `imageLongPress_opensActionMenuAndFullscreen`
- Fixtures/probes privados del archivo: `testEmail(...).copy(...)`,
  `SnackbarHostState` recordado, `TRACE_KEY` fija sin datos reales, y
  `WaitForWebViewProgress` (ViewAction de Espresso que espera
  `WebView.progress == 100` con url cargada, 15 s de timeout).
- Cero cambios de producción: sin `testTag`/semantics/hooks nuevos en
  `app/src/main`, API pública y navegación intactas.

### 29.2 Verificación (2026-08-06)

- `./gradlew compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL.
- `./gradlew testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL,
  **573/573 pruebas, 0 fallos**.
- `./gradlew assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- Instrumentación en emulador `Medium_Phone_API_36.1`
  (`ANDROID_SERIAL=emulator-5554`), 8 suites → **45/45 tests, 0 fallos**:

| Suite | Tests | Resultado |
|---|---|---|
| **EmailDetailPresentationTest (nueva)** | **8** | ✅ |
| EmailDetailIntegrationTest | 11 | ✅ |
| EmailDetailCancellationTest | 7 | ✅ |
| EmailDetailReadFailureEffectTest | 1 | ✅ |
| EmailDetailStateContentTest | 3 | ✅ |
| ImageOverlaysTest | 4 | ✅ |
| MainNavigationTest | 9 | ✅ |
| PdfCancellationContractsTest | 2 | ✅ |
| **Total** | **45** | **0 fallos** |

- Incidencia transitoria documentada: en la **primera** ejecución
  `inlineCancellationKeepsPendingReadyStateAndDoesNotWriteFallback` falló por
  `TimeoutCancellationException` (5 s esperando `inlineImagesCalls == 1`).
  Verificado que no es regresión: producción no modificada en 4.3 (hashes de
  entrada intactos), el test pasa aislado y en la re-ejecución completa
  45/45. Se atribuye a latencia del emulador recién arrancado bajo carga.
- `git diff --check` → sin errores.

### 29.3 Búsqueda estática

- `EmailDetailPresentation.kt` sin AppContainer, ViewModel, repositorio,
  lifecycle, Intent, SAF, ContentResolver, FileProvider ni launchers (grep
  sin coincidencias).
- Cero `testTag`/hooks nuevos en `app/src/main` (solo preexistentes en
  navigation/trash; ninguno en emaildetail).
- Ningún archivo de producción modificado durante 4.3 (`git status`: solo el
  nuevo archivo de test añadido).

### 29.4 Hashes de entrada protegidos

| Archivo | Hash esperado | Verificado |
|---|---|---|
| `EmailDetailScreen.kt` | `cbf868c9…03d65` | ✅ |
| `EmailDetailRoute.kt` | `88c4cbe5…f91c` | ✅ |
| `EmailDetailPresentation.kt` | `547e4ff6…f4022` | ✅ |
| `EmailDetailPdfEffects.kt` | `9dfddcf7…4c040` | ✅ |
| Diff MainActivity + SearchScreen | `84adbdbe…c224e` | ✅ |

### 29.5 Cierre

- Subfase 4.3 marcada **Aprobada** en la tabla de estado.
- **Etapa 4 permanece En curso** — pendiente de la auditoría consolidada
  externa.
- No se modifican los resultados registrados de 4.1 ni 4.2.
- No se ejecuta la auditoría de Etapa 4 ni se hace stage/commit (reservados
  al agente auditor, commit previsto:
  `refactor(emaildetail): separate route from presentation`).

---

## 30. Auditoría consolidada y cierre de Etapa 4

**Estado: APROBADA** (2026-08-06).

### 30.1 Arquitectura y alcance

- `EmailDetailScreen.kt` conserva la fachada pública y su firma original; su
  única responsabilidad es delegar con argumentos nombrados en
  `EmailDetailRoute`.
- `EmailDetailRoute.kt` concentra ViewModel, repositorio/fuente, colecciones
  ligadas al ciclo de vida, trazas y conexión entre efectos y presentación.
- `EmailDetailPresentation.kt` contiene la UI pura y permanece libre de
  AppContainer, ViewModel, repositorio, lifecycle, Intent, SAF,
  ContentResolver, FileProvider y launchers.
- `EmailDetailPdfEffects.kt` es el único coordinador de los efectos PDF:
  launcher SAF, apertura/guardado, caché, estados persistibles y consumo de
  eventos.
- La extracción conserva el orden de efectos y la matriz visual de estados,
  sin cambios en navegación, ViewModel, fuente, manejador PDF ni componentes
  extraídos en la Etapa 3.
- La Subfase 4.3 añade ocho pruebas directas de presentación sin incorporar
  tags, semantics ni hooks de prueba en producción.

### 30.2 Gates independientes del agente auditor

- `./gradlew testDebugUnitTest assembleDebug --rerun-tasks` → BUILD
  SUCCESSFUL: **573/573 pruebas JVM, 0 fallos**, y APK debug ensamblado
  correctamente (25.642.958 bytes).
- Instrumentación consolidada en `Medium_Phone_API_36.1`, con las ocho suites
  de 4.3 → **45/45 pruebas, 0 fallos ni omitidas** en la primera ejecución de
  la auditoría independiente.
- `git diff --check` → limpio.
- Las advertencias de compilación observadas son deprecaciones preexistentes;
  no constituyen errores ni una regresión de la Etapa 4.

### 30.3 Clasificación de la incidencia transitoria

- El timeout informado durante la primera ejecución del implementador en
  `inlineCancellationKeepsPendingReadyStateAndDoesNotWriteFallback` no es
  atribuible a la Etapa 4: la ruta de producción implicada no fue modificada,
  el test pasó aislado, pasó en la repetición completa del implementador y
  volvió a pasar dentro de las **45/45** pruebas de la auditoría independiente.
- Se clasifica como flakiness puntual por latencia del emulador recién
  iniciado bajo carga, sin evidencia de regresión funcional.

### 30.4 Evidencia manual y protección de cambios ajenos

- Se revisó la evidencia de smoke de la Subfase 4.2 en Pixel 9: transición
  Loading→Ready, apertura/descarga PDF, guardado y cancelación SAF, expiración
  de caché, recreación de Activity y salida durante descarga.
- Tras la prueba de recreación quedaron restaurados
  `always_finish_activities=0` y las escalas de animación de ventana,
  transición y animator en `1.0`.
- El hash conjunto protegido de `MainActivity.kt` y `SearchScreen.kt` sigue
  siendo `84adbdbeee0dd263c5b3ab94b56b996dd5adf51c08a2f60c6e2f2f7bbb9c224e`.
  Ambos cambios del usuario quedan fuera del staging y del commit de esta
  etapa.

### 30.5 Cierre

- Subfases 4.1, 4.2 y 4.3: **Aprobadas**.
- Auditoría consolidada de Etapa 4: **Aprobada**.
- Etapa 4 marcada **Aprobada** en la tabla de estado.
- Staging limitado a la fachada, Route, Presentation, coordinador PDF, prueba
  de caracterización y este registro técnico.
- Commit de cierre previsto:
  `refactor(emaildetail): separate route from presentation`.
