# Resultados — Subfase 1.2, Gate técnico de entrada

## Identificación y alcance

- Plan maestro: Refactor estructural conservador de `EmailRepository` — Etapa 1,
  Subfase 1.2 (Gate técnico de entrada).
- Fecha: 2026-08-10, zona CST (`-0600`).
- Objetivo: demostrar que el árbol aprobado en 1.1 compila y conserva
  íntegramente la baseline antes de modificar `EmailRepository.kt`.
- Alcance: JVM, build, lint e instrumentación focal (137 contratos). No se
  modificó producción, pruebas, Gradle ni configuración.

## Entorno de ejecución

| Campo | Valor |
|---|---|
| Proyecto | `/Users/david/Desktop/MailApp 0.3.0 2` |
| Gradle | Daemon Gradle 9.6.1 (proyecto) |
| Emulador | `Medium_Phone_API_36.1` — Android 16 / API 36 (`sdk_gphone64_x86_64`) |
| Serial fijado | `ANDROID_SERIAL=emulator-5554` |
| Arranque del AVD | Sin `-wipe-data` y sin carga de snapshots (`-no-snapshot-load -no-snapshot-save`) |
| Dispositivos adicionales | Pixel 9 conectado vía TLS; **no utilizado** conforme al plan |
| Boot completo | `sys.boot_completed=1` antes de la instrumentación |

## 1. JVM

- Comando: `./gradlew testDebugUnitTest --rerun-tasks`
- Resultado: `BUILD SUCCESSFUL` (1m 12s).
- Conteos XML (`app/build/test-results/testDebugUnitTest/`): 58 suites.
- Total: **584/584** pruebas; 0 fallos; 0 errores; 0 omitidas.
- `FolderCommitCoordinatorTest`: **3/3**, completando junto con la
  instrumentación los 140 contratos directos.

## 2. Build

- Comando: `./gradlew assembleDebug assembleDebugAndroidTest --rerun-tasks`
- Resultado: `BUILD SUCCESSFUL` (49s).
- Artefactos verificados (existencia y tamaño no nulo):

| Artefacto | Tamaño |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 25,642,958 bytes |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 1,470,979 bytes |

## 3. Lint

- Comando: `./gradlew lintDebug --rerun-tasks`
- Resultado: `BUILD SUCCESSFUL` (1m 22s).
- Reporte: `app/build/reports/lint-results-debug.xml`.
- Conteos: **0 errores**, **64 advertencias**, 0 información.
- Categorías: Correctness 49, Performance 11, Usability:Icons 1, Productivity 3
  — idénticas a la baseline (ninguna categoría o severidad nueva).

## 4. Instrumentación focal

- Comando:
  `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks`
  con las nueve clases focales vía
  `-Pandroid.testInstrumentationRunnerArguments.class=...`.
- Resultado: `BUILD SUCCESSFUL` (5m 31s); `137/137` tests completados,
  `0 skipped`, `0 failed`.
- Conteos por suite (XML `TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml`):

| Suite | Casos |
|---|---:|
| Resolución (`EmailResolutionContractsTest`) | 29 |
| Acciones (`EmailRepositoryActionContractsTest`) | 25 |
| Lectura, refresh y búsqueda (`EmailRepositoryReadSyncSearchContractsTest`) | 20 |
| Refresh seguro (`SafeRefreshContractsTest`) | 11 |
| Página parcial (`PartialPageContractsTest`) | 1 |
| Contenido (`EmailRepositoryContentContractsTest`) | 19 |
| PDF (`EmailRepositoryPdfContractsTest`) | 21 |
| Cancelación PDF (`PdfCancellationContractsTest`) | 2 |
| Cuenta y envío (`EmailRepositoryAccountSendContractsTest`) | 9 |
| **Total** | **137** |

## 5. Incidencias

- El clasificador de la sesión (modo Auto) bloqueó temporalmente la ejecución
  de comandos Gradle y `date` al inicio de la subfase
  («Auto mode classifier unavailable — blocked for safety»). Se documenta como
  restricción de infraestructura de la sesión, no como fallo de aplicación.
  Se resolvió cambiando la sesión a modo YOLO con aprobación explícita del
  usuario; ningún comando del plan se modificó.
- Aviso ambiental conocido del daemon `fsmonitor`: no invalida resultados de Git.
- Advertencias Gradle heredadas (`android.builtInKotlin`, `android.newDsl`,
  SDK XML v4, deprecaciones de Kotlin) ya presentes en la baseline; sin cambios.
- Durante la auditoría de cierre apareció una edición no autorizada y ajena a
  esta subfase en `ComposeScreen.kt`: `width(52.dp)` había cambiado a
  `widthIn(min = 52.dp)` (2 inserciones y 2 eliminaciones). La edición quedó
  fechada a las 16:50:57, después de generar los APK a las 16:48–16:49, no
  pertenecía a ningún commit o rama y el usuario confirmó que no era suya.
- Antes de restaurar se conservó una copia recuperable en
  `/private/tmp/MailApp-ComposeScreen-unknown-20260810-165057.kt`, con SHA-256
  `cb7af8db5c82366bc41661c8d958b59d774cb9bebdf6598abad880b28f3e0492`.
  Se restauró exclusivamente `ComposeScreen.kt` desde HEAD y se confirmó su
  SHA-256 original
  `e41f2a6a1d4097af3a9d74ad5456d5aff8d3de97d652e0a3094742679a241486`,
  sin diff residual. La instrumentación utilizó los APK ya empaquetados antes
  de esa edición, por lo que no fue necesario repetir Gradle.

## 6. Auditoría de hashes (post-ejecución)

| Control | Valor | Estado |
|---|---|---|
| HEAD | `aef2d02e96d0b972b6156cc0cde20e18374fa5f8` | ✓ |
| `EmailRepository.kt` SHA-256 | `abcac202041966e53383b40613c5c3d027a93bbb0d8c2616fe16a90a424dbe4b` | ✓ |
| `MainNavHost.kt` SHA-256 | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` | ✓ |
| Diff protegido SHA-256 | `8d7c88bbf84d56d2008d2d3611962bcbf3c97f4c2522e3d16fe72c2f0a81018e` | ✓ |
| Diff stat protegido | 6 inserciones, 2 eliminaciones | ✓ |
| `ComposeScreen.kt` SHA-256 restaurado | `e41f2a6a1d4097af3a9d74ad5456d5aff8d3de97d652e0a3094742679a241486` | ✓ |
| Diff de `ComposeScreen.kt` | Ausente | ✓ |
| API pública | 20 métodos + `MAX_PDF_SIZE` | ✓ |
| `git diff --check` | Limpio | ✓ |
| Staging | Vacío | ✓ |
| Árbol | Solo `MainNavHost.kt` + los dos documentos propios | ✓ |

## 7. Criterio GO/NO-GO

**GO.** Todos los gates se cumplieron sin divergencias funcionales, sin
flakiness, sin archivos fuera del allowlist y sin modificación de los
fingerprints protegidos:

- JVM 584/584 (incluye `FolderCommitCoordinatorTest` 3/3).
- Build correcto con ambos APKs generados y no nulos.
- Lint 0 errores / 64 advertencias heredadas, sin categorías nuevas.
- Instrumentación focal 137/137.
- 140 contratos directos consolidados (137 instrumentados + 3 JVM del coordinador).
- Hashes intactos y cierre documental preparado para el commit exclusivo de
  los dos documentos autorizados.

Auditoría correctiva finalizada el `2026-08-10 17:37:11 -0600` (CST).

La aprobación de 1.2 cierra la Etapa 1, pero **no** autoriza la Subfase 2.1:
esta requiere su plan técnico cerrado y aprobación explícita.
