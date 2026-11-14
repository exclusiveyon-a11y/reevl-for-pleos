package kr.co.reevl.ontheway.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/** Lucide icons used only by 동네 (same builder and stroke as [Lucide]; kept apart so the area branch merges cleanly). */
object AreaIcons {
    /** lucide `map` */
    val Map: ImageVector by lazy {
        icon("Map", "M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0z",
            "M15 5.764v15", "M9 3.236v15")
    }
    /** lucide `trending-up` */
    val TrendingUp: ImageVector by lazy { icon("TrendingUp", "M16 7h6v6", "m22 7-8.5 8.5-5-5L2 17") }
    /** lucide `trending-down` */
    val TrendingDown: ImageVector by lazy { icon("TrendingDown", "M16 17h6v-6", "m22 17-8.5-8.5-5 5L2 7") }
    /** lucide `newspaper` */
    val Newspaper: ImageVector by lazy {
        icon("Newspaper", "M15 18h-5", "M18 14h-8",
            "M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-4 0v-9a2 2 0 0 1 2-2h2", "M10 6h8v4h-8z")
    }
    /** lucide `chart-column` */
    val ChartColumn: ImageVector by lazy { icon("ChartColumn", "M3 3v16a2 2 0 0 0 2 2h16", "M18 17V9", "M13 17V5", "M8 17v-3") }

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = CarSize.Icon.glyph, defaultHeight = CarSize.Icon.glyph, viewportWidth = 24f, viewportHeight = 24f).apply {
            for (d in paths) addPath(pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }.build()
}
