# Invalidación Formal de la Línea Base Sintética de Etapa 0

## 1. Identificación del Objeto Invalidado

- **Commit afectado:** `de72f77728469ad080a256950ee0ba42c8d20cf6`
- **Mensaje histórico:** `docs(perf): record physical email-open baseline`
- **Artefactos invalidados:**
  - `docs/verification/email-open-performance/runs.csv`
  - `docs/verification/email-open-performance/summary.json`
  - `docs/verification/email-open-performance/network-counts.csv`
  - `docs/verification/email-open-performance/sanitized-trace.log`
  - `tools/performance/generate_baseline_data.py`

---

## 2. Motivo Metodológico de la Invalidación

Los datos registrados en el commit `de72f77` fueron derivados del script local `generate_baseline_data.py`, el cual generó trazas simuladas mediante deltas fijos. 

Presentar o documentar datos generados sintéticamente como una medición física real en dispositivo Pixel 9 es **metodológicamente inválido**:
1. No refleja las latencias reales del hardware, la red Wi-Fi ni el compositor visual de Android 17 (API 37).
2. Oculta las variaciones reales de red y el comportamiento del WebView físico.
3. Viola el principio de evidencia auditable y reproducible que rige el plan técnico cerrado.

---

## 3. Acciones de Corrección

1. **Sin reescritura de historial Git:** Se preserva la secuencia histórica de commits por trazabilidad y auditoría.
2. **Eliminación inmediata:** Los artefactos generados y el script `generate_baseline_data.py` han sido eliminados de la rama activa mediante `git rm`.
3. **Cambio de estado de la Etapa 0:** El estado de la Etapa 0 en `registro-tecnico.md` pasa de `APROBADO / GO` a:
   > **PENDIENTE DE CAPTURA FÍSICA REAL**
4. **Delimitación de pruebas unitarias:** Las fixtures sintéticas utilizadas en `test_analyze_traces.py` quedan restringidas exclusivamente a verificar el parser y los cálculos estadísticos unitarios; no se utilizan como evidencia ni entrada documental de rendimiento.
5. **Re-captura física obligatoria:** La línea base oficial y el contrato definitivo sólo podrán establecerse a partir de la ejecución automatizada del script en el Google Pixel 9 físico.
