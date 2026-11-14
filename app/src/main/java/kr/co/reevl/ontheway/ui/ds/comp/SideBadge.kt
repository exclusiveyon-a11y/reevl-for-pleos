package kr.co.reevl.ontheway.ui.ds.comp

import kr.co.reevl.ontheway.nearby.NearbyEngine.Side

/** car-ds-v2 §5.6 (text only: the arrow icon is left out so `거리 · 금액` fits a 620 list). Unknown → no badge. */
fun sideChip(side: Side): ChipSpec? = when (side) {
    Side.LEFT -> ChipSpec("왼쪽", Tone.Brand, tag = "side")
    Side.RIGHT -> ChipSpec("오른쪽", Tone.Brand, tag = "side")
    Side.AHEAD -> ChipSpec("앞", Tone.Neutral, tag = "side")
    Side.BEHIND -> ChipSpec("지나옴", Tone.Sub, tag = "side")
    Side.UNKNOWN -> null
}
