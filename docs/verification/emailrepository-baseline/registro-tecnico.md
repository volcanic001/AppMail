# Registro técnico — Baseline verificable de EmailRepository

## Estado del documento

- Plan: baseline verificable previo al refactor estructural conservador.
- Etapa: 1 — Congelar estado, contratos y cobertura.
- Subfases completadas: 1.1 — Estado inicial y registro técnico; 1.2 — Inventario de API y consumidores; 1.3 — Matriz contractual y análisis de huecos; 1.4 — Baseline técnico existente.
- Estado de la etapa: completada y cerrada en el commit documental `docs(repository): establish verifiable baseline scope`.
- Estado actual: etapa 4 completada y cerrada en el commit `test(repository): characterize pdf account and send contracts`; etapa 5 pendiente.
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

## Evidencia de la subfase 3.1

- [Resultados de resolución y acciones existentes](resultados-subfase-3.1.md): ocho contratos nuevos; corrida conjunta de 54/54 pruebas y tres ejecuciones consecutivas de resolución con 29/29 en verde.

La matriz contractual aumenta de 83 a 91 casos directos. Resolución y las cuatro acciones quedan selladas para el alcance previsto, sin modificar producción. La Etapa 3 permanece abierta y no se crea commit hasta cerrar la Subfase 3.4.

## Evidencia de la subfase 3.2

- [Resultados de persistencia del cuerpo](resultados-subfase-3.2.md): tres contratos directos de éxito para `fetchAndCacheBody`; corrida en el emulador previsto con resultado final 3/3 en verde y XML sin fallos, errores ni omitidas.

La matriz contractual aumenta de 91 a 94 casos directos. `fetchAndCacheBody` pasa de cobertura ausente a media, con éxito y persistencia protegidos; fallos, sesión y cancelación quedan pendientes para la Subfase 3.3. `FakeEmailProvider` se amplió únicamente en AndroidTest con el registro de `gmail.fetchBody` y de los `emailId` recibidos, sin alterar resultados, errores ni cancelación. La Etapa 3 permanece abierta y no se crea commit hasta cerrar la Subfase 3.4.

## Evidencia de la subfase 3.3

- [Resultados de fallos, sesión y cancelación del cuerpo](resultados-subfase-3.3.md): siete contratos nuevos; corrida completa de 10/10 en verde y tres ejecuciones consecutivas del contrato temporal de sesión con 1/1 en verde.

La matriz contractual aumenta de 94 a 101 casos directos. `fetchAndCacheBody` pasa de cobertura media a alta, con ausencias de lease/proveedor/resultado, errores y cancelación remotos, cambio de sesión sin escritura tardía y fallo local de commit protegidos. El retorno del resultado remoto ante un commit rechazado queda confirmado como comportamiento heredado. `FakeEmailProvider` se amplió únicamente en AndroidTest con la señal de inicio `fetchBodyStarted` para la sincronización determinista del contrato de sesión. La Etapa 3 permanece abierta y no se crea commit hasta cerrar la Subfase 3.4.

## Evidencia de la subfase 3.4 y cierre de etapa 3

- [Resultados de imágenes inline e inyección CID](resultados-subfase-3.4.md): nueve contratos nuevos; corrida de 19/19 de la suite ampliada y corrida conjunta de 73/73 de las tres suites de la Etapa 3, sin fallos, errores ni omitidas.

La matriz contractual aumenta de 101 a 110 casos directos. `downloadInlineImages` e `injectInlineImages` pasan de cobertura ausente a alta, incluida la propagación de cancelación y la ausencia de provider en inline. La sensibilidad a prefijos y al orden del mapa en la inyección CID queda registrada como comportamiento heredado sospechoso para el futuro refactor lógico. `FakeEmailProvider` se amplió únicamente en AndroidTest con el registro de `emailId` y referencias ordenadas de `downloadInlineImages`. `EmailRepository.kt` y `MainNavHost.kt` conservan sus hashes originales. La Etapa 3 se cierra en el commit `test(repository): characterize resolution actions and content`, que excluye el cambio previo del usuario en `MainNavHost.kt`.

## Evidencia de la subfase 4.1

- [Resultados de prevalidación y consultas de caché PDF](resultados-subfase-4.1.md): diez contratos nuevos; corrida de 10/10 de la suite nueva, corrida conjunta de 12/12 con `PdfCancellationContractsTest` y 22/22 de `PdfCacheManagerTest` en JVM, sin fallos, errores ni omitidas.

La matriz contractual aumenta de 110 a 120 casos directos. Las tres consultas de caché (`isPdfCached`, `checkPdfCache`, `getValidatedCachedPdf`) pasan de ausente a alta y `downloadPdf` de mínima a media, con la prevalidación, el límite declarado y el cache hit sellados. `MAX_PDF_SIZE` queda cubierto en su frontera exacta y un byte superior. No se ampliaron fakes. `EmailRepository.kt` y `MainNavHost.kt` conservan sus hashes originales. La Etapa 4 permanece en progreso y no se crea commit hasta cerrar la Subfase 4.4; 4.2 es la siguiente subfase.

## Evidencia de la subfase 4.2

- [Resultados de descarga, validación y resultados PDF](resultados-subfase-4.2.md): ocho contratos nuevos; corrida de 18/18 de la suite ampliada, corrida conjunta de 20/20 con `PdfCancellationContractsTest` y 22/22 de `PdfCacheManagerTest` en JVM, sin fallos, errores ni omitidas.

La matriz contractual aumenta de 120 a 128 casos directos. La descarga válida, la postvalidación (vacío, tamaño real excesivo y firma inválida), la ausencia de provider, el error remoto convertido a `NETWORK`, el error de escritura en caché (`CACHE_WRITE`) y la limpieza de caché inválida quedan cubiertos. Los seis valores de `PdfDownloadFailure` están representados en contratos del repositorio. `FakeEmailProvider` se amplió únicamente en AndroidTest con el registro de `emailId`/`attachmentId` y el evento `gmail.downloadAttachment`. La Etapa 4 permanece en progreso y no se crea commit hasta cerrar la Subfase 4.4; 4.3 es la siguiente subfase.

## Evidencia de la subfase 4.3

- [Resultados de sesión, cancelación y atomicidad PDF](resultados-subfase-4.3.md): tres contratos nuevos de sesión y refuerzo de cancelación; corrida de 21/21 de la suite ampliada, conjunta de 23/23, tres repeticiones de C21 1/1 sin flakiness, regresión de `EmailDetailCancellationTest` 7/7 y `PdfCacheManagerTest` 22/22 en JVM, sin fallos, errores ni omitidas.

La matriz contractual aumenta de 128 a 131 casos directos. `downloadPdf` pasa de media a alta con lease ausente, limpieza rechazada y cambio real de sesión sin escritura tardía protegidos. El contrato transversal «Caché PDF atómica y sin residuos» alcanza cobertura alta. `FakeEmailProvider` se amplió solo en AndroidTest con la señal `downloadAttachmentStarted` para sincronización determinista. La Etapa 4 permanece en progreso; 4.4 cierra con identidad y envío.

## Evidencia de la subfase 4.4 y cierre de etapa 4

- [Resultados de identidad y envío](resultados-subfase-4.4.md): nueve contratos nuevos; corrida de 9/9 de la suite nueva, corrida conjunta de 32/32 de las tres suites de la Etapa 4, regresión `EmailDetailCancellationTest` 7/7 y `PdfCacheManagerTest` 22/22 en JVM, sin fallos, errores ni omitidas.

La matriz contractual aumenta de 131 a 140 casos directos. `getUserEmail` y `sendEmail` pasan de ausente a alta. Los 20 métodos públicos de `EmailRepository` quedan en cobertura alta. Provider dinámico, cancelación y ausencia de provider alcanzan cobertura transversal alta. El mensaje heredado `No hay proveedor activo` en `sendEmail` sin provider queda confirmado como comportamiento actual. `FakeEmailProvider` se amplió solo en AndroidTest con `SendRequest`, contador y eventos para identidad y envío. La Etapa 4 se cierra en el commit `test(repository): characterize pdf account and send contracts`, que excluye el cambio previo del usuario en `MainNavHost.kt`.

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
