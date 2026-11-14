package com.firstride.app.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.firstride.R
import com.firstride.app.start.CarModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.floor

/** 차 이미지를 읽는 쪽. 화면은 어디서 오는지 모른다(assets든 테스트용이든). */
class CarImages(val frame: (CarModel, Int) -> ImageBitmap?, val interior: (CarModel) -> ImageBitmap?)

/** 손을 떼고 이만큼 가만히 있으면 천천히 돈다(ms). */
private const val IDLE_BEFORE_SPIN_MS = 2_500L

/** 한 바퀴를 도는 시간(초). 멀리서 봐도 도는 게 보이되 시선을 끌지 않는 느린 속도. */
private const val SECONDS_PER_TURN = 28f

/** 바깥 틀 안쪽 여백. 안쪽 면의 모서리는 바깥보다 이만큼 작게 해 곡선이 같은 중심을 갖게 한다. */
private val BEZEL = 6.dp
private val SHELL = RoundedCornerShape(38.dp)
private val CORE = RoundedCornerShape(32.dp)

/**
 * 홈의 360도 차 무대. 바깥 틀(얇은 선)과 안쪽 면의 이중 구조다. 좌우로 밀어 돌리고, 손을 떼면 관성으로 잠시 더 돈다.
 * 가만히 두면 천천히 돈다(시스템 애니메이션이 꺼져 있으면 돌지 않는다). [models]가 비어 있으면 [empty]를 보인다.
 */
@Composable
fun CarStage(
    models: List<CarModel>,
    selected: CarModel?,
    onSelect: (CarModel) -> Unit,
    images: CarImages,
    modifier: Modifier = Modifier,
    empty: String? = null,
) {
    var interior by remember { mutableStateOf(false) }
    val model = selected?.takeIf { it in models }
    val showInterior = interior && model?.interior != null

    Box(
        modifier
            .clip(SHELL)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), SHELL)
            .padding(BEZEL),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CORE)
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background),
                        center = Offset.Unspecified,
                    ),
                ),
        ) {
            if (model == null) {
                Text(
                    empty.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                if (showInterior) InteriorView(model, images, Modifier.fillMaxSize())
                else ExteriorView(model, images, Modifier.fillMaxSize())

                Text(
                    model.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 24.dp),
                )
                Controls(
                    models, model, onSelect,
                    interior = showInterior, canInterior = model.interior != null, onInterior = { interior = it },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ExteriorView(model: CarModel, images: CarImages, modifier: Modifier) {
    val n = model.frames.size
    var angle by remember(model.id) { mutableFloatStateOf(0f) }            // 프레임 단위, 소수 포함
    var width by remember { mutableFloatStateOf(1f) }
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val reduceMotion = rememberReduceMotion()
    val framesPerPx = n / (width * 1.2f)                                    // 화면 폭의 1.2배를 밀면 한 바퀴

    // 최근 프레임만 들고 있는다. 한 바퀴 전부를 비트맵으로 쥐면 차량 화면 메모리를 많이 쓴다.
    val cache = remember(model.id) {
        object : LinkedHashMap<Int, ImageBitmap>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?) = size > 24
        }
    }
    // 소수 각도만큼 다음 프레임을 섞어 보여, 프레임 수가 적어도 천천히 돌 때 끊겨 보이지 않게 한다.
    val index = floor(angle).toInt().mod(n)
    val nextIndex = (index + 1).mod(n)
    val blend = angle - floor(angle)
    var shown by remember(model.id) { mutableStateOf<ImageBitmap?>(null) }
    var shownNext by remember(model.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(model.id, index) {
        val bitmap = cache[index] ?: withContext(Dispatchers.IO) { images.frame(model, index) }?.also { cache[index] = it }
        if (bitmap != null) shown = bitmap            // 읽는 동안은 앞 프레임을 그대로 둔다
    }
    LaunchedEffect(model.id, nextIndex) {
        val bitmap = cache[nextIndex] ?: withContext(Dispatchers.IO) { images.frame(model, nextIndex) }?.also { cache[nextIndex] = it }
        if (bitmap != null) shownNext = bitmap
    }

    // 손을 뗀 뒤 잠시 가만히 있으면 천천히 돈다. 시스템 애니메이션이 꺼져 있으면(접근성) 돌지 않는다.
    LaunchedEffect(model.id, dragging, reduceMotion) {
        if (dragging || reduceMotion) return@LaunchedEffect
        delay(IDLE_BEFORE_SPIN_MS)
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) angle += (t - last) / 1e9f * n / SECONDS_PER_TURN
                last = t
            }
        }
    }

    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                state = rememberDraggableState { dx -> angle -= dx * framesPerPx },
                orientation = Orientation.Horizontal,
                interactionSource = interaction,
                onDragStopped = { v ->
                    val start = angle
                    Animatable(0f).animateDecay(-v * framesPerPx, exponentialDecay()) { angle = start + value }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val description = stringResource(R.string.car_stage_description, model.name)
        val framePadding = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 104.dp)
        shown?.let { Image(it, description, framePadding, contentScale = ContentScale.Fit) }
        // 위에 얹는 다음 프레임은 소수 각도만큼만 보인다. 아래 프레임이 불투명하므로 두 장이 정확히 섞인다.
        shownNext?.let { Image(it, null, framePadding, contentScale = ContentScale.Fit, alpha = blend) }
    }
}

/** 가로로 긴 파노라마를 좌우로 밀어 둘러본다. 양 끝이 이어진다(한 바퀴 360도 사진을 전제한다). */
@Composable
private fun InteriorView(model: CarModel, images: CarImages, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, model.id) { value = withContext(Dispatchers.IO) { images.interior(model) } }
    var pan by remember(model.id) { mutableFloatStateOf(0f) }               // 이미지 폭에 대한 비율
    var box by remember { mutableStateOf(IntSize(1, 1)) }
    val desc = stringResource(R.string.car_stage_interior_description, model.name)
    val img = image
    val tileWidth = if (img == null) 1f else img.width * (box.height.toFloat() / img.height)

    Canvas(
        modifier
            .semantics { contentDescription = desc }
            .onSizeChanged { box = it }
            .draggable(
                state = rememberDraggableState { dx -> pan = (pan - dx / tileWidth).mod(1f) },
                orientation = Orientation.Horizontal,
                onDragStopped = { v ->
                    val start = pan
                    Animatable(0f).animateDecay(-v / tileWidth, exponentialDecay()) { pan = (start + value).mod(1f) }
                },
            ),
    ) {
        if (img == null) return@Canvas
        var x = -pan * tileWidth
        while (x < this.size.width) {
            drawImage(
                img,
                dstOffset = IntOffset(x.toInt(), 0),
                dstSize = IntSize(tileWidth.toInt() + 1, this.size.height.toInt()),
            )
            x += tileWidth
        }
    }
}

@Composable
private fun Controls(
    models: List<CarModel>,
    selected: CarModel,
    onSelect: (CarModel) -> Unit,
    interior: Boolean,
    canInterior: Boolean,
    onInterior: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // 차종이 하나뿐이면 고를 것이 없으니 숨긴다.
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (models.size > 1) models.forEach { m -> Pill(m.name, m.id == selected.id) { onSelect(m) } }
        }
        if (canInterior) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(stringResource(R.string.car_view_exterior), !interior) { onInterior(false) }
                Pill(stringResource(R.string.car_view_interior), interior) { onInterior(true) }
            }
        }
    }
}

/** 선택된 것은 포인트색으로 채우고, 아닌 것은 선만 둔다. 높이는 차량 화면의 터치 크기(64dp)를 지킨다. */
@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .heightIn(min = 64.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else Color.Transparent)
            .border(1.5.dp, if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.6f), CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
        )
    }
}

/** 시스템에서 애니메이션을 끄면(배율 0) 자동 회전을 하지 않는다. */
@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
