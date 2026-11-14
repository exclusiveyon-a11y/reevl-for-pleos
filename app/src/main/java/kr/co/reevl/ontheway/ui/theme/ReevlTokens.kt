// 어댑터 — 값은 리블 디자인 토큰 생성물(kr.co.reevl.design, scripts/sync_reevl_tokens.sh로 reevl/design-tokens에서 복사)에서 읽는다.
// 여기에 색·글자 숫자를 새로 적지 않는다. 예외: AI 움직임 주기·빛 겹(토큰에 아직 없음 — 웹 globals.css 값), 아바타 이니셜 색(lib/rt.ts PAL).
// 예전 생성기(tools/gen_reevl_tokens.mjs, reevl globals.css를 읽음)는 없앴다 — reevl docs/open/DS-QA-SYSTEM.md "차량 앱은 dist 복사로".
// 차량 크기(dp·sp)는 kr.co.reevl.design.car.ReevlCarTheme(생성물) → ds/token/CarTypeV2 · CarSpace.
package kr.co.reevl.ontheway.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kr.co.reevl.design.ReevlColors
import kr.co.reevl.design.ReevlPalette
import kr.co.reevl.design.ReevlType
import kotlin.math.roundToInt

/** 웹 글자 역할 하나(globals.css `.t-*`). 크기는 웹 모바일 px — 차량 sp는 CarTypeV2가 정한다. */
data class WebRole(val mobilePx: Float, val lineHeight: Float, val letterSpacingEm: Float, val tabularNums: Boolean) {
    companion object {
        /** 생성물 [ReevlType] 한 역할 → 행간 배수 · 자간 · tnum. */
        fun of(t: TextStyle) = WebRole(
            mobilePx = t.fontSize.value,
            lineHeight = t.lineHeight.value / t.fontSize.value,
            letterSpacingEm = t.letterSpacing.value,
            tabularNums = t.fontFeatureSettings == "tnum",
        )
    }
}

/** AI 빛 한 겹(`.ai-soft-glow`): box-shadow 0 0 blur spread color-mix(--ai alpha, transparent). */
data class GlowLayer(val blurPx: Int, val spreadPx: Int, val alpha: Float)

/** 웹 `color-mix(in srgb, a p%, b)` — 8비트로 반올림(브라우저 계산값과 같게). */
internal fun srgbMix(a: Color, b: Color, p: Float): Color {
    fun ch(x: Float, y: Float) = ((x * p + y * (1 - p)) * 255f).roundToInt() / 255f
    return Color(ch(a.red, b.red), ch(a.green, b.green), ch(a.blue, b.blue))
}

/** 한 테마의 웹 색 이름(차량 코드가 쓰는 이름) → 생성물 [ReevlColors]. */
class WebColors internal constructor(t: ReevlColors) {
    val app = t.app
    val bg = t.bg
    val surface = t.surface
    val hover = t.hover
    val chip = t.chip
    val line = t.line
    val lineStrong = t.lineStrong
    val ink = t.ink
    val ink2 = t.ink2
    val sub = t.sub
    val dim = t.dim
    val brand = t.brand
    val brandStrong = t.brandStrong
    /** --brand-fill: 흰 글자를 얹는 주 버튼 면(낮·밤 같은 값, 흰 글자 4.88:1). */
    val brandFill = t.brandFill
    val brandSoft = t.brandSoft
    val onBrand = t.onBrand
    /** --brand-ink: 브랜드 파랑 글자(낮 #1B64DA). */
    val brandInk = t.brandInk
    val up = t.up
    val upSoft = t.upSoft
    val upInk = t.upInk
    val down = t.down
    val downSoft = t.downSoft
    val downInk = t.downInk
    val live = t.live
    val liveSoft = t.liveSoft
    val pos = t.pos
    val posSoft = t.posSoft
    val posStrong = t.posStrong
    val warn = t.warn
    val warnSoft = t.warnSoft
    val warnStrong = t.warnStrong
    val crown = t.crown
    val crownSoft = t.crownSoft
    /** --ai = var(--brand) */
    val ai = t.brand
    /** --ai-ink = color-mix(in srgb, var(--ink) 30%, var(--ai)) */
    val aiInk = srgbMix(t.ink, t.brand, 0.3f)
}

object ReevlWeb {
    val Light = WebColors(ReevlPalette.Light)
    val Dark = WebColors(ReevlPalette.Dark)

    /** AI 시각 언어 — globals.css:296–306, .ai-soft-glow globals.css:486–491 */
    object Ai {
        /** --ai-flow (.ai-live-grad · .ai-text-grad 흐름 주기) */
        const val FLOW_MS = 5000
        /** --ai-flow-2 (강조 표면 흐름 주기) */
        const val FLOW2_MS = 4500
        /** --ai-glow (.ai-glow 맥동 주기) */
        const val GLOW_MS = 2600
        /** .ai-soft-glow 3겹 */
        val softGlow = listOf(GlowLayer(14, -8, 0.3f), GlowLayer(40, -14, 0.22f), GlowLayer(80, -26, 0.16f))
    }

    /** 글자 역할 — 생성물 [ReevlType](웹 모바일 크기 · 행간 · 자간 · tnum). */
    object Type {
        val display = WebRole.of(ReevlType.display)
        val titleLg = WebRole.of(ReevlType.titleLg)
        val headline = WebRole.of(ReevlType.headline)
        val title = WebRole.of(ReevlType.title)
        val bodyLg = WebRole.of(ReevlType.bodyLg)
        val body = WebRole.of(ReevlType.body)
        val valueXl = WebRole.of(ReevlType.valXl)
        val value2xl = WebRole.of(ReevlType.val2xl)
        val valueLg = WebRole.of(ReevlType.valLg)
        val value = WebRole.of(ReevlType.`val`)
        val valueSm = WebRole.of(ReevlType.valSm)
        val label = WebRole.of(ReevlType.label)
        val labelSm = WebRole.of(ReevlType.labelSm)
        val cap = WebRole.of(ReevlType.cap)
        val eyebrow = WebRole.of(ReevlType.eyebrow)
        val micro = WebRole.of(ReevlType.micro)
        /** --lh-bold 굵기별 행간 보정(globals.css — 토큰에 아직 없음) */
        const val LH_BOLD_MEDIUM = 0.008f
        const val LH_BOLD_SEMIBOLD = 0.015f
        const val LH_BOLD_BOLD = 0.025f
        const val LH_BOLD_EXTRABOLD = 0.035f
    }

    /** 아바타 이니셜 색 — lib/rt.ts:3151 `const PAL` (avatarColor가 cid 해시로 고른다) */
    val avatarPalette = kr.co.reevl.design.car.ReevlCarTheme.Brand.avatar  // tokens.json car.color.avatar
}
