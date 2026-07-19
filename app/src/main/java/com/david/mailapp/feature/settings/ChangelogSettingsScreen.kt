package com.david.mailapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.david.mailapp.feature.settings.components.SettingsCard
import com.david.mailapp.feature.settings.components.SettingsCardPosition

/**
 * Data structure for the simple changelog.
 */
data class ChangelogEntry(
    val version: String,
    val changes: List<String>
)

/**
 * Pre-defined changelog history.
 */
private val changelogHistory = listOf(
    ChangelogEntry(
        version = "1.7.0",
        changes = listOf(
            "Atenuación de correos leídos: Se mejoró la jerarquía visual de la bandeja de entrada y la pantalla de búsqueda reduciendo la opacidad al 60% en el asunto, el fragmento y la hora de los correos ya leídos, manteniendo el remitente y avatar legibles al 100%.",
            "Soporte de fuente personalizada (Google Sans Flex): Integración de la fuente variable original y el mecanismo de Monomail, creando familias dinámicas con control del eje ROND para la redondez y del eje wght (pesos del 400 al 900), activable mediante un switch en la configuración de apariencia."
        )
    ),
    ChangelogEntry(
        version = "1.5.0",
        changes = listOf(
            "Rediseño de cita de respuesta: Simplificación de la cita del correo original al responder, mostrando remitente, asunto y fecha de forma limpia con ajuste de altura dinámico y soporte para un extracto de 2 líneas de texto sanitizado.",
            "Acordeón colapsable en reenvíos: Implementación de un contenedor interactivo para ocultar/mostrar el correo original completo al reenviar, manteniendo despejado el espacio de redacción."
        )
    ),
    ChangelogEntry(
        version = "1.3.0",
        changes = listOf(
            "Corrección en responder/reenviar: Se solucionó el bug donde ambas opciones mostraban la misma pantalla debido a la reutilización de caché del ViewModel, y se eliminó el retraso visual de un segundo al abrir la pantalla de redacción."
        )
    ),
    ChangelogEntry(
        version = "1.0",
        changes = listOf(
            "Optimizacion de correos: Se unificó la descarga de red en una única solicitud (format=full), eliminando peticiones remotas duplicadas al servidor de Gmail.",
            "Visualización progresiva de emails: El cuerpo del mensaje se muestra inmediatamente en pantalla con el HTML pre-limpiado y seguro, cargando las imágenes inline en segundo plano sin bloquear la lectura.",
            "Caché inteligente de HTML limpio: La sanitización y remoción de trackers con Jsoup se realiza una única vez en segundo plano, persistiendo el resultado final en la base de datos local (Room) para cargas instantáneas subsecuentes.",
            "Corrección de scroll en refresco: Solucionado el desplazamiento residual del gesto pull-to-refresh que requería scroll manual para ver el último correo recibido.",
            "Reestructuración de la pantalla de detalle del correo.",
            "Corrección del problema visual al abrir correos usando instrumentación para encontrar el ajuste."
        )
    ),
    ChangelogEntry(
        version = "0.9.6",
        changes = listOf(
            "Integración de imágenes en línea (inline attachments) referenciadas por Content-ID (cid:). Las imágenes ahora se descargan en memoria RAM bajo demanda y se inyectan dinámicamente en el WebView del correo, evitando sobrecargas de almacenamiento en la base de datos local (SQLiteBlobTooBigException).",
            "Gestos interactivos en imágenes: Al mantener presionada cualquier imagen dentro del cuerpo del correo, se despliega un menú inferior con opciones personalizadas.",
            "Opción para abrir imágenes decodificadas en pantalla completa y guardarlas de forma segura en la galería del dispositivo usando MediaStore."
        )
    ),
    ChangelogEntry(
        version = "0.9.0",
        changes = listOf(
            "El refresco (Pull-to-Refresh) ahora preserva el cuerpo HTML descargado de los correos en la base de datos local, evitando descargas innecesarias."
        )
    ),
    ChangelogEntry(
        version = "0.8.0",
        changes = listOf(
            "Simplificación visual de los correos en la bandeja de entrada y papelera",
            "Los correos ahora muestran únicamente el nombre del remitente en lugar de la dirección completa, reduciendo el ruido visual",
            "Mejoras en el zoom táctil (pinch-to-zoom) en la vista de detalle, evitando que la pantalla haga scroll vertical de forma involuntaria al pellizcar para ampliar o reducir, además de asegurar que los correos se adapten perfectamente a pantallas móviles (viewport y textZoom fijos)",
            "Carga reactiva y resiliente en el detalle de correos: Se eliminaron estados bloqueantes en memoria, garantizando que el indicador de carga no se quede girando infinitamente ante fallos de sincronización"
        )
    ),
    ChangelogEntry(
        version = "0.7.0",
        changes = listOf(
            "Rediseño completo del header de detalle de correo en modo colapsable y expandible",
            "Formateo inteligente de remitentes (correos largos recortados elegantemente solo en vista colapsada, mostrados completos al expandir)",
            "Lógica de fecha relativa a la derecha (hora para hoy, fecha relativa para días/años recientes)",
            "Flecha de expansión estática con rotación e inicio de transición animada suave (AnimatedVisibility)"
        )
    ),
    ChangelogEntry(
        version = "0.6.0",
        changes = listOf(
            "Corregida la sincronización de la papelera para que al vaciarla desde Gmail también se actualice en MailApp con pull-to-refresh"
        )
    ),
    ChangelogEntry(
        version = "0.5.0",
        changes = listOf(
            "Corregido el retorno a la lista de correos para mantener la posición de scroll",
            "Ajustado el detalle del email para estabilizar el loading circular y la carga del cuerpo",
            "Resaltado tonal suave (800 ms) en el correo recientemente leído al regresar a la lista."
        )
    ),
    ChangelogEntry(
        version = "0.4.1",
        changes = listOf(
            "Corregido el retorno a la lista de correos para mantener la posición de scroll en Inbox, Papelera y Búsqueda"
        )
    ),
    ChangelogEntry(
        version = "0.4.0",
        changes = listOf(
            "Implementada la visualización del cuerpo del email mediante un componente WebView optimizado",
            "Privacidad y Seguridad: bloqueo automático de trackers, rastreadores y píxeles espía",
            "Limpiador HTML mejorado para eliminar atributos y propiedades CSS de fondo (bgcolor, background) en modo oscuro y claro",
            "Normalización de contraste tipográfico: texto blanco suave en modo oscuro y negro carbón suave (100% opacidad) en modo claro",
            "Sincronización del tema del WebView con la preferencia de la aplicación (LocalThemeConfig)"
        )
    ),
    ChangelogEntry(
        version = "0.3.0",
        changes = listOf(
            "Implementada fase 1 del detalle de correos (cabeceras con asunto, remitente, destinatario y fecha)",
            "Corregido bug de caché de ViewModel al alternar entre diferentes correos",
            "Corregido cierre inesperado (crash) de la aplicación cuando no hay conexión a Internet",
            "Conectada la navegación al detalle de correos desde las pantallas de Papelera y Búsqueda",
            "Corregido espacio negro inferior (edge-to-edge) en la pantalla de Búsqueda"
        )
    ),
    ChangelogEntry(
        version = "0.2.0",
        changes = listOf(
            "Agregado historial de versiones (Changelog)",
            "Navegación nativa ajustada para no cerrar la app desde Ajustes",
            "Márgenes y tamaños consistentes en tarjetas de configuración"
        )
    ),
    ChangelogEntry(
        version = "0.1.0",
        changes = listOf(
            "Lanzamiento inicial de MailApp",
            "Soporte para múltiples paletas de colores y modo oscuro",
            "Gestos predictivos de navegación suaves"
        )
    )
)

/**
 * Dedicated screen for displaying the app's version history (Changelog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Historial de versiones", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            changelogHistory.forEach { entry ->
                item(key = "header_${entry.version}") {
                    Text(
                        text = entry.version,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 16.dp)
                    )
                }

                items(entry.changes.size) { index ->
                    val change = entry.changes[index]
                    val position = when {
                        entry.changes.size == 1 -> SettingsCardPosition.Single
                        index == 0 -> SettingsCardPosition.First
                        index == entry.changes.lastIndex -> SettingsCardPosition.Last
                        else -> SettingsCardPosition.Middle
                    }
                    
                    SettingsCard(position = position) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = change,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
