// ★생성 파일 — 손으로 고치지 말 것. 원본: reevl/frontend/app/globals.css · reevl/frontend/lib/rt.ts
// 재생성: node tools/gen_reevl_tokens.mjs   (생성 시각 2026-10-26T12:57:54.546Z)
// 이 파일은 리블 웹의 값을 **그대로** 옮긴다. 차량 크기(dp)로 바꾸는 규칙은 CarType.kt / car-ui-spec.md §2.
package kr.co.reevl.ontheway.ui.theme

import androidx.compose.ui.graphics.Color

/** 웹 글자 역할 하나(globals.css `.t-*` + `:where(.t-*)`). 크기는 웹 px — 차량 dp는 CarType이 정한다. */
data class WebRole(val pcPx: Float, val mobilePx: Float, val lineHeight: Float, val letterSpacingEm: Float, val tabularNums: Boolean)

/** AI 빛 한 겹(`.ai-soft-glow`): box-shadow 0 0 blur spread color-mix(--ai alpha, transparent). */
data class GlowLayer(val blurPx: Int, val spreadPx: Int, val alpha: Float)

object ReevlWeb {
    /** 라이트 — globals.css:14–136 `:root,[data-theme=light]` */
    object Light {
        /** --app · globals.css:114 */
        val app = Color(0xFFF7F8FA)
        /** --bg · globals.css:114 */
        val bg = Color(0xFFFFFFFF)
        /** --surface = var(--app) · globals.css:114 */
        val surface = Color(0xFFF7F8FA)
        /** --hover · globals.css:114 */
        val hover = Color(0xFFF4F5F7)
        /** --chip · globals.css:114 */
        val chip = Color(0xFFF2F4F6)
        /** --line · globals.css:115 */
        val line = Color(0xFFEFF1F3)
        /** --line-strong · globals.css:115 */
        val lineStrong = Color(0xFFE3E6EA)
        /** --ink · globals.css:121 */
        val ink = Color(0xFF191F28)
        /** --ink2 · globals.css:121 */
        val ink2 = Color(0xFF4E5968)
        /** --sub · globals.css:121 */
        val sub = Color(0xFF616C7A)
        /** --dim · globals.css:121 */
        val dim = Color(0xFF6D7581)
        /** --brand · globals.css:122 */
        val brand = Color(0xFF3182F6)
        /** --brand-strong · globals.css:122 */
        val brandStrong = Color(0xFF2272EB)
        /** --brand-soft · globals.css:122 */
        val brandSoft = Color(0xFFEAF3FF)
        /** --on-brand · globals.css:122 */
        val onBrand = Color(0xFFFFFFFF)
        /** --up · globals.css:123 */
        val up = Color(0xFFF04452)
        /** --up-soft · globals.css:123 */
        val upSoft = Color(0xFFFEEBEC)
        /** --down · globals.css:123 */
        val down = Color(0xFF3182F6)
        /** --down-soft · globals.css:123 */
        val downSoft = Color(0xFFEAF3FF)
        /** --live · globals.css:124 */
        val live = Color(0xFF3182F6)
        /** --live-soft · globals.css:124 */
        val liveSoft = Color(0xFFEAF3FF)
        /** --pos · globals.css:124 */
        val pos = Color(0xFF12B886)
        /** --pos-soft · globals.css:124 */
        val posSoft = Color(0xFFE6F7F1)
        /** --pos-strong · globals.css:124 */
        val posStrong = Color(0xFF0B7A5A)
        /** --warn · globals.css:124 */
        val warn = Color(0xFFE8A33D)
        /** --warn-soft · globals.css:124 */
        val warnSoft = Color(0xFFFBF1E0)
        /** --warn-strong · globals.css:124 */
        val warnStrong = Color(0xFFA15C00)
        /** --crown · globals.css:125 */
        val crown = Color(0xFFB07D0D)
        /** --crown-soft · globals.css:125 */
        val crownSoft = Color(0xFFFBF1D3)
        /** --ai = var(--brand) · globals.css:297 */
        val ai = Color(0xFF3182F6)
        /** --ai-ink = color-mix(in srgb, var(--ink) 30%, var(--ai)) · globals.css:298 (sRGB 보간 계산값) */
        val aiInk = Color(0xFF2A64B8)
        /** --track-base -0.01em · globals.css:93 */
        const val TRACK_BASE_EM = -0.01f
    }

    /** 다크 — globals.css:146–178 `[data-theme=dark]` (없는 토큰은 라이트 값) */
    object Dark {
        /** --app · globals.css:149 */
        val app = Color(0xFF0E0F11)
        /** --bg · globals.css:149 */
        val bg = Color(0xFF16171A)
        /** --surface = var(--app) · globals.css:149 */
        val surface = Color(0xFF0E0F11)
        /** --hover · globals.css:149 */
        val hover = Color(0xFF232529)
        /** --chip · globals.css:149 */
        val chip = Color(0xFF232529)
        /** --line · globals.css:150 */
        val line = Color(0xFF282A2D)
        /** --line-strong · globals.css:150 */
        val lineStrong = Color(0xFF333640)
        /** --ink · globals.css:170 */
        val ink = Color(0xFFEDEEF1)
        /** --ink2 · globals.css:170 */
        val ink2 = Color(0xFFC7CCD4)
        /** --sub · globals.css:170 */
        val sub = Color(0xFFAAB1BC)
        /** --dim · globals.css:170 */
        val dim = Color(0xFF79818D)
        /** --brand · globals.css:171 */
        val brand = Color(0xFF4D9FFF)
        /** --brand-strong · globals.css:171 */
        val brandStrong = Color(0xFF4D9FFF)
        /** --brand-soft · globals.css:171 */
        val brandSoft = Color(0xFF16233A)
        /** --on-brand · globals.css:171 */
        val onBrand = Color(0xFFFFFFFF)
        /** --up · globals.css:175 */
        val up = Color(0xFFFF5C6C)
        /** --up-soft · globals.css:175 */
        val upSoft = Color(0xFF2B1A1F)
        /** --down · globals.css:175 */
        val down = Color(0xFF4D9FFF)
        /** --down-soft · globals.css:175 */
        val downSoft = Color(0xFF14243A)
        /** --live · globals.css:176 */
        val live = Color(0xFF4D9FFF)
        /** --live-soft · globals.css:176 */
        val liveSoft = Color(0xFF16233A)
        /** --pos · globals.css:176 */
        val pos = Color(0xFF2DD4A0)
        /** --pos-soft · globals.css:176 */
        val posSoft = Color(0xFF122A24)
        /** --pos-strong · globals.css:176 */
        val posStrong = Color(0xFF34D8A6)
        /** --warn · globals.css:176 */
        val warn = Color(0xFFF0B254)
        /** --warn-soft · globals.css:176 */
        val warnSoft = Color(0xFF2E2413)
        /** --warn-strong · globals.css:176 */
        val warnStrong = Color(0xFFF0B254)
        /** --crown · globals.css:177 */
        val crown = Color(0xFFE6B53F)
        /** --crown-soft · globals.css:177 */
        val crownSoft = Color(0xFF2B2410)
        /** --ai = var(--brand) · globals.css:297 */
        val ai = Color(0xFF4D9FFF)
        /** --ai-ink = color-mix(in srgb, var(--ink) 30%, var(--ai)) · globals.css:298 (sRGB 보간 계산값) */
        val aiInk = Color(0xFF7DB7FB)
        /** --track-base -0.005em · globals.css:147 */
        const val TRACK_BASE_EM = -0.005f
    }

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

    /** 글자 역할 — globals.css .t-* (PC·모바일 크기, 행간 배수, 자간 em) */
    object Type {
        /** .t-display PC 30px (globals.css:781) · 모바일 28px (:820) · 행간 1.14 · 자간 -0.02em (:798) */
        val display = WebRole(pcPx = 30f, mobilePx = 28f, lineHeight = 1.14f, letterSpacingEm = -0.02f, tabularNums = false)
        /** .t-title-lg PC 22px (globals.css:782) · 모바일 20px (:820) · 행간 1.22 · 자간 -0.02em (:799) */
        val titleLg = WebRole(pcPx = 22f, mobilePx = 20f, lineHeight = 1.22f, letterSpacingEm = -0.02f, tabularNums = false)
        /** .t-headline PC 17px (globals.css:783) · 모바일 18px (:820) · 행간 1.28 · 자간 -0.015em (:800) */
        val headline = WebRole(pcPx = 17f, mobilePx = 18f, lineHeight = 1.28f, letterSpacingEm = -0.015f, tabularNums = false)
        /** .t-title PC 15px (globals.css:784) · 모바일 17px (:821) · 행간 1.3 · 자간 -0.01em (:801) */
        val title = WebRole(pcPx = 15f, mobilePx = 17f, lineHeight = 1.3f, letterSpacingEm = -0.01f, tabularNums = false)
        /** .t-body-lg PC 15px (globals.css:785) · 모바일 17px (:821) · 행간 1.66 · 자간 -0.01em (:802) */
        val bodyLg = WebRole(pcPx = 15f, mobilePx = 17f, lineHeight = 1.66f, letterSpacingEm = -0.01f, tabularNums = false)
        /** .t-body PC 14px (globals.css:786) · 모바일 16px (:821) · 행간 1.58 · 자간 -0.005em (:803) */
        val body = WebRole(pcPx = 14f, mobilePx = 16f, lineHeight = 1.58f, letterSpacingEm = -0.005f, tabularNums = false)
        /** .t-val-xl PC 30px (globals.css:759) · 모바일 32px (:825) · 행간 1.12 · 자간 -0.02em · tabular-nums (:761) */
        val valueXl = WebRole(pcPx = 30f, mobilePx = 32f, lineHeight = 1.12f, letterSpacingEm = -0.02f, tabularNums = true)
        /** .t-val-2xl PC 40px (globals.css:760) · 모바일 34px (:825) · 행간 1.08 · 자간 -0.02em · tabular-nums (:762) */
        val value2xl = WebRole(pcPx = 40f, mobilePx = 34f, lineHeight = 1.08f, letterSpacingEm = -0.02f, tabularNums = true)
        /** .t-val-lg PC 18px (globals.css:787) · 모바일 19px (:822) · 행간 1.2 · 자간 -0.015em · tabular-nums (:804) */
        val valueLg = WebRole(pcPx = 18f, mobilePx = 19f, lineHeight = 1.2f, letterSpacingEm = -0.015f, tabularNums = true)
        /** .t-val PC 15px (globals.css:788) · 모바일 16px (:822) · 행간 1.2 · 자간 -0.01em · tabular-nums (:805) */
        val value = WebRole(pcPx = 15f, mobilePx = 16f, lineHeight = 1.2f, letterSpacingEm = -0.01f, tabularNums = true)
        /** .t-val-sm PC 13px (globals.css:789) · 모바일 14.5px (:822) · 행간 1.2 · 자간 0em · tabular-nums (:806) */
        val valueSm = WebRole(pcPx = 13f, mobilePx = 14.5f, lineHeight = 1.2f, letterSpacingEm = 0f, tabularNums = true)
        /** .t-label PC 13px (globals.css:790) · 모바일 14.5px (:823) · 행간 1.3 · 자간 0em (:807) */
        val label = WebRole(pcPx = 13f, mobilePx = 14.5f, lineHeight = 1.3f, letterSpacingEm = 0f, tabularNums = false)
        /** .t-label-sm PC 12px (globals.css:791) · 모바일 13.5px (:823) · 행간 1.3 · 자간 0.005em (:808) */
        val labelSm = WebRole(pcPx = 12f, mobilePx = 13.5f, lineHeight = 1.3f, letterSpacingEm = 0.005f, tabularNums = false)
        /** .t-cap PC 12.5px (globals.css:792) · 모바일 14px (:823) · 행간 1.3 · 자간 0em (:809) */
        val cap = WebRole(pcPx = 12.5f, mobilePx = 14f, lineHeight = 1.3f, letterSpacingEm = 0f, tabularNums = false)
        /** .t-eyebrow PC 12px (globals.css:793) · 모바일 13px (:824) · 행간 1.26 · 자간 0.02em (:810) */
        val eyebrow = WebRole(pcPx = 12f, mobilePx = 13f, lineHeight = 1.26f, letterSpacingEm = 0.02f, tabularNums = false)
        /** .t-micro PC 11px (globals.css:794) · 모바일 12.5px (:824) · 행간 1.2 · 자간 0.01em (:811) */
        val micro = WebRole(pcPx = 11f, mobilePx = 12.5f, lineHeight = 1.2f, letterSpacingEm = 0.01f, tabularNums = false)
        /** --lh-bold 굵기별 행간 보정 */
        const val LH_BOLD_MEDIUM = 0.008f // globals.css:750
        const val LH_BOLD_SEMIBOLD = 0.015f // globals.css:751
        const val LH_BOLD_BOLD = 0.025f // globals.css:752
        const val LH_BOLD_EXTRABOLD = 0.035f // globals.css:753
    }

    /** 아바타 이니셜 색 — lib/rt.ts:3151 `const PAL` (avatarColor가 cid 해시로 고른다) */
    val avatarPalette = listOf(Color(0xFF3182F6), Color(0xFFF5A623), Color(0xFF7B61FF), Color(0xFFF04452), Color(0xFF00C2B3), Color(0xFFFF7A45), Color(0xFF4C6EF5), Color(0xFFE64980), Color(0xFF12B886))
}
