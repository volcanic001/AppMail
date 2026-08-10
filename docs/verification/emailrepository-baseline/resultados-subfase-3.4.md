# Resultados — Subfase 3.4, imágenes inline e inyección CID

## Identificación

- Etapa: 3 — Resolución, acciones, cuerpos e imágenes inline.
- Subfase: 3.4 — Imágenes inline e inyección CID.
- Ejecución final: 2026-08-09, zona CST (`-0600`).
- Dispositivo: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suite ampliada: `EmailRepositoryContentContractsTest` (10 → 19 casos).

## Contratos añadidos

### `downloadInlineImages`

| Caso | Contrato protegido |
|---|---|
| `c11_downloadInlineImages_empty_refs_returns_empty_without_resolving_provider` | Referencias vacías: devuelve mapa vacío sin resolver siquiera el provider vigente (contador de `providerFactory` en cero). |
| `c12_downloadInlineImages_delegates_exact_refs_order_and_returns_same_map` | Éxito parcial: delega exactamente emailId, referencias y orden; devuelve la misma instancia del mapa sin filtrar. |
| `c13_downloadInlineImages_without_provider_returns_empty_map` | Proveedor ausente: devuelve mapa vacío. |
| `c14_downloadInlineImages_remote_error_propagates_same_instance` | Excepción ordinaria: propaga la misma instancia. |
| `c15_downloadInlineImages_remote_cancellation_propagates_same_instance` | `CancellationException`: propaga la misma instancia. |

### `injectInlineImages`

| Caso | Contrato protegido |
|---|---|
| `c16_injectInlineImages_empty_map_returns_same_html_instance` | Mapa de imágenes vacío: devuelve la misma instancia del HTML. |
| `c17_injectInlineImages_replaces_all_three_cid_variants_everywhere` | Sustitución de todas las apariciones de las tres variantes actuales: `cid:id`, `cid:&lt;id&gt;` y `cid:<id>`. |
| `c18_injectInlineImages_no_match_and_case_mismatch_leave_html_unchanged` | CID sin coincidencia y diferencias de mayúsculas: el HTML permanece igual. |
| `c19_injectInlineImages_similar_ids_depend_on_map_order_prefix_replaced` | IDs similares con `linkedMapOf`: el resultado depende del orden y un CID corto reemplaza el prefijo de otro más largo (`img` → `DATA_SHORT2` sobre `cid:img2`). Comportamiento heredado sospechoso, documentado y no corregido. |

## Cambios en fakes

- `FakeEmailProvider` (solo AndroidTest): registro de `emailId` y lista ordenada de `InlineImageRef` recibidos por `downloadInlineImages` en `receivedInlineImageRequests`. Resultados, errores, gates y cancelación existentes intactos.

## Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 27s
30 actionable tasks: 2 executed, 28 up-to-date
```

## Validación instrumentada

### Suite ampliada (19 casos)

```text
Starting 19 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 19 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 1m 17s
```

Resultado esperado 19/19 cumplido.

### Corrida conjunta de la Etapa 3 (73 casos)

```text
Starting 73 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 73 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL in 1m
```

| Suite | Casos |
|---|---:|
| `EmailResolutionContractsTest` | 29 |
| `EmailRepositoryActionContractsTest` | 25 |
| `EmailRepositoryContentContractsTest` | 19 |
| **Total** | **73** |

El XML conjunto reportó 73 pruebas, 0 fallos, 0 errores, 0 omitidas y 6.136 s agregados.

## Comportamiento heredado sospechoso registrado

`injectInlineImages` reemplaza en orden de iteración del mapa; un CID corto (`img`) sustituye también el prefijo de otro más largo (`img2` → `DATA_SHORT2`) cuando aparece primero. El resultado depende del orden del mapa. Este comportamiento queda caracterizado en `c19` y explícitamente identificado para el futuro refactor lógico, sin corregirse durante el baseline.

## Integridad y cierre

- Cobertura directa: 101 → 110 casos.
- `downloadInlineImages` e `injectInlineImages` pasan de cobertura ausente a alta.
- No se introdujeron contratos de Room, lease o sesión: ambas APIs no escriben ni usan `SessionWriteGuard`.
- No se modificaron producción, Room, providers reales ni Gradle.
- `EmailRepository.kt` conserva su hash `abcac202…`; `MainNavHost.kt` conserva el cambio previo del usuario (hash `a6840cfc…`).
- La Subfase 3.4 y la Etapa 3 quedan cerradas; se crea el commit de cierre `test(repository): characterize resolution actions and content` excluyendo `MainNavHost.kt`.
