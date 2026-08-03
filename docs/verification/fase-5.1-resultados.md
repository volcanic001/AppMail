# Fase 5.1 — Pruebas unitarias e integración: Resultados

**Fecha:** 2026-08-03
**Sistema:** macOS 25.5.0 (x86_64), iMac-de-David.local
**Java:** JDK 17 (jvmToolchain(17))
**Gradle:** 9.6.1
**Android SDK:** compileSdk 36, minSdk 26, targetSdk 36 (AGP 9.0.0, Kotlin 2.1.20)

## SHA

- **Inicial:** `9c37a15224d43235b06c81cd15e66014c87166a7`
- **Final:** `9c37a15224d43235b06c81cd15e66014c87166a7` (sin cambios funcionales)

## Suite JVM completa

**Comando:**
```
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
| Duración Gradle | 31s |

**Fuente:** `app/build/test-results/testDebugUnitTest/TEST-*.xml`

✅ Línea base confirmada: 520 pruebas.

## Suite instrumentada contractual dirigida

**AVD:** Medium_Phone_API_36.1 (API 36), puerto 5554
**Serial:** emulator-5554
**Dispositivo reportado:** `Medium_Phone_API_36.1(AVD) - 16`

**Comando:**
```
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.data.local.MailDatabaseMigrationTest,com.david.mailapp.data.repository.EmailRepositoryActionContractsTest,com.david.mailapp.data.repository.PartialPageContractsTest,com.david.mailapp.data.repository.SafeRefreshContractsTest,com.david.mailapp.data.pdf.PdfCancellationContractsTest,com.david.mailapp.feature.emaildetail.EmailDetailCancellationTest,com.david.mailapp.feature.emaildetail.EmailDetailReadFailureEffectTest,com.david.mailapp.feature.trash.TrashContentActionTest,com.david.mailapp.ui.navigation.DestinationLifecycleTest,com.david.mailapp.ui.navigation.MainNavigationTest,com.david.mailapp.ui.navigation.RestorationTest,com.david.mailapp.ui.navigation.ScrollStatePresentationTest
```

**Resultados (desde XML):**

| Métrica | Valor |
|---------|-------|
| Tests | 82 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Duración | 134.277s (~5m 48s Gradle) |

**Fuente:** `app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml`

✅ Línea base confirmada: 82 pruebas.

## Matriz de contratos verificados

| Contrato | Cobertura JVM | Cobertura Instrumentada | Clase(s) |
|----------|:---:|:---:|------|
| DAO, merge y preservación de caché | ✅ | — | `EmailCacheMergeTest`, `EmailEntityPdfMetadataTest`, `EmailEntityRfcMappingTest`, `PdfAttachmentMetadataCodecTest` |
| Mapeo Gmail, threading RFC y MIME/PDF | ✅ | — | `GmailMappingAndThreadingTest`, `GmailPdfAttachmentParserTest`, `GmailPageHelperTest`, `EmailHtmlCleanerTest`, `PdfAttachmentFormattingTest` |
| Páginas parciales, retries y límite de concurrencia | ✅ | ✅ | `PartialPageContractsTest`, `GmailPageHelperTest` |
| Generaciones de refresh y paginación | ✅ | ✅ | `InboxRefreshCoordinationTest`, `InboxViewModelRefreshTokenTest`, `TrashRefreshCoordinationTest`, `TrashViewModelRefreshTokenTest`, `SafeRefreshContractsTest` |
| Cambio de consulta durante búsqueda | ✅ | — | `SearchCoordinationContractsTest`, `SearchViewModelTest` |
| Propagación de cancelación | ✅ | ✅ | `GmailProviderCancellationTest`, `EmailDetailCancellationTest`, `PdfCancellationContractsTest` |
| Acciones remoto-primero y feedback | ✅ | ✅ | `InboxContractsTest`, `InboxViewModelActionTest`, `ActionFeedbackTest`, `TrashContractsTest`, `TrashViewModelActionTest`, `TrashDeleteCoordinatorTest`, `EmailRepositoryActionContractsTest` |
| Lectura idempotente al abrir Detail | ✅ | ✅ | `EmailReadOnOpenGateTest`, `EmailReadOnOpenCoordinatorTest`, `EmailDetailReadFailureEffectTest`, `EmailDetailContractsTest` |
| Protección contra doble envío | ✅ | — | `ComposeSendContractsTest` |
| SavedState de Search y Compose | ✅ | ✅ | `ComposeRestorationTest`, `RestorationTest`, `SearchViewModelTest` |
| Contratos de rutas y navegación | ✅ | ✅ | `NavigationTest`, `MainNavigationTest`, `DestinationLifecycleTest`, `ScrollStatePresentationTest` |
| Migración Room 5→6 | ✅ | ✅ | `MailDatabaseMigrationTest` |
| Borrado permanente sin undo | ✅ | ✅ | `TrashContractsTest`, `TrashContentActionTest` |
| Recreación de Activity y restauración de proceso | — | ✅ | `RestorationTest` |
| Estado de scroll independiente | — | ✅ | `ScrollStatePresentationTest` |

## Diferencias respecto de las líneas base

- **JVM:** 520 / 520 (sin diferencia)
- **Instrumentadas:** 82 / 82 (sin diferencia)

## Fallos encontrados

Ninguno.

## Advertencias no bloqueantes

- Deprecation warnings de Kotlin (`@param:` annotation target, `LocalLifecycleOwner`, `createComposeRule`)
- Deprecation de Gradle (incompatibilidad futura con Gradle 10)
- Librerías nativas no strippeadas: `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`
- `android.builtInKotlin=false` y `android.newDsl=false` deprecados (AGP 10)

## Confirmaciones

- ✅ Pixel físico no fue utilizado — dispositivo reportado: `Medium_Phone_API_36.1(AVD) - 16`
- ✅ No se usaron credenciales Gmail real ni datos del usuario
- ✅ Schemas Room 5.json y 6.json sin cambios
- ✅ No existe 7.json
- ✅ Worktree limpio (solo `_sum_tests.sh` sin trackear, se elimina antes del commit)
- ✅ No se ejecutaron lint, build release ni suite Android completa
