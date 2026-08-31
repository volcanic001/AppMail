# Resultados — Subfase 3.1: Placeholders

Fecha: 2026-08-31 (CST)

## Cambios realizados
- Se movió `InboxErrorContent` de forma literal de `InboxStatePlaceholders.kt` a `InboxPlaceholders.kt`, preservando UiErrorReason, toUiText().asString(), símbolo, tamaños, estilos y reintento.
- Se eliminó el archivo obsoleto `InboxStatePlaceholders.kt`.
- No se realizaron cambios en `InboxContent`, `InboxScreen` ni componentes dinámicos de Inbox.

## Verificación

| Verificación | Comando / Acción | Resultado |
|---|---|---|
| Compilación completa | `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin --rerun-tasks` | exit 0 (BUILD SUCCESSFUL) |
| JVM focales Inbox | `./gradlew testDebugUnitTest --tests "com.david.mailapp.feature.inbox.*" --rerun-tasks` | 28/28 APROBADOS (exit 0) |
| Tests instrumentados Compose | `./gradlew :app:connectedDebugAndroidTest` | 5/5 APROBADOS en Pixel 9 (exit 0) |
| Git diff check | `git diff --check` | Limpio (exit 0) |

## Evidencia visual baseline / post

Se capturaron los tres estados estáticos desde `427e943` (baseline) y
`611cf4e` (post-refactor) con el mismo harness Compose temporal, reloj
controlado y fixtures sintéticos. El harness no quedó versionado. Cada par se
comparó por SHA-256 de píxeles ARGB y por identidad binaria del PNG.

| Dispositivo | Estado | Dimensiones | SHA-256 de píxeles baseline = post | PNG |
|---|---|---:|---|---|
| Medium_Phone_API_36.1 (API 36) | Loading | 1080×2400 | `9de695eff7aeb5e860d48bf9a8bda781207e80fa2b88d74406962abe84cc6645` | idéntico |
| Medium_Phone_API_36.1 (API 36) | Error | 1080×2400 | `2e3b940a226126b91941eb713df1e073eb124386b8913362403dfdae5e5aa79a` | idéntico |
| Medium_Phone_API_36.1 (API 36) | Empty | 1080×2400 | `ba2d8d6330cbbb0f040a1c2ed41f4763a061e9fae54accbd9941a66c3c305a5e` | idéntico |
| Pixel 9 (API 37) | Loading | 1080×2424 | `b886197d1f12870225f3d4524173546faf32611ab0d0008e320d60ff7d5c0194` | idéntico |
| Pixel 9 (API 37) | Error | 1080×2424 | `e63ab8134bb8e509313bab847883583ef430099a16d39b733cf458b3b020d4bf` | idéntico |
| Pixel 9 (API 37) | Empty | 1080×2424 | `263cb30d6c40c2ab1b37340469720e1cc40042493a06521a7282dd5dbdb37902` | idéntico |

Los 24 artefactos (PNG y manifiestos para cada lado de la comparación) están
en `capturas/3.1/<dispositivo>/{baseline,post}/` junto a este registro.

## Integridad de Archivos Ajenos Protegidos

Los hashes SHA-1 de los archivos ajenos coinciden exactamente con el baseline pre-cambio:
- `ComposeScreen.kt`: `70334824e407daf48f6e1ec445e7114722c6e1eb` (OK)
- `MainNavHost.kt`: `411373faa8cd6f32c98c708830bd494f9aab40b8` (OK)
- `gradle.properties`: `49a749189f38ad45b2bec7aca6a7bf865cc932f5` (OK)

## Criterio de salida
**GO.** Se ha consolidado la presentación de placeholders estáticos (`EmptyInbox`, `ShimmerLoading` y `InboxErrorContent`) en un único archivo (`InboxPlaceholders.kt`). La red de seguridad está totalmente en verde y no se han alterado archivos ajenos ni lógica de negocio.
