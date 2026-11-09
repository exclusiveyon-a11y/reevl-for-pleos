package kr.co.reevl.ontheway.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kr.co.reevl.ontheway.ui.theme.LocalCarColors
import kr.co.reevl.ontheway.ui.theme.LocalMotion

/** Web `#3182F6` — the logo square is this exact colour in both themes (ReevlLogo.tsx rect fill="#3182F6"). */
private val SparkBlue = Color(0xFF3182F6)

/**
 * Reevl wordmark / mark, drawn from the web SVG paths (ReevlLogoPaths, generated from ReevlLogo.tsx).
 * Letters = ink (web fill=currentColor → text-ink), square = #3182F6 with `.reevl-spark` (globals.css:587-592):
 * opacity .82 ↔ 1 and a blue glow, 2 s ease-in-out; still glow when motion is off (web reduced-motion rule).
 */
@Composable
fun ReevlLogo(height: Dp, modifier: Modifier = Modifier, spec: ReevlLogoSpec = ReevlLogoPaths.Mobile, letterColor: Color? = null) {
    val ink = letterColor ?: LocalCarColors.current.ink
    val paths = remember(spec) { spec.letters.map { PathParser().parsePathString(it).toPath() } }
    val motion = LocalMotion.current
    val pulse by if (motion) {
        rememberInfiniteTransition(label = "reevlSpark")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "spark")
    } else remember { mutableFloatStateOf(-1f) } // reduced motion: the web's still frame (opacity 1, drop-shadow 0 0 4px .6)
    val width = height * (spec.viewportWidth / spec.viewportHeight)
    Canvas(modifier.width(width).height(height).semantics { contentDescription = "Reevl" }) {
        val k = size.height / spec.viewportHeight
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            paths.forEach { drawPath(it, ink) }
        }
        // spark: 0 %/100 % → opacity .82, drop-shadow 1.5px @.55 · 50 % → opacity 1, 3px @.95 + 9px @.6 (CSS px of a 40-unit box)
        val x = spec.sparkX * k; val y = spec.sparkY * k; val w = spec.sparkW * k; val h = spec.sparkH * k
        // The web's drop-shadow is in CSS px on an ~18 px-tall wordmark, where the square is ≈ 4.3 px wide.
        // Keep the same glow-to-square ratio at car size.
        val unit = w / 4.3f
        drawIntoCanvas { c ->
            fun glow(blurCss: Float, alpha: Float) {
                if (alpha <= 0f) return
                val p = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = SparkBlue.copy(alpha = alpha).toArgb()
                    maskFilter = BlurMaskFilter((blurCss * unit).coerceAtLeast(0.5f), BlurMaskFilter.Blur.OUTER)
                }
                c.nativeCanvas.drawRect(x, y, x + w, y + h, p)
            }
            if (pulse < 0f) {
                glow(4f, 0.6f) // globals.css:592 prefers-reduced-motion: filter: drop-shadow(0 0 4px rgba(49,130,246,.6))
            } else {
                glow(1.5f + 1.5f * pulse, 0.55f + 0.40f * pulse)
                glow(9f, 0.6f * pulse)
            }
        }
        drawRect(SparkBlue.copy(alpha = if (pulse < 0f) 1f else 0.82f + 0.18f * pulse), topLeft = androidx.compose.ui.geometry.Offset(x, y),
            size = androidx.compose.ui.geometry.Size(w, h))
    }
}
