# Resultados — Subfase 1.4, baseline técnico existente

## Identificación

- Etapa: 1 — Congelar estado, contratos y cobertura.
- Subfase: 1.4 — Baseline técnico existente.
- Ejecución formal: 2026-08-08, zona CST (`-0600`).
- Commit de referencia: `0ba0f8bbbabb4442a747134f3db64b576837d595`.
- Emulador: `Medium_Phone_API_36.1`, serial `emulator-5554`, Android 16/API 36.

## Resultados Gradle

| Verificación | Resultado | Evidencia |
|---|---|---|
| `./gradlew testDebugUnitTest --rerun-tasks` | Verde | 584 pruebas, 0 fallos, 0 errores, 0 omitidas; 28 tareas ejecutadas; 47 s. |
| `./gradlew assembleDebug` | Verde | `app-debug.apk` generado correctamente. |
| `./gradlew lintDebug` | Verde | 0 errores y 64 advertencias preexistentes. |
| `./gradlew assembleDebugAndroidTest` | Verde | `app-debug-androidTest.apk` generado correctamente. |

Las tres últimas tareas se ejecutaron dentro de una sola invocación Gradle:

```text
./gradlew assembleDebug lintDebug assembleDebugAndroidTest
```

Resultado: `BUILD SUCCESSFUL`, 82 tareas consideradas, 1 ejecutada y 81 `UP-TO-DATE`, en 3 s. Que varias tareas estuvieran actualizadas es válido porque la ejecución JVM inmediatamente anterior había recompilado el mismo árbol de fuentes y ningún archivo cambió entre comandos.

Artefactos comprobados:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
app/build/reports/tests/testDebugUnitTest/index.html
app/build/reports/lint-results-debug.html
```

## Suites instrumentadas focalizadas

Se ejecutó, con repetición forzada y `ANDROID_SERIAL=emulator-5554`, la selección cerrada de seis clases:

| Clase | Casos esperados |
|---|---:|
| `EmailResolutionContractsTest` | 26 |
| `EmailRepositoryActionContractsTest` | 20 |
| `SafeRefreshContractsTest` | 11 |
| `PartialPageContractsTest` | 1 |
| `PdfCancellationContractsTest` | 2 |
| `EmailDetailIntegrationTest` | 11 |
| **Total** | **71** |

Resultado del runner:

```text
Starting 71 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 71 tests on Medium_Phone_API_36.1(AVD) - 16
tests=71, failures=0, errors=0, skipped=0
BUILD SUCCESSFUL in 3m 18s
74 actionable tasks: 74 executed
```

El tiempo agregado reportado por los casos fue 5.943 s; el tiempo Gradle incluye recompilación forzada, empaquetado, instalación y preparación del dispositivo.

Reportes:

```text
app/build/reports/androidTests/connected/debug/index.html
app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml
```

## Incidencia de infraestructura

El primer intento de instrumentación dentro del sandbox terminó antes de iniciar Gradle:

```text
Could not create service of type FileLockContentionHandler
java.net.SocketException: Operation not permitted
```

Clasificación: restricción del sandbox sobre el socket local de Gradle, no fallo de aplicación ni de prueba. Se repitió exactamente la misma selección con autorización fuera del sandbox; esa ejecución completó 71/71 casos en verde. El intento inicial no se contabiliza como ejecución de pruebas.

## Advertencias aceptadas como baseline

No hubo errores de compilación o lint. Se observaron advertencias preexistentes de estas categorías:

- Opciones Gradle `android.builtInKotlin=false` y `android.newDsl=false` deprecadas para AGP 10.
- Funciones Gradle deprecadas con incompatibilidad futura con Gradle 10.
- Cambio futuro del target por defecto de anotaciones Kotlin.
- `LocalLifecycleOwner`, accesos WebView, icono ArrowBack y APIs Compose Test v1 deprecados.
- Uso de `createTempDir` en pruebas y comprobaciones estáticas siempre verdaderas/falsas en algunas pruebas.
- Bibliotecas nativas que no pudieron ser stripped y fueron empaquetadas sin modificación.

`lintDebug` cerró con 0 errores y 64 advertencias. Estas advertencias quedan registradas y no se corregirán dentro del baseline de `EmailRepository`.

## Integridad del árbol de trabajo

Después de todas las verificaciones:

- `EmailRepository.kt` conserva SHA-256 `abcac202041966e53383b40613c5c3d027a93bbb0d8c2616fe16a90a424dbe4b`.
- El cambio previo del usuario en `MainNavHost.kt` conserva SHA-256 `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.
- No se modificó producción, pruebas ni Gradle.
- Los únicos archivos nuevos del plan están bajo `docs/verification/emailrepository-baseline`.

## Criterio de cierre de la subfase 1.4

- Suite JVM completa, forzada y verde: cumplido.
- APK debug compilado: cumplido.
- Lint sin errores: cumplido.
- APK AndroidTest compilado: cumplido.
- Seis suites focalizadas ejecutadas en el emulador previsto: cumplido.
- Resultados guardados por clase/dispositivo: cumplido.
- Fallos preexistentes de aplicación o pruebas: ninguno observado.
- Advertencias e incidencia de infraestructura clasificadas: cumplido.
- Integridad de producción y cambio ajeno verificada: cumplido.
