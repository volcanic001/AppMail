# Contrato de Rendimiento — Apertura Rápida y Robusta de Correos

## Objetivo y Vigencia

Este contrato numérico y arquitectónico rige de forma obligatoria la aceptación de las Etapas 1 a 6. Todo cambio posterior deberá medirse en el mismo dispositivo físico (Google Pixel 9, Android 17 / API 37) y bajo las mismas condiciones de prueba de la Etapa 0.

---

## Tabla Contractual de Aceptación

| Escenario Futuro | Peticiones `format=full` | Objetivo Numérico (p95) | Línea Base Etapa 0 (Referencia) |
|---|:---:|---|:---:|
| **Primera apertura de texto** | **Exactamente 1** | Post-HTTP a legible **≤ 300 ms** | 235 ms (cumple margen para render nativo) |
| **Reapertura de texto (proceso vivo)** | **0** | **≤ 300 ms** y **≤ 70 %** del p95 inicial (≤ 408 ms) | 584 ms con 1 petición de red |
| **Reapertura de texto (proceso reiniciado)** | **0** | **≤ 500 ms** y **≤ 80 %** del p95 inicial (≤ 654 ms) | 818 ms con 1 petición de red |
| **HTML simple (render local)** | **0 adicionales** | Mejora **≥ 20 %** frente a la línea base local | — |
| **Inbox y memoria** | — | Sin regresión **> 10 %** en primera presentación y memoria pico | — |

---

## Invariantes Técnicos Mandatorios

1. **Una Sola Ruta de Red por Correo:**
   - Queda estrictamente prohibida cualquier segunda petición `messages.get(format=full)` para un correo ya sincronizado o resuelto.
   - Las listas (Inbox, Papelera, Búsqueda) materializan y persisten metadatos y contenido usando el parser único.
   - Reapertura de cualquier correo disponible en Room produce exactamente **0 peticiones HTTP**.

2. **Renderizado Nativo para Texto Plano:**
   - Mensajes con `EmailBodyKind.PLAIN_TEXT` se renderizarán de forma nativa en Jetpack Compose, eliminando por completo la instanciación de `WebView` y el coste de Jsoup en texto plano.

3. **Caché LRU con Límite Duro de 50 MiB:**
   - La tabla Room de correos mantendrá un presupuesto de 50 MiB por cuenta activa para cuerpos y referencias serializadas.
   - La expulsión LRU cambia el estado a `NOT_FETCHED` y vacía los cuerpos, preservando siempre el resumen y metadatos de lista.

4. **Cero Fugas de Datos Privados:**
   - Las trazas de producción y benchmarks jamás emitirán cabeceras de autorización, tokens, direcciones de correo en claro, asuntos, cuerpos ni identificadores reales de Gmail. Toda correlación usará exclusivamente el hash truncado `mailKey`.

---

## Criterios de Aprobación de Etapas Subsiguientes

Una etapa futura se considerará aprobada únicamente si:
1. Pasa la suite completa JVM sin fallos.
2. Compila limpiamente en debug, release y benchmark.
3. Cumple las metas numéricas de esta tabla demostradas por la herramienta `analyze_traces.py`.
4. El diff de código no incluye archivos ajenos protegidos (`ComposeScreen.kt`, `MainNavHost.kt`, `gradle.properties`).
