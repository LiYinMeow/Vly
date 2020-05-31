package date.liyin.vly.utils

import android.content.Context
import androidx.room.*
import org.json.JSONArray
import org.json.JSONObject

@Entity
data class ModelSetting(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: String
)

@Entity
data class _3DModelSetting(
    @PrimaryKey(autoGenerate = true) val _3did: Long = 0,
    @ColumnInfo(name = "uid") var uid: Long,
    @ColumnInfo(name = "type") var type: String,
    @ColumnInfo(name = "modelName") var modelName: String,
    @ColumnInfo(name = "useAnimation") var useAnimation: Boolean,
    @ColumnInfo(name = "animationFilter") var animationFilter: Set<String>,
    @ColumnInfo(name = "baseAnimation") var baseAnimation: String,
    @ColumnInfo(name = "animationSpeed") var animationSpeed: Map<String, Long>,
    @ColumnInfo(name = "allowWalkAnimation") var allowWalkAnimation: Boolean,
    @ColumnInfo(name = "walkAnimation") var walkAnimation: String,
    @ColumnInfo(name = "walkActionSpeed") var walkActionSpeed: Double,
    @ColumnInfo(name = "walkAnimationSpeed") var walkAnimationSpeed: Double //One Time
)

@Entity
data class _L2DModelSetting(
    @PrimaryKey(autoGenerate = true) val _l2did: Long = 0,
    @ColumnInfo(name = "uid") var uid: Long,
    @ColumnInfo(name = "modelName") var modelName: String,
    @ColumnInfo(name = "modelJsonName") var modelJsonName: String,
    @ColumnInfo(name = "canvasHeight") var canvasHeight: Int,
    @ColumnInfo(name = "canvasWidth") var canvasWidth: Int,
    @ColumnInfo(name = "x") var x: Float,
    @ColumnInfo(name = "y") var y: Float,
    @ColumnInfo(name = "scale") var scale: Float,
    @ColumnInfo(name = "height") var height: Float
)

@Dao
interface ModelDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertModel(model: ModelSetting): Long

    @Update
    fun updateModel(model: ModelSetting)

    @Delete
    fun deleteModel(model: ModelSetting)

    @Query("SELECT * FROM ModelSetting")
    fun loadAllModel(): Array<ModelSetting?>?
}

@Dao
interface _3DModelDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertModel(model: _3DModelSetting): Long

    @Update
    fun updateModel(model: _3DModelSetting)

    @Delete
    fun deleteModel(model: _3DModelSetting)

    @Query("SELECT * FROM _3DModelSetting")
    fun loadAllModel(): Array<_3DModelSetting?>?

    @Query("SELECT * FROM _3DModelSetting WHERE uid == :uid")
    fun loadAllModelWithUID(uid: Long): Array<_3DModelSetting?>?
}

@Dao
interface _L2DModelDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertModel(model: _L2DModelSetting): Long

    @Update
    fun updateModel(model: _L2DModelSetting)

    @Delete
    fun deleteModel(model: _L2DModelSetting)

    @Query("SELECT * FROM _L2DModelSetting")
    fun loadAllModel(): Array<_L2DModelSetting?>?

    @Query("SELECT * FROM _L2DModelSetting WHERE uid == :uid")
    fun loadAllModelWithUID(uid: Long): Array<_L2DModelSetting?>?
}

class MapConverters {
    @TypeConverter
    fun JSONStringfromMap(value: Map<String, Long>?): String? {
        return JSONObject.wrap(value)?.toString()
    }

    @TypeConverter
    fun JSONStringtoMap(value: String?): Map<String, Long>? {
        return try {
            JSONObject(value!!).let { j ->
                val key = j.keys()
                val map = mutableMapOf<String, Long>()
                key.forEach {
                    map[it] = j.getLong(it)
                }
                map.toMap()
            }
        } catch (e: Exception) {
            null
        }
    }
}

class SetConverters {
    @TypeConverter
    fun JSONStringfromSet(value: Set<String>?): String? {
        return JSONObject.wrap(value)?.toString()
    }

    @TypeConverter
    fun JSONStringtoSet(value: String?): Set<String>? {
        return try {
            val array = JSONArray(value!!)
            (0 until array.length()).flatMap {
                val set = mutableSetOf<String>()
                set.add(array.getString(it))
                set.toList()
            }.toSet()
        } catch (e: Exception) {
            null
        }
    }
}

@Database(
    entities = [ModelSetting::class, _3DModelSetting::class, _L2DModelSetting::class],
    version = 1
)
@TypeConverters(MapConverters::class, SetConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun _3dModelDao(): _3DModelDao
    abstract fun _l2dModelDao(): _L2DModelDao

    companion object {
        private var instance: AppDatabase? = null
        private val sLock = Any()
        fun getInstance(context: Context): AppDatabase {
            synchronized(sLock) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "model.db"
                    ).build()
                }
                return instance!!
            }
        }
    }
}