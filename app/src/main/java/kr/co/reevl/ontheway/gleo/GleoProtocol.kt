package kr.co.reevl.ontheway.gleo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Gleo Interface wire format (document.pleos.ai pleos-only/app-integration-guide, local copy 2026-10-19/26):
 *  · Action: intent action = function name, `setPackage(ours)`, parameters in the `ACTION_PARAM` string extra. The guide's
 *    own samples disagree on its shape (a quoted JSON string of the parameters vs. an object carrying `actionParameters`),
 *    and §1.1 also says each key may be `putExtra`'d on its own — [parseParams] accepts all three.
 *  · Callback: broadcast `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` with extras `result` (= status),
 *    `packageName`, `response` (JSON: functionName, status, message, data = JSON array string, mode).
 *    Status ∈ success | asking | fail (the guide has no "error": system errors are `fail`). Gleo cancels after 7 s.
 */
object GleoProtocol {
    const val ACTION_PARAM = "ACTION_PARAM"
    const val CALLBACK_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    const val SEARCH = "SEARCH_PROPERTIES"
    const val ANALYZE = "ANALYZE_PROPERTY"
    const val PLAN = "MANAGE_VISIT_PLAN"
    val FUNCTIONS = listOf(SEARCH, ANALYZE, PLAN)
    /** Gleo AI on the Pleos image (declares the signature permission [APP_ACTION_PERMISSION]). */
    const val GLEO_PACKAGE = "ai.umos.ambientai"
    const val APP_ACTION_PERMISSION = "ai.umos.gleo.permission.APP_ACTION"

    /** null = the platform did not tell us (sender did not share its identity) → allowed (the permission gate still applies). */
    fun senderAllowed(sender: String?): Boolean = sender == null || sender == GLEO_PACKAGE

    /** Gleo gives up after 7 s; we answer before that. */
    const val TIMEOUT_MS = 6_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** ACTION_PARAM (quoted or not, flat or under `actionParameters`) + any individual string extras → name → value. */
    fun parseParams(actionParam: String?, extras: Map<String, String?> = emptyMap()): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        extras.forEach { (k, v) -> if (k != ACTION_PARAM && v != null) out[k] = v }
        var raw = actionParam?.trim().orEmpty()
        repeat(2) { // a JSON string that itself holds JSON ("\"{...}\"")
            if (raw.startsWith("\"")) raw = runCatching { json.parseToJsonElement(raw).jsonPrimitive.content }.getOrDefault(raw.trim('"')).trim()
        }
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return out
        val params = (obj["actionParameters"] as? JsonObject) ?: obj
        params.forEach { (k, v) -> text(v)?.let { out[k] = it } }
        return out
    }

    private fun text(e: JsonElement): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> e.joinToString(" ") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
        else -> e.toString()
    }
}

enum class GleoStatus(val wire: String) { Success("success"), Asking("asking"), Fail("fail") }

/** One Callback. [data] = list of small key → value objects (sent as a JSON array string, as the guide's schema says). */
data class GleoCallback(val functionName: String, val status: GleoStatus, val message: String, val data: List<Map<String, String>> = emptyList()) {
    fun toJson(): String = buildJsonObject {
        put("functionName", functionName)
        put("status", status.wire)
        put("message", message)
        if (data.isNotEmpty()) put("data", JsonArray(data.map { m -> JsonObject(m.mapValues { JsonPrimitive(it.value) }) }).toString())
    }.toString()
}
