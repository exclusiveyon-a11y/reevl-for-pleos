package kr.co.reevl.ontheway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.reevl.ontheway.ui.theme.CarColors
import kr.co.reevl.ontheway.ui.theme.CarDims
import kr.co.reevl.ontheway.ui.theme.CarTheme

// ── Badge / status chip ─────────────────────────────────────────────────────────────────────────────────────────
// Port of components/common/Badge.tsx:10-19 (TONE) + :22-37 (Badge) — tones neutral·ink·brand·up·down·pos·warn·live, size md
// (`rounded-md px-2 py-1 t-label-sm font-extrabold`, ×2 for the car → radius 16, 16/8 padding, Label 24→26 bold).

enum class BadgeTone { Neutral, Ink, Brand, Up, Down, Pos, Warn }

private fun BadgeTone.colors(c: CarColors): Pair<Color, Color> = when (this) {
    BadgeTone.Neutral -> c.chip to c.sub          // bg-chip text-sub
    BadgeTone.Ink -> c.ink to c.card              // bg-ink text-bg
    BadgeTone.Brand -> c.brandSoft to c.brand     // bg-brand-soft text-brand
    BadgeTone.Up -> c.upSoft to c.up              // bg-up-soft text-up
    BadgeTone.Down -> c.downSoft to c.down        // bg-down-soft text-down
    BadgeTone.Pos -> c.posSoft to c.posStrong     // bg-pos-soft text-pos-strong (AA)
    BadgeTone.Warn -> c.warnSoft to c.warnStrong  // bg-warn-soft text-warn-strong (AA)
}

@Composable
fun ReevlBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier, icon: ImageVector? = null, tag: String? = null) {
    val c = CarTheme.colors
    val (bg, fg) = tone.colors(c)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
        Text(text, style = kr.co.reevl.ontheway.ui.theme.TextUse.badge(CarTheme.type).copy(color = fg), maxLines = 1)
    }
}

/**
 * Order badge — Reevl TradeHighlights area chip (`rounded-lg bg-chip` · selected `bg-brand text-white`), ×2.
 * next = brand, done = pos-soft + check, skipped = chip + dim.
 */
@Composable
fun OrderBadge(position: Int, state: StopState, modifier: Modifier = Modifier) {
    val c = CarTheme.colors
    val (bg, fg) = when (state) {
        StopState.Next, StopState.Guiding -> c.brand to c.onBrand
        StopState.Visited -> c.posSoft to c.posStrong
        StopState.Skipped -> c.chip to c.sub
        StopState.Planned, StopState.NeedsLocation -> c.chip to c.ink
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(CarDims.orderBadge).clip(RoundedCornerShape(16.dp)).background(bg)) {
        if (state == StopState.Visited) Icon(Lucide.Check, "방문 완료", tint = fg, modifier = Modifier.size(32.dp))
        else Text("$position", style = CarTheme.type.valueSm.copy(color = fg, fontWeight = FontWeight.ExtraBold))
    }
}

enum class StopState { Next, Guiding, Planned, Visited, Skipped, NeedsLocation }

// ── Card + CardHead (components/common/CardHead.tsx:11-37) ────────────────────────────────────────────────────────────

/**
 * Reevl card at car scale: `rounded-2xl p-4` ×2 → radius 32, padding 32. Face = `--bg` (white / #16171A) on the
 * `--app` page (Pleos window: white cards on #F7F8FA) — car-ui-spec §1.1 why the face is kept.
 */
@Composable
fun ReevlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tag: String? = null,
    padding: Dp = CarDims.cardPad,
    face: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(CarDims.radiusCard)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        modifier = modifier
            .graphicsLayer { val s = if (pressed) 0.98f else 1f; scaleX = s; scaleY = s }
            .clip(shape)
            .background(face ?: c.card, shape)
            .then(if (pressed) Modifier.background(c.pressOverlay, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** CardHead.tsx: title `t-headline font-extrabold text-ink` + sub `t-cap text-dim` (≤ 2 lines), right slot. */
@Composable
fun CardHead(title: String, modifier: Modifier = Modifier, sub: String? = null, right: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CarTheme.type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = CarTheme.type.cap, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        if (right != null) Row(horizontalArrangement = Arrangement.spacedBy(CarDims.gapItem), verticalAlignment = Alignment.CenterVertically, content = right)
    }
}

// ── StatRow / StatGrid (components/common/StatRow.tsx:9-27, components/detail/StatBlocks.tsx:21-68) ───────────────────────

/**
 * StatRow.tsx: label left (`t-micro font-medium text-dim`, shrink-0) · value right (`t-val-sm font-bold`, min-w-0
 * truncate) · optional chip before the value. No row divider (StatBlocks: "행 사이 = 선 없음"). Car sizes: label
 * Label 24 (`--sub`), value Body L 30 ExtraBold (KeyStats values are the facts a parked buyer reads first).
 */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier, chip: String? = null, valueColor: Color? = null, tag: String? = null,
            /** As-of line under the label — only for the number it dates (e.g. 전세가율's `2026-10-26 기준`). */
            note: String? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CarDims.gapItem),
    ) {
        // label: shrink-0 · value: min-w-0 truncate, right-aligned (StatRow.tsx "안 잘림 규약")
        Column {
            Text(label, style = CarTheme.type.cap, maxLines = 1, softWrap = false)
            if (note != null) Text(note, style = CarTheme.type.cap, maxLines = 1, softWrap = false)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(CarDims.gapItem, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (chip != null) ReevlBadge(chip, BadgeTone.Neutral)
            Text(value, style = CarTheme.type.valueSm.copy(fontSize = CarTheme.type.title.fontSize, lineHeight = CarTheme.type.title.lineHeight,
                fontWeight = FontWeight.ExtraBold, color = valueColor ?: CarTheme.colors.ink),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

/** StatBlocks STAT_GRID: 1 column, 2 columns once the card is ≥ 1152 dp (web `@xl` 576 px × 2). Odd count → pad. */
@Composable
fun StatGrid(cells: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = if (maxWidth >= 1152.dp) 2 else 1
        Column {
            cells.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    row.forEach { cell -> Box(Modifier.weight(1f)) { cell() } }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** StatBlocks StatSection: a 1 dp line above + small section title (`t-label-sm font-bold text-sub`). */
@Composable
fun StatSection(title: String, modifier: Modifier = Modifier, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = CarTheme.colors
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = CarTheme.type.cap.copy(color = c.sub, fontWeight = FontWeight.Bold))
            if (hint != null) Text("  $hint", style = CarTheme.type.cap)
        }
        content()
    }
}

// ── Banner (Badge tones + Pleos Toast padding 24) ───────────────────────────────────────────────────────────────

enum class BannerKind { Info, Offline, Error, Warn, Done }

/** [icon] overrides the kind's icon (e.g. a clock on a late-appointment warning). Warn = the Offline colour pair. */
@Composable
fun Banner(text: String, kind: BannerKind, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = CarTheme.colors
    val (bg, fg, kindIcon) = when (kind) {
        BannerKind.Info -> Triple(c.chip, c.sub, Lucide.RefreshCw)
        BannerKind.Offline -> Triple(c.warnSoft, c.warnStrong, Lucide.WifiOff)
        BannerKind.Error -> Triple(c.upSoft, c.up, Lucide.CircleAlert)
        BannerKind.Warn -> Triple(c.warnSoft, c.warnStrong, Lucide.Clock)
        // Badge `pos` tone (bg-pos-soft text-pos-strong) — a saved change (`방문 순서를 바꿨어요`).
        BannerKind.Done -> Triple(c.posSoft, c.posStrong, Lucide.Check)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).padding(horizontal = 24.dp, vertical = 20.dp).testTag("banner"),
    ) {
        Icon(icon ?: kindIcon, null, tint = fg, modifier = Modifier.size(32.dp))
        Text(text, style = kr.co.reevl.ontheway.ui.theme.TextUse.banner(CarTheme.type).copy(color = fg))
    }
}

// ── Empty state (phone / Gleo empty state: centred title + description + one action) ────────────────────────────

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null) {
    val c = CarTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (icon != null) Box(Modifier.size(96.dp).clip(CircleShape).background(c.chip), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.sub, modifier = Modifier.size(44.dp))
        }
        Text(title, style = CarTheme.type.headline, textAlign = TextAlign.Center)
        Text(body, style = CarTheme.type.body.copy(color = c.sub), textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(8.dp)); action() }
    }
}

/** Reevl `.skeleton` (chip face; the web shimmer is motion — kept still in the car). */
@Composable
fun SkeletonBar(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).clip(RoundedCornerShape(12.dp)).background(CarTheme.colors.chip))
}

/**
 * Driving lock line — Google "clearly distinguish allowed from disallowed features"; our in-car rule
 * "잠긴 것은 숨기지 않고 '정차하면 열려요' 한 줄로".
 */
@Composable
fun DrivingLock(text: String, modifier: Modifier = Modifier) {
    val c = CarTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.chip).padding(24.dp).testTag("driving_lock"),
    ) {
        Icon(Lucide.Lock, null, tint = c.sub, modifier = Modifier.size(36.dp))
        Text(text, style = CarTheme.type.title.copy(color = c.ink2, fontWeight = FontWeight.SemiBold))
    }
}

/** Thin 1 dp divider (`--line`). */
@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(CarTheme.colors.line))

/** A small square tile (used for the code cells' focus ring, etc.). */
fun Modifier.ring(color: Color, width: Dp, radius: Dp) = this.border(width, color, RoundedCornerShape(radius))
