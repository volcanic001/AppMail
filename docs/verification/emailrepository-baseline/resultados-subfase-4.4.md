# Resultados — Subfase 4.4, identidad y envío

## Identificación

- Etapa: 4 — PDF, identidad y envío.
- Subfase: 4.4 — Identidad y envío.
- Ejecución final: 2026-08-09, zona CST (`-0600`).
- Dispositivo: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suite nueva: `EmailRepositoryAccountSendContractsTest`.

## Contratos añadidos

### `getUserEmail`

| Caso | Contrato protegido |
|---|---|
| `c1_getUserEmail_dynamic_provider_no_reuse` | Una misma instancia de repositorio consulta sucesivamente provider A, ausencia de provider y provider B; devuelve email A, null y email B sin reutilizar el provider anterior. Cada provider recibe exactamente una llamada. |
| `c2_getUserEmail_null_result_preserved` | Provider vigente devuelve null; el repositorio conserva null sin transformación. |
| `c3_getUserEmail_remote_error_propagates_same_instance` | Excepción centinela remota; se propaga la misma instancia. |
| `c4_getUserEmail_remote_cancellation_propagates_same_instance` | `CancellationException` remota; se propaga la misma instancia. |

### `sendEmail`

| Caso | Contrato protegido |
|---|---|
| `c5_sendEmail_exact_delegation_full_and_null_args` | Dos envíos ordenados: todos los campos no nulos y `ReplyContext` completo; cc, bcc y replyContext nulos. Provider recibe exactamente to, cc, bcc, subject, body y la misma instancia de `ReplyContext`. |
| `c6_sendEmail_without_provider_throws_legacy_message` | Sin provider → `IllegalStateException` con el mensaje heredado `No hay proveedor activo`. |
| `c7_sendEmail_dynamic_provider_login_and_logout` | Una misma instancia de repositorio envía con provider A, atraviesa ausencia de provider (excepción) y después envía con provider B. A y B reciben únicamente su solicitud; no se reutiliza el provider anterior. |
| `c8_sendEmail_remote_error_propagates_same_instance` | Excepción centinela del provider; se propaga la misma instancia. |
| `c9_sendEmail_remote_cancellation_propagates_same_instance` | `CancellationException` del provider; se propaga la misma instancia. |

Todos los casos siembran una fila Room y un PDF cacheado previamente y confirman que entidad, bytes de caché y ausencia de `.tmp` permanecen intactos, con cero commits del `SessionWriteGuard`.

## Cambios en fakes

- `FakeEmailProvider` (solo AndroidTest): contador, error configurable y evento `gmail.getUserEmail` para `getUserEmail`; data class test-only `SendRequest` con los seis argumentos de envío, lista ordenada de solicitudes recibidas y evento `gmail.sendEmail` registrados al entrar (antes del gate). Resultados, gates y errores existentes intactos.

## Validación

### Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 9s
```

### Suite nueva (9 casos)

```text
Starting 9 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 9 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 35s
```

### Corrida conjunta de la Etapa 4 (32 casos)

```text
Starting 32 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 32 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 36s
```

| Suite | Casos |
|---|---:|
| `EmailRepositoryPdfContractsTest` | 21 |
| `PdfCancellationContractsTest` | 2 |
| `EmailRepositoryAccountSendContractsTest` | 9 |
| **Total** | **32** |

### Regresión del consumidor

```text
EmailDetailCancellationTest: 7/7, BUILD SUCCESSFUL in 48s
```

### `PdfCacheManagerTest` en JVM

```text
22/22, BUILD SUCCESSFUL in 12s
```

### Corrección posterior de auditoría

- C5 comprueba explícitamente que el provider recibe la misma instancia de `ReplyContext`.
- Los nueve contratos comparan la `EmailEntity` completa antes y después, además de los bytes del PDF y la ausencia de temporales.
- La matriz marca como cerrados, y no como pendientes, los huecos críticos y altos ya cubiertos por las etapas 2–4.
- Después de estas correcciones, `compileDebugAndroidTestKotlin` terminó en `BUILD SUCCESSFUL` en 14 s; la suite de identidad/envío volvió a pasar 9/9 en 33 s y la corrida consolidada de Etapa 4 volvió a pasar 32/32 en 35 s, sin fallos, errores ni omitidas.

## Integridad y cierre

- Cobertura directa: 131 → 140 casos.
- `getUserEmail` y `sendEmail` pasan de ausente a alta.
- Los 20 métodos públicos de `EmailRepository` quedan en cobertura alta.
- Provider dinámico, cancelación y ausencia de provider cubiertos para identidad y envío.
- Mensaje heredado `No hay proveedor activo` confirmado como comportamiento actual.
- No se modificaron producción, providers reales, Room ni Gradle.
- `EmailRepository.kt` conserva su hash `abcac202…`; `MainNavHost.kt` conserva `a6840cfc…`.
- La Subfase 4.4 y la Etapa 4 quedan cerradas; se crea el commit consolidado `test(repository): characterize pdf account and send contracts` excluyendo `MainNavHost.kt`.
