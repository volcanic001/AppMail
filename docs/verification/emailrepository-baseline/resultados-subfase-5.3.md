# Resultados — Subfase 5.3, baseline real en Pixel 9 con Gmail dedicado

## Identificación y alcance

- Fecha: 2026-08-10, zona CST (`-0600`).
- Dispositivo exclusivo: Pixel 9 (`tokay`), Android 17/API 37, serial USB `55080DLAQ002CK`.
- Cuenta: cuenta Gmail dedicada de pruebas; la dirección completa y toda credencial quedan fuera de la evidencia.
- Commit verificado: `a96582a` (HEAD).
- APK: el debug instalado se reemplazó por el APK validado en 5.1/5.2 y ambos quedaron byte por byte iguales, SHA-256 `f27ff938…f003`.
- No se ejecutó `connectedDebugAndroidTest`, Gradle ni instrumentación en el Pixel.
- Se usaron exclusivamente mensajes autocreados y contenido técnico neutral para todas las acciones destructivas o de envío.

## Integridad previa y posterior

| Elemento congelado | Resultado |
|---|---|
| `EmailRepository.kt` | `abcac202…be4b`, intacto |
| `MainNavHost.kt` | `a6840cfc…088`, intacto |
| Código de producción o Gradle | Sin cambios propios de 5.3 |
| Wi-Fi / datos móviles al cierre | `1` / `1`, restaurados |
| Eliminaciones remotas permanentes | Exactamente una, el sacrificio `MAILAPP_REPO_BL53_20260810_PAGE_19` |

## Matriz manual ejecutada

| Área | Ejecución y evidencia observable | Resultado |
|---|---|---|
| Login e identidad | Inicio de sesión manual en la cuenta dedicada; Inbox mostró exclusivamente la identidad truncada de esa cuenta. Tras el cierre de sesión apareció Login y fue posible reautenticar la misma cuenta. | Conforme |
| Inbox y refresh | Refresh remoto completó y dejó visibles los fixtures autocreados. Medición de comando: 11:43:49–11:44:05; incluye 15 s de espera deliberada. | Conforme |
| Paginación | Se alcanzaron 21 filas de Inbox. La primera carga quedó en 20 y el scroll mostró un fixture antiguo en la segunda página, demostrando append remoto. | Conforme |
| Búsqueda | `MAILAPP` devolvió cinco fixtures remotos y `complete=true`. La consulta exacta con guiones bajos devolvió cero por tokenización/indexación de Gmail; los mensajes recién enviados aún no estaban indexados. | Conforme, limitación registrada |
| Trash | Apertura y refresh correctos. Solo se actuó sobre fixtures autocreados; mensajes ambientales quedaron intactos. | Conforme |
| Correo no cacheado/cacheado | La cabecera resolvió desde Room en 54 ms; cuerpo remoto 510 ms y commit Room total 571 ms. La reapertura resolvió la cabecera desde caché en 72 ms. | Conforme |
| HTML e inline | Cuerpo de 911 bytes, tres referencias y dos PDFs; dos descargas inline terminaron e inyección produjo 9438 bytes. El fixture escapado muestra HTML literal y fallback visual de la imagen; la referencia grande agotó timeout, sin bloquear el resultado del repositorio. | Conforme con anomalía heredada |
| Offline del cuerpo | Sin red, la cabecera persistida siguió visible; el refresh de cuerpo falló con `UnresolvedAddressException` sin perder los datos cacheados ni el PDF listo. | Conforme |
| Marcar leído | Se abrió PAGE_21 y, al volver a Inbox, dejó de presentarse como no leído. | Conforme |
| Mover/restaurar | PAGE_20 salió de Inbox al moverlo, apareció en Trash tras refresh, salió de Trash al restaurarlo y reapareció en Inbox. | Conforme |
| Enviar | Mensajes PAGE_15–PAGE_21 y un piloto neutral se enviaron a la propia cuenta y reaparecieron en Inbox. | Conforme |
| Responder | PAGE_21 generó destinatario propio y asunto `Re: ...PAGE_21`; el cuerpo neutral `BL53_REPLY_OK_20260810` se envió y apareció remotamente. | Conforme |
| Reenviar | PAGE_21 generó asunto `Fwd: ...PAGE_21`; destinatario propio y cuerpo `BL53_FORWARD_OK_20260810` se enviaron y aparecieron remotamente. | Conforme |
| PDF offline/reintento | `baseline-small.pdf` falló sin red con estado reintentable. Con red, descargó 678 bytes en ~508 ms, validó y abrió en el visor externo. | Conforme |
| PDF cacheado offline | Con ambas redes desactivadas volvió a abrirse el mismo PDF desde caché. El flujo de UI prevalidó la caché y no invocó otra descarga; por eso no existe línea `CACHE_HIT` de `downloadPdf`. | Conforme |
| Salida durante descarga | Se inició `baseline-cancel.pdf` (15,000,693 bytes), se salió al segundo y se esperaron 20 s: sin visor, archivo final grande ni `.tmp`; el PDF pequeño permaneció. | Conforme |
| Sesión durante descarga | Descarga iniciada 12:41:26.658; logout confirmado 5,432 ms después. La descarga antigua terminó `state=Error`; inmediatamente y 55 s después no existían archivo final ni `.tmp`, y Room de la sesión cerrada contenía cero correos. | Conforme |
| Eliminación definitiva | PAGE_19 se movió a Trash, se verificó el diálogo irreversible y se confirmó una sola vez. Tras refresh no reapareció; Room: `PAGE19_ROWS|0`. Ningún otro correo se eliminó. | Conforme |

## Tiempos y trazas representativas

El extracto saneado está en
[`evidencias/subfase-5.3/pixel9-api37/logs-saneados.txt`](evidencias/subfase-5.3/pixel9-api37/logs-saneados.txt).
No contiene credenciales, direcciones completas, cuerpos, URLs ni identificadores largos de Gmail.

- Resolve desde Room: 54 ms en primera apertura y 72 ms en reapertura.
- Fetch de cuerpo: 510 ms; limpieza y persistencia total del repositorio: 571 ms.
- Reapertura: fetch 831 ms y persistencia total 865 ms.
- Inline: dos resultados útiles; 15.219–15.540 s por timeout de la referencia grande.
- PDF pequeño: error offline en 70 ms; reintento online y éxito en ~508 ms.
- Gate de sesión: logout 5.432 s después de iniciar; observación final superior a tres veces el timeout de 15 s, sin commit tardío.

## Anomalías conocidas y clasificación

1. **HTML literal y fallback de inline en el detalle.** El fixture escapado presenta etiquetas HTML como texto y la imagen como fallback visual, aunque las trazas confirman descarga de la parte PNG e inyección del mapa. La presentación literal ya aparece en evidencia histórica anterior al baseline de `EmailRepository`; se clasifica como comportamiento heredado de presentación/fixture, no como regresión ni fallo del contrato del repositorio.
2. **Re-fetch del cuerpo al reabrir.** Aunque `EmailResolve` usa Room, la pantalla vuelve a invocar `fetchAndCacheBody` y persiste el resultado. Coincide con el flujo actual y con el contrato caracterizado de delegación/persistencia; queda como candidato para un futuro refactor lógico, no para corregirse durante el baseline.
3. **PDFs incluidos entre referencias inline.** El provider expuso tres referencias, incluidas dos partes PDF; la grande agotó ~15 s. El repositorio conservó los dos resultados disponibles y propagó el mapa según los contratos. Se registra como comportamiento sospechoso del parsing/provider fuera del alcance estructural.
4. **Indexación de búsqueda.** Gmail no devolvió fixtures recién enviados para la consulta con guiones bajos, pero sí devolvió resultados remotos para `MAILAPP`. Es una característica temporal/tokenizadora externa, no pérdida de datos local.
5. **Preparación de fixtures.** Una automatización de composición se detuvo al detectar navegación inesperada. Produjo únicamente siete efectos neutrales dentro de la cuenta dedicada: tres envíos directos y cuatro reenvíos/niveles de reenvío. No tocó destinatarios personales ni se ocultó mediante borrado. El resto se creó y verificó uno a uno.
6. **Ensayos previos del gate de sesión.** Dos intentos de cambio de tarea no confirmaron logout: uno permitió que terminara la descarga y otro seleccionó el visor PDF. Para restablecer la precondición se eliminó solo el archivo local grande de caché, recuperable por descarga, y se retiró únicamente la tarea residual del visor. El gate final fue repetido desde cero y es el resultado aceptado.

Ninguna anomalía contradice los contratos automatizados de `EmailRepository` ni exige modificar producción antes del refactor estructural conservador.

## Evidencia versionada

- `01-inbox-fixtures.png`: sesión dedicada y fixtures neutrales tras reautenticación.
- `02-contenido-html-inline-pdf.png`: cuerpo, inline y dos PDFs.
- `03-pdf-offline-error.png`: estado reintentable sin red.
- `04-pdf-viewer.png`: PDF pequeño válido abierto.
- `05-salida-durante-descarga.png`: PDF grande en `Descargando…` antes de salir.
- `06-descarga-antes-de-logout.png` y `07-login-despues-de-logout.png`: gate de sesión.
- `08-confirmacion-borrado-sacrificial.png`: objetivo y confirmación irreversible antes de la única eliminación.
- `dispositivo.txt`: dispositivo, app, APK y estado final de red.
- `logs-saneados.txt`: extracto mínimo de `EmailResolve`/`MailPerfTrace`.

Las capturas temporales con cuentas o mensajes ajenos a los fixtures no se copiaron al repositorio.

## Cierre

- Todos los efectos remotos observables exigidos por la Subfase 5.3 coinciden con los contratos automatizados.
- No hubo datos tardíos ni residuos PDF tras el cambio de sesión.
- Se eliminó permanentemente exactamente un mensaje sacrificial autocreado; el efecto es irreversible en Gmail.
- No se alteró producción, Gradle ni el cambio protegido de `MainNavHost.kt`.
- La Subfase 5.3 queda cerrada; la Subfase 5.4 queda habilitada y pendiente.
- No se crea commit: el commit documental de Etapa 5 corresponde al cierre de 5.4.
