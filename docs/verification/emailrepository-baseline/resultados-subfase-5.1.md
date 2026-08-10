# Resultados — Subfase 5.1, validación JVM, build y análisis estático

## Identificación

- Etapa: 5 — Validación integral y cierre del baseline.
- Subfase: 5.1 — Validación JVM, build y análisis estático.
- Ejecución: 2026-08-10, hora de inicio ~06:04 CST (`-0600`).
- Commit verificado: `a96582a` (HEAD).

## Precondiciones

| Condición | Estado |
|---|---|
| HEAD coincide con el commit esperado | `a96582a` ✓ |
| `EmailRepository.kt` SHA-256 | `abcac202…be4b` ✓ |
| `MainNavHost.kt` SHA-256 | `a6840cfc…088` ✓ |
| Archivos de producción modificados desde `0ba0f8b` | Solo `MainNavHost.kt` (6 líneas, cambio del usuario) ✓ |
| Archivos Gradle modificados desde `0ba0f8b` | Ninguno ✓ |
| Cambios en `app/src/test` desde `00d3881` (Etapa 1) | Ninguno ✓ |

## Ejecución

### 1. Suite JVM (`./gradlew testDebugUnitTest --rerun-tasks`)

| Métrica | Valor |
|---|---|
| Resultado | BUILD SUCCESSFUL (2 min 34 s) |
| Pruebas totales | 584 (idéntico al baseline de 1.4) |
| Fallos | 0 |
| Errores | 0 |
| Omitidas | 0 |
| Tareas ejecutadas | 28 |

Comparación con 1.4: sin variación. El source set `app/src/test` no recibió cambios desde la Etapa 1, por lo que el conteo de 584 y el resultado verde se mantienen.

### 2. Build de APK (`./gradlew assembleDebug assembleDebugAndroidTest`)

| Artefacto | Existencia | Tamaño |
|---|---|---|
| `app-debug.apk` | Sí | 25 642 958 bytes (∼25.6 MB) |
| `app-debug-androidTest.apk` | Sí | 1 594 848 bytes (∼1.6 MB) |

Resultado: BUILD SUCCESSFUL en 7 s, 73 tareas actualizadas.

### 3. Análisis estático (`./gradlew lintDebug`)

| Métrica | Valor |
|---|---|
| Resultado | BUILD SUCCESSFUL (47 s) |
| Errores | 0 |
| Advertencias | 64 |

Comparación con 1.4: idéntico (0 errores, 64 advertencias preexistentes). No aparecieron advertencias nuevas ni de mayor severidad. Las categorías y los hallazgos individuales coinciden con el baseline conocido; las 64 advertencias corresponden a `GradleDependency`, `NewerVersionAvailable`, `ModifierParameter`, `FrequentlyChangingValue`, `UnusedResources` y otras categorías preexistentes documentadas en la Subfase 1.4.

## Auditoría post-Gradle

| Condición | Estado |
|---|---|
| `EmailRepository.kt` SHA-256 inalterado | `abcac202…be4b` ✓ |
| `MainNavHost.kt` SHA-256 inalterado | `a6840cfc…088` ✓ |
| Archivos nuevos en producción o Gradle | Ninguno ✓ |
| Working tree | El único cambio no documental es `MainNavHost.kt`; además existen los cambios documentales permitidos de la Subfase 5.1 ✓ |

## Integridad y cierre

- JVM: 584 pruebas, 0 fallos (idéntico a 1.4).
- Ambos APK generados correctamente.
- Lint: 0 errores, 64 advertencias (idéntico a 1.4, sin regresiones).
- Producción y Gradle permanecen congelados.
- No se crea commit; la Subfase 5.1 queda cerrada y la 5.2 pendiente.
