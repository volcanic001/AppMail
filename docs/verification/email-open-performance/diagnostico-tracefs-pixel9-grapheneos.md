# Diagnóstico de Bloqueo TraceFS en Pixel 9 GrapheneOS

## Resumen

- **Fecha:** 2026-09-01
- **Objetivo:** Determinar por qué la captura física 0R.4 no puede ejecutarse en Google Pixel 9.
- **Estado:** BLOQUEADO POR ENTORNO EXTERNO.
- **Conclusión:** El Pixel 9 con GrapheneOS expone `tracefs` montado, pero sin los nodos ftrace requeridos por `atrace` y AndroidX Macrobenchmark. La línea base física de Etapa 0 no puede declararse cerrada con este dispositivo en su estado actual.

## Dispositivo Objetivo

- **Modelo:** Google Pixel 9
- **Device:** `tokay`
- **Android:** 17 / API 37
- **Build fingerprint:** `google/tokay/tokay:17/CP2A.260805.005/2026081301:user/release-keys`
- **Build type:** `user`
- **ADB debuggable:** `ro.debuggable=0`
- **SELinux:** `Enforcing`
- **Tracing service:** `persist.traced.enable=1`

## Evidencia Observada

`tracefs` aparece montado:

```text
tracefs /sys/kernel/tracing tracefs rw,seclabel,relatime,gid=3012
```

El usuario `shell` pertenece al grupo de lectura de tracing:

```text
uid=2000(shell) gid=2000(shell) groups=...,3012(readtracefs) context=u:r:shell:s0
```

Sin embargo, el árbol visible está vacío:

```text
/sys/kernel/tracing:
.
..
```

Faltan nodos requeridos:

```text
/sys/kernel/tracing/trace_marker: No such file or directory
/sys/kernel/tracing/events: No such file or directory
/sys/kernel/tracing/tracing_on: No such file or directory
/sys/kernel/tracing/trace: No such file or directory
/sys/kernel/debug/tracing: No such file or directory
```

`atrace` falla antes de iniciar cualquier benchmark:

```text
Error: Did not find trace folder
No trace folder found
```

Macrobenchmark reporta:

```text
DEVICE-TRACING-MISCONFIGURED
```

## Control Diagnóstico con Segundo Android

Se conectó un dispositivo Android diferente para aislar si el fallo provenía del host, ADB o la instalación local de platform-tools.

- **Modelo:** Huawei `JLN-LX3`
- **Product:** `JLN-L03`
- **Device:** `HWJLN-Q`
- **Android:** 12 / API 31
- **Build fingerprint:** `HUAWEI/JLN-L03/HWJLN-Q:12/HUAWEIJLN-LX3/103.0.0.317LAMC25:user/release-keys`

En este dispositivo:

- `atrace --list_categories` funciona.
- `/sys/kernel/tracing` contiene `events`, `trace`, `trace_marker`, `tracing_on` y demás nodos ftrace esperados.

Esto descarta razonablemente:

- fallo global de ADB en el Mac;
- fallo general de `platform-tools`;
- error de interpretación del mensaje de `atrace`;
- incompatibilidad universal de `atrace` en builds `user`.

## Causa Raíz Probable

La causa inmediata es que el `tracefs` visible desde `shell` en el Pixel 9 GrapheneOS no contiene los nodos ftrace obligatorios. Dado que el dispositivo es un build `user` con `ro.debuggable=0`, no hay ruta legítima por ADB para inspeccionar o remontar el namespace real como root.

La hipótesis técnica más fuerte es una de estas:

1. Hardening intencional de GrapheneOS que oculta o neutraliza ftrace para `shell`.
2. Regresión o diferencia del build `CP2A.260805.005/2026081301`.
3. Restricción de namespace/SELinux que presenta un `tracefs` vacío al dominio `u:r:shell:s0`.

## Decisión Metodológica

El Huawei queda registrado únicamente como control diagnóstico positivo. No reemplaza la captura física obligatoria en Google Pixel 9 ni puede cerrar la Etapa 0.

La Etapa 0 permanece:

```text
PENDIENTE DE CAPTURA FÍSICA REAL EN PIXEL 9
```

## Opciones Siguientes

1. Reportar el caso a GrapheneOS con esta evidencia y confirmar si el `tracefs` vacío para `shell` en `tokay` es comportamiento esperado.
2. Ejecutar 0R.4 en otro Pixel 9 físico con `atrace` funcional.
3. Ejecutar 0R.4 en Pixel 9 con Android stock.
4. Si se autoriza más adelante, actualizar/cambiar build de GrapheneOS o usar una imagen compatible con tracing.
5. Usar el Huawei solo para pruebas diagnósticas del pipeline, nunca como línea base oficial.
