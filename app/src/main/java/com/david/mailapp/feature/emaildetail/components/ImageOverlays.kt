package com.david.mailapp.feature.emaildetail.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.david.mailapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageActionSheet(
    activeImageUrl: String,
    saveCoroutineScope: CoroutineScope,
    onOpenFullscreen: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val saveLabels = ImageSaveLabels(
        invalidFormatMessage = stringResource(R.string.image_invalid_format),
        savedToGalleryMessage = stringResource(R.string.image_saved_to_gallery),
        saveErrorMessage = stringResource(R.string.image_save_error),
        filenameTemplate = stringResource(R.string.image_filename_format)
    )
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 12.dp) // Tightens top/bottom spacing
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.image_open)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // Eliminates background shadow box
                modifier = Modifier.clickable {
                    onOpenFullscreen(activeImageUrl)
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.image_save)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), // Eliminates background shadow box
                modifier = Modifier.clickable {
                    val urlToSave = activeImageUrl
                    val resolvedLabels = saveLabels
                    saveCoroutineScope.launch {
                        ImageUtils.saveImageToGallery(context, urlToSave, resolvedLabels)
                    }
                    onDismiss()
                }
            )
        }
    }
}

@Composable
internal fun FullscreenImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember(imageUrl) {
        ImageUtils.decodeDataUriToBitmap(imageUrl)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.image_fullscreen),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    stringResource(R.string.image_load_error),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
