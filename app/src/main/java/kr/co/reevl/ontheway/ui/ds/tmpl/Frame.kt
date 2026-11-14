package kr.co.reevl.ontheway.ui.ds.tmpl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.ds.comp.ActionBar
import kr.co.reevl.ontheway.ui.ds.comp.Actions
import kr.co.reevl.ontheway.ui.ds.comp.Banner
import kr.co.reevl.ontheway.ui.ds.comp.BannerSpec
import kr.co.reevl.ontheway.ui.ds.comp.Toast
import kr.co.reevl.ontheway.ui.ds.comp.ToastSpec
import kr.co.reevl.ontheway.ui.ds.comp.TopBar
import kr.co.reevl.ontheway.ui.ds.comp.TopBarSpec
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalWideWindow
import kr.co.reevl.ontheway.ui.ds.token.ProvidePane

/** Toast + its dismiss, shared by every template. */
data class ToastSlot(val spec: ToastSpec, val onDismiss: () -> Unit)

/**
 * §3.4 vertical structure shared by all templates: TopBar 100 · [Banner] · body · [ActionBar 148]; toast floats 24 above
 * the bottom. [readWidth] = one-pane screens in the wide window centre at 1200 dp.
 */
@Composable
internal fun Frame(
    tag: String,
    top: TopBarSpec?,
    banner: BannerSpec?,
    actions: Actions?,
    toast: ToastSlot?,
    onePane: Boolean,
    /** Extra lift for the toast when an action bar sits inside the body (wide list preview). */
    toastLift: Dp = 0.dp,
    body: @Composable ColumnScope.() -> Unit,
) {
    val r = LocalRoles.current
    val wide = LocalWideWindow.current
    val rail = kr.co.reevl.ontheway.ui.ds.comp.LocalRail.current
    // v2.1: one page colour (surface, like the built-in apps); the wide window keeps the left menu beside every screen.
    Column(Modifier.fillMaxSize().background(r.surface).testTag(tag)) {
        top?.let { TopBar(it) }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (wide && rail != null) kr.co.reevl.ontheway.ui.ds.comp.Rail(rail)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        val w: Dp = if (onePane && wide && rail == null) minOf(maxWidth, CarSpace.readWidth) else maxWidth
                        ProvidePane(w) {
                            Column((if (onePane && wide && rail == null) Modifier.widthIn(max = CarSpace.readWidth) else Modifier).fillMaxSize()) {
                                banner?.let { Banner(it) }
                                body()
                            }
                        }
                    }
                    toast?.let { t ->
                        Box(Modifier.fillMaxSize().padding(start = CarSpace.actionPad, end = CarSpace.actionPad, bottom = CarSpace.actionPad + toastLift), contentAlignment = Alignment.BottomCenter) { Toast(t.spec, t.onDismiss) }
                    }
                }
                actions?.let { a -> ProvidePane(CarSpace.split) { ActionBar(a, null) } }
            }
        }
    }
}

