package com.eflglobal.visitorsapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eflglobal.visitorsapp.data.local.dao.PersonDao
import com.eflglobal.visitorsapp.data.local.dao.StationDao
import com.eflglobal.visitorsapp.data.local.dao.VisitDao
import com.eflglobal.visitorsapp.data.local.dao.VisitReasonDao
import com.eflglobal.visitorsapp.data.local.entity.PersonEntity
import com.eflglobal.visitorsapp.data.local.entity.StationEntity
import com.eflglobal.visitorsapp.data.local.entity.VisitEntity
import com.eflglobal.visitorsapp.data.local.entity.VisitReasonEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PersonEntity::class,
        VisitEntity::class,
        StationEntity::class,
        VisitReasonEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun visitDao(): VisitDao
    abstract fun stationDao(): StationDao
    abstract fun visitReasonDao(): VisitReasonDao

    companion object {
        private const val DATABASE_NAME = "visitors_app_database"

        /** Standard visit reason seed data (language-neutral keys). */
        val DEFAULT_VISIT_REASONS = listOf(
            VisitReasonEntity("VISITOR",        "Visitante",        "Visitor",        1),
            VisitReasonEntity("DRIVER",         "Conductor",        "Driver",         2),
            VisitReasonEntity("CONTRACTOR",     "Contratista",      "Contractor",     3),
            VisitReasonEntity("TEMPORARY_STAFF","Personal Temporal","Temporary Staff",4),
            VisitReasonEntity("DELIVERY",       "Entrega",          "Delivery",       5),
            VisitReasonEntity("VENDOR",         "Proveedor",        "Vendor",         6),
            VisitReasonEntity("OTHER",          "Otro",             "Other",          7)
        )

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed visit reasons on first creation
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    database.visitReasonDao().insertReasons(DEFAULT_VISIT_REASONS)
                                }
                            }
                        }
                    })
                    .build()

                INSTANCE = instance

                // Also seed if table is empty (handles first-run after fallbackToDestructiveMigration)
                CoroutineScope(Dispatchers.IO).launch {
                    if (instance.visitReasonDao().count() == 0) {
                        instance.visitReasonDao().insertReasons(DEFAULT_VISIT_REASONS)
                    }
                }

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
