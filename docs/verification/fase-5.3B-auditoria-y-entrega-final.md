# Fase 5.3B — Auditoría y entrega final: Resultados

**Fecha:** 2026-08-03
**Sistema:** macOS 25.5.0 (x86_64), iMac-de-David.local
**Java:** JDK 17 | **Gradle:** 9.6.1 | **AGP:** 9.0.0 | **Kotlin:** 2.1.20
**Android SDK:** compileSdk 36, minSdk 26, targetSdk 36

## SHA

- **Inicial:** `4e52f53` (Completar fase 5.3A de correcciones de auditoría)
- **Código auditado:** `4e52f53` (el commit 5.3B añade únicamente este informe)
- **Commit de entrega:** el commit que contiene este informe. Se identifica en el historial
  por el mensaje `Completar fase 5.3B de auditoría y entrega final`; no se incrusta su SHA
  porque modificar el informe cambiaría recursivamente ese mismo SHA.

---

## 1. Auditoría Room y migraciones

| Criterio | Resultado |
|----------|-----------|
| Base Room en versión 6 | ✅ |
| exportSchema = true | ✅ |
| Única migración productiva: MIGRATION_5_6 | ✅ |
| fallbackToDestructiveMigration | 0 apariciones ✅ |
| Solo existen 5.json y 6.json | ✅ |
| No existe 7.json | ✅ |
| No se admite origen v4 | ✅ |

### Hashes de esquemas

| Schema | SHA-256 | Coincide |
|--------|---------|----------|
| 5.json | `6e7de01f68929daaa71c71b92680f805e999b165a79a60cbf6f24e5862a2ac0f` | ✅ |
| 6.json | `e2e1644ff669cd110b6184d4ce406bef293b261848468b262aeefca7adb8c8e1` | ✅ |

Preservación confirmada: 17 columnas originales, cuerpo, cuerpo saneado, labels, metadata PDF, rfc_message_id (nullable), rfc_references (nullable).

---

## 2. Auditoría de cancelación y excepciones

### Inventario de capturas

Se auditaron las 136 capturas existentes en `app/src/main/java`. Las categorías siguientes
describen su propósito; se omiten cantidades por categoría porque algunos bloques cumplen
más de una función (por ejemplo, relanzan cancelación y convierten el resto a resultado
tipado), por lo que no forman una partición sumable del total.

| Clasificación | Ejemplos |
|---------------|----------|
| Relanzamiento de CancellationException | EmailRepository, SessionCoordinator, ViewModels |
| Conversión a resultado tipado | GmailProvider, OAuth helpers, GoogleOAuthTokenService |
| Error visible para UI | ViewModels (ActionFeedback, Snackbar) |
| Fallback síncrono de parseo | ImageUtils, EmailHtmlCleaner, PdfAttachmentMetadataCodec |
| Cleanup best-effort con diagnóstico | SessionCoordinator, EmailBodyWebView, EmailDetailScreen |
| NonCancellable (limpieza/commits) | SessionCoordinator |

### Criterios

| Criterio | Resultado |
|----------|-----------|
| `catch (Throwable)` | 0 ✅ |
| Capturas completamente vacías | 0 ✅ (4 corregidas en 5.3A) |
| Toda captura amplia alrededor de suspendida relanza CancellationException | ✅ EmailRepository, ViewModels, GmailProvider |
| Cancelaciones no se convierten en snackbar/toast/null | ✅ |
| Helpers de 5.3A conservan instancia cancelada | ✅ (OAuthLaunchPreflight, OAuthTokenExchange) |
| NonCancellable limitado a limpieza/commits | ✅ |
| Logs sin tokens, state, code verifier ni contenido de correo | ✅ |

---

## 3. Auditoría Gmail–Room y concurrencia

### Acciones remoto-primero

| Acción | Orden | Remote-first | ViewModel no escribe DAO | Fallo Gmail → remoteApplied=false |
|--------|-------|-------------|--------------------------|-----------------------------------|
| `moveToTrash` | lease → provider → Gmail → Room → reconcile | ✅ | ✅ | ✅ |
| `restoreFromTrash` | lease → provider → Gmail → Room → reconcile | ✅ | ✅ | ✅ |
| `deletePermanently` | lease → provider → Gmail → Room → reconcile | ✅ | ✅ | ✅ |
| `markAsRead` | lease → provider → Gmail → Room → reconcile | ✅ | ✅ | ✅ |

- Solo EmailRepository llama a las 4 operaciones remotas ✅
- Borrado permanente exige confirmación, no ofrece undo ✅ (TrashContentActionTest)
- Abrir correo no leído: único propietario (EmailReadOnOpenCoordinator), una tentativa por instancia ✅

### Refresh, paginación y búsqueda

| Criterio | Resultado |
|----------|-----------|
| Inbox/Trash: refreshJob + paginationJob + generación | ✅ |
| Refresh nuevo cancela anterior + paginación | ✅ |
| Resultado obsoleto no publica estado ni escribe Room | ✅ |
| Primera página completa puede reemplazar ventana | ✅ |
| Página parcial solo merge, no avanza token | ✅ |
| Search cancela paginación al cambiar consulta | ✅ |
| Search comprueba consulta y generación antes de anexar | ✅ |
| Resultados deduplicados por ID | ✅ |

### Compose y Detail

| Criterio | Resultado |
|----------|-----------|
| sendJob impide doble envío | ✅ |
| Snapshot de campos antes de coroutine | ✅ |
| Salida de Compose cancela envío no comprometido | ✅ |
| Body/imágenes/PDF en ViewModel/Detail | ✅ |

---

## 4. Auditoría de jobs y navegación

### Propiedad de coroutines

| Scope | Propietario | Uso |
|-------|------------|-----|
| `viewModelScope` | InboxVM, TrashVM, SearchVM, ComposeVM, DetailVM | Operaciones de feature |
| `lifecycleScope` | MainActivity | OAuth, handleOAuthRedirect |
| `rememberCoroutineScope` | Composables | Interacción UI (drawer, snackbar) |
| `applicationScope` | AppContainer | SupervisorJob, colector global de reautenticación |

| Criterio | Resultado |
|----------|-----------|
| `GlobalScope` | 0 ✅ |
| Jobs sin propietario | 0 ✅ |

### Rutas y SavedState

| Ruta | Contenido | Verificación |
|------|-----------|-------------|
| `MainRoute.EmailDetail` | Solo `emailId: String` | ✅ Sin Email, cuerpo, adjuntos ni tokens |
| `MainRoute.Compose` | Solo `mode` y `originalEmailId?` | ✅ Sin objetos Email |
| Settings | Rutas serializables sin payload | ✅ |
| Search | Solo consulta en SavedState | ✅ |
| Compose | Argumentos mínimos, borrador, Cc/Cco en SavedState | ✅ |

- Sin Navigator manual ✅
- Scroll independiente para Inbox, Trash, Search ✅
- NavigationTest, Detail + Compose + Settings + Search sobreviven recreación ✅

---

## 5. Gate integral definitivo

**AVD:** Medium_Phone_API_36.1, emulator-5554

### Suite JVM

| Métrica | Valor |
|---------|-------|
| Tests | 530 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |

### Suite instrumentada

| Métrica | Valor |
|---------|-------|
| Tests | 94 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Dispositivo | Medium_Phone_API_36.1(AVD) - 16 |

### Lint

| Variante | Errores | MissingKeepAnnotation | CredentialDependency |
|----------|---------|-----------------------|---------------------|
| Debug | 0 | 0 | 0 |
| Release | 0 | 0 | 0 |

### Builds

| Tarea | Resultado |
|-------|-----------|
| `assembleDebug` | ✅ |
| `assembleRelease` | ✅ (R8 + optimizeReleaseResources) |
| `assembleDebugAndroidTest` | ✅ |

---

## 6. Artefactos finales

| APK | Ruta | SHA-256 |
|-----|------|---------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | `88533d273fd2c23edf607eeca97c85a3a0710bb810c7b513d575079dec70af53` |
| Release | `app/build/outputs/apk/release/app-release.apk` | `254eebd9d4110a866293e5a0ab2fd30291625841374ef64c9ae014b7faa92708` |
| AndroidTest | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `b4eb6f591abad8adfbdf5488ab37a6dc3d08f7fd996997acba8f2ae12ec666c8` |

### Verificación por APK

| APK | apksigner | zipalign | Firmante | Application ID |
|-----|-----------|----------|----------|---------------|
| Debug | ✅ Verifies (v2) | ✅ | CN=Android Debug | com.david.mailapp |
| Release | ✅ Verifies (v2) | ✅ | CN=Android Debug | com.david.mailapp |
| AndroidTest | ✅ Verifies (v2) | ✅ | CN=Android Debug | N/A |

---

## 7. Matriz de criterios de aceptación del plan maestro

| Criterio | Fase | Estado |
|----------|------|--------|
| Room no cambia hasta confirmar Gmail | F2 | ✅ Remote-first |
| CancellationException no se convierte en error | F3 | ✅ Propagación completa |
| Solo operación vigente publica estado | F3 | ✅ Generaciones + cancelación |
| Acciones: Gmail antes de Room, fallo remoto no modifica local | F2 | ✅ |
| Fallo Room post-Gmail reconcilia y muestra error | F2 | ✅ |
| Refresh antiguo no reemplaza datos | F2 | ✅ |
| Página parcial no avanza token | F2 | ✅ |
| Cambio de búsqueda impide anexo de consulta anterior | F3 | ✅ |
| Back stack sobrevive recreación y muerte de proceso | F4 | ✅ RestorationTest |
| Migración 5→6 conserva correos, cuerpos, PDF, labels | F1 | ✅ MailDatabaseMigrationTest |
| Doble toque en enviar → una sola petición | F3 | ✅ sendJob |
| Borrado permanente sin undo | F2 | ✅ |
| Abrir no leído → máx. 1 acción remota | F2.6 | ✅ EmailReadOnOpenCoordinator |
| Lint sin MissingKeepAnnotation ni CredentialDependency | F5.2 | ✅ |
| 0 capturas vacías | F5.3A | ✅ |
| Schemas 5 y 6 sin cambios, sin 7.json | F5 | ✅ |

---

## 8. Historial de commits de la Fase 5

| Commit | Descripción |
|--------|-------------|
| `e61ff43` | Completar fase 5.1 de pruebas unitarias e integración |
| `f587326` | Completar fase 5.2 de validación Android |
| `4e52f53` | Completar fase 5.3A de correcciones de auditoría |
| Este commit | Completar fase 5.3B de auditoría y entrega final |

---

## 9. Limitaciones

1. **Sin prueba end-to-end con cuenta Gmail real** — las pruebas usan fakes/mocks de red.
2. **Release firmado con certificado debug** (`CN=Android Debug`) — no es publicable.
3. **Sin validación de Play Store** ni firma de producción.
4. **Pruebas Android limitadas al AVD API 36** (`Medium_Phone_API_36.1`).
5. **Sin matriz completa desde minSdk 26** — solo validado en API 36.
6. **Muerte de proceso validada mediante reconstrucción determinista equivalente** (RestorationTest recreation → process death).
7. **Única migración soportada: 5→6** — instalaciones anteriores a v5 deben limpiar datos.
8. **Refresh basado en ventana paginada**, no Gmail History API.
9. **Actualizaciones de SDK y dependencias fuera del alcance** de esta fase.

---

## 10. Confirmaciones finales

- ✅ 5.json y 6.json intactos, sin 7.json
- ✅ Gate integral completamente verde (JVM 530 + instrumentada 94)
- ✅ Lint debug/release: 0 errores, 0 MissingKeepAnnotation, 0 CredentialDependency
- ✅ R8 y reducción de recursos ejecutados en release
- ✅ APK debug, release y androidTest verificados (apksigner + zipalign)
- ✅ Application ID: `com.david.mailapp`
- ✅ No se usaron credenciales Gmail real ni datos del usuario
- ✅ Pixel físico no fue utilizado
- ✅ Worktree limpio después del commit de entrega
