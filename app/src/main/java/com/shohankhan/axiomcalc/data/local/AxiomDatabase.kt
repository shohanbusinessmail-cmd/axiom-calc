package com.shohankhan.axiomcalc.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.shohankhan.axiomcalc.domain.model.CalcOrigin
import kotlinx.coroutines.flow.Flow

/**
 * One row of calculation history.
 *
 * The normalized result value is stored separately from the formatted string so
 * that "reuse" restores an exact value without ever parsing display text.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "expression") val expression: String,
    @ColumnInfo(name = "result_value") val resultValue: String,
    @ColumnInfo(name = "result_display") val resultDisplay: String,
    @ColumnInfo(name = "origin") val origin: CalcOrigin,
    @ColumnInfo(name = "timestamp") val timestampMillis: Long,
)

/** A permanently kept, user-named calculation. Never touched by retention. */
@Entity(tableName = "saved_calculations")
data class SavedCalculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "expression") val expression: String,
    @ColumnInfo(name = "result_value") val resultValue: String,
    @ColumnInfo(name = "result_display") val resultDisplay: String,
    @ColumnInfo(name = "origin") val origin: CalcOrigin,
    @ColumnInfo(name = "saved_at") val savedAtMillis: Long,
)

/** Formula Library favorite, keyed by the stable library formula id. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "formula_id") val formulaId: String,
)

/** Recently viewed library formulas, most recent first, capped in the DAO. */
@Entity(tableName = "recent_formulas")
data class RecentFormulaEntity(
    @PrimaryKey @ColumnInfo(name = "formula_id") val formulaId: String,
    @ColumnInfo(name = "accessed_at") val accessedAtMillis: Long,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM history WHERE expression LIKE '%' || :query || '%' " +
            "OR result_display LIKE '%' || :query || '%' ORDER BY timestamp DESC",
    )
    fun observeSearch(query: String): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("DELETE FROM history WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}

@Dao
interface SavedCalculationDao {
    @Query("SELECT * FROM saved_calculations ORDER BY saved_at DESC")
    fun observeAll(): Flow<List<SavedCalculationEntity>>

    @Insert
    suspend fun insert(entity: SavedCalculationEntity): Long

    @Query("DELETE FROM saved_calculations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface FavoritesDao {
    @Query("SELECT formula_id FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Insert
    suspend fun add(entity: FavoriteEntity)

    @Delete
    suspend fun remove(entity: FavoriteEntity)

    @Query("SELECT formula_id FROM recent_formulas ORDER BY accessed_at DESC LIMIT 20")
    fun observeRecent(): Flow<List<String>>

    @Insert
    suspend fun touchRecent(entity: RecentFormulaEntity)

    @Query("DELETE FROM recent_formulas WHERE formula_id = :formulaId")
    suspend fun dropRecent(formulaId: String)

    @Query("DELETE FROM recent_formulas WHERE formula_id NOT IN (SELECT formula_id FROM recent_formulas ORDER BY accessed_at DESC LIMIT 20)")
    suspend fun trimRecent()
}

class Converters {
    @TypeConverter
    fun originToString(value: CalcOrigin): String = value.name

    @TypeConverter
    fun stringToOrigin(value: String): CalcOrigin =
        CalcOrigin.entries.firstOrNull { it.name == value } ?: CalcOrigin.STANDARD
}

/**
 * The single Room database. Schema version 1 is exported to `app/schemas/` so
 * every future migration is reviewable and testable; the schema is never
 * dropped to avoid writing migrations.
 */
@Database(
    entities = [
        HistoryEntity::class,
        SavedCalculationEntity::class,
        FavoriteEntity::class,
        RecentFormulaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AxiomDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun savedDao(): SavedCalculationDao
    abstract fun favoritesDao(): FavoritesDao

    companion object {
        const val NAME = "axiom-calc.db"
    }
}
