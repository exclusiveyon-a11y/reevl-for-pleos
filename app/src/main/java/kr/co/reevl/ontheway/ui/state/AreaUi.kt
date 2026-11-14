package kr.co.reevl.ontheway.ui.state

import kr.co.reevl.ontheway.AppViewModel
import kr.co.reevl.ontheway.area.AreaLogic
import kr.co.reevl.ontheway.data.AreaResponse

// 지나가는 동네 (area-context-car-spec §3): one screen, four tabs — 요약 · 히트맵 · 뉴스 · 주목 단지.

data class AreaUi(
    val tab: Int,
    val period: AreaLogic.Period,
    val data: AreaResponse?,
    val grid: AreaLogic.Grid?,
    /** Selected 동 on the map (key); default = the current 동. */
    val selectedKey: String?,
    val loading: Boolean,
    val noLocation: Boolean,
    val notice: Notice?,
) {
    companion object { val TABS = listOf("요약", "히트맵", "뉴스", "주목 단지") }
}

data class AreaEvents(val onTab: (Int) -> Unit, val onPeriod: (AreaLogic.Period) -> Unit, val onSelectDong: (String) -> Unit,
                      val onOpenNotable: (String) -> Unit, val onRefresh: () -> Unit, val onBack: () -> Unit)

fun AppViewModel.areaUi(): AreaUi {
    val d = areaData
    return AreaUi(
        tab = areaTab, period = areaPeriod, data = d,
        grid = d?.neighbors?.takeIf { it.isNotEmpty() }?.let { AreaLogic.grid(it) },
        selectedKey = areaSelected ?: d?.neighbors?.firstOrNull { it.current }?.key,
        loading = areaLoading, noLocation = areaNoLocation,
        // Same short words as 주변/검색 (QA round 2: 동네 alone showed the long D2 sentence `인터넷 연결을 확인해 주세요.`).
        notice = areaMessage?.let { m -> Notice(if (m.startsWith(kr.co.reevl.ontheway.data.UserMessages.OFFLINE)) NoticeKind.Offline else NoticeKind.Error,
            Notices.short(m), retry = true) },
    )
}

fun AppViewModel.areaEvents(onRefresh: () -> Unit) = AreaEvents(
    onTab = ::selectAreaTab, onPeriod = ::selectAreaPeriod, onSelectDong = ::selectAreaDong,
    onOpenNotable = ::openAreaNotable, onRefresh = onRefresh, onBack = ::closeArea,
)
