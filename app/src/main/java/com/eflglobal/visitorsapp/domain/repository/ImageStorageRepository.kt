package com.eflglobal.visitorsapp.domain.repository

import java.io.File

/**
 * Repositorio para el manejo de almacenamiento de imágenes.
 * Gestiona el guardado, lectura y eliminación de fotos localmente.
 */
interface ImageStorageRepository {

    /**
     * Guarda una imagen de documento (frente o reverso).
     *
     * @param personId ID de la persona
     * @param imageBytes Bytes de la imagen
     * @param isFront true si es el frente, false si es el reverso
     * @return Ruta local donde se guardó la imagen
     */
    suspend fun saveDocumentImage(
        personId: String,
        imageBytes: ByteArray,
        isFront: Boolean
    ): Result<String>

    /**
     * Guarda una foto de perfil.
     *
     * @param personId ID de la persona
     * @param imageBytes Bytes de la imagen
     * @return Ruta local donde se guardó la imagen
     */
    suspend fun saveProfilePhoto(
        personId: String,
        imageBytes: ByteArray
    ): Result<String>

    /**
     * Obtiene un archivo de imagen por su ruta.
     */
    suspend fun getImageFile(imagePath: String): File?

    /**
     * Elimina todas las imágenes de una persona.
     */
    suspend fun deletePersonImages(personId: String): Result<Unit>

    /**
     * Elimina una imagen específica.
     */
    suspend fun deleteImage(imagePath: String): Result<Unit>

    /**
     * Verifica si una imagen existe.
     */
    suspend fun imageExists(imagePath: String): Boolean

    /**
     * Obtiene el directorio base para imágenes de una persona.
     */
    fun getPersonImagesDirectory(personId: String): File
}

