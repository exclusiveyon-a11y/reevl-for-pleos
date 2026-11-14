package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.ds.token.CarRoles
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/**
 * REEVL compare mark `VS` — port of web components/common/CompareIcon.tsx (same two strokes, 24 grid, stroke 2, round caps),
 * so the car's 비교 reads like the web's sidebar/bottom-nav 비교.
 */
val CompareVs: ImageVector by lazy {
    ImageVector.Builder(name = "CompareVs", defaultWidth = CarSize.Icon.glyph, defaultHeight = CarSize.Icon.glyph, viewportWidth = 24f, viewportHeight = 24f).apply {
        for (d in listOf("M5.2 4.75L8.4 12.8L11.6 4.75",
            "M18.3 12C18.3 10.8 17 10.6 15.4 10.6C13.8 10.6 12.5 10.8 12.5 12C12.5 15.2 18.3 14.2 18.3 17.4C18.3 18.6 17 18.8 15.4 18.8C13.8 18.8 12.5 18.6 12.5 17.4"))
            addPath(addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}

/** Value colour by the server's `color`: `dir` = sign (up red / down blue, web dirColor) · `ai` = REEVL AI ink · else ink. */
fun statColor(r: CarRoles, color: String?, value: String): Color = when (color) {
    "dir" -> when { value.startsWith("+") && value.any { it in '1'..'9' } -> r.upText; value.startsWith("-") -> r.downText; else -> r.ink }
    "ai" -> r.aiInk
    else -> r.ink
}

/**
 * One REEVL StatRow (web StatBlocks `StatCell`: label left `text-dim`, value right tnum, chip beside it) as one flat car line:
 * label (rowSecondary, sub) · sub (rowSecondary, sub — rank/percentile/count, cut first) · value (`value` role, Medium, tnum),
 * 1 dp line below. `ai` adds the one AI mark before the label. Min 84 high — a 2 s glance reads a row, not a card.
 */
@Composable
fun StatRow(label: String, value: String, sub: String?, color: String?, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = CarSize.Row.stat).padding(vertical = CarSpace.item), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
            Row(Modifier.widthIn(max = CarSize.Measure.statLabel), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.item)) {
                // AI forecast label = the web AI card title (TypeOutlookCard.tsx:56–58): `.ai-icon-grad` Sparkles + `.ai-text-grad` text.
                if (color == "ai") {
                    kr.co.reevl.ontheway.ui.components.AiIcon(Lucide.Sparkles, null, Modifier.size(CarSpace.iconSmall))
                    kr.co.reevl.ontheway.ui.components.AiGradientText(label, t.rowSecondary)
                } else Text(label, style = t.rowSecondary.copy(color = r.sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // §15 narrow window: the secondary stat (rank · count · line name) is the first thing to go — label and value stay whole.
            val narrow = kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current == kr.co.reevl.ontheway.ui.ds.token.WindowClass.Narrow
            Text(if (narrow) "" else sub ?: "", style = t.rowSecondary.copy(color = r.sub, textAlign = TextAlign.End), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Text(value, style = t.value.copy(color = statColor(r, color, value)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = CarSize.Measure.statValue))
        }
        Box(Modifier.fillMaxWidth().height(CarSpace.hairline).background(r.line))
    }
}

/** Rows of one tab: one column in the 840 window, two in a pane ≥ 1000 dp (web KeyStats `@xl` 2 columns), filled row by row. */
@Composable
fun StatGrid(items: List<StatSpec>) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Two columns only when every line fits half a pane (school names and stations stay one per line, never cut).
        val twoCol = maxWidth >= CarSize.Measure.statTwoCol && items.all { it.label.length + it.value.length + (it.sub?.length ?: 0) <= 24 }
        if (!twoCol) Column { items.forEach { s -> StatRow(s.label, s.value, s.sub, s.color, Modifier.tag("stat_${s.key}")) } }
        else Column {
            items.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CarSize.Measure.statColumnGap)) {
                    pair.forEach { s -> StatRow(s.label, s.value, s.sub, s.color, Modifier.weight(1f).tag("stat_${s.key}")) }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Small section label inside a tab (`최근 실거래`). */
@Composable
fun StatHead(text: String) {
    Text(text, style = LocalTypeV2.current.rowSecondary.copy(color = LocalRoles.current.sub), maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(top = CarSpace.gapTight, bottom = CarSpace.snug))
}
