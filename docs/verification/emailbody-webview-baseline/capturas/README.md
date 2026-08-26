# capturas — reserva de nombres (Subfase 1.3)

Este directorio fue creado en la Subfase 1.3 del baseline de
`EmailBodyWebView` y se puebla con capturas PNG reales en las Subfases
3.1, 3.2 y 4.1.

## Evidencia presente

Las **11 capturas de la Subfase 3.1** (corrida focal 3, emulador
`Medium_Phone_API_36.1`) están disponibles: `S01-null-a-simple-claro.png`,
`S02-simple-claro.png`, `S03-simple-oscuro.png`, `S04-newsletter-claro.png`,
`S05-newsletter-oscuro.png`, `S06-remota-habilitada.png`,
`S07-remota-bloqueada.png`, `S08-data-bloqueo-remoto.png`,
`S11-cambio-body.png`, `S12-cambio-tema.png` y
`S13-cambio-politica-imagenes.png`. Ver `resultados-subfase-3.1.md` (sección
evidencia y revisión visual). Las 11 fueron revisadas: ocho son conformes y
S04, S05 y S11 quedan aceptadas como evidencia fiel de un defecto conocido de
F02 (overflow horizontal y tercera columna fuera del viewport). Esta
aceptación congela el baseline; no declara correcto el comportamiento. S10 no
genera captura.

Las **5 capturas de la Subfase 3.2** (corrida focal 3) también están
disponibles: `S09-enlace-custom-tab.png`, `S14-scroll-antes-pausa.png`,
`S14-scroll-despues-resume.png`, `S15-long-press-data.png` y
`S16-reapertura.png`. La revisión visual está aprobada y documentada en
`resultados-subfase-3.2.md`: S09 muestra la Custom Tab cargada, las dos S14
son idénticas byte a byte, S15 conserva el área inline 96×96 y S16 muestra
F01 tras la nueva instancia. El acumulado actual es **16 PNG**.

## Nombres reservados (emulador)

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

El escenario S10 no tiene captura: su contrato es la ausencia de recarga y se
demuestra con trazas.

## Evidencia de dispositivo físico

Las **16 capturas físicas de la Subfase 4.1** están disponibles con el sufijo
`-fisico` inmediatamente antes de la extensión y sin subdirectorios por
dispositivo. Todas son PNG reales, no vacíos y de **1080×2424**. La revisión
visual está aprobada en `resultados-subfase-4.1.md`: Vanadium muestra la
Custom Tab de S09, las dos S14 son idénticas byte a byte, S15 conserva el área
inline y S16 muestra F01 tras la reapertura. S04, S05 y S11 reproducen el
overflow F02 ya aceptado como defecto conocido. El acumulado es **32 PNG**:
16 de emulador y 16 físicas.

## Reglas de privacidad y formato

- Formato PNG, orientación vertical y tamaño nativo del dispositivo, sin
  recortar el archivo. El overflow del contenido F02 en S04/S05/S11 es un
  hallazgo preservado, no un recorte aplicado a la captura.
- Sin notificaciones visibles ni datos de cuenta.
- Ausencia total de datos personales, credenciales e identificadores reales.

## Criterios de presencia

- Un archivo ausente significa "pendiente", nunca "aprobado".
- Prohibidos placeholders, archivos vacíos y evidencia fabricada.
