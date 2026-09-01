# Resultados — Subfase 6.2: Instrumentación y equivalencia visual

Fecha: 2026-08-31 (CST)

## Instrumentación focal Inbox

`InboxContentCharacterizationTest` ejecutó sus 19 casos sin fallos en las tres repeticiones del emulador `Medium_Phone_API_36.1` (API 36). La misma suite focal también pasó 19/19 en Pixel 9 (API 37). La cobertura incluye loading, error, lista vacía y poblada, barra superior, pull-to-refresh, Snackbar/undo, highlight, gestos y paginación.

## Suite instrumentada completa

La ejecución válida, con Pixel desconectado temporalmente del servidor ADB local y solo `emulator-5554` disponible, ejecutó:

```text
./gradlew connectedDebugAndroidTest --rerun-tasks --console=plain
```

El resultado UTP del emulador registró `scheduled_test_case_count: 325`, 325 casos aprobados y 0 fallos.

Se registró además un intento inicial con ambos dispositivos. Pixel 9 tuvo 15 fallos ajenos a Inbox en `EmailBodyWebViewBaselineTest`: esos tests fijan un ancho nativo esperado de 1080 px mientras el dispositivo informa 2424 px. El intento no se usa como evidencia de aceptación; la ejecución completa requerida por el plan maestro es la pasada limpia de emulador anterior.

## Equivalencia visual

Se revalidaron todos los artefactos canónicos almacenados en `capturas/`:

| Evidencia | Pares comparados | Diferencias |
|---|---:|---:|
| PNG baseline/post (SHA-256) | 16 | 0 |
| Manifiestos TSV baseline/post (comparación binaria) | 16 | 0 |

Los pares cubren loading, error, vacío, lista poblada, refresh vacío/poblado y divisores habilitados/deshabilitados en emulador y Pixel. Al ser idénticos, preservan layout, contenido, posición, colores y divisores; la caracterización focal aprobada cubre adicionalmente refresh, Snackbar, highlight y loaders.

## Integridad

Las SHA-1 de los tres archivos ajenos protegidos coinciden con el preflight. No se modificó código productivo ni pruebas durante esta subfase.

## Criterio de salida

**GO.** La evidencia focal Inbox y la suite completa requerida en emulador pasan, y no hay diferencia visual en los pares canónicos.
