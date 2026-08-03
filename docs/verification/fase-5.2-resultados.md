# Fase 5.2 — Validación Android: Resultados

**Fecha:** 2026-08-03
**Sistema:** macOS 25.5.0 (x86_64), iMac-de-David.local
**Java:** JDK 17 (jvmToolchain(17))
**Gradle:** 9.6.1
**AGP:** 9.0.0
**Kotlin:** 2.1.20
**Android SDK:** compileSdk 36, minSdk 26, targetSdk 36

## SHA

- **Inicial:** `e61ff43` (Completar fase 5.1 de pruebas unitarias e integración)

## Correcciones previas de lint

### 1. `@Keep` en ComposeMode

**Archivo:** `app/src/main/java/com/david/mailapp/feature/compose/ComposeUiState.kt`

Añadido `import androidx.annotation.Keep` y anotación `@Keep` al enum `ComposeMode`. Conserva `@Serializable` y todos sus valores (`WRITE`, `REPLY`, `FORWARD`).

### 2. Retirar Credential Manager

**Archivos:**
- `app/build.gradle.kts` — eliminado `implementation(libs.credentials)`
- `gradle/libs.versions.toml` — eliminado alias `credentials`

La dependencia se retira porque la autenticación actual usa Custom Tabs + PKCE sin ninguna referencia a Credential Manager.

## Suite JVM completa (desde clean)

**Comando:**
```
./gradlew clean
./gradlew testDebugUnitTest --rerun-tasks
```

**Resultados (desde XMLs):**

| Métrica | Valor |
|---------|-------|
| Tests | 520 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Archivos XML | 52 |

✅ Mínimo 520 confirmado.

## Lint

**Comando:**
```
./gradlew lintDebug lintRelease --rerun-tasks
```

**Resultado:** BUILD SUCCESSFUL

### Advertencias corregidas

| ID | Estado |
|----|--------|
| MissingKeepAnnotation | ✅ 0 incidencias |
| CredentialDependency | ✅ 0 incidencias |

### Advertencias aceptadas (debug)

| ID | Categoría |
|----|-----------|
| AndroidGradlePluginVersion | Nueva versión de plugin |
| ConfigurationScreenWidthHeight | Estilo Compose |
| FrequentlyChangingValue | Rendimiento |
| GradleDependency | Versiones de dependencias |
| IconLocation | Recursos |
| ModifierParameter | Estilo Compose |
| NewerVersionAvailable | Nuevas versiones |
| ObsoleteSdkInt | SDK |
| OldTargetApi | targetSdk 36 (aceptado) |
| UnusedResources | Recursos no utilizados |
| UseKtx | Recomendación |
| UseOfNonLambdaOffsetOverload | Estilo Compose |

### Advertencias aceptadas (release)

| ID | Categoría |
|----|-----------|
| AndroidGradlePluginVersion | Nueva versión de plugin |
| ConfigurationScreenWidthHeight | Estilo Compose |
| GradleDependency | Versiones de dependencias |
| IconLocation | Recursos |
| ModifierParameter | Estilo Compose |
| MonochromeLauncherIcon | Recurso (release only) |
| NewerVersionAvailable | Nuevas versiones |
| ObsoleteSdkInt | SDK |
| OldTargetApi | targetSdk 36 (aceptado) |
| UnusedResources | Recursos no utilizados |
| UseKtx | Recomendación |
| UseOfNonLambdaOffsetOverload | Estilo Compose |

✅ 0 errores, 0 MissingKeepAnnotation, 0 CredentialDependency.
✅ No se creó lint-baseline.xml.

## Ensamblado

**Comandos:**
```
./gradlew assembleDebug assembleRelease assembleDebugAndroidTest --rerun-tasks
```

**Resultado:** BUILD SUCCESSFUL

- ✅ Compilación debug completa
- ✅ Compilación release con R8 (`minifyReleaseWithR8`)
- ✅ Reducción de recursos (`optimizeReleaseResources`)
- ✅ Generación del APK de pruebas instrumentadas

## Suite instrumentada completa

**AVD:** Medium_Phone_API_36.1 (API 36)
**Serial:** emulator-5554
**Puerto:** 5554
**Arranque:** `-no-snapshot-load`

**Comando:**
```
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks
```

**Resultados (desde XML):**

| Métrica | Valor |
|---------|-------|
| Tests | 94 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Duración | 140.09s (~6m 30s Gradle) |

✅ Mínimo 94 confirmado.
✅ Dispositivo: `Medium_Phone_API_36.1(AVD) - 16` — no Pixel físico.
✅ Incluye las 12 pruebas adicionales: Keystore, gesto de Inbox y animación del indicador.

## Verificación de artefactos

### APKs generados

| APK | Ruta | Tamaño |
|-----|------|--------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 24M |
| Release | `app/build/outputs/apk/release/app-release.apk` | 4.7M |
| AndroidTest | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 1.2M |

### SHA-256

| APK | Hash |
|-----|------|
| Debug | `19007274411227b4e041c05c40ae930f74adcc1d1304136ec40a278199eac896` |
| Release | `8b7f59bcc15b209a387399d7b367a4a9109c2e8f5c0012775d8faad30fddd11d` |
| AndroidTest | `b4eb6f591abad8adfbdf5488ab37a6dc3d08f7fd996997acba8f2ae12ec666c8` |

### Firmas (apksigner)

| APK | Resultado | Esquema | Firmante |
|-----|-----------|---------|----------|
| Debug | ✅ Verifies | v2 | CN=Android Debug |
| Release | ✅ Verifies | v2 | CN=Android Debug |
| AndroidTest | ✅ Verifies | v2 | CN=Android Debug |

Herramienta: `build-tools/36.1.0/apksigner`

### Alineación (zipalign)

| APK | Resultado |
|-----|-----------|
| Debug | ✅ Verification successful |
| Release | ✅ Verification successful |
| AndroidTest | ✅ Verification successful |

Herramienta: `build-tools/36.1.0/zipalign -c -v 4`

### Application ID (aapt)

| APK | Application ID |
|-----|---------------|
| Debug | `com.david.mailapp` ✅ |
| Release | `com.david.mailapp` ✅ |

## Limitación documentada

El APK release está firmado con el certificado **Android Debug** (`CN=Android Debug`). Esto es una limitación conocida de esta entrega: demuestra compilación, minificación (R8) y empaquetado, pero no preparación para publicación. No debe reemplazarse por una firma de producción en esta fase.

## Fallos encontrados

Ninguno.

## Confirmaciones

- ✅ Pixel físico no fue utilizado — dispositivo: `Medium_Phone_API_36.1(AVD) - 16`
- ✅ No se usaron credenciales Gmail real ni datos del usuario
- ✅ Schemas Room 5.json y 6.json sin cambios, sin 7.json
- ✅ `minifyReleaseWithR8` y `optimizeReleaseResources` ejecutados
- ✅ Worktree listo para commit
