# Resultados — Subfase 5.4, auditoría final y puerta de entrada al refactor

## Identificación y alcance

- Fecha: 2026-08-10, zona CST (`-0600`).
- HEAD previo al cierre: `a96582ad67e86310b2c09b8bc167e7c795cc6ca1`.
- Objetivo auditado: baseline verificable de `EmailRepository` completo, desde la Subfase 1.1 hasta la 5.3.
- Alcance de 5.4: auditoría, documentación y commit de cierre; ningún cambio de comportamiento o producción.
- Commit de cierre previsto: `docs(repository): close verifiable baseline`.

## Integridad de producción y árbol de trabajo

| Control | Resultado |
|---|---|
| `EmailRepository.kt` SHA-256 | `abcac202041966e53383b40613c5c3d027a93bbb0d8c2616fe16a90a424dbe4b` ✓ |
| `MainNavHost.kt` SHA-256 | `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088` ✓ |
| Producción fuera de `MainNavHost.kt` | Sin diferencias respecto de HEAD ✓ |
| Gradle y configuración Android | Sin diferencias respecto de HEAD ✓ |
| Índice antes del cierre | Vacío ✓ |
| `git diff --check` | Limpio; solo apareció el aviso ambiental conocido de `fsmonitor` ✓ |

El único cambio de producción presente es la modificación previa del usuario en
`MainNavHost.kt` (6 inserciones y 2 eliminaciones). Se comprobó su hash y queda excluido
del staging y del commit final.

## Cobertura contractual consolidada

- API pública inventariada: 20 métodos y la constante pública `MAX_PDF_SIZE`.
- Cobertura alta: 20/20 métodos.
- Cobertura media, mínima o ausente: 0 métodos.
- Contratos directos de `EmailRepository`: 140.
- Contratos críticos cerrados: provider dinámico, ausencia de sesión/provider,
  cancelación sin transformación, coordinación de Inbox/Trash, remote-first,
  búsqueda efímera, cambio de sesión y caché PDF atómica sin residuos.

La comparación entre `EmailRepository.kt`, el inventario y la matriz no encontró APIs
públicas sin registrar ni huecos contractuales pendientes.

## Validación acumulada aceptada

| Bloque | Resultado final |
|---|---:|
| JVM | 584/584 |
| Foco instrumentado de repositorio | 137/137 |
| Instrumentación completa | 284/284 |
| Regresión de las dos pruebas corregidas | 42/42 |
| Serie temporal anti-flakiness | 330/330 |
| Lint | 0 errores; 64 advertencias heredadas |
| Pixel 9/API 37 | Matriz manual completa conforme |

No se repitió la instrumentación completa en 5.4: después de la corrida final de 5.2 no
cambió código ejecutable; 5.3 añadió únicamente evidencia y 5.4 documentación. Las dos
correcciones AndroidTest que entran en el commit son exactamente las validadas en 5.2.

## Auditoría de evidencias y anomalías

- Ocho capturas de 5.3 verificadas como PNG RGBA de 1080×2424.
- Metadatos del Pixel, APK y red registrados en `dispositivo.txt`.
- Extracto `EmailResolve`/`MailPerfTrace` saneado y sin credenciales, tokens, URLs de
  Gmail, direcciones completas ni identificadores remotos largos.
- Búsqueda textual sobre documentos y pruebas sin patrones sensibles.
- Logout durante descarga: resultado antiguo en error, cero archivo final, cero `.tmp`
  y cero filas Room de la sesión cerrada.
- Eliminación definitiva: exactamente un fixture sacrificial; `PAGE19_ROWS|0`.

Anomalías conocidas, todas no críticas y preservadas como baseline:

1. HTML literal y fallback visual de inline para el fixture escapado.
2. Re-fetch del cuerpo al reabrir aunque la cabecera resuelva desde Room.
3. Partes PDF expuestas entre referencias inline por el provider.
4. Tokenización/indexación temporal de Gmail en búsquedas con guiones bajos.
5. Efectos neutrales adicionales durante la preparación de fixtures, confinados a la
   cuenta dedicada y documentados sin ocultarlos mediante borrado.

Ninguna contradice los contratos automatizados ni requiere una corrección de lógica antes
del refactor estructural conservador.

## Allowlist del commit final

El cierre solo puede incluir:

- Las correcciones de sincronización AndroidTest en `EmailDetailCancellationTest.kt` y
  `TrashContentActionTest.kt`, validadas en 5.2.
- `registro-tecnico.md` y los resultados de las subfases 5.1–5.4.
- Las evidencias saneadas de la Subfase 5.3 bajo
  `evidencias/subfase-5.3/pixel9-api37`.

`MainNavHost.kt` y cualquier otro archivo quedan expresamente fuera. El contenido real del
commit se valida después de crearlo mediante `git show --stat --name-status HEAD`.

## Decisión y puerta de entrada

**GO — baseline verificable apto para iniciar un plan independiente de refactor
estructural conservador de `EmailRepository`.**

Condiciones obligatorias para el siguiente plan:

- Partir exactamente del commit `docs(repository): close verifiable baseline`, cuyo hash
  completo se obtiene y reporta después de crearlo.
- Mantener inicialmente la API pública y los comportamientos heredados caracterizados.
- No mezclar el refactor estructural con correcciones lógicas.
- Ejecutar las suites protegidas tras cada extracción relevante.
- Detener el refactor ante cualquier divergencia no explicada por movimiento estructural.

El hash del commit se registra en el reporte de ejecución posterior al commit. No se
inserta dentro de este archivo porque un commit no puede contener su propio hash sin
cambiarlo.
