package kr.co.reevl.ontheway.ui.ds.tmpl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.ds.comp.AreaHero
import kr.co.reevl.ontheway.ui.ds.comp.AreaHeroSpec
import kr.co.reevl.ontheway.ui.ds.comp.AreaMap
import kr.co.reevl.ontheway.ui.ds.comp.AreaMapSpec
import kr.co.reevl.ontheway.ui.ds.comp.BannerSpec
import kr.co.reevl.ontheway.ui.ds.comp.MessageContent
import kr.co.reevl.ontheway.ui.ds.comp.MessageSpec
import kr.co.reevl.ontheway.ui.ds.comp.RowGroup
import kr.co.reevl.ontheway.ui.ds.comp.RowSpec
import kr.co.reevl.ontheway.ui.ds.comp.Skeleton
import kr.co.reevl.ontheway.ui.ds.comp.SourceFooter
import kr.co.reevl.ontheway.ui.ds.comp.Tabs
import kr.co.reevl.ontheway.ui.ds.comp.TabsSpec
import kr.co.reevl.ontheway.ui.ds.comp.TopBarSpec
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalWideWindow
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/**
 * 동네 (area-context-car-spec §3): tabs (≤ 4) over one scrolling pane — hero (요약), heat grid + legend (히트맵), flat rows
 * (≤ 12, car limit 21 items per task). Wide window: grid left, the selected 동's rows right (like the built-in split panes).
 */
@Composable
fun AreaTemplate(top: TopBarSpec, banner: BannerSpec?, tabs: TabsSpec, hero: AreaHeroSpec?, map: AreaMapSpec?, rows: List<RowSpec>,
                 empty: MessageSpec?, loading: Boolean, footer: String?, onRow: (RowSpec) -> Unit, toast: ToastSlot? = null,
                 summary: kr.co.reevl.ontheway.ui.ds.comp.AreaSummarySpec? = null) {
    Frame("screen_area", top, banner, null, toast, onePane = true) {
        Tabs(tabs)
        // §15.3: an empty / no-location state is centred in the pane like every other message.
        if (empty != null && !(loading && hero == null && summary == null)) CenteredMessage(empty)
        else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = CarSpace.margin).padding(bottom = CarSpace.margin)) {
            when {
                loading && hero == null && summary == null -> Column(Modifier.padding(top = CarSpace.gapTight), verticalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
                    repeat(4) { Skeleton(CarSpace.rowDouble) }
                }
                empty != null -> MessageContent(empty, Modifier.padding(top = CarSize.Empty.topArea))
                summary != null -> {
                    kr.co.reevl.ontheway.ui.ds.comp.AreaSummary(summary, rows, onRow)
                    footer?.let { SourceFooter(it) }
                }
                else -> {
                    hero?.let { AreaHero(it) }
                    if (map != null && LocalWideWindow.current) {
                        Row(Modifier.padding(top = CarSpace.gapTight), horizontalArrangement = Arrangement.spacedBy(CarSpace.gap)) {
                            AreaMap(map, Modifier.width(CarSize.Measure.areaMapSide))
                            Column(Modifier.weight(1f)) { RowGroup(rows.take(12), null, onRow) }
                        }
                    } else {
                        map?.let { AreaMap(it, Modifier.padding(top = CarSpace.gapTight)) }
                        RowGroup(rows.take(12), null, onRow)
                    }
                    footer?.let { SourceFooter(it) }
                }
            }
        }
    }
}
