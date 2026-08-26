# trazas — reserva de nombres (Subfase 1.3)

Este directorio fue creado en la Subfase 1.3 del baseline de
`EmailBodyWebView` y se puebla con trazas Logcat reales en las Subfases
3.1, 3.2 y 4.1.

## Evidencia presente

Las **12 trazas de la Subfase 3.1** (corrida focal 3, emulador
`Medium_Phone_API_36.1`) están disponibles: `S01-null-a-simple-claro.log`,
`S02-simple-claro.log`, `S03-simple-oscuro.log`,
`S04-newsletter-claro.log`, `S05-newsletter-oscuro.log`,
`S06-remota-habilitada.log`, `S07-remota-bloqueada.log`,
`S08-data-bloqueo-remoto.log`, `S10-recomposicion-equivalente.log`,
`S11-cambio-body.log`, `S12-cambio-tema.log` y
`S13-cambio-politica-imagenes.log`. Ver `resultados-subfase-3.1.md` (sección
evidencia y contrato de trazas).

Las **4 trazas de la Subfase 3.2** (corrida focal 3) están disponibles:
`S09-enlace-custom-tab.log`, `S14-scroll-lifecycle.log`,
`S15-long-press-data.log` y `S16-release-reapertura.log`. Documentan el viaje
a Custom Tab, pausa/reanudación con scroll 1000, la carga usada por el
long-press y la liberación/reapertura respectivamente. La entrega exacta de
la URL `data:` en S15 se demuestra mediante la aserción y el XML porque el
componente no emite una traza propia para el long-press. Ver
`resultados-subfase-3.2.md`. El acumulado actual es **16 trazas**.

## Nombres reservados (emulador)

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

## Evidencia de dispositivo físico

Las **16 trazas físicas de la Subfase 4.1** están disponibles con el sufijo
`-fisico` inmediatamente antes de la extensión y sin subdirectorios por
dispositivo. Conservan exclusivamente líneas `MailRenderTrace`; su privacidad,
presencia y SHA-256 están validados en `resultados-subfase-4.1.md`. S09
documenta pausa/reanudación durante la Custom Tab, S14 restaura
`scrollY=1000`, S15 respalda el ciclo usado por el long-press y S16 documenta
la liberación y las dos instancias. El acumulado es **32 trazas**: 16 de
emulador y 16 físicas.

## Reglas de privacidad y formato

- Filtro por tag `MailRenderTrace`; solo líneas que contengan la clave anónima
  asignada al escenario.
- Prohibido registrar body, asunto, remitente, destinatario, tokens o IDs reales.
- Conservar las líneas completas sin editar tiempos, thread, layer, event ni
  payload.

## Criterios de presencia

- Un archivo ausente significa "pendiente", nunca "aprobado".
- Prohibidos placeholders, archivos vacíos y evidencia fabricada.
