package kr.co.reevl.ontheway.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kr.co.reevl.ontheway.R

/**
 * Pretendard v1.3.9 — the font reevl.co.kr ships (frontend/app/pretendard.css, same version), SIL OFL 1.1
 * (assets/licenses/Pretendard-OFL.txt). Pleos Typography: brands may set their font; avoid characterful ones —
 * Pretendard is a neutral grotesque.
 */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

/** Korean wraps between phrases (어절) — web `word-break: keep-all` (globals.css body rule). */
private val KoBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)

/** Web `--lh-bold` (globals.css:750-753): heavier text gets a little more leading. */
private fun lhBold(w: FontWeight): Float = when {
    w >= FontWeight.ExtraBold -> ReevlWeb.Type.LH_BOLD_EXTRABOLD
    w >= FontWeight.Bold -> ReevlWeb.Type.LH_BOLD_BOLD
    w >= FontWeight.SemiBold -> ReevlWeb.Type.LH_BOLD_SEMIBOLD
    w >= FontWeight.Medium -> ReevlWeb.Type.LH_BOLD_MEDIUM
    else -> 0f
}

/**
 * One car text role = a Reevl web role (line-height multiple, letter-spacing, tabular numbers) at a Pleos size step
 * (Typography guide: Headline 60/56/48 · Title 40/36/32 · Body 30/28/26 · Label 24/20). car-ui-spec §1.4.
 */
private fun role(web: WebRole, sizeDp: Int, weight: FontWeight, colors: CarColors) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = sizeDp.sp,
    lineHeight = (sizeDp * (web.lineHeight + lhBold(weight))).sp,
    letterSpacing = web.letterSpacingEm.em,
    fontFeatureSettings = if (web.tabularNums) "tnum" else null,
    lineBreak = KoBreak,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    color = colors.ink,
)

@Immutable
data class CarTypography(
    /** t-display → Pleos Headline S 48 */
    val display: TextStyle,
    /** t-title-lg → Title L 40 */
    val titleLg: TextStyle,
    /** Page Navigation title → Title M 36 */
    val pageTitle: TextStyle,
    /** t-headline → Title S 32 (Google primary text 32 dp) */
    val headline: TextStyle,
    /** t-title / t-body-lg → Body L 30 */
    val title: TextStyle,
    /** t-body → Body M 28 */
    val body: TextStyle,
    /** t-val-xl → Headline M 56 */
    val valueXl: TextStyle,
    /** t-val-lg → Title M 36 */
    val valueLg: TextStyle,
    /** t-val → Body L 30 */
    val value: TextStyle,
    /** t-val-sm → Body M 28 */
    val valueSm: TextStyle,
    /** t-label → Body S 26 */
    val label: TextStyle,
    /** t-cap / t-label-sm / t-micro → Label M 24 (Google secondary 24 dp; Pleos Label S 20 not used) */
    val cap: TextStyle,
) {
    companion object {
        fun create(c: CarColors) = with(ReevlWeb.Type) {
            CarTypography(
                display = role(display, 48, FontWeight.ExtraBold, c),
                titleLg = role(titleLg, 40, FontWeight.ExtraBold, c),
                pageTitle = role(headline, 36, FontWeight.Bold, c),
                headline = role(headline, 32, FontWeight.ExtraBold, c),
                title = role(title, 30, FontWeight.Bold, c),
                body = role(body, 28, FontWeight.Normal, c).copy(color = c.ink2),
                valueXl = role(valueXl, 56, FontWeight.ExtraBold, c),
                valueLg = role(valueLg, 36, FontWeight.ExtraBold, c),
                value = role(value, 30, FontWeight.Bold, c),
                valueSm = role(valueSm, 28, FontWeight.Bold, c),
                label = role(label, 26, FontWeight.Bold, c),
                // Car: captions use `--sub`, not `--dim` — dim is 4.38:1 on the light page and 3.9:1 on the dark chip (ContrastTest),
                // under Google 4.5:1. Same hierarchy one step up.
                cap = role(cap, 24, FontWeight.Medium, c).copy(color = c.sub),
            )
        }
    }
}

val LocalCarType = staticCompositionLocalOf { CarTypography.create(CarColors.Light) }
