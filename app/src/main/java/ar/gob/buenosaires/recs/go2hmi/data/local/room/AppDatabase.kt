package ar.gob.buenosaires.recs.go2hmi.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local Room (`recs_go2_hmi.db`) para operación 100% offline en campo.
 *
 * Exporta su esquema JSON (`app/schemas/.../1.json`) para trazabilidad y futuras migraciones.
 * Se instancia como Singleton único a través del contenedor de la aplicación (`AppContainer`).
 */
@Database(
    entities = [HistoryEventEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyEventDao(): HistoryEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recs_go2_hmi.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
