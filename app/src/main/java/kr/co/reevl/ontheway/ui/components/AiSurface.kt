package kr.co.reevl.ontheway.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.theme.LocalCarColors
import kr.co.reevl.ontheway.ui.theme.LocalMotion
import kr.co.reevl.ontheway.ui.theme.ReevlWeb
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

// REEVL AI visual language, ported from reevl/frontend/app/globals.css (AI VISUAL LANGUAGE block, :291–330 and :486–583).
// Car scale = web px × 2 (car-ui-spec §1.5). Only AI surfaces use these (the web rule: the AI signature marks "AI is here").
//
// Motion: one shared clock ([ProvideAiClock], at the theme root) drives every flowing effect — no per-surface infinite
// transition. The clock ticks only while motion is allowed AND at least one AI surface is on screen. While the car is moving /
// idling / UX-restricted, or the system animator scale is 0, every effect draws the static mid-frame ([AiMotion.STATIC_PHASE])
// — no moving pixels while driving (Google/AAOS driver-distraction guidance; Pleos "운전 중 애니메이션 금지").

/** Pure rules (unit-tested in AiMotionTest). */
object AiMotion {
    /** The frozen frame. The web gradients are symmetric (stops repeat every 50 % of the 200 % image), so 0.5 = 0 visually. */
    const val STATIC_PHASE = 0.5f

    /** Gradients move only while parked, unrestricted, and with system animations on (web `prefers-reduced-motion`). */
    fun allowed(restricted: Boolean, moving: Boolean, animatorScale: Float): Boolean = !restricted && !moving && animatorScale > 0f

    /** 0→1 phase of the web's `aiGradShift` (`background-position 200% 0 → 0% 0`, linear, infinite) at [elapsedMs]. */
    fun phase(elapsedMs: Long, periodMs: Int, allowed: Boolean): Float =
        if (!allowed || periodMs <= 0) STATIC_PHASE else (elapsedMs % periodMs).toFloat() / periodMs

    /** `aiGlow` keyframes 0 %,100 % → none, 50 % → full, ease-in-out (approximated by a raised cosine). Static = mid-frame = full. */
    fun glowLevel(phase: Float): Float = ((1f - kotlin.math.cos(2.0 * Math.PI * phase).toFloat()) / 2f)
}

/** Shared AI clock: elapsed ms, ticking only while [users] > 0 and motion is allowed. */
@Stable
class AiClock {
    internal val ms = mutableLongStateOf(0L)
    internal var users by mutableIntStateOf(0)
}

val LocalAiClock = staticCompositionLocalOf { AiClock() }

@Composable
fun ProvideAiClock(running: Boolean, content: @Composable () -> Unit) {
    val clock = remember { AiClock() }
    val active = running && clock.users > 0
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val t0 = withFrameMillis { it } - clock.ms.longValue
        while (true) withFrameMillis { clock.ms.longValue = it - t0 }
    }
    CompositionLocalProvider(LocalAiClock provides clock, content = content)
}

/** A phase reader for draw lambdas: reading it inside draw invalidates only the draw, never recomposition. */
@Composable
fun rememberAiPhase(periodMs: Int): () -> Float {
    val clock = LocalAiClock.current
    val motion = LocalMotion.current
    DisposableEffect(clock, motion) {
        if (motion) clock.users++
        onDispose { if (motion) clock.users-- }
    }
    return remember(clock, motion, periodMs) { { AiMotion.phase(if (motion) clock.ms.longValue else 0L, periodMs, motion) } }
}

/**
 * The web's 200 %-wide gradient at [phase]: image width 2W, background-position p ∈ [200 %, 0 %] → x offset = −2W·(1−phase).
 * Five stops at 0/25/50/75/100 % repeat every W, so the loop is seamless (globals.css "대칭이라 리셋 튐 없음").
 */
internal fun flowBrush(colors: List<Color>, width: Float, phase: Float): Brush {
    val offset = -2f * width * (1f - phase)
    return Brush.linearGradient(colors, start = Offset(offset, 0f), end = Offset(offset + 2f * width, 0f), tileMode = TileMode.Repeated)
}

/** Five stops of [ai] at alternating alphas (the web's `color-mix(in srgb, var(--ai) N%, transparent)`). */
internal fun aiStops(ai: Color, hi: Float, lo: Float) = listOf(hi, lo, hi, lo, hi).map { ai.copy(alpha = it) }

/**
 * `.ai-live-grad` (globals.css:317–326): ai 10 % ↔ 3 %, 200 % wide, `--ai-flow` 5 s. With [glow] = `.ai-live-grad.ai-glow`
 * (globals.css:538): the flow runs at `--ai-flow-2` 4.5 s and the `aiGlow` halo (0 0 18px −3px ai 42 %, `--ai-glow` 2.6 s) breathes
 * under the band — the web's AI search header (CommandPalette.tsx:866).
 */
fun Modifier.aiLiveGrad(glow: Boolean = false): Modifier = composed {
    val ai = LocalCarColors.current.ai
    val phase = rememberAiPhase(if (glow) ReevlWeb.Ai.FLOW2_MS else ReevlWeb.Ai.FLOW_MS)
    val glowPhase = rememberAiPhase(ReevlWeb.Ai.GLOW_MS)
    val stops = remember(ai) { aiStops(ai, 0.10f, 0.03f) }
    drawBehind {
        if (glow) {
            val level = AiMotion.glowLevel(glowPhase())
            if (level > 0.01f) drawIntoCanvas { canvas ->
                // box-shadow 0 0 18px −3px × 2: blur 36 → σ 18 dp; spread −6 dp.
                val sigma = 18f * density
                val spread = -6f * density
                val paint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = ai.copy(alpha = 0.42f * level).toArgb()
                    maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
                // outer box-shadow only: never under the band itself (CSS paints box-shadow outside the border box)
                val nc = canvas.nativeCanvas
                nc.save(); nc.clipOutRect(0f, 0f, size.width, size.height)
                nc.drawRect(-spread, -spread, size.width + spread, size.height + spread, paint)
                nc.restore()
            }
        }
        drawRect(flowBrush(stops, size.width, phase()))
    }
}

/**
 * `.ai-soft-glow` (globals.css:486–491): three box-shadow layers `0 0 blur spread ai@alpha`, no fill, no border. Static (no motion).
 * Scaled ×2 for the car. Drawn outside the bounds — the parent must not clip it.
 * CSS blur radius B = 2σ; Android BlurMaskFilter radius r ↔ σ = 0.57735·r + 0.5.
 */
fun Modifier.aiSoftGlow(cornerRadius: Dp, scale: Float = 2f): Modifier = composed {
    val ai = LocalCarColors.current.ai
    val layers = ReevlWeb.Ai.softGlow
    drawBehind {
        drawIntoCanvas { canvas ->
            // CSS outer box-shadow is never painted under the element's own border box — with a transparent face (the web AI
            // input is `bg-transparent`) the inside must stay clear, so clip the box out before painting the halo.
            val nc = canvas.nativeCanvas
            val cr = cornerRadius.toPx()
            val hole = android.graphics.Path().apply { addRoundRect(0f, 0f, size.width, size.height, cr, cr, android.graphics.Path.Direction.CW) }
            nc.save()
            nc.clipOutPath(hole)
            for (l in layers) {
                val sigma = l.blurPx * scale / 2f * density
                val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(1f)
                val spread = l.spreadPx * scale * density
                val paint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = ai.copy(alpha = l.alpha).toArgb()
                    maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                }
                val r = (cornerRadius.toPx() + spread).coerceAtLeast(0f)
                nc.drawRoundRect(-spread, -spread, size.width + spread, size.height + spread, r, r, paint)
            }
            nc.restore()
        }
    }
}

/**
 * `.ai-chip-hover:hover` (globals.css:528–537): ai 26 % ↔ 12 % flowing fill, `--ai-flow` 5 s. A car has no hover — the fill shows
 * while the chip is held ([on]). Drawn behind the content.
 */
fun Modifier.aiChipFill(on: Boolean): Modifier = composed {
    if (!on) return@composed this
    val ai = LocalCarColors.current.ai
    val phase = rememberAiPhase(ReevlWeb.Ai.FLOW_MS)
    val stops = remember(ai) { aiStops(ai, 0.26f, 0.12f) }
    drawBehind { drawRect(flowBrush(stops, size.width, phase())) }
}

/**
 * `.ai-text-grad` (globals.css:522–526) as a fill for any drawn content: ai → ai-ink → ai → ai-ink → ai, 200 % wide, `--ai-flow` 5 s,
 * applied as the content's alpha mask (SrcIn). Same stops as the ReevlAI wordmark (ReevlLogo.tsx:87–93) — the web's working
 * icon gradient; the shared `#reevl-ai-grad` def (AiGradDef.tsx) animates stop-color with `var(--ai)` in SMIL, which browsers
 * do not resolve (ReevlLogo.tsx:26–29), so it is not the effect users see.
 */
fun Modifier.aiGradFill(): Modifier = composed {
    val c = LocalCarColors.current
    val phase = rememberAiPhase(ReevlWeb.Ai.FLOW_MS)
    val stops = remember(c.ai, c.aiInk) { listOf(c.ai, c.aiInk, c.ai, c.aiInk, c.ai) }
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(flowBrush(stops, size.width, phase()), blendMode = BlendMode.SrcIn)
        }
}

/** `.ai-text-grad` text. Day: the ai end is #3182F6 (3.71:1 on white) — AI text is ≥ 26 sp (WCAG large text, §14.5). */
@Composable
fun AiGradientText(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) {
    BasicText(text = text, style = style.copy(color = LocalCarColors.current.ai), maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        modifier = modifier.aiGradFill())
}

/** `.ai-icon-grad`: a stroke icon painted with the AI gradient (Sparkles, the AI mic). */
@Composable
fun AiIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription, tint = LocalCarColors.current.ai, modifier = modifier.aiGradFill())
}

/** Small static helper for the "ai" dot used next to the next-stop label (web .live-pulse uses currentColor). */
val AiDotSize = CarSize.Icon.dot
