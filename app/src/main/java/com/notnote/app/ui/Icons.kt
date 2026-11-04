package com.notnote.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Uygulamanın birkaç çizgi ikonu (ek kütüphane gerekmesin diye elle çizildi). */
object NIcons {

    private fun stroke(name: String, width: Float = 2f, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    val Menu = stroke("menu") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(4f, 12f); lineTo(17f, 12f)
        moveTo(4f, 17f); lineTo(13f, 17f)
    }

    val ArrowUp = stroke("up", 2.2f) {
        moveTo(12f, 19f); lineTo(12f, 5f)
        moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
    }

    val Back = stroke("back") {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }

    val Checklist = stroke("checklist") {
        moveTo(3.5f, 7f); lineTo(5.5f, 9f); lineTo(9f, 5f)
        moveTo(3.5f, 15f); lineTo(5.5f, 17f); lineTo(9f, 13f)
        moveTo(12.5f, 7.5f); lineTo(20.5f, 7.5f)
        moveTo(12.5f, 15.5f); lineTo(20.5f, 15.5f)
    }

    val Lock = stroke("lock") {
        moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(6f, 20f); close()
        moveTo(8.5f, 11f); lineTo(8.5f, 8f)
        curveTo(8.5f, 3.5f, 15.5f, 3.5f, 15.5f, 8f)
        lineTo(15.5f, 11f)
    }

    val At = stroke("at") {
        moveTo(16f, 12f)
        curveTo(16f, 17f, 21f, 16f, 21f, 12f)
        curveTo(21f, 6f, 17f, 3f, 12f, 3f)
        curveTo(7f, 3f, 3f, 7f, 3f, 12f)
        curveTo(3f, 17f, 7f, 21f, 12f, 21f)
        curveTo(14f, 21f, 15.5f, 20.5f, 17f, 19.5f)
        moveTo(16f, 12f)
        curveTo(16f, 6.7f, 8f, 6.7f, 8f, 12f)
        curveTo(8f, 17.3f, 16f, 17.3f, 16f, 12f)
    }

    val Save = stroke("save") {
        moveTo(12f, 4f); lineTo(12f, 15f)
        moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        moveTo(5f, 20f); lineTo(19f, 20f)
    }

    val Search = stroke("search") {
        moveTo(15.5f, 15.5f); lineTo(20f, 20f)
        moveTo(17f, 10.5f)
        curveTo(17f, 1.8f, 4f, 1.8f, 4f, 10.5f)
        curveTo(4f, 19.2f, 17f, 19.2f, 17f, 10.5f)
    }

    val Close = stroke("close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val Mic = stroke("mic") {
        moveTo(9f, 7f)
        curveTo(9f, 3f, 15f, 3f, 15f, 7f)
        lineTo(15f, 11f)
        curveTo(15f, 15f, 9f, 15f, 9f, 11f)
        close()
        moveTo(5.5f, 11f)
        curveTo(5.5f, 19.5f, 18.5f, 19.5f, 18.5f, 11f)
        moveTo(12f, 17.5f); lineTo(12f, 21f)
    }

    val Pin = stroke("pin", 1.8f) {
        moveTo(9f, 4f); lineTo(15f, 4f)
        moveTo(10f, 4f); lineTo(10f, 10f); lineTo(7f, 14f); lineTo(17f, 14f); lineTo(14f, 10f); lineTo(14f, 4f)
        moveTo(12f, 14f); lineTo(12f, 20f)
    }

    val Restore = stroke("restore") {
        moveTo(12f, 15f); lineTo(12f, 4f)
        moveTo(7f, 9f); lineTo(12f, 4f); lineTo(17f, 9f)
        moveTo(5f, 20f); lineTo(19f, 20f)
    }
}
