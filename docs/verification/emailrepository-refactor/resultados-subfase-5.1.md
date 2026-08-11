# Resultados — Subfase 5.1, JVM, build y lint

## Identificación
- Plan maestro: Refactor estructural de `EmailRepository` — Etapa 5, Subfase 5.1.
- Fecha: 2026-08-11, CST (`-0600`).
- Carácter: exclusivamente de validación y evidencia. Sin modificaciones a producción, pruebas, Gradle, recursos ni configuración.

## Estado de entrada
- HEAD: `8530fc6b2f8ab74cdd7242843643a269dbfce46b` — `refactor(repository): extract email resolution coordination`.
- Rama `main`: 10 commits locales sin push.
- `EmailRepository.kt`: 177 líneas, 8,093 bytes, SHA-256 `0e3a1520b91fd3579ae41d809c52282052907eb7d058f426272c50fb651dec4a`.
- API pública: 20 métodos + `MAX_PDF_SIZE` confirmados (21 entradas públicas).
- Staging vacío; solo `ComposeScreen.kt` (4+/2−) y `MainNavHost.kt` (6+/2−) modificados.

## Fingerprints protegidos (iniciales y finales idénticos)
- `ComposeScreen.kt`: archivo `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69`; diff `5c4c94a32cffbe928f7bed1e2f9dbeff3fc35319ca6afb555e44e4aeb34dfd53`.
- `MainNavHost.kt`: archivo `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`; diff `8d7c88bbf84d56d2008d2d3611962bcbf3c97f4c2522e3d16fe72c2f0a81018e`.

## Gates ejecutados

### 1. JVM integral — `./gradlew testDebugUnitTest --rerun-tasks`
- Duración: 42 s.
- XML: **58 suites, 584/584 pruebas, 0 fallos, 0 errores, 0 omisiones**.

### 2. APKs — `./gradlew assembleDebug assembleDebugAndroidTest --rerun-tasks`
- Duración: 1 m 03 s.
- `app-debug.apk`: 25,642,958 bytes, 2026-08-11 08:17, SHA-256 `e1531ab9e4cd940951451242e7c60e8ecfd5aa9bc99b2acf6f70e9412d176e01`.
- `app-debug-androidTest.apk`: 1,470,979 bytes, 2026-08-11 08:17, SHA-256 `6b1322f2e43f4c0b5839af03eab6b38ce4a6528da36dbe9fcddee1bb725a5b12`.
- `unzip -t`: integridad ZIP correcta en ambos (`No errors detected in compressed data`).
- Tamaños coincidentes con la baseline; artefactos regenerados en esta subfase y hashes actuales registrados.

### 3. Lint — `./gradlew lintDebug --rerun-tasks`
- Duración: 1 m 32 s.
- **0 errores, 0 información, 65 advertencias** (referencia esperada).
- Desglose por categoría: Correctness 50, Performance 11, Productivity 3, Usability:Icons 1.
- Única variación ambiental documentada: `AndroidGradlePluginVersion` (Gradle 9.6.1 → 9.7.0 disponible; AGP 9.0.0 → 9.3.1 disponible), ambas en `Correctness`. Corresponde al incremento aceptado de 64→65 desde la baseline original.

## Integridad
- Constructor, 20 métodos públicos y `MAX_PDF_SIZE` intactos tras todas las ejecuciones.
- Ninguna ejecución modificó archivos versionados: `git status` posterior idéntico al inicial.
- `git diff --check` limpio; staging vacío.
- Sin emulador ni Pixel 9 (no corresponde a esta subfase).

## Decisión
- **GO**. Sin fallos funcionales, sin cambios de API, sin errores lint atribuibles al refactor, sin archivos fuera del allowlist.

## Cierre
- Subfase 5.1 **aprobada**; Etapa 5 en curso; 5.2–5.4 pendientes.
- Sin commit ni push: las evidencias de 5.1–5.3 permanecen sin staging y se cerrarán en el commit documental único de la Subfase 5.4.
