package kr.co.reevl.ontheway.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.components.Banner
import kr.co.reevl.ontheway.ui.components.BannerKind
import kr.co.reevl.ontheway.ui.components.CarKeypad
import kr.co.reevl.ontheway.ui.components.CodeCells
import kr.co.reevl.ontheway.ui.components.MainHeader
import kr.co.reevl.ontheway.ui.components.ReevlLogo
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme

/**
 * LINK — 6-digit code from the phone (리블 › 내 계정 › 임장 계획 › 차량 연결). Google "minimise text entry" → digits
 * only, Pleos Keypad + Number Field. Landscape: instructions left, code + keypad right. 1:1: stacked.
 */
@Composable
fun LinkScreen(code: String, busy: Boolean, message: String?, onDigit: (Char) -> Unit, onDelete: () -> Unit, onSubmit: () -> Unit) {
    val wide = LocalWide.current
    ScreenFrame(tag = "screen_link", header = { MainHeader("차량 연결", onLogout = null) }) {
        if (wide) {
            Row(Modifier.fillMaxWidth().weight(1f).padding(CarDims.gutter), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                    Intro(message)
                }
                Column(Modifier.widthIn(max = 760.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CodeCells(code, invalid = message != null && code.isEmpty())
                    Box(Modifier.size(32.dp))
                    CarKeypad(onDigit, onDelete, onSubmit, submitEnabled = code.length == 6, busy = busy, modifier = Modifier.widthIn(max = 720.dp).testTag("keypad"))
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(CarDims.gutter),
                verticalArrangement = Arrangement.spacedBy(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Intro(message, compact = true)
                CodeCells(code, invalid = message != null && code.isEmpty(), cell = 100.dp)
                CarKeypad(onDigit, onDelete, onSubmit, submitEnabled = code.length == 6, busy = busy, modifier = Modifier.fillMaxWidth().testTag("keypad"))
            }
        }
    }
}

@Composable
private fun Intro(message: String?, compact: Boolean = false) {
    val c = CarTheme.colors
    val t = CarTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp), modifier = Modifier.fillMaxWidth()) {
        if (!compact) ReevlLogo(height = 56.dp)
        Text("휴대폰과 연결해 주세요", style = t.display)
        Text("휴대폰 리블에서 받은 6자리 코드를 입력하면 임장 계획을 이 차에서 볼 수 있어요.", style = t.body.copy(color = c.sub))
        // 1:1 window: the three steps folded into one short "where the code comes from" line (review Minor 2).
        if (compact) Text("코드 받는 곳 · 휴대폰 리블 › 내 계정 › 임장 계획 › 차량 연결", style = t.label.copy(color = c.ink2),
            modifier = Modifier.testTag("code_where"))
        if (!compact) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                Step(1, "휴대폰 리블 › 내 계정")
                Step(2, "임장 계획 › 차량 연결")
                Step(3, "화면에 나온 6자리 코드 입력")
            }
        }
        if (message != null) Banner(message, BannerKind.Error)
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val c = CarTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.brandSoft), contentAlignment = Alignment.Center) {
            Text("$n", style = CarTheme.type.label.copy(color = c.brand, fontWeight = FontWeight.ExtraBold))
        }
        Text(text, style = CarTheme.type.title.copy(fontWeight = FontWeight.SemiBold))
    }
}
