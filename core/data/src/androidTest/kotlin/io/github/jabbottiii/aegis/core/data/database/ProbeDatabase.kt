package io.github.jabbottiii.aegis.core.data.database

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase

/** A test-only table. `app.db`'s real entities arrive with their Phase 1 requirements. */
@Entity(tableName = "probe")
data class ProbeRow(
    @PrimaryKey val id: Long,
    val label: String,
)

@Dao
interface ProbeDao {
    @Insert
    suspend fun insert(row: ProbeRow)

    @Query("SELECT * FROM probe WHERE id = :id")
    suspend fun find(id: Long): ProbeRow?
}

@Database(entities = [ProbeRow::class], version = 1, exportSchema = false)
abstract class ProbeDatabase : RoomDatabase() {
    abstract fun probeDao(): ProbeDao
}
