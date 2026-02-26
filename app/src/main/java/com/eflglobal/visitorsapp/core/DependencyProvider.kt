package com.eflglobal.visitorsapp.core

import android.content.Context
import com.eflglobal.visitorsapp.data.local.AppDatabase
import com.eflglobal.visitorsapp.data.local.dao.OcrMetricDao
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

    // All DB access goes through AppDatabase.getInstance() — single source of truth
    @Volatile private var imageStorageRepository: ImageStorageRepository? = null
    @Volatile private var personRepository: PersonRepository? = null
    @Volatile private var stationRepository: StationRepository? = null
    @Volatile private var visitRepository: VisitRepository? = null

    /** Unified database accessor — always uses AppDatabase.getInstance(). */
    fun provideDatabase(context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    /** OcrMetricDao — used by MetricsLogger and DocumentValidator.runOcr(). */
    fun provideOcrMetricDao(context: Context): OcrMetricDao =
        AppDatabase.getInstance(context).ocrMetricDao()

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
     * Limpia las instancias de repositorio (útil para testing).
     * La base de datos se gestiona a través de AppDatabase.getInstance().
     */
    fun clear() {
        imageStorageRepository = null
        personRepository = null
        stationRepository = null
        visitRepository = null
    }
}

