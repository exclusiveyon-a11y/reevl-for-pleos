package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.link.QrLink
import kr.co.reevl.ontheway.link.QrPhase
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/** Link QR side ≥ 280 dp so a phone camera reads it from the passenger seat (spec 2026-11-10). */
val QR_SIZE: Dp = CarSize.Panel.qr

/**
 * The link QR: black modules on white with a 4-module quiet zone — **in night mode too** (phone cameras read dark-on-light;
 * an inverted code fails on many readers). Modules are snapped to whole pixels so no module blurs into its neighbour.
 */
@Composable
fun QrCode(text: String, size: Dp = QR_SIZE, tag: String? = "link_qr", label: String = "연결 QR") {
    val m = remember(text) { QrLink.matrix(text) }
    Canvas(Modifier.size(size).tag(tag).semantics { contentDescription = label }) {
        drawRect(Color.White)
        val cells = m.size + 2 * QrLink.QUIET_MODULES
        val px = kotlin.math.floor(this.size.minDimension / cells)
        val origin = (this.size.minDimension - px * m.size) / 2f
        for (y in 0 until m.size) for (x in 0 until m.size) {
            if (m[x, y]) drawRect(Color.Black, Offset(origin + x * px, origin + y * px), Size(px, px))
        }
    }
}

/** Left pane of the QR link screen: the code, or a same-size placeholder while it is made / after it expired. */
@Composable
fun QrPanel(phase: QrPhase, url: String?) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    if (phase == QrPhase.SHOWING && url != null) { QrCode(url); return }
    Box(Modifier.size(QR_SIZE).background(r.chip, RoundedCornerShape(CarSpace.radiusS)).tag("link_qr_placeholder"), contentAlignment = Alignment.Center) {
        when (phase) {
            QrPhase.IDLE, QrPhase.STARTING -> Spinner(r.ink2, CarSize.Icon.progress)
            QrPhase.EXPIRED -> Text("QR이 만료됐어요", style = t.rowPrimary.copy(color = r.sub), textAlign = TextAlign.Center)
            else -> Text("QR을 만들지 못했어요", style = t.rowPrimary.copy(color = r.sub), textAlign = TextAlign.Center)
        }
    }
}

/** Right pane value: the 2-digit match number the phone shows too, and one plain line. */
@Composable
fun QrMatchNumber(phase: QrPhase, match: String?) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    // Centred under the QR (§15.3 focus column): label · number · one line.
    Column(verticalArrangement = Arrangement.spacedBy(CarSpace.item), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        if (phase == QrPhase.SHOWING && match != null) {
            Text("확인 숫자", style = t.rowSecondary.copy(color = r.sub))
            Text(match, style = t.display.copy(color = r.brandText), modifier = Modifier.tag("link_match").semantics { contentDescription = "확인 숫자 ${match.toList().joinToString(" ")}" })
            Text("휴대폰에 같은 숫자가 보이면 연결을 눌러 주세요", style = t.rowSecondary.copy(color = r.sub), maxLines = 2, textAlign = TextAlign.Center)
        } else if (phase == QrPhase.EXPIRED || phase == QrPhase.FAILED) {
            Text("새 QR을 띄우거나 6자리 번호로 연결할 수 있어요", style = t.rowSecondary.copy(color = r.sub), maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/**
 * 휴대폰으로 넘기기 — under the QR: the one-line instruction, and (while it checks) a quiet `휴대폰에서 기다리는 중` with the
 * phone glyph. [stopped] = the 10 minutes ran out → says so instead.
 */
@Composable
fun PhoneHandoffNote(line: String, stopped: Boolean) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    Column(verticalArrangement = Arrangement.spacedBy(CarSpace.item), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(line, style = t.rowSecondary.copy(color = r.sub), maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.tag("phone_line"))
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.block),
            modifier = Modifier.tag(if (stopped) "phone_stopped" else "phone_waiting")) {
            androidx.compose.material3.Icon(kr.co.reevl.ontheway.ui.components.Lucide.Smartphone, null,
                tint = if (stopped) r.sub else r.brandText, modifier = Modifier.size(kr.co.reevl.ontheway.ui.ds.token.CarSpace.iconSmall))
            Text(if (stopped) "10분이 지나 확인을 멈췄어요" else "휴대폰에서 기다리는 중",
                style = t.rowSecondary.copy(color = if (stopped) r.sub else r.brandText), maxLines = 1)
        }
    }
}
