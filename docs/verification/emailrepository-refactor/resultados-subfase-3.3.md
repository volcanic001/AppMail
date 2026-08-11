# Resultados — Subfase 3.3, Imágenes inline

## Identificación
- Plan maestro: Refactor estructural de `EmailRepository` — Etapa 3, Subfase 3.3.
- Fecha: 2026-08-10, CST (`-0600`).
- Objetivo: extraer `downloadInlineImages` e `injectInlineImages` a `EmailContentCoordinator` conservando cortocircuito vacío, delegación exacta, tres variantes CID, sensibilidad a mayúsculas/prefijos, orden del mapa, logs y mediciones.

## Implementación
- **Ampliado** `EmailContentCoordinator.kt`: `downloadInlineImages` (delegación exacta, cortocircuito `refs.isEmpty()`, `providerFactory()` dinámico, `[REPO_INLINE]` logs, retorno de misma instancia de mapa) e `injectInlineImages` (cortocircuito `inlineImages.isEmpty()`, sustitución literal de `cid:$cid` / `cid:&lt;$cid&gt;` / `cid:<$cid>`, `[REPO_INJECT]` logs). Nuevo import de `InlineImageRef`.
- **Modificado** `EmailRepository.kt`: ambos métodos convertidos a delegaciones de 1 línea a `contentCoordinator`. Sin imports retirados (todos siguen usados por PDF/resolución).

## Gates
- JVM: 584/584
- Instrumentación: `EmailRepositoryContentContractsTest` en
  `Medium_Phone_API_36.1(AVD) - 16`, serial `emulator-5554`: 3×19=57
  (0 fallos, 0 errores, 0 omitidas).
- Lint: 0 errores, 65 advertencias (referencia)

## Incidencia corregida
- La auditoría posterior detectó que las primeras tres corridas se habían hecho
  en `Pixel 9 - 17`, distinto del dispositivo fijado por el plan. El gate se
  repitió completo tres veces en `Medium_Phone_API_36.1`; las tres corridas
  finalizaron con `BUILD SUCCESSFUL` y 19/19.

## Integridad
- 20 métodos + MAX_PDF_SIZE intactos. Coordinadores anteriores y gateway con fingerprints intactos. Archivos protegidos (`ComposeScreen.kt`, `MainNavHost.kt`) con hashes coincidentes con el plan. `git diff --check` limpio. Solo archivos del allowlist modificados.

## Cierre
- Commit **`refactor(repository): extract inline image coordination`** (4 archivos, sin push). Etapa 3 aprobada. Etapa 4 y Subfase 4.1 pendientes de su plan técnico cerrado.
