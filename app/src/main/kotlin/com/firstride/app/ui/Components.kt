package com.firstride.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstride.R
import com.firstride.app.AppViewModel
import com.firstride.engine.judge.ConditionStatus
import com.firstride.engine.judge.ConditionView
import com.firstride.engine.judge.Phase

/*
 * 공통 컴포넌트. 이름은 Figma 01 컴포넌트 보드와 같다(기술 설계 12절). 크기는 docs/08-design-tokens.md 4절, 색은 DESIGN.md 3절.
 * 글자에는 색을 넣지 않는다. 카드·배너·판·버튼에 테두리를 두르지 않는다.
 */

// ---------------------------------------------------------------- 누름

/**
 * 누를 수 있는 것. 누르는 동안 95%로 줄어든다(Pleos 디자인 가이드). 물결 효과는 쓰지 않는다 —
 * 움직이는 요소는 스피너와 누름 효과뿐이다(POL-U-5). 누름 색은 [content]가 pressed 값으로 고른다.
 */
@Composable
private fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    role: Role = Role.Button,
    content: @Composable (pressed: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed && enabled) 0.95f else 1f
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick),
        // 바깥에서 준 너비(fillMaxWidth, weight)가 안쪽 모양에도 그대로 가게 한다.
        propagateMinConstraints = true,
    ) { content(pressed && enabled) }
}

// ---------------------------------------------------------------- 버튼

private enum class ButtonKind { PRIMARY, SECONDARY, TEXT, DANGER }

@Composable
private fun AppButton(kind: ButtonKind, text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    val shape = RoundedCornerShape(Radius.button)
    val cs = MaterialTheme.colorScheme
    val app = appColors
    Pressable(onClick, modifier.heightIn(min = Size.buttonH), enabled, shape) { pressed ->
        val bg = when (kind) {
            ButtonKind.PRIMARY -> when { !enabled -> app.disabledContainer; pressed -> app.primaryPressed; else -> cs.primary }
            ButtonKind.SECONDARY -> when { !enabled -> app.disabledContainer; pressed -> app.secondaryPressed; else -> app.secondary }
            ButtonKind.TEXT -> if (pressed) app.secondaryPressed else Color.Transparent
            ButtonKind.DANGER -> if (enabled) cs.error else app.disabledContainer
        }
        val fg = when {
            !enabled -> app.textDisabled
            kind == ButtonKind.PRIMARY -> cs.onPrimary
            kind == ButtonKind.SECONDARY -> cs.onSurface
            kind == ButtonKind.DANGER -> app.onStatus
            else -> cs.onSurfaceVariant
        }
        Box(Modifier.heightIn(min = Size.buttonH).background(bg, shape).padding(horizontal = Space.xl), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center)
        }
    }
}

/** 주된 행동 하나. 높이 88dp, 모서리 20dp. (예전 `BigButton(primary = true)`) */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    AppButton(ButtonKind.PRIMARY, text, onClick, modifier, enabled)

/** 보조 행동. 회색으로 채운 버튼, 테두리 없음. (예전 `BigButton(primary = false)`) */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    AppButton(ButtonKind.SECONDARY, text, onClick, modifier, enabled)

/** 가벼운 행동(그만하기, 건너뛰기, 돌아가기). 글자만(text/secondary), 누르는 동안 바탕이 생긴다. */
@Composable
fun TextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    AppButton(ButtonKind.TEXT, text, onClick, modifier, enabled)

/** 되돌릴 수 없는 행동의 확인(기록 지우기). 바탕 error, 글자 #131417. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    AppButton(ButtonKind.DANGER, text, onClick, modifier, enabled)

// ---------------------------------------------------------------- TopBar

/** 화면 맨 위 줄. 높이 96dp. 왼쪽 행동(돌아가기·그만하기), 가운데 제목, 오른쪽 짧은 정보(1/3) 또는 행동. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    left: (@Composable () -> Unit)? = null,
    right: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(cs.surface)) {
        Box(Modifier.fillMaxWidth().height(Size.topBarH).padding(horizontal = Space.s)) {
            Box(Modifier.align(Alignment.CenterStart)) { left?.invoke() }
            Text(
                title,
                Modifier.align(Alignment.Center).padding(horizontal = 220.dp),
                style = MaterialTheme.typography.titleLarge,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Box(Modifier.align(Alignment.CenterEnd).padding(end = Space.l)) { right?.invoke() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
    }
}

/** TopBar 오른쪽의 짧은 정보(예: 1 / 3 단계). */
@Composable
fun TopBarInfo(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = appColors.textTertiary)

// ---------------------------------------------------------------- 목록·카드

/** 카드 모양의 누를 수 있는 상자(CourseCard 등). 바탕 bg/card, 테두리 없음, 모서리 24dp, 안쪽 24dp. */
@Composable
fun CardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.card)
    Pressable(onClick, modifier.fillMaxWidth(), true, shape) { pressed ->
        Box(
            Modifier.fillMaxWidth().heightIn(min = Size.touchMin)
                .background(if (pressed) appColors.secondaryPressed else appColors.card, shape)
                .padding(Space.l),
        ) { content() }
    }
}

/** 알약 모양 이름표(코스 상태 등). 테두리 없이 채운다. 기본은 bg/surface-variant 위 text/secondary. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text,
        modifier.background(background, CircleShape).padding(horizontal = 18.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

/** 목록의 한 줄. 높이 76dp 이상, 아래 구분선. [onClick]이 있으면 누를 수 있다. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val body: @Composable (Boolean) -> Unit = { pressed ->
        Column(Modifier.fillMaxWidth().background(if (pressed) appColors.secondaryPressed else Color.Transparent)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Size.touchMin).padding(vertical = Space.ml),
                horizontalArrangement = Arrangement.spacedBy(Space.ml),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    if (!description.isNullOrBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = appColors.textTertiary)
                }
                trailing?.invoke()
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
        }
    }
    if (onClick != null) Pressable(onClick, modifier.fillMaxWidth(), true, RoundedCornerShape(0.dp)) { body(it) }
    else Box(modifier.fillMaxWidth()) { body(false) }
}

/** 회색 안내 상자(정차 안내 등). 바탕 surface-variant, 모서리 16dp. */
@Composable
fun NoticeBox(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.md)).padding(horizontal = Space.l, vertical = Space.m),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------- 조건 줄

/**
 * 조건 하나의 상태(AC-04-3). 기호와 문구로 구분하고 색은 기호에만 쓴다(POL-U-4, DESIGN.md 3절).
 * 오른쪽 문구는 회색 단계만 쓴다. 폴링하는 조건은 "확인 중"을 덧붙인다(AC-04-7).
 */
@Composable
fun ConditionRow(view: ConditionView, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val app = appColors
    val (status, color) = when (view.status) {
        ConditionStatus.MET -> R.string.condition_met to cs.onSurfaceVariant
        ConditionStatus.ALREADY_MET -> R.string.condition_already_met to cs.onSurfaceVariant
        ConditionStatus.UNMET -> R.string.condition_unmet to app.textTertiary
        ConditionStatus.TOGGLE_NEEDED -> R.string.condition_toggle to cs.onSurface
        ConditionStatus.UNKNOWN -> R.string.condition_unknown to cs.onSurfaceVariant
    }
    val labelColor = if (view.status == ConditionStatus.UNKNOWN) cs.onSurfaceVariant else cs.onSurface
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.ml),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(Size.mark), contentAlignment = Alignment.Center) {
                if (view.polling && view.status == ConditionStatus.UNMET) Spinner(24.dp, cs.onSurfaceVariant) else ConditionMark(view.status)
            }
            Text(view.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = labelColor)
            val polling = if (view.polling) " ${stringResource(R.string.condition_polling)}" else ""
            Text(stringResource(status) + polling, style = MaterialTheme.typography.labelMedium, color = color)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
    }
}

/** 조건 줄의 44dp 표시. DESIGN.md 3절. 초록 채움은 신호로 확인된 것(MET)에만 쓴다. */
@Composable
private fun ConditionMark(status: ConditionStatus) {
    val cs = MaterialTheme.colorScheme
    val app = appColors
    when (status) {
        ConditionStatus.MET -> Glyph(Size.mark, fill = app.statusConfirmed) { check(app.onStatus) }
        ConditionStatus.ALREADY_MET -> Glyph(Size.mark, ring = cs.onSurfaceVariant) { check(cs.onSurfaceVariant) }
        ConditionStatus.UNMET -> Glyph(Size.mark, ring = app.textTertiary) {}
        ConditionStatus.TOGGLE_NEEDED -> Glyph(Size.mark, ring = cs.onSurfaceVariant) { refresh(cs.onSurfaceVariant) }
        ConditionStatus.UNKNOWN -> Glyph(Size.mark, ring = app.textTertiary, dashed = true, letter = "?", letterColor = app.textTertiary)
    }
}

/**
 * 조건 줄 밖에서 쓰는 상태 기호(소등 확인, 연결 오류, 홈 진행 현황). DESIGN.md 2-6절.
 * SKIPPED(건너뜀 있음)와 EMPTY(시작 전)는 홈 진행 현황에서 코스 결과를 보인다. 색 없이 모양으로 구분된다(POL-U-4).
 */
enum class Mark { CONFIRMED, ATTENTION, ERROR, UNKNOWN, SKIPPED, EMPTY }

@Composable
fun StatusMark(mark: Mark, size: Dp = Size.mark) {
    val cs = MaterialTheme.colorScheme
    val app = appColors
    when (mark) {
        Mark.CONFIRMED -> Glyph(size, fill = app.statusConfirmed) { check(app.onStatus) }
        // 노랑은 밝은 바탕과 대비가 낮아 #131417 외곽선을 두른다.
        Mark.ATTENTION -> Glyph(size, fill = app.statusAttention, ring = app.onStatus, ringWidth = 2.dp, letter = "!", letterColor = app.onStatus)
        Mark.ERROR -> Glyph(size, fill = cs.error, letter = "!", letterColor = app.onStatus)
        Mark.UNKNOWN -> Glyph(size, ring = app.textTertiary, dashed = true, letter = "?", letterColor = app.textTertiary)
        Mark.SKIPPED -> Glyph(size, ring = cs.onSurfaceVariant) { chevrons(cs.onSurfaceVariant) }
        Mark.EMPTY -> Glyph(size, ring = app.textTertiary)
    }
}

// ---------------------------------------------------------------- 상태 배너

/**
 * SCR-04의 판정 상태(DESIGN.md 3절). 위 줄: 상태 기호 + 이름표, 아래: 제목·설명. 테두리가 없다.
 * 교정만 노랑 바탕에 #131417 글자이고, 나머지는 회색 바탕에 기본 글자다. 상태는 기호와 이름표가 구분한다.
 * [showHead]가 false면 바탕만 빌려 쓴다(SCR-04 밖의 알림).
 */
@Composable
fun StateBanner(phase: Phase, title: String, modifier: Modifier = Modifier, body: String? = null, showHead: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val attention = phase == Phase.CORRECTION
    val bg = if (attention) appColors.statusAttention else cs.surfaceVariant
    val fg = if (attention) appColors.onStatus else cs.onSurface
    Column(
        modifier.fillMaxWidth().background(bg, RoundedCornerShape(Radius.card)).padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (showHead) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                StateIcon(phase, bg)
                Text(stringResource(stateLabel(phase)), style = MaterialTheme.typography.labelMedium, color = fg)
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = fg)
        if (!body.isNullOrBlank()) Text(body, style = MaterialTheme.typography.bodyLarge, color = fg)
    }
}

private fun stateLabel(phase: Phase) = when (phase) {
    Phase.PRECHECK -> R.string.state_precheck
    Phase.WAITING -> R.string.state_waiting
    Phase.ALREADY_AT_TARGET -> R.string.state_already
    Phase.PARTIAL -> R.string.state_partial
    Phase.CORRECTION -> R.string.state_correction
    Phase.DONE -> R.string.state_done
    Phase.UNVERIFIABLE -> R.string.state_unverifiable
    Phase.SKIPPED -> R.string.state_skipped
}

/** 배너의 상태 기호. [bg]는 배너 바탕(채운 원의 "구멍" 색). */
@Composable
private fun StateIcon(phase: Phase, bg: Color) {
    val s = Size.stateIcon
    val cs = MaterialTheme.colorScheme
    val app = appColors
    when (phase) {
        Phase.PRECHECK -> Box(Modifier.size(s), contentAlignment = Alignment.Center) { Spinner(30.dp, cs.onSurfaceVariant) }
        Phase.WAITING -> Glyph(s, fill = cs.onSurfaceVariant) { arrow(bg) }
        Phase.ALREADY_AT_TARGET -> Glyph(s, fill = cs.onSurfaceVariant) { refresh(bg) }
        Phase.PARTIAL -> Glyph(s, fill = app.statusConfirmed) { half(app.onStatus) }
        Phase.CORRECTION -> Box(Modifier.size(s), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(s)) { triangle(app.onStatus) }
            Text("!", Modifier.padding(top = 6.dp), color = bg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Phase.DONE -> Glyph(s, fill = app.statusConfirmed) { check(app.onStatus) }
        Phase.UNVERIFIABLE -> Glyph(s, ring = app.textTertiary, dashed = true, letter = "?", letterColor = app.textTertiary)
        Phase.SKIPPED -> Glyph(s, fill = app.textTertiary) { chevrons(bg) }
    }
}

// ---------------------------------------------------------------- 음성 표시

/**
 * 듣는 중(POL-V-13)과 음성 꺼짐(AC-06-6) 표시. 점과 글자로 알린다(POL-U-4). 점은 깜박이지 않는다(POL-U-5).
 * 둘 다 아니면 자리를 차지하지 않는다.
 */
@Composable
fun VoiceBar(voice: AppViewModel.VoiceStatus, modifier: Modifier = Modifier) {
    if (!voice.listening && !voice.off) return
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(64.dp).background(cs.surfaceVariant, CircleShape).padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        if (voice.listening) {
            Box(Modifier.size(20.dp).background(cs.onSurface, CircleShape))
            Text(stringResource(R.string.voice_listening), style = MaterialTheme.typography.labelMedium, color = cs.onSurface)
        } else {
            Box(Modifier.size(20.dp).border(3.dp, appColors.textTertiary, CircleShape))
            Text(stringResource(R.string.voice_off), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- 위치 안내

/**
 * 위치 안내 그림을 담는 판. 판 색은 그림 바탕과 같아 그림의 가장자리가 보이지 않는다. 야간에도 밝게 둔다(DESIGN.md 2-5절). 테두리 없음.
 * 그림이 없으면 판 안에 [fallback] 문구를 쓴다.
 */
@Composable
fun LocatorPlate(image: ImageBitmap?, description: String?, modifier: Modifier = Modifier, fallback: String? = null) {
    val shape = RoundedCornerShape(Radius.card)
    Box(
        modifier.fillMaxWidth().clip(shape).background(appColors.locatorPlate),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().padding(Space.s))
        } else if (fallback != null) {
            Text(fallback, Modifier.padding(Space.l), style = MaterialTheme.typography.bodyLarge, color = appColors.locatorInk, textAlign = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------- 진행 표시

/** 진행 표시. 화면에서 유일하게 계속 움직이는 요소(POL-U-5). 색은 text/secondary. */
@Composable
fun Spinner(size: Dp = 40.dp, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    CircularProgressIndicator(Modifier.size(size), color = color, strokeWidth = 4.dp)

// ---------------------------------------------------------------- 기호 그리기

/** 원 하나에 기호를 그린다. [fill]이면 채운 원, [ring]이면 테두리 원(기본 3dp). 둘 다 주면 채운 원에 외곽선. */
@Composable
private fun Glyph(
    size: Dp,
    fill: Color? = null,
    ring: Color? = null,
    ringWidth: Dp = 3.dp,
    dashed: Boolean = false,
    letter: String? = null,
    letterColor: Color = Color.Unspecified,
    draw: DrawScope.() -> Unit = {},
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = ringWidth.toPx()
            if (fill != null) drawCircle(fill)
            if (ring != null) drawCircle(
                ring,
                radius = this.size.minDimension / 2 - w / 2,
                style = Stroke(w, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null),
            )
            draw()
        }
        if (letter != null) Text(letter, color = letterColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

private fun DrawScope.line(c: Color) = Stroke(size.minDimension * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.check(c: Color) {
    val s = size.minDimension
    val p = Path().apply { moveTo(s * 0.28f, s * 0.52f); lineTo(s * 0.44f, s * 0.67f); lineTo(s * 0.72f, s * 0.36f) }
    drawPath(p, c, style = line(c))
}

private fun DrawScope.arrow(c: Color) {
    val s = size.minDimension
    drawLine(c, Offset(s * 0.27f, s * 0.5f), Offset(s * 0.72f, s * 0.5f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
    val p = Path().apply { moveTo(s * 0.52f, s * 0.31f); lineTo(s * 0.72f, s * 0.5f); lineTo(s * 0.52f, s * 0.69f) }
    drawPath(p, c, style = line(c))
}

private fun DrawScope.refresh(c: Color) {
    val s = size.minDimension
    val r = s * 0.22f
    drawArc(c, startAngle = -60f, sweepAngle = 290f, useCenter = false, topLeft = Offset(s / 2 - r, s / 2 - r), size = GeoSize(r * 2, r * 2), style = line(c))
    // 원호가 시작하는 곳(-60°)에 화살 끝을 단다
    val a = Math.toRadians(-60.0)
    val tip = Offset(s / 2 + r * Math.cos(a).toFloat(), s / 2 + r * Math.sin(a).toFloat())
    val p = Path().apply { moveTo(tip.x - s * 0.12f, tip.y - s * 0.02f); lineTo(tip.x + s * 0.04f, tip.y - s * 0.1f); lineTo(tip.x + s * 0.03f, tip.y + s * 0.08f); close() }
    drawPath(p, c)
}

private fun DrawScope.half(c: Color) {
    val s = size.minDimension
    val r = s * 0.24f
    val tl = Offset(s / 2 - r, s / 2 - r)
    drawCircle(c, radius = r, style = Stroke(s * 0.07f))
    drawArc(c, 90f, 180f, useCenter = true, topLeft = tl, size = GeoSize(r * 2, r * 2))
}

private fun DrawScope.chevrons(c: Color) {
    val s = size.minDimension
    for (dx in listOf(-0.1f, 0.1f)) {
        val p = Path().apply { moveTo(s * (0.4f + dx), s * 0.32f); lineTo(s * (0.58f + dx), s * 0.5f); lineTo(s * (0.4f + dx), s * 0.68f) }
        drawPath(p, c, style = line(c))
    }
}

private fun DrawScope.triangle(c: Color) {
    val s = size.minDimension
    val p = Path().apply { moveTo(s / 2, s * 0.06f); lineTo(s * 0.97f, s * 0.92f); lineTo(s * 0.03f, s * 0.92f); close() }
    drawPath(p, c)
    drawPath(p, c, style = Stroke(s * 0.08f, join = StrokeJoin.Round))
}
