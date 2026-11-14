// ★생성 파일 — 손으로 고치지 말 것. 원본: lucide-react 1.22.0 (reevl/frontend/node_modules/lucide-react, ISC License — Lucide Icons and Contributors)
// 재생성: node tools/gen_lucide_icons.mjs   — 리블 웹이 쓰는 아이콘과 같은 선(24 그리드, 선 굵기 2, 둥근 끝).
package kr.co.reevl.ontheway.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

private fun lucide(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = CarSize.Icon.glyph, defaultHeight = CarSize.Icon.glyph, viewportWidth = 24f, viewportHeight = 24f).apply {
        for (d in paths) addPath(
            pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

object Lucide {
    /** lucide `chevron-left` */
    val ChevronLeft: ImageVector by lazy { lucide("ChevronLeft", "m15 18-6-6 6-6") }
    /** lucide `chevron-right` */
    val ChevronRight: ImageVector by lazy { lucide("ChevronRight", "m9 18 6-6-6-6") }
    /** lucide `ellipsis` */
    val Ellipsis: ImageVector by lazy { lucide("Ellipsis", "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M18 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M4 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0") }
    /** lucide `check` */
    val Check: ImageVector by lazy { lucide("Check", "M20 6 9 17l-5-5") }
    /** lucide `refresh-cw` */
    val RefreshCw: ImageVector by lazy { lucide("RefreshCw", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5") }
    /** lucide `wifi-off` */
    val WifiOff: ImageVector by lazy { lucide("WifiOff", "M12 20h.01", "M8.5 16.429a5 5 0 0 1 7 0", "M5 12.859a10 10 0 0 1 5.17-2.69", "M19 12.859a10 10 0 0 0-2.007-1.523", "M2 8.82a15 15 0 0 1 4.177-2.643", "M22 8.82a15 15 0 0 0-11.288-3.764", "m2 2 20 20") }
    /** lucide `lock` */
    val Lock: ImageVector by lazy { lucide("Lock", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2Z", "M7 11V7a5 5 0 0 1 10 0v4") }
    /** lucide `navigation` */
    val Navigation: ImageVector by lazy { lucide("Navigation", "M3 11L22 2L13 21L11 13L3 11Z") }
    /** lucide `circle-alert` */
    val CircleAlert: ImageVector by lazy { lucide("CircleAlert", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M12 8L12 12", "M12 16L12.01 16") }
    /** lucide `map-pin` */
    val MapPin: ImageVector by lazy { lucide("MapPin", "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0", "M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    /** lucide `building-2` */
    val Building2: ImageVector by lazy { lucide("Building2", "M10 12h4", "M10 8h4", "M14 21v-3a2 2 0 0 0-4 0v3", "M6 10H4a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-2", "M6 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16") }
    /** lucide `tag` */
    val Tag: ImageVector by lazy { lucide("Tag", "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z", "M7 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0") }
    /** lucide `delete` */
    val Delete: ImageVector by lazy { lucide("Delete", "M10 5a2 2 0 0 0-1.344.519l-6.328 5.74a1 1 0 0 0 0 1.481l6.328 5.741A2 2 0 0 0 10 19h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z", "m12 9 6 6", "m18 9-6 6") }
    /** lucide `train-front` */
    val TrainFront: ImageVector by lazy { lucide("TrainFront", "M8 3.1V7a4 4 0 0 0 8 0V3.1", "m9 15-1-1", "m15 15 1-1", "M9 19c-2.8 0-5-2.2-5-5v-4a8 8 0 0 1 16 0v4c0 2.8-2.2 5-5 5Z", "m8 19-2 3", "m16 19 2 3") }
    /** lucide `calendar` */
    val Calendar: ImageVector by lazy { lucide("Calendar", "M8 2v4", "M16 2v4", "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z", "M3 10h18") }
    /** lucide `log-out` */
    val LogOut: ImageVector by lazy { lucide("LogOut", "m16 17 5-5-5-5", "M21 12H9", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4") }
    /** lucide `route` */
    val Route: ImageVector by lazy { lucide("Route", "M3 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15", "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    /** lucide `house` */
    val House: ImageVector by lazy { lucide("House", "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z") }
    /** lucide `smartphone` */
    val Smartphone: ImageVector by lazy { lucide("Smartphone", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2Z", "M12 18h.01") }
    /** lucide `clock` */
    val Clock: ImageVector by lazy { lucide("Clock", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M12 6v6l4 2") }
    /** lucide `user-round` */
    val UserRound: ImageVector by lazy { lucide("UserRound", "M7 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0", "M20 21a8 8 0 0 0-16 0") }
    /** lucide `eye` */
    val Eye: ImageVector by lazy { lucide("Eye", "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    /** lucide `undo-2` */
    val Undo2: ImageVector by lazy { lucide("Undo2", "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11") }
    /** lucide `plus` */
    val Plus: ImageVector by lazy { lucide("Plus", "M5 12h14", "M12 5v14") }
    /** lucide `locate-fixed` */
    val LocateFixed: ImageVector by lazy { lucide("LocateFixed", "M2 12L5 12", "M19 12L22 12", "M12 2L12 5", "M12 19L12 22", "M5 12a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    /** lucide `phone` */
    val Phone: ImageVector by lazy { lucide("Phone", "M13.832 16.568a1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 6.392 6.384") }
    /** lucide `handshake` */
    val Handshake: ImageVector by lazy { lucide("Handshake", "m11 17 2 2a1 1 0 1 0 3-3", "m14 14 2.5 2.5a1 1 0 1 0 3-3l-3.88-3.88a3 3 0 0 0-4.24 0l-.88.88a1 1 0 1 1-3-3l2.81-2.81a5.79 5.79 0 0 1 7.06-.87l.47.28a2 2 0 0 0 1.42.25L21 4", "m21 3 1 11h-2", "M3 3 2 14l6.5 6.5a1 1 0 1 0 3-3", "M3 4h8") }
    /** lucide `map-pin-plus` */
    val MapPinPlus: ImageVector by lazy { lucide("MapPinPlus", "M19.914 11.105A7.298 7.298 0 0 0 20 10a8 8 0 0 0-16 0c0 4.993 5.539 10.193 7.399 11.799a1 1 0 0 0 1.202 0 32 32 0 0 0 .824-.738", "M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M16 18h6", "M19 15v6") }
    /** lucide `search` */
    val Search: ImageVector by lazy { lucide("Search", "m21 21-4.34-4.34", "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0") }
    /** lucide `sparkles` */
    val Sparkles: ImageVector by lazy { lucide("Sparkles", "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z", "M20 2v4", "M22 4h-4", "M2 20a2 2 0 1 0 4 0a2 2 0 1 0 -4 0") }
    /** lucide `x` */
    val X: ImageVector by lazy { lucide("X", "M18 6 6 18", "m6 6 12 12") }
    /** lucide `circle-question-mark` */
    val CircleQuestionMark: ImageVector by lazy { lucide("CircleQuestionMark", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3", "M12 17h.01") }
    /** lucide `star` */
    val Star: ImageVector by lazy { lucide("Star", "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z") }
    /** lucide `arrow-left` */
    val ArrowLeft: ImageVector by lazy { lucide("ArrowLeft", "m12 19-7-7 7-7", "M19 12H5") }
    /** lucide `arrow-right` */
    val ArrowRight: ImageVector by lazy { lucide("ArrowRight", "M5 12h14", "m12 5 7 7-7 7") }
    /** lucide `arrow-up` */
    val ArrowUp: ImageVector by lazy { lucide("ArrowUp", "m5 12 7-7 7 7", "M12 19V5") }
    /** lucide `arrow-down` */
    val ArrowDown: ImageVector by lazy { lucide("ArrowDown", "M12 5v14", "m19 12-7 7-7-7") }
    /** lucide `flag` */
    val Flag: ImageVector by lazy { lucide("Flag", "M4 22V4a1 1 0 0 1 .4-.8A6 6 0 0 1 8 2c3 0 5 2 7.333 2q2 0 3.067-.8A1 1 0 0 1 20 4v10a1 1 0 0 1-.4.8A6 6 0 0 1 16 16c-3 0-5-2-8-2a6 6 0 0 0-4 1.528") }
    /** lucide `info` */
    val Info: ImageVector by lazy { lucide("Info", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M12 16v-4", "M12 8h.01") }
    /** lucide `menu` */
    val Menu: ImageVector by lazy { lucide("Menu", "M4 5h16", "M4 12h16", "M4 19h16") }
    /** lucide `mic` */
    val Mic: ImageVector by lazy { lucide("Mic", "M12 19v3", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 2h0a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h0a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3Z") }
}
