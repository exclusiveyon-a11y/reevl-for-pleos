package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.R
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.ReevlLogo
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.CarTypeV2
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/** No [onBack] = top level (square app icon + title, `REEVL` when null); with [onBack] = depth (‹ + title). Actions ≤ 2. */
data class TopBarSpec(val title: String? = null, val onBack: (() -> Unit)? = null, val actions: List<IconSpec> = emptyList())

@Composable
fun TopBar(s: TopBarSpec) {
    val r = LocalRoles.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(CarSpace.topBar).background(r.surface).padding(start = if (s.onBack != null) 0.dp else CarSpace.margin),
            verticalAlignment = Alignment.CenterVertically) {
            // Top level: square app icon + title (built-in Phone `전화`); depth: ‹ + title.
            if (s.onBack == null) {
                // §14.4-3: the launcher icon is a white square (RGB, no alpha) → at night it read as a blank white block. The header
                // uses the REEVL mark on transparency instead; at night it sits on a dark tile like the built-in apps' icons
                // (native_08_phone_dark: grey rounded tile + glyph). Day: the mark alone on the white header (same look as before).
                ReevlMark(CarSize.Control.appMark, Modifier.tag("app_mark"))
                Spacer(Modifier.width(CarSize.Inset.m))
                // weight: the actions are measured first, the title takes what is left and ellipsizes (narrow window, §15).
                Text(s.title ?: "REEVL", style = CarTypeV2.Compact.topTitle.copy(color = r.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else {
                IconBtn(IconSpec(Lucide.ChevronLeft, "뒤로", s.onBack, tag = "back"))
                Text(s.title.orEmpty(), style = CarTypeV2.Compact.topTitle.copy(color = r.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            // 1:1 window: the left menu folds into this button. Narrow window: actions keep only their icon (label → a11y).
            val rail = LocalRail.current
            val narrow = kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current == kr.co.reevl.ontheway.ui.ds.token.WindowClass.Narrow
            val acts = s.actions.take(2).map { if (narrow) it.copy(text = null) else it } + if (rail != null && !kr.co.reevl.ontheway.ui.ds.token.LocalWideWindow.current)
                listOf(IconSpec(Lucide.Menu, "메뉴", rail.onMenu, tag = "menu_open")) else emptyList()
            if (acts.isNotEmpty()) Box(Modifier.width(CarSpace.hairline).height(CarSpace.topBar).background(r.line)) // built-in header divider before ⋯
            acts.forEach { IconBtn(it) }
        }
        Box(Modifier.fillMaxWidth().height(CarSpace.hairline).background(r.line))
    }
}

/** The REEVL mark: on transparency by day; at night on a dark rounded tile (never the white launcher square — glare at night). */
@Composable
fun ReevlMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    Box(modifier.size(size).then(if (r.dark) Modifier.background(r.chip, RoundedCornerShape(size * 0.2f)) else Modifier), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.reevl_mark), "리블", modifier = Modifier.size(if (r.dark) size * 0.8f else size))
    }
}
