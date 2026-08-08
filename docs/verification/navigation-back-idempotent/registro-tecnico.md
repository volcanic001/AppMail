# Registro técnico — Subfase 1: caracterización y baseline de Back

> **Base histórica:** `621df4d`
> **HEAD efectivo:** `3a09abe`
> **Fecha:** 2026-08-07
> **Prueba:** `app/src/androidTest/java/com/david/mailapp/ui/navigation/BackIdempotencyCharacterizationTest.kt`

## Estado

**Cerrada.** La caracterización quedó determinista en dos ejecuciones finales
consecutivas por dispositivo. No se modificó producción ni se creó commit.

## Método

- `MainScreen` se renderiza con `TestNavHostController`, navegadores Compose y
  Dialog, y un `TestBackPressedDispatcherOwner` propio en estado `RESUMED`.
- El owner registra cualquier escape al fallback; las cuatro corridas válidas
  terminaron con `fallbackCount == 0` en todos los casos que alcanzaron esa
  comprobación.
- Las acciones visuales se obtienen una sola vez mediante
  `SemanticsActions.OnClick` y esa misma acción se invoca dos veces dentro del
  mismo bloque UI, antes de recomponer.
- El panel expandido se prueba directamente con `EmailDetailPresentation` y
  `EmailDetailUiState.Ready(testEmail(...))`, sin correo ni datos reales.
- No se usan `sleep`, coordenadas, debounce, reintentos internos, `@Ignore` ni
  una segunda búsqueda del nodo originario.

## Resultados finales

| Suite | Emulador `Medium_Phone_API_36.1` | Pixel 9 |
|---|---:|---:|
| `MainNavigationTest` | 9/9 verdes | 9/9 verdes |
| `ScrollStatePresentationTest` | 7/7 verdes | No requerida por el plan |
| Caracterización, corrida final 1 | 53: 25 verdes, 28 expected-red | 53: 25 verdes, 28 expected-red |
| Caracterización, corrida final 2 | 53: 25 verdes, 28 expected-red | 53: 25 verdes, 28 expected-red |

Las cuatro corridas finales produjeron los mismos nombres y tipos de fallo.
Una corrida preliminar del Pixel se descartó porque el dispositivo estaba en
`Dozing` con lockscreen activo; produjo fallos de infraestructura antes de las
aserciones. Tras despertar el dispositivo y retirar el keyguard, las dos
corridas válidas coincidieron exactamente con el emulador. El ajuste temporal
`svc power stayon true` se restauró a `false` al terminar.

## Matriz de caracterización

| Entrada originaria | Solicitud repetida | Resultado actual | Clasificación |
|---|---|---|---|
| EmailDetail desde Inbox | `popBackStack()` directo | consume Inbox | expected-red |
| EmailDetail desde Trash | directo | consume Trash y el destino anterior | expected-red |
| EmailDetail desde Search | directo | consume Detail y Search | expected-red |
| EmailDetail desde Inbox | flecha capturada | consume Detail e Inbox | expected-red |
| EmailDetail desde Trash/Search | flecha capturada | consume dos destinos | expected-red |
| EmailDetail desde Inbox | System Back | queda en Inbox, sin fallback | verde actual |
| EmailDetail desde Trash/Search | System Back | consume el origen siguiente | expected-red |
| Search desde Inbox | directo o flecha capturada | consume Search e Inbox | expected-red |
| Search desde Inbox | System Back | queda en Inbox, sin fallback | verde actual |
| Compose Write | directo o cierre visual capturado | consume Compose e Inbox | expected-red |
| Compose Write | System Back | queda en Inbox, sin fallback | verde actual |
| Compose Reply/Forward | directo, visual o System Back | consume Compose y Detail | expected-red |
| Settings Hub | directo o flecha capturada | consume Settings e Inbox | expected-red |
| Settings Hub | System Back | queda en Inbox, sin fallback | verde actual |
| Appearance, Account, Notifications, Privacy, Security, About | flecha capturada | consume la hoja y el Hub interno | expected-red |
| Las mismas seis hojas | System Back | queda en Hub, sin fallback | verde actual |
| Changelog | flecha capturada o System Back | avanza más allá de About | expected-red |
| Drawer abierto | System Back repetido | cierra drawer y conserva Inbox | verde actual |
| Panel expandido | System Back repetido | cierra panel y no alcanza el handler exterior | verde actual |

## Expected-red confirmados — 28

### `popBackStack()` directo — 8

- `expectedRed_programmatic_doublePop_fromDetail_toInbox`
- `expectedRed_programmatic_doublePop_fromDetail_toTrash`
- `expectedRed_programmatic_doublePop_fromDetail_toSearch`
- `expectedRed_programmatic_doublePop_fromSearch`
- `expectedRed_programmatic_doublePop_fromComposeWrite`
- `expectedRed_programmatic_doublePop_fromComposeReply`
- `expectedRed_programmatic_doublePop_fromComposeForward`
- `expectedRed_programmatic_doublePop_fromSettings`

### Back del sistema — 5

- `expectedRed_systemBack_doubleFromDetail_toTrash`
- `expectedRed_systemBack_doubleFromDetail_toSearch`
- `expectedRed_systemBack_doubleFromComposeReply`
- `expectedRed_systemBack_doubleFromComposeForward`
- `expectedRed_systemBack_doubleFromSettingsChangelog`

### Acciones visuales capturadas — 15

- EmailDetail: `expectedRed_arrowBack_doubleFromDetail_toInbox`,
  `...toTrash`, `...toSearch`.
- Search: `expectedRed_arrowBack_doubleFromSearch`.
- Compose: `expectedRed_visualClose_doubleFromComposeWrite`, `...Reply`,
  `...Forward`.
- Settings: `expectedRed_visualBack_doubleFromSettingsHub`, `...Appearance`,
  `...Account`, `...Notifications`, `...Privacy`, `...Security`, `...About`,
  `...Changelog`.

Todos fallan en una aserción de destino o contenido final. No hay fallos por
nodo desaparecido, Activity cerrada, Espresso ni ausencia de jerarquía en las
corridas aceptadas.

## Casos verdes — 25

- 12 contratos ya seguros: System Back desde Detail→Inbox, Search, Compose
  Write, Settings Hub, las seis hojas simples de Settings y drawer; además,
  repetición del handler del panel expandido.
- 13 baselines: Back simple de Detail, Search, Compose y Settings; acciones
  visuales simples; highlight de Detail; drawer; segundo Back legítimo después
  de recomponer en Settings y en el panel.
- La suite existente de scroll confirma posiciones independientes, retorno
  desde Detail/Search y restauración del NavHost interno de Settings.

## Mecanismo confirmado

La evidencia programática demuestra que dos `popBackStack()` crudos consumen
dos entradas siempre que ambas existan. La evidencia visual confirma que una
acción capturada de la entrada original conserva su callback y vuelve a
invocar el pop después de retirar esa entrada. System Back presenta el mismo
fallo cuando queda otro destino consumible; en rutas raíz la infraestructura
actual puede resultar no-op y por eso esos casos ya son verdes.

El defecto no es una ventana temporal global: un segundo Back enviado después
de recomponer desde la pantalla nueva sigue siendo válido. Drawer y panel
conservan prioridad y no requieren el contrato de navegación.

## Comandos ejecutados

```bash
./gradlew assembleDebugAndroidTest --rerun-tasks

ANDROID_SERIAL=<dispositivo> ./gradlew connectedDebugAndroidTest --rerun-tasks \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.ui.navigation.BackIdempotencyCharacterizationTest

ANDROID_SERIAL=<dispositivo> ./gradlew connectedDebugAndroidTest --rerun-tasks \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.ui.navigation.MainNavigationTest

ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest --rerun-tasks \
  -Pandroid.testInstrumentationRunnerArguments.class=com.david.mailapp.ui.navigation.ScrollStatePresentationTest
```

Dispositivos efectivos:

- `emulator-5554` — `Medium_Phone_API_36.1(AVD) - 16`.
- `adb-55080DLAQ002CK-0Wyjbr._adb-tls-connect._tcp` — `Pixel 9 - 17`.

## Límites y cierre

- Los ocho tests programáticos prueban el efecto de llamadas crudas, no por sí
  solos la identidad de una solicitud obsoleta; esa identidad queda cubierta
  por las acciones visuales capturadas.
- No se modificaron `MainActivity.kt`, `SearchScreen.kt` ni ningún archivo de
  producción durante esta subfase.
- No se conservaron capturas ni contenido personal.
- La solución de producción se pospone a la Subfase 2.

**Última actualización:** 2026-08-07.

---

# Registro técnico — Subfase 2: contrato y adopción de EmailDetail

> **HEAD efectivo:** `3a09abe`
> **Fecha:** 2026-08-08
> **Estado:** Cerrada

## Implementación

- `canPopBackFrom()` autoriza únicamente la entrada originaria que continúa
  actual, está exactamente en `RESUMED` y tiene un destino anterior.
- `popBackStackFrom(originatingEntry)` convierte solicitudes obsoletas en
  no-op y devuelve si retiró la entrada autorizada.
- `closeEmailDetail(originatingEntry, emailId)` publica el highlight sólo
  después de un pop válido hacia Inbox, Trash o Search.
- La flecha de EmailDetail conserva un callback ligado a su
  `NavBackStackEntry`.
- El Back del sistema de EmailDetail se registra en el nivel estable de
  `MainNavHost`, después de `NavHost`. Así tiene prioridad sobre el pop
  genérico y continúa presente durante dos despachos consecutivos del mismo
  bloque UI; el segundo despacho reutiliza la entrada ya retirada y es no-op.

## Hallazgo durante la corrección

La primera adopción dejó el `BackHandler` dentro del contenido de
EmailDetail. La primitiva funcionaba para llamadas programáticas y flechas,
pero el primer Back retiraba también esa composición y su handler. El segundo
despacho se entregaba entonces al callback inferior: Detail→Trash y
Detail→Search terminaban en Inbox. Esto produjo 17 fallos: los 15 rojos
previstos y dos verdes adicionales.

Mover el handler protegido antes de `NavHost` tampoco era suficiente, porque
el callback genérico del host se registraba después y conservaba prioridad.
El orden final —`NavHost` y después el handler estable de Detail— corrigió los
dos casos sin debounce, reloj, flags globales ni cambios en Search, Compose o
Settings. El caso de panel expandido siguió verde y confirmó que su handler
interno conserva prioridad.

## Pruebas y resultados

| Suite | Emulador `Medium_Phone_API_36.1` | Pixel 9 |
|---|---:|---:|
| `BackPopPolicyTest` (JVM, 11) | 11/11 | N/A |
| `BackIdempotencyContractTest` | 8/8 | 8/8 |
| `MainNavigationTest` | 9/9 | 9/9 |
| `ScrollStatePresentationTest` | 7/7 | 7/7 |
| Caracterización, corrida final 1 | 53: 38 verdes, 15 expected-red | 53: 38 verdes, 15 expected-red |
| Caracterización, corrida final 2 | 53: 38 verdes, 15 expected-red | 53: 38 verdes, 15 expected-red |

También quedaron verdes `testDebugUnitTest`, `assembleDebugAndroidTest` y
`git diff --check`. Las corridas de caracterización hacen fallar la tarea de
Gradle de forma intencional porque los 15 consumidores aún no adoptados siguen
siendo expected-red; los XML registraron 53 ejecutados, 15 fallos, 0 errores y
0 omitidos.

Un intento inicial del emulador ejecutó 0 tests por
`INSTALL_FAILED_INSUFFICIENT_STORAGE`. El AVD no contenía apps de terceros;
se reinicializó, pasó de 412 MB a 4.9 GB libres y las corridas válidas se
realizaron sólo después de completar el arranque en frío. Las escalas de
animación usadas temporalmente para instrumentación se restauraron a `1` y el
AVD se apagó al finalizar.

## Límites confirmados

- No se modificaron `MainActivity.kt`, `SearchScreen.kt`, rutas, transiciones,
  ViewModels, repositorios, recursos ni APIs públicas.
- Los 15 expected-red restantes pertenecen a Search, Compose y Settings y se
  mantienen para la Subfase 3.
- Staging vacío y sin commit intermedio.

**Última actualización:** 2026-08-08.

---

# Registro técnico — Subfase 3.1: adopción en el host principal

> **HEAD efectivo:** `3a09abe`
> **Fecha:** 2026-08-08
> **Estado:** Cerrada

## Implementación

- `MainNavHost.kt` usa callbacks ligados al `NavBackStackEntry` originario en
  Search, Compose y Settings Hub mediante `popBackStackFrom(entry)`.
- El handler estable exterior incorpora Compose junto con EmailDetail. Dos
  despachos consecutivos reutilizan la misma entrada: el primero retira el
  destino y el segundo es no-op.
- Search conserva su manejo de teclado y foco; Settings conserva la prioridad
  de su `BackHandler` interno; drawer y panel expandido mantienen prioridad.
- No se modificaron pantallas, rutas, transiciones, ViewModels, recursos,
  `NavExtensions.kt` ni el `NavHost` interno de Settings.

## Ajustes de prueba

- Se reclasificaron como `green_*` los siete casos resueltos: Back visual de
  Search, Compose Write/Reply/Forward y Settings Hub, y Back del sistema de
  Compose Reply/Forward.
- El baseline de Compose Reply ejecuta ahora dos Back legítimos separados por
  recomposición: Compose Reply → EmailDetail → Inbox.
- La matriz conserva 53 casos: 45 verdes y ocho expected-red.

## Evidencia final

| Suite | Emulador `Medium_Phone_API_36.1` | Pixel 9 |
|---|---:|---:|
| Caracterización, corrida final 1 | 53: 45 verdes, 8 expected-red | 53: 45 verdes, 8 expected-red |
| Caracterización, corrida final 2 | 53: 45 verdes, 8 expected-red | 53: 45 verdes, 8 expected-red |
| `BackIdempotencyContractTest` | 8/8 | 8/8 |
| `MainNavigationTest` | 9/9 | 9/9 |
| `ScrollStatePresentationTest` | 7/7 | 7/7 |

También quedaron verdes `testDebugUnitTest` y
`assembleDebugAndroidTest`. Las corridas de caracterización fallan la tarea de
Gradle intencionalmente por los ocho expected-red, pero registran 53
ejecutados, ocho fallos, cero errores y cero omitidos.

Una corrida auxiliar inicial del Pixel produjo cuatro
`RootViewWithoutFocusException` y se descartó como fallo de infraestructura.
Tras mantener despierto el dispositivo, retirar el keyguard y confirmar
Launcher reanudado, la repetición válida terminó 24/24. `svc power stayon` se
restauró a `false` al concluir.

## Alcance restante

Los únicos expected-red son Back visual desde Appearance, Account,
Notifications, Privacy, Security, About y Changelog, y Back del sistema desde
Changelog. Todos pertenecen al `NavHost` interno de Settings y pasan a la
Subfase 3.2.

`git diff --check` quedó limpio, staging vacío y no se creó commit intermedio.

**Última actualización:** 2026-08-08.

---

# Registro técnico — Subfase 3.2: NavHost interno de Settings

> **HEAD efectivo:** `3a09abe`
> **Fecha:** 2026-08-08
> **Estado:** Cerrada

## Implementación

- Las siete hojas internas capturan su entrada originaria y usan
  `popBackStackFrom(backStackEntry)` para la flecha visual.
- Un callback estable observado mediante `currentBackStackEntryAsState()`
  protege el Back del sistema de las hojas.
- El `BackHandler` se registra después del `NavHost` interno y queda
  deshabilitado en Hub, preservando allí la delegación exterior hacia Inbox.
- No se modificaron el contrato, el host principal, pantallas, rutas,
  transiciones, ViewModels ni APIs públicas.

## Hallazgo y corrección

La implementación inicial usó `destination !is SettingsRoute.Hub`. Como
`destination` es un `NavDestination`, el handler de hojas también quedaba
habilitado en Hub. Esto interceptaba el System Back y produjo dos fallos:
`baseline_singleSystemBack_fromSettingsHub_toInbox` y
`alreadyGreen_systemBack_doubleFromSettingsHub`.

La detección se corrigió con
`!destination.hasRoute<SettingsRoute.Hub>()`. Los dos casos focales
terminaron 2/2 en Pixel y no fue necesario cambiar lifecycle ni relajar
`canPopBackFrom()`.

## Evidencia final

| Suite | Emulador `Medium_Phone_API_36.1` | Pixel 9 |
|---|---:|---:|
| Caracterización, corrida final 1 | 53/53 | 53/53 |
| Caracterización, corrida final 2 | 53/53 | 53/53 |
| `BackIdempotencyContractTest` | 8/8 | 8/8 |
| `MainNavigationTest` | 9/9 | 9/9 |
| `ScrollStatePresentationTest` | 7/7 | 7/7 |

`testDebugUnitTest` y `assembleDebugAndroidTest` también quedaron verdes.
Las cuatro corridas de caracterización finalizaron con cero fallos, errores u
omitidos. Ya no quedan expected-red en la matriz.

Staging vacío, sin commit intermedio y `git diff --check` limpio.

**Última actualización:** 2026-08-08.

---

# Registro técnico — Subfase 4: validación final, smoke y commit

> **HEAD de entrada:** `3a09abe`
> **Fecha:** 2026-08-08
> **Estado:** Cerrada

## Preflight y candidato

- Rama `main`, staging vacío y nueve archivos previstos: tres de producción,
  cinco de pruebas y este registro.
- Cero métodos `expectedRed_*`.
- Los ocho archivos de código y pruebas conservaron exactamente sus hashes
  durante todos los gates:
  - `NavExtensions.kt`: `ee08f726…1820b`.
  - `MainNavHost.kt`: `3c55becb…d6e7`.
  - `SettingsNavHost.kt`: `d4919bba…46d`.
  - `BackPopPolicyTest.kt`: `f1a0aab9…3dcc`.
  - `BackIdempotencyContractTest.kt`: `8b1fd1d7…97c6`.
  - `BackIdempotencyCharacterizationTest.kt`: `9c1ea614…b2b3`.
  - `MainNavigationTest.kt`: `cfe0e410…588d`.
  - `ScrollStatePresentationTest.kt`: `c583dd00…fa83`.
- El registro técnico tenía hash de entrada `ed3e4537…beb0` y evolucionó
  únicamente para documentar este cierre.

## Gates finales

| Gate | Resultado |
|---|---|
| `testDebugUnitTest --rerun-tasks` | 584/584, 0 fallos, errores u omitidos |
| `assembleDebug assembleDebugAndroidTest --rerun-tasks` | BUILD SUCCESSFUL |
| `lintDebug --rerun-tasks` | 0 errores/fatales, 64 warnings, delta 0 |
| `connectedDebugAndroidTest --rerun-tasks` | 207/207, 0 fallos, errores u omitidos |

La instrumentación completa se ejecutó exclusivamente en
`Medium_Phone_API_36.1`, Android 16/API 36.1, serial `emulator-5554`, con
arranque frío y animaciones en escala 1. La corrida duró 13m55s. El XML
agregado confirmó:

- `BackIdempotencyCharacterizationTest`: 53/53.
- `BackIdempotencyContractTest`: 8/8.
- `MainNavigationTest`: 9/9.
- `ScrollStatePresentationTest`: 7/7.

El AVD se restauró a sus ajustes previos y se apagó. Pixel y Huawei quedaron
excluidos de Gradle.

Artefactos candidatos:

- `app-debug.apk`: 25.642.958 bytes; SHA-256
  `ea59c490c7eabb8bebdcefc7a53897885eecf4f24c334c6925680f2352cd1758`.
- `app-debug-androidTest.apk`: 1.375.275 bytes; SHA-256
  `ec24e3e7460c4dc66aa3f9c6c6b630c077eaa9f90351a44c845f3779704b32b9`.

El APK release hallado en outputs era un artefacto histórico y no forma parte
del candidato ni de la evidencia.

## Smoke físico — Pixel 9

El APK debug candidato se instaló directamente con ADB, sin instrumentación
Gradle. El usuario restauró privadamente la cuenta temporal
`appmail.testing0@gmail.com`; no se compartieron credenciales. El smoke usó
sólo fixtures neutrales.

Resultados:

- EmailDetail desde Inbox: doble flecha regresó a Bandeja, con FAB y lista
  conservados.
- Compose Write regresó a Bandeja; Reply y Forward regresaron únicamente a
  EmailDetail. Un Back posterior, tras recomposición, regresó al origen.
- Search conservó consulta y resultados después de doble flecha desde Detail.
- Detail desde Trash no produjo pantalla vacía ni crash. Dos eventos ADB
  secuenciales alcanzaron Trash y después Inbox; el segundo ocurrió tras la
  recomposición y se clasificó como Back legítimo.
- Appearance, Account, Notifications, Privacy, Security y About regresaron a
  Hub con doble flecha.
- Changelog quedó en About con doble flecha y también con doble System Back.
- About→Hub funcionó con un Back legítimo posterior. Desde Hub, dos eventos
  ADB secuenciales alcanzaron Inbox y luego Launcher porque el segundo llegó
  después de recomponer; la prueba determinista de mismo frame permaneció
  53/53 y confirma que una solicitud originaria repetida es no-op.
- Back cerró el drawer conservando Inbox.
- Back cerró el panel expandido conservando EmailDetail.
- Segundo plano y reanudación conservaron EmailDetail; Back regresó a Inbox.

Estado final:

- `MainActivity` reanudada en Bandeja, FAB visible.
- Logcat temporal sin errores `AndroidRuntime`.
- Pixel con MailApp instalada y sesión temporal activa; ajuste `stayon`
  restaurado a 0.
- No se versionaron capturas, UI dumps ni logs. El XML temporal del dispositivo
  se eliminó.
- Huawei permaneció intacto.

## Commit de cierre

- Asunto autorizado: `fix(navigation): make back actions idempotent`.
- El hash se verifica externamente como el HEAD que contiene esta sección; no
  se incrusta dentro del propio commit para evitar una referencia circular.
- No se realiza amend, push, tag ni release.

**Resultado final:** Subfase 4 y plan correctivo Back idempotente aprobados y
cerrados el 2026-08-08.

**Última actualización:** 2026-08-08.
