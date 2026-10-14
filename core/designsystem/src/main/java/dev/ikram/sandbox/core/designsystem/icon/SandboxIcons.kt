package dev.ikram.sandbox.core.designsystem.icon

import androidx.compose.ui.graphics.Color as GraphicsColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The design's stroke icons (24 x 24 grid, round caps and joins), built from the same SVG
 * paths the mockups use. Tint them with `Icon(tint = ...)` like any other vector.
 */
object SandboxIcons {
    private const val SHIELD = "M12 2.8 4.5 5.8v5.7c0 4.6 3.2 8.5 7.5 9.7 4.3-1.2 7.5-5.1 7.5-9.7V5.8L12 2.8Z"
    private const val DOCUMENT = "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z"

    /** The SafeHold mark: a shield with a keyhole. Also the vault tab. */
    val Vault by lazy { icon("Vault", SHIELD, circle(12f, 10.5f, 1.8f), "M12 12.3v3.2") }
    val ShieldCheck by lazy { icon("ShieldCheck", SHIELD, "m9 12 2 2 4-4") }
    val Lock by lazy { icon("Lock", rect(5f, 11f, 14f, 10f, 2f), "M8 11V8a4 4 0 0 1 8 0v3") }
    val Browser by lazy {
        icon(
            "Browser",
            circle(12f, 12f, 9f),
            "M3 12h18M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3Z",
        )
    }
    val Settings by lazy {
        icon("Settings", "M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f))
    }

    val Back by lazy { icon("Back", "M19 12H5M12 19l-7-7 7-7") }
    val Forward by lazy { icon("Forward", "M5 12h14M12 5l7 7-7 7") }
    val Refresh by lazy { icon("Refresh", "M20 12a8 8 0 1 1-2.34-5.66M20 4v5h-5") }
    val Search by lazy { icon("Search", circle(11f, 11f, 7f), "m20 20-3.5-3.5") }
    val Close by lazy { icon("Close", "M18 6 6 18M6 6l12 12") }
    val Check by lazy { icon("Check", "m5 12 5 5 9-10") }
    val ChevronRight by lazy { icon("ChevronRight", "m9 6 6 6-6 6") }
    val ChevronDown by lazy { icon("ChevronDown", "m6 9 6 6 6-6") }
    val Menu by lazy { icon("Menu", "M4 7h16M4 12h16M4 17h16") }
    val MoreVertical by lazy { icon("MoreVertical", "M12 5h.01M12 12h.01M12 19h.01", strokeWidth = 3f) }
    val ListView by lazy { icon("ListView", "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01") }
    val GridView by lazy {
        icon(
            "GridView",
            rect(4f, 4f, 7f, 7f, 1.5f),
            rect(13f, 4f, 7f, 7f, 1.5f),
            rect(4f, 13f, 7f, 7f, 1.5f),
            rect(13f, 13f, 7f, 7f, 1.5f),
        )
    }

    val Open by lazy {
        icon("Open", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z", circle(12f, 12f, 3f))
    }
    val Share by lazy {
        icon(
            "Share",
            circle(18f, 5f, 3f),
            circle(6f, 12f, 3f),
            circle(18f, 19f, 3f),
            "m8.6 13.5 6.8 4M15.4 6.5l-6.8 4",
        )
    }
    val Delete by lazy {
        icon("Delete", "M4 7h16M10 11v6M14 11v6M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13M9 7V4h6v3")
    }
    val Download by lazy { icon("Download", "M12 4v11M7 10l5 5 5-5M5 20h14") }
    val Copy by lazy {
        icon("Copy", rect(8f, 8f, 12f, 12f, 2f), "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2")
    }
    val Clock by lazy { icon("Clock", circle(12f, 12f, 9f), "M12 7v5l3 2") }
    val Info by lazy { icon("Info", circle(12f, 12f, 9f), "M12 11v5M12 8h.01") }
    val Warning by lazy { icon("Warning", "M12 3 2 20h20L12 3ZM12 10v4M12 17h.01") }
    val Fingerprint by lazy {
        icon(
            "Fingerprint",
            "M12 11v3a8 8 0 0 1-1.5 4.6M8 11a4 4 0 0 1 8 0v1.5M5 9a8 8 0 0 1 14 0M16 15a12 12 0 0 1-1 4" +
                "M8 15a8 8 0 0 1-1 3.5",
        )
    }
    val ScanCheck by lazy { icon("ScanCheck", circle(11f, 11f, 7f), "m20 20-3.5-3.5M8 11l2 2 4-4") }

    // File types
    val Image by lazy { icon("Image", rect(3f, 4f, 18f, 16f, 2f), circle(9f, 10f, 2f), "m21 16-5-5-9 9") }
    val Video by lazy { icon("Video", rect(3f, 5f, 13f, 14f, 2f), "m16 10 5-3v10l-5-3") }
    val Audio by lazy { icon("Audio", "M9 18V5l11-2v13", circle(6f, 18f, 3f), circle(17f, 16f, 3f)) }
    val Pdf by lazy { icon("Pdf", DOCUMENT, "M14 3v5h5M9 13h6M9 17h4") }
    val Text by lazy { icon("Text", "M5 6h14M5 11h14M5 16h9") }
    val Code by lazy { icon("Code", "m8 8-4 4 4 4M16 8l4 4-4 4M13.5 5l-3 14") }
    val Archive by lazy {
        icon("Archive", rect(3f, 4f, 18f, 5f, 1f), "M5 9v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V9M10 13h4")
    }
    val Blocked by lazy { icon("Blocked", circle(12f, 12f, 9f), "m5.6 5.6 12.8 12.8") }
    val Unknown by lazy {
        icon("Unknown", DOCUMENT, "M14 3v5h5", "M10 12a2 2 0 1 1 2.5 1.9c-.4.2-.5.5-.5.9V15M12 18h.01")
    }

    private fun icon(name: String, vararg paths: String, strokeWidth: Float = 1.8f): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(GraphicsColor.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    /** SVG `<circle>` as path data. */
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

    /** SVG `<rect rx>` as path data. */
    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float): String {
        val innerW = w - 2 * rx
        val innerH = h - 2 * rx
        return "M${x + rx} ${y}h${innerW}a$rx $rx 0 0 1 $rx ${rx}v${innerH}a$rx $rx 0 0 1 ${-rx} $rx" +
            "h${-innerW}a$rx $rx 0 0 1 ${-rx} ${-rx}v${-innerH}a$rx $rx 0 0 1 $rx ${-rx}Z"
    }
}
