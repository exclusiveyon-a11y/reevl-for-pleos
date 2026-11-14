package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/** Lucide `star` with a fill (same path) — the "saved" look. Tinted by the caller like any icon. */
val StarFilled: ImageVector by lazy {
    ImageVector.Builder("StarFilled", CarSize.Icon.glyph, CarSize.Icon.glyph, 24f, 24f).apply {
        addPath(addPathNodes("M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z"), fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}

/**
 * car-ds-v2 §5.5 IconButton toggle, one rule for every ☆ (TopBar and pane header):
 * on = filled star + `저장됨` in brandText · off = outline star + `저장` in ink · unknown = outline `저장` disabled (32 %).
 */
enum class SaveLook(val filled: Boolean, val label: String, val on: Boolean, val enabled: Boolean) {
    On(true, "저장됨", true, true), Off(false, "저장", false, true), Unknown(false, "저장", false, false);

    companion object { fun of(saved: Boolean?) = when (saved) { true -> On; false -> Off; null -> Unknown } }
}

fun saveToggle(saved: Boolean?, busy: Boolean, onClick: () -> Unit): IconSpec {
    val l = SaveLook.of(saved)
    return IconSpec(if (l.filled) StarFilled else Lucide.Star, "저장", onClick, on = l.on, text = l.label, enabled = l.enabled, loading = busy, tag = "save")
}
