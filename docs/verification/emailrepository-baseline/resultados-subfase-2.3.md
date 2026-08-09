# Resultados — Subfase 2.3, búsqueda remota

## Identificación

- Etapa: 2 — Lecturas, sincronización, búsqueda y concurrencia.
- Subfase: 2.3 — Búsqueda remota.
- Ejecución final: 2026-08-08, zona CST (`-0600`).
- Emulador: `Medium_Phone_API_36.1`, Android 16/API 36.
- Suite ampliada: `EmailRepositoryReadSyncSearchContractsTest`.

## Contratos nuevos

Se añadieron cinco casos directos sobre `searchEmails`:

| Caso | Contrato protegido |
|---|---|
| `search_without_provider_returns_empty_without_room_changes` | Sin provider devuelve una página vacía canónica y conserva Inbox, Trash y Other. |
| `search_delegates_query_and_token_and_returns_remote_result_without_room_changes` | Delega exactamente query/token, conserva items, token, completitud y fallos, y no persiste el resultado. |
| `search_resolves_current_provider_for_each_call` | Cada llamada obtiene el provider vigente; no queda capturada una cuenta anterior. |
| `search_provider_errors_propagate_same_instance_and_leave_room_unchanged` | Una excepción remota ordinaria conserva identidad y no modifica Room. |
| `search_cancellation_propagates_same_instance_and_leave_room_unchanged` | `CancellationException` conserva identidad y no modifica Room. |

El fake instrumentado registra el número de búsquedas y cada par `query`/`pageToken`; además aplica los mecanismos de resultado, error y suspensión que ya exponía exclusivamente para pruebas.

## Compilación

```text
./gradlew compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 7s
30 actionable tasks: 2 executed, 28 up-to-date
```

## Ejecución instrumentada

```text
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
com.david.mailapp.data.repository.EmailRepositoryReadSyncSearchContractsTest

Starting 17 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 17 tests on Medium_Phone_API_36.1(AVD) - 16
tests=17, failures=0, errors=0, skipped=0
BUILD SUCCESSFUL in 4m 32s
```

El XML reportó 3.625 s agregados para los 17 casos. Una ejecución anterior no generó un informe actualizado y fue descartada; no se utilizó como evidencia.

## Integridad y cierre

- `EmailRepository.kt` conserva SHA-256 `abcac202041966e53383b40613c5c3d027a93bbb0d8c2616fe16a90a424dbe4b`.
- `MainNavHost.kt` conserva SHA-256 `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.
- No se modificaron producción, Gradle ni otras suites; solo se amplió una utilidad del source set instrumentado para hacer observables los contratos.
- Delegación, resultado vacío, no persistencia, provider dinámico, errores y cancelación quedan protegidos: cumplido.
- La subfase 2.3 queda cerrada; la subfase 2.4 permanece pendiente.
