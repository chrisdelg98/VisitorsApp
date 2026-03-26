package com.eflglobal.visitorsapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eflglobal.visitorsapp.data.local.dao.OcrMetricDao
import com.eflglobal.visitorsapp.data.local.dao.PersonDao
import com.eflglobal.visitorsapp.data.local.dao.StationDao
import com.eflglobal.visitorsapp.data.local.dao.VisitDao
import com.eflglobal.visitorsapp.data.local.dao.VisitReasonDao
import com.eflglobal.visitorsapp.data.local.entity.OcrMetricEntity
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
        VisitReasonEntity::class,
        OcrMetricEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun visitDao(): VisitDao
    abstract fun stationDao(): StationDao
    abstract fun visitReasonDao(): VisitReasonDao
    abstract fun ocrMetricDao(): OcrMetricDao

    companion object {
        private const val DATABASE_NAME = "visitors_app_database"

        /** Standard visit reason seed data — real visit purposes. */
        val DEFAULT_VISIT_REASONS = listOf(
            VisitReasonEntity("MEETING",           "Reunión",          "Meeting",           1),
            VisitReasonEntity("INTERVIEW",         "Entrevista",       "Interview",         2),
            VisitReasonEntity("DELIVERY",          "Entrega",          "Delivery",          3),
            VisitReasonEntity("PICKUP",            "Recolecta",        "Pickup",            4),
            VisitReasonEntity("MAINTENANCE",       "Mantenimiento",    "Maintenance",       5),
            VisitReasonEntity("TRAINING",          "Capacitación",     "Training",          6),
            VisitReasonEntity("AUDIT",             "Auditoría",        "Audit",             7),
            VisitReasonEntity("TECHNICAL_SERVICE", "Servicio Técnico", "Technical Service", 8),
            VisitReasonEntity("ONSITE_WORK",       "Trabajo en Sitio", "On-Site Work",      9),
            VisitReasonEntity("OTHER",             "Otro",             "Other",            10)
        )

        /**
         * Migration 4 → 5: adds per-visit photo snapshot columns to the visits table.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE visits ADD COLUMN visitProfilePhotoPath TEXT")
                db.execSQL("ALTER TABLE visits ADD COLUMN visitDocumentFrontPath TEXT")
                db.execSQL("ALTER TABLE visits ADD COLUMN visitDocumentBackPath TEXT")
            }
        }

        /**
         * Migration 5 → 6: replaces visit_reasons seed data with real visit purposes.
         * Old reason keys in existing visits are preserved (display falls back gracefully).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove old person-type reasons
                db.execSQL("DELETE FROM visit_reasons")
                // Insert new purpose-based reasons
                db.execSQL("INSERT OR IGNORE INTO visit_reasons (reasonKey, labelEs, labelEn, sortOrder, isActive) VALUES ('MEETING',           'Reunión',          'Meeting',            1, 1)")
                db.execSQL("INSERT OR IGNORE INTO visit_reasons (reasonKey, labelEs, labelEn, sortOrder, isActive) VALUES ('INTERVIEW',         'Entrevista',       'Interview',          2, 1)")
                db.execSQL("INSERT OR IGNORE INTO visit_reasons (reasonKey, labelEs, labelEn, sortOrder, isActive) VALUES ('DELIVERY',          'Entrega',          'Delivery',           3, 1)")
                db.execSQL("INSERT OR IGNORE INTO visit_reasons (reasonKey, labelEs, labelEn, sortOrder, isActive) VALUES ('PICKUP',            'Recolecta',        'Pickup',             4, 1)")
                db.execSQL("INSERT OR IGNORE INTO visit_reasons (reasonKey, labelEs, labelEn, sortOrder, isActive) VALUES ('MAINTENANCE',       'Mantenimiento',    'Maintenance',        5, 1)")
                db.execSQL("INSERT OR IGNORE INTO visit_reasons (reasonKey, labelEs, labelEn, sortOrder, isActive) VALUES ('TRAINING',          'Capacitación',     'Training',           6, 1)")
                db.execSQL("INSERT OR IGNORE INTO visit_reasons (reasonKey, labelEs, labelEn, sortOrder, isActive) VALUES ('AUDIT',             'Auditoría',        'Audit',              7, 1)")
                db.execSQL("INSERT OR IGNORE INTO visit_reasons (reasonKey, labelEs, labelEn, sortOrder, isActive) VALUES ('TECHNICAL_SERVICE', 'Servicio Técnico', 'Technical Service',  8, 1)")
                db.execSQL("INSERT OR IGNORE INTO visit_reasons (reasonKey, labelEs, labelEn, sortOrder, isActive) VALUES ('ONSITE_WORK',      'Trabajo en Sitio', 'On-Site Work',       9, 1)")
                db.execSQL("INSERT OR IGNORE INTO visit_reasons (reasonKey, labelEs, labelEn, sortOrder, isActive) VALUES ('OTHER',             'Otro',             'Other',             10, 1)")
            }
        }

        /**
         * Migration 6 → 7: adds Continue Visit tracking columns to visits table.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE visits ADD COLUMN reentryCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE visits ADD COLUMN lastReentryAt INTEGER")
                db.execSQL("ALTER TABLE visits ADD COLUMN originalVisitId TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
