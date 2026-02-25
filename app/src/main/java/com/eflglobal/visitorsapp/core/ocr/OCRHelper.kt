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
     * Resultado de la verificación de duplicado por OCR.
     */
    data class DuplicateCheckResult(
        val isDuplicate: Boolean,
        /** Similarity score 0.0–1.0: higher means more similar text content */
        val similarity: Float,
        val reason: String
    )

    /**
     * Compares two document images using ML Kit OCR text similarity to determine
     * if they are the same side of a document.
     *
     * Strategy:
     *  1. Extract full OCR text from both images.
     *  2. Tokenize each text into a set of meaningful words (≥ 3 chars, letters/digits only).
     *  3. Compute Jaccard similarity = |intersection| / |union|.
     *  4. If similarity >= [threshold], the images are considered duplicates.
     *
     * A threshold of 0.55 works well in practice:
     *  - Front vs front of the SAME card → similarity ~0.80–1.0  → DUPLICATE
     *  - Front vs back of the SAME card  → similarity ~0.10–0.35 → NOT duplicate
     *
     * If either image yields very little OCR text (< 6 tokens), we fall back to
     * [fallbackResult] so the caller can decide (default = not a duplicate, to
     * avoid blocking the user when OCR has no data to compare).
     */
    suspend fun isSameSideByOCR(
        referenceBitmap: Bitmap,
        candidateBitmap: Bitmap,
        threshold: Float = 0.55f,
        fallbackResult: Boolean = false
    ): DuplicateCheckResult {
        return try {
            val refText  = extractRawText(referenceBitmap)
            val canText  = extractRawText(candidateBitmap)

            println("🔤 OCR duplicate check — ref tokens: ${tokenize(refText).size}, cand tokens: ${tokenize(canText).size}")

            val refTokens  = tokenize(refText)
            val candTokens = tokenize(canText)

            // Not enough text in either image → cannot reliably compare
            if (refTokens.size < 6 || candTokens.size < 6) {
                println("🔤 OCR duplicate → insufficient tokens, fallback=$fallbackResult")
                return DuplicateCheckResult(
                    isDuplicate = fallbackResult,
                    similarity  = 0f,
                    reason      = "insufficient_text"
                )
            }

            val intersection = refTokens.intersect(candTokens).size.toFloat()
            val union        = refTokens.union(candTokens).size.toFloat()
            val similarity   = if (union == 0f) 0f else intersection / union

            println("🔤 OCR duplicate → similarity=${"%.2f".format(similarity)} (threshold=$threshold) intersection=$intersection union=$union")

            DuplicateCheckResult(
                isDuplicate = similarity >= threshold,
                similarity  = similarity,
                reason      = if (similarity >= threshold) "same_side" else "different_side"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            DuplicateCheckResult(isDuplicate = fallbackResult, similarity = 0f, reason = "ocr_error")
        }
    }

    /** Runs ML Kit OCR on [bitmap] and returns the raw text string. */
    private suspend fun extractRawText(bitmap: Bitmap): String = suspendCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resume("") }
    }

    /**
     * Converts OCR text into a normalized set of tokens.
     * - Lowercase
     * - Only alphanumeric tokens with at least 3 characters
     * - Numbers kept as-is (important for document numbers / dates)
     */
    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()

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

    /**
     * Resultado de la validación de documento.
     */
    data class DocumentValidationResult(
        val isDocument: Boolean,
        val reason: String
    )

    /**
     * Palabras clave EXCLUSIVAS del FRENTE de documentos de identidad.
     * Se eliminaron palabras genéricas que pueden aparecer en laptops, carteles, etc.
     */
    private val DOCUMENT_KEYWORDS = setOf(
        // Español — solo aparecen en documentos oficiales
        "nombres", "apellidos", "apellido",
        "nacimiento", "vencimiento", "emisión", "emision",
        "identidad", "identificacion", "identificación",
        "dui", "domicilio", "nacionalidad",
        "ministerio", "república", "republica",
        "salvadoreño", "salvadorena",
        // Inglés / internacional — exclusivos de documentos
        "surname", "forename", "nationality",
        "expiry", "expiration", "date of birth",
        "passport no", "passport number",
        "identity card", "id card",
        // Zona MRZ de pasaportes (siempre presente en pasaportes)
        "<<<"
    )

    /**
     * Palabras clave del REVERSO de documentos de identidad.
     * El reverso no tiene nombre/apellido pero sí dirección, municipio, etc.
     * También incluye patrones típicos de reversos de carnets (firma, huella, etc.)
     */
    private val BACK_SIDE_KEYWORDS = setOf(
        // Dirección / domicilio
        "domicilio", "dirección", "direccion", "address", "residencia",
        // División político-administrativa
        "municipio", "departamento", "canton", "cantón", "barrio", "colonia",
        "ciudad", "distrito", "provincia", "estado", "region", "región",
        // Campos típicos de reverso
        "profesión", "profesion", "ocupacion", "ocupación", "profession",
        "estatura", "height", "talla",
        "firma", "signature", "huella",
        "estado civil", "civil status", "marital",
        "lugar de nacimiento", "birthplace", "lugar nacimiento",
        "emisión", "emision", "issued", "expedido",
        "vencimiento", "expiry", "expiration", "expires",
        "código", "codigo", "code",
        // Instituciones emisoras que a veces aparecen en el reverso
        "ministerio", "registro", "civil", "republic", "republica", "república",
        "government", "gobierno",
        // Zona MRZ (algunos documentos la tienen al reverso)
        "<<<"
    )

    /** Patrones de números de documento — evidencia muy fuerte de que es un carnet/pasaporte */
    private val DOC_NUMBER_PATTERNS = listOf(
        Regex("""\b\d{8}-\d\b"""),                  // DUI salvadoreño: 12345678-9
        Regex("""\b\d{9}\b"""),                      // DUI sin guión
        Regex("""\b[A-Z]{1,2}\d{6,8}\b"""),          // Pasaporte: PA123456
        Regex("""\b\d{2}/\d{2}/\d{4}\b"""),          // Fecha: 01/01/1990
        Regex("""\b\d{2}-\d{2}-\d{4}\b"""),          // Fecha: 01-01-1990
        Regex("""[A-Z0-9<]{30,44}""")                // Línea MRZ completa
    )

    /**
     * Determina si la imagen parece ser un documento de identidad válido.
     *
     * [isBackSide] = true cuando se escanea el reverso del documento.
     * El reverso no tiene nombre/apellido pero sí dirección, municipio, etc.
     * En ese modo se usan palabras clave del reverso y criterios más permisivos.
     *
     * Para el FRENTE (isBackSide = false):
     *   Criterios 2 Y 3 son AMBOS obligatorios para evitar falsos positivos.
     *
     * Para el REVERSO (isBackSide = true):
     *   Basta con tener texto suficiente + al menos 1 keyword del reverso,
     *   O tener texto suficiente + un patrón de número de documento.
     *   Si no se detecta ningún keyword ni número pero hay bastante texto
     *   estructurado (≥4 líneas, ≥6 palabras), igual se acepta — algunos
     *   reversos solo tienen código de barras y datos mínimos.
     */
    suspend fun isLikelyDocument(bitmap: Bitmap, isBackSide: Boolean = false): DocumentValidationResult {
        return suspendCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText   = visionText.text
                    val blockCount = visionText.textBlocks.size
                    val lineCount  = visionText.textBlocks.sumOf { it.lines.size }
                    val wordCount  = fullText.trim().split(Regex("\\s+")).count { it.length >= 2 }
                    val totalChars = fullText.trim().length
                    val lowerText  = fullText.lowercase()

                    println("📄 Doc check (backSide=$isBackSide) → blocks=$blockCount lines=$lineCount words=$wordCount chars=$totalChars")

                    if (isBackSide) {
                        // ═══════════════════════════════════════════════════════════
                        // REVERSO — criterios más permisivos
                        // El reverso del DUI/pasaporte no tiene nombre ni apellidos.
                        // Solo necesitamos evidencia mínima de que es un documento.
                        // ═══════════════════════════════════════════════════════════

                        // Criterio A: texto mínimo (más laxo que el frente)
                        val hasMinText = totalChars >= 10 && wordCount >= 2
                        if (!hasMinText) {
                            println("📄 BACK → REJECTED: no hay texto")
                            continuation.resume(DocumentValidationResult(isDocument = false, reason = "no_text"))
                            return@addOnSuccessListener
                        }

                        // Criterio B: palabras clave del reverso
                        val backKeywordHits = BACK_SIDE_KEYWORDS.count { lowerText.contains(it) }
                        println("📄 BACK → backKeywords=$backKeywordHits")

                        // Criterio C: palabras clave de cualquier documento (frente también aplica)
                        val frontKeywordHits = DOCUMENT_KEYWORDS.count { lowerText.contains(it) }
                        println("📄 BACK → frontKeywords=$frontKeywordHits")

                        // Criterio D: patrón de número de documento
                        val hasDocNumber = DOC_NUMBER_PATTERNS.any { it.containsMatchIn(fullText) }
                        println("📄 BACK → hasDocNumber=$hasDocNumber")

                        // Aceptar reverso si:
                        //  - Tiene al menos 1 keyword del reverso, O
                        //  - Tiene al menos 1 keyword del frente, O
                        //  - Tiene un patrón de número de documento, O
                        //  - Tiene bastante texto estructurado (≥ 4 líneas y ≥ 5 palabras)
                        //    → algunos reversos solo tienen código de barras 2D y texto mínimo
                        val isDocument = backKeywordHits >= 1
                            || frontKeywordHits >= 1
                            || hasDocNumber
                            || (lineCount >= 4 && wordCount >= 5)

                        println("📄 BACK → isDocument=$isDocument")
                        continuation.resume(DocumentValidationResult(
                            isDocument = isDocument,
                            reason = if (isDocument) "ok" else "no_text"
                        ))

                    } else {
                        // ═══════════════════════════════════════════════════════════
                        // FRENTE — criterios estrictos para evitar falsos positivos
                        // ═══════════════════════════════════════════════════════════

                        // Criterio 1: volumen mínimo de texto
                        val hasEnoughText = totalChars >= 20 && wordCount >= 3 && lineCount >= 2
                        if (!hasEnoughText) {
                            println("📄 FRONT → REJECTED: not enough text")
                            continuation.resume(DocumentValidationResult(isDocument = false, reason = "no_text"))
                            return@addOnSuccessListener
                        }

                        // Criterio 2: palabras clave EXCLUSIVAS de documentos
                        val keywordHits = DOCUMENT_KEYWORDS.count { lowerText.contains(it) }
                        println("📄 FRONT → keyword hits: $keywordHits")

                        // Criterio 3: patrón de número de documento
                        val hasDocNumber = DOC_NUMBER_PATTERNS.any { it.containsMatchIn(fullText) }
                        println("📄 FRONT → hasDocNumber: $hasDocNumber")

                        // OBLIGATORIO: al menos 1 keyword + (2+ keywords O número detectado)
                        val isDocument = keywordHits >= 1 && (keywordHits >= 2 || hasDocNumber)

                        println("📄 FRONT → keywords=$keywordHits docNumber=$hasDocNumber → isDocument=$isDocument")
                        continuation.resume(DocumentValidationResult(
                            isDocument = isDocument,
                            reason = if (isDocument) "ok" else "no_text"
                        ))
                    }
                }
                .addOnFailureListener {
                    continuation.resume(DocumentValidationResult(isDocument = true, reason = "ocr_error"))
                }
        }
    }
}
