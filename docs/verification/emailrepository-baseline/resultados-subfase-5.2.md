# Resultados — Subfase 5.2, instrumentación completa en emulador

## Identificación

- Etapa: 5 — Validación integral y cierre del baseline.
- Subfase: 5.2 — Instrumentación completa en emulador.
- Ejecución inicial y corrección: 2026-08-10, zona CST (`-0600`).
- Dispositivo: `Medium_Phone_API_36.1`, serial `emulator-5554`, Android 16/API 36.
- Commit verificado: `a96582a` (HEAD).

## Precondiciones e integridad

| Condición | Estado |
|---|---|
| `EmailRepository.kt` SHA-256 | `abcac202…be4b` ✓ |
| `MainNavHost.kt` SHA-256 | `a6840cfc…088` ✓ |
| Producción o Gradle modificados | Ninguno |
| Cambios propios de la corrección | Dos pruebas bajo `app/src/androidTest` y esta documentación |
| Cambio ajeno protegido | `MainNavHost.kt`, intacto y excluido |

Todas las corridas se fijaron mediante `ANDROID_SERIAL=emulator-5554`; el Pixel 9 no se utilizó.

## Bloque focal de repositorio

Se ejecutaron juntas las nueve suites previstas:

| Suite | Casos |
|---|---:|
| `EmailResolutionContractsTest` | 29 |
| `EmailRepositoryActionContractsTest` | 25 |
| `EmailRepositoryReadSyncSearchContractsTest` | 20 |
| `SafeRefreshContractsTest` | 11 |
| `PartialPageContractsTest` | 1 |
| `EmailRepositoryContentContractsTest` | 19 |
| `EmailRepositoryPdfContractsTest` | 21 |
| `PdfCancellationContractsTest` | 2 |
| `EmailRepositoryAccountSendContractsTest` | 9 |
| **Total** | **137** |

Resultado: **137/137**, cero fallos, errores u omitidas; `BUILD SUCCESSFUL` en 5 min 10 s.

## Incidencias encontradas en la primera validación integral

La primera suite completa ejecutó los 284 casos, con 282 aprobados y dos
`ComposeTimeoutException` en `TrashContentActionTest`:

- `rapid_identical_failures_are_consumed_without_stalling_snackbar_queue`.
- `failure_shows_error_not_success_and_row_remains`.

La primera serie temporal ejecutó 329/330 casos correctamente. Falló
`inlineCancellationKeepsPendingReadyStateAndDoesNotWriteFallback` en
`EmailDetailCancellationTest`; el reporte original fue sobrescrito antes de preservar su
stack trace, por lo que la Subfase 5.2 se reabrió y 5.3 quedó bloqueada.

## Diagnóstico reproducible

El AVD llevaba 6 h 39 min activo y presentó load average 16.77, aproximadamente 90 MB
libres, swap en uso y `ShellCommandUnresponsiveException`. Un intento diagnóstico terminó
antes del runner con 0 pruebas y `failed to attach`; se clasificó como infraestructura y no
como fallo de aplicación. Con autorización del usuario se reinicializó exclusivamente
`Medium_Phone_API_36.1`; no se alteraron el proyecto, el Pixel 9 ni otros dispositivos.

Después de recuperar el AVD, los tres métodos pasaron aisladamente, pero expusieron dos
problemas deterministas en las pruebas:

| Método | Evidencia | Causa |
|---|---|---|
| `rapid_identical_failures…` | 1/1; 26.922 s | Esperaba cinco snackbars de duración real con timeout de 25 s. |
| `failure_shows_error…` | 1/1; 7.521 s | Esperaba autodismiss real dentro de 6 s. |
| `inlineCancellation…` | 1/1; 0.308 s | La condición `calls == 1` podía perderse cuando una emisión Room provocaba un segundo intento. |

Durante la validación de la primera corrección, una corrida temporal capturó el tercer caso
de forma explícita: `expected:<1> but was:<2>`. La cardinalidad no forma parte del contrato
de esta prueba; el comportamiento heredado puede reintentar después de la cancelación. El
contrato protegido es conservar `Ready` con inline pendiente y no escribir fallback.

## Correcciones exclusivas de AndroidTest

- `TrashContentActionTest`: las pruebas descartan explícitamente cada snackbar y esperan
  que la cola observable se consuma; ya no dependen de 6–25 segundos de reloj real.
- `EmailDetailCancellationTest`: espera `inlineImagesCalls >= 1` y el estado observable
  `Ready(inlineImagesLoading=true)` con el CID intacto; se eliminó el `delay(50)` y no se
  convirtió la cardinalidad heredada en un contrato nuevo.
- No se modificaron producción, expectativas funcionales, Gradle ni la matriz contractual.

`compileDebugAndroidTestKotlin` terminó correctamente. Después, las dos clases corregidas
se ejecutaron juntas tres veces:

| Corrida focal | Resultado | Tiempo agregado XML | Duración Gradle |
|---:|---:|---:|---:|
| 1 | 14/14 | 41.840 s | 2 min 23 s |
| 2 | 14/14 | 77.880 s | 4 min 59 s |
| 3 | 14/14 | 21.563 s | 2 min 50 s |

Total: 42/42, cero fallos, errores u omitidas.

## Suite instrumentada completa final

```text
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks
```

Resultado final sobre el árbol corregido:

- **284/284**.
- 26 clases instrumentadas.
- 0 fallos, 0 errores y 0 omitidas.
- `BUILD SUCCESSFUL` en 7 min 52 s; 74 tareas ejecutadas.
- Tiempo agregado XML: 212.154 s.

El XML confirmó el conteo de las 26 clases. Entre ellas,
`TrashContentActionTest` pasó 7/7 y `EmailDetailCancellationTest` pasó 7/7.

## Serie temporal final anti-flakiness

Después de reiniciar el invitado sin borrar datos, se reinició desde cero la serie de ocho
clases y 110 casos. Las tres corridas usaron `--rerun-tasks` y se ejecutaron consecutivamente
sin reiniciar entre ellas:

| Corrida final | Resultado | Fallos | Errores | Omitidas | Duración Gradle |
|---:|---:|---:|---:|---:|---:|
| 1 | 110/110 | 0 | 0 | 0 | 5 min 15 s |
| 2 | 110/110 | 0 | 0 | 0 | 4 min 19 s |
| 3 | 110/110 | 0 | 0 | 0 | 3 min 37 s |

Total final: **330/330**, cero flakiness. Cada XML confirmó los conteos por clase:

| Clase | Casos por corrida |
|---|---:|
| `EmailResolutionContractsTest` | 29 |
| `EmailRepositoryReadSyncSearchContractsTest` | 20 |
| `SafeRefreshContractsTest` | 11 |
| `PartialPageContractsTest` | 1 |
| `EmailRepositoryContentContractsTest` | 19 |
| `EmailRepositoryPdfContractsTest` | 21 |
| `PdfCancellationContractsTest` | 2 |
| `EmailDetailCancellationTest` | 7 |

## Cierre

- Bloque focal de repositorio: 137/137.
- Suite completa final: 284/284.
- Regresión de las dos clases corregidas: 42/42.
- Serie temporal final: 330/330 en tres corridas consecutivas.
- Cero fallos, errores, omisiones inesperadas o flakiness pendiente.
- `EmailRepository.kt`, producción, Gradle y `MainNavHost.kt` permanecen intactos.
- La Subfase 5.2 queda cerrada; la Subfase 5.3 queda habilitada y pendiente.
- No se crea commit; el commit documental de Etapa 5 corresponde al cierre de 5.4.
