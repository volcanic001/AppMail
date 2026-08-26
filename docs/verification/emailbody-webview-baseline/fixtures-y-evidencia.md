# Fixtures y evidencia de referencia — Subfase 1.3

## 1. Estado del documento

- **Plan**: plan maestro de baseline de `EmailBodyWebView` (documento externo al repositorio).
- **Etapa**: 1. **Subfase**: 1.3.
- **Estado**: COMPLETADA.
- **Fecha local**: 2026-08-24 07:45:43 CST (-0600).
- **Rama**: `main`.
- **HEAD**: `2a433a67af4ea6f4f27e320133c963a408e80453`.
- **Hash vigente de `EmailBodyWebView.kt`**:
  `83cf07eb2725ba8002bd3f003c4b048776d2efa2a50d938423ab004363ddd2d1`.
- **Declaración**: no se ejecutaron builds ni tests en esta subfase y no se
  modificó producción.

## 2. Reglas de privacidad y estabilidad

- Todos los archivos se guardan como UTF-8, con salto de línea final.
- Las fixtures no contienen nombres, correos, identificadores, credenciales ni
  URLs reales de usuarios.
- La imagen remota usa deliberadamente el dominio reservado `.invalid`
  (`https://example.invalid/...`): la prueba no depende de Internet y no se
  sustituirá por un servicio público.
- La imagen `data:` es una carga útil fija y no contiene datos personales.
- Queda prohibido sustituir las fixtures por correos reales.

## 3. Catálogo de fixtures

| ID | archivo | tipo | rasgo controlado | subfases consumidoras |
|---|---|---|---|---|
| F01 | `fixtures/01-html-simple.html` | HTML simple | camino de HTML simple; tipografía, color, espaciado y tema claro/oscuro | 3.1, 3.2, 4.1 |
| F02 | `fixtures/02-newsletter-tabla.html` | HTML complejo con tablas | normalización de tablas, ancho, escala y viewport | 3.1, 3.2, 4.1; derivación S14 en 2.3 o 3.2 |
| F03 | `fixtures/03-imagen-remota.html` | imagen remota `https:` | política de imágenes remotas (`showImages` true/false) | 3.1, 3.2, 4.1 |
| F04 | `fixtures/04-imagen-data.html` | imagen inline `data:` | persistencia de `data:` con imágenes remotas deshabilitadas | 3.1, 3.2, 4.1 |
| F05 | `fixtures/05-enlace-externo.html` | enlace externo | interceptación de navegación externa (Custom Tabs) | 3.2, 4.1 |

## 4. Procedimiento de inyección futuro

En las Subfases 3.1 y 3.2 el contenido literal del archivo se suministrará
como `body` a la función pública `EmailBodyWebView`; no se crearán hooks de
producción.

Si la ejecución manual usa la UI real y no existe un mecanismo seguro para
inyectar el contenido, el caso queda pendiente hasta la prueba pública de la
Subfase 2.3, sin importar correos reales ni alterar repositorios o cachés.

## 5. Matriz de escenarios

| ID | Fixture/transición | Tema | Imágenes | Conducta que se observará | Evidencia futura |
|---|---|---|---|---|---|
| S01 | `body=null` → `01-html-simple.html` | claro | true | espera, preparación y primera carga | captura + traza |
| S02 | `01-html-simple.html` | claro | true | HTML simple y render final | captura + traza |
| S03 | `01-html-simple.html` | oscuro | true | colores y darkening | captura + traza |
| S04 | `02-newsletter-tabla.html` | claro | true | HTML complejo, tabla y viewport | captura + traza |
| S05 | `02-newsletter-tabla.html` | oscuro | true | tabla compleja en oscuro | captura + traza |
| S06 | `03-imagen-remota.html` | claro | true | recurso remoto permitido por settings | captura + traza |
| S07 | `03-imagen-remota.html` | claro | false | recurso remoto bloqueado/oculto | captura + traza |
| S08 | `04-imagen-data.html` | claro | false | `data:` no oculto por la política remota | captura + traza |
| S09 | `05-enlace-externo.html` | claro | true | intento de Custom Tab sin abandonar detalle | captura + traza |
| S10 | recomposición equivalente de `01-html-simple.html` | claro | true | misma clave, sin recarga | traza |
| S11 | `01-html-simple.html` → `02-newsletter-tabla.html` | claro | true | nueva clave por cambio de body | captura + traza |
| S12 | `01-html-simple.html`, claro → oscuro | mixto | true | nueva clave por tema | captura + traza |
| S13 | `03-imagen-remota.html`, true → false | claro | mixto | nueva clave por política de imágenes | captura + traza |
| S14 | documento largo derivado de `02-newsletter-tabla.html` | claro | true | scroll, pausa y reanudación | captura + traza |
| S15 | `04-imagen-data.html` | claro | true | long-press entrega URL `data:` no vacía | captura + traza |
| S16 | `01-html-simple.html`, salir y reabrir | claro | true | liberación y nueva instancia/carga | captura + traza |

Para S14, "derivado" significa repetir 20 veces, solo durante el montaje del
escenario de prueba, el bloque de tabla interior de la fixture 02. No existe
una sexta fixture ni se modifica el archivo 02. La transformación se realizará
en código de prueba de la Subfase 2.3 o se documentará como paso manual en 3.2.

## 6. Contrato de capturas

- Formato PNG, orientación vertical, tamaño nativo del dispositivo, sin recortes.
- Sin notificaciones visibles ni datos de cuenta.

## 7. Contrato de trazas

- Filtro por tag `MailRenderTrace`; solo líneas que contengan la clave anónima
  asignada al escenario.
- Prohibido registrar body, asunto, remitente, destinatario, tokens o IDs reales.
- Conservar las líneas completas sin editar tiempos, thread, layer, event ni
  payload.

## 8. Convención de nombres

Nombres exactos reservados para capturas de emulador (Subfases 3.1/3.2):

```text
S01-null-a-simple-claro.png
S02-simple-claro.png
S03-simple-oscuro.png
S04-newsletter-claro.png
S05-newsletter-oscuro.png
S06-remota-habilitada.png
S07-remota-bloqueada.png
S08-data-bloqueo-remoto.png
S09-enlace-custom-tab.png
S11-cambio-body.png
S12-cambio-tema.png
S13-cambio-politica-imagenes.png
S14-scroll-antes-pausa.png
S14-scroll-despues-resume.png
S15-long-press-data.png
S16-reapertura.png
```

S10 no tiene captura: su contrato es la ausencia de recarga y se demuestra con
trazas.

Nombres exactos reservados para trazas de emulador (Subfases 3.1/3.2):

```text
S01-null-a-simple-claro.log
S02-simple-claro.log
S03-simple-oscuro.log
S04-newsletter-claro.log
S05-newsletter-oscuro.log
S06-remota-habilitada.log
S07-remota-bloqueada.log
S08-data-bloqueo-remoto.log
S09-enlace-custom-tab.log
S10-recomposicion-equivalente.log
S11-cambio-body.log
S12-cambio-tema.log
S13-cambio-politica-imagenes.log
S14-scroll-lifecycle.log
S15-long-press-data.log
S16-release-reapertura.log
```

Evidencia de dispositivo físico (Subfase 4.1): las capturas y trazas reutilizan
el nombre correspondiente con el sufijo `-fisico` inmediatamente antes de la
extensión, por ejemplo `S04-newsletter-claro-fisico.png` y
`S04-newsletter-claro-fisico.log`. No se crean subdirectorios por dispositivo.

## 9. Criterios de aceptación de la evidencia futura

- Captura legible.
- Traza no vacía cuando aplique.
- Correspondencia uno-a-uno con el escenario.
- Ausencia de datos personales.
- Registro explícito `NO_OBSERVABLE` o `BLOQUEADO` si Android no permite
  obtener evidencia estable.

## 10. Resultado de la Subfase 1.3

Archivos creados en esta subfase:

```text
docs/verification/emailbody-webview-baseline/fixtures/01-html-simple.html
docs/verification/emailbody-webview-baseline/fixtures/02-newsletter-tabla.html
docs/verification/emailbody-webview-baseline/fixtures/03-imagen-remota.html
docs/verification/emailbody-webview-baseline/fixtures/04-imagen-data.html
docs/verification/emailbody-webview-baseline/fixtures/05-enlace-externo.html
docs/verification/emailbody-webview-baseline/capturas/README.md
docs/verification/emailbody-webview-baseline/trazas/README.md
docs/verification/emailbody-webview-baseline/fixtures-y-evidencia.md
```

Al cierre de la Subfase 1.3, `capturas/README.md` y `trazas/README.md`
reservaban los nombres futuros, pero todavía no constituían evidencia de
ejecución. La evidencia real se incorporó posteriormente en la Subfase 3.1.

## 11. Hallazgo posterior — Subfase 3.1 (2026-08-25)

La matriz funcional de emulador confirmó que F02 reproduce un overflow
horizontal en `Medium_Phone_API_36.1` (1080×2400): la frase superior se corta
a la derecha y la tercera columna queda fuera del viewport en S04 (claro),
S05 (oscuro) y S11 (estado final después de cambiar el body).

Las capturas y trazas correspondientes se conservan en `capturas/` y
`trazas/`, con hashes registrados en `resultados-subfase-3.1.md`. El hallazgo
se acepta como defecto conocido del baseline actual, no como resultado visual
deseable. El refactor debe preservarlo mientras no exista un plan correctivo
específico; cualquier cambio de tablas, escala o viewport requiere pruebas y
aprobación explícitas y no puede introducirse silenciosamente.
