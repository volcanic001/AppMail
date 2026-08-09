# Resultados — Subfase 2.2, huecos de refresh

## Identificación

- Etapa: 2 — Lecturas, sincronización, búsqueda y concurrencia.
- Subfase: 2.2 — Huecos de refresh.
- Ejecución final: 2026-08-08, zona CST (`-0600`).
- Emulador: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suite ampliada: `EmailRepositoryReadSyncSearchContractsTest`.

## Contratos nuevos

Se añadieron siete casos que aplican simétricamente a Inbox y Trash:

| Caso | Contrato protegido |
|---|---|
| `refresh_without_lease_returns_empty_without_provider_or_room_changes` | Sin lease devuelve página vacía, no resuelve provider y conserva las tres carpetas. |
| `refresh_without_provider_returns_empty_without_room_changes` | Sin provider devuelve página vacía y no modifica Room. |
| `refresh_delegates_page_tokens_and_returns_complete_remote_results` | Delega tokens exactos, devuelve el resultado completo y fusiona paginación. |
| `refresh_provider_errors_propagate_same_instance_and_leave_room_unchanged` | Excepciones remotas ordinarias conservan identidad y no escriben. |
| `refresh_cancellation_propagates_same_instance_and_leave_room_unchanged` | Cancelación remota conserva la instancia y no escribe. |
| `refresh_rejected_commit_returns_remote_result_but_leaves_room_unchanged` | Un commit rechazado devuelve el resultado remoto actual sin modificar Room. |
| `refresh_local_commit_errors_propagate_same_instance_and_leave_room_unchanged` | Una excepción local conserva identidad y no deja escrituras parciales. |

Los contratos ya existentes de replace/merge, página parcial, preservación de datos ricos y generaciones obsoletas permanecen en `SafeRefreshContractsTest` y `PartialPageContractsTest`; no se duplicaron.

## Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 8s
30 actionable tasks: 2 executed, 28 up-to-date
```

## Ejecuciones instrumentadas

La primera ejecución conjunta alcanzó los 24 casos y presentó un único fallo de la prueba nueva: se comparó el resultado de `getEntitiesByFolderSync`, que no declara orden, contra una lista ordenada. La corrección sustituyó esa expectativa por igualdad de conjuntos; no cambió ningún contrato del repositorio.

Selección final:

| Suite | Casos |
|---|---:|
| `EmailRepositoryReadSyncSearchContractsTest` | 12 |
| `SafeRefreshContractsTest` | 11 |
| `PartialPageContractsTest` | 1 |
| **Total** | **24** |

Resultado final:

```text
Starting 24 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 24 tests on Medium_Phone_API_36.1(AVD) - 16
tests=24, failures=0, errors=0, skipped=0
BUILD SUCCESSFUL in 48s
```

El XML reportó 3.182 s agregados para los casos.

## Integridad y cierre

- `EmailRepository.kt` conserva SHA-256 `abcac202041966e53383b40613c5c3d027a93bbb0d8c2616fe16a90a424dbe4b`.
- `MainNavHost.kt` conserva SHA-256 `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.
- No se modificaron producción, fakes, Gradle ni suites existentes.
- Los huecos de lease/provider, tokens, error, cancelación y commit de refresh quedan protegidos: cumplido.
- La subfase 2.2 queda cerrada; las subfases 2.3 y 2.4 permanecen pendientes.
