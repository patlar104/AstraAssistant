package dev.patrick.astra.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AstraDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: AstraDatabase? = null

        fun getInstance(context: Context): AstraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AstraDatabase::class.java,
                    "astra.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
