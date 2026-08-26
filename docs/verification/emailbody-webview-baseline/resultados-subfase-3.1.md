# Resultados — Subfase 3.1: matriz de carga y documento

## 1. Estado

**COMPLETADA — GO CON DEFECTO CONOCIDO.** Todas las puertas automatizadas
están aprobadas (serie focal 54/54, suite completa 302/302 y contratos de
traza 12/12). La revisión visual posterior cubrió los 11 PNG: ocho son
conformes y tres (`S04`, `S05`, `S11`) reproducen un overflow horizontal de
la fixture F02. Se acepta expresamente como defecto conocido del baseline
actual: la aprobación certifica fidelidad de la evidencia, no corrección
visual del comportamiento. No se modificó producción para ocultarlo.

## 2. Identidad

- **Fecha local de ejecución**: 2026-08-25 (CST, `-0600`).
- **Rama**: `main`; **HEAD**: `2a433a67af4ea6f4f27e320133c963a408e80453`.
- **Hashes protegidos** (verificados antes de editar y al cierre):
  - `EmailBodyWebView.kt`: `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`;
  - `ComposeScreen.kt`: `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69`;
  - `MainNavHost.kt`: `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.

## 3. Precondiciones verificadas

- HEAD y los tres hashes protegidos coinciden con el baseline; producción,
  Gradle y cambios ajenos intactos.
- 2.3 conserva su cierre GO; la suite 2.3 sigue verde dentro de las corridas.
- Ajustes test-only de 2.2-R sin revertir; fixtures canónicas sin cambios.

## 4. Dispositivo

- **Serial**: `emulator-5554` (único dispositivo; Pixel 9 excluido).
- **AVD**: `Medium_Phone_API_36.1`, Android release 16 / SDK `36`,
  orientación vertical, resolución **1080×2400** (`wm size`).
- **RAM del AVD**: elevada de 2048 MB a **4096 MB** en `config.ini`
  (`hw.ramSize`) a mitad de subfase por degradación severa del rendimiento
  (ver Incidencias, I-5). La identidad contractual del AVD no cambia.

## 5. Implementación

Solo se amplió `EmailBodyWebViewBaselineTest.kt` (de 6 a **18 pruebas**; se
añadieron los 12 escenarios S01–S08 y S10–S13). No se creó otra suite ni se
añadieron dependencias. Helpers test-only añadidos:

- `clearLogcat()` — limpieza de Logcat vía `UiAutomation` antes de cada
  escenario.
- `waitForRendered`/`waitForRenderedCount` — espera observable
  (progress==100, URL no nula, callback) sin sleeps ciegos; timeout 45 s por
  emulador lento.
- `waitForTrace()` — espera observable de una traza concreta en Logcat
  (avance de frame + drenado de fd), usada por S01 para eliminar la carrera
  de arranque.
- `captureAndSave`/`assertNativeCapture` — `UiAutomation.takeScreenshot()`,
  PNG nativo 1080×2400 con verificación de magia, tamaño y dimensiones.
- `saveTrace()` — extracción de Logcat (`-v threadtime -s MailRenderTrace`)
  filtrada por `mail=Sxx_3_1`, guardada íntegra.
- `publishEvidence()` — copia a `/data/local/tmp/emailbody-3.1/` (ubicación
  persistente en el dispositivo; ver Incidencias I-3/I-4) con verificación
  byte a byte (drenado de fd del `cp` para evitar carreras).
- Montaje con `MailAppTheme(palette=Blue, useDynamicColor=false,
  darkTheme=isDark)` y las fixtures AndroidTest existentes. Sin
  `evaluateJavascript`, repositorios, red pública, hooks ni parámetros de
  producción.

## 6. Compilación AndroidTest independiente

- Comando: `./gradlew compileDebugAndroidTestKotlin --rerun-tasks
  --console=plain`.
- Verificación independiente posterior a la implementación: **BUILD
  SUCCESSFUL in 29s**, 30 tareas ejecutadas.
- Solo se observaron warnings de deprecación preexistentes; no hubo errores de
  compilación ni cambios de código derivados de esta comprobación.

## 7. Incidencias (clasificadas)

- **I-1 — Adjunt de instrumentación intermitente**: `failed to attach`
  (proceso matado por ActivityManager a los ~10 s de spawn) en el AVD
  degradado con 2 GB. Mitigado con la elevación de RAM (I-5) y limpieza del
  guest (`am kill-all`, `force-stop wellbeing`, animaciones a 0). Reintentos
  conforme al protocolo del plan.
- **I-2 — Crash nativo de RenderThread post-evidencia**: SIGSEGV en
  `eglDestroySurface`/`libhwui` al destruir la superficie del test
  (emulador). Flaky; la evidencia queda publicada antes del teardown. Un
  reintento de la corrida la absorbe.
- **I-3 — Directorio externo del paquete de test inaccesible**:
  `AndroidJUnitRunner` ejecuta la instrumentación **en el proceso de la app
  objetivo** (`com.david.mailapp`); los directorios del paquete de test
  (`getExternalFilesDir`/`filesDir` del `context`) no son escribibles desde
  ese proceso. Se documentó con el uid del proceso (u0_aXXX de la app) frente
  a los uids de los paquetes. La evidencia se escribe en el directorio externo
  de la app objetivo y se publica en `/data/local/tmp` (persistente).
- **I-4 — Desinstalación de APKs tras cada corrida**: AGP desinstala la app
  y el test al terminar `connectedDebugAndroidTest`, borrando cualquier
  evidencia en almacenamiento de la app. Por eso la evidencia vive en
  `/data/local/tmp` y se extrae tras la corrida 3.
- **I-5 — Emulador extremadamente lento / thrashing con 2 GB**: cargas de
  WebView atascadas en `progress=10/70` dentro del timeout de 20 s, ~1
  min/test. Solución: RAM del AVD a 4096 MB y timeouts de espera a 45 s. Tras
  el cambio, la corrida focal baja de ~9 min a ~2.5 min y los atascos
  desaparecen.
- **I-6 — Carrera de arranque en S01**: el `LaunchedEffect(null)` y el apply
  inicial del `AndroidView.update` pueden cancelarse si el body llega antes de
  que corran los efectos (dispatcher StandardTestDispatcher). Solución:
  `waitForTrace` para `HTML_BUILD_WAITING reason=body_pending` antes de
  cambiar el body, y aceptación de `WV_UPDATE action=wait` con
  `reason=body_pending` **o** `html_pending` (ambas demuestran la espera sin
  carga previa; la elección de una u otra depende del momento del apply del
  montaje — verificado: en solitario aparece `body_pending`, en suite completa
  a veces `html_pending`). La evidencia aceptada (corrida 3) contiene
  `body_pending`.

## 8. G25 — Serie focal (EmailBodyWebViewBaselineTest, 18 pruebas)

Comando literal de las tres corridas:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.emaildetail.components.EmailBodyWebViewBaselineTest
```

| Corrida | Resultado | Fallos/Errores/Omitidas | Duración Gradle | SHA-256 XML |
|---:|---:|---:|---:|---|
| 1 | 18/18 | 0 / 0 / 0 | 5m24s | `3eaf1650609eb37a1dc92af95d4c62cef26f8548cda9315ff4ff9bcc8eb82e64` |
| 2 | 18/18 | 0 / 0 / 0 | 2m22s | `2c2e53ae1b2fd5b74d164e35011e008d49a2d7b25ebc1100f85f36447e85f5e3` |
| 3 | 18/18 | 0 / 0 / 0 | 2m32s | `2a921fb6995dda09851bf3345b06abe0de5adf27d778999ab87feb5a843a8998` |

Total: **54/54**. La corrida 1 fue la primera limpia (tras I-1/I-2/I-5); la
corrida 2 y la 3 se lograron tras los ajustes. Los XML de las corridas
descartadas (fallos de infraestructura) no se conservan como evidencia.
Dispositivo reportado en los tres XML: `Medium_Phone_API_36.1(AVD) - 16`.

## 9. G26 — Suite completa

- Comando: `ANDROID_SERIAL=emulator-5554 ./gradlew
  connectedDebugAndroidTest --rerun-tasks --console=plain`.
- Primera ejecución: 302 tests con 1 fallo (S01, incidencia I-6), no
  conservada.
- Ejecución aceptada: **302/302, 0 failures, 0 errors, 0 skipped**,
  `BUILD SUCCESSFUL in 8m 50s`, 27 clases.
- XML: `reportes-subfase-3.1/completa.xml`, SHA-256
  `8af5691b7b93e6bdc099ba771ceacd032755dc9a93830744a157cfec46d8f658`.

## 10. Evidencia aceptada (corrida 3)

Extraída de `/data/local/tmp/emailbody-3.1/` tras la corrida 3:
**11 PNG en `capturas/` y 12 logs en `trazas/`** (23 archivos).

| Archivo | SHA-256 |
|---|---|
| capturas/S01-null-a-simple-claro.png | `0e0e6b3b296c4bfb9ab06269c06ff1c8c45be005d946f2708e8cd04f172dec03` |
| capturas/S02-simple-claro.png | `0e0e6b3b296c4bfb9ab06269c06ff1c8c45be005d946f2708e8cd04f172dec03` |
| capturas/S03-simple-oscuro.png | `07502ca025ec31645a60821c27c8f794fa6584462d19c495ce9a025c889b57fc` |
| capturas/S04-newsletter-claro.png | `2d59cd50cbf177a87b940cebc6cd3eeb4bd9d3af8a838d669b9676b2fb228721` |
| capturas/S05-newsletter-oscuro.png | `513252d2f7d8baaf8f288f572813ac6c6dbe10247c785a38f4da471f6b6de2af` |
| capturas/S06-remota-habilitada.png | `4ecd16f7fa7ad6b83daf3145bf1da2b7dfdea74de0090d06c58460af0f7a9492` |
| capturas/S07-remota-bloqueada.png | `12188aba27787f592f25dee15d1c0f7ef35182f4054abb09abdbfaf0968ee646` |
| capturas/S08-data-bloqueo-remoto.png | `015f8cd8aac654fc4c784b46c9e0bfc9a72d0bda71ebbdc2b7622d04e5f8fc6c` |
| capturas/S11-cambio-body.png | `26e12f0ed5d948a51eb5f7ac233de8feb0868dccc49d0fc6de2ed640850a4031` |
| capturas/S12-cambio-tema.png | `07502ca025ec31645a60821c27c8f794fa6584462d19c495ce9a025c889b57fc` |
| capturas/S13-cambio-politica-imagenes.png | `12188aba27787f592f25dee15d1c0f7ef35182f4054abb09abdbfaf0968ee646` |
| trazas/S01-null-a-simple-claro.log | `7b501050dfbdddc3cca6c2826055838957dcbff498cdafde7a1751e6e6304499` |
| trazas/S02-simple-claro.log | `56c6b347e2ab0c8da62ecb5306112d9481c5f9d900cd91719d2419453c444199` |
| trazas/S03-simple-oscuro.log | `de05f01f19db6037510af27f30e3b1f5511a13a6aaa10d14a040e6fd4b427b30` |
| trazas/S04-newsletter-claro.log | `61400be37f45f11fba8e756bc8145489d76298e774f77dfa01fb103f668d6096` |
| trazas/S05-newsletter-oscuro.log | `47d69954009b79ada9e9e4fe18a1a8ff0ac37066026ba7b866596b2027faba6e` |
| trazas/S06-remota-habilitada.log | `dc11ed2fcc38b1adc41ba04163b0f546379c1747de197332afbc588b36c33b56` |
| trazas/S07-remota-bloqueada.log | `abab69cce475eaadd46cb2fa7c08e83c36c2cdd152acb9f209a7338a5774603b` |
| trazas/S08-data-bloqueo-remoto.log | `ecc071b3af50021f361183ccc4f427722124cfa4046eea97de625203cd1477b8` |
| trazas/S10-recomposicion-equivalente.log | `759e9f902417d27f01e08049e3ec364a62f5115864740a92b5f1f8017cf966ef` |
| trazas/S11-cambio-body.log | `87ed93eebd4dc4e89b2a1da301c007137ebf3340ca280bfd870f2c2b0eee8fe8` |
| trazas/S12-cambio-tema.log | `3c54bb3ab9051214d417e7de57896882c3b50e33687ecfadaed2fd4a4c7c2fcb` |
| trazas/S13-cambio-politica-imagenes.log | `62c11b09fc0f0c12f6d2c0a5875b0da1eeb9af06aa718a660cb81e0c4b3b3ee1` |

Nota: S01≡S02, S12≡S03 y S13≡S07 en sus PNG — los estados finales de las
transiciones reproducen exactamente los estados iniciales equivalentes, como
exige la matriz.

Verificaciones automáticas superadas:

- Formato PNG, dimensiones nativas **1080×2400** y tamaño > 0 en las 11
  capturas (dentro del test y con `file` en el host).
- Trazas con líneas completas sin editar (threadtime, tag `MailRenderTrace`,
  clave `mail=Sxx_3_1`).
- **Contrato de trazas: 12/12 OK** (script `trace_check`): secuencia canónica
  por loadKey (`HTML_BUILD_START → END → READY → WV_UPDATE action=load →
  WV_LOAD_DATA → WV_PAGE_RENDERED_DISPATCH`); S01 con `HTML_BUILD_WAITING
  reason=body_pending` + `WV_UPDATE action=wait` previos a la preparación; S10
  con un único `action=skip reason=already_loaded`; S11–S13 con dos ciclos y
  dos loadKeys distintos; cero `WV_PAGE_RENDERED_IGNORED`.
- Ausencia de datos sensibles: sin resultados para `Authorization`, `Bearer`,
  `access_token`, `refresh_token`, `@gmail.com`, `@outlook.com` en
  capturas/trazas.

## 11. Análisis visual cuantitativo (apoyo a la revisión, no sustituto)

| Captura | mean_lum | dark% | bright% | Lectura |
|---|---:|---:|---:|---|
| S01 / S02 (simple claro) | 248.1 | 0.38 | 99.4 | fondo claro, texto presente |
| S03 (simple oscuro) | 21.0 | 99.2 | 0.6 | fondo oscuro, texto claro |
| S04 / S11 (newsletter claro) | 246.9 | 0.76 | 98.7 | claro, más tinta (tabla) |
| S05 (newsletter oscuro) | 22.7 | 98.3 | 1.2 | oscuro, más tinta |
| S06 (remota habilitada) | 247.9 | 0.46 | 99.3 | claro; marcador de imagen |
| S07 / S13 (remota bloqueada) | 248.2 | 0.35 | 99.4 | claro; flujo colapsado |
| S08 (data, red bloqueada) | 248.8 | 0.16 | 99.7 | claro; área data 96×96 |
| S12 (tema final oscuro) | 21.0 | 99.2 | 0.6 | coincide con S03 ✓ |

Los estados finales de S11, S12 y S13 coinciden con los estados iniciales
equivalentes de la matriz (S04, S03 y S07 respectivamente).

El análisis cuantitativo confirma presencia de contenido y cambio de tema,
pero no detecta recortes horizontales; por eso no sustituye la inspección
visual documentada a continuación.

## 12. Revisión visual — COMPLETADA

Revisión realizada sobre los 11 PNG nativos preservados:

- **8 conformes**: S01, S02 y S03 muestran texto completo, acentos legibles,
  márgenes y contraste; S06 conserva el marcador remoto; S07 oculta la imagen
  y colapsa el flujo; S08 mantiene el área inline de 96×96 CSS px; S12 y S13
  representan correctamente sus estados finales.
- **3 con defecto conocido aceptado**: S04, S05 y S11 muestran F02 con
  overflow horizontal. La frase superior queda truncada a la derecha y la
  tercera columna de la tabla queda fuera del viewport, tanto en claro como
  en oscuro y tras el cambio de body.
- Las 11 capturas son evidencia real, sin notificaciones, datos personales,
  placeholders fabricados ni contenido ajeno.

La aceptación de S04/S05/S11 congela el comportamiento visual observado para
el refactor. No afirma que el diseño sea correcto y no autoriza corregirlo de
forma incidental: una mejora de tablas/viewport requiere un plan y una
aprobación propios.

## 13. Integridad y cierre

- Hashes protegidos: idénticos a la sección 2 al cierre.
- `git diff --check`: limpio.
- Estado Git: únicamente los cambios previstos (suite ampliada, 5 assets,
  documentación/reportes; cambios ajenos congelados y ajustes 2.2-R intactos).
  Se eliminó `.kotlin/sessions/` (artefacto de build).
- Sin commit (reservado para la Subfase 4.2).
- AVD activo en `emulator-5554` (RAM 4096 MB) — cambio documentado en I-5.

## 14. Decisión

**GO — Subfase 3.1 cerrada con defecto visual conocido F02; autorizada la
Subfase 3.2.** El overflow de S04/S05/S11 forma parte explícita del baseline y
debe preservarse o cambiarse únicamente mediante una decisión posterior
trazable.
