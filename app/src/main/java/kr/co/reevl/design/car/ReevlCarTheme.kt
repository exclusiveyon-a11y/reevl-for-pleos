// 생성물 — reevl design-tokens에서 복사, 직접 고치지 말 것 (scripts/sync_reevl_tokens.sh)
// 생성 파일 — 손으로 고치지 말 것. 원본: design-tokens/tokens.json(digest 7de59f144dd2) ← frontend/scripts/ds_tokens_export.mjs
// 다시 만들기: cd frontend && npm run ds:tokens
// 차량(AAOS · Pleos) 덮어쓰기 — 브랜드 층(색·AI 빛·글꼴)은 리블 그대로, 크기·대비·밀도만 차량 규칙으로.
// 근거: design-tokens/platforms.json car._source (car-ds-v2 · Google 차량 시각 원칙 · Android 차량 앱 품질 · Pleos 가이드)
package kr.co.reevl.design.car

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kr.co.reevl.design.ReevlColors
import kr.co.reevl.design.ReevlPalette

object ReevlCarTheme {
    /** 기본은 어두운 바탕(밤·주행). 시스템이 낮을 알리면 Light. */
    const val DARK_DEFAULT = true
    /** 글자 대비 4.5:1 미만인 글자 역할은 더 진한 역할로 바꿔 둔 팔레트(car-ds-v2 §7). */
    val Dark: ReevlColors = ReevlPalette.Dark
    val Light: ReevlColors = ReevlPalette.Light
    const val MIN_CONTRAST = 4.5f
    const val MIN_ICON_CONTRAST = 3f

    /** 누르는 칸 — 바닥 76dp, 리블 차량 앱 기준 100dp, 사이 32dp */
    val touchTargetMin = 76.dp
    val touchTarget = 100.dp
    val touchGap = 32.dp
    const val MIN_TYPE_SP = 24
    const val PRIMARY_ROW_TYPE_SP = 32
    const val PRESS_SCALE = 0.95f
    const val PRESS_MAX_MILLIS = 250
    const val MAX_BOLD_PER_SCREEN = 1
    /** 입력의 주 행동은 음성 — 키보드는 보조. */
    const val VOICE_FIRST = true

    /** 웹 모서리 × 2 */
    object Radius {
        val sm = 12.dp
        val md = 16.dp
        val lg = 20.dp
        val xl = 24.dp
        val xl2 = 32.dp
        val xl3 = 48.dp
    }

    object Space {
        val margin = 32.dp
        val gap = 32.dp
        val gapTight = 16.dp
        val topBar = 100.dp
        val actionBar = 148.dp
        val rowSingle = 100.dp
        val rowDouble = 128.dp
        val chipVisual = 80.dp
        val icon = 40.dp
        val iconSmall = 28.dp
        val readWidth = 1200.dp
        val wideMin = 1200.dp
    }

    /** 동선 지도 바탕(MapLibre) 중 리블 색 토큰에 없는 지도 전용 색 — 웹 frontend/lib/planMapStyle.ts PLAN_MAP과 같은 값(car:tokens가 대조). 낮 땅=chip·길=bg·철도=ink2, 밤 철도=sub는 색 토큰을 그대로 쓴다. */
    object MapColors {
        object Light {
            val water = Color(0xFFD3DBE4)
            val green = Color(0xFFDCEBE2)
            val label = Color(0xFF6B7684)
            val building = Color(0xFFE5E8EB)
        }
        object Dark {
            val land = Color(0xFF222831)
            val water = Color(0xFF151C26)
            val green = Color(0xFF1E2D2A)
            val road = Color(0xFF3D4553)
            val road2 = Color(0xFF353C48)
            val road3 = Color(0xFF2C323C)
            val label = Color(0xFF8B95A1)
            val building = Color(0xFF2A303A)
        }
    }

    object Brand {
        /** 리블 로고 네모 — 낮·밤 같은 색(ReevlLogo.tsx rect fill). 서버 단지 브랜드 색 기본값(#3182f6)도 이 값. */
        val logo = Color(0xFF3182F6)
        const val LOGO_HEX = "#3182f6"
        /** 단지 아바타 바탕 9색 — 웹 lib/rt.ts PAL과 같은 순서(이름 해시로 고른다). */
        val avatar: List<Color> = listOf(Color(0xFF3182F6), Color(0xFFF5A623), Color(0xFF7B61FF), Color(0xFFF04452), Color(0xFF00C2B3), Color(0xFFFF7A45), Color(0xFF4C6EF5), Color(0xFFE64980), Color(0xFF12B886))
        val avatarHex: List<String> = listOf("#3182f6", "#f5a623", "#7b61ff", "#f04452", "#00c2b3", "#ff7a45", "#4c6ef5", "#e64980", "#12b886")
    }

    /** 차량 앱 배치 크기(dp). 간격·누르는 칸·모서리는 space·density.car에 있고, 여기는 그 밖의 아이콘·선·안쪽 여백·부품 폭·판 높이·읽기 폭. */
    object Size {
        /** glyph 24 = Lucide·벡터 아이콘 원래 크기(viewport) · inline 32 = 버튼·배너·칩 안 · mark 36 = 완료 표시·작은 회전 · hero 64 = 안내 화면 · progress 72 = 기다림 회전 · dot 12 = AI 점. 40·28은 space.icon·iconSmall. */
        object Icon {
            val dot = 12.dp
            val glyph = 24.dp
            val inline = 32.dp
            val mark = 36.dp
            val hero = 64.dp
            val progress = 72.dp
        }
        /** 테두리 굵기 — 1은 hairline. thin 2 = 순서 표시·아바타 테두리 · focus 3 = 초점·입력 칸·탭 표시 · heavy 4 = 지금 칸. */
        object Stroke {
            val thin = 2.dp
            val focus = 3.dp
            val heavy = 4.dp
        }
        /** 누르는 것의 가로 안쪽 여백 — s 20 = 메뉴·탭·칩 끝 · m 24 = 아이콘 단추·제목 옆 · l 28 = 버튼·칩·검색 칸 시작. */
        object Inset {
            val s = 20.dp
            val m = 24.dp
            val l = 28.dp
        }
        /** 부품 최소·고정 폭과 높이. */
        object Control {
            val buttonMin = 160.dp
            val buttonMinCompact = 120.dp
            val messageButton = 240.dp
            val searchButtonNarrow = 128.dp
            val chipMin = 140.dp
            val tabMin = 120.dp
            val tabMinDense = 100.dp
            val tabShareMin = 80.dp
            val keypadCell = 104.dp
            val keypadCellHeight = 120.dp
            val keypadMax = 704.dp
            val appMark = 62.dp
            val avatarCompact = 56.dp
        }
        /** 줄 최소 높이 — line 64 = 한 줄 안내 · skeleton 80 · stat 84 · compare 96. */
        object Row {
            val line = 64.dp
            val skeleton = 80.dp
            val stat = 84.dp
            val compare = 96.dp
        }
        /** 읽기 폭·최대 폭(넘으면 줄인다) — 비교 이름 칸 wide/compact/narrow · 수치 이름·값 · 지역 지도. */
        object Measure {
            val compareLabelWide = 280.dp
            val compareLabelCompact = 200.dp
            val compareLabelNarrow = 180.dp
            val statLabel = 280.dp
            val statValue = 520.dp
            val statTwoCol = 1000.dp
            val statColumnGap = 48.dp
            val areaMap = 760.dp
            val areaMapSide = 640.dp
        }
        /** 지도·판 높이 — plan 440(좁은 창 340) · side 400(세로로 쌓으면 300) · preview 360 · mapSmall 420 = 이보다 작으면 핀을 줄인다 · qr 400/qrSmall 200. */
        object Panel {
            val plan = 440.dp
            val planNarrow = 340.dp
            val side = 400.dp
            val sideStacked = 300.dp
            val preview = 360.dp
            val mapSmall = 420.dp
            val qr = 400.dp
            val qrSmall = 200.dp
        }
        /** 빈 화면 안내의 위 여백. */
        object Empty {
            val top = 96.dp
            val topCompact = 48.dp
            val topArea = 160.dp
        }
        /** 동선 선·핀 — line 6 · casing 5(선 바깥 테두리 더함) · dash 22/dashGap 14 · pinSmall 22(작은 지도 핀 반지름) · pinHalo 4 · nextRing 6. */
        object Route {
            val line = 6.dp
            val casing = 5.dp
            val dash = 22.dp
            val dashGap = 14.dp
            val pinSmall = 22.dp
            val pinHalo = 4.dp
            val nextRing = 6.dp
        }
    }

    /** 창 폭 1200dp 미만 */
    object TypeCompact {
        /** 웹 t-display의 행간·자간 · Compact */
        val display = TextStyle(fontSize = 48.sp, lineHeight = 54.72.sp, letterSpacing = (-0.02).em, fontWeight = FontWeight.W500)
        /** 웹 t-title-lg의 행간·자간 · Compact */
        val title = TextStyle(fontSize = 36.sp, lineHeight = 43.92.sp, letterSpacing = (-0.02).em, fontWeight = FontWeight.W500)
        /** 웹 t-headline의 행간·자간 · Compact */
        val topTitle = TextStyle(fontSize = 36.sp, lineHeight = 46.08.sp, letterSpacing = (-0.015).em, fontWeight = FontWeight.W500)
        /** 웹 t-headline의 행간·자간 · Compact */
        val rowPrimary = TextStyle(fontSize = 32.sp, lineHeight = 40.96.sp, letterSpacing = (-0.015).em, fontWeight = FontWeight.W400)
        /** 웹 t-val의 행간·자간 · Compact */
        val rowSecondary = TextStyle(fontSize = 26.sp, lineHeight = 31.2.sp, letterSpacing = (-0.01).em, fontWeight = FontWeight.W400, fontFeatureSettings = "tnum")
        /** 웹 t-val-lg의 행간·자간 · Compact */
        val value = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.015).em, fontWeight = FontWeight.W500, fontFeatureSettings = "tnum")
        /** 웹 t-label의 행간·자간 · Compact */
        val label = TextStyle(fontSize = 30.sp, lineHeight = 39.sp, letterSpacing = (0).em, fontWeight = FontWeight.W500)
        /** 웹 t-label의 행간·자간 · Compact */
        val chip = TextStyle(fontSize = 26.sp, lineHeight = 33.8.sp, letterSpacing = (0).em, fontWeight = FontWeight.W400)
        /** 웹 t-cap의 행간·자간 · Compact */
        val caption = TextStyle(fontSize = 24.sp, lineHeight = 31.2.sp, letterSpacing = (0).em, fontWeight = FontWeight.W400)
    }

    /** 창 폭 1200dp 이상 */
    object TypeWide {
        /** 웹 t-display의 행간·자간 · Wide */
        val display = TextStyle(fontSize = 56.sp, lineHeight = 63.84.sp, letterSpacing = (-0.02).em, fontWeight = FontWeight.W500)
        /** 웹 t-title-lg의 행간·자간 · Wide */
        val title = TextStyle(fontSize = 40.sp, lineHeight = 48.8.sp, letterSpacing = (-0.02).em, fontWeight = FontWeight.W500)
        /** 웹 t-headline의 행간·자간 · Wide */
        val topTitle = TextStyle(fontSize = 36.sp, lineHeight = 46.08.sp, letterSpacing = (-0.015).em, fontWeight = FontWeight.W500)
        /** 웹 t-headline의 행간·자간 · Wide */
        val rowPrimary = TextStyle(fontSize = 32.sp, lineHeight = 40.96.sp, letterSpacing = (-0.015).em, fontWeight = FontWeight.W400)
        /** 웹 t-val의 행간·자간 · Wide */
        val rowSecondary = TextStyle(fontSize = 28.sp, lineHeight = 33.6.sp, letterSpacing = (-0.01).em, fontWeight = FontWeight.W400, fontFeatureSettings = "tnum")
        /** 웹 t-val-lg의 행간·자간 · Wide */
        val value = TextStyle(fontSize = 34.sp, lineHeight = 40.8.sp, letterSpacing = (-0.015).em, fontWeight = FontWeight.W500, fontFeatureSettings = "tnum")
        /** 웹 t-label의 행간·자간 · Wide */
        val label = TextStyle(fontSize = 30.sp, lineHeight = 39.sp, letterSpacing = (0).em, fontWeight = FontWeight.W500)
        /** 웹 t-label의 행간·자간 · Wide */
        val chip = TextStyle(fontSize = 26.sp, lineHeight = 33.8.sp, letterSpacing = (0).em, fontWeight = FontWeight.W400)
        /** 웹 t-cap의 행간·자간 · Wide */
        val caption = TextStyle(fontSize = 24.sp, lineHeight = 31.2.sp, letterSpacing = (0).em, fontWeight = FontWeight.W400)
    }
}
