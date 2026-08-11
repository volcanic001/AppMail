# Resultados — Subfase 2.3, Refresh y coordinación por carpeta

## Identificación y alcance

- Plan maestro: Refactor estructural conservador de `EmailRepository` — Etapa 2
  (Accesos simples y sincronización), Subfase 2.3 (Refresh y coordinación).
- Fecha: 2026-08-10, zona CST (`-0600`).
- Objetivo: extraer `refreshInbox`, `refreshTrash` y la coordinación temporal
  hacia `EmailMailboxCoordinator`, y trasladar `FolderCommitCoordinator` a su
  propio archivo, conservando el orden de efectos, la coordinación independiente
  Inbox/Trash y todos los contratos de sesión, paginación y persistencia.
  Al aprobarse esta subfase queda cerrada la Etapa 2.

## Estado de entrada

| Control | Valor | Estado |
|---|---|---|
| HEAD | `94547af` — `refactor(repository): extract mailbox reads` | ✓ |
| `EmailRepository.kt` | `edaa39fbd0944b51c9c2bc1b16d3fc59f686b677cb68cc3058c392666c3cbcd5` | ✓ |
| `EmailMailboxCoordinator.kt` | `7af807dc15fdd8f457bdeeb2b8e6fe8fef91f4cdc3910185720a77c7a52092a3` | ✓ |
| `EmailProviderGateway.kt` | `6f152a5837babd0210a0be2774916141acc55928d9e045ee078a1771fd4e64f2` | ✓ |
| `MainNavHost.kt` archivo | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | ✓ |
| `MainNavHost.kt` diff | `8d7c88bbf84d56d2008d2d3611962bcbf3c97f4c2522e3d16fe72c2f0a81018e` (6+/2-) | ✓ |

## Cambios de implementación

- **Nuevo** `FolderCommitCoordinator.kt`: `internal class FolderCommitCoordinator`
  con `Mutex`, `currentGeneration`, `nextGeneration()`, `currentGeneration()` y
  `commitIfValid(generation, block)`. Extraído sin cambios de `EmailRepository.kt`.
- **Ampliado** `EmailMailboxCoordinator.kt`:
  - Constructor: `EmailMailboxCoordinator(dao, providerFactory, writeGuard)`.
  - Dos `FolderCommitCoordinator` independientes (`inboxCommitCoordinator`,
    `trashCommitCoordinator`) — generaciones y mutex separados.
  - `refreshInbox(pageToken: String?)` y `refreshTrash(pageToken: String?)`:
    orden de 11 pasos preservado exactamente (generación, lease, provider,
    fetch, resultado parcial, conversión a `EmailEntity`, validación de
    generación, validación de lease, `replaceFolder` en primera página completa,
    `upsertPreservingBodies` en parcial/paginación, retorno del resultado
    normalizado). Sin valor predeterminado interno; el `= null` pertenece
    exclusivamente a la fachada pública.
  - Mantiene las tres lecturas reactivas (`getInbox`, `getTrash`, `getEmailById`).
  - KDoc actualizado: «Live Room-backed reads and refresh coordination for mailbox folders.»
- **Modificado** `EmailRepository.kt`:
  - `FolderCommitCoordinator` eliminado (movido a su archivo).
  - Propiedades `inboxCommitCoordinator`/`trashCommitCoordinator` eliminadas.
  - `mailboxCoordinator` actualizado a tres parámetros.
  - `refreshInbox`/`refreshTrash` delegados en una línea conservando `= null`
    en la firma pública.
  - Imports `Mutex` y `withLock` retirados (sin uso restante).
  - 20 métodos públicos + `MAX_PDF_SIZE` conservados.

## Contratos verificados

- Inbox y Trash con generaciones y mutex separados.
- Una carpeta nunca invalida o bloquea la otra.
- Primera página nueva invalida refresh/paginación antiguos de la misma carpeta.
- Sección crítica por carpeta: nunca dos commits simultáneos.
- Generación nueva espera si existe un commit vigente (mutex).
- Páginas completas reemplazan, parciales/paginadas fusionan.
- `body`, HTML limpio, metadata PDF y cabeceras preservadas.
- Errores y `CancellationException` del provider se propagan sin transformación.
- No se consulta el provider sin lease.
- Provider dinámico, tokens, resultados y literales `"inbox"`/`"trash"` exactos.
- Sin cambios en API pública, consumidores, DI, DAO, entidades o modelos.

## Validación

### 1. JVM integral

- Comando: `./gradlew testDebugUnitTest --rerun-tasks`
- Resultado: `BUILD SUCCESSFUL` (2m 58s).
- **584/584** pruebas; `FolderCommitCoordinatorTest`: **3/3**; 0 fallos; 0 errores;
  0 omitidas (58 suites).

### 2. Concurrencia JVM

- Comando: `./gradlew testDebugUnitTest --rerun-tasks --tests FolderCommitCoordinatorTest` × 3.
- Resultado: **3/3 por corrida**; 9 ejecuciones acumuladas sin flakiness.

### 3. Instrumentación consolidada

- Emulador: `Medium_Phone_API_36.1` (Android 16 / API 36);
  `ANDROID_SERIAL=emulator-5554`; Pixel 9 no usado.
- Comando: `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
  --rerun-tasks` con `EmailRepositoryReadSyncSearchContractsTest`,
  `SafeRefreshContractsTest` y `PartialPageContractsTest` × 3.
- Resultado por corrida: **32/32**; 3 corridas = **96** ejecuciones acumuladas;
  0 fallos; 0 errores; 0 omitidas.

| Suite (por corrida) | Casos |
|---|---:|
| Lectura, refresh y búsqueda (`EmailRepositoryReadSyncSearchContractsTest`) | 20 |
| Refresh seguro (`SafeRefreshContractsTest`) | 11 |
| Página parcial (`PartialPageContractsTest`) | 1 |
| **Total** | **32** |

### 4. Lint

- Comando: `./gradlew lintDebug --rerun-tasks` (repetido y confirmado por el
  usuario).
- Resultado: `BUILD SUCCESSFUL` (3m 39s); **0 errores**, **65 advertencias**.
- Desglose: Correctness 50: 49 de baseline, incluido
  `ConfigurationScreenWidthHeight`, más 1 ambiental de
  `AndroidGradlePluginVersion`. Performance 11, Usability:Icons 1,
  Productivity 3.
- Variación ambiental: `AndroidGradlePluginVersion` ×2 (Gradle wrapper 9.6.1 vs
  9.7 disponible; archivo intacto desde el commit inicial). Ninguna categoría
  nueva.
- El gate se documenta como **65 advertencias aceptadas** (0 errores, 0
  categorías nuevas). No se modifica Gradle ni se suprimen advertencias dentro
  de este refactor.

### 5. Integridad

- `git diff --check`: limpio.
- `EmailProviderGateway.kt` SHA-256 `6f152a58…64f2`: sin cambios.
- `MainNavHost.kt` y su diff: fingerprints intactos.
- Log de errores Kotlin eliminado (`.kotlin/errors/errors-1786414043390.log`).
- Árbol: solo archivos del allowlist + cambios ajenos (`MainNavHost.kt`,
  `ComposeScreen.kt`).

## Incidencias

- **Lint +1**: `AndroidGradlePluginVersion` adicional (Correctness: 50 en lugar
  de 49). Investigación del usuario confirmó que `ConfigurationScreenWidthHeight`
  ya estaba en la baseline de 64; el incremento es ambiental por detección de
  Gradle 9.7. Aceptado como variación de infraestructura; no se modifica Gradle.
- **KSP incremental**: el primer `connectedDebugAndroidTest --rerun-tasks` falló
  por `EmailDao_Impl.kt` ausente tras mover `FolderCommitCoordinator`. Un
  `./gradlew clean` resolvió el problema. Incidencia de build incremental, no de
  lógica.
- Aviso ambiental conocido del daemon `fsmonitor`: no invalida resultados de Git.

## Cierre

- Commit exclusivo con los cinco archivos del allowlist:
  `refactor(repository): extract mailbox refresh coordination`.
- `ComposeScreen.kt` y `MainNavHost.kt` excluidos del staging (cambios ajenos al
  allowlist).
- Después del commit, el árbol muestra solamente los cambios ajenos.
- Sin push. Sin Pixel 9 (validación física en Etapa 5).
- **Etapa 2 cerrada.** La aprobación de 2.3 no autoriza 3.1 sin su propio plan
  técnico cerrado.
