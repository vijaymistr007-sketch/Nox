package com.nox.feature.agent.domain.knowledge

import com.nox.feature.agent.domain.model.AgentMessage

/** 抽取出的候选条目，尚未落库。 */
data class KnowledgeCandidate(
    val title: String,
    val content: String,
    val tags: String,
    val sourceMessageId: String
)

/**
 * 会话内容 → 知识条目的规则抽取器。
 *
 * 刻意不用 LLM：抽取发生在每轮对话结束后，走模型会额外计费、增加延迟，
 * 且离线/无 key 时整条链路会断。规则版只捞「明确带约定语气」和「明确给结论」的句子，
 * 宁可漏，不可滥——噪音条目会稀释召回质量。
 */
internal object KnowledgeExtractor {

    /** 用户表达长期偏好的信号词。 */
    private val PREFERENCE_SIGNALS = listOf(
        "记住", "以后", "今后", "总是", "永远", "不要", "别用", "必须", "务必",
        "约定", "规范", "偏好", "习惯", "统一", "禁止", "只能用", "一律", "默认"
    )

    /** 助手给出结论的信号词。 */
    private val CONCLUSION_SIGNALS = listOf(
        "根因", "结论", "总结", "已修复", "已解决", "原因是", "方案是", "正确做法",
        "注意：", "坑：", "踩坑", "关键点", "必须注意"
    )

    /** 单条正文上限：知识库是索引不是存档，超长内容截断。 */
    private const val MAX_CONTENT = 600

    private const val MIN_USER_LENGTH = 12
    private const val MIN_ASSISTANT_LENGTH = 40

    fun extract(messages: List<AgentMessage>): List<KnowledgeCandidate> {
        val out = mutableListOf<KnowledgeCandidate>()
        for (message in messages) {
            when (message) {
                is AgentMessage.UserMessage -> {
                    val text = message.content.trim()
                    if (text.length < MIN_USER_LENGTH) continue
                    if (PREFERENCE_SIGNALS.none { text.contains(it) }) continue
                    out += candidate(text, message.id, "偏好,约定")
                }

                is AgentMessage.AssistantMessage -> {
                    val text = message.content.trim()
                    if (text.length < MIN_ASSISTANT_LENGTH) continue
                    if (CONCLUSION_SIGNALS.none { text.contains(it) }) continue
                    out += candidate(text, message.id, "结论")
                }

                is AgentMessage.ToolResultMessage -> Unit
            }
        }
        return out
    }

    private fun candidate(text: String, messageId: String, baseTags: String): KnowledgeCandidate {
        val content = if (text.length <= MAX_CONTENT) text else text.take(MAX_CONTENT) + "…"
        val keywords = KnowledgeText.tokenize(text)
            .filter { it.length >= 3 && it.any { c -> c.isLetter() } }
            .take(6)
        val tags = (baseTags.split(',') + keywords).distinct().joinToString(",")
        return KnowledgeCandidate(
            title = KnowledgeText.titleOf(text),
            content = content,
            tags = tags,
            sourceMessageId = messageId
        )
    }
}
