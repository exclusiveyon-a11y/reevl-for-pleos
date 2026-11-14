package kr.co.reevl.ontheway.ui.ds.tmpl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.ds.comp.Actions
import kr.co.reevl.ontheway.ui.ds.comp.QrCode
import kr.co.reevl.ontheway.ui.ds.comp.RowGroup
import kr.co.reevl.ontheway.ui.ds.comp.RowSpec
import kr.co.reevl.ontheway.ui.ds.comp.TopBarSpec
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/** A link the car cannot open itself: QR for the phone + the address as text (+ an optional second line). */
data class LinkQrSpec(val url: String, val title: String, val text: String, val extra: String? = null, val label: String = "$title QR")

/**
 * Notice + choice (개인정보·위치 안내): FocusTemplate with the question, the read-only rows (2-line secondary), a policy QR and
 * ≤ 2 buttons. Same in every window class (focus column ≤ 720 dp, scrolls if too short).
 */
@Composable
fun NoticeTemplate(tag: String, top: TopBarSpec, title: String, rows: List<RowSpec>, link: LinkQrSpec, actions: Actions) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    FocusTemplate(tag, top, null, title,
        value = { RowGroup(rows, null, {}) },
        input = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.gap)) {
                QrCode(link.url, CarSize.Panel.qrSmall, tag = "${tag}_qr", label = link.label)
                Column(verticalArrangement = Arrangement.spacedBy(CarSpace.item)) {
                    Text(link.title, style = t.rowPrimary.copy(color = r.ink))
                    Text(link.text, style = t.rowSecondary.copy(color = r.sub), modifier = Modifier.testTag("${tag}_url"))
                    link.extra?.let { Text(it, style = t.rowSecondary.copy(color = r.sub), maxLines = 3) }
                }
            }
        },
        actions = actions)
}

/** A long read-only text (licence) under a top bar; scrolls. */
@Composable
fun TextTemplate(tag: String, top: TopBarSpec, text: String) {
    val r = LocalRoles.current
    Frame(tag, top, null, null, null, onePane = true) {
        val pad = kr.co.reevl.ontheway.ui.ds.token.marginFor(kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = CarSpace.gapTight)) {
            Text(text, style = LocalTypeV2.current.caption.copy(color = r.ink), modifier = Modifier.testTag("${tag}_text"))
        }
    }
}
