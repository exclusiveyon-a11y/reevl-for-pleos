package kr.co.reevl.ontheway.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.R
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme

/**
 * Pleos Page Navigation (docs-design/components/page-navigation), measured on the native phone/Gleo apps:
 * height 100, 32 side margin, 62 dp app icon, title, then suffix actions and `⋯` more. 1 dp line below.
 *
 * Main (top-level screens): [app icon] [Reevl wordmark] │ [screen name] … [status] [⋯]
 * Depth (pushed screens):   [‹ back] [title / subtitle] … [actions] [⋯]
 */
@Composable
fun MainHeader(screenName: String, onLogout: (() -> Unit)?, status: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = CarTheme.colors
    HeaderFrame {
        Image(painterResource(R.drawable.reevl_app_icon), "리블", modifier = Modifier.size(CarDims.appIcon).clip(RoundedCornerShape(18.dp)).background(androidx.compose.ui.graphics.Color.White))
        ReevlLogo(height = 30.dp, modifier = Modifier.padding(start = 20.dp))
        Box(Modifier.padding(horizontal = 20.dp).width(2.dp).height(32.dp).background(c.lineStrong))
        Text(screenName, style = CarTheme.type.pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (status != null) Text(status, style = CarTheme.type.cap, maxLines = 1, modifier = Modifier.padding(end = 16.dp).testTag("header_status"))
        actions()
        if (onLogout != null) MoreMenu(onLogout)
    }
}

@Composable
fun DepthHeader(title: String, onBack: () -> Unit, subtitle: String? = null, backTag: String = "back", onLogout: (() -> Unit)? = null,
                actions: @Composable RowScope.() -> Unit = {}) {
    HeaderFrame(startPad = 16.dp) {
        IconAction(Lucide.ChevronLeft, "뒤로", onBack, tag = backTag)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, style = CarTheme.type.pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = CarTheme.type.cap.copy(color = CarTheme.colors.sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
        if (onLogout != null) MoreMenu(onLogout)
    }
}

@Composable
private fun HeaderFrame(startPad: androidx.compose.ui.unit.Dp = CarDims.gutter, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxWidth().height(CarDims.header).padding(start = startPad, end = 16.dp),
            content = content,
        )
        Hairline()
    }
}

/** Page Navigation suffix `⋯` → small menu with `로그아웃` (D2 behaviour). */
@Composable
private fun MoreMenu(onLogout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val c = CarTheme.colors
    Box {
        IconAction(Lucide.Ellipsis, "메뉴", { open = true }, tag = "menu")
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(c.card)) {
            DropdownMenuItem(
                leadingIcon = { Icon(Lucide.LogOut, null, tint = c.ink, modifier = Modifier.size(32.dp)) },
                text = { Text("로그아웃", style = CarTheme.type.title) },
                onClick = { open = false; onLogout() },
                modifier = Modifier.height(CarDims.buttonL).width(360.dp).testTag("logout"),
            )
        }
    }
}
