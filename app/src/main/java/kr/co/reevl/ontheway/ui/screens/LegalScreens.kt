package kr.co.reevl.ontheway.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kr.co.reevl.ontheway.legal.Consent
import kr.co.reevl.ontheway.legal.OssLicenses
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.ds.comp.Actions
import kr.co.reevl.ontheway.ui.ds.comp.Btn
import kr.co.reevl.ontheway.ui.ds.comp.Leading
import kr.co.reevl.ontheway.ui.ds.comp.RowSpec
import kr.co.reevl.ontheway.ui.ds.comp.TopBarSpec
import kr.co.reevl.ontheway.ui.ds.tmpl.LinkQrSpec
import kr.co.reevl.ontheway.ui.ds.tmpl.ListTemplate
import kr.co.reevl.ontheway.ui.ds.tmpl.NoticeTemplate
import kr.co.reevl.ontheway.ui.ds.tmpl.TextTemplate

/**
 * 개인정보·위치 안내 (legal/Legal.kt). First run: [onAccept] / [onExit] — only the accept button counts as consent (review-user-
 * privacy-security: "동의 버튼을 누르는 행동 외의 행동을 동의로 간주해서는 안 됩니다"); back = 나가기. From 설정 ([onAccept] null):
 * the same notice read again, with 동의 철회 ([onWithdraw]). Parked only — the driving lock covers it like every screen.
 */
@Composable
fun ConsentScreen(onAccept: (() -> Unit)?, onExit: () -> Unit, onWithdraw: (() -> Unit)? = null) {
    val first = onAccept != null
    NoticeTemplate(if (first) "screen_consent" else "screen_privacy",
        if (first) TopBarSpec() else TopBarSpec("개인정보 안내", onExit), Consent.TITLE,
        rows = Consent.ITEMS.mapIndexed { i, it -> RowSpec("c$i", it.title, it.body, inert = true, wrap = true, tag = "consent_item_$i") },
        link = LinkQrSpec(Consent.PRIVACY_URL, "개인정보 처리방침", Consent.PRIVACY_TEXT,
            listOfNotNull(Consent.LOCATION_TEXT, Consent.LOCATION_TERMS_URL?.let { "위치기반서비스 약관 · ${it.removePrefix("https://")}" }).joinToString("\n")),
        actions = if (onAccept != null) Actions(Btn(Consent.ACCEPT, onAccept, tag = "consent_accept"), Btn(Consent.EXIT, onExit, tag = "consent_exit"))
            else Actions(onWithdraw?.let { Btn("동의 철회", it, tag = "consent_withdraw") }, Btn("닫기", onExit, tag = "consent_close")))
}

/** 설정 › 오픈소스 라이선스: one row per library group; a tap shows the licence text that ships in assets/licenses. */
@Composable
fun LicensesScreen(open: String?, onOpen: (String) -> Unit, onBack: () -> Unit) {
    if (open != null) {
        val ctx = LocalContext.current
        val text = remember(open) { runCatching { ctx.assets.open(open).bufferedReader().readText() }.getOrDefault("") }
        TextTemplate("screen_license_text", TopBarSpec(OssLicenses.LIBS.firstOrNull { it.file == open }?.license ?: "라이선스", onBack), text)
        return
    }
    ListTemplate("screen_licenses", TopBarSpec("오픈소스 라이선스", onBack), null,
        rows = OssLicenses.LIBS.mapIndexed { i, l -> RowSpec(l.file + i, l.name, l.license, Leading.Glyph(Lucide.Info), chevron = true, wrap = true,
            onClick = { onOpen(l.file) }, tag = "oss_$i") } +
            RowSpec("map", OssLicenses.MAP_DATA, inert = true, wrap = true, tag = "oss_map"),
        selectedKey = null, onSelect = {}, onOpen = {}, footer = null)
}
