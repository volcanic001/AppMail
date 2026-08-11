# Resultados — Subfase 2.2, Lecturas de Mailbox

## Identificación y alcance

- Plan maestro: Refactor estructural conservador de `EmailRepository` — Etapa 2
  (Accesos simples y sincronización), Subfase 2.2 (Lecturas de Mailbox).
- Fecha: 2026-08-10, zona CST (`-0600`).
- Objetivo: extraer exclusivamente `getInbox`, `getTrash` y `getEmailById` hacia
  `EmailMailboxCoordinator`, manteniendo exactamente la lógica, API pública y
  comportamiento reactivo de Room. `refreshInbox`, `refreshTrash` y los
  coordinadores de generación quedan intactos para la Subfase 2.3.

## Estado de entrada

| Control | Valor | Estado |
|---|---|---|
| HEAD | `f33afbd` — `refactor(repository): extract provider gateway` | ✓ |
| `EmailRepository.kt` SHA-256 | `5abaac496be091f389e1762c7a172d7f2696c88cb43fce6769d335d9901f4711` | ✓ |
| `EmailProviderGateway.kt` SHA-256 | `6f152a5837babd0210a0be2774916141acc55928d9e045ee078a1771fd4e64f2` | ✓ |
| `MainNavHost.kt` archivo | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | ✓ |
| `MainNavHost.kt` diff | `8d7c88bbf84d56d2008d2d3611962bcbf3c97f4c2522e3d16fe72c2f0a81018e` (6+/2-) | ✓ |
| Cambios locales al entrar | MainNavHost (previo) + corrección documental del usuario en registro-tecnico.md | ✓ |

## Cambios de implementación

- **Nuevo** `EmailMailboxCoordinator.kt`:
  - `internal class EmailMailboxCoordinator(private val dao: EmailDao)`.
  - `getInbox()`: `dao.getByFolder("inbox").map { it.toDomain() }`.
  - `getTrash()`: `dao.getByFolder("trash").map { it.toDomain() }`.
  - `getEmailById(emailId)`: `dao.getById(emailId).map { it?.toDomain() }`.
  - Sin manejo de errores, dispatchers, caché, provider, `SessionWriteGuard`
    ni estado mutable; depende únicamente de `EmailDao`.
- **Modificado** `EmailRepository.kt`:
  - `private val mailboxCoordinator = EmailMailboxCoordinator(dao)`.
  - Los tres cuerpos sustituidos por delegaciones.
  - Import `kotlinx.coroutines.flow.map` retirado (ya no se usa).
  - Conservados firmas, visibilidad, KDoc, tipos `Flow`, constructor y 20
    métodos públicos.
- **No movidos** en esta subfase: `refreshInbox`, `refreshTrash`,
  `inboxCommitCoordinator`, `trashCommitCoordinator`, `FolderCommitCoordinator`
  ni las conversiones usadas por refresh, resolución, acciones o contenido.

## Contratos verificados

- Flows vivos respaldados exclusivamente por Room (`EmailDao`).
- Inbox usa `"inbox"` y Trash `"trash"` (literales idénticos a la
  implementación anterior).
- Orden de carpeta determinado por el DAO (timestamp DESC).
- `getEmailById` preserva la secuencia null → entidad → actualización → null.
- Conversión completa `EmailEntity.toDomain()` (todos los campos ricos).
- Las lecturas no consultan provider ni escriben en Room.
- Sin cambios en API, consumidores, ViewModels, adaptadores, DI ni base de datos.

## Validación

### 1. JVM

- Comando: `./gradlew testDebugUnitTest --rerun-tasks`
- Resultado: `BUILD SUCCESSFUL` (1m 28s); resultados a las 19:36:27.
- **584/584** pruebas; 0 fallos; 0 errores; 0 omitidas (58 suites).

### 2. Instrumentación focal

- Emulador: `Medium_Phone_API_36.1` (Android 16 / API 36);
  `ANDROID_SERIAL=emulator-5554`; Pixel 9 no usado.
- Comando: `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
  --rerun-tasks` con `EmailRepositoryReadSyncSearchContractsTest`.
- Resultado: `BUILD SUCCESSFUL` (2m 35s); **20/20**; 0 fallos; 0 errores;
  0 omitidas (XML a las 19:39:27).
- Confirma los cinco contratos directos de lectura: Inbox vivo y ordenado;
  Trash vivo con inserción/eliminación; aislamiento de carpetas; secuencia
  reactiva por ID; mapeo completo de entidad rica.

### 3. Lint

- Comando: `./gradlew lintDebug --rerun-tasks`
- Resultado: `BUILD SUCCESSFUL` (2m 17s); reporte a las 19:41:57.
- **0 errores**, **64 advertencias** heredadas; categorías Correctness 49,
  Performance 11, Usability:Icons 1, Productivity 3 — ninguna nueva.

### 4. Integridad

- `git diff --check`: limpio.
- `EmailProviderGateway.kt` SHA-256 `6f152a58…64f2`: sin cambios.
- `MainNavHost.kt` y su diff: fingerprints intactos.
- Árbol: solo `EmailRepository.kt` (M), `registro-tecnico.md` (M),
  `EmailMailboxCoordinator.kt` (nuevo) y `MainNavHost.kt` (cambio previo).
  Ningún archivo fuera del allowlist modificado.

## Incidencias

- Ninguna. Las advertencias de compilación y Gradle son las heredadas de la
  baseline (deprecaciones Kotlin/AGP, `LocalLifecycleOwner`, etc.), sin cambios.
- Aviso ambiental conocido del daemon `fsmonitor`: no invalida resultados de Git.

## Cierre

- Commit exclusivo: `refactor(repository): extract mailbox reads`.
- Incluye únicamente los cuatro archivos del allowlist:
  `EmailMailboxCoordinator.kt`, `EmailRepository.kt`, `registro-tecnico.md`
  (incorporando la corrección documental local del usuario sobre el estado de
  2.1) y `resultados-subfase-2.2.md`.
- Después del commit, el árbol muestra solamente el cambio previo de
  `MainNavHost.kt`.
- Sin push. Sin Pixel 9 (validación física en Etapa 5).
- La aprobación de 2.2 no autoriza 2.3 sin su propio plan técnico cerrado.
