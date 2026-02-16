package com.eflglobal.visitorsapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.eflglobal.visitorsapp.data.local.dao.PersonDao
import com.eflglobal.visitorsapp.data.local.dao.StationDao
import com.eflglobal.visitorsapp.data.local.dao.VisitDao
import com.eflglobal.visitorsapp.data.local.entity.PersonEntity
import com.eflglobal.visitorsapp.data.local.entity.StationEntity
import com.eflglobal.visitorsapp.data.local.entity.VisitEntity

@Database(
    entities = [
        PersonEntity::class,
        VisitEntity::class,
        StationEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun visitDao(): VisitDao
    abstract fun stationDao(): StationDao

    companion object {
        private const val DATABASE_NAME = "visitors_app_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // Solo para desarrollo, eliminar en producción
                    .build()

                INSTANCE = instance
                instance
            }
        }

        // Método para testing
        fun getInMemoryDatabase(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}

