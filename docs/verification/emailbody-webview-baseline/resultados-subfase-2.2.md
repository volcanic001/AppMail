# Resultados — Subfase 2.2: instrumentación existente de Email Detail

## 1. Estado

**COMPLETADA TRAS ESTABILIZACIÓN DE ANDROIDTEST (2.2-R)**

La primera serie focal quedó correctamente bloqueada por inestabilidad: dos
corridas fallaron en métodos distintos, una corrida fue verde y hubo además un
crash de infraestructura sin tests. La evidencia original se conserva en las
secciones 4–7. La reapertura 2.2-R corrigió exclusivamente dos esperas frágiles
de AndroidTest, sin alterar producción ni contratos. Después de la corrección,
ambos casos pasaron 3/3 aisladamente, G21 pasó 3/3 (102/102) y G22 pasó 284/284.
La puerta final es `GO` y la Subfase 2.3 queda habilitada.

## 2. Identidad

- **Fecha local**: ejecución 2026-08-24 10:53 – 15:16 CST (-0600); documento
  actualizado 2026-08-24 15:17 CST (-0600).
- **Rama**: `main`.
- **HEAD**: `2a433a67af4ea6f4f27e320133c963a408e80453`.
- **Hashes protegidos**:
  - `EmailBodyWebView.kt`: `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`;
  - `ComposeScreen.kt`: `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69`;
  - `MainNavHost.kt`: `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.

Las precondiciones de repositorio pasaron (rama, HEAD, hashes y árbol con
únicamente los dos cambios ajenos congelados y los artefactos aprobados del
baseline; `resultados-subfase-2.1.md` declara `GO`).

## 3. Dispositivo

- **Serial**: `emulator-5554`.
- **AVD**: `Medium_Phone_API_36.1` (creado por el usuario en Android Studio).
- **Android / API**: release `16`, SDK `36`.
- **Boot completed**: `1`.
- **Arranque**: AVD no estaba activo; se inició con
  `emulator @Medium_Phone_API_36.1 -port 5554 -no-snapshot-load -no-boot-anim -no-audio`
  (proceso persistente, log en `/tmp/mailapp-emailbody-2.2.2t06R5/emulator.log`).
  Boot completo en 50 s. El AVD no se reinició ni se limpió en ningún momento
  de la serie (incluida la reapertura).
- **Pixel 9 físico**: presente en `adb devices` (serial `adb-55080DLAQ002CK-…`)
  y **excluido** conforme al plan; no se usó ni se modificó su estado.

## 4. G21 — Focal (Email Detail)

Comando literal (idéntico en todos los intentos funcionales):

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.emaildetail.EmailDetailPresentationTest,com.david.mailapp.feature.emaildetail.EmailDetailIntegrationTest,com.david.mailapp.feature.emaildetail.EmailDetailCancellationTest,com.david.mailapp.feature.emaildetail.EmailDetailReadFailureEffectTest,com.david.mailapp.feature.emaildetail.components.EmailDetailStateContentTest,com.david.mailapp.feature.emaildetail.components.ImageOverlaysTest
```

Serie completa de intentos:

| Intento | Clasificación | Rango horario | Duración (s) | Exit | Resultado |
|---|---|---|---|---|---|
| 1 | infraestructura (lanzamiento) | 10:53:33 | 0 | 127 | `bash: ./gradlew: No such file or directory` (cwd incorrecto del shell background); sin corrida |
| 2 | **funcional** | 10:54:20 – 11:14:32 | 1212 | 1 | 34 ejecutados, 33 aprobados, 1 fallido |
| 3 | infraestructura (runner) | 11:15:18 – 11:25:16 | 598 | 1 | `Process crashed`; 0 tests ejecutados — no cuenta como resultado funcional (sección 9.6) |
| 4 | **funcional** | 11:25:58 – 11:36:30 | 632 | 1 | 34 ejecutados, 33 aprobados, 1 fallido |
| 5 | **funcional** | 15:11:10 – 15:16:49 | 339 | 0 | 34 ejecutados, 34 aprobados, 0 fallidos — verde |

Las tres ejecuciones funcionales completas (XML con los 34 tests) son los
intentos 2, 4 y 5.

Resultados por corrida funcional:

- **Intento-2** (34 ejecutados, 33 aprobados, 1 fallido): `EmailDetailCancellationTest.abandonDetailDuringPdfDownloadCancelsNetworkAndDoesNotCreateFileOrEmitOpenSave` → `java.lang.AssertionError`.
- **Intento-4** (34 ejecutados, 33 aprobados, 1 fallido): `EmailDetailPresentationTest.imageLongPress_opensActionMenuAndFullscreen` → `AssertionError: The component with Text + InputText + EditableText contains 'Abrir imagen' (ignoreCase: false) is not displayed!`.
- **Intento-5** (34 ejecutados, 34 aprobados, 0 fallidos): `BUILD SUCCESSFUL`, exit 0; XML con device `Medium_Phone_API_36.1(AVD) - 16`, 34 tests, 0 failures, 0 errors, 0 skipped y las seis clases focales presentes.

Criterio de pase de G21: se exige la serie verde y estable. Con resultados
distintos entre corridas funcionales (dos fallos en métodos diferentes y una
corrida verde), G21 no puede declararse verde y estable como serie.

## 5. G22 — Completa

**No ejecutada.** G21 no quedó verde y estable como serie; no corresponde
lanzar la suite completa. Esta decisión se mantiene bajo cualquier resultado de
la serie focal (sección 9 del plan y reapertura).

## 6. Reportes

No se conservaron `focal.xml` ni `completa.xml` versionables: no existe una
serie aceptada que copiar (sección 6 del plan). La evidencia cruda de cada
intento (XML y HTML de resultados + log + metadatos) quedó preservada en el
directorio temporal `/tmp/mailapp-emailbody-2.2.2t06R5/`:

```text
G21-focal-intento-2-result.xml     G21-focal-intento-2-index.html
G21-focal-intento-3-result.xml     G21-focal-intento-3-index.html
G21-focal-intento-4-result.xml     G21-focal-intento-4-index.html
G21-focal-intento-5-result.xml     G21-focal-intento-5-index.html
G21-focal-intento-{1..5}.log       G21-focal-intento-{2..5}.meta
emulator.log
```

## 7. Incidencias/repeticiones

Serie completa conforme al protocolo de la sección 9, sin editar archivos ni
reiniciar/limpiar el AVD entre intentos:

1. **Intento-1 — infraestructura (lanzamiento)**: el shell background no
   heredó el directorio de trabajo (`bash: ./gradlew: No such file or
   directory`, exit 127, 0 s). No constituye resultado funcional (sección 9.6);
   no se contó en la serie. Relanzado con el directorio correcto.
2. **Intento-2 — funcional**: exit 1, 1212 s. 34 ejecutados, 33 aprobados,
   1 fallido: `EmailDetailCancellationTest.abandonDetailDuringPdfDownloadCancelsNetworkAndDoesNotCreateFileOrEmitOpenSave` (`java.lang.AssertionError`).
3. **Intento-3 — infraestructura (runner)**: exit 1, 598 s. `Instrumentation
   run failed due to Process crashed`; 0 tests ejecutados (XML sin suites). No
   cuenta como ejecución funcional (sección 9.6). Evidencia preservada.
4. **Intento-4 — funcional**: exit 1, 632 s. 34 ejecutados, 33 aprobados,
   1 fallido: `EmailDetailPresentationTest.imageLongPress_opensActionMenuAndFullscreen` (`AssertionError`: componente con texto `Abrir imagen` no visible).
5. **Intento-5 — funcional**: exit 0, 339 s. 34 ejecutados, 34 aprobados,
   0 fallidos. Verde.

Clasificación: **INESTABLE** — tres corridas funcionales consecutivas completas
(intentos 2, 4, 5) con resultados distintos: fallo en `EmailDetailCancellationTest`, fallo en `EmailDetailPresentationTest` y corrida verde. No se diagnosticó como regresión de producción (esta subfase es baseline); no se corrigió nada.

## 8. Integridad final

- Hashes finales de los tres archivos protegidos: idénticos a la sección 2.
- `git diff --check`: sin salida.
- Estado Git: únicamente los dos cambios ajenos congelados y los artefactos
  aprobados del baseline; 2.2 no añadió ningún XML versionable. Producción,
  AndroidTest y configuración Gradle sin cambios.
- Sin commit. AVD y emulador dejados activos conforme al plan (no se cerró ni
  borró el dispositivo).

## 9. Conclusión inicial antes de 2.2-R

**NO-GO histórico.** En este punto la Subfase 2.2 quedó documentada y abierta,
y 2.3 se bloqueó correctamente. Esta decisión no se borra ni se reinterpreta:
motivó la estabilización acotada que se documenta a continuación.

## 10. Reapertura 2.2-R — causa y alcance

- Fecha de reapertura y cierre: 2026-08-24, CST (`-0600`).
- Autorización: estabilizar únicamente los dos AndroidTest que habían fallado,
  conservar la evidencia original y cerrar 2.2 antes de preparar el siguiente
  plan técnico.
- Producción, `EmailBodyWebView.kt`, Gradle, fixtures y contratos: sin cambios.
- El AVD original ya no estaba disponible al reanudar. Se volvió a iniciar
  `Medium_Phone_API_36.1` en `emulator-5554` con `-no-snapshot-load`,
  `-no-boot-anim` y `-no-audio`, sin `-wipe-data`. Se verificó nombre, Android
  16/API 36 y `sys.boot_completed=1`. No se dirigió ningún comando al Pixel 9.

### 10.1 `EmailDetailCancellationTest`

Caso:
`abandonDetailDuringPdfDownloadCancelsNetworkAndDoesNotCreateFileOrEmitOpenSave`.

La prueba ejecutaba `viewModelStore.clear()`, liberaba inmediatamente
`pdfGate`, esperaba 50 ms y después comprobaba la cancelación. Eso permitía que
la finalización normal del fake compitiera con la cancelación de
`viewModelScope`. Se sustituyó esa espera temporal por
`awaitCondition("pdf download cancellation")` antes de liberar el gate.

Se preservaron íntegramente las comprobaciones de cancelación, ausencia de
PDF/`.tmp` y ausencia de eventos de apertura/guardado.

### 10.2 `EmailDetailPresentationTest`

Caso: `imageLongPress_opensActionMenuAndFullscreen`.

La interacción se ejecuta en un `WebView`, pero el menú se materializa en el
árbol de Compose. La prueba consultaba el nodo `Abrir imagen` inmediatamente
después de `ViewActions.longClick()`, sin esperar la transición entre ambos
sistemas UI. Se añadió una espera observable y acotada de 5 segundos mediante
el árbol de semántica, seguida de `waitForIdle()`.

Se mantuvieron las aserciones de visibilidad de `Abrir imagen` y `Guardar
imagen`, el click de apertura y la comprobación del fullscreen. No se añadieron
sleeps, hooks, tags ni cambios de producción.

## 11. Validación de la estabilización

### 11.1 Compilación AndroidTest

- `./gradlew compileDebugAndroidTestKotlin --rerun-tasks --console=plain`
- Resultado: `BUILD SUCCESSFUL in 35s`.
- Solo se observaron warnings ya conocidos de deprecación; cero errores.

### 11.2 Casos afectados aislados

Todas las corridas usaron `ANDROID_SERIAL=emulator-5554`, `--rerun-tasks` y
`--console=plain`.

| Caso | Corrida 1 | Corrida 2 | Corrida 3 | Total |
|---|---:|---:|---:|---:|
| Cancelación PDF | 1/1 — 2m22s | 1/1 — 2m25s | 1/1 — 2m03s | **3/3** |
| Long-press de imagen | 1/1 — 1m40s | 1/1 — 1m53s | 1/1 — 2m30s | **3/3** |

Cero fallos, errores, omisiones o crashes en las seis corridas.

### 11.3 G21 focal estable

Se ejecutaron las mismas seis clases y 34 pruebas del gate original, tres veces
consecutivas y sin reiniciar ni limpiar el AVD entre corridas:

| Corrida | Resultado | Fallos | Errores | Omitidas | Duración Gradle | SHA-256 XML temporal |
|---:|---:|---:|---:|---:|---:|---|
| 1 | 34/34 | 0 | 0 | 0 | 2m36s | `434dcf2d6c6c1a9f4012660741e54d79865da0791c44f97414b7e68305802404` |
| 2 | 34/34 | 0 | 0 | 0 | 2m20s | `84baf6e4c382bcec9c3ee556019528a0c3d3bb182e704d0d692985af27c975a6` |
| 3 | 34/34 | 0 | 0 | 0 | 2m41s | `7ea7a3d9ecd5b1a20dd8da4cf781a612464e7e65bdb85e9c2e4a69b077a48ab6` |

Total: **102/102**, cero flakiness. El XML versionado `focal.xml` corresponde
a la tercera corrida; contiene exactamente las seis clases previstas.

### 11.4 G22 completa

- Comando: `ANDROID_SERIAL=emulator-5554 ./gradlew
  connectedDebugAndroidTest --rerun-tasks --console=plain`.
- Resultado: `BUILD SUCCESSFUL in 9m 11s`.
- XML: **284/284**, 26 clases, 0 failures, 0 errors y 0 skipped.
- Dispositivo reportado: `Medium_Phone_API_36.1(AVD) - 16`.
- El conteo coincide con la referencia histórica de esta rama.

## 12. Reportes finales conservados

| Reporte | Tests | Tamaño | SHA-256 |
|---|---:|---:|---|
| `reportes-subfase-2.2/focal.xml` | 34 | 6,042 bytes | `7ea7a3d9ecd5b1a20dd8da4cf781a612464e7e65bdb85e9c2e4a69b077a48ab6` |
| `reportes-subfase-2.2/completa.xml` | 284 | 47,424 bytes | `099a86646ce2dc3d4c694f50cc4d059ed75a93098d356d4e358cba68447df75d` |

Los reportes son copias exactas de los XML Gradle aceptados. La búsqueda de
`Authorization`, `Bearer`, `access_token`, `refresh_token`, `@gmail.com` y
`@outlook.com` no produjo resultados. La evidencia intermedia de G21 se
conserva temporalmente en `/tmp/mailapp-emailbody-2.2R.wnlDX4/`.

## 13. Cierre final

- La serie inestable original permanece documentada; no se ocultó ni se
  convirtió retroactivamente en verde.
- Las únicas correcciones son sincronización observable dentro de dos pruebas
  existentes.
- No se modificó comportamiento de producto ni la API de `EmailBodyWebView`.
- Casos afectados aislados: **6/6**.
- G21 estable: **102/102**.
- G22 completa: **284/284**.
- Decisión: **GO — Subfase 2.2 cerrada; autorizado continuar con la Subfase
  2.3.**
