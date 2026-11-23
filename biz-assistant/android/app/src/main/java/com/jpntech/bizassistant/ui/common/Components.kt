package com.jpntech.bizassistant.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jpntech.bizassistant.data.Labels
import com.jpntech.bizassistant.data.settings.ENTITIES
import com.jpntech.bizassistant.ui.theme.BizColors

@Composable
fun Tag(text: String, bg: Color = BizColors.Chip, fg: Color = BizColors.Ink, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun UrgencyTag(urgency: String) = when (urgency) {
    "high" -> Tag("긴급", BizColors.DangerSoft, BizColors.Danger)
    "low" -> Tag("낮음", BizColors.Chip, BizColors.Muted)
    else -> Tag("보통", BizColors.NavySoft, BizColors.Navy)
}

@Composable
fun TypeTag(type: String) = when (type) {
    "fault" -> Tag(Labels.type(type), BizColors.DangerSoft, BizColors.Danger)
    "payment" -> Tag(Labels.type(type), BizColors.OkSoft, BizColors.Ok)
    "document", "nightwork" -> Tag(Labels.type(type), BizColors.WarnSoft, BizColors.Warn)
    else -> Tag(Labels.type(type), BizColors.NavySoft, BizColors.Navy)
}

@Composable
fun EntityTag(entity: String) = when (entity) {
    "서일이앤씨" -> Tag(entity, BizColors.TealSoft, BizColors.Teal)
    else -> Tag(entity, BizColors.NavySoft, BizColors.Navy)
}

/** 전체 / 제이피앤텍 / 서일이앤씨 전환. selected가 null이면 전체 */
@Composable
fun EntityFilterRow(selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("전체") })
        ENTITIES.forEach { e ->
            FilterChip(selected = selected == e, onClick = { onSelect(e) }, label = { Text(e) })
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = BizColors.Muted,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = BizColors.Muted, modifier = modifier.padding(12.dp))
}
