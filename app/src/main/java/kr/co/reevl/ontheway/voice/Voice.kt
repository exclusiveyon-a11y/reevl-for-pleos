package kr.co.reevl.ontheway.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Voice first (2026-11-10 owner: "키보드보다는 음성 지시 위주로 최대한. 차라는 점을 잊지마").
 *
 * Two voice paths, in this order:
 *  1. **Gleo** (the car's assistant) — hands-free, works while driving: "글레오, 리블에서 …" reaches our App Schema functions
 *     (gleo/GleoReceiver: SEARCH_PROPERTIES · ANALYZE_PROPERTY · MANAGE_VISIT_PLAN). The app can only *tell* the driver what to say.
 *  2. **In-app speech** while parked — [VoiceInput]. Only when the car has a speech recognizer another app may bind
 *     ([VoiceProbe]). Pleos Connect 2.1.0 (emulator, 2026-11-10): the one RecognitionService is Gleo's
 *     `ai.umos.ambientai/.service.AmbRecognitionService`, `exported=false` → unusable by us; `RECOGNIZE_SPEECH` has no activity.
 *     The Pleos STT SDK is "준비 중". So the 말하기 button shows the Gleo phrase instead — never a fake recogniser.
 *     A real STT (the Pleos SDK when it ships) plugs in by implementing [VoiceInput].
 */
interface VoiceInput {
    /** true = a recogniser this app may use exists (checked, not assumed). */
    fun available(): Boolean
    /** Starts listening; [onPartial] while speaking, then exactly one of [onText] / [onEnd]. */
    fun listen(onPartial: (String) -> Unit, onText: (String) -> Unit, onEnd: (VoiceEnd) -> Unit)
    fun cancel()
}

enum class VoiceEnd { NoMatch, Error, NoPermission }

/** One RecognitionService the package manager knows of. */
data class RecognizerSvc(val pkg: String, val cls: String, val exported: Boolean, val enabled: Boolean = true)

object VoiceProbe {
    /** The service we may bind: enabled and exported, or our own. Gleo's private recogniser (exported=false) does not count. */
    fun pick(services: List<RecognizerSvc>, ownPkg: String): RecognizerSvc? =
        services.firstOrNull { it.enabled && (it.exported || it.pkg == ownPkg) }

    fun services(ctx: Context): List<RecognizerSvc> = runCatching {
        @Suppress("DEPRECATION")
        ctx.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), PackageManager.GET_META_DATA).map {
            RecognizerSvc(it.serviceInfo.packageName, it.serviceInfo.name, it.serviceInfo.exported, it.serviceInfo.enabled)
        }
    }.getOrDefault(emptyList())
}

/** What the app shows when it cannot listen itself: the Gleo phrase (native Gleo screens quote their examples the same way). */
object VoiceHints {
    const val WAKE = "글레오"
    const val INVOKE = "리블" // assets/schema.json invokeWord
    fun say(command: String) = "“$WAKE, ${INVOKE}에서 $command”"

    /** Lock screen + HOME: one line, the most useful request while driving. */
    val DRIVING = say("1천 세대 넘는 곳 찾아줘")

    /** The voice panel (parked): a search, a question about here, a plan action — one per App Schema function. */
    fun examples(sided: Boolean, hasPlan: Boolean): List<String> = listOf(
        say(if (sided) "오른쪽 8억 이하 찾아줘" else "1천 세대 넘는 곳 찾아줘"),
        say("이 동네 어때?"),
        say(if (hasPlan) "오늘 방문 알려줘" else "첫 번째 단지 저장해 줘"),
    )
}

/**
 * One-tap requests (no typing), built from what the car knows now. Only queries the AI search understands; a context
 * that is not there is never offered (no `방금 지나온` without a trail, no `오른쪽` without a trusted heading).
 */
object VoiceSuggest {
    fun build(sided: Boolean, passedRecently: Boolean, trailOn: Boolean): List<String> = buildList {
        if (trailOn && passedRecently) add("방금 지나온 신축")
        add(if (sided) "오른쪽 8억 이하" else "8억 이하")
        add("1,000세대 넘는 곳")
        add("30평대 10억 이하")
        if (trailOn && !passedRecently) add("방금 지나온 신축")
    }.distinct().take(4)
}

/** Android SpeechRecognizer bound to [VoiceProbe.pick]'s service (ko-KR). */
class AndroidVoiceInput(private val ctx: Context, private val log: (String, Map<String, Any?>) -> Unit) : VoiceInput {
    private var rec: SpeechRecognizer? = null

    private fun target(): RecognizerSvc? = VoiceProbe.pick(VoiceProbe.services(ctx), ctx.packageName)

    override fun available(): Boolean {
        if (!kr.co.reevl.ontheway.Features.VOICE_INPUT) return false // Pleos flavor: no RECORD_AUDIO declared
        val all = VoiceProbe.services(ctx)
        val t = VoiceProbe.pick(all, ctx.packageName)
        log("voice.probe", mapOf("services" to all.joinToString("|") { "${it.pkg}/${it.cls}:exported=${it.exported}" }, "usable" to (t != null)))
        return t != null
    }

    override fun listen(onPartial: (String) -> Unit, onText: (String) -> Unit, onEnd: (VoiceEnd) -> Unit) {
        val t = target() ?: return onEnd(VoiceEnd.Error)
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return onEnd(VoiceEnd.NoPermission)
        cancel()
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(ctx, ComponentName(t.pkg, t.cls)) }.getOrNull() ?: return onEnd(VoiceEnd.Error)
        rec = r
        var done = false
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial) }
            override fun onResults(b: Bundle?) {
                if (done) return; done = true
                val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (text.isNullOrEmpty()) onEnd(VoiceEnd.NoMatch) else onText(text)
            }
            override fun onError(error: Int) {
                if (done) return; done = true
                log("voice.error", mapOf("code" to error))
                onEnd(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) VoiceEnd.NoMatch
                      else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) VoiceEnd.NoPermission else VoiceEnd.Error)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        runCatching { r.startListening(i) }.onFailure { done = true; onEnd(VoiceEnd.Error) }
    }

    override fun cancel() {
        rec?.let { runCatching { it.cancel(); it.destroy() } }
        rec = null
    }
}
