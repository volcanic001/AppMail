package com.david.mailapp.feature.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.mailapp.R
import com.david.mailapp.core.di.AppContainer
import com.david.mailapp.core.localization.resolve
import com.david.mailapp.core.localization.toUiText
import com.david.mailapp.feature.compose.components.ComposeTopBar
import com.david.mailapp.feature.compose.components.ForwardedMessageBlock
import com.david.mailapp.feature.compose.components.OriginalMessageQuote

@Composable
fun ComposeScreen(
    args: ComposeArgs,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = AppContainer.emailRepository
    val authManager = AppContainer.authManager
    val stringProvider = AppContainer.stringProvider
    val viewModel: ComposeViewModel = viewModel(
        key = "compose_${args::class.simpleName}_${args.hashCode()}",
        factory = ComposeViewModel.Factory(args, repository, authManager, stringProvider)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle send result
    LaunchedEffect(uiState.sendResult) {
        when (val result = uiState.sendResult) {
            is SendResult.Success -> {
                snackbarHostState.showSnackbar(stringProvider.getString(R.string.compose_sent))
                viewModel.onDismissSendResult()
                onClose()
            }
            is SendResult.Error -> {
                snackbarHostState.showSnackbar(result.reason.toUiText().resolve(stringProvider))
                viewModel.onDismissSendResult()
            }
            null -> {}
        }
    }

    val transparentFieldColors = TextFieldDefaults.colors(
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ComposeTopBar(
                mode = uiState.composeMode,
                isSending = uiState.isSending,
                onClose = onClose,
                onSend = viewModel::onSend
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val subtleDividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ── De (solo lectura) ─────────────────────────────
            FieldRow(
                label = stringResource(R.string.compose_field_from),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.fromAddress.ifBlank { stringResource(R.string.compose_from_loading) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = subtleDividerColor)

            // ── Para + botón Cc/Cco integrado a la derecha ────
            FieldRow(
                label = stringResource(R.string.compose_field_to),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = uiState.toField,
                    onValueChange = viewModel::onToChanged,
                    placeholder = {
                        Text(
                            stringResource(R.string.compose_placeholder_recipient),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = transparentFieldColors,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (uiState.isCcBccExpanded) {
                        stringResource(R.string.compose_cc_bcc_hide)
                    } else {
                        stringResource(R.string.compose_cc_bcc_label)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { viewModel.onToggleCcBcc() }
                        .padding(start = 8.dp, end = 4.dp, top = 16.dp, bottom = 16.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = subtleDividerColor)

            // ── Cc / Cco (colapsable) ─────────────────────────
            AnimatedVisibility(visible = uiState.isCcBccExpanded) {
                Column {
                    FieldRow(
                        label = stringResource(R.string.compose_field_cc),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = uiState.ccField,
                            onValueChange = viewModel::onCcChanged,
                            placeholder = {
                                Text(
                                    stringResource(R.string.compose_field_cc),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = transparentFieldColors,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = subtleDividerColor)
                    FieldRow(
                        label = stringResource(R.string.compose_field_bcc),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = uiState.bccField,
                            onValueChange = viewModel::onBccChanged,
                            placeholder = {
                                Text(
                                    stringResource(R.string.compose_field_bcc),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = transparentFieldColors,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = subtleDividerColor)
                }
            }

            // ── Asunto ────────────────────────────────────────
            FieldRow(
                label = stringResource(R.string.compose_field_subject),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = uiState.subject,
                    onValueChange = viewModel::onSubjectChanged,
                    placeholder = {
                        Text(
                            stringResource(R.string.compose_field_subject),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = transparentFieldColors,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // ── Body ──────────────────────────────────────────
            when (uiState.composeMode) {
                ComposeMode.WRITE -> {
                    TextField(
                        value = uiState.bodyText,
                        onValueChange = viewModel::onBodyChanged,
                        placeholder = {
                            Text(
                                stringResource(R.string.compose_placeholder_write),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = transparentFieldColors,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
                ComposeMode.REPLY -> {
                    TextField(
                        value = uiState.bodyText,
                        onValueChange = viewModel::onBodyChanged,
                        placeholder = {
                            Text(
                                stringResource(R.string.compose_placeholder_reply),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = transparentFieldColors,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    )

                    HorizontalDivider(color = subtleDividerColor)

                    uiState.originalEmail?.let { email ->
                        OriginalMessageQuote(
                            email = email,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                ComposeMode.FORWARD -> {
                    TextField(
                        value = uiState.bodyText,
                        onValueChange = viewModel::onBodyChanged,
                        placeholder = {
                            Text(
                                stringResource(R.string.compose_placeholder_forward),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = transparentFieldColors,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    )

                    HorizontalDivider(color = subtleDividerColor)

                    uiState.originalEmail?.let { email ->
                        ForwardedMessageBlock(
                            email = email,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Fila de campo con label a la izquierda y contenido a la derecha alineados con precisión.
 */
@Composable
private fun FieldRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        content()
    }
}
