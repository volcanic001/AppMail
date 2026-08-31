# Preflight — Subfase 6.1: JVM, compilación y análisis

Fecha: 2026-08-31 (CST)

## Estado Inicial del Repositorio
- Commit base: `58e057367660c6f252cd7a2b1f40c0266afc5f8b` (refactor(inbox): complete structural cleanup audit)
- Allowlist limitada a: `docs/verification/inbox-screen-refactor/preflight-6.1.md` y `docs/verification/inbox-screen-refactor/resultados-subfase-6.1.md`

## Snapshot SHA-1 de Archivos Ajenos Protegidos
- `ComposeScreen.kt`: `706a326dc81cdee274d9a593ff46903c8c349d64`
- `MainNavHost.kt`: `b1bdcff0725e300662b5156684cae7c9a30c9087`
- `gradle.properties`: `75b884d288ee107275c34d109b8db2a2042b2283`

## Plan de Ejecución
Ejecutar individualmente con `--rerun-tasks --console=plain`:
1. `testDebugUnitTest` completo
2. `testDebugUnitTest` focal Inbox (`com.david.mailapp.feature.inbox.*`): exigir 28/28
3. `compileDebugKotlin`
4. `compileDebugAndroidTestKotlin`
5. `assembleDebug`
6. `assembleDebugAndroidTest`
7. `lintDebug`
8. `git diff --check`
