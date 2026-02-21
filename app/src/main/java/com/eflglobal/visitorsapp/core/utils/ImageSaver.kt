package com.eflglobal.visitorsapp.core.utils

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Utilidad para guardar imágenes en el almacenamiento interno.
 *
 * Organiza las imágenes por persona en carpetas:
 * /storage/internal/visitors/{personId}/
 *   - document_front.jpg
 *   - document_back.jpg
 *   - profile.jpg
 */
object ImageSaver {

    /**
     * Tipo de imagen del visitante.
     */
    enum class ImageType(val filename: String) {
        DOCUMENT_FRONT("document_front.jpg"),
        DOCUMENT_BACK("document_back.jpg"),
        PROFILE("profile.jpg"),
        QR_CODE("qr_code.png")
    }

    /**
     * Guarda una imagen en el almacenamiento interno.
     *
     * @param context Contexto de la aplicación
     * @param bitmap Imagen a guardar
     * @param personId ID de la persona (UUID)
     * @param imageType Tipo de imagen
     * @return Path completo del archivo guardado
     */
    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        personId: String,
        imageType: ImageType
    ): String {
        // Crear directorio para la persona si no existe
        val personDir = File(context.filesDir, "visitors/$personId")
        if (!personDir.exists()) {
            personDir.mkdirs()
        }

        // Crear archivo
        val imageFile = File(personDir, imageType.filename)

        // Guardar bitmap
        FileOutputStream(imageFile).use { outputStream ->
            val format = if (imageType == ImageType.QR_CODE) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            val quality = if (imageType == ImageType.QR_CODE) 100 else 90

            bitmap.compress(format, quality, outputStream)
            outputStream.flush()
        }

        return imageFile.absolutePath
    }

    /**
     * Guarda una imagen para una visita (como QR).
     *
     * @param context Contexto de la aplicación
     * @param bitmap Imagen a guardar
     * @param visitId ID de la visita
     * @return Path completo del archivo guardado
     */
    fun saveVisitImage(
        context: Context,
        bitmap: Bitmap,
        visitId: String
    ): String {
        // Crear directorio para visitas si no existe
        val visitsDir = File(context.filesDir, "visits")
        if (!visitsDir.exists()) {
            visitsDir.mkdirs()
        }

        // Crear archivo
        val imageFile = File(visitsDir, "$visitId.png")

        // Guardar bitmap
        FileOutputStream(imageFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        }

        return imageFile.absolutePath
    }

    /**
     * Obtiene el path de una imagen guardada.
     *
     * @param context Contexto de la aplicación
     * @param personId ID de la persona
     * @param imageType Tipo de imagen
     * @return Path del archivo o null si no existe
     */
    fun getImagePath(
        context: Context,
        personId: String,
        imageType: ImageType
    ): String? {
        val imageFile = File(context.filesDir, "visitors/$personId/${imageType.filename}")
        return if (imageFile.exists()) {
            imageFile.absolutePath
        } else {
            null
        }
    }

    /**
     * Elimina todas las imágenes de una persona.
     *
     * @param context Contexto de la aplicación
     * @param personId ID de la persona
     * @return true si se eliminaron correctamente
     */
    fun deletePersonImages(
        context: Context,
        personId: String
    ): Boolean {
        val personDir = File(context.filesDir, "visitors/$personId")
        return if (personDir.exists()) {
            personDir.deleteRecursively()
        } else {
            false
        }
    }

    /**
     * Obtiene el tamaño total de almacenamiento usado por las imágenes.
     *
     * @param context Contexto de la aplicación
     * @return Tamaño en bytes
     */
    fun getTotalStorageSize(context: Context): Long {
        val visitorsDir = File(context.filesDir, "visitors")
        val visitsDir = File(context.filesDir, "visits")

        var totalSize = 0L

        if (visitorsDir.exists()) {
            totalSize += visitorsDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        }

        if (visitsDir.exists()) {
            totalSize += visitsDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        }

        return totalSize
    }
}

