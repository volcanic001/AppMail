# Registro técnico — Baseline verificable de EmailRepository

## Estado del documento

- Plan: baseline verificable previo al refactor estructural conservador.
- Etapa: 1 — Congelar estado, contratos y cobertura.
- Subfases completadas: 1.1 — Estado inicial y registro técnico; 1.2 — Inventario de API y consumidores; 1.3 — Matriz contractual y análisis de huecos; 1.4 — Baseline técnico existente.
- Estado de la etapa: completada y cerrada en el commit documental `docs(repository): establish verifiable baseline scope`.
- Estado actual: etapa 2 completada; sus subfases 2.1–2.4 y la auditoría consolidada están cerradas. Etapa 3 pendiente.
- Captura realizada: 2026-08-08 16:38:46 -0600 (CST).
- Alcance de esta subfase: documentación del estado inicial; no se modificó código de producción, pruebas ni configuración.

## Repositorio y punto de partida

- Ruta de trabajo: `/Users/david/Desktop/MailApp 0.3.0 2`.
- Rama: `main`.
- Commit inicial: `0ba0f8bbbabb4442a747134f3db64b576837d595`.
- Archivo objetivo: `app/src/main/java/com/david/mailapp/data/repository/EmailRepository.kt`.
- Tamaño inicial: 745 líneas y 31,613 bytes.
- SHA-256 inicial: `abcac202041966e53383b40613c5c3d027a93bbb0d8c2616fe16a90a424dbe4b`.
- Última modificación registrada por el sistema de archivos: `2026-08-03 16:54:51 -0600`.

Este hash será la referencia para demostrar que `EmailRepository.kt` permanece sin cambios durante todo el plan de baseline. El futuro refactor estructural no forma parte de este registro.

## Evidencia de las subfases 1.2 y 1.3

- [Inventario de API y consumidores](inventario-api-y-consumidores.md): constructor, dependencias, 20 métodos públicos, constante PDF, coordinador interno, consumidores y efectos laterales.
- [Matriz contractual y análisis de huecos](matriz-contractual-y-huecos.md): cobertura directa actual, contratos transversales, huecos priorizados y trazabilidad hacia las siguientes etapas.

Estas subfases fueron documentales y de análisis estático. No modificaron producción, pruebas ni Gradle, y no ejecutaron todavía la validación formal reservada para la subfase 1.4.

## Evidencia de la subfase 1.4

- [Resultados del baseline técnico existente](resultados-subfase-1.4.md): 584 pruebas JVM y 71 pruebas instrumentadas en verde, build y AndroidTest compilados, lint con 0 errores, advertencias clasificadas e integridad de hashes confirmada.

La validación formal se ejecutó sin cambios en producción o pruebas. Con este resultado, la etapa 1 queda cerrada en un commit documental independiente que excluye el cambio previo del usuario.

## Evidencia de la subfase 2.1

- [Resultados de contratos de lectura desde Room](resultados-subfase-2.1.md): cinco pruebas directas nuevas para Inbox, Trash y lectura por id, ejecutadas en el emulador previsto con resultado final 5/5 en verde.

La matriz contractual fue actualizada de 63 a 68 casos directos. Las tres APIs de lectura pasan de cobertura ausente a cobertura alta; `EmailRepository.kt` continúa congelado.

## Evidencia de la subfase 2.2

- [Resultados de huecos de refresh](resultados-subfase-2.2.md): siete contratos nuevos aplicados a Inbox y Trash; validación conjunta de 24/24 pruebas en verde con las suites previas de refresh seguro y página parcial.

La matriz contractual aumenta de 68 a 75 casos directos. `refreshInbox` y `refreshTrash` pasan de cobertura media a alta; la independencia concurrente entre carpetas queda reservada para la subfase 2.4.

## Evidencia de la subfase 2.3

- [Resultados de búsqueda remota](resultados-subfase-2.3.md): cinco contratos nuevos para delegación exacta, resultado efímero, provider dinámico, ausencia, error y cancelación; suite completa de 17/17 pruebas en verde.

La matriz contractual aumenta de 75 a 80 casos directos. `searchEmails` pasa de cobertura ausente a alta; no se modificó producción y la subfase 2.4 sigue siendo el único trabajo pendiente de la etapa 2.

## Evidencia de la subfase 2.4 y cierre de etapa 2

- [Resultados de coordinación temporal](resultados-subfase-2.4.md): tres contratos cruzados nuevos, cada uno con tres iteraciones; tres corridas JVM de `FolderCommitCoordinatorTest` y tres corridas instrumentadas consolidadas de 32/32 pruebas en verde.

La matriz contractual aumenta de 80 a 83 casos directos. Inbox y Trash quedan protegidos frente a invalidación cruzada y mantienen exclusión mutua dentro de cada carpeta. La Etapa 2 se cierra en el commit `test(repository): characterize reads refresh and search`, sin incluir el cambio previo del usuario en `MainNavHost.kt`.

## Estado previo del árbol de trabajo

Antes de crear este registro existía un único archivo modificado:

```text
 M app/src/main/java/com/david/mailapp/ui/navigation/MainNavHost.kt
```

Resumen del cambio previo:

```text
6 inserciones, 2 eliminaciones
SHA-256 de la copia de trabajo:
a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088
```

`MainNavHost.kt` pertenece al usuario y queda expresamente excluido del baseline de `EmailRepository`. No debe modificarse, revertirse, formatearse ni incluirse en los futuros commits de este plan. Su hash se volverá a comprobar en cada auditoría de etapa.

Durante las consultas de Git apareció el aviso no bloqueante:

```text
error: fsmonitor_ipc__send_query: unspecified error on '.git/fsmonitor--daemon.ipc'
```

Git sí devolvió correctamente el estado, el diff y el commit. El aviso se registra como condición del entorno y no se intentará corregir dentro de este plan.

## Entorno de compilación

- Sistema: macOS 26.5.2, build 25F84, arquitectura x86_64.
- Java: Eclipse Temurin OpenJDK 25.0.2 LTS.
- Gradle Wrapper: 9.6.1.
- Kotlin embebido reportado por Gradle: 2.3.21.
- Android Gradle Plugin: 9.0.0.
- Kotlin del proyecto: 2.1.20.
- KSP: 2.1.20-1.0.31.
- Room: 2.7.0.
- `compileSdk`: 36.
- `targetSdk`: 36.
- `minSdk`: 26.
- Aplicación: versionCode 1, versionName 1.0.
- Android Debug Bridge: 1.0.41, platform-tools 37.0.0-14910828.

La diferencia entre el Kotlin embebido de Gradle y la versión del plugin Kotlin del proyecto es esperable; ambas cifras se conservan por separado para evitar confundirlas en verificaciones posteriores.

## Dispositivos disponibles

### Dispositivo físico

- Modelo: Pixel 9 (`tokay`).
- Android: 17.
- API: 37.
- Transporte detectado: ADB inalámbrico.
- Estado al capturar el baseline: conectado y autorizado.

### Emulador previsto

- AVD disponible: `Medium_Phone_API_36.1`.
- Estado al capturar esta subfase: no iniciado.

El listado del AVD devolvió mensajes de Crashpad por permisos, pero también devolvió correctamente el nombre del emulador. La ejecución y salud del AVD se comprobarán en la subfase instrumentada correspondiente, no en esta subfase.

## Archivos permitidos y prohibidos

Durante el plan de baseline solamente podrán cambiar:

- Pruebas bajo `app/src/test` y `app/src/androidTest` relacionadas con los contratos de `EmailRepository`.
- Fakes y utilidades ubicados exclusivamente en source sets de prueba.
- Evidencia dentro de `docs/verification/emailrepository-baseline`.

Quedan congelados:

- `EmailRepository.kt` y los demás archivos de producción.
- `EmailProvider` y sus implementaciones.
- DAOs, base de datos, entidades, modelos y codecs.
- `AppContainer`, ViewModels, navegación y recursos.
- Archivos Gradle, catálogo de versiones y configuración Android.
- El cambio previo del usuario en `MainNavHost.kt`.

Si una prueba no puede escribirse sin introducir un seam en producción, la cobertura se registrará como bloqueada y no se modificará producción dentro de este plan.

## Comandos de captura

La información anterior se obtuvo mediante operaciones de solo lectura equivalentes a:

```text
git rev-parse HEAD
git branch --show-current
git status --short --untracked-files=all
git diff --stat
git diff --name-only
wc -l app/src/main/java/com/david/mailapp/data/repository/EmailRepository.kt
shasum -a 256 app/src/main/java/com/david/mailapp/data/repository/EmailRepository.kt
java -version
./gradlew --version
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
emulator -list-avds
```

No se ejecutaron pruebas, compilaciones, lint, instalación de APK ni pruebas manuales como parte de la subfase 1.1.

## Criterio de cierre de la subfase 1.1

- Punto de partida Git identificado: cumplido.
- Archivo objetivo medido y hasheado: cumplido.
- Cambio previo del usuario identificado y protegido: cumplido.
- Entorno Java, Gradle y Android registrado: cumplido.
- Dispositivo físico y AVD identificados: cumplido.
- Archivos permitidos y prohibidos definidos: cumplido.
- Código de producción, pruebas y Gradle sin modificaciones durante las subfases 1.1–1.3: cumplido; la auditoría posterior conserva los hashes iniciales de `EmailRepository.kt` y `MainNavHost.kt`, y solo añade documentación dentro de `docs/verification/emailrepository-baseline`.
