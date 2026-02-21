package com.eflglobal.visitorsapp.core.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Utilidad para reconocimiento de texto (OCR) usando ML Kit.
 *
 * Extrae información de documentos de identidad:
 * - Nombre completo
 * - Número de documento
 * - Fecha de nacimiento (opcional)
 *
 * TODO: Mejorar patrones de detección para diferentes tipos de documentos
 */
object OCRHelper {

    /**
     * Resultado del reconocimiento de texto.
     */
    data class OCRResult(
        val fullText: String,
        val detectedName: String? = null,
        val detectedDocumentNumber: String? = null,
        val confidence: Float = 0f
    )

    /**
     * Procesa una imagen y extrae texto usando ML Kit.
     *
     * @param bitmap Imagen del documento
     * @return OCRResult con el texto extraído
     */
    suspend fun processDocument(bitmap: Bitmap): OCRResult {
        return suspendCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text

                    // Intentar extraer nombre y número de documento
                    val name = extractName(fullText)
                    val docNumber = extractDocumentNumber(fullText)

                    val result = OCRResult(
                        fullText = fullText,
                        detectedName = name,
                        detectedDocumentNumber = docNumber,
                        confidence = calculateConfidence(visionText)
                    )

                    continuation.resume(result)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }

    /**
     * Intenta extraer el nombre del texto detectado.
     *
     * Busca patrones comunes en documentos de identidad.
     */
    private fun extractName(text: String): String? {
        // TODO: Implementar lógica más robusta
        // Patrones comunes:
        // - "NOMBRE:" o "NAME:"
        // - Línea después de "APELLIDOS"
        // - Nombres propios (mayúsculas)

        val lines = text.split("\n")

        // Buscar línea con "NOMBRE" o "NAME"
        val nameKeywords = listOf("nombre", "name", "apellido", "surname")
        for (i in lines.indices) {
            val line = lines[i].lowercase()
            if (nameKeywords.any { line.contains(it) }) {
                // Retornar la siguiente línea si existe
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (nextLine.length > 3 && nextLine.all { it.isLetter() || it.isWhitespace() }) {
                        return nextLine
                    }
                }
            }
        }

        // Si no se encuentra, buscar primera línea con solo letras mayúsculas
        for (line in lines) {
            val cleaned = line.trim()
            if (cleaned.length > 5 && cleaned.all { it.isUpperCase() || it.isWhitespace() }) {
                return cleaned
            }
        }

        return null
    }

    /**
     * Intenta extraer el número de documento del texto detectado.
     *
     * Busca patrones comunes:
     * - DUI: 12345678-9
     * - Pasaporte: PA123456
     */
    private fun extractDocumentNumber(text: String): String? {
        // Patrón para DUI salvadoreño: 8 dígitos - 1 dígito
        val duiPattern = Regex("""\d{8}-\d""")
        val duiMatch = duiPattern.find(text)
        if (duiMatch != null) {
            return duiMatch.value
        }

        // Patrón para pasaporte: PA + 6 dígitos
        val passportPattern = Regex("""PA\d{6}""")
        val passportMatch = passportPattern.find(text)
        if (passportMatch != null) {
            return passportMatch.value
        }

        // Buscar cualquier secuencia de números
        val numbersPattern = Regex("""\d{6,}""")
        val numberMatch = numbersPattern.find(text)
        if (numberMatch != null) {
            return numberMatch.value
        }

        return null
    }

    /**
     * Calcula un nivel de confianza basado en la cantidad de texto detectado.
     */
    private fun calculateConfidence(visionText: com.google.mlkit.vision.text.Text): Float {
        val blockCount = visionText.textBlocks.size
        val lineCount = visionText.textBlocks.sumOf { it.lines.size }

        return when {
            lineCount >= 10 -> 0.9f
            lineCount >= 5 -> 0.7f
            lineCount >= 3 -> 0.5f
            else -> 0.3f
        }
    }

    /**
     * Valida si un documento tiene suficiente texto legible.
     *
     * @param bitmap Imagen del documento
     * @return true si el documento es legible
     */
    suspend fun validateDocumentQuality(bitmap: Bitmap): Boolean {
        return try {
            val result = processDocument(bitmap)
            result.fullText.length > 20 && result.confidence > 0.5f
        } catch (e: Exception) {
            false
        }
    }
}

