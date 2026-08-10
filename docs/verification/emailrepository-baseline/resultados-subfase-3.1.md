# Resultados — Subfase 3.1, resolución y acciones existentes

## Identificación

- Etapa: 3 — Resolución, acciones, cuerpos e imágenes inline.
- Subfase: 3.1 — Sellar resolución y acciones existentes.
- Ejecución final: 2026-08-09, zona CST (`-0600`).
- Dispositivo: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suites ampliadas: `EmailResolutionContractsTest` y `EmailRepositoryActionContractsTest`.

## Auditoría y contratos añadidos

La auditoría confirmó que los 46 casos previos ya protegían cache-first, errores tipados, merge, separación por generación, remote-first, orden de carpetas, `remoteApplied` y las ramas compartidas de commit/cancelación. Se añadieron solamente ocho huecos públicos:

| Suite | Caso nuevo | Contrato protegido |
|---|---|---|
| Resolución | `provider_is_resolved_fresh_for_each_cache_miss` | Dos cache misses consecutivos usan el provider vigente de cada llamada. |
| Resolución | `non_cacheable_terminal_flights_are_removed_and_retried` | `NotFound` y `Failure` eliminan el vuelo; una llamada posterior al mismo ID vuelve a red. |
| Resolución | `leader_cancellation_cancels_joined_follower_and_retry_starts_new_flight` | Cancelar al líder cancela al follower unido, limpia el vuelo y permite un retry remoto nuevo. |
| Acciones | `remaining_actions_without_lease_return_no_active_account_without_side_effects` | Restaurar, eliminar y marcar leído cubren su rama propia sin lease. |
| Acciones | `remaining_actions_without_provider_return_no_active_account_without_local_commit` | Las mismas tres APIs cubren su rama propia sin provider. |
| Acciones | `remaining_remote_cancellations_propagate_same_instance_without_room_changes` | Mover, restaurar y marcar leído preservan la instancia de cancelación sin escribir. |
| Acciones | `rejected_action_commit_accepts_complete_reconciliation_in_folder_order` | Un rechazo inicial permite reconciliación completa Inbox → Trash y devuelve `remoteApplied=true`. |
| Acciones | `rejected_action_commit_merges_partial_reconciliation_without_deleting_cache` | Páginas parciales de reconciliación fusionan ambas carpetas sin borrar caché previa. |

`FakeSessionWriteGuard` se amplió únicamente en AndroidTest con resultados nulos programables por número de llamada y contador de commits. Sus controles anteriores mantienen el mismo comportamiento.

## Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 11s
30 actionable tasks: 2 executed, 28 up-to-date
```

Después de ajustar la sincronización test-only descrita abajo, la compilación volvió a terminar correctamente en 8 s.

## Validación instrumentada

Corrida conjunta final:

| Suite | Casos |
|---|---:|
| `EmailResolutionContractsTest` | 29 |
| `EmailRepositoryActionContractsTest` | 25 |
| **Total** | **54** |

```text
Starting 54 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 54 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 2m 6s
```

Resolución se ejecutó tres veces en total:

| Corrida | Resultado | Duración Gradle |
|---:|---|---:|
| 1, dentro de la conjunta | 29/29 | 2 min 6 s para ambas suites |
| 2 | 29/29 | 2 min 58 s |
| 3 | 29/29 | 2 min 55 s |

El XML final reportó 29 pruebas, 0 fallos, 0 errores, 0 omitidas y 3.761 s agregados.

## Incidentes controlados

- La primera corrida de 54 casos encontró que la prueba nueva de cancelación consultaba el contador antes de que Room terminara su lectura asíncrona. Se corrigió solo esa prueba usando `StandardTestDispatcher` como executor de su base local, patrón ya existente en la suite. Producción y expectativas contractuales no cambiaron.
- El AVD permaneció `offline` y su log confirmó un snapshot corrupto (`Failed to load snapshot`). Con autorización explícita se reinicializó exclusivamente `Medium_Phone_API_36.1`; se borraron sus apps y datos locales, sin afectar el Pixel físico ni el proyecto. El arranque limpio quedó en estado `device` y permitió todas las corridas finales.

## Integridad y cierre

- Cobertura directa: 83 → 91 casos.
- Resolución y las cuatro acciones quedan contractualmente selladas para el alcance de esta subfase.
- No se modificaron producción, Room, providers reales ni Gradle.
- `EmailRepository.kt` permanece congelado.
- La Subfase 3.1 queda cerrada; la Etapa 3 continúa abierta y sin commit hasta completar la Subfase 3.4.
