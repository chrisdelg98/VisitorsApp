package com.eflglobal.visitorsapp.core

import android.content.Context
import androidx.room.Room
import com.eflglobal.visitorsapp.data.local.AppDatabase
import com.eflglobal.visitorsapp.data.repository.ImageStorageRepositoryImpl
import com.eflglobal.visitorsapp.data.repository.PersonRepositoryImpl
import com.eflglobal.visitorsapp.data.repository.StationRepositoryImpl
import com.eflglobal.visitorsapp.data.repository.VisitRepositoryImpl
import com.eflglobal.visitorsapp.domain.repository.ImageStorageRepository
import com.eflglobal.visitorsapp.domain.repository.PersonRepository
import com.eflglobal.visitorsapp.domain.repository.StationRepository
import com.eflglobal.visitorsapp.domain.repository.VisitRepository

/**
 * Proveedor simple de dependencias sin usar Hilt.
 *
 * Proporciona instancias singleton de todos los repositorios y servicios
 * necesarios para la aplicación.
 */
object DependencyProvider {

    @Volatile
    private var database: AppDatabase? = null

    @Volatile
    private var imageStorageRepository: ImageStorageRepository? = null

    @Volatile
    private var personRepository: PersonRepository? = null

    @Volatile
    private var stationRepository: StationRepository? = null

    @Volatile
    private var visitRepository: VisitRepository? = null

    /**
     * Inicializa el proveedor de dependencias.
     * Debe llamarse una sola vez al inicio de la aplicación.
     */
    fun initialize(context: Context) {
        if (database == null) {
            synchronized(this) {
                if (database == null) {
                    database = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "visitors_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                }
            }
        }
    }

    /**
     * Proporciona la instancia de la base de datos.
     */
    fun provideDatabase(context: Context): AppDatabase {
        if (database == null) {
            initialize(context)
        }
        return database!!
    }

    /**
     * Proporciona el repositorio de almacenamiento de imágenes.
     */
    fun provideImageStorageRepository(context: Context): ImageStorageRepository {
        if (imageStorageRepository == null) {
            synchronized(this) {
                if (imageStorageRepository == null) {
                    imageStorageRepository = ImageStorageRepositoryImpl(context.applicationContext)
                }
            }
        }
        return imageStorageRepository!!
    }

    /**
     * Proporciona el repositorio de personas.
     */
    fun providePersonRepository(context: Context): PersonRepository {
        if (personRepository == null) {
            synchronized(this) {
                if (personRepository == null) {
                    val db = provideDatabase(context)
                    personRepository = PersonRepositoryImpl(db.personDao())
                }
            }
        }
        return personRepository!!
    }

    /**
     * Proporciona el repositorio de estaciones.
     */
    fun provideStationRepository(context: Context): StationRepository {
        if (stationRepository == null) {
            synchronized(this) {
                if (stationRepository == null) {
                    val db = provideDatabase(context)
                    stationRepository = StationRepositoryImpl(db.stationDao())
                }
            }
        }
        return stationRepository!!
    }

    /**
     * Proporciona el repositorio de visitas.
     */
    fun provideVisitRepository(context: Context): VisitRepository {
        if (visitRepository == null) {
            synchronized(this) {
                if (visitRepository == null) {
                    val db = provideDatabase(context)
                    visitRepository = VisitRepositoryImpl(db.visitDao())
                }
            }
        }
        return visitRepository!!
    }

    /**
     * Limpia todas las instancias (útil para testing).
     */
    fun clear() {
        database?.close()
        database = null
        imageStorageRepository = null
        personRepository = null
        stationRepository = null
        visitRepository = null
    }
}

