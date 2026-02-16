package com.eflglobal.visitorsapp.data.repository

import android.content.Context
import com.eflglobal.visitorsapp.domain.repository.ImageStorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementación del repositorio de almacenamiento de imágenes.
 *
 * Maneja el guardado y gestión de imágenes en el almacenamiento interno
 * de la aplicación. Organiza las imágenes por persona en carpetas dedicadas.
 *
 * Estructura de carpetas:
 * /visitors/{personId}/document_front.jpg
 * /visitors/{personId}/document_back.jpg
 * /visitors/{personId}/profile.jpg
 */
class ImageStorageRepositoryImpl(
    private val context: Context
) : ImageStorageRepository {

    companion object {
        private const val BASE_DIRECTORY = "visitors"
        private const val DOCUMENT_FRONT_FILENAME = "document_front.jpg"
        private const val DOCUMENT_BACK_FILENAME = "document_back.jpg"
        private const val PROFILE_PHOTO_FILENAME = "profile.jpg"
    }

    /**
     * Obtiene el directorio base donde se almacenan todas las imágenes.
     */
    private fun getBaseDirectory(): File {
        val baseDir = File(context.filesDir, BASE_DIRECTORY)
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    override fun getPersonImagesDirectory(personId: String): File {
        val personDir = File(getBaseDirectory(), personId)
        if (!personDir.exists()) {
            personDir.mkdirs()
        }
        return personDir
    }

    override suspend fun saveDocumentImage(
        personId: String,
        imageBytes: ByteArray,
        isFront: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val personDir = getPersonImagesDirectory(personId)
            val filename = if (isFront) DOCUMENT_FRONT_FILENAME else DOCUMENT_BACK_FILENAME
            val imageFile = File(personDir, filename)

            // Guardar los bytes en el archivo
            imageFile.writeBytes(imageBytes)

            // Retornar la ruta relativa para guardar en la base de datos
            val relativePath = "${BASE_DIRECTORY}/${personId}/${filename}"
            Result.success(relativePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveProfilePhoto(
        personId: String,
        imageBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val personDir = getPersonImagesDirectory(personId)
            val imageFile = File(personDir, PROFILE_PHOTO_FILENAME)

            // Guardar los bytes en el archivo
            imageFile.writeBytes(imageBytes)

            // Retornar la ruta relativa
            val relativePath = "${BASE_DIRECTORY}/${personId}/${PROFILE_PHOTO_FILENAME}"
            Result.success(relativePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getImageFile(imagePath: String): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, imagePath)
            if (file.exists()) file else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deletePersonImages(personId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val personDir = getPersonImagesDirectory(personId)
            if (personDir.exists()) {
                personDir.deleteRecursively()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteImage(imagePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, imagePath)
            if (file.exists()) {
                file.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun imageExists(imagePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, imagePath)
            file.exists()
        } catch (e: Exception) {
            false
        }
    }
}

