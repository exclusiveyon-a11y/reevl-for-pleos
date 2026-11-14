package kr.co.reevl.ontheway.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kr.co.reevl.ontheway.area.AreaLogic
import kr.co.reevl.ontheway.area.AreaLogic.Period
import kr.co.reevl.ontheway.data.AreaNeighbor
import kr.co.reevl.ontheway.data.AreaResponse
import kr.co.reevl.ontheway.nearby.NearbyEngine
import kr.co.reevl.ontheway.ui.components.AreaIcons
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.ds.comp.AreaHeroSpec
import kr.co.reevl.ontheway.ui.ds.comp.AreaCompare
import kr.co.reevl.ontheway.ui.ds.comp.AreaFact
import kr.co.reevl.ontheway.ui.ds.comp.AreaInsightLine
import kr.co.reevl.ontheway.ui.ds.comp.AreaPeriodCell
import kr.co.reevl.ontheway.ui.ds.comp.AreaSummarySpec
import kr.co.reevl.ontheway.format.ReevlFormat
import kr.co.reevl.ontheway.ui.ds.comp.AreaMapSpec
import kr.co.reevl.ontheway.ui.ds.comp.AreaTileSpec
import kr.co.reevl.ontheway.ui.ds.comp.BannerKind
import kr.co.reevl.ontheway.ui.ds.comp.BannerSpec
import kr.co.reevl.ontheway.ui.ds.comp.Btn
import kr.co.reevl.ontheway.ui.ds.comp.ChipSpec
import kr.co.reevl.ontheway.ui.ds.comp.IconSpec
import kr.co.reevl.ontheway.ui.ds.comp.Leading
import kr.co.reevl.ontheway.ui.ds.comp.MessageSpec
import kr.co.reevl.ontheway.ui.ds.comp.RowSpec
import kr.co.reevl.ontheway.ui.ds.comp.TabsSpec
import kr.co.reevl.ontheway.ui.ds.comp.Tone
import kr.co.reevl.ontheway.ui.ds.comp.TopBarSpec
import kr.co.reevl.ontheway.ui.ds.comp.sideChip
import kr.co.reevl.ontheway.ui.ds.tmpl.AreaTemplate
import kr.co.reevl.ontheway.ui.ds.tmpl.ToastSlot
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.state.AreaEvents
import kr.co.reevl.ontheway.ui.state.AreaUi

// 동네 (area-context-car-spec §3) — data → AreaTemplate slots. No layout here (ScreensGateTest).

private fun md(d: String?) = d?.takeIf { it.length >= 10 }?.let { it.substring(5, 7) + "." + it.substring(8, 10) }

private fun sideOf(n: AreaNeighbor) = n.relDeg?.let { NearbyEngine.sideOf(it.toDouble()) } ?: NearbyEngine.Side.UNKNOWN

/** Row number = the period on the map only (one number per row, R3); the 구 fallback is said in words. */
private fun periodLine(n: AreaNeighbor, p: Period) = "${p.label} ${AreaLogic.pct(AreaLogic.retOf(n.ret, p))}" + if (n.level != "dong") " · 구 값" else ""

@Composable
fun AreaScreen(ui: AreaUi, ev: AreaEvents, toast: ToastSlot?) {
    val r = LocalRoles.current
    val d: AreaResponse? = ui.data
    val a = d?.area
    val tabs = TabsSpec(AreaUi.TABS, ui.tab, ev.onTab)
    val top = TopBarSpec("동네", null, listOf(IconSpec(Lucide.RefreshCw, "새로고침", ev.onRefresh, tag = "refresh")))
    val banner = ui.notice?.let { BannerSpec(BannerKind.Error, it.text, Btn("다시", ev.onRefresh, tag = "retry")) }
    val empty = when {
        ui.loading && d == null -> null
        ui.noLocation -> MessageSpec(null, "위치를 확인하지 못했어요", primary = Btn("다시", ev.onRefresh, tag = "retry"), tag = "area_noloc")
        d != null && a == null -> MessageSpec(null, "이 근처에는 단지가 없어요", tag = "area_none")
        d == null && ui.notice == null && !ui.loading -> MessageSpec(null, "동네 정보를 불러오지 못했어요", primary = Btn("다시", ev.onRefresh, tag = "retry"))
        else -> null
    }
    val indexDay = md(d?.asOf?.index)
    val y = a?.ret?.y1
    val hero = a?.let {
        AreaHeroSpec(region = AreaLogic.regionLine(it), place = AreaLogic.placeName(it), value = "1년 ${AreaLogic.pct(y)}",
            dir = if (y == null) 0 else y.compareTo(0.0),
            periods = "1개월 ${AreaLogic.pct(it.ret.m1)} · 3개월 ${AreaLogic.pct(it.ret.m3)} · ${it.gu.split(' ').last()} 1년 ${AreaLogic.pct(it.guRet.y1)}")
    }
    var footer: String? = null
    var map: AreaMapSpec? = null
    var heroShown = hero
    val rows: List<RowSpec> = if (a == null) emptyList() else when (ui.tab) {
        0 -> { // 요약
            footer = listOfNotNull("리블 매매지수 · 실거래 신고", indexDay?.let { "$it 기준" }, "가장 가까운 단지의 동").joinToString(" · ")
            listOfNotNull(
                d.notable.firstOrNull()?.let { n -> RowSpec("top_${n.cid}", n.name, AreaLogic.notableLine(n), Leading.Avatar(n.name, n.cid, null),
                    chip = if (n.reason == "new_high") ChipSpec("오늘 최고가", Tone.Warn) else ChipSpec("주목", Tone.Sub), chevron = true,
                    onClick = { ev.onOpenNotable(n.cid) }, wrap = true, tag = "area_top") },
                d.news.firstOrNull()?.let { n -> RowSpec("news0", n.title, AreaLogic.newsLine(n), Leading.Glyph(AreaIcons.Newspaper),
                    chevron = true, onClick = { ev.onTab(2) }, tag = "area_news0") },
            )
        }
        1 -> { // 히트맵
            heroShown = null
            val dark = r.dark
            val legend = listOf(AreaLogic.heat(0.0, dark), AreaLogic.heat(0.5, dark), AreaLogic.heat(1.0, dark)).map { Color(it.bg) }
            val grid = ui.grid
            map = grid?.let { g ->
                AreaMapSpec(
                    tiles = g.cells.map { c ->
                        val v = AreaLogic.retOf(c.n.ret, ui.period)
                        val h = AreaLogic.heatOf(v, ui.period, dark)
                        AreaTileSpec(c.n.key, c.n.dong, AreaLogic.pct(v), h?.let { Color(it.bg) } ?: r.chip, h?.let { Color(it.fg) } ?: r.sub,
                            c.col, c.row, c.n.current, c.n.level != "dong")
                    },
                    radius = g.radius, headingUp = g.headingUp, selectedKey = ui.selectedKey, onSelect = ev.onSelectDong,
                    periods = Period.entries.map { it.label }, period = ui.period.ordinal, onPeriod = { ev.onPeriod(Period.entries[it]) },
                    legendMin = "−${(ui.period.cap * 100).toInt()}%", legendMax = "+${(ui.period.cap * 100).toInt()}%", legend = legend,
                    basis = (if (g.headingUp) "진행 방향이 위" else "북쪽이 위") + " · 칸 하나 약 700m · 옅은 칸은 동 지수가 없어 구 값",
                )
            }
            footer = listOfNotNull("리블 매매지수", indexDay?.let { "$it 기준" }).joinToString(" · ")
            val sel = d.neighbors.firstOrNull { it.key == ui.selectedKey }
            val others = d.neighbors.filter { it.key != sel?.key }
                .sortedByDescending { AreaLogic.retOf(it.ret, ui.period) ?: Double.NEGATIVE_INFINITY }
            (listOfNotNull(sel) + others).map { n ->
                RowSpec(n.key, n.dong + if (n.current) " · 지금 여기" else "", periodLine(n, ui.period),
                    chip = if (n.current) null else sideChip(sideOf(n)), onClick = { ev.onSelectDong(n.key) }, tag = "dong_${n.dong}")
            }
        }
        2 -> { // 뉴스 — 제목 · 언론사 · 날짜만 (no body, no link)
            footer = listOfNotNull("뉴스 제목 · 언론사 제공", md(d.asOf.news?.replace('.', '-'))?.let { "$it 수집" }).joinToString(" · ")
            d.news.map { n -> RowSpec("news_${n.title.hashCode()}", n.title, AreaLogic.newsLine(n), Leading.Glyph(AreaIcons.Newspaper), inert = true, tag = "news_${n.scope}") }
        }
        else -> { // 주목 단지
            footer = listOfNotNull("1년 등락 = 단지 실거래 기준", md(d.asOf.notable)?.let { "최고가 $it" }).joinToString(" · ")
            d.notable.map { n ->
                RowSpec(n.cid, n.name, AreaLogic.notableLine(n), Leading.Avatar(n.name, n.cid, null),
                    chip = if (n.reason == "new_high") ChipSpec("오늘 최고가", Tone.Warn) else null, chevron = true,
                    onClick = { ev.onOpenNotable(n.cid) }, tag = "notable_${n.name}")
            }
        }
    }
    val tabEmpty = when {
        empty != null -> empty
        a == null -> null
        ui.tab == 2 && rows.isEmpty() -> MessageSpec(null, "이 동네 소식이 없어요", tag = "news_empty")
        ui.tab == 3 && rows.isEmpty() -> MessageSpec(null, "눈에 띄는 단지가 없어요", tag = "notable_empty")
        else -> null
    }
    // 요약 (2026-11-14 redesign): the same data as a glanceable summary — see AreaSummary.
    val summary = if (ui.tab == 0 && a != null) {
        val per = listOf("1개월" to a.ret.m1, "3개월" to a.ret.m3, "1년" to a.ret.y1).map { (l, v) -> AreaPeriodCell(l, AreaLogic.pct(v), AreaLogic.pct(v).let { p -> if (p.startsWith("+")) 1 else if (p.startsWith("−")) -1 else 0 }) }
        val guName = a.gu.split(' ').last()
        val placeName = AreaLogic.placeName(a)
        val facts = listOfNotNull(
            a.complexes?.let { AreaFact("단지", "${ReevlFormat.num(it.toDouble())}곳", "complexes") },
            a.households?.let { AreaFact("세대", "${ReevlFormat.num(it.toDouble())}세대", "households") },
            a.trades12?.let { AreaFact("최근 1년 거래", "${ReevlFormat.num(it.toDouble())}건", "trades") },
            a.stations.firstOrNull()?.let { AreaFact("가까운 역", it, "station") },
        )
        val ins = d.insights.take(2).map { i ->
            AreaInsightLine(when (i.kind) {
                "rank", "vs_gu" -> if ((y ?: 0.0) >= 0) AreaIcons.TrendingUp else AreaIcons.TrendingDown
                "new_high" -> Lucide.ArrowUp
                else -> AreaIcons.ChartColumn
            }, i.text, i.kind)
        }
        AreaSummarySpec(region = AreaLogic.regionLine(a), place = placeName, headLabel = "최근 1년 매매지수", headValue = AreaLogic.pct(y),
            headDir = if (y == null) 0 else y.compareTo(0.0), periods = per,
            compare = if (a.level == "dong") AreaCompare(placeName, a.ret.y1, AreaLogic.pct(a.ret.y1), guName, a.guRet.y1, AreaLogic.pct(a.guRet.y1)) else null,
            facts = facts, insights = ins)
    } else null
    AreaTemplate(top, banner, tabs, if (ui.tab == 0 && summary == null) heroShown else null, map, rows, tabEmpty, ui.loading, footer,
        onRow = { row -> row.onClick?.invoke() }, toast = toast, summary = summary)
}
