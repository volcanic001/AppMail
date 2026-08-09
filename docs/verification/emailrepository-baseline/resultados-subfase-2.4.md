# Resultados — Subfase 2.4, coordinación temporal

## Identificación

- Etapa: 2 — Lecturas, sincronización, búsqueda y concurrencia.
- Subfase: 2.4 — Coordinación temporal.
- Ejecución final: 2026-08-08, zona CST (`-0600`).
- Dispositivo final: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suite ampliada: `EmailRepositoryReadSyncSearchContractsTest`.

## Contratos nuevos

Se añadieron tres casos directos a nivel repositorio. Cada caso ejecuta tres iteraciones con datos y barreras nuevas:

| Caso | Contrato protegido |
|---|---|
| `concurrent_first_page_refreshes_commit_inbox_and_trash_independently_three_times` | Dos primeras páginas simultáneas conservan ambas generaciones; Trash puede completar mientras Inbox continúa bloqueado. |
| `trash_first_page_does_not_invalidate_inbox_pagination_three_times` | Una generación nueva de Trash no rechaza una paginación activa de Inbox. |
| `inbox_first_page_does_not_invalidate_trash_pagination_three_times` | Una generación nueva de Inbox no rechaza una paginación activa de Trash. |

La protección de la misma carpeta ya estaba cubierta por cuatro casos instrumentados de `SafeRefreshContractsTest`: refresh antiguo rechazado y paginación invalidada por una primera página nueva, simétricamente para Inbox y Trash. La exclusión mutua de dos secciones críticas y el bloqueo de una generación nueva durante un commit activo permanecen cubiertos directamente por `FolderCommitCoordinatorTest`.

## Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 8s
30 actionable tasks: 2 executed, 28 up-to-date
```

## Repetición JVM anti-flakiness

La selección siguiente se ejecutó tres veces con `--rerun-tasks`:

```text
./gradlew testDebugUnitTest \
  --tests com.david.mailapp.data.repository.FolderCommitCoordinatorTest \
  --rerun-tasks
```

| Corrida | Casos | Resultado | Duración Gradle |
|---:|---:|---|---:|
| 1 | 3 | 3/3 | 45 s |
| 2 | 3 | 3/3 | 33 s |
| 3 | 3 | 3/3 | 50 s |

Total: 9 ejecuciones de casos, cero fallos. El XML final reportó 3 pruebas, 0 fallos, 0 errores y 0 omitidas.

## Repetición instrumentada consolidada

Selección de Etapa 2:

| Suite | Casos por corrida |
|---|---:|
| `EmailRepositoryReadSyncSearchContractsTest` | 20 |
| `SafeRefreshContractsTest` | 11 |
| `PartialPageContractsTest` | 1 |
| **Total** | **32** |

| Corrida válida | Resultado | Duración Gradle |
|---:|---|---:|
| 1 | 32/32 | 1 min 47 s |
| 2 | 32/32 | 2 min 39 s |
| 3 | 32/32 | 2 min 27 s |

Total: 96 ejecuciones instrumentadas válidas, cero fallos, errores u omisiones. El XML de la tercera corrida reportó 32 pruebas y 6.653 s agregados.

## Incidentes de entorno descartados

- El primer arranque completo del AVD permaneció `offline`; se cerró limpiamente y el segundo terminó de arrancar durante la interrupción de la sesión. No se reinicializó ni borró el AVD.
- Una selección preliminar en el Pixel 9 usó por error el paquete inexistente `com.david.mailapp.data.remote.PartialPageContractsTest`: los otros 31 casos se ejecutaron, pero la corrida completa fue descartada por el error de inicialización. Las tres corridas finales usaron el paquete correcto `com.david.mailapp.data.repository.PartialPageContractsTest` en el AVD previsto.

## Integridad y cierre

- No se modificó código de producción ni configuración Gradle.
- Los tres escenarios cruzados ejecutaron nueve iteraciones internas acumuladas sin contaminación ni resultados tardíos.
- Las garantías de la misma carpeta se ejecutaron tres veces a nivel JVM e instrumentado, sin flakiness.
- La subfase 2.4 y la Etapa 2 quedan cerradas.
