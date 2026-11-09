package kr.co.reevl.ontheway.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.components.Hairline
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme

/**
 * true in the landscape (2:0) window (≈ 1706 dp), false in the 1:1 window (≈ 850 dp). Pleos screen model: a portrait
 * core view + extra views in landscape — same functions in both, only the arrangement changes.
 */
val LocalWide = compositionLocalOf { true }

/** Page (`--app`) + Page Navigation header + body + optional fixed action row at the bottom. */
@Composable
fun ScreenFrame(
    tag: String,
    header: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val c = CarTheme.colors
    Column(Modifier.fillMaxSize().background(c.page).testTag(tag)) {
        header()
        Column(Modifier.weight(1f).fillMaxWidth(), content = body)
        if (actions != null) {
            Hairline()
            // "한 단계 = 질문 하나 + 아래 고정 행동 줄" (memory feedback-vehicle-ui-craft)
            Row(
                Modifier.fillMaxWidth().background(c.card).padding(horizontal = CarDims.gutter, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(CarDims.cardGap),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
