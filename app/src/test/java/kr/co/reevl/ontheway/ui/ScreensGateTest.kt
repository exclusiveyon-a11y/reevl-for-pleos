package kr.co.reevl.ontheway.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** car-ds-v2 §10.5 source gates: screens only fill templates; button words 2–4 characters; no implementation words. */
class ScreensGateTest {
    private val screens = File("src/main/java/kr/co/reevl/ontheway/ui/screens").listFiles()!!.filter { it.extension == "kt" }
    private val all = File("src/main/java/kr/co/reevl/ontheway").walk().filter { it.extension == "kt" }.toList()

    @Test fun screens_haveNoLayoutOfTheirOwn() {
        val hits = screens.flatMap { f -> Regex("""\b(Column|Row|Box|LazyColumn)\(""").findAll(f.readText()).map { "${f.name}: ${it.value}" }.toList() }
        assertEquals(emptyList<String>(), hits)
    }

    @Test fun buttonWords_are2to4Characters() {
        val labels = screens.flatMap { f -> Regex("""Btn\("([^"]+)"""").findAll(f.readText()).map { it.groupValues[1] }.toList() }
        assertTrue(labels.isNotEmpty())
        val bad = labels.filter { it.replace(" ", "").length !in 2..4 }
        assertEquals(emptyList<String>(), bad)
    }

    @Test fun noImplementationWordsOnScreen() {
        val banned = listOf("FAKE", "시연", "추정", "가명", "멈추기 직전", "대표 위치 기준", "방문 계획에 추가", "다음 장소로 안내", "첫 장소 안내", "계획 열기",
            "주행 중에는 단지 정보를 숨겨요", "고객 투어 모드")
        val ui = all.filter { it.path.contains("/ui/") }
        val hits = ui.flatMap { f -> banned.filter { f.readText().contains("\"$it") || f.readText().contains("$it\"") }.map { "${f.name}: $it" } }
        assertEquals(emptyList<String>(), hits)
    }
}
