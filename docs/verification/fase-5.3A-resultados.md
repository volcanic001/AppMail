# Fase 5.3A — Correcciones de auditoría: Resultados

**Fecha:** 2026-08-03
**Sistema:** macOS 25.5.0 (x86_64)
**Java:** JDK 17 (jvmToolchain(17))
**Gradle:** 9.6.1, AGP 9.0.0, Kotlin 2.1.20

## SHA

- **Inicial:** `f587326` (Completar fase 5.2 de validación Android)

## Hallazgos corregidos

### 1. Capturas que absorbían CancellationException (2)

| Archivo | Problema | Corrección |
|---------|----------|------------|
| `MainActivity.kt:launchOAuth()` | `catch (_: Exception)` absorbía CancellationException | Extraído a `runOAuthLaunchPreflight()` en `OAuthLaunchPreflight.kt` — relanza CancellationException |
| `GmailAuthClient.kt:handleOAuthRedirect()` | `catch (e: Exception)` absorbía CancellationException | Extraído a `runOAuthTokenExchange()` en `OAuthTokenExchange.kt` — relanza CancellationException |

### 2. Capturas completamente vacías (4)

| # | Archivo | Línea | Contexto | Log añadido |
|---|---------|-------|----------|-------------|
| 1 | `SessionCoordinator.kt` | 199 | PDF cleanup failure during invalidation | `Log.w(TAG, "PDF cleanup failed during invalidation", e)` |
| 2 | `EmailDetailScreen.kt` | 217 | SAF document deletion failure | `Log.w(TAG, "Failed to delete partial SAF document", e)` |
| 3 | `EmailBodyWebView.kt` | 609 | WebView modern API link opening | `Log.w(TAG, "Failed to open link via modern WebView API", e)` |
| 4 | `EmailBodyWebView.kt` | 621 | WebView legacy link opening | `Log.w(TAG, "Failed to open link via legacy WebView API", e)` |

## Archivos modificados/creados

| Archivo | Acción |
|---------|--------|
| `core/auth/OAuthLaunchPreflight.kt` | Nuevo — helper `runOAuthLaunchPreflight()` + `OAuthLaunchPreflightResult` |
| `core/auth/OAuthTokenExchange.kt` | Nuevo — helper `runOAuthTokenExchange()` |
| `MainActivity.kt` | Modificado — delegar preflight al helper |
| `core/auth/GmailAuthClient.kt` | Modificado — delegar intercambio al helper |
| `core/di/SessionCoordinator.kt` | Modificado — añadir `Log.w` + TAG |
| `feature/emaildetail/EmailDetailScreen.kt` | Modificado — añadir `Log.w` + TAG |
| `feature/emaildetail/components/EmailBodyWebView.kt` | Modificado — añadir `Log.w` + TAG (2 catches) |

## Contratos de los helpers

### `runOAuthLaunchPreflight`

```kotlin
internal suspend fun runOAuthLaunchPreflight(
    isPendingPdfCleanup: suspend () -> Boolean,
    clearPdfCache: suspend () -> PdfCacheClearResult,
    markPdfCleanupCompleted: suspend () -> Unit
): OAuthLaunchPreflightResult
```

- `Ready` — sin limpieza pendiente, o limpieza exitosa + marcador retirado
- `Failed(TEMP_CLEANUP_FAILED)` — `PdfCacheClearResult.Failure`, marcador conservado
- `Failed(LOCAL_CLEANUP_CHECK_FAILED)` — excepción ordinaria
- `CancellationException` — relanzada

### `runOAuthTokenExchange`

```kotlin
internal suspend fun runOAuthTokenExchange(
    exchange: suspend () -> Unit
): OAuthRedirectResult
```

- `Success` — bloque completado
- `TokenExchangeFailed` — excepción ordinaria
- `CancellationException` — relanzada

## Pruebas de regresión (10 nuevas)

### OAuthLaunchPreflightTest (7)

| # | Prueba | Resultado |
|---|--------|-----------|
| 1 | `noPendingCleanup_returnsReadyWithoutClearing` | ✅ |
| 2 | `successfulCleanup_clearsMarkerAndReturnsReady` | ✅ |
| 3 | `typedCleanupFailure_keepsMarkerAndReturnsTempCleanupFailure` | ✅ |
| 4 | `ordinaryException_returnsLocalCleanupCheckFailure` | ✅ |
| 5 | `cancellationWhileCheckingMarker_isRethrownAsSameInstance` | ✅ |
| 6 | `cancellationDuringCleanup_isRethrownAsSameInstance` | ✅ |
| 7 | `cancellationWhileClearingMarker_isRethrownAsSameInstance` | ✅ |

### GmailAuthClientCancellationTest (3)

| # | Prueba | Resultado |
|---|--------|-----------|
| 8 | `successfulExchange_returnsSuccess` | ✅ |
| 9 | `ordinaryExchangeFailure_returnsTokenExchangeFailed` | ✅ |
| 10 | `exchangeCancellation_isRethrownAsSameInstance` | ✅ |

## Suite JVM

**Comando:** `./gradlew testDebugUnitTest --rerun-tasks`

| Métrica | Valor |
|---------|-------|
| Tests | 530 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Archivos XML | 54 |

✅ Mínimo 530 confirmado.

## Lint

**Comando:** `./gradlew lintDebug lintRelease --rerun-tasks`

| Variante | Errores | MissingKeepAnnotation | CredentialDependency |
|----------|---------|-----------------------|---------------------|
| Debug | 0 | 0 | 0 |
| Release | 0 | 0 | 0 |

## Ensamblado

**Comando:** `./gradlew assembleDebug assembleRelease --rerun-tasks`

- ✅ APK debug generado
- ✅ APK release generado con R8 (`minifyReleaseWithR8`)
- ✅ Reducción de recursos (`optimizeReleaseResources`)

## Conteo de capturas vacías

```
grep -rn 'catch\s*([^)]*)\s*{\s*}' app/src/main/java/
```

**Resultado:** 0 coincidencias ✅

## Confirmaciones

- ✅ No se modificaron APIs públicas, Room, DAO, entidades, esquemas, rutas ni SavedState
- ✅ No se añadieron dependencias
- ✅ No se actualizó SDK, AGP, Kotlin ni librerías
- ✅ No se usaron red, Gmail real ni credenciales
- ✅ Suite instrumentada de 94 pruebas reservada para 5.3B
- ✅ 5.json y 6.json intactos, sin 7.json
- ✅ Worktree listo para commit
