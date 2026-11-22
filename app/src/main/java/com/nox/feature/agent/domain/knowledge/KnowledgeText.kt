package com.nox.feature.agent.domain.knowledge

import java.security.MessageDigest

/**
 * 知识库的文本工具：归一化、指纹、分词、相似度。
 *
 * 不引第三方分词器：这里只需要「够用的近似」，中文按 2-gram、英文按词切，
 * 目的是去重与粗排，不是做语义检索。
 */
internal object KnowledgeText {

    private val PUNCT = Regex("[\\s\\p{Punct}，。！？；：、“”‘’（）《》【】…—·～]+")

    private val STOPWORDS = setOf(
        "的", "了", "是", "在", "和", "与", "就", "都", "也", "要", "这", "那",
        "我", "你", "他", "它", "们", "一个", "可以", "需要", "如果", "因为",
        "所以", "但是", "然后", "现在", "已经", "还是", "不是", "没有", "什么",
        "怎么", "这个", "那个", "我们", "你们", "他们", "一下", "这样", "那样"
    )

    /** 归一化：去空白与标点、统一小写。用于指纹与相似度，不用于展示。 */
    fun normalize(text: String): String = text.lowercase().replace(PUNCT, "")

    /** 归一化正文的 SHA-256 十六进制指纹。 */
    fun hash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalize(text).toByteArray())
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** 分词：英文/数字/路径按词切，中文按 2-gram 切并过滤停用词。 */
    fun tokenize(text: String): Set<String> {
        val out = mutableSetOf<String>()
        val lower = text.lowercase()
        Regex("[a-z0-9_./-]{2,}").findAll(lower).forEach { out += it.value }
        val cjk = lower.filter { it in '\u4e00'..'\u9fff' }
        for (i in 0 until cjk.length - 1) {
            val bigram = cjk.substring(i, i + 2)
            if (bigram !in STOPWORDS) out += bigram
        }
        return out
    }

    /** Jaccard 相似度，0..1。 */
    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.count { it in b }
        return intersection.toDouble() / (a.size + b.size - intersection)
    }

    /** 取首句作为标题，超长截断。 */
    fun titleOf(text: String, maxChars: Int = 32): String {
        val firstLine = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        val sentence = firstLine.split('。', '！', '？', '.', '!', '?', '\n')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        val base = sentence.ifBlank { firstLine }
        return if (base.length <= maxChars) base else base.take(maxChars) + "…"
    }
}
