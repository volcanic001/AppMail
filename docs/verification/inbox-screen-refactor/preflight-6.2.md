# Preflight — Subfase 6.2: Instrumentación y equivalencia visual

Fecha: 2026-08-31 (CST)

## Estado inicial

- Commit base: `b15500f` (`docs(inbox): record JVM, compilation and lint verification results`).
- Allowlist: `preflight-6.2.md`, `resultados-subfase-6.2.md`, `preflight-6.3.md` y `resultados-subfase-6.3.md`.
- Archivos ajenos protegidos, SHA-1:
  - `ComposeScreen.kt`: `706a326dc81cdee274d9a593ff46903c8c349d64`
  - `MainNavHost.kt`: `b1bdcff0725e300662b5156684cae7c9a30c9087`
  - `gradle.properties`: `75b884d288ee107275c34d109b8db2a2042b2283`

## Ejecución prevista

1. Correr tres veces `InboxContentCharacterizationTest` en el emulador.
2. Correr una vez `connectedDebugAndroidTest` completo en el emulador.
3. Correr la suite focal una vez en Pixel 9.
4. Revalidar los pares canónicos baseline/post y sus manifiestos.
