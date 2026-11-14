package kr.co.reevl.ontheway.search

import java.time.ZoneId
import java.time.ZonedDateTime
import kr.co.reevl.ontheway.data.Memo
import kr.co.reevl.ontheway.data.RecentItem
import kr.co.reevl.ontheway.data.RecentResponse
import kr.co.reevl.ontheway.data.Screener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 휴대폰 ↔ 차 동기화 rules (search/Recent.kt). */
class RecentTest {
    private val kst = ZoneId.of("Asia/Seoul")
    private fun at(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, kst).toInstant().toEpochMilli()
    private val now = at(10, 15)

    @Test fun merged_phoneAndCar_oneRowPerComplex_newerWins_newestFirst_apartmentsOnly() {
        val server = RecentResponse(listOf(
            RecentItem("11110|A|동|1", "A", households = 1000, viewedAt = at(10, 9), source = "web"),
            RecentItem("11110|B|동|2", "B", viewedAt = at(9, 20), source = "car"),
            RecentItem("officetel~11110|C|동|3", "C", viewedAt = at(10, 14), source = "web")))
        val pending = listOf(RecentItem("11110|B|동|2", "B", viewedAt = at(10, 14, 50), source = "car"))
        val m = Recent.merged(server, pending)
        assertEquals(listOf("11110|B|동|2", "11110|A|동|1"), m.map { it.cid })
        assertEquals(at(10, 14, 50), m[0].viewedAt)
        assertEquals(1000, m[1].households)
    }

    @Test fun note_movesToTheFront_onlyRealComplexes_capped() {
        var p = emptyList<RecentItem>()
        repeat(35) { i -> p = Recent.note(p, RecentItem("11110|X$i|동|$i", "X$i"), now + i) }
        assertEquals(Recent.KEEP, p.size)
        p = Recent.note(p, RecentItem("11110|X10|동|10", "X10"), now + 100)
        assertEquals("11110|X10|동|10", p[0].cid)
        assertEquals("car", p[0].source)
        assertEquals(1, p.count { it.cid == "11110|X10|동|10" })
        assertTrue(Recent.note(p, RecentItem("stop:1", "x"), now) === p)
        assertTrue(Recent.note(p, RecentItem("11110|Y|동|1", null), now) === p)
    }

    @Test fun sourceLabel_saysWhereAndWhen_neverInventsAnUnknownTime() {
        assertEquals("휴대폰에서 · 방금", Recent.sourceLabel(RecentItem("c", viewedAt = now - 20_000, source = "web"), now, kst))
        assertEquals("이 차에서 · 12분 전", Recent.sourceLabel(RecentItem("c", viewedAt = now - 12 * 60_000, source = "car"), now, kst))
        assertEquals("휴대폰에서 · 6시간 전", Recent.sourceLabel(RecentItem("c", viewedAt = at(10, 9), source = "web"), now, kst))
        assertEquals("휴대폰에서 · 어제", Recent.sourceLabel(RecentItem("c", viewedAt = at(9, 23), source = "web"), now, kst))
        assertEquals("휴대폰에서 · 3일 전", Recent.sourceLabel(RecentItem("c", viewedAt = at(7, 10), source = "web"), now, kst))
        assertEquals("휴대폰에서 · 10.20", Recent.sourceLabel(RecentItem("c", viewedAt = ZonedDateTime.of(2026, 10, 20, 9, 0, 0, 0, kst).toInstant().toEpochMilli(), source = "web"), now, kst))
        assertEquals("휴대폰에서", Recent.sourceLabel(RecentItem("c", viewedAt = at(9, 9), source = "web", approx = true), now, kst))
    }

    @Test fun syncDue_fiveMinutes_orForced_orFirst() {
        assertTrue(Recent.syncDue(null, 1000))
        assertFalse(Recent.syncDue(1000, 1000 + Recent.SYNC_EVERY_MS - 1))
        assertTrue(Recent.syncDue(1000, 1000 + Recent.SYNC_EVERY_MS))
        assertTrue(Recent.syncDue(1000, 1001, force = true))
    }

    @Test fun memo_ofCid_lineAndText() {
        val ms = listOf(Memo("a", "  로열동   12억 호가 ", at(9, 10)), Memo("b", " "))
        val m = Recent.memoOf(ms, "a")!!
        assertEquals("로열동 12억 호가", Recent.memoText(m))
        assertEquals("휴대폰에서 적은 메모 · 11.09", Recent.memoLine(m, kst))
        assertNull(Recent.memoOf(ms, "b"))
        assertNull(Recent.memoOf(null, "a"))
        assertEquals(80, Recent.memoText(Memo("a", "가".repeat(200))).length)
    }

    @Test fun chips_ownConditionsFirst_thenVoice_total() {
        val sc = listOf(Screener("1", "역세권 10억", "10억 이하", listOf("역세권")), Screener("2", "학군", null, listOf("학군")), Screener("3", "큰 단지", "1,000세대~9,000세대"))
        val c = Recent.chips(sc, listOf("오른쪽 8억 이하", "1,000세대 넘는 곳", "주변 큰 단지"), 3)
        assertEquals(listOf("역세권 10억", "큰 단지", "오른쪽 8억 이하"), c.map { it.label })
        assertEquals("10억 이하", c[0].q)
        assertEquals("1", c[0].screenerId)
        assertNull(c[2].screenerId)
        assertEquals(listOf("a", "b"), Recent.chips(null, listOf("a", "b", "c"), 2).map { it.label })
        assertEquals("역세권은 휴대폰에서만 볼 수 있어요", Recent.skippedNote(sc[0]))
        assertEquals(listOf("역세권 10억", "큰 단지", "주변 큰 단지"), Recent.chips(sc, listOf("10억 이하", "주변 큰 단지"), 3).map { it.label })
        val own = Recent.chips(sc, emptyList(), 2)
        assertEquals(listOf("a", "b"), Recent.idleVoice(own, listOf("10억 이하", "a", "b", "c")))
        assertEquals(listOf("a", "b", "c"), Recent.idleVoice(own.take(1), listOf("a", "b", "c", "d")))
        assertNull(Recent.skippedNote(sc[2]))
    }

    /** 0 server calls while driving-locked: every new sync entry checks the lock before asking (source gate, like QaLoopGateTest). */
    @Test fun syncEntries_holdWhileDriving() {
        val vm = java.io.File("src/main/java/kr/co/reevl/ontheway/AppViewModel.kt").readText()
        for (fn in listOf("fun syncAccount(", "private fun pushRecent(")) {
            val body = vm.substringAfter(fn).take(400)
            assertTrue("$fn must return while driving", body.contains("if (driving) { netSkip("))
        }
        assertTrue(vm.substringAfter("fun onForeground()").take(700).contains("!driving"))
    }
}
