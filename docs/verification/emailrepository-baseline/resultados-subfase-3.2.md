# Resultados — Subfase 3.2, persistencia del cuerpo

## Identificación

- Etapa: 3 — Resolución, acciones, cuerpos e imágenes inline.
- Subfase: 3.2 — Persistencia del cuerpo.
- Ejecución final: 2026-08-09, zona CST (`-0600`).
- Dispositivo: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suite nueva: `EmailRepositoryContentContractsTest`.

## Cambios

### Ampliación de `FakeEmailProvider` (solo AndroidTest)

- Registro de `gmail.fetchBody` en el log compartido de eventos al entrar en `fetchBodyWithRefs`.
- Registro de cada `emailId` recibido por `fetchBodyWithRefs` en `receivedFetchBodyIds`.
- Resultados, errores, gates y mecanismos de cancelación existentes de `fetchBodyWithRefs` permanecen intactos.

### Contratos añadidos

| Caso | Contrato protegido |
|---|---|
| `c1_fetchAndCacheBody_complete_html_and_pdf_metadata_persist_atomically` | Retorno de la misma instancia de `BodyFetchResult`; delegación exacta del `emailId`; persistencia del HTML crudo sin alterar y del HTML limpio producido por `EmailHtmlCleaner`; persistencia de todos los campos PDF, `pdfMetadataScanned=true` y `hasAttachments=true`; observación vía Flow de Room de una sola actualización con todos los campos ya consistentes; orden `gmail.fetchBody → room.commit`; preservación de identidad, remitente, asunto, carpeta, labels, estado leído, timestamp y cabeceras RFC. |
| `c2_fetchAndCacheBody_null_body_preserves_body_and_persists_pdf_metadata` | Cuerpo nulo normalizado sin transformación observable: una fila inicialmente vacía se mantiene vacía y una fila con cuerpo previamente almacenado conserva `body`/`cleanBody`; ambas filas persisten la metadata PDF, `pdfMetadataScanned=true` y `hasAttachments=true`; preservación del resto de campos. |
| `c3_fetchAndCacheBody_empty_pdf_list_replaces_old_metadata` | Lista PDF vacía como resultado autoritativo sobre metadata antigua: nuevo cuerpo y versión limpia persistidos, metadata reemplazada por lista vacía, `pdfMetadataScanned=true`, `hasAttachments=false` y campos no relacionados preservados. |

## Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 7s
30 actionable tasks: 2 executed, 28 up-to-date
```

Solo aparece el warning de opt-in de `limitedParallelism` en el helper `awaitValue`, idéntico al patrón ya existente en `EmailRepositoryReadSyncSearchContractsTest`.

## Validación instrumentada

```text
Starting 3 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 3 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 35s
74 actionable tasks: 5 executed, 69 up-to-date
```

El XML final reportó 3 pruebas, 0 fallos, 0 errores, 0 omitidas y 2.402 s agregados:

| Testcase | Tiempo |
|---|---:|
| `c1_fetchAndCacheBody_complete_html_and_pdf_metadata_persist_atomically` | 0.304 s |
| `c2_fetchAndCacheBody_null_body_preserves_body_and_persists_pdf_metadata` | 0.062 s |
| `c3_fetchAndCacheBody_empty_pdf_list_replaces_old_metadata` | 0.020 s |

## Integridad y cierre

- Cobertura directa: 91 → 94 casos.
- `fetchAndCacheBody` pasa de cobertura ausente a media: éxito y persistencia protegidos; fallos, sesión y cancelación quedan pendientes para la Subfase 3.3.
- No se modificaron producción, Room, providers reales ni Gradle.
- `EmailRepository.kt` conserva su hash original; `MainNavHost.kt` conserva el cambio previo del usuario.
- Solo cambian la suite nueva, el fake instrumentado y la documentación del baseline.
- La Subfase 3.2 queda cerrada; la Etapa 3 continúa abierta y sin commit hasta completar la Subfase 3.4.
