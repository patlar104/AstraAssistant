package dev.patrick.astra.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,
    val text: String,
    val timestamp: Long,
    val sessionId: Long
)

object MessageRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
}
