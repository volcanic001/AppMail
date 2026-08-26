# Resultados — Subfase 4.2: paquete de handoff

## 1. Estado

**COMPLETADA — GO.** La línea base quedó consolidada, auditada y preparada
para versionarse en un único commit aislado. No se modificó producción,
configuración Gradle, navegación ni la API pública de `EmailBodyWebView`.

## 2. Identidad

- Fecha local: 2026-08-26, CST (`-0600`).
- Rama: `main`.
- HEAD base: `2a433a67af4ea6f4f27e320133c963a408e80453`.
- Mensaje reservado: `docs(emailbody): establish webview baseline`.
- El SHA definitivo se reporta después de crear el commit y no se introduce
  en este archivo para evitar una autorreferencia imposible de estabilizar.

## 3. Auditoría del paquete

- Cinco fixtures canónicas y cinco assets instrumentados: **5/5 idénticos**
  mediante `cmp`.
- Capturas: **32/32**, no vacías y reconocidas como PNG válido.
- Trazas: **32/32**, no vacías y con evidencia `MailRenderTrace`.
- Reportes: **16/16 XML parseables**, todos con `failures=0`, `errors=0` y
  `skipped=0`.
- Hashes: 41 entradas publicadas de capturas/trazas recalculadas sin ninguna
  discrepancia; los SHA de reportes y fixtures coinciden con sus informes.
- Privacidad: sin valores de credenciales, tokens Bearer/access/refresh ni
  direcciones Gmail/Outlook en trazas, XML o assets.
- `git diff --check`: limpio antes de incorporar los archivos sin seguimiento.
- Los XML conservan CRLF generado por Gradle; el staging se valida con
  `git -c core.whitespace=cr-at-eol diff --cached --check` para aceptar ese fin
  de línea sin modificar los hashes aprobados.

No se repitieron suites: 4.2 solo consolida documentación y no cambia código
después de la puerta física 22/22 aceptada en 4.1.

## 4. Alcance definitivo

Incluido en el commit:

- `EmailBodyWebViewBaselineTest.kt`;
- cinco assets de `app/src/androidTest/assets/emailbody-webview/`;
- estabilizaciones 2.2-R de `EmailDetailCancellationTest.kt` y
  `EmailDetailPresentationTest.kt`;
- todo `docs/verification/emailbody-webview-baseline/`.

Excluido y preservado sin editar ni revertir:

- `ComposeScreen.kt`;
- `MainNavHost.kt`;
- `gradle.properties`.

La presencia de cinco assets y dos estabilizaciones test-only es una
ampliación autorizada y documentada frente al alcance original. No introduce
hooks de producción ni cambios de comportamiento.

## 5. Contratos y excepciones

El registro técnico contiene la tabla definitiva, con una fila por contrato:
**34 automatizados, 6 manuales y 0 no observables**, todos aprobados.

Se conservan como excepciones conocidas el overflow horizontal de F02, la
inestabilidad histórica previa a 2.2-R y las incidencias transitorias de
infraestructura documentadas en 3.1 y 4.1. Ninguna se atribuye a una
modificación de producción.

## 6. Puerta de commit

Antes del commit se exige:

1. staging construido únicamente con la allowlist de la sección 4;
2. ausencia de archivos bajo `app/src/main/` y de configuración Gradle;
3. `git -c core.whitespace=cr-at-eol diff --cached --check` limpio;
4. hashes protegidos idénticos y `EmailBodyWebView.kt` en 669 líneas;
5. estado posterior con solo los tres cambios excluidos.

## 7. Decisión

**GO — paquete de handoff cerrado y apto para el Plan B.**
