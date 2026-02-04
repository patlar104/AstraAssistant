package dev.patrick.astra.data

import dev.patrick.astra.assistant.AstraMessage
import dev.patrick.astra.brains.memory.ConversationMemory

class ConversationRepository(
    private val dao: ConversationDao
) {
    suspend fun loadRecent(sessionId: Long, limit: Int): List<AstraMessage> {
        val entities = dao.listRecentForSession(sessionId, limit)
        return entities.asReversed().map { entity ->
            AstraMessage(
                fromUser = entity.role == MessageRole.USER,
                text = entity.text,
                timestamp = entity.timestamp
            )
        }
    }

    suspend fun insertMessage(message: AstraMessage, sessionId: Long) {
        dao.insert(message.toEntity(sessionId))
    }

    suspend fun insertMessages(messages: List<AstraMessage>, sessionId: Long) {
        dao.insertAll(messages.map { it.toEntity(sessionId) })
    }

    suspend fun clear() {
        dao.clear()
    }

    fun buildMemory(messages: List<AstraMessage>): ConversationMemory {
        val memory = ConversationMemory()
        messages.forEach { message ->
            if (message.fromUser) {
                memory.addUserMessage(message.text)
            } else {
                memory.addAssistantReply(message.text)
            }
        }
        return memory
    }

    private fun AstraMessage.toEntity(sessionId: Long): MessageEntity {
        return MessageEntity(
            role = if (fromUser) MessageRole.USER else MessageRole.ASSISTANT,
            text = text,
            timestamp = timestamp,
            sessionId = sessionId
        )
    }
}
