package kr.co.reevl.ontheway.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA loop 2026-11-10 (owner: "키보드보다는 음성 지시 위주로 최대한. 차라는 점을 잊지마"). */
class VoiceTest {
    private val own = "kr.co.reevl.ontheway"

    @Test fun probe_gleoPrivateRecognizer_isNotUsable() {
        // Pleos Connect 2.1.0 emulator, `pm query-services -a android.speech.RecognitionService`: Gleo's service, exported=false.
        val gleo = RecognizerSvc("ai.umos.ambientai", "ai.umos.ambientai.service.AmbRecognitionService", exported = false)
        assertNull(VoiceProbe.pick(listOf(gleo), own))
        assertNull(VoiceProbe.pick(emptyList(), own))
    }

    @Test fun probe_exportedOrOwnRecognizer_isUsable() {
        val stt = RecognizerSvc("ai.pleos.stt", "ai.pleos.stt.Svc", exported = true)
        val gleo = RecognizerSvc("ai.umos.ambientai", "x", exported = false)
        assertEquals(stt, VoiceProbe.pick(listOf(gleo, stt), own))
        assertEquals(own, VoiceProbe.pick(listOf(RecognizerSvc(own, "Mine", exported = false)), own)?.pkg)
        assertNull(VoiceProbe.pick(listOf(stt.copy(enabled = false)), own))
    }

    @Test fun hints_followTheNativeGleoPattern() {
        // Native Gleo screens quote what to say: “글레오, …”; the app is reached by its schema invokeWord.
        val schema = File("src/main/assets/schema.json").readText()
        assertTrue(schema.contains("\"invokeWord\": \"${VoiceHints.INVOKE}\""))
        assertTrue(VoiceHints.DRIVING.startsWith("“글레오, 리블에서 "))
        assertTrue(VoiceHints.DRIVING.endsWith("”"))
        assertEquals(3, VoiceHints.examples(sided = true, hasPlan = true).size)
        assertTrue(VoiceHints.examples(sided = false, hasPlan = false).none { it.contains("오른쪽") || it.contains("오늘 방문") })
    }

    @Test fun suggestions_neverOfferAContextTheCarDoesNotHave() {
        val plain = VoiceSuggest.build(sided = false, passedRecently = false, trailOn = false)
        assertFalse(plain.any { it.contains("오른쪽") || it.contains("왼쪽") })
        assertFalse(plain.any { it.contains("지나온") }) // trail off (설정 '지나온 길 기억' 꺼짐) → never
        assertTrue(VoiceSuggest.build(sided = true, passedRecently = false, trailOn = false).first().startsWith("오른쪽"))
        // just passed something → that request comes first
        assertEquals("방금 지나온 신축", VoiceSuggest.build(sided = true, passedRecently = true, trailOn = true).first())
        assertTrue(VoiceSuggest.build(true, true, true).size <= 4)
    }

    @Test fun field_offersVoiceFirst_andLockShowsTheGleoPhrase() {
        val field = File("src/main/java/kr/co/reevl/ontheway/ui/ds/comp/SearchField.kt").readText()
        assertTrue(field.contains("Btn(\"말하기\""))
        assertTrue(field.contains("iconOnly = narrow")) // 128 dp narrow button showed `말…`
        val vm = File("src/main/java/kr/co/reevl/ontheway/AppViewModel.kt").readText()
        assertTrue(vm.substringAfter("fun startVoice(").substringBefore("fun closeVoice").contains("typeQuery(\"\")"))
        val lock = File("src/main/java/kr/co/reevl/ontheway/ui/ds/comp/Lock.kt").readText()
        // The Gleo phrase is there, but only behind Features.GLEO (off until Pleos approves the schema).
        assertTrue(lock.contains("if (kr.co.reevl.ontheway.Features.GLEO)") && lock.contains("VoiceHints.DRIVING"))
        // nothing pressable on the lock: the hint is a line, not a button
        assertFalse(lock.contains("Btn("))
    }

    /** Gleo claims stay behind one switch (Features.GLEO). Default true since 2026-11-14 (contest: no approval step). */
    @Test fun gleoClaims_allBehindTheFlag_onByDefault() {
        assertTrue("GLEO_ENABLED must default to true (contest build)", kr.co.reevl.ontheway.BuildConfig.GLEO_ENABLED)
        val root = File("src/main/java/kr/co/reevl/ontheway")
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" && !it.path.contains("/gleo/") && !it.path.endsWith("voice/Voice.kt") }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l ->
                val code = l.substringBefore("//").trim()
                val claims = code.contains("VoiceHints.") || code.contains("\"글레오에게") || code.contains("글레오가")
                if (claims && !code.contains("Features.GLEO") && !code.trimStart().startsWith("*")) "${f.name}:${i + 1}" else null } }.toList()
        // the lines guarded on the line before (Lock.kt `if (Features.GLEO)` + next line) are listed here explicitly
        assertEquals(listOf("Lock.kt"), offenders.map { it.substringBefore(':') }.distinct().filter { it != "UiBuilders.kt" })
        val b = File("src/main/java/kr/co/reevl/ontheway/ui/state/UiBuilders.kt").readText()
        assertTrue(b.contains("if (!kr.co.reevl.ontheway.Features.GLEO) emptyList() else kr.co.reevl.ontheway.voice.VoiceHints.examples("))
    }
}
