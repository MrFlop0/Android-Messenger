package workflow.onech.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Entity::class], version = 1)
abstract class DB : RoomDatabase() {
    abstract fun messagesDAO(): DAO

    companion object {

        @Volatile
        private var INSTANCE: DB? = null

        fun getDatabase(context: Context): DB {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = buildDatabase(context)
                }
            }
            return INSTANCE!!
        }

        private fun buildDatabase(context: Context): DB {
            return Room.databaseBuilder(
                context.applicationContext,
                DB::class.java,
                "DB"
            ).build()
        }
    }
}