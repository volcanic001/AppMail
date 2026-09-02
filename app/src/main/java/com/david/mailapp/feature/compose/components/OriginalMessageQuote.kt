package com.david.mailapp.feature.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.mailapp.R
import com.david.mailapp.domain.model.Email
import com.david.mailapp.feature.compose.ComposeFormatUtils

/**
 * Bloque de cita del email original en modo REPLY.
 *
 * Muestra una línea vertical azul a la izquierda con el remitente,
 * fecha y contenido del email original (no editable).
 */
@Composable
fun OriginalMessageQuote(
    email: Email,
    modifier: Modifier = Modifier
) {
    val displayBody = ComposeFormatUtils.getOriginalPlainText(email)
    val sanitizedBody = displayBody.replace(Regex("[\\s\\u00A0\\u200B]+"), " ").trim()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(IntrinsicSize.Min)
        ) {
            // Línea vertical izquierda
            Surface(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary
            ) {}

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = email.from.substringBefore("<").trim().ifBlank { email.from },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = email.subject.ifBlank { stringResource(R.string.compose_no_subject_quote_fallback) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = ComposeFormatUtils.formatTimestamp(
                        email.timestamp,
                        stringResource(R.string.date_pattern_short)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (sanitizedBody.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sanitizedBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
