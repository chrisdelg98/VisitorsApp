package com.eflglobal.visitorsapp.core.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.sqrt

/**
 * Utilidad para reconocimiento de texto (OCR) usando ML Kit.
 *
 * Extrae información de documentos de identidad:
 * - Nombre completo
 * - Número de documento
 * - Fecha de nacimiento (opcional)
 */
object OCRHelper {

    /**
     * Resultado del reconocimiento de texto.
     */
    data class OCRResult(
        val fullText: String,
        val detectedName: String? = null,
        val detectedDocumentNumber: String? = null,
        val confidence: Float = 0f,
        val sharpness: Float = 0f,
        val isSharp: Boolean = false
    )

    /**
     * Calcula la nitidez de una imagen usando el método de varianza de Laplaciano.
     * Una imagen nítida tiene mayor varianza en los gradientes.
     *
     * @param bitmap Imagen a analizar
     * @return Valor de nitidez (mayor = más nítida). Típicamente > 100 es nítida
     */
    fun calculateSharpness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convertir a escala de grises y aplicar Laplaciano
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x

                // Convertir a gris
                val pixel = pixels[idx]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                // Calcular Laplaciano (aproximado con kernel simple)
                val top = pixels[(y - 1) * width + x]
                val bottom = pixels[(y + 1) * width + x]
                val left = pixels[y * width + (x - 1)]
                val right = pixels[y * width + (x + 1)]

                val topG = ((top shr 16) and 0xFF) * 0.299 + ((top shr 8) and 0xFF) * 0.587 + (top and 0xFF) * 0.114
                val bottomG = ((bottom shr 16) and 0xFF) * 0.299 + ((bottom shr 8) and 0xFF) * 0.587 + (bottom and 0xFF) * 0.114
                val leftG = ((left shr 16) and 0xFF) * 0.299 + ((left shr 8) and 0xFF) * 0.587 + (left and 0xFF) * 0.114
                val rightG = ((right shr 16) and 0xFF) * 0.299 + ((right shr 8) and 0xFF) * 0.587 + (right and 0xFF) * 0.114

                val laplacian = kotlin.math.abs(4 * gray - topG - bottomG - leftG - rightG)

                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }

        // Calcular varianza
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)

        return sqrt(variance).toFloat()
    }

    /**
     * Procesa una imagen y extrae texto usando ML Kit.
     *
     * @param bitmap Imagen del documento
     * @return OCRResult con el texto extraído
     */
    suspend fun processDocument(bitmap: Bitmap): OCRResult {
        // Calcular nitidez de la imagen
        val sharpness = calculateSharpness(bitmap)
        val isSharp = sharpness > 30.0f // Umbral MUY tolerante - aceptar casi todas las imágenes

        return suspendCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text

                    // Intentar extraer nombre y número de documento
                    val name = extractName(fullText, visionText)
                    val docNumber = extractDocumentNumber(fullText)

                    val result = OCRResult(
                        fullText = fullText,
                        detectedName = name,
                        detectedDocumentNumber = docNumber,
                        confidence = calculateConfidence(visionText),
                        sharpness = sharpness,
                        isSharp = isSharp
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
     * Versión mejorada con múltiples estrategias y soporte internacional.
     */
    private fun extractName(text: String, visionText: com.google.mlkit.vision.text.Text): String? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        // Estrategia 1: Buscar combinando nombre y apellido con palabras clave
        val firstNameKeywords = listOf(
            "nombres", "nombre", "forename", "given name", "given names",
            "first name", "primer nombre", "prenom"
        )

        val lastNameKeywords = listOf(
            "apellidos", "apellido", "surname", "last name",
            "family name", "nom", "nom de famille"
        )

        var firstName: String? = null
        var lastName: String? = null

        // Buscar nombres y apellidos por separado
        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()

            // Buscar nombre
            if (firstName == null && firstNameKeywords.any { lineLower.contains(it) }) {
                // Buscar en las siguientes 3 líneas
                for (j in 1..3) {
                    if (i + j < lines.size) {
                        val candidate = lines[i + j].trim()
                        if (isValidName(candidate)) {
                            firstName = candidate
                            break
                        }
                    }
                }
            }

            // Buscar apellido
            if (lastName == null && lastNameKeywords.any { lineLower.contains(it) }) {
                // Buscar en las siguientes 3 líneas
                for (j in 1..3) {
                    if (i + j < lines.size) {
                        val candidate = lines[i + j].trim()
                        if (isValidName(candidate)) {
                            lastName = candidate
                            break
                        }
                    }
                }
            }
        }

        // Si encontramos ambos, combinarlos
        if (firstName != null && lastName != null) {
            return cleanName("$firstName $lastName")
        }

        // Si solo encontramos uno, usarlo
        if (firstName != null) return cleanName(firstName)
        if (lastName != null) return cleanName(lastName)

        // Estrategia 2: Buscar después de cualquier palabra clave de nombre
        val allNameKeywords = firstNameKeywords + lastNameKeywords
        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            if (allNameKeywords.any { lineLower.contains(it) }) {
                // Buscar en las siguientes 3 líneas
                for (j in 1..3) {
                    if (i + j < lines.size) {
                        val candidate = lines[i + j].trim()
                        if (isValidName(candidate)) {
                            return cleanName(candidate)
                        }
                    }
                }
            }
        }

        // Estrategia 3: Buscar líneas que parezcan nombres (solo letras mayúsculas, > 6 caracteres)
        for (line in lines) {
            if (isValidName(line) && line.length >= 6) {
                return cleanName(line)
            }
        }

        // Estrategia 4: Buscar en bloques de texto con alta confianza
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val textLine = line.text.trim()
                if (isValidName(textLine) && textLine.length >= 6) {
                    return cleanName(textLine)
                }
            }
        }

        return null
    }

    /**
     * Valida si un texto parece ser un nombre válido.
     * Versión más permisiva.
     */
    private fun isValidName(text: String): Boolean {
        if (text.length < 2) return false

        // Debe contener mayormente letras (más permisivo)
        val letterCount = text.count { it.isLetter() }
        val totalChars = text.length

        if (letterCount < totalChars * 0.5) return false // Reducido de 0.7 a 0.5

        // No debe contener muchos números (más permisivo)
        val digitCount = text.count { it.isDigit() }
        if (digitCount > 3) return false // Aumentado de 2 a 3

        // No requiere mayúscula obligatoria - aceptar cualquier capitalización

        // No debe contener caracteres especiales extraños
        val validChars = text.all {
            it.isLetter() || it.isWhitespace() || it == '.' || it == '\'' || it == '-' || it == ','
        }

        return validChars
    }

    /**
     * Limpia un nombre extraído de caracteres innecesarios.
     */
    private fun cleanName(name: String): String {
        return name.trim()
            .replace(Regex("\\s+"), " ") // Normalizar espacios
            .split(" ")
            .joinToString(" ") { word ->
                // Capitalizar cada palabra
                if (word.isNotEmpty()) {
                    word.lowercase().replaceFirstChar { it.uppercase() }
                } else word
            }
    }

    /**
     * Intenta extraer el número de documento del texto detectado.
     * Versión mejorada con múltiples patrones internacionales.
     */
    private fun extractDocumentNumber(text: String): String? {
        // Patrón 1: DUI salvadoreño: 12345678-9
        val duiPattern = Regex("""\b\d{8}-\d\b""")
        duiPattern.find(text)?.let { return it.value }

        // Patrón 2: DUI sin guión: 123456789
        val duiNoHyphenPattern = Regex("""\b\d{9}\b""")
        duiNoHyphenPattern.find(text)?.let {
            val num = it.value
            return "${num.substring(0, 8)}-${num[8]}"
        }

        // Patrón 3: Pasaporte con letras: PA123456, A1234567, etc.
        val passportPattern = Regex("""\b[A-Z]{1,2}\d{6,8}\b""")
        passportPattern.find(text)?.let { return it.value }

        // Patrón 4: Número de identificación genérico: 6-15 dígitos
        val genericIdPattern = Regex("""\b\d{6,15}\b""")
        genericIdPattern.find(text)?.let { return it.value }

        // Patrón 5: Buscar después de palabras clave más completas
        val docKeywords = listOf(
            "dui", "id", "identification", "documento", "number", "no.", "nº", "num",
            "numero unico de identidad", "numero de identificacion", "id number",
            "document number", "numero unico", "unique id", "identity number",
            "cedula", "dni", "passport", "pasaporte"
        )

        val lines = text.split("\n")

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            // Verificar si la línea contiene alguna palabra clave
            if (docKeywords.any { keyword -> lineLower.contains(keyword) }) {
                // Buscar número en la misma línea
                val sameLineMatch = Regex("""[\d-]{6,}""").find(lines[i])
                if (sameLineMatch != null) {
                    return sameLineMatch.value.replace(Regex("""[^\d-]"""), "")
                }

                // Buscar número en las siguientes 3 líneas
                for (j in 1..3) {
                    if (i + j < lines.size) {
                        val numMatch = Regex("""[\d-]{6,}""").find(lines[i + j])
                        if (numMatch != null) {
                            return numMatch.value.replace(Regex("""[^\d-]"""), "")
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Calcula un nivel de confianza basado en la cantidad de texto detectado.
     */
    private fun calculateConfidence(visionText: com.google.mlkit.vision.text.Text): Float {
        val blockCount = visionText.textBlocks.size
        val lineCount = visionText.textBlocks.sumOf { it.lines.size }
        val totalChars = visionText.text.length

        return when {
            totalChars > 100 && lineCount >= 10 -> 0.95f
            totalChars > 50 && lineCount >= 7 -> 0.85f
            totalChars > 30 && lineCount >= 5 -> 0.70f
            totalChars > 15 && lineCount >= 3 -> 0.55f
            totalChars > 8 -> 0.35f
            else -> 0.20f
        }
    }

    /**
     * Valida si un documento tiene suficiente texto legible y está nítido.
     *
     * @param bitmap Imagen del documento
     * @return true si el documento es legible y nítido
     */
    suspend fun validateDocumentQuality(bitmap: Bitmap): Boolean {
        return try {
            val result = processDocument(bitmap)
            result.fullText.length > 10 &&
            result.confidence > 0.3f &&
            result.isSharp
        } catch (e: Exception) {
            false
        }
    }
}

