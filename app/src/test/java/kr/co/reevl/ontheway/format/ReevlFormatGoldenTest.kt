package kr.co.reevl.ontheway.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every row of `golden/reevl_format.json` was produced by running the real Reevl web TS
 * (lib/rt.ts, KeyStats.tsx, TradeHighlights.tsx) — see tools/gen_format_golden.mjs. The Kotlin port must give the
 * exact same string for every input.
 */
class ReevlFormatGoldenTest {

    private val golden: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("golden/reevl_format.json")!!.readText(),
    ).jsonObject
    private val year = golden["currentYear"]!!.jsonPrimitive.int

    private fun num(e: JsonElement): Double? = when (e) {
        JsonNull -> null
        is JsonObject -> when (e["special"]!!.jsonPrimitive.content) {
            "NaN" -> Double.NaN
            "Infinity" -> Double.POSITIVE_INFINITY
            "-Infinity" -> Double.NEGATIVE_INFINITY
            else -> error("unknown special $e")
        }
        else -> e.jsonPrimitive.double
    }

    private fun str(e: JsonElement): String? = if (e is JsonNull) null else e.jsonPrimitive.contentOrNull

    private fun unit(e: JsonElement) = if (str(e) == "py") AreaUnit.PY else AreaUnit.M2

    private fun run(fn: String, a: JsonArray): String? = when (fn) {
        "won" -> ReevlFormat.won(num(a[0]))
        "wonShort" -> ReevlFormat.wonShort(num(a[0]))
        "eok" -> ReevlFormat.eok(num(a[0]))
        "num" -> ReevlFormat.num(num(a[0]))
        "pct" -> ReevlFormat.pct(num(a[0]), a[1].jsonPrimitive.int)
        "areaFmt" -> ReevlFormat.areaFmt(num(a[0]), unit(a[1]), str(a[2]))
        "areaFmtBoth" -> ReevlFormat.areaFmtBoth(num(a[0]), unit(a[1]), str(a[2]))
        "distTxt" -> ReevlFormat.distTxt(num(a[0])!!)
        "stName" -> ReevlFormat.stName(str(a[0]))
        "avatarColor" -> ReevlFormat.avatarColor(str(a[0])!!)
        "approveYM" -> ReevlFormat.approveYM(str(a[0]))
        "yearsSince" -> ReevlFormat.yearsSince(str(a[0]), year)?.toString()
        "mdD" -> ReevlFormat.mdD(str(a[0])!!)
        else -> error("no port for $fn")
    }

    @Test fun everyGoldenRow_matchesTheWebOutput() {
        val rows = golden["rows"]!!.jsonArray
        assertTrue("golden file has rows", rows.size > 250)
        val failures = mutableListOf<String>()
        for (r in rows) {
            val o = r.jsonObject
            val fn = o["fn"]!!.jsonPrimitive.content
            val args = o["args"]!!.jsonArray
            val want = o["value"]!!.let { if (it is JsonNull) null else (it as JsonPrimitive).content }
            val got = run(fn, args)
            if (got != want) failures += "$fn$args → kotlin=$got web=$want"
        }
        assertEquals("mismatches:\n" + failures.joinToString("\n"), 0, failures.size)
    }

    @Test fun constants_matchTheWeb() {
        val c = golden["constants"]!!.jsonObject
        assertEquals(c["PY"]!!.jsonPrimitive.double, ReevlFormat.PY, 0.0)
        assertEquals(c["areaRatioApt"]!!.jsonPrimitive.double, ReevlFormat.areaRatio("apt"), 0.0)
        assertEquals(c["areaRatioOfficetel"]!!.jsonPrimitive.double, ReevlFormat.areaRatio("officetel"), 0.0)
        assertEquals(c["areaRatioLand"]!!.jsonPrimitive.double, ReevlFormat.areaRatio("land"), 0.0)
    }

    // TradeHighlights.tsx:164 and :177-179 are inline JSX expressions (not functions) — asserted against the
    // expressions' own output for the A7 demo trades (경희궁자이(2단지), 2026-10-26).
    @Test fun recentTradeRow_dateAndDelta_followTradeHighlights() {
        assertEquals("09.28", ReevlFormat.recentRowDate("2026-09-28"))
        assertEquals("▲6,500만", ReevlFormat.deltaText(236500.0 - 230000.0))
        assertEquals("▼4,000만", ReevlFormat.deltaText(273000.0 - 277000.0))
        assertEquals("보합", ReevlFormat.deltaText(0.0))
    }
}
