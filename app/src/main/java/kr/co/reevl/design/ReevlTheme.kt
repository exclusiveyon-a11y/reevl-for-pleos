// 생성물 — reevl design-tokens에서 복사, 직접 고치지 말 것 (scripts/sync_reevl_tokens.sh)
// 생성 파일 — 손으로 고치지 말 것. 원본: design-tokens/tokens.json(digest 7de59f144dd2) ← frontend/scripts/ds_tokens_export.mjs
// 다시 만들기: cd frontend && npm run ds:tokens
// Jetpack Compose 테마 — 리블 웹과 같은 숫자(px = dp·sp 1:1, 글자는 웹 모바일 크기).
package kr.co.reevl.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

const val REEVL_TOKENS_DIGEST = "7de59f144dd2"

@Immutable
data class ReevlColors(
    val app: Color,
    val bg: Color,
    val surface: Color,
    val hover: Color,
    val chip: Color,
    val line: Color,
    val lineStrong: Color,
    val switchOff: Color,
    val tileLine: Color,
    val ink: Color,
    val ink2: Color,
    val sub: Color,
    val dim: Color,
    val brand: Color,
    val brandStrong: Color,
    val brandFill: Color,
    val brandSoft: Color,
    val onBrand: Color,
    val brandInk: Color,
    val up: Color,
    val upSoft: Color,
    val upInk: Color,
    val upFill: Color,
    val down: Color,
    val downSoft: Color,
    val downInk: Color,
    val live: Color,
    val liveSoft: Color,
    val liveInk: Color,
    val pos: Color,
    val posSoft: Color,
    val posStrong: Color,
    val posFill: Color,
    val warn: Color,
    val warnSoft: Color,
    val warnStrong: Color,
    val warnFill: Color,
    val crown: Color,
    val crownSoft: Color,
    val crownInk: Color,
    val fav: Color,
    val vizAi: Color,
)

object ReevlPalette {
    val Light = ReevlColors(
        app = Color(0xFFF7F8FA),
        bg = Color(0xFFFFFFFF),
        surface = Color(0xFFF7F8FA),
        hover = Color(0xFFF4F5F7),
        chip = Color(0xFFF2F4F6),
        line = Color(0xFFEFF1F3),
        lineStrong = Color(0xFFE3E6EA),
        switchOff = Color(0xFF8E9094),
        tileLine = Color(0x29000000),
        ink = Color(0xFF191F28),
        ink2 = Color(0xFF4E5968),
        sub = Color(0xFF616C7A),
        dim = Color(0xFF686F7B),
        brand = Color(0xFF1A69E0),
        brandStrong = Color(0xFF1858C4),
        brandFill = Color(0xFF1D6CE3),
        brandSoft = Color(0xFFEAF3FF),
        onBrand = Color(0xFFFFFFFF),
        brandInk = Color(0xFF1B64DA),
        up = Color(0xFFF04452),
        upSoft = Color(0xFFFEEBEC),
        upInk = Color(0xFFD22030),
        upFill = Color(0xFFDF3144),
        down = Color(0xFF3182F6),
        downSoft = Color(0xFFEAF3FF),
        downInk = Color(0xFF1B64DA),
        live = Color(0xFF3182F6),
        liveSoft = Color(0xFFEAF3FF),
        liveInk = Color(0xFF1B64DA),
        pos = Color(0xFF12B886),
        posSoft = Color(0xFFE6F7F1),
        posStrong = Color(0xFF0B7A5A),
        posFill = Color(0xFF008859),
        warn = Color(0xFFE8A33D),
        warnSoft = Color(0xFFFBF1E0),
        warnStrong = Color(0xFFA15C00),
        warnFill = Color(0xFFA86700),
        crown = Color(0xFFB07D0D),
        crownSoft = Color(0xFFFBF1D3),
        crownInk = Color(0xFF8A6000),
        fav = Color(0xFFC58200),
        vizAi = Color(0xFF7B61FF),
    )
    val Dark = ReevlColors(
        app = Color(0xFF0E0F11),
        bg = Color(0xFF16171A),
        surface = Color(0xFF0E0F11),
        hover = Color(0xFF232529),
        chip = Color(0xFF232529),
        line = Color(0xFF282A2D),
        lineStrong = Color(0xFF333640),
        switchOff = Color(0xFF60646F),
        tileLine = Color(0x21FFFFFF),
        ink = Color(0xFFEDEEF1),
        ink2 = Color(0xFFC7CCD4),
        sub = Color(0xFFAAB1BC),
        dim = Color(0xFF848C97),
        brand = Color(0xFF4D9FFF),
        brandStrong = Color(0xFF1858C4),
        brandFill = Color(0xFF1D6CE3),
        brandSoft = Color(0xFF16233A),
        onBrand = Color(0xFFFFFFFF),
        brandInk = Color(0xFF4D9FFF),
        up = Color(0xFFFF5C6C),
        upSoft = Color(0xFF2B1A1F),
        upInk = Color(0xFFFF5C6C),
        upFill = Color(0xFFDA384F),
        down = Color(0xFF4D9FFF),
        downSoft = Color(0xFF14243A),
        downInk = Color(0xFF4D9FFF),
        live = Color(0xFF4D9FFF),
        liveSoft = Color(0xFF16233A),
        liveInk = Color(0xFF4D9FFF),
        pos = Color(0xFF2DD4A0),
        posSoft = Color(0xFF122A24),
        posStrong = Color(0xFF34D8A6),
        posFill = Color(0xFF1B8665),
        warn = Color(0xFFF0B254),
        warnSoft = Color(0xFF2E2413),
        warnStrong = Color(0xFFF0B254),
        warnFill = Color(0xFFA46A00),
        crown = Color(0xFFE6B53F),
        crownSoft = Color(0xFF2B2410),
        crownInk = Color(0xFFE6B53F),
        fav = Color(0xFFFFB900),
        vizAi = Color(0xFF7B61FF),
    )
}

/** 리블 그라디언트(SNS·AI 강조) — 테마와 무관하게 같은 값. */
object ReevlBrandGradient {
    val accentFrom = Color(0xFF5BB7FF)
    val accentTo = Color(0xFF177CF3)
    val bgFrom = Color(0xFFFFFFFF)
    val bgMid = Color(0xFFDEF2FF)
    val bgTo = Color(0xFFCDEBFF)
    val glow = Color(0xFF1A73D0)
    val ink = Color(0xFF111111)
    val sub = Color(0xFF4E4E52)
    val muted = Color(0xFF8C8C94)
}

/** 데이터 시각화 고정색 — 뜻이 아니라 구분이 목적(테마 무관). */
object ReevlViz {
    val ai = Color(0xFF7B61FF)
    val area = listOf(Color(0xFF3F92F0), Color(0xFF3182F6), Color(0xFFF04452))
    val compare = listOf(Color(0xFF3182F6), Color(0xFF00A474), Color(0xFFCB7F00))
}

/** 글자 역할 — 웹 t-* 와 같은 이름. 글꼴(Pretendard Variable)은 앱이 FontFamily로 덧입힌다. 굵기는 역할이 정하지 않는다. */
object ReevlType {
    /** t-val-xl · 웹 PC 30 · 모바일 32 */
    val valXl = TextStyle(fontSize = 32.sp, lineHeight = 35.84.sp, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum")
    /** t-val-2xl · 웹 PC 40 · 모바일 34 */
    val val2xl = TextStyle(fontSize = 34.sp, lineHeight = 36.72.sp, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum")
    /** t-stat · 웹 PC {"min":36,"fluidVw":4,"max":44} · 모바일 {"min":36,"fluidVw":4,"max":44} */
    val stat = TextStyle(fontSize = 36.sp, lineHeight = 37.44.sp, letterSpacing = (-0.025).em, fontFeatureSettings = "tnum")
    /** t-stat-lg · 웹 PC {"min":44,"fluidVw":6,"max":66} · 모바일 {"min":44,"fluidVw":6,"max":66} */
    val statLg = TextStyle(fontSize = 44.sp, lineHeight = 45.76.sp, letterSpacing = (-0.025).em, fontFeatureSettings = "tnum")
    /** t-stat-xl · 웹 PC {"min":54,"fluidVw":9,"max":104} · 모바일 {"min":54,"fluidVw":9,"max":104} */
    val statXl = TextStyle(fontSize = 54.sp, lineHeight = 56.16.sp, letterSpacing = (-0.025).em, fontFeatureSettings = "tnum")
    /** t-hero · 웹 PC {"min":32,"fluidVw":4.5,"max":46} · 모바일 {"min":32,"fluidVw":4.5,"max":46} */
    val hero = TextStyle(fontSize = 32.sp, lineHeight = 39.04.sp, letterSpacing = (-0.02).em)
    /** t-display · 웹 PC 30 · 모바일 28 */
    val display = TextStyle(fontSize = 28.sp, lineHeight = 31.92.sp, letterSpacing = (-0.02).em)
    /** t-title-lg · 웹 PC 22 · 모바일 20 */
    val titleLg = TextStyle(fontSize = 20.sp, lineHeight = 24.4.sp, letterSpacing = (-0.02).em)
    /** t-headline · 웹 PC 17 · 모바일 18 */
    val headline = TextStyle(fontSize = 18.sp, lineHeight = 23.04.sp, letterSpacing = (-0.015).em)
    /** t-title · 웹 PC 15 · 모바일 17 */
    val title = TextStyle(fontSize = 17.sp, lineHeight = 22.1.sp, letterSpacing = (-0.01).em)
    /** t-body-lg · 웹 PC 15 · 모바일 17 */
    val bodyLg = TextStyle(fontSize = 17.sp, lineHeight = 28.22.sp, letterSpacing = (-0.01).em)
    /** t-body · 웹 PC 14 · 모바일 16 */
    val body = TextStyle(fontSize = 16.sp, lineHeight = 25.28.sp, letterSpacing = (-0.005).em)
    /** t-val-lg · 웹 PC 18 · 모바일 19 */
    val valLg = TextStyle(fontSize = 19.sp, lineHeight = 22.8.sp, letterSpacing = (-0.015).em, fontFeatureSettings = "tnum")
    /** t-val · 웹 PC 15 · 모바일 16 */
    val `val` = TextStyle(fontSize = 16.sp, lineHeight = 19.2.sp, letterSpacing = (-0.01).em, fontFeatureSettings = "tnum")
    /** t-val-sm · 웹 PC 13 · 모바일 14.5 */
    val valSm = TextStyle(fontSize = 14.5.sp, lineHeight = 17.4.sp, letterSpacing = (0).em, fontFeatureSettings = "tnum")
    /** t-label · 웹 PC 13 · 모바일 14.5 */
    val label = TextStyle(fontSize = 14.5.sp, lineHeight = 18.85.sp, letterSpacing = (0).em)
    /** t-label-sm · 웹 PC 12 · 모바일 13.5 */
    val labelSm = TextStyle(fontSize = 13.5.sp, lineHeight = 17.55.sp, letterSpacing = (0.005).em)
    /** t-cap · 웹 PC 12.5 · 모바일 14 */
    val cap = TextStyle(fontSize = 14.sp, lineHeight = 18.2.sp, letterSpacing = (0).em)
    /** t-eyebrow · 웹 PC 12 · 모바일 13 */
    val eyebrow = TextStyle(fontSize = 13.sp, lineHeight = 16.38.sp, letterSpacing = (0.02).em)
    /** t-micro · 웹 PC 11 · 모바일 12.5 */
    val micro = TextStyle(fontSize = 12.5.sp, lineHeight = 15.sp, letterSpacing = (0.01).em)
}

object ReevlRadius {
    val sm = 6.dp
    val md = 8.dp
    val lg = 10.dp
    val xl = 12.dp
    val xl2 = 16.dp
    val xl3 = 24.dp
    val full = 9999.dp
}

/** 간격 — 역할로 고른다(웹 lib/ds.ts GAP). */
object ReevlSpace {
    val hair = 2.dp
    val snug = 4.dp
    val tight = 6.dp
    val item = 8.dp
    val card = 10.dp
    val block = 12.dp
    val row = 12.dp
    val group = 16.dp
    val section = 24.dp
}

object ReevlMotion {
    val easeOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val easeIn = CubicBezierEasing(0.55f, 0f, 0.85f, 0.4f)
    val easeInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    val easeDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    const val PRESS_SCALE = 0.975f
    const val PRESS_MILLIS = 120
}

/** 겹침 층 — Modifier.zIndex에 쓴다(웹 lib/z.ts ZV). */
object ReevlZ {
    const val stickyName = 1f
    const val panelSticky = 20f
    const val chrome = 30f
    const val stickyHeadName = 30f
    const val rail = 40f
    const val stickyHead = 45f
    const val dock = 46f
    const val ticker = 47f
    const val noticebar = 50f
    const val pageHeader = 50f
    const val hint = 50f
    const val modal = 60f
    const val sheet = 61f
    const val aiSheet = 70f
    const val popover = 80f
    const val fullDock = 85f
    const val screenerSheet = 90f
    const val fullscreen = 95f
    const val tooltip = 120f
    const val maintenance = 100000f
}

/** 창 폭 경계(dp) — 폰 = md 미만. */
object ReevlBreakpoint {
    val sm = 640.dp
    val md = 768.dp
    val lg = 1024.dp
    val xl = 1280.dp
    val xl2 = 1536.dp
}

/** 떠 있는 판 그림자(웹 --float-shadow) — 겹 그대로. Compose는 가장 큰 겹의 blur/2를 elevation으로 근사한다. */
data class ReevlShadowLayer(val x: Float, val y: Float, val blur: Float, val spread: Float, val color: Color)
object ReevlElevation {
    val floatLight = listOf(
        ReevlShadowLayer(0f, 1f, 2f, 0f, Color(0x0A000000)),
        ReevlShadowLayer(0f, 4f, 10f, -2f, Color(0x0D000000)),
        ReevlShadowLayer(0f, 14f, 32f, -8f, Color(0x12000000)),
        ReevlShadowLayer(0f, 36f, 80f, -20f, Color(0x17000000)),
    )
    val floatDark = listOf(
        ReevlShadowLayer(0f, 0f, 0f, 1f, Color(0x0FFFFFFF)),
        ReevlShadowLayer(0f, 1f, 2f, 0f, Color(0x99000000)),
        ReevlShadowLayer(0f, 6f, 14f, -3f, Color(0x8C000000)),
        ReevlShadowLayer(0f, 18f, 40f, -10f, Color(0x99000000)),
        ReevlShadowLayer(0f, 44f, 96f, -24f, Color(0xB3000000)),
    )
}
