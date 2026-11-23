package com.jpntech.bizassistant.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jpntech.bizassistant.data.Labels
import com.jpntech.bizassistant.data.settings.ENTITIES
import com.jpntech.bizassistant.ui.theme.BizColors

/** 비서의 표식. 가는 고리와 가운데 점. live이면 천천히 숨 쉬듯 밝기가 변한다. */
@Composable
fun AssistantRing(modifier: Modifier = Modifier, live: Boolean = false, size: androidx.compose.ui.unit.Dp = 34.dp) {
    val transition = rememberInfiniteTransition(label = "ring")
    val pulse by transition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    )
    val a = if (live) pulse else 0.9f
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(color = BizColors.Signal.copy(alpha = 0.18f * a), radius = r)
        drawCircle(color = BizColors.Signal, radius = r - 1.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color = BizColors.Signal.copy(alpha = a), radius = r * 0.42f)
    }
}

/** 화면 맨 위, 비서가 상황을 한두 문장으로 말하는 띠. 숫자나 핵심어는 청록으로 강조한다. */
@Composable
fun AssistantBand(kicker: String?, voice: AnnotatedString, live: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AssistantRing(live = live, modifier = Modifier.padding(top = 2.dp))
        Column {
            if (!kicker.isNullOrBlank()) Text(kicker, style = MaterialTheme.typography.labelMedium, color = BizColors.Muted, modifier = Modifier.padding(bottom = 4.dp))
            Text(voice, style = MaterialTheme.typography.bodyLarge, color = BizColors.Text)
        }
    }
}

/** "오늘 일정 {3건}이 있습니다" 처럼 중괄호 안을 강조한다. */
fun voice(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val open = text.indexOf('{', i)
        if (open < 0) { append(text.substring(i)); break }
        append(text.substring(i, open))
        val close = text.indexOf('}', open)
        if (close < 0) { append(text.substring(open)); break }
        withStyle(SpanStyle(color = BizColors.Signal, fontWeight = FontWeight.Medium)) { append(text.substring(open + 1, close)) }
        i = close + 1
    }
}

/** 세로 레일. 왼쪽에 가는 선과 점이 있고 항목은 오른쪽에 쌓인다. */
@Composable
fun RailItem(
    last: Boolean,
    tone: Color = BizColors.Muted,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    Row(if (onClick != null) base.clickable(onClick = onClick) else base) {
        Box(Modifier.width(24.dp).fillMaxHeight()) {
            if (!last) Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp).width(1.dp).fillMaxHeight().background(BizColors.Line))
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(9.dp)
                    .background(if (active) tone else BizColors.Ground, CircleShape)
                    .border(1.5.dp, tone, CircleShape),
            )
        }
        Column(Modifier.weight(1f)) {
            Column(Modifier.padding(start = 4.dp, top = 9.dp, bottom = 11.dp, end = 4.dp), content = content)
            if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(BizColors.LineSoft))
        }
    }
}

@Composable
fun Pill(text: String, tone: Color = BizColors.Muted, modifier: Modifier = Modifier) {
    Text(
        text,
        color = tone,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.background(tone.copy(alpha = 0.14f), RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun UrgencyPill(urgency: String) = when (urgency) {
    "high" -> Pill("긴급", BizColors.Red)
    "low" -> Pill("낮음", BizColors.Muted)
    else -> Pill("보통", BizColors.Signal)
}

@Composable
fun TypePill(type: String) = when (type) {
    "fault" -> Pill(Labels.type(type), BizColors.Red)
    "payment" -> Pill(Labels.type(type), BizColors.Green)
    "document", "nightwork" -> Pill(Labels.type(type), BizColors.Amber)
    else -> Pill(Labels.type(type), BizColors.Muted)
}

@Composable
fun EntityPill(entity: String) = Pill(entity, if (entity == "서일이앤씨") BizColors.Teal else BizColors.Signal)

// 이전 이름과의 호환
@Composable fun Tag(text: String, bg: Color = BizColors.Chip, fg: Color = BizColors.Text, modifier: Modifier = Modifier) = Pill(text, fg, modifier)
@Composable fun UrgencyTag(urgency: String) = UrgencyPill(urgency)
@Composable fun TypeTag(type: String) = TypePill(type)
@Composable fun EntityTag(entity: String) = EntityPill(entity)

@Composable
fun EntityFilterRow(selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    val colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = BizColors.SignalSoft, selectedLabelColor = BizColors.Signal,
        containerColor = Color.Transparent, labelColor = BizColors.Muted,
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("전체") }, colors = colors)
        ENTITIES.forEach { e -> FilterChip(selected = selected == e, onClick = { onSelect(e) }, label = { Text(e) }, colors = colors) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = BizColors.Muted, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = BizColors.Dim, modifier = modifier.padding(vertical = 12.dp))
}

/** 큰 숫자 하나와 작은 설명. 위에 가는 선. */
@Composable
fun Stat(value: String, unit: String?, label: String, tone: Color = BizColors.Text, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Column(m.padding(top = 14.dp, bottom = 10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BizColors.Line))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displaySmall, color = tone)
            if (unit != null) Text(unit, style = MaterialTheme.typography.bodyMedium, color = tone, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = BizColors.Muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(BizColors.Panel, RoundedCornerShape(14.dp)).padding(14.dp), content = content)
}

@Composable
fun Faded(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Box(modifier.alpha(0.6f)) { content() }
