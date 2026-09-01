# Diagnóstico — Macrobenchmark/Perfetto en Huawei JLN-LX3

## Contexto

- **Fecha:** 2026-09-01
- **Subfase:** 0R.6 — intento de captura física en dispositivo Android alternativo
- **Dispositivo:** Huawei `JLN-LX3`
- **Serial ADB:** `5PNYD22915400266`
- **Android/API:** Android 12 / API 31
- **Build fingerprint:** `HUAWEI/JLN-L03/HWJLN-Q:12/HUAWEIJLN-LX3/103.0.0.317LAMC25:user/release-keys`
- **Comando de captura:** `TARGET_SERIAL=5PNYD22915400266 REFERENCE_LABEL=huawei-jln-lx3-api31 SKIP_INSTALL=true tools/performance/run_physical_benchmark.sh`

## Preflight técnico

El dispositivo sí cumple los preflights básicos que bloquearon al Pixel 9 GrapheneOS:

- `atrace --list_categories` responde correctamente.
- `/sys/kernel/tracing` expone nodos requeridos por el contrato (`events`, `trace`, `trace_marker`, `tracing_on`).
- Batería: 100%.
- Estado térmico: 0.
- Wi-Fi activo.
- Datos móviles desactivados.

Esto confirma que el bloqueo original del Pixel 9 no es atribuible al host, ADB, platform-tools ni al script de preflight.

## Resultado observado

La ejecución limpia de Macrobenchmark entra al primer test:

```text
INSTRUMENTATION_STATUS: class=com.david.macrobenchmark.EmailOpenMacrobenchmark
INSTRUMENTATION_STATUS: test=benchmark_01_plainTextFirstOpen
INSTRUMENTATION_STATUS_CODE: 1
```

Después de esto no avanza a resultado, fallo JUnit, ni extracción de métricas. No se generan artefactos finales de baseline (`runs.csv`, `summary.json`, traces o reportes de benchmark).

La espera normal del test no explica el bloqueo:

- Si `inbox_list` no existiera, el setup debería fallar por timeout.
- Si el fixture no estuviera visible, `Until.findObject(By.textContains(...))` debería fallar en aproximadamente 5 s.
- Si el detalle no llegara a `email_detail_visual_ready`, debería fallar en aproximadamente 15 s.

En cambio, el proceso queda bloqueado alrededor de la infraestructura Macrobenchmark/Perfetto.

## Evidencia de procesos residuales

Tras corridas abortadas se observaron procesos auxiliares vivos:

```text
trace_processor_shell -D --http-port 9001
com.david.macrobenchmark
```

En una corrida previa, múltiples instancias de `trace_processor_shell` quedaron activas simultáneamente sobre el puerto `9001`. Se limpiaron los helpers huérfanos y se repitió la captura sin reinstalar APKs para preservar la sesión. El bloqueo se reprodujo con el entorno limpio.

## Diagnóstico

El Huawei `JLN-LX3` es útil como control diagnóstico de `atrace/tracefs`, pero no es apto actualmente para cerrar la línea base física de Etapa 0 porque Macrobenchmark/Perfetto no completa la ejecución ni produce artefactos verificables.

La causa raíz más probable no está en las trazas de la app ni en los fixtures, sino en la interacción entre AndroidX Macrobenchmark/Perfetto y el entorno del dispositivo Huawei:

- `trace_processor_shell` queda como daemon auxiliar en el dispositivo.
- La instrumentación no devuelve métricas ni excepción JUnit.
- No se producen archivos de salida de Macrobenchmark.
- El bloqueo se reproduce después de limpiar procesos residuales.

## Decisión metodológica

No se declara cierre de Etapa 0 con este dispositivo.

El Huawei queda registrado como:

1. **Control positivo de `atrace/tracefs`:** confirma que el host y scripts pueden detectar un dispositivo con nodos de tracing funcionales.
2. **Control negativo de Macrobenchmark/Perfetto:** confirma que pasar `atrace/tracefs` no basta; el dispositivo también debe completar la suite Macrobenchmark y producir artefactos exportables.

## Opciones siguientes

1. **Conservar contrato actual y usar otro dispositivo físico Android** que pase preflight y complete Macrobenchmark/Perfetto.
2. **Agregar un preflight de compatibilidad Macrobenchmark/Perfetto** antes de intentar la captura completa, para fallar rápido si `trace_processor_shell`/instrumentación quedan bloqueados.
3. **Crear un modo diagnóstico reducido** que ejecute un solo benchmark o una prueba mínima de Perfetto para separar incompatibilidad del dispositivo de problemas de fixture.
4. **No cerrar Etapa 0 todavía** y pasar solo si existe evidencia física completa: manifest, traces, `runs.csv`, `summary.json`, conteos de red y logs saneados.
