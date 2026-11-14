package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.components.Lucide
import kr.co.reevl.ontheway.ui.components.AiIcon
import kr.co.reevl.ontheway.ui.components.AiGradientText
import kr.co.reevl.ontheway.ui.components.aiSoftGlow
import kr.co.reevl.ontheway.ui.ds.token.CarSpace
import kr.co.reevl.ontheway.ui.ds.token.LocalRoles
import kr.co.reevl.ontheway.ui.ds.token.LocalTypeV2
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

data class FieldSpec(val value: String, val onValue: (String) -> Unit, val onSubmit: (String) -> Unit, val loading: Boolean,
                     val placeholder: String = "어떤 집을 찾으세요?",
                     /** Voice first: with an empty field the field's button is `말하기` (typing stays possible, it is the second way). */
                     val onVoice: (() -> Unit)? = null)

/** §5.10: AI field 100 high + `찾기` (200 wide). Focus = 3 dp brand ring (§14). */
@Composable
fun SearchField(s: FieldSpec, onFocus: (Boolean) -> Unit = {}) {
    val r = LocalRoles.current
    val t = LocalTypeV2.current
    val kb = LocalSoftwareKeyboardController.current
    val fm = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(CarSpace.radius)
    val submit = { fm.clearFocus(); kb?.hide(); s.onSubmit(s.value) }
    // §15 narrow window: `찾기` keeps its word but shrinks to 128 so the field keeps room for the placeholder.
    val narrow = kr.co.reevl.ontheway.ui.ds.token.LocalWindowClass.current == kr.co.reevl.ontheway.ui.ds.token.WindowClass.Narrow
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (narrow) CarSpace.gapTight else CarSpace.gap)) {
        // §14.5: the REEVL AI input exactly as the web draws it — `.ai-soft-glow` only, no border, no face
        // (SearchTrigger.tsx:28–37, AISearch.tsx `ai-soft-glow … bg-transparent`, lib/aiCard.ts "테두리 제거, 빛만 유지" —
        // ring + glow together is the combination the web forbids). Prompt = `.ai-text-grad` (NlRotator prefix), icon =
        // `.ai-icon-grad` Sparkles. Car addition: focus = 3 dp brand ring (rotary/touch focus must be visible, §14.3). R = 24.
        Row(Modifier.weight(1f).height(CarSpace.button).aiSoftGlow(CarSpace.radius)
            .then(if (focused) Modifier.border(CarSize.Stroke.focus, r.brand, shape) else Modifier).padding(start = CarSize.Inset.l),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CarSpace.gapTight)) {
            AiIcon(Lucide.Sparkles, null, Modifier.size(CarSpace.icon))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (s.value.isEmpty()) AiGradientText(s.placeholder, t.rowPrimary)
                BasicTextField(s.value, s.onValue, singleLine = true, enabled = !s.loading, textStyle = t.rowPrimary.copy(color = r.ink),
                    cursorBrush = SolidColor(r.brand), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier.fillMaxWidth().testTag("search_field").onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) })
            }
            if (s.loading) Spinner(r.brandText, CarSpace.icon, Modifier.padding(end = 30.dp)) // layout: spinner sits where the 검색 label ends
            else if (s.value.isNotEmpty()) IconBtn(IconSpec(Lucide.X, "지우기", { s.onValue("") }, tag = "search_clear"))
        }
        val w = Modifier.width(if (narrow) CarSize.Control.searchButtonNarrow else CarSpace.searchButton)
        if (s.onVoice != null && s.value.isBlank() && !s.loading)
            PrimaryButton(Btn("말하기", { fm.clearFocus(); kb?.hide(); s.onVoice.invoke() }, icon = Lucide.Mic, tag = "voice", ai = true, iconOnly = narrow), w, fill = true)
        else PrimaryButton(Btn("찾기", { submit() }, enabled = s.value.isNotBlank() && !s.loading, tag = "search_run", ai = true), w, fill = true)
    }
}
