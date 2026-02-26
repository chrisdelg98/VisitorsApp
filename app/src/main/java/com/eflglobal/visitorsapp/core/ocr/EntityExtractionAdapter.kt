package com.eflglobal.visitorsapp.core.ocr

import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * EntityExtractionAdapter
 *
 * Wraps ML Kit Entity Extraction (on-device, no network required after model
 * download) to boost confidence scoring in FieldScoringEngine.
 *
 * Lines tagged as date/phone by ML Kit are penalised in name scoring.
 * Gracefully returns EMPTY if the model isn't downloaded yet.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
object EntityExtractionAdapter {

    data class EntityExtractionResult(
        val dateLines: Set<String>,
        val phoneLines: Set<String>,
        val addressLines: Set<String>,
        val allEntityLines: Set<String>
    ) {
        val nonNameLines: Set<String> get() = dateLines + phoneLines

        companion object {
            val EMPTY = EntityExtractionResult(emptySet(), emptySet(), emptySet(), emptySet())
        }
    }

    suspend fun extractEntities(text: String): EntityExtractionResult =
        suspendCoroutine { cont ->
            if (text.isBlank()) { cont.resume(EntityExtractionResult.EMPTY); return@suspendCoroutine }

            val extractor = EntityExtraction.getClient(
                EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
            )

            extractor.downloadModelIfNeeded()
                .addOnSuccessListener { _ ->
                    val params = EntityExtractionParams.Builder(text).build()
                    extractor.annotate(params)
                        .addOnSuccessListener { annotations: List<EntityAnnotation> ->
                            val dateLines    = mutableSetOf<String>()
                            val phoneLines   = mutableSetOf<String>()
                            val addressLines = mutableSetOf<String>()
                            val allLines     = mutableSetOf<String>()

                            for (annotation in annotations) {
                                val snippet = annotation.annotatedText
                                allLines += snippet
                                for (entity in annotation.entities) {
                                    when (entity.type) {
                                        Entity.TYPE_DATE_TIME -> dateLines  += snippet
                                        Entity.TYPE_PHONE     -> phoneLines += snippet
                                        Entity.TYPE_ADDRESS   -> addressLines += snippet
                                        else -> Unit
                                    }
                                }
                            }

                            log("entities → dates=${dateLines.size} phones=${phoneLines.size}")
                            extractor.close()
                            cont.resume(EntityExtractionResult(dateLines, phoneLines, addressLines, allLines))
                        }
                        .addOnFailureListener { e: Exception ->
                            log("annotate failed: ${e.message}")
                            extractor.close()
                            cont.resume(EntityExtractionResult.EMPTY)
                        }
                }
                .addOnFailureListener { e: Exception ->
                    log("model not ready: ${e.message}")
                    extractor.close()
                    cont.resume(EntityExtractionResult.EMPTY)
                }
        }

    private fun log(msg: String) = android.util.Log.d("EntityAdapter", msg)
}
