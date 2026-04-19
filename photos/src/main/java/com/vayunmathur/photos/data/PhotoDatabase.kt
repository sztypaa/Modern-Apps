package com.vayunmathur.photos.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.vayunmathur.library.util.TrueDao

@Dao
interface PhotoDao: TrueDao<Photo> {
    @Query("DELETE FROM Photo WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Database(entities = [Photo::class], version = 4)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
}

val MIGRATION_1_2 = Migration(1, 2) {
    it.execSQL("CREATE INDEX IF NOT EXISTS `index_Photo_date` ON `Photo` (`date`)")
}

val MIGRATION_2_3 = Migration(2, 3) {
    it.execSQL("ALTER TABLE Photo ADD COLUMN dateModified INTEGER NOT NULL DEFAULT 0")
}

val MIGRATION_3_4 = Migration(3, 4) {
    it.execSQL("ALTER TABLE Photo ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
}
