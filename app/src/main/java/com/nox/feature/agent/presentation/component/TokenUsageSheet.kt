package com.nox.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nox.R
import com.nox.core.theme.Radius
import com.nox.core.theme.Spacing
import com.nox.core.ui.AdaptiveModalBottomSheet
import com.nox.feature.agent.data.local.dao.SessionCallStats
import compose.icons.FeatherIcons
import compose.icons.feathericons.Database
import java.util.Locale

/**
 * 输入栏「Token 用量」面板。
 *
 * 数据来自两处：上下文占用取 workflow 实时上报的 [contextUsed]/[contextLimit]（与发送键外圈弧同源），
 * 累计用量取 llm_call_records 按 sessionId 聚合的 [stats]。两者口径不同，分开成组展示，
 * 避免把「当前上下文」和「本会话累计消耗」混成一个数字。
 */
@Composable
internal fun TokenUsageSheet(
    contextUsed: Int,
    contextLimit: Int,
    estimated: Boolean,
    stats: SessionCallStats?,
    rounds: Int,
    steps: Int,
    onCompact: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val progress = if (contextLimit > 0) (contextUsed.toFloat() / contextLimit).coerceIn(0f, 1f) else 0f
    val hasRecords = stats != null && stats.calls > 0

    AdaptiveModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    FeatherIcons.Database,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.token_usage_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.sm)
                )
                Text(
                    text = formatTokens((stats?.inputTokens ?: 0L) + (stats?.outputTokens ?: 0L)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            UsageSection {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.token_usage_context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(
                            R.string.token_usage_context_ratio,
                            formatTokens(contextUsed.toLong()),
                            if (contextLimit > 0) formatTokens(contextLimit.toLong()) else "—",
                            (progress * 100).toInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(contextProgressColor(progress))
                        )
                    }
                }
                if (estimated) {
                    Text(
                        text = stringResource(R.string.token_usage_estimated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (hasRecords) {
                UsageSection {
                    UsageRow(
                        label = stringResource(R.string.token_usage_cached),
                        value = formatTokens(stats.cachedInputTokens)
                    )
                    UsageRow(
                        label = stringResource(R.string.token_usage_uncached),
                        value = formatTokens((stats.inputTokens - stats.cachedInputTokens).coerceAtLeast(0))
                    )
                    UsageRow(
                        label = stringResource(R.string.token_usage_output),
                        value = formatTokens(stats.outputTokens)
                    )
                }
            }

            UsageSection {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.token_usage_session),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.token_usage_rounds_steps, rounds, steps),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (hasRecords) {
                    UsageRow(
                        label = stringResource(R.string.token_usage_calls),
                        value = stats.calls.toString()
                    )
                    UsageRow(
                        label = stringResource(R.string.token_usage_duration),
                        value = formatSeconds(stats.avgDurationMillis?.times(stats.calls))
                    )
                    UsageRow(
                        label = stringResource(R.string.token_usage_ttft),
                        value = formatSeconds(stats.avgTtfbMillis)
                    )
                    UsageRow(
                        label = stringResource(R.string.token_usage_tps),
                        value = formatTps(stats)
                    )
                    if (stats.errors > 0) {
                        UsageRow(
                            label = stringResource(R.string.token_usage_errors, stats.errors),
                            value = "",
                            valueColor = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.token_usage_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (onCompact != null) {
                Button(
                    onClick = onCompact,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Text(stringResource(R.string.token_usage_compact))
                }
            }
        }
    }
}

@Composable
private fun UsageSection(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        content()
    }
}

@Composable
private fun UsageRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor
        )
    }
}

/** 上下文占用越高越警示：过半转琥珀，过八成转红。 */
@Composable
private fun contextProgressColor(progress: Float): Color = when {
    progress >= 0.8f -> MaterialTheme.colorScheme.error
    progress >= 0.5f -> Color(0xFFF59E0B)
    else -> MaterialTheme.colorScheme.primary
}

private fun formatTokens(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
    value >= 10_000 -> String.format(Locale.US, "%.1fK", value / 1000.0)
    else -> String.format(Locale.US, "%,d", value)
}

private fun formatSeconds(millis: Double?): String = when {
    millis == null || millis <= 0.0 -> "—"
    millis >= 1000.0 -> String.format(Locale.US, "%.1fs", millis / 1000.0)
    else -> String.format(Locale.US, "%.0fms", millis)
}

private fun formatTps(stats: SessionCallStats): String {
    val totalSeconds = (stats.avgDurationMillis ?: 0.0) * stats.calls / 1000.0
    if (totalSeconds <= 0.0 || stats.outputTokens <= 0) return "—"
    return String.format(Locale.US, "%.0f tok/s", stats.outputTokens / totalSeconds)
}
