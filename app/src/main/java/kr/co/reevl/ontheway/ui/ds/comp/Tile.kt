package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import kr.co.reevl.ontheway.ui.components.aiSoftGlow
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2

/** Home tile: icon top-left, name 1 line + value 1 line at the bottom. [ai] = Reevl AI surface. */
data class TileSpec(val icon: ImageVector, val name: String, val value: String, val onClick: () -> Unit, val ai: Boolean = false,
                    val enabled: Boolean = true, val tag: String? = null)

@Composable
fun Tile(s: TileSpec, modifier: Modifier = Modifier) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val shape = RoundedCornerShape(CarSpace.radius)
    Column(modifier.then(if (s.ai) Modifier.aiSoftGlow(CarSpace.radius) else Modifier).background(r.surface, shape)
        .press(shape, enabled = s.enabled, tag = s.tag, onClick = s.onClick).padding(CarSpace.margin)) {
        val a = if (s.enabled) 1f else r.disabledAlpha
        Icon(s.icon, null, tint = if (s.ai) r.ai else r.ink2, modifier = Modifier.size(CarSpace.tileIcon).alpha(a))
        Spacer(Modifier.weight(1f))
        // AI name in brandText (aiInk, 5.81:1 day): the web's flowing ai→aiInk text would drop to 3.71:1 at its ai end (§7.3 rule 4.5).
        Text(s.name, style = t.title.copy(color = when { !s.enabled -> r.sub; s.ai -> r.brandText; else -> r.ink }), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(s.value, style = t.rowSecondary.copy(color = r.sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Suppress("unused") private val keep = Modifier.fillMaxSize()
