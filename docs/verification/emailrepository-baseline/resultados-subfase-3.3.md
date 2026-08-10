# Resultados — Subfase 3.3, fallos, sesión y cancelación del cuerpo

## Identificación

- Etapa: 3 — Resolución, acciones, cuerpos e imágenes inline.
- Subfase: 3.3 — Fallos, sesión y cancelación del cuerpo.
- Ejecución final: 2026-08-09, zona CST (`-0600`).
- Dispositivo: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suite ampliada: `EmailRepositoryContentContractsTest` (3 → 10 casos).

## Contratos añadidos

| Caso | Contrato protegido |
|---|---|
| `c4_fetchAndCacheBody_without_lease_returns_null_without_remote_or_commit` | `capture()` nulo: retorna null, no consulta al proveedor, no intenta commit y Room permanece intacto. |
| `c5_fetchAndCacheBody_without_provider_returns_null_without_commit` | Lease válido con `providerFactory` nulo: retorna null, no intenta commit y conserva la fila original. |
| `c6_fetchAndCacheBody_null_remote_result_returns_null_room_intact` | El proveedor recibe exactamente el emailId y devuelve null: retorna null, no limpia HTML, no intenta commit y no modifica Room. |
| `c7_fetchAndCacheBody_remote_error_propagates_same_instance_room_intact` | `fetchBodyWithRefs` lanza excepción centinela: se propaga la misma instancia, sin commit ni cambios locales. |
| `c8_fetchAndCacheBody_remote_cancellation_propagates_same_instance_room_intact` | `fetchBodyWithRefs` lanza una `CancellationException` centinela: se propaga exactamente la misma instancia y Room queda intacto. |
| `c9_fetchAndCacheBody_session_change_rejects_commit_and_returns_old_result` | `SessionWriteGuardImpl` real: invalidar y reactivar la sesión mientras la descarga está pendiente; el resultado remoto antiguo se devuelve (comportamiento heredado) pero su commit es rechazado y no contamina la fila de la sesión nueva. |
| `c10_fetchAndCacheBody_commit_failure_propagates_and_preserves_entity` | El guard lanza una excepción centinela antes de ejecutar el bloque Room: se propaga la misma instancia y la entidad conserva íntegramente cuerpo, cuerpo limpio, PDF y campos no relacionados. |

## Cambios en fakes

- `FakeEmailProvider` (solo AndroidTest): señal explícita de inicio `fetchBodyStarted` completada al entrar en `fetchBodyWithRefs`, necesaria para sincronizar de forma determinista el contrato temporal de cambio de sesión (el lease ya está capturado cuando el proveedor entra). Resultados, errores, gates y cancelación existentes intactos.
- No se amplió `FakeSessionWriteGuard`; se reutilizaron `captureResult`, `commitCalls`, `commitError` y el log compartido.

## Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 51s
30 actionable tasks: 2 executed, 28 up-to-date
```

## Validación instrumentada

### Corrida completa (10 casos)

```text
Starting 10 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 10 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 2m 27s
```

El XML final reportó 10 pruebas, 0 fallos, 0 errores, 0 omitidas y 3.882 s agregados. Testcases: c1 (0.244 s), c7 (0.002 s), c3 (0.000 s), c8 (0.001 s), c2 (0.001 s), c4 (0.001 s), c9 (0.000 s), c5 (0.001 s), c6 (0.000 s), c10 (0.000 s).

### Repetición del contrato temporal de sesión (c9)

| Corrida | Resultado | Duración Gradle |
|---:|---|---:|
| 1 | 1/1 | 2 min 5 s |
| 2 | 1/1 | 2 min 29 s |
| 3 | 1/1 | 1 min 40 s |

El XML de la corrida 3 reportó 1 prueba, 0 fallos, 0 errores, 0 omitidas y 4.569 s agregados, con `c9` en 0.127 s. Cero flakiness en el contrato temporal.

## Integridad y cierre

- Cobertura directa: 94 → 101 casos.
- `fetchAndCacheBody` pasa de cobertura media a alta: éxito, persistencia, ausencias, errores, cancelación, cambio de sesión y fallo local de commit quedan protegidos.
- Comportamiento heredado documentado: un commit rechazado por cambio de sesión todavía devuelve el resultado remoto, sin escritura tardía.
- Fallos de limpieza HTML internos no inyectados: no existe frontera reemplazable sin modificar producción; quedan cubiertos por la propagación natural.
- No se modificaron producción, Room, providers reales ni Gradle.
- `EmailRepository.kt` conserva su hash original; `MainNavHost.kt` conserva el cambio previo del usuario.
- La Subfase 3.3 queda cerrada; la Etapa 3 continúa abierta y sin commit hasta completar la Subfase 3.4.
