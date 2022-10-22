package workflow.onech.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DB")
class Entity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val user: String,
    val date: String,
    val text: String?,
    val image: String?,
    )