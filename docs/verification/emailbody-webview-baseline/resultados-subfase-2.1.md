# Resultados — Subfase 2.1: puerta JVM y compilación

## 1. Estado

**COMPLETADA**

## 2. Identidad de ejecución

- **Fecha local**: 2026-08-24 08:25:06 – 08:34:37 CST (-0600).
- **Rama**: `main`.
- **HEAD**: `2a433a67af4ea6f4f27e320133c963a408e80453`.
- **Gradle**: 9.6.1 (build 2026-06-26 14:25:50 UTC, revisión `309d128bd9fe8c0b71311878fc660b9cbaa07c51`; Kotlin 2.3.21, Groovy 4.0.32, Ant 1.10.17).
- **JVM**: 17.0.20.1 (Eclipse Adoptium 17.0.20.1+1); daemon en `/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`.
- **Sistema operativo**: Mac OS X 26.6.2 x86_64.
- **Hash inicial de `EmailBodyWebView.kt`**: `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`.

## 3. Estado inicial protegido

- **Hash de `ComposeScreen.kt`**: `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69`.
- **Hash de `MainNavHost.kt`**: `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.
- **Cambios preexistentes**:
  - `M app/src/main/java/com/david/mailapp/feature/compose/ComposeScreen.kt` (cambio ajeno congelado);
  - `M app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt` (cambio ajeno congelado);
  - artefactos sin seguimiento de la Subfase 1.3:
    `docs/verification/emailbody-webview-baseline/fixtures/01-html-simple.html`,
    `02-newsletter-tabla.html`, `03-imagen-remota.html`, `04-imagen-data.html`,
    `05-enlace-externo.html`, `capturas/README.md`, `trazas/README.md`,
    `fixtures-y-evidencia.md` y los documentos previos
    `contratos-observables.md` y `registro-tecnico.md`.

## 4. Resumen de puertas

| ID | comando | inicio | fin | duración (s) | exit code | resultado | artefacto/reporte |
|---|---|---|---|---|---|---|---|
| G01 | `./gradlew testDebugUnitTest --rerun-tasks --console=plain` | 08:25:06 -0600 | 08:26:28 -0600 | 82 | 0 | PASA | `app/build/reports/tests/testDebugUnitTest/index.html` |
| G02 | `./gradlew compileDebugKotlin --rerun-tasks --console=plain` | 08:26:51 -0600 | 08:27:09 -0600 | 18 | 0 | PASA | — |
| G03 | `./gradlew assembleDebug --rerun-tasks --console=plain` | 08:27:21 -0600 | 08:27:54 -0600 | 33 | 0 | PASA | `app/build/outputs/apk/debug/app-debug.apk` |
| G04 | `./gradlew assembleRelease --rerun-tasks --console=plain` | 08:28:06 -0600 | 08:32:15 -0600 | 249 | 0 | PASA | `app/build/outputs/apk/release/app-release.apk` |
| G05 | `./gradlew lintDebug --rerun-tasks --console=plain` | 08:32:30 -0600 | 08:34:37 -0600 | 127 | 0 | PASA | `app/build/reports/lint-results-debug.xml` (+ HTML/TXT) |

## 5. G01 — Suite JVM debug

- Comando ejecutado y resultado: `BUILD SUCCESSFUL in 1m 21s`, exit code `0`.
- Conteo real desde los XML `app/build/test-results/testDebugUnitTest/TEST-*.xml`:
  - suites: **58**
  - tests: **584**
  - failures: **0**
  - errors: **0**
  - skipped: **0**
- Reporte HTML: `app/build/reports/tests/testDebugUnitTest/index.html` (existe).

## 6. G02 — Compilación Kotlin debug

- Resultado: `BUILD SUCCESSFUL in 18s`, exit code `0`.
- Warnings de compilación Kotlin observados (11):
  - `UiText.kt:25:9` — anotación aplicada solo al parámetro de valor; en el futuro también al campo.
  - `EmailDetailReadFailureEffect.kt:22:26` — `LocalLifecycleOwner` deprecado (mover a `lifecycle-runtime-compose`).
  - `EmailDetailRoute.kt:34:26` — `LocalLifecycleOwner` deprecado.
  - `EmailBodyWebView.kt:72:26` — `LocalLifecycleOwner` deprecado.
  - `EmailBodyWebView.kt:538:5` — `allowFileAccessFromFileURLs` deprecado (Java).
  - `EmailBodyWebView.kt:539:5` — `allowUniversalAccessFromFileURLs` deprecado (Java).
  - `SearchTopBar.kt:98:35` — `Icons.Filled.ArrowBack` deprecado; usar `Icons.AutoMirrored.Filled.ArrowBack`.
  - `ChangelogSettingsScreen.kt:40:5` — anotación aplicada solo al parámetro de valor.
  - `ChangelogSettingsScreen.kt:41:5` — idem.
  - `DrawerDestination.kt:16:5` — idem.
  - `Color.kt:23:5` — idem.
- Warnings generales de Gradle observados (3):
  - `WARNING: The option setting 'android.builtInKotlin=false' is deprecated.`
  - `WARNING: The option setting 'android.newDsl=false' is deprecated.`
  - `Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.`

## 7. G03/G04 — APK

- **Debug**: `app/build/outputs/apk/debug/app-debug.apk`
  - tamaño: **25 642 958 bytes**
  - SHA-256: `c01f6f49889ef49ef867ef254420eaa1204d628e80e63f7d47cd29e4d61f2d28`
- **Release**: `app/build/outputs/apk/release/app-release.apk` (único APK generado)
  - tamaño: **4 952 662 bytes**
  - SHA-256: `c547a0e91e33f1c85102f3820caa4dbbf2818fd68438c43e3528b39fedd97b15`

Los hashes identifican el artefacto en esta ejecución; no constituyen un
contrato de hash reproducible.

## 8. G05 — Lint debug

- Resultado: `BUILD SUCCESSFUL in 2m 7s`, exit code `0`.
- Conteo por severidad desde `app/build/reports/lint-results-debug.xml`:
  - Error: **0**
  - Fatal: **0**
  - Warning: **66**
  - Total: **66**
- Conteo por `issue/@id`:

| ID | conteo |
|---|---|
| AndroidGradlePluginVersion | 2 |
| ConfigurationScreenWidthHeight | 1 |
| FrequentlyChangingValue | 6 |
| GradleDependency | 17 |
| IconLocation | 1 |
| ModifierParameter | 15 |
| NewerVersionAvailable | 15 |
| ObsoleteSdkInt | 1 |
| OldTargetApi | 1 |
| UnusedResources | 3 |
| UseKtx | 3 |
| UseOfNonLambdaOffsetOverload | 1 |

- Reportes existentes: `app/build/reports/lint-results-debug.xml`,
  `app/build/reports/lint-results-debug.html`,
  `app/build/reports/lint-results-debug.txt`.

## 9. Advertencias clasificadas

- **Compilador** (deprecaciones/avisos emitidos al compilar fuentes): los 11
  warnings Kotlin de la sección 6, incluida la deprecación de
  `LocalLifecycleOwner` y de `allowFileAccessFromFileURLs` /
  `allowUniversalAccessFromFileURLs` en `EmailBodyWebView.kt`, y el aviso de
  anotaciones a futuro en 5 archivos.
- **Gradle/AGP/JDK** (entorno de build): `android.builtInKotlin=false`
  deprecado, `android.newDsl=false` deprecado y "Deprecated Gradle features…
  incompatible with Gradle 10".
- **Lint**: 66 warnings, 0 Error, 0 Fatal, distribuidos en 12 IDs (sección 8).
- **Ambientales** (disponibilidad de versiones / dependientes del SDK/host):
  `NewerVersionAvailable` (15), `AndroidGradlePluginVersion` (2) y
  `GradleDependency` (17), que dependen del entorno y pueden variar.

Como producción, pruebas y configuración permanecieron idénticas al HEAD de
referencia, estas advertencias se registran como baseline actual de la
Subfase 2.1. No se afirma identidad histórica de cada texto al no existir
evidencia comparativa directa; no se ocultó, corrigió ni descartó ninguna.

## 10. Incidencias y repeticiones

**Ninguna.** Las cinco puertas pasaron a la primera ejecución; no hubo
repeticiones ni fallos.

## 11. Integridad final

- Hash final de `EmailBodyWebView.kt`:
  `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`.
- Hash final de `ComposeScreen.kt`:
  `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69`.
- Hash final de `MainNavHost.kt`:
  `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.
- `git diff --check`: sin salida.
- Estado Git final: únicamente los dos cambios ajenos congelados
  (`ComposeScreen.kt`, `MainNavHost.kt`) y los artefactos sin seguimiento del
  baseline (10 documentos previos + `resultados-subfase-2.1.md`). Sin cambios
  en producción ni configuración. Sin commit.

## 12. Conclusión

Puerta global: **GO** para la Subfase 2.2. Las cinco puertas G01–G05 pasaron
con exit code 0 y la auditoría de integridad no registró desviaciones.
