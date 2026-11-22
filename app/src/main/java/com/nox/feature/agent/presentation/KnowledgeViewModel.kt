package com.nox.feature.agent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nox.feature.agent.data.local.dao.AgentMessageDao
import com.nox.feature.agent.domain.knowledge.KnowledgeEntry
import com.nox.feature.agent.domain.knowledge.KnowledgeRepository
import com.nox.feature.agent.domain.model.AgentMessage
import com.nox.feature.agent.domain.session.SessionUseCase
import com.nox.feature.workspace.data.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 记忆知识库页：浏览、搜索、编辑、删除、置顶，以及手动触发一次当前会话抽取。
 *
 * 列表走 Flow 直连 Room，增删改后无需手动刷新；抽取是显式动作，结果用 [message] 回显。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
    private val agentMessageDao: AgentMessageDao,
    private val sessionUseCase: SessionUseCase,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _projectRoot = MutableStateFlow<String?>(null)
    val projectRoot: StateFlow<String?> = _projectRoot.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val entries: StateFlow<List<KnowledgeEntry>> =
        combine(_query, _projectRoot) { query, root -> query to root }
            .flatMapLatest { (query, root) ->
                if (query.isBlank()) repository.observeForProject(root)
                else repository.search(query, root)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { workspaceRepository.currentPath() }
            _projectRoot.value = path.takeIf { it.isNotBlank() }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(id) }
        }
    }

    fun setPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.setPinned(id, pinned) }
        }
    }

    fun save(
        id: Long?,
        title: String,
        content: String,
        tags: String,
        onResult: (Boolean) -> Unit
    ) {
        if (content.isBlank()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                repository.save(
                    id = id,
                    title = title,
                    content = content,
                    tags = tags,
                    projectRoot = _projectRoot.value
                )
            }
            onResult(ok)
        }
    }

    /** 手动从指定会话抽取一次；返回新增条数。 */
    fun extractFromSession(sessionId: String, onDone: (Int) -> Unit) {
        if (sessionId.isBlank()) {
            onDone(0)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val added = withContext(Dispatchers.IO) {
                runCatching {
                    val session = sessionUseCase.getSessionById(sessionId)
                    val messages = agentMessageDao.getMessagesBySessionOnce(sessionId)
                    repository.extractAndStore(
                        sessionId = sessionId,
                        projectRoot = session?.workspacePath?.takeIf { it.isNotBlank() },
                        messages = messages.mapNotNull { entity ->
                            when (entity.role.uppercase()) {
                                "USER" -> AgentMessage.UserMessage(id = entity.id, content = entity.content)
                                "ASSISTANT" -> AgentMessage.AssistantMessage(id = entity.id, content = entity.content)
                                else -> null
                            }
                        }
                    )
                }.getOrDefault(0)
            }
            _busy.value = false
            _message.value = if (added > 0) "已提取 $added 条新知识" else "没有新的可提取内容"
            onDone(added)
        }
    }
}
