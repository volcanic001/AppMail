# Diagnóstico — Preflight Macrobenchmark/Perfetto (0R.7)

## Contexto

- **Fecha:** 2026-09-01
- **Subfase:** 0R.7 — preflight de compatibilidad Macrobenchmark/Perfetto
- **Objetivo:** detectar de forma temprana si un dispositivo físico puede ejecutar AndroidX Macrobenchmark con Perfetto antes de iniciar la captura completa de Etapa 0.

## Implementación

Se agregó `MacrobenchmarkPerfettoPreflight`, una prueba mínima en el módulo `:macrobenchmark` que:

- usa el mismo paquete objetivo (`com.david.mailapp`);
- ejecuta una sola iteración;
- usa `FrameTimingMetric`, suficiente para forzar la ruta Macrobenchmark/Perfetto;
- usa `startupMode = null`, alineado con el contrato de captura de Etapa 0;
- lanza la app manualmente con `pressHome()` y `startActivityAndWait()`;
- no depende de Gmail, login, fixtures, contenido de Inbox ni `email_detail_visual_ready`.

El script `tools/performance/run_physical_benchmark.sh` ahora ejecuta este preflight antes de la captura completa, salvo que `RUN_MACROBENCHMARK_PREFLIGHT=false`.

Variables nuevas:

- `PREFLIGHT_ONLY=true`: ejecuta solo el preflight y termina.
- `MACROBENCHMARK_PREFLIGHT_TIMEOUT_SECONDS`: timeout del preflight; default `90`.
- `MACROBENCHMARK_CAPTURE_TIMEOUT_SECONDS`: timeout de la captura completa; default `900`.
- `SKIP_TARGET_INSTALL`: permite preservar la instalación/sesión de la app objetivo.
- `SKIP_BENCHMARK_INSTALL`: permite controlar por separado la instalación del APK de benchmark.

## Verificación local

Comandos ejecutados:

```text
bash -n tools/performance/run_physical_benchmark.sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :macrobenchmark:assembleBenchmark --console=plain
```

Resultado:

- Sintaxis del script: OK.
- `:macrobenchmark:assembleBenchmark`: BUILD SUCCESSFUL.

## Verificación en Huawei JLN-LX3

Comando ejecutado:

```text
TARGET_SERIAL=5PNYD22915400266 REFERENCE_LABEL=huawei-jln-lx3-api31 SKIP_TARGET_INSTALL=true SKIP_BENCHMARK_INSTALL=false PREFLIGHT_ONLY=true tools/performance/run_physical_benchmark.sh
```

Resultado:

```text
java.lang.IllegalStateException: Perfetto unexpected exit code, output = EXITCODE=1
```

Interpretación:

- El dispositivo pasa `atrace/tracefs`.
- El dispositivo no pasa AndroidX Macrobenchmark/Perfetto.
- El fallo ocurre en aproximadamente 15 s, sin depender de Gmail ni de fixtures.
- El preflight evita ejecutar una captura completa que terminaría bloqueada o sin artefactos.

## Hallazgo adicional

Una variante inicial del preflight con `StartupMode.COLD` falló por:

```text
The DROP_SHADER_CACHE broadcast was not received
```

Logcat confirmó una denegación del receiver `androidx.profileinstaller.ProfileInstallReceiver` por `android.permission.DUMP` en Huawei. Ese camino no corresponde al contrato vigente de Etapa 0 porque la captura real usa `startupMode = null`; por eso el preflight final replica `startupMode = null`.

## Decisión

0R.7 queda aprobado como infraestructura defensiva.

Etapa 0 sigue pendiente: solo puede cerrarse con un dispositivo físico que pase, en orden:

1. preflight ADB/dispositivo físico;
2. preflight `atrace/tracefs`;
3. preflight Macrobenchmark/Perfetto;
4. captura completa con artefactos exportables y analizables.
