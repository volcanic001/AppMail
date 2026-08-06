package com.david.mailapp.feature.emaildetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// ── Custom vector icons (MaterialSymbolsReply and FluentuiSystemIconsArrowForward) ──────

val MaterialSymbolsReply: ImageVector
    get() {
        if (_MaterialSymbolsReply != null) return _MaterialSymbolsReply!!
        _MaterialSymbolsReply = ImageVector.Builder(
            name = "reply",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(760f, 760f)
                verticalLineToRelative(-160f)
                quadToRelative(0f, -50f, -35f, -85f)
                reflectiveQuadToRelative(-85f, -35f)
                horizontalLineTo(273f)
                lineToRelative(144f, 144f)
                lineToRelative(-57f, 56f)
                lineToRelative(-240f, -240f)
                lineToRelative(240f, -240f)
                lineToRelative(57f, 56f)
                lineToRelative(-144f, 144f)
                horizontalLineToRelative(367f)
                quadToRelative(83f, 0f, 141.5f, 58.5f)
                reflectiveQuadTo(840f, 600f)
                verticalLineToRelative(160f)
                horizontalLineToRelative(-80f)
                close()
            }
        }.build()
        return _MaterialSymbolsReply!!
    }

private var _MaterialSymbolsReply: ImageVector? = null

val TablerArrowForwardUpDouble: ImageVector
    get() {
        if (_TablerArrowForwardUpDouble != null) return _TablerArrowForwardUpDouble!!
        _TablerArrowForwardUpDouble = ImageVector.Builder(
            name = "arrow-forward-up-double",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11f, 14f)
                lineToRelative(4f, -4f)
                lineToRelative(-4f, -4f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 14f)
                lineToRelative(4f, -4f)
                lineToRelative(-4f, -4f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 10f)
                horizontalLineToRelative(-7f)
                arcToRelative(4f, 4f, 0f, true, false, 0f, 8f)
                horizontalLineToRelative(1f)
            }
        }.build()
        return _TablerArrowForwardUpDouble!!
    }

private var _TablerArrowForwardUpDouble: ImageVector? = null
