# Resultados — Subfase 4.3, Resolución y single-flight

## Identificación
- Plan maestro: Refactor estructural de `EmailRepository` — Etapa 4, Subfase 4.3.
- Fecha: 2026-08-11, CST (`-0600`).
- Objetivo: extraer toda la resolución a `EmailResolutionCoordinator`, centralizar trazas en `RepositoryTrace`, y dejar `EmailRepository` como fachada sin lógica propia.

## Implementación
- **Nuevo** `RepositoryTrace.kt`: objeto `internal` con `MAIL_PERF_TAG = "MailPerfTrace"`, `RESOLVE_TAG = "EmailResolve"`, y `now() = SystemClock.elapsedRealtime()`.
- **Nuevo** `EmailResolutionCoordinator.kt`: `internal class EmailResolutionCoordinator(dao, providerFactory, writeGuard)` con `resolveEmailById`, `resolveInternal`, `mapLookupFailure`, `logResolve`, `pendingResolutions` y `CachedRead`. Single-flight con clave `(lease.generation, emailId)`, reglas leader/follower, limpieza terminal, cache-first, provider dinámico, escritura protegida y mapeo de errores sin reformular.
- **Modificado** `EmailRepository.kt`: `resolutionCoordinator` añadido, `resolveEmailById` → delegación de 1 línea, toda la lógica de resolución removida (`pendingResolutions`, `CachedRead`, `resolveInternal`, `mapLookupFailure`, `logResolve`, `RESOLVE_TAG`, `repoNow()`). Imports limpiados (12 removidos). Fachada final: solo construcción, delegaciones, KDoc y `MAX_PDF_SIZE`.
- **Modificados** `EmailContentCoordinator.kt`, `EmailActionCoordinator.kt`, `EmailPdfCoordinator.kt`: `REPO_TAG`/`repoNow()` locales reemplazados por `RepositoryTrace.MAIL_PERF_TAG`/`RepositoryTrace.now()`. Sin cambios de mensajes, categorías ni puntos de medición.

## Gates
- Compilación: `compileDebugKotlin --rerun-tasks` exitosa.
- JVM: 584/584
- Instrumentación en `Medium_Phone_API_36.1(AVD) - 16` (`ANDROID_SERIAL=emulator-5554`):
  - `EmailResolutionContractsTest` 3×29=87 (0 fallos)
- Lint: 0 errores, 65 advertencias (referencia)

## Integridad
- 20 métodos públicos + `MAX_PDF_SIZE` intactos. `EmailRepository` sin lógica propia (solo construcción, delegaciones y constante pública). `EmailProviderGateway`, `EmailMailboxCoordinator` y `FolderCommitCoordinator` con fingerprints intactos. Archivos protegidos (`ComposeScreen.kt`, `MainNavHost.kt`) con hashes coincidentes. `EmailResolutionCoordinator` con visibilidad `internal`. `git diff --check` limpio. Solo archivos del allowlist modificados.

## Cierre
- Commit **`refactor(repository): extract email resolution coordination`** (6 archivos modificados + 2 nuevos, sin push). Etapa 4 aprobada. Subfase 5.1 pendiente de su plan técnico cerrado.
