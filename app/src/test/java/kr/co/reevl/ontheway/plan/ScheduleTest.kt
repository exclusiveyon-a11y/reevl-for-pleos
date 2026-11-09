package kr.co.reevl.ontheway.plan

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import kr.co.reevl.ontheway.data.Api
import kr.co.reevl.ontheway.data.MeetingPoint
import kr.co.reevl.ontheway.data.Plan
import kr.co.reevl.ontheway.data.PlanStop
import kr.co.reevl.ontheway.data.PlaceRef
import kr.co.reevl.ontheway.navi.RouteSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7 §3-B sentences, §9-6-A ETA contract, §3-C insert rule, slide 11 customer view. */
class ScheduleTest {

    private val day = LocalDate.parse("2026-11-07")
    private val office = MeetingPoint("데모 공인중개사사무소", 37.5694, 126.9638, verified = true, source = "data.go.kr",
        placeRef = PlaceRef("office", id = "o1"))

    private fun stop(id: String, pos: Int, status: String = "planned", at: String? = null, mp: MeetingPoint? = null, name: String = "단지$id") =
        PlanStop(id = id, position = pos, kind = "apartment_complex", cid = "cid-$id", name = name, address = "서울", lat = 37.5, lng = 126.9,
            locationVerified = true, visitStatus = status, note = "내부 메모", appointmentAt = at, meetingPoint = mp)

    private fun tour(vararg s: PlanStop, date: String? = "2026-11-07", mode: String = "tour") =
        Plan("p", "토요일 임장", date, "planned", 1, 0, s.toList(), mode = mode, clientLabel = "고객 A")

    private val demo = tour(
        stop("a", 1, at = "2026-11-07T14:00:00+09:00", mp = office, name = "경희궁자이(2단지)"),
        stop("b", 2, at = "2026-11-07T14:40:00+09:00", name = "경희궁자이(3단지)"),
        stop("c", 3, at = "2026-11-07T15:20:00+09:00", name = "경희궁롯데캐슬"),
    )

    private fun eta(hhmm: String, source: Schedule.EtaSource = Schedule.EtaSource.NAVI_VERIFIED) =
        Schedule.Eta(OffsetDateTime.parse("2026-11-07T$hhmm:00+09:00").toInstant(), source)

    // ── parsing (tolerant) ──

    @Test fun planJson_withoutV7Fields_decodesAsBasic_andNullsAreTolerated() {
        val old = Api.json.decodeFromString(Plan.serializer(),
            """{"id":"p","title":"t","status":"planned","version":1,"updatedAt":0,"stops":[{"id":"s","position":1,"kind":"apartment_complex","name":"n","visitStatus":"planned"}]}""")
        assertEquals("basic", old.mode)
        assertNull(old.clientLabel)
        assertNull(old.stops[0].appointmentAt)
        assertNull(old.stops[0].meetingPoint)
        assertFalse(Tour.isTour(old))
        val nulls = Api.json.decodeFromString(Plan.serializer(),
            """{"id":"p","title":"t","status":"planned","version":1,"updatedAt":0,"mode":null,"clientLabel":null,
               "stops":[{"id":"s","position":1,"kind":"apartment_complex","name":"n","visitStatus":"planned","appointmentAt":null,"meetingPoint":null}]}""")
        assertEquals("basic", nulls.mode)
    }

    @Test fun planJson_tourFields_decode() {
        val p = Api.json.decodeFromString(Plan.serializer(), """
            {"id":"p","title":"토요일 임장","plannedDate":"2026-11-07","status":"planned","version":4,"updatedAt":0,
             "mode":"tour","clientLabel":"고객 A",
             "stops":[{"id":"s1","position":1,"kind":"apartment_complex","name":"경희궁자이(2단지)","visitStatus":"planned",
               "appointmentAt":"2026-11-07T14:00:00+09:00",
               "meetingPoint":{"label":"OO공인중개사사무소","lat":37.5694,"lng":126.9638,"verified":true,"source":"seoul-open-data",
                               "placeRef":{"kind":"office","id":"o1"}}}]}""".trimIndent())
        assertTrue(Tour.isTour(p))
        assertEquals("고객 A", Tour.clientLabel(p))
        assertEquals("고객 투어 모드 · 고객 A", Tour.modeLine(p))
        assertEquals("14:00", Schedule.hhmm(Schedule.appointment(p.stops[0])!!))
        assertEquals("o1", p.stops[0].meetingPoint!!.placeRef!!.id)
        assertTrue(Schedule.canGuideToMeeting(p.stops[0].meetingPoint))
    }

    @Test fun clientLabel_onlyInTourMode() {
        assertNull(Tour.clientLabel(demo.copy(mode = "basic")))
        assertNull(Tour.modeLine(demo.copy(mode = "basic")))
        assertNull(Tour.clientLabel(demo.copy(clientLabel = "  ")))
        assertEquals("고객 투어 모드", Tour.modeLine(demo.copy(clientLabel = null)))
    }

    @Test fun badAppointmentString_isNoAppointment() {
        assertNull(Schedule.parse("14:00"))
        assertNull(Schedule.parse(""))
        assertNull(Schedule.parse(null))
        assertNotNull(Schedule.parse("2026-11-07T14:00:00+09:00"))
    }

    @Test fun spokenTimes() {
        fun t(s: String) = OffsetDateTime.parse("2026-11-07T$s:00+09:00")
        assertEquals("오후 2시", Schedule.spoken(t("14:00")))
        assertEquals("오후 2시 40분", Schedule.spoken(t("14:40")))
        assertEquals("오전 9시", Schedule.spoken(t("09:00")))
        assertEquals("오후 12시", Schedule.spoken(t("12:00")))
        assertEquals("오전 12시 5분", Schedule.spoken(t("00:05")))
    }

    // ── §3-B / §9-6-A ──

    @Test fun late_withNaviVerifiedEta_isTheSection3BSentence() {
        val c = Schedule.appointmentCard(demo, eta("14:12"), day)!!
        assertEquals("다음 약속 14:00 · 경희궁자이(2단지)", c.title)
        assertEquals("도착 예정 14:12", c.etaLine)
        assertEquals(12, c.delayMinutes)
        assertEquals("오후 2시 약속에 약 12분 늦을 수 있습니다.", c.sentence)
        assertTrue(c.warn)
        assertEquals("만남 장소 · 데모 공인중개사사무소", c.meetingLine)
        assertNull(c.dayNote)
        assertEquals(Schedule.EtaSource.NAVI_VERIFIED, c.etaSource)
    }

    @Test fun noEta_isTheUnknownSentence_andNoDelay() {
        val c = Schedule.appointmentCard(demo, null, day)!!
        assertNull(c.etaLine)
        assertNull(c.delayMinutes)
        assertEquals("약속은 오후 2시입니다. 도착 예정시간을 확인할 수 없습니다.", c.sentence)
        assertFalse(c.warn)
    }

    @Test fun etaThatIsNotNaviVerified_isNeverCompared() {
        val c = Schedule.appointmentCard(demo, eta("14:30", Schedule.EtaSource.UNAVAILABLE), day)!!
        assertNull(c.etaLine)
        assertNull(c.delayMinutes)
        assertEquals("약속은 오후 2시입니다. 도착 예정시간을 확인할 수 없습니다.", c.sentence)
        assertNull(Schedule.delayMinutes(Schedule.appointment(demo.stops[0]), eta("14:30", Schedule.EtaSource.UNAVAILABLE)))
    }

    @Test fun early_orOnTime_isZeroDelay_andNoWarning() {
        val c = Schedule.appointmentCard(demo, eta("13:50"), day)!!
        assertEquals(0, c.delayMinutes)
        assertEquals("도착 예정 13:50", c.etaLine)
        assertNull(c.sentence)
        assertFalse(c.warn)
        // under the initial 5-minute threshold: the ETA is shown, no warning sentence
        val d = Schedule.appointmentCard(demo, eta("14:03"), day)!!
        assertEquals(3, d.delayMinutes)
        assertNull(d.sentence)
    }

    @Test fun notToday_showsTheTime_withoutAnyEtaComparison() {
        val c = Schedule.appointmentCard(demo, eta("14:12"), LocalDate.parse("2026-10-28"))!!
        assertEquals("다음 약속 14:00 · 경희궁자이(2단지)", c.title)
        assertNull(c.etaLine)
        assertNull(c.delayMinutes)
        assertNull(c.sentence)
        assertEquals("11월 7일 (토) 약속", c.dayNote)
    }

    @Test fun noAppointment_onTheNextStop_meansNoCard_onlyTheNextStop() {
        val p = tour(stop("a", 1), stop("b", 2, at = "2026-11-07T14:40:00+09:00"))
        assertNull(Schedule.appointmentCard(p, eta("14:12"), day))
        assertNull(Schedule.appointmentCard(demo.copy(mode = "basic"), eta("14:12"), day)) // basic mode: no card
    }

    @Test fun afterVisitDone_theCardMovesToTheNextAppointment() {
        val p = demo.copy(stops = demo.stops.map { if (it.id == "a") it.copy(visitStatus = "visited") else it })
        val c = Schedule.appointmentCard(p, null, day)!!
        assertEquals("다음 약속 14:40 · 경희궁자이(3단지)", c.title)
        assertEquals("약속은 오후 2시 40분입니다. 도착 예정시간을 확인할 수 없습니다.", c.sentence)
        assertEquals("만남 장소 · 단지 대표 위치", c.meetingLine)
    }

    @Test fun sameTimeClash_isTheClashSentence() {
        val p = tour(stop("a", 1, at = "2026-11-07T14:00:00+09:00"), stop("b", 2, at = "2026-11-07T14:00:00+09:00"))
        assertEquals("오후 2시에 방문 일정이 두 개 있습니다. 어느 곳을 먼저 방문할까요?", Schedule.appointmentCard(p, null, day)!!.sentence)
    }

    @Test fun etaFromNavi_onlyForAVerifiedRouteWithAReportTime() {
        val at = Instant.parse("2026-11-07T04:40:00Z").toEpochMilli() // 13:40 KST
        val r = RouteSummary("경희궁자이(2단지)", 24000, 1980, at)
        val e = Schedule.etaFromNavi(r, verified = true)!!
        assertEquals("14:13", Schedule.hhmm(e.at))
        assertEquals(Schedule.EtaSource.NAVI_VERIFIED, e.source)
        assertNull(Schedule.etaFromNavi(r, verified = false))
        assertNull(Schedule.etaFromNavi(r.copy(reportedAtMs = null), verified = true))
        assertNull(Schedule.etaFromNavi(r.copy(durationS = null), verified = true))
        assertNull(Schedule.etaFromNavi(null, verified = true))
    }

    @Test fun meetingPoint_unverified_hasNoGuide() {
        val raw = MeetingPoint("정문 앞 상가", 37.5, 126.9, verified = false, source = "client")
        assertFalse(Schedule.canGuideToMeeting(raw))
        assertEquals("만남 장소 · 정문 앞 상가 (위치 확인 필요)", Schedule.meetingLine(raw))
        assertFalse(Schedule.canGuideToMeeting(office.copy(lat = null)))
        assertFalse(Schedule.canGuideToMeeting(null))
        assertTrue(Schedule.canGuideToMeeting(office))
    }

    @Test fun perStopLabels_andFirstAppointment() {
        assertEquals("14:40 약속", Schedule.stopTimeLabel(demo, demo.stops[1]))
        assertNull(Schedule.stopTimeLabel(demo.copy(mode = "basic"), demo.stops[1]))
        assertEquals("14:00", Schedule.hhmm(Schedule.firstAppointment(demo)!!))
    }

    // ── insert (§3, §3-C 기능 2) ──

    @Test fun insert_afterTheLastVisitedStop_visitedUntouched() {
        val p = demo.copy(stops = demo.stops.map { if (it.id == "a") it.copy(visitStatus = "visited") else it })
        assertEquals("a", Insert.anchor(p)!!.id)
        assertEquals("경희궁자이(2단지) → 경희궁자이(4단지) → 경희궁자이(3단지) → 경희궁롯데캐슬", Insert.previewLine(p, "경희궁자이(4단지)"))
        val rows = Insert.preview(p, "경희궁자이(4단지)")
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.position })
        assertEquals("visited", rows[0].visitStatus)
        assertTrue(rows[1].isNew)
        assertEquals("경희궁자이(2단지) 다음에 경희궁자이(4단지)를 방문할까요?", Insert.question(p, "경희궁자이(4단지)"))
    }

    @Test fun insert_withNothingVisited_goesAfterTheCurrentStop() {
        assertEquals("a", Insert.anchor(demo)!!.id)
        assertEquals("경희궁자이(2단지) → 새 곳 → 경희궁자이(3단지) → 경희궁롯데캐슬", Insert.previewLine(demo, "새 곳"))
    }

    @Test fun insert_afterASkippedLaterStop_keepsEverythingBeforeIt() {
        val p = demo.copy(stops = demo.stops.map { if (it.id != "c") it.copy(visitStatus = if (it.id == "a") "visited" else "skipped") else it })
        assertEquals("b", Insert.anchor(p)!!.id)
        assertEquals("경희궁자이(2단지) → 경희궁자이(3단지) → X → 경희궁롯데캐슬", Insert.previewLine(p, "X"))
    }

    @Test fun addedStop_isTheNewId() {
        val after = demo.copy(stops = demo.stops + stop("n", 4, name = "경희궁자이(4단지)"))
        assertEquals("n", Insert.addedStop(demo, after)!!.id)
        assertNull(Insert.addedStop(demo, demo))
    }

    @Test fun josa() {
        assertEquals("를", Josa.eulReul("경희궁자이(4단지)"))
        assertEquals("을", Josa.eulReul("경희궁롯데캐슬"))
        assertEquals("를", Josa.eulReul("○○공인중개사사무소"))
        assertEquals("을", Josa.eulReul("래미안1"))
        assertEquals("를", Josa.eulReul("래미안2"))
        assertEquals("를", Josa.eulReul("Xi"))
    }

    // ── customer view (slide 11) ──

    @Test fun customerView_hasOrderAndNamesOnly_noClientOrAppointment() {
        val items = CustomerView.items(demo)
        assertEquals(listOf("경희궁자이(2단지)", "경희궁자이(3단지)", "경희궁롯데캐슬"), items.map { it.name })
        assertEquals(listOf(1, 2, 3), items.map { it.position })
        val text = items.toString() + CustomerView.headline(demo)
        for (secret in listOf("고객 A", "14:00", "14:40", "데모 공인중개사사무소", "내부 메모", "토요일 임장")) {
            assertFalse("customer view leaks '$secret'", text.contains(secret))
        }
        assertEquals("함께 볼 곳 3곳", CustomerView.headline(demo))
    }
}
