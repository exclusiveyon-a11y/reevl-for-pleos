package kr.co.reevl.ontheway.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.theme.LocalCarColors
import kr.co.reevl.ontheway.ui.theme.LocalMotion
import kr.co.reevl.ontheway.ui.theme.ReevlWeb

// Reevl AI visual language, ported from reevl frontend/app/globals.css (AI VISUAL LANGUAGE block).
// Car scale = web px × 2 (car-ui-spec §1.5). Motion stops while driving / when animations are off (LocalMotion).

/**
 * 0→1 phase of the web's `aiGradShift` keyframes (`background-position 200% 0 → 0% 0`, linear, infinite).
 * Still (0) when motion is not allowed — the web's reduced-motion fallback is the first frame.
 */
@Composable
fun aiFlowPhase(periodMs: Int = ReevlWeb.Ai.FLOW_MS): State<Float> {
    if (!LocalMotion.current) return remember { mutableFloatStateOf(0f) }
    val t = rememberInfiniteTransition(label = "aiGradShift")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "phase")
}

/**
 * The web's 200%-wide gradient at [phase]: image width 2W, background-position p ∈ [200%, 0%] → x offset = −W·p.
 * Five stops at 0/25/50/75/100 % repeat every W, so the loop is seamless (globals.css comment "대칭이라 리셋 튐 없음").
 */
private fun flowBrush(colors: List<Color>, width: Float, phase: Float): Brush {
    val offset = -2f * width * (1f - phase)
    return Brush.linearGradient(colors, start = Offset(offset, 0f), end = Offset(offset + 2f * width, 0f), tileMode = TileMode.Repeated)
}

/**
 * `.ai-live-grad` (globals.css:315 keyframes, 317-326): ai 10 % → 3 % → 10 % → 3 % → 10 %, 200 % wide, flowing over `--ai-flow` (5 s).
 * Paints behind the content inside the shape bounds (callers clip with their card shape).
 */
fun Modifier.aiLiveGrad(): Modifier = composed {
    val ai = LocalCarColors.current.ai
    val phase by aiFlowPhase(ReevlWeb.Ai.FLOW_MS)
    val stops = remember(ai) { listOf(0.10f, 0.03f, 0.10f, 0.03f, 0.10f).map { ai.copy(alpha = it) } }
    drawBehind { drawRect(flowBrush(stops, size.width, phase)) }
}

/**
 * `.ai-soft-glow` (globals.css:486-491): three box-shadow layers `0 0 blur spread ai@alpha`, no fill, no border.
 * Scaled ×2 for the car. Drawn outside the bounds — the parent must not clip it (give scroll containers padding).
 * CSS blur radius B = 2σ; Android BlurMaskFilter radius r ↔ σ = 0.57735·r + 0.5.
 */
fun Modifier.aiSoftGlow(cornerRadius: Dp, scale: Float = 2f): Modifier = composed {
    val ai = LocalCarColors.current.ai
    val layers = ReevlWeb.Ai.softGlow
    drawBehind {
        drawIntoCanvas { canvas ->
            for (l in layers) {
                val sigma = l.blurPx * scale / 2f * density / 1f
                val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(1f)
                val spread = l.spreadPx * scale * density
                val paint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = ai.copy(alpha = l.alpha).toArgb()
                    maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                }
                val r = (cornerRadius.toPx() + spread).coerceAtLeast(0f)
                canvas.nativeCanvas.drawRoundRect(-spread, -spread, size.width + spread, size.height + spread, r, r, paint)
            }
        }
    }
}

/**
 * `.ai-text-grad` (globals.css:522-526): text filled with ai → ai-ink → ai → ai-ink → ai, 200 % wide, `--ai-flow` 5 s.
 * Drawn as the text's alpha mask (SrcIn) so any [style] works.
 */
@Composable
fun AiGradientText(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val c = LocalCarColors.current
    val phase by aiFlowPhase(ReevlWeb.Ai.FLOW_MS)
    val stops = remember(c.ai, c.aiInk) { listOf(c.ai, c.aiInk, c.ai, c.aiInk, c.ai) }
    BasicText(
        text = text,
        style = style.copy(color = c.ai),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(flowBrush(stops, size.width, phase), blendMode = BlendMode.SrcIn)
            },
    )
}

/** Small static helper for the "ai" dot used next to the next-stop label (web .live-pulse uses currentColor). */
val AiDotSize = 12.dp
