# Resultados — Subfase 2.1, contratos de lectura desde Room

## Identificación

- Etapa: 2 — Lecturas, sincronización, búsqueda y concurrencia.
- Subfase: 2.1 — Contratos de lectura desde Room.
- Ejecución final: 2026-08-08, zona CST (`-0600`).
- Emulador: `Medium_Phone_API_36.1`, Android 16/API 36.
- Archivo añadido: `app/src/androidTest/java/com/david/mailapp/data/repository/EmailRepositoryReadSyncSearchContractsTest.kt`.

## Contratos añadidos

| Caso | Contrato protegido |
|---|---|
| `getInbox_emits_initial_empty_and_live_updates_ordered_newest_first` | Emisión inicial, inserción, orden descendente por timestamp y actualización viva de lectura. |
| `getTrash_emits_initial_empty_and_live_insert_delete_updates` | Emisión inicial, inserción ordenada y eliminación viva en Trash. |
| `folder_flows_isolate_inbox_trash_and_other` | Inbox y Trash excluyen entre sí y excluyen `Other`. |
| `getEmailById_emits_absent_insert_update_and_delete_sequence` | Secuencia observable null → insertado → actualizado → null. |
| `read_apis_map_complete_rich_entity_to_domain_model` | Mapeo completo de identidad, flags, labels, body, PDF y cabeceras RFC. |

Todos los casos verifican que las APIs de lectura no resuelven ni invocan el proveedor.

## Compilación

Comando:

```text
./gradlew compileDebugAndroidTestKotlin
```

Resultado:

```text
BUILD SUCCESSFUL in 48s
30 actionable tasks: 2 executed, 28 up-to-date
```

## Ejecución instrumentada

La primera ejecución compiló e instaló correctamente la suite, pero tres casos con collectors continuos fallaron por usar `withTimeout` sobre el reloj virtual de `runTest` mientras Room emitía desde un dispatcher real. El runner indicó explícitamente que el timeout debía envolverse en un dispatcher real.

La corrección se limitó al helper de pruebas: `awaitValue` ejecuta su timeout en `Dispatchers.Default.limitedParallelism(1)`. No cambió ninguna expectativa ni archivo de producción.

Repetición final:

```text
Starting 5 tests on Medium_Phone_API_36.1(AVD) - 16
Finished 5 tests on Medium_Phone_API_36.1(AVD) - 16
tests=5, failures=0, errors=0, skipped=0
BUILD SUCCESSFUL in 2m 28s
```

El XML final reportó 2.488 s agregados de ejecución de casos. El resto corresponde a compilación, empaquetado, instalación y preparación del emulador.

Reporte:

```text
app/build/reports/androidTests/connected/debug/index.html
app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml
```

## Integridad y cierre

- `EmailRepository.kt` conserva SHA-256 `abcac202041966e53383b40613c5c3d027a93bbb0d8c2616fe16a90a424dbe4b`.
- `MainNavHost.kt` conserva SHA-256 `a6840cfc931e19185fbc29db02e11cc31152b9d719cd1b31fff564060feea088`.
- Producción, fakes, Gradle y pruebas existentes permanecen sin cambios.
- Solo se añadió la suite nueva y documentación de esta subfase.
- Los cinco contratos de lectura están verdes: cumplido.
- La subfase 2.1 queda cerrada; la subfase 2.2 permanece pendiente.
