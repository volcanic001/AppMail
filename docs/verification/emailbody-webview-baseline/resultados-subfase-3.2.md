# Resultados — Subfase 3.2: matriz de interacción y lifecycle

## 1. Estado

**COMPLETADA — GO.** S09, S14, S15 y S16 cumplen sus contratos
automatizados y visuales. La serie focal terminó **66/66**, la suite completa
**306/306** y las cinco capturas de la corrida focal 3 fueron revisadas y
aprobadas. No se modificó producción, Gradle, fixtures ni evidencia histórica.

## 2. Identidad y precondiciones

- **Fecha local de ejecución**: 2026-08-25 (CST, `-0600`).
- **Rama**: `main`; **HEAD**:
  `2a433a67af4ea6f4f27e320133c963a408e80453`.
- **Dispositivo exclusivo**: `emulator-5554`, AVD
  `Medium_Phone_API_36.1`, Android 16 / API 36, vertical, **1080×2400** y
  4096 MB de RAM. El Pixel 9 físico permaneció excluido.
- Chrome estaba preparado como navegador HTTPS, sin pantalla de bienvenida,
  cuenta ni avisos pendientes.
- **Hashes protegidos** (verificados antes y después):
  - `EmailBodyWebView.kt`:
    `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`;
  - `ComposeScreen.kt`:
    `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69`;
  - `MainNavHost.kt`:
    `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.

## 3. Implementación test-only

`EmailBodyWebViewBaselineTest.kt` pasó de 18 a **22 pruebas** con cuatro
escenarios y helpers exclusivamente instrumentales:

- **S09** carga F05, activa el enlace mediante el árbol de accesibilidad y
  eventos táctiles reales, espera la Custom Tab de Chrome y contenido
  observable de `example.com`, vuelve y comprueba misma actividad, WebView,
  cliente, documento y un solo callback/ciclo de carga.
- **S14** construye F02 largo, fija `scrollY=1000`, sincroniza el frame,
  captura, envía la actividad a HOME, recupera la misma instancia con
  `SINGLE_TOP` y exige mismo WebView/loadKey/scroll, sin recarga. Además
  compara automáticamente el área de contenido de ambas capturas.
- **S15** hace long-press real sobre la imagen inline de F04 y exige una sola
  entrega de la URL `data:image/gif;base64,...` completa. El componente no
  emite evento Logcat propio para long-press; la aserción y el XML prueban el
  callback.
- **S16** desmonta F01, exige un único `WV_RELEASE` y ausencia de WebView;
  después remonta y exige otra instancia y cliente, dos cargas y dos
  callbacks.

La evidencia se escribe en el directorio externo de la app y se publica en
`/data/local/tmp/emailbody-3.2/`, para sobrevivir a la desinstalación que AGP
realiza al final de cada corrida. No se añadió `evaluateJavascript`, hooks,
flags, tags ni dependencias de producción.

## 4. Correcciones realizadas durante la revisión

La primera serie automatizada reportada no se aceptó porque dos capturas no
demostraban visualmente el estado afirmado:

1. **S09 capturaba el frame anterior del WebView** aunque ActivityManager y
   la traza ya indicaban que Chrome estaba en primer plano. Se cambió la
   evidencia de interacción a un `screencap` real del framebuffer y se espera
   la ventana accesible de Chrome. Una revisión intermedia detectó además una
   página aún blanca; la captura definitiva espera el texto observable
   `Example Domain`.
2. **S14 capturaba un frame obsoleto antes de pausar**. Se añadió una barrera
   `postVisualStateCallback` antes de cada captura y captura real del
   framebuffer. El test compara el área útil antes/después; en la evidencia
   aceptada ambos PNG son idénticos byte a byte.
3. En este AVD, BACK desde la actividad de prueba independiente puede llevar
   a HOME. S09 espera primero el retorno normal y, si Chrome dejó la tarea en
   segundo plano, trae al frente esa tarea existente con `SINGLE_TOP`. Las
   aserciones de identidad de actividad, WebView y cliente impiden que esto
   oculte una recreación.

Estas correcciones solo endurecen sincronización, captura y comprobaciones de
la suite; no alteran el comportamiento caracterizado.

## 5. Compilación

Comando:

```bash
./gradlew compileDebugAndroidTestKotlin --rerun-tasks --console=plain
```

Resultado: **BUILD SUCCESSFUL in 52s**. Solo hubo warnings de deprecación; no
errores de compilación.

## 6. Serie focal — 3 × 22

Comando literal de las tres corridas:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.emaildetail.components.EmailBodyWebViewBaselineTest
```

| Corrida | Resultado | Fallos/Errores/Omitidas | Duración Gradle | Tiempo XML | SHA-256 XML |
|---:|---:|---:|---:|---:|---|
| 1 | 22/22 | 0 / 0 / 0 | 2m42s | 100.677s | `7d0d80507dfdf3bce89f36d1ef2a5cefd10e0686e19d221900587b38a7109257` |
| 2 | 22/22 | 0 / 0 / 0 | 2m45s | 100.093s | `9de095d44e2d4349ccb2c1dc8dd24a69ee4a38bd2c7ebcfb7a38af9c8f021327` |
| 3 | 22/22 | 0 / 0 / 0 | 3m30s | 98.468s | `699844725cd12d6fa941a1bbc8054c762e9601db776baa9d8b3fcacdd1584f07` |

Total aceptado: **66/66**, cero crashes. Los XML están en
`reportes-subfase-3.2/focal-corrida-{1,2,3}.xml`. La serie anterior a las
correcciones visuales quedó descartada como evidencia de cierre.

## 7. Suite completa

- Comando: `ANDROID_SERIAL=emulator-5554 ./gradlew
  connectedDebugAndroidTest --rerun-tasks --console=plain`.
- Resultado: **306/306**, `0 failures`, `0 errors`, `0 skipped`,
  `BUILD SUCCESSFUL in 10m 59s`.
- Tiempo instrumental XML: 547.508 s.
- XML: `reportes-subfase-3.2/completa.xml`; SHA-256:
  `d3e4ad2283e4d502ee6f20fa8f60723b1115bd73b5cbcaa46779f983a3df2619`.

## 8. Contratos observados

- **S09**: un `WV_FACTORY`, un ciclo canónico build/load/dispatch, un
  callback, `WV_ON_PAUSE → WV_ON_RESUME`, mismo WebView/cliente y cero
  `WV_RELEASE` durante el viaje a Chrome.
- **S14**: un ciclo canónico y un callback; `WV_ON_PAUSE scrollY=1000`,
  `WV_ON_RESUME savedScrollY=1000` y `WV_RESUME_SCROLL_APPLIED scrollY=1000`;
  misma instancia/loadKey y cero liberaciones o cargas adicionales.
- **S15**: un ciclo/callback de render y exactamente un callback de imagen
  con la URL `data:` completa; ninguna traza propia de long-press, conforme al
  contrato documentado.
- **S16**: dos `WV_FACTORY` con identidades distintas, dos ciclos
  build/load/dispatch, dos callbacks y exactamente un `WV_RELEASE` entre las
  dos instancias.

Las cuatro trazas conservan líneas `threadtime` completas, tag
`MailRenderTrace` y claves `S09_3_2`, `S14_3_2`, `S15_3_2` y `S16_3_2`.

## 9. Evidencia aceptada — corrida focal 3

| Archivo | SHA-256 |
|---|---|
| `capturas/S09-enlace-custom-tab.png` | `e52a7ac149b2c1b5276f9e7cb194649f69202a61236e2a60de8c0a13aceea9d9` |
| `capturas/S14-scroll-antes-pausa.png` | `729de285ff02a4bd0013685f5ce6a7ca39a1b98a32e5d1970651c66ca1b28215` |
| `capturas/S14-scroll-despues-resume.png` | `729de285ff02a4bd0013685f5ce6a7ca39a1b98a32e5d1970651c66ca1b28215` |
| `capturas/S15-long-press-data.png` | `845a970ce25a5401e7733819e0c681bf805af96207321874edfcc05c275c60c7` |
| `capturas/S16-reapertura.png` | `b0d5b08a384ee10db36c76439a90fb44f3e812ec9f4d44877c41000c014c0b7b` |
| `trazas/S09-enlace-custom-tab.log` | `e02461678328480fa5c670ad0f403e85f345109e714f71937d312513cd90c18f` |
| `trazas/S14-scroll-lifecycle.log` | `3f39ffbe218e40e63cc34ccaecebed67cfea4ff2760031a90f706680e89e3190` |
| `trazas/S15-long-press-data.log` | `f1f0075270e247edf0ab724f0d8edc5a45f397f3610003c65a439f815690bf58` |
| `trazas/S16-release-reapertura.log` | `c322b5619d0ef87ac8c694e56e950bbccdd9d5efbc303fb42f4d5ceb2676060b` |

El acumulado verificado es **16 PNG + 16 logs**. Los 11 PNG y 12 logs de
3.1 coinciden exactamente con los hashes publicados en
`resultados-subfase-3.1.md`; la evidencia histórica no cambió. Los 16 PNG son
archivos válidos, no vacíos y de 1080×2400.

La búsqueda de privacidad no encontró `Authorization`, `Bearer`,
`access_token`, `refresh_token`, `@gmail.com` ni `@outlook.com` en las trazas.
Las imágenes contienen solo fixtures sintéticas y la página pública de
ejemplo.

## 10. Revisión visual — APROBADA

- **S09** muestra una Custom Tab de Chrome ya cargada con `Example Domain` y
  `example.com`, sin onboarding, cuentas ni datos reales.
- **S14 antes/después** muestra exactamente el mismo punto vertical del
  newsletter. Los archivos tienen el mismo SHA-256; el overflow horizontal
  F02 continúa visible y aceptado como defecto conocido de 3.1.
- **S15** muestra el texto sintético y el área inline preservada de 96×96.
- **S16** muestra F01 completo tras crear la segunda instancia, incluidos
  énfasis, acentos y `ñ`.
- Ninguna de las cinco capturas contiene notificaciones, datos personales ni
  contenido fabricado para sustituir la ejecución.

## 11. Integridad y cierre

- Producción, Gradle, fixtures y evidencia 3.1: intactos.
- Hashes de los tres archivos protegidos: idénticos a la sección 2.
- Cuatro XML 3.2 presentes, verdes y con SHA-256 registrado.
- `git diff --check`: limpio.
- Cambios ajenos de `ComposeScreen.kt` y `MainNavHost.kt`: preservados sin
  modificación.
- Sin commit; continúa reservado para la Subfase 4.2.
- AVD dejado activo en `emulator-5554`.

## 12. Decisión

**GO — Subfase 3.2 cerrada; autorizada la Subfase 4.1.**
