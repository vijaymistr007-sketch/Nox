package com.nox.feature.agent.presentation.component

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nox.R
import com.nox.core.theme.Radius
import com.nox.core.theme.Spacing
import com.nox.feature.agent.domain.knowledge.KnowledgeEntry
import com.nox.feature.agent.presentation.KnowledgeViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Download
import compose.icons.feathericons.MoreVertical
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Search
import compose.icons.feathericons.Star
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记忆知识库页：会话里自动沉淀的结论在这里浏览、搜索、编辑。
 *
 * 入口在会话侧边栏底部；抽取在每轮对话结束时自动发生，页面上的「提取」按钮是手动补一次。
 */
@Composable
internal fun KnowledgeLibraryScreen(
    viewModel: KnowledgeViewModel,
    currentSessionId: String?,
    onNavigateBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editorTarget by remember { mutableStateOf<KnowledgeEntry?>(null) }
    var showNewEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<KnowledgeEntry?>(null) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(FeatherIcons.ArrowLeft, contentDescription = null)
            }
            Text(
                text = stringResource(R.string.knowledge_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { currentSessionId?.let { viewModel.extractFromSession(it) {} } },
                enabled = !busy && !currentSessionId.isNullOrBlank()
            ) {
                Icon(
                    FeatherIcons.Download,
                    contentDescription = stringResource(R.string.knowledge_extract),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = { showNewEditor = true }) {
                Icon(
                    FeatherIcons.Plus,
                    contentDescription = stringResource(R.string.knowledge_new),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            placeholder = { Text(stringResource(R.string.knowledge_search_hint)) },
            leadingIcon = {
                Icon(FeatherIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            singleLine = true,
            shape = RoundedCornerShape(Radius.md)
        )

        Spacer(Modifier.height(Spacing.sm))

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = stringResource(
                        if (query.isBlank()) R.string.knowledge_empty else R.string.knowledge_empty_search
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = Spacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(entries, key = { it.id }) { entry ->
                    KnowledgeCard(
                        entry = entry,
                        onClick = { editorTarget = entry },
                        onTogglePin = { viewModel.setPinned(entry.id, !entry.pinned) },
                        onDelete = { deleteTarget = entry }
                    )
                }
            }
        }
    }

    if (showNewEditor || editorTarget != null) {
        KnowledgeEditorDialog(
            entry = editorTarget,
            onDismiss = {
                showNewEditor = false
                editorTarget = null
            },
            onSave = { title, content, tags ->
                viewModel.save(editorTarget?.id, title, content, tags) { ok ->
                    Toast.makeText(
                        context,
                        context.getString(if (ok) R.string.knowledge_saved else R.string.knowledge_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (ok) {
                        showNewEditor = false
                        editorTarget = null
                    }
                }
            }
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.knowledge_delete_confirm)) },
            text = { Text(entry.title) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(entry.id)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KnowledgeCard(
    entry: KnowledgeEntry,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val timeText = remember(entry.updatedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.updatedAt))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.pinned) {
                Icon(
                    FeatherIcons.Star,
                    contentDescription = stringResource(R.string.knowledge_pinned),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        FeatherIcons.MoreVertical,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (entry.pinned) R.string.knowledge_unpin else R.string.knowledge_pin
                                )
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.common_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
        Text(
            text = entry.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        Row(
            modifier = Modifier.padding(top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.hitCount > 0) {
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.knowledge_hits, entry.hitCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            entry.tags.take(3).forEach { tag ->
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun KnowledgeEditorDialog(
    entry: KnowledgeEntry?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, tags: String) -> Unit
) {
    var title by remember(entry) { mutableStateOf(entry?.title.orEmpty()) }
    var content by remember(entry) { mutableStateOf(entry?.content.orEmpty()) }
    var tags by remember(entry) { mutableStateOf(entry?.tags?.joinToString(",").orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (entry == null) R.string.knowledge_new else R.string.knowledge_edit_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.knowledge_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.knowledge_field_content)) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.knowledge_field_tags)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, content, tags) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
