package date.liyin.vly.utils

import android.content.Context
import androidx.room.*
import org.json.JSONArray
import org.json.JSONObject
//ROOM DAO
@Entity
data class ModelSetting( //主存储
    @PrimaryKey(autoGenerate = true) val uid: Long = 0, //模型ID
    @ColumnInfo(name = "name") val name: String, //模型名称
    @ColumnInfo(name = "type") val type: String //类型
)

@Entity
data class _3DModelSetting( //3D
    @PrimaryKey(autoGenerate = true) val _3did: Long = 0,
    @ColumnInfo(name = "uid") var uid: Long, //模型ID
    @ColumnInfo(name = "type") var type: String, //模型类型 GLB GLTF 或者别的什么
    @ColumnInfo(name = "modelName") var modelName: String, //模型名称
    @ColumnInfo(name = "useAnimation") var useAnimation: Boolean, //是否使用动画
    @ColumnInfo(name = "animationFilter") var animationFilter: Set<String>, //动画过滤器（未导出的功能）
    @ColumnInfo(name = "baseAnimation") var baseAnimation: String, //基础动作
    @ColumnInfo(name = "animationSpeed") var animationSpeed: Map<String, Long>, //覆盖动画速度（未导出的功能）
    @ColumnInfo(name = "allowWalkAnimation") var allowWalkAnimation: Boolean, //允许移动动画
    @ColumnInfo(name = "walkAnimation") var walkAnimation: String, //移动动画名
    @ColumnInfo(name = "walkActionSpeed") var walkActionSpeed: Double, //移动动画速度
    @ColumnInfo(name = "walkAnimationSpeed") var walkAnimationSpeed: Double //一次移动的动画速度（不再使用）
)

@Entity
data class _L2DModelSetting( //Live2D
    @PrimaryKey(autoGenerate = true) val _l2did: Long = 0,
    @ColumnInfo(name = "uid") var uid: Long, //模型ID
    @ColumnInfo(name = "modelName") var modelName: String, //模型名称
    @ColumnInfo(name = "modelJsonName") var modelJsonName: String, //模型 JSON 名
    @ColumnInfo(name = "canvasHeight") var canvasHeight: Int, //Canvas 大小，目前不可自定义（未导出的功能）
    @ColumnInfo(name = "canvasWidth") var canvasWidth: Int,
    @ColumnInfo(name = "x") var x: Float, //X，Y 坐标
    @ColumnInfo(name = "y") var y: Float,
    @ColumnInfo(name = "scale") var scale: Float, //缩放
    @ColumnInfo(name = "height") var height: Float //记录模型高度
)

//ROOM DAO 详见 ROOM 使用方法
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

//使用 JSON 转换 Map
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

//使用 JSON 转换 Set
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