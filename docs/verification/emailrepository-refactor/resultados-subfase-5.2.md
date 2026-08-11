# Resultados — Subfase 5.2, Instrumentación y concurrencia

## Identificación
- Plan maestro: Refactor estructural de `EmailRepository` — Etapa 5, Subfase 5.2.
- Fecha: 2026-08-11, CST (`-0600`).
- Carácter: exclusivamente de validación y evidencia. Sin modificaciones a producción, pruebas, Gradle ni recursos.

## Estado de entrada
- HEAD: `8530fc6b2f8ab74cdd7242843643a269dbfce46b`.
- `EmailRepository.kt`: 177 líneas, SHA-256 `0e3a1520b91fd3579ae41d809c52282052907eb7d058f426272c50fb651dec4a` (intacto al cierre).
- Árbol: dos cambios protegidos de UI (`ComposeScreen.kt`, `MainNavHost.kt`) y los dos documentos de 5.1.
- Dispositivo: `Medium_Phone_API_36.1(AVD) - 16`, Android 16/API 36, serial `emulator-5554`, `sys.boot_completed=1`. Pixel 9 no utilizado.
- Preparación: `compileDebugAndroidTestKotlin --rerun-tasks` exitoso (1 m 09 s).

## Matriz de pruebas

### Bloque 1 — Contratos directos del repositorio (140/140)
- `FolderCommitCoordinatorTest` JVM: **3/3** (0 fallos, 0 errores).
- Nueve suites instrumentadas conjuntas: **137/137** en una corrida (44 s).
  - Resolución 29, Acciones 25, Lectura/refresh/búsqueda 20, Refresh seguro 11, Página parcial 1, Contenido 19, PDF 21, Cancelación PDF 2, Cuenta/envío 9.
- Consolidado: **140/140** (3 JVM + 137 instrumentadas).

### Bloque 2 — Instrumentación completa (284/284)
- `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks`.
- Primera ejecución: XML escrito con 284/284 tras ~10 min; el wrapper fue interrumpido por timeout del shell al escribir el reporte final (exit code 1), sin afectar resultados. Se preservó el XML como evidencia (`connected-b2-1.xml`).
- Segunda ejecución (corrida limpia de cierre): **BUILD SUCCESSFUL in 9 m 57 s**, exit code 0, XML **284/284, 26 clases, 0 fallos, 0 errores, 0 omisiones** (`connected-b2-2.xml`).
- Verificaciones específicas: `TrashContentActionTest` **7/7** y `EmailDetailCancellationTest` **7/7**.

### Bloque 3 — Serie temporal anti-flakiness (330/330)
- Reinicio del invitado del AVD (`adb reboot`, sin borrar datos); `sys.boot_completed=1` confirmado.
- Ocho clases seleccionadas (29+20+11+1+19+21+2+7=110), tres corridas consecutivas con `--rerun-tasks`, sin reinicios intermedios:
  - Corrida 1: 110/110, BUILD SUCCESSFUL in 2 m 55 s.
  - Corrida 2: 110/110, BUILD SUCCESSFUL in 2 m 57 s.
  - Corrida 3: 110/110, BUILD SUCCESSFUL in 2 m 41 s.
- Acumulado: **330/330**, 0 fallos, 0 errores, 0 omisiones (verificado desde XML en `r1/`, `r2/`, `r3/`).

## Evidencia XML (fuera del repositorio)
- `/tmp/refactor52/bloque1/` — FolderCommitCoordinatorTest.xml + connected-b1.xml (137).
- `/tmp/refactor52/bloque2/` — connected-b2-1.xml + connected-b2-2.xml (284, 26 clases).
- `/tmp/refactor52/bloque3/r1..r3/` — 110/110 cada uno (330 acumuladas).

## Integridad
- Constructor, 20 métodos públicos y `MAX_PDF_SIZE` intactos (21 entradas públicas).
- SHA de `EmailRepository.kt` intacto; fingerprints de `ComposeScreen.kt` (`2505050c…f5e69`) y `MainNavHost.kt` (`a6840cfc…088`) intactos.
- Ningún cambio nuevo en producción, pruebas o Gradle: árbol posterior idéntico al inicial.
- `git diff --check` limpio; staging vacío.
- Emulador detenido al finalizar.

## Decisión
- **GO**. 140/140, 284/284 y 330/330 sin fallos, errores ni omisiones. Sin flakiness, regresiones ni divergencias contractuales.

## Cierre
- Subfase 5.2 **aprobada**; Etapa 5 en curso; 5.3–5.4 pendientes.
- Documentos de 5.1 y 5.2 sin staging. Sin commit ni push; cierre documental único en Subfase 5.4.
