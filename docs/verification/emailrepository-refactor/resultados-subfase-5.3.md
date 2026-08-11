# Resultados — Subfase 5.3, Verificación real en Pixel 9

## Identificación
- Plan maestro: Refactor estructural de `EmailRepository` — Etapa 5, Subfase 5.3.
- Fecha: 2026-08-11, CST (`-0600`).
- Dispositivo: Google Pixel 9 (tokay), Android 17/API 37, serial 55080DLAQ002CK (ADB inalámbrico TLS).
- APK: `app-debug.apk` (5.1/5.2) — 25,642,958 bytes, SHA-256 `e1531ab9…6e01`; hash instalado coincidente.
- Cuenta dedicada: `CUENTA_DEDICADA`. Sin cuentas personales.
- HEAD: `8530fc6b…` (sin cambios de código durante la subfase).

## Preparación
- Instalación con `adb install -r`; hash instalado == hash local.
- Sesión: login → identidad confirmada; logout y reautenticación realizados (limpieza de Room/caché PDF).
- Fixtures creados (2, autocreados, neutrales, auto-dirigidos):
  - `MAILAPP_REFACTOR_53_20260811_PRIMARY` (11:22).
  - `MAILAPP_REFACTOR_53_20260811_DELETE_ONLY` (11:16).
- Demora de indexación de Gmail en búsquedas parciales: documentada como externa; búsquedas con nombre completo funcionan.

## Matriz manual

### 1. Cuenta y navegación — CONFORME
- Login, identidad truncada, logout y reautenticación: OK.
- Refresh de Inbox y Trash: OK (fixtures y Papelera visibles).
- Paginación: fila posterior a las primeras 20 observada (`MAILAPP_REPO_BL53_20260810_PAGE_01`).
- Búsqueda remota por nombre completo: OK (fixtures, reply y forward localizados).

### 2. Resolución y contenido — CONFORME (anomalías heredadas)
- Fixture técnico histórico abierto: `MAILAPP_BASELINE_S_3_20260807` (HTML + imagen inline) y `MAILAPP_BASELINE_1_4_20260805` (HTML + inline + `baseline-small.pdf` + `baseline-cancel.pdf`).
- Cabecera desde Room, cuerpo remoto, persistencia e inline: confirmados visualmente por el usuario.
- Reapertura desde caché: `[RESOLVE] source=cache category=FOUND` (57 ms) + re-fetch del cuerpo (anomalía heredada documentada).
- Anomalías aceptadas: HTML literal/fallback (visible en OCR), re-fetch del cuerpo, inline lento (28.5 s en primera carga).

### 3. Acciones y envío — CONFORME PARA EL REFACTOR, CON DESVIACIÓN HEREDADA
- PRIMARY abierto y marcado como leído: OK.
- Inbox → Trash: OK (swipe; PRIMARY apareció en Papelera).
- Restaurar: **criterio funcional Trash → Inbox no cumplido** — `GmailProvider.restoreFromTrash` usa el endpoint `untrash`, que quita TRASH pero **no re-añade INBOX**; PRIMARY quedó sin etiquetas (visible solo en «TODOS» en Gmail Web). El código del provider **no fue modificado por el refactor**, por lo que se clasifica como desviación funcional heredada aceptada y no como regresión. Queda documentada para su consideración fuera del alcance del refactor.
- Responder: `RF53_REPLY_OK_20260811` — confirmado remotamente (Re: PRIMARY, 12:33).
- Reenviar: `RF53_FORWARD_OK_20260811` — confirmado remotamente (Fwd: PRIMARY, 12:34).
- PRIMARY no fue eliminado permanentemente.

### 4. PDF y conectividad — CONFORME
- Offline (ambos enlaces de red desactivados): error reintentable «No se pudo descargar. Toca para reintentar», sin pérdida de contenido (evidencia 03).
- Red restaurada, reintento: descarga validada y apertura en visor externo (evidencia 04).
- Offline de nuevo: apertura instantánea desde caché (sin red).
- Cancelación de `baseline-cancel.pdf` (salida a ~1 s): sin visor, sin PDF grande ni `.tmp` (caché solo con baseline-small.pdf).

### 5. Protección de sesión — CONFORME
- Descarga de `baseline-cancel.pdf` iniciada y logout en ~5 s; espera ≥55 s.
- Sin PDF grande final ni `.tmp` (caché PDF vacío tras logout).
- Room de la sesión cerrada: 0 correos.
- Repetición de cierre documental: descarga iniciada a las 14:03:19, confirmación de logout a las 14:03:25 y consulta final a las 14:04:24 (59 s después).
- Evidencia técnica conservada: `SESSION_ROOM_COUNT=0`, `PDF_CACHE_LARGE_COUNT=0`, `PDF_TMP_COUNT=0` y `DELETE_ONLY_ROOM_COUNT=0`.
- Reautenticación autorizada y completada por el usuario; recuperación normal verificada después: OK (Inbox repoblado).

### 6. Eliminación sacrificial — CONFORME
- Objetivo único: `MAILAPP_REFACTOR_53_20260811_DELETE_ONLY` (creado en esta ejecución, auto-dirigido).
- Inbox → Trash → diálogo irreversible «¿Eliminar permanentemente? Esta acción no se puede deshacer» (evidencia 08) → **confirmación explícita del usuario** → eliminación permanente única.
- Verificación: búsqueda remota sin resultados; Room `COUNT(*)` para DELETE_ONLY = 0. Ninguna otra eliminación permanente.

## Evidencia
- `evidencias/subfase-5.3/pixel9-api37/`: 01-inbox-fixtures, 02-contenido-html-inline-pdf, 03-pdf-offline-error, 04-pdf-viewer, 05-salida-durante-descarga, 06-descarga-antes-de-logout, 07-login-despues-de-logout, 08-confirmacion-borrado-sacrificial, dispositivo.txt, logs-saneados.txt.
- Capturas nativas del dispositivo preservadas en `/sdcard/Pictures/Screenshots/` (no eliminadas).

## Integridad
- HEAD, SHA de `EmailRepository.kt` y fingerprints de UI intactos. Sin cambios en producción, pruebas ni Gradle. `git diff --check` limpio, staging vacío. Red restaurada al estado original (Wi-Fi ON, datos ON, avión OFF).

## Decisión
- **GO del refactor, con desviación heredada documentada**. Las comprobaciones atribuibles al refactor son conformes; el criterio funcional Trash → Inbox no se cumplió por el comportamiento preexistente de `untrash`, fuera del alcance de este refactor.

## Cierre
- Subfase 5.3 **aprobada**; Etapa 5 en curso; 5.4 pendiente. Evidencias 5.1–5.3 sin staging hasta el commit documental único de 5.4. Sin commit ni push.
