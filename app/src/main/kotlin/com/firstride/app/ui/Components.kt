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
 * 공통 컴포넌트. 이름은 Figma 01 컴포넌트 보드와 같다(기술 설계 12절). 값은 docs/08-design-tokens.md 4절.
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

private enum class ButtonKind { PRIMARY, SECONDARY, TEXT }

@Composable
private fun AppButton(kind: ButtonKind, text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    val shape = RoundedCornerShape(Radius.button)
    val cs = MaterialTheme.colorScheme
    val app = appColors
    Pressable(onClick, modifier.heightIn(min = Size.buttonH), enabled, shape) { pressed ->
        val bg = when (kind) {
            ButtonKind.PRIMARY -> when { !enabled -> app.disabledContainer; pressed -> app.primaryPressed; else -> cs.primary }
            ButtonKind.SECONDARY -> if (pressed) app.secondaryPressed else cs.surface
            ButtonKind.TEXT -> if (pressed) app.secondaryPressed else Color.Transparent
        }
        val fg = when {
            !enabled -> app.textDisabled
            kind == ButtonKind.PRIMARY -> cs.onPrimary
            kind == ButtonKind.SECONDARY -> cs.onSurface
            else -> cs.primary
        }
        var m = Modifier.heightIn(min = Size.buttonH).background(bg, shape)
        if (kind == ButtonKind.SECONDARY) m = m.border(2.dp, if (enabled) cs.outline else cs.outlineVariant, shape)
        Box(m.padding(horizontal = Space.xl), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center)
        }
    }
}

/** 주된 행동 하나. 높이 88dp, 모서리 20dp. (예전 `BigButton(primary = true)`) */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    AppButton(ButtonKind.PRIMARY, text, onClick, modifier, enabled)

/** 보조 행동. 테두리 2dp. (예전 `BigButton(primary = false)`) */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    AppButton(ButtonKind.SECONDARY, text, onClick, modifier, enabled)

/** 가벼운 행동(그만하기, 건너뛰기, 돌아가기). 글자만, 누르는 동안 바탕이 생긴다. */
@Composable
fun TextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    AppButton(ButtonKind.TEXT, text, onClick, modifier, enabled)

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
fun TopBarInfo(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

// ---------------------------------------------------------------- 목록·카드

/** 카드 모양의 누를 수 있는 상자(CourseCard 등). 바탕 surface, 테두리 2dp, 모서리 24dp, 안쪽 24dp. */
@Composable
fun CardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.card)
    val cs = MaterialTheme.colorScheme
    Pressable(onClick, modifier.fillMaxWidth(), true, shape) { pressed ->
        Box(
            Modifier.fillMaxWidth().heightIn(min = Size.touchMin)
                .background(if (pressed) appColors.secondaryPressed else cs.surface, shape)
                .border(2.dp, if (highlight) cs.primary else cs.outlineVariant, shape)
                .padding(Space.l),
        ) { content() }
    }
}

/** 알약 모양 이름표(코스 상태 등). 테두리 border/strong. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, border: Color = MaterialTheme.colorScheme.outline) {
    Text(
        text,
        modifier.border(2.dp, border, CircleShape).padding(horizontal = 18.dp, vertical = 8.dp),
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
                    if (!description.isNullOrBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
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
 * 조건 하나의 상태(AC-04-3). 기호 + 문구 + 색으로 구분한다(POL-U-4). 폴링하는 조건은 "확인 중"을 덧붙인다(AC-04-7).
 */
@Composable
fun ConditionRow(view: ConditionView, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val app = appColors
    val (status, color) = when (view.status) {
        ConditionStatus.MET -> R.string.condition_met to app.markMet
        ConditionStatus.ALREADY_MET -> R.string.condition_already_met to app.markAlready
        ConditionStatus.UNMET -> R.string.condition_unmet to cs.onSurfaceVariant
        ConditionStatus.TOGGLE_NEEDED -> R.string.condition_toggle to app.markAlready
        ConditionStatus.UNKNOWN -> R.string.condition_unknown to app.unverifiable.fg
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.ml),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(Size.mark), contentAlignment = Alignment.Center) {
                if (view.polling && view.status == ConditionStatus.UNMET) Spinner(24.dp, cs.onSurfaceVariant) else ConditionMark(view.status)
            }
            Text(view.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            val polling = if (view.polling) " ${stringResource(R.string.condition_polling)}" else ""
            Text(stringResource(status) + polling, style = MaterialTheme.typography.labelMedium, color = color)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
    }
}

/** 조건 줄의 44dp 표시. 문서 1-3절. */
@Composable
private fun ConditionMark(status: ConditionStatus) {
    val cs = MaterialTheme.colorScheme
    val app = appColors
    when (status) {
        ConditionStatus.MET -> Glyph(Size.mark, fill = app.markMet) { check(cs.surface) }
        ConditionStatus.ALREADY_MET -> Glyph(Size.mark, ring = app.markAlready) { check(app.markAlready) }
        ConditionStatus.UNMET -> Glyph(Size.mark, ring = cs.onSurfaceVariant) {}
        ConditionStatus.TOGGLE_NEEDED -> Glyph(Size.mark, ring = app.markAlready) { refresh(app.markAlready) }
        ConditionStatus.UNKNOWN -> Glyph(Size.mark, ring = app.markUnknown, dashed = true, letter = "?", letterColor = app.markUnknown)
    }
}

// ---------------------------------------------------------------- 상태 배너

/**
 * SCR-04의 판정 상태(문서 1-2절). 위 줄: 상태 기호 + 이름표, 아래: 제목·설명.
 * [showHead]가 false면 색만 빌려 쓴다(SCR-04 밖의 알림).
 */
@Composable
fun StateBanner(phase: Phase, title: String, modifier: Modifier = Modifier, body: String? = null, showHead: Boolean = true) {
    val c = appColors.status(phase)
    val shape = RoundedCornerShape(Radius.card)
    var m = modifier.fillMaxWidth().background(c.bg, shape)
    m = when (phase) {
        Phase.DONE -> m
        Phase.UNVERIFIABLE -> m.dashedBorder(2.dp, c.icon, Radius.card)
        Phase.CORRECTION -> m.border(3.dp, c.icon, shape)
        else -> m.border(2.dp, c.icon, shape)
    }
    Column(m.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (showHead) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                StateIcon(phase, c)
                Text(stringResource(stateLabel(phase)), style = MaterialTheme.typography.labelMedium, color = c.fg)
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.fg)
        if (!body.isNullOrBlank()) Text(body, style = MaterialTheme.typography.bodyLarge, color = c.fg)
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

@Composable
private fun StateIcon(phase: Phase, c: StatusColor) {
    val s = Size.stateIcon
    when (phase) {
        Phase.PRECHECK -> Box(Modifier.size(s), contentAlignment = Alignment.Center) { Spinner(30.dp, c.icon) }
        Phase.WAITING -> Glyph(s, fill = c.icon) { arrow(c.bg) }
        Phase.ALREADY_AT_TARGET -> Glyph(s, fill = c.icon) { refresh(c.bg) }
        Phase.PARTIAL -> Glyph(s, fill = c.icon) { half(c.bg) }
        Phase.CORRECTION -> Box(Modifier.size(s), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(s)) { triangle(c.icon) }
            Text("!", Modifier.padding(top = 6.dp), color = c.bg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Phase.DONE -> Glyph(s, fill = c.icon) { check(c.bg) }
        Phase.UNVERIFIABLE -> Glyph(s, ring = c.icon, dashed = true, letter = "?", letterColor = c.icon)
        Phase.SKIPPED -> Glyph(s, fill = c.icon) { chevrons(c.bg) }
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
            Box(Modifier.size(20.dp).background(cs.primary, CircleShape))
            Text(stringResource(R.string.voice_listening), style = MaterialTheme.typography.labelMedium, color = cs.onSurface)
        } else {
            Box(Modifier.size(20.dp).border(3.dp, cs.onSurfaceVariant, CircleShape))
            Text(stringResource(R.string.voice_off), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- 위치 안내

/**
 * 위치 안내 그림을 담는 판. 판 색은 그림 바탕과 같아 그림의 가장자리가 보이지 않는다. 야간에도 밝게 둔다(문서 1-4절).
 * 그림이 없으면 판 안에 [fallback] 문구를 쓴다.
 */
@Composable
fun LocatorPlate(image: ImageBitmap?, description: String?, modifier: Modifier = Modifier, fallback: String? = null) {
    val shape = RoundedCornerShape(Radius.card)
    Box(
        modifier.fillMaxWidth().clip(shape).background(appColors.locatorPlate).border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape),
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

/** 진행 표시. 화면에서 유일하게 계속 움직이는 요소(POL-U-5). */
@Composable
fun Spinner(size: Dp = 40.dp, color: Color = MaterialTheme.colorScheme.primary) =
    CircularProgressIndicator(Modifier.size(size), color = color, strokeWidth = 4.dp)

// ---------------------------------------------------------------- 기호 그리기

/** 원 하나에 기호를 그린다. [fill]이면 채운 원, [ring]이면 테두리 원(3dp). */
@Composable
private fun Glyph(
    size: Dp,
    fill: Color? = null,
    ring: Color? = null,
    dashed: Boolean = false,
    letter: String? = null,
    letterColor: Color = Color.Unspecified,
    draw: DrawScope.() -> Unit = {},
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 3.dp.toPx()
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

/** 점선 테두리(확인 불가). */
private fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(w / 2, w / 2),
        size = GeoSize(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
    )
}
