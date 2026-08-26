# Resultados — Subfase 4.1: puerta física Pixel 9

## 1. Estado

**COMPLETADA — GO.** La corrida física aceptada terminó **22/22**, con cero
fallos, errores, omitidas o crashes. Se conservaron y validaron exactamente
**16 PNG + 16 trazas** con sufijo `-fisico`, además del XML verde. La revisión
visual en el Pixel 9 está aprobada, manteniendo como defecto conocido el
overflow horizontal de F02.

No se repitió la corrida del emulador, la suite completa de 306 pruebas ni una
compilación forzada. Producción, Gradle, fixtures y cambios ajenos no se
modificaron.

## 2. Identidad y precondiciones

- **Fecha local**: 2026-08-25 (CST, `-0600`).
- **Rama**: `main`; **HEAD**:
  `2a433a67af4ea6f4f27e320133c963a408e80453`.
- **Dispositivo**: Google Pixel 9, serial `55080DLAQ002CK`, product/device
  `tokay`, Android 17 / API 37.
- **Pantalla**: encendida, desbloqueada, vertical, resolución física nativa
  **1080×2424**, sin override y sin notificaciones sensibles visibles.
- **Navegador HTTPS**: `app.vanadium.browser`, actividad resuelta
  `com.google.android.apps.chrome.IntentDispatcher`, Vanadium
  `152.0.7977.54.1` (`versionCode=797705534`). S09 confirmó su proveedor de
  Custom Tabs y cargó `example.com`.
- **Hashes protegidos**, idénticos al baseline:
  - `EmailBodyWebView.kt`:
    `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`;
  - `ComposeScreen.kt`:
    `2505050cf45aab8fc691a2b439d442a9b1a73c62c1d0a32c53bc3703469f5e69`;
  - `MainNavHost.kt`:
    `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.

## 3. Ejecución física aceptada

Comando:

```bash
ANDROID_SERIAL=55080DLAQ002CK ./gradlew connectedDebugAndroidTest \
  --console=plain --warning-mode=none \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.feature.emaildetail.components.EmailBodyWebViewBaselineTest
```

| Resultado | Fallos | Errores | Omitidas | Duración Gradle | Tiempo XML |
|---:|---:|---:|---:|---:|---:|
| 22/22 | 0 | 0 | 0 | 1m42s | 66.589s |

Gradle informó `74 actionable tasks: 1 executed, 73 up-to-date`; las tareas de
compilación permanecieron `UP-TO-DATE`. El XML aceptado es
`reportes-subfase-4.1/fisico.xml`, SHA-256
`588f60644b9dde9b03008324c55c101a766ffaa67b1a6bda021598241c0e6fec`.

## 4. Incidencias reales

1. El primer intento no inició pruebas (`0/22`) porque el paquete instalado
   `com.david.mailapp` tenía una firma incompatible
   (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Con autorización expresa se eliminó
   el paquete y sus datos; aunque `adb uninstall` devolvió
   `DELETE_FAILED_INTERNAL_ERROR`, la comprobación posterior confirmó que el
   paquete ya no existía en ninguno de los perfiles ni globalmente.
2. La corrida autorizada posterior terminó **21/22**: solo
   `s03_simpleDark_initial` falló porque `ActivityScenario` observó la actividad
   destruida antes de poder ejecutar `onActivity`. No hubo artefactos S03 de
   esa corrida y `always_finish_activities=0`; se clasificó como carrera
   transitoria de instrumentación, no como fallo de producción.
3. No se hicieron reintentos automáticos. El usuario autorizó explícitamente
   la corrida completa final, que produjo el resultado verde y la evidencia
   conservada en este informe.

## 5. Validación de evidencia

- Fuente persistente del dispositivo: `/data/local/tmp/emailbody-4.1/`.
- Presencia exacta: **16 PNG** y **16 logs**, todos con sufijo `-fisico`.
- Todos los archivos son no vacíos; los 16 PNG tienen firma PNG válida,
  decodifican y miden exactamente **1080×2424**.
- Todas las líneas de trazas conservadas contienen `MailRenderTrace`.
- La búsqueda de privacidad no encontró `Authorization`, `Bearer`, tokens,
  contraseñas, secretos, `@gmail.com` ni `@outlook.com`.
- La revisión visual no encontró cuentas, mensajes, notificaciones con
  contenido sensible ni datos personales. Solo aparecen fixtures sintéticas
  y la página pública `Example Domain`.

## 6. SHA-256 — capturas físicas

| Archivo | SHA-256 |
|---|---|
| `capturas/S01-null-a-simple-claro-fisico.png` | `3cf02eeeeaa3373a5ccf5d636552e07fe21b3a174b07d8b25b8c9c8661ea4736` |
| `capturas/S02-simple-claro-fisico.png` | `3cf02eeeeaa3373a5ccf5d636552e07fe21b3a174b07d8b25b8c9c8661ea4736` |
| `capturas/S03-simple-oscuro-fisico.png` | `2a3ff0dcf2b58efa156c5a3027f6595cc81bb0f39d1523c82fa0a0d946ae0fcf` |
| `capturas/S04-newsletter-claro-fisico.png` | `36a0cb9fd8ea0bd6b706cff52fa63d4965ac28e295aed24efcb17b49b2fe9a88` |
| `capturas/S05-newsletter-oscuro-fisico.png` | `978cff0349c429e75f2b497931920acf499315ea25f14d76786015e1ec441a1b` |
| `capturas/S06-remota-habilitada-fisico.png` | `f67b6ade024cde70ac3ec1a2c79cccc2f20fa3c5c29adb3f8ace706619e2ee8b` |
| `capturas/S07-remota-bloqueada-fisico.png` | `82a3a0bd1aad09880ccd449d28649e3013d85e1f9b45983a2b4a8e41af067da8` |
| `capturas/S08-data-bloqueo-remoto-fisico.png` | `f2442edf5ec7e56b1f937352f2fae0e830264e674c530f172691c368441d283c` |
| `capturas/S09-enlace-custom-tab-fisico.png` | `af1186195181a79245471743b5ea266681305ab0ff86e4c0738b8e654b807de0` |
| `capturas/S11-cambio-body-fisico.png` | `36a0cb9fd8ea0bd6b706cff52fa63d4965ac28e295aed24efcb17b49b2fe9a88` |
| `capturas/S12-cambio-tema-fisico.png` | `cc87d36686a03335345cc27686abfcfcbaf3faad506410736fd820a3aa188c9b` |
| `capturas/S13-cambio-politica-imagenes-fisico.png` | `82a3a0bd1aad09880ccd449d28649e3013d85e1f9b45983a2b4a8e41af067da8` |
| `capturas/S14-scroll-antes-pausa-fisico.png` | `841e23823679c59973cec4953717ec1d88c99cbe04ee06fba9f6cf33e3abe4c4` |
| `capturas/S14-scroll-despues-resume-fisico.png` | `841e23823679c59973cec4953717ec1d88c99cbe04ee06fba9f6cf33e3abe4c4` |
| `capturas/S15-long-press-data-fisico.png` | `29458b27700ee6a99b397107d149ebbef455f4fa189ba7de26e0088faf06bb06` |
| `capturas/S16-reapertura-fisico.png` | `3cf02eeeeaa3373a5ccf5d636552e07fe21b3a174b07d8b25b8c9c8661ea4736` |

## 7. SHA-256 — trazas físicas

| Archivo | SHA-256 |
|---|---|
| `trazas/S01-null-a-simple-claro-fisico.log` | `86b87967767e68deed45a698066bacd0c81f0d725b79c1b42808a99da1748004` |
| `trazas/S02-simple-claro-fisico.log` | `0d4e9970927109f7e727e1f8d6676591f9521e20243b2a8180019feb5005d339` |
| `trazas/S03-simple-oscuro-fisico.log` | `a2df3242e0915c9a96fc92228b834c6b07d56cab46067bd6d5b22a0c6d01fb0d` |
| `trazas/S04-newsletter-claro-fisico.log` | `8812153d952883ff004c108f2f3317a390d7e9066ace012eb1ae37bf49244c82` |
| `trazas/S05-newsletter-oscuro-fisico.log` | `7d3c56575765ef5a5962009c3014605eba20c2d5dffdf761ce595d6569a2a732` |
| `trazas/S06-remota-habilitada-fisico.log` | `136fe592a0c044a4215e72cb2891ef0e2f424264df63b9d24e16c1d5cedc9a9d` |
| `trazas/S07-remota-bloqueada-fisico.log` | `a28587bf1d7e6f9d84d443a684b60c5ab9d805f6f18298b2ef4706b49f786fe1` |
| `trazas/S08-data-bloqueo-remoto-fisico.log` | `c90e126aa32500daa33fc1d47e3f9635b13273e310538531e38da660e9732b5b` |
| `trazas/S09-enlace-custom-tab-fisico.log` | `8b57e9d7a3152f977180291c686ec3b0d0fbf4cca82ba411e2c76c05408273f9` |
| `trazas/S10-recomposicion-equivalente-fisico.log` | `a62d70ef28c62b9d6da617e270a22104182ac354014a033174c8eceb930142f6` |
| `trazas/S11-cambio-body-fisico.log` | `65afc0966ef5ac583d9d79583a2fe66d373478470c87837e62904d322a77ac56` |
| `trazas/S12-cambio-tema-fisico.log` | `f04f57409909df52fea0651bdfc0ea6d378f63443bf5ba9de5d3a3324f583043` |
| `trazas/S13-cambio-politica-imagenes-fisico.log` | `1f9132e49828fc0808f6e6d8b002ae9c61ae6cc8d63a80f9cf8404db58891f46` |
| `trazas/S14-scroll-lifecycle-fisico.log` | `5ee2011b386a6c6100c100317a85ef86f0d3c61a767575f77fe9001a0ff99f60` |
| `trazas/S15-long-press-data-fisico.log` | `89e641fda84d4b5d0fe84e4579f2e037de06afdeab4157ce2e0e5d242d197cb5` |
| `trazas/S16-release-reapertura-fisico.log` | `5cfad04300b5c5bcac4367f15a5029f40cd1f8d0e3a3c8205c321e34bcb2122b` |

## 8. Comparación visual y contratos

- **Temas**: S01/S02 y S03 muestran F01 en claro/oscuro; S12 confirma el
  cambio a oscuro. Texto, énfasis, acentos y `ñ` permanecen legibles.
- **Newsletter/F02**: S04, S05 y S11 reproducen en claro/oscuro el overflow
  horizontal ya aceptado en 3.1. Es evidencia fiel del defecto conocido, no
  una regresión física nueva.
- **Imágenes**: S06 conserva el mismo icono de recurso remoto sintético no
  cargado observado en emulador; S07 y S13 ocultan el recurso al bloquear red;
  S08 y S15 conservan el área inline sintética de 96×96.
- **S09**: Vanadium muestra una Custom Tab cargada con `Example Domain` y
  `example.com`. La traza registra pausa/reanudación sin liberar ni recrear el
  WebView.
- **S14**: antes y después muestran el mismo punto de scroll; ambos PNG son
  idénticos byte a byte. La traza registra pausa en `scrollY=1000`, reanudación
  con `savedScrollY=1000` y aplicación de `scrollY=1000`, sin recarga.
- **S15**: la imagen inline permanece visible y el test verde exige la entrega
  exacta de la URL `data:` en el long-press; el componente no emite una traza
  específica para ese callback.
- **S16**: F01 reaparece completo. La traza contiene dos `WV_FACTORY` con
  instancias distintas (`463f95e`, `a9c6edf`) y un único `WV_RELEASE` entre
  ambas.

La diferencia de altura frente al emulador (2424 frente a 2400 píxeles) impide
comparar hashes entre dispositivos, pero la composición y los estados
observables coinciden visualmente con el baseline de 3.1/3.2.

## 9. Integridad y cierre

- XML físico verde presente y con SHA-256 registrado.
- 16 PNG y 16 trazas físicas presentes, validadas y con SHA-256 registrado.
- Hashes protegidos idénticos al baseline.
- Producción, Gradle, fixtures y cambios ajenos intactos.
- `git diff --check`: limpio.
- Sin commit; queda reservado para la Subfase 4.2.

## 10. Decisión

**GO — Subfase 4.1 cerrada; autorizada la Subfase 4.2.**
