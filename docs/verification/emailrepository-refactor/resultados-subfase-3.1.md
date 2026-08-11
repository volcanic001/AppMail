# Resultados — Subfase 3.1, Acciones remotas y reconciliación

## Identificación y alcance

- Plan maestro: Refactor estructural conservador de `EmailRepository` — Etapa 3
  (Mutaciones y contenido), Subfase 3.1 (Acciones remotas y reconciliación).
- Fecha: 2026-08-10, zona CST (`-0600`).
- Objetivo: extraer las cuatro mutaciones (`moveToTrash`, `restoreFromTrash`,
  `deletePermanently`, `markAsRead`) y los dos helpers privados
  (`commitWithReconcile`, `reconcileFolder`) hacia un nuevo
  `EmailActionCoordinator`, conservando exactamente remote-first, protección de
  sesión, persistencia local y reconciliación por carpeta. La Etapa 3 permanece
  abierta para 3.2 y 3.3.

## Estado de entrada

- HEAD: `e5a7529` — commit documental del cierre de Etapa 2.
- `EmailRepository.kt`: `b5b43a70…48808`.
- `EmailProviderGateway.kt`: `6f152a58…64f2`.
- `EmailMailboxCoordinator.kt`: `d5ecdade…3ed8`.
- `FolderCommitCoordinator.kt`: `fa2190dd…fe62`.
- `MainNavHost.kt`: archivo `a6840cfc…a088`, diff `8d7c88bb…018e` (6+/2-).
- `ComposeScreen.kt`: archivo `2505050c…5e69`, diff `5c4c94a3…d53` (4+/2-).

## Cambios de implementación

- **Nuevo** `EmailActionCoordinator.kt`: `internal class EmailActionCoordinator(dao, providerFactory, writeGuard)`.
  - `moveToTrash`, `restoreFromTrash`, `deletePermanently`, `markAsRead`:
    remote-first inalterado (captura lease → provider dinámico → acción remota
    → commitWithReconcile). Órdenes de carpeta: Inbox→Trash, Trash→Inbox, Trash
    según acción.
  - `commitWithReconcile` (private): intenta commit local; si falla,
    reconciliación por carpeta (continúa con la siguiente si una falla);
    Failure(UNKNOWN, remoteApplied=true) ante commit rechazado.
  - `reconcileFolder` (private): fetch completo de la carpeta +
    replaceFolder/upsertPreservingBodies según completitud.
  - `REPO_TAG = "MailPerfTrace"` duplicado para preservar logs idénticos.
  - Sin dependencias de PDF, caché o cuerpo.
- **Modificado** `EmailRepository.kt`:
  - `private val actionCoordinator = EmailActionCoordinator(dao, providerFactory, writeGuard)`.
  - Las cuatro mutaciones → delegaciones de una línea conservando firmas, KDoc, visibilidad.
  - `commitWithReconcile` y `reconcileFolder` eliminados del repositorio.
  - `provider` dinámico conservado (usado por cuerpo, inline y PDF).
  - 529 líneas (reducción de 128); 20 métodos públicos + `MAX_PDF_SIZE` intactos.

## Validación

### 1. JVM integral

- Comando: `./gradlew testDebugUnitTest --rerun-tasks`
- Resultado: `BUILD SUCCESSFUL`.
- **584/584** pruebas; 0 fallos; 0 errores; 0 omitidas (58 suites).

### 2. Instrumentación de acciones

- Emulador: `Medium_Phone_API_36.1` (Android 16 / API 36);
  `ANDROID_SERIAL=emulator-5554`; Pixel 9 no usado.
- Comando: `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
  --rerun-tasks` con `EmailRepositoryActionContractsTest` × 3.
- Resultado por corrida: **25/25**; 3 corridas = **75** ejecuciones acumuladas;
  0 fallos; 0 errores; 0 omitidas.

### 3. Lint

- Comando: `./gradlew lintDebug --rerun-tasks`
- Resultado: `BUILD SUCCESSFUL`; **0 errores**, **65 advertencias**.
- Referencia ambiental de 65 (50 Correctness, 11 Performance, 1 Usability:Icons,
  3 Productivity). Sin nuevas advertencias atribuibles a los archivos del
  allowlist.

### 4. Integridad

- `git diff --check`: limpio.
- 20 métodos públicos + `MAX_PDF_SIZE`: intactos.
- `EmailActionCoordinator`: `internal`.
- `AppContainer` y consumidores: sin cambios.
- `EmailProviderGateway` (`6f152a58…`), `EmailMailboxCoordinator`
  (`d5ecdade…`), `FolderCommitCoordinator` (`fa2190dd…`), `MainNavHost`
  (`a6840cfc…` y diff `8d7c88bb…`): fingerprints intactos.
- Solo los archivos del allowlist modificados.

## Incidencias

- **Import incorrecto**: `EmailActionResult` estaba importado erróneamente de
  `domain.model`; se corrigió eliminando el import ya que la clase está en el
  mismo paquete `data.repository`. Detectado por compilación, corregido antes
  de validar.
- Aviso ambiental conocido del daemon `fsmonitor`: no invalida resultados de Git.

## Cierre

- Commit exclusivo con los cuatro archivos del allowlist:
  `refactor(repository): extract action coordination`.
- `MainNavHost.kt` y `ComposeScreen.kt` excluidos del staging.
- Sin push. Sin Pixel 9 (Etapa 5).
- La aprobación de 3.1 no autoriza 3.2 sin su plan técnico cerrado.
