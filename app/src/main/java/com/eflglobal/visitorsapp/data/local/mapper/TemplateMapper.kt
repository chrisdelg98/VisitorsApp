package com.eflglobal.visitorsapp.data.local.mapper

import com.eflglobal.visitorsapp.data.local.entity.DocumentTemplateEntity
import com.eflglobal.visitorsapp.data.remote.dto.OcrTemplateDto
import com.eflglobal.visitorsapp.data.remote.dto.SignatureDto
import com.eflglobal.visitorsapp.data.remote.dto.TemplateFieldDto
import com.eflglobal.visitorsapp.domain.model.DocumentTemplate
import com.eflglobal.visitorsapp.domain.model.TemplateAnchor
import com.eflglobal.visitorsapp.domain.model.TemplateField
import com.eflglobal.visitorsapp.domain.model.TemplateRegion
import com.eflglobal.visitorsapp.domain.model.TemplateSignature
import com.eflglobal.visitorsapp.domain.model.TemplateValidation
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Converts OCR templates between the three representations:
 *   wire DTO  ⇄  Room entity (signature/fields as JSON)  ⇄  domain model.
 *
 * A private Moshi instance keeps the nested `signature`/`fields` (de)serialisation
 * self-contained — it is only ever used here.
 */
object TemplateMapper {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val signatureAdapter = moshi.adapter(SignatureDto::class.java)
    private val fieldsAdapter = moshi.adapter<List<TemplateFieldDto>>(
        Types.newParameterizedType(List::class.java, TemplateFieldDto::class.java)
    )

    // ── DTO → Entity ──────────────────────────────────────────────────────────

    fun toEntity(dto: OcrTemplateDto): DocumentTemplateEntity = DocumentTemplateEntity(
        id               = dto.id,
        documentTypeId   = dto.documentTypeId,
        countryId        = dto.countryId,
        code             = dto.code,
        name             = dto.name,
        documentKind     = dto.documentKind,
        subdivision      = dto.subdivision,
        version          = dto.version,
        side             = dto.side,
        extractionMethod = dto.extractionMethod,
        signatureJson    = signatureAdapter.toJson(dto.signature ?: SignatureDto()),
        fieldsJson       = fieldsAdapter.toJson(dto.fields),
        publishedAt      = dto.publishedAt,
        updatedAt        = dto.updatedAt
    )

    // ── Entity → Domain ───────────────────────────────────────────────────────

    fun toDomain(entity: DocumentTemplateEntity): DocumentTemplate {
        val signatureDto = runCatching { signatureAdapter.fromJson(entity.signatureJson) }
            .getOrNull() ?: SignatureDto()
        val fieldDtos = runCatching { fieldsAdapter.fromJson(entity.fieldsJson) }
            .getOrNull() ?: emptyList()

        return DocumentTemplate(
            id               = entity.id,
            documentTypeId   = entity.documentTypeId,
            countryId        = entity.countryId,
            code             = entity.code,
            name             = entity.name,
            documentKind     = entity.documentKind,
            subdivision      = entity.subdivision,
            version          = entity.version,
            side             = entity.side,
            extractionMethod = entity.extractionMethod,
            signature        = TemplateSignature(
                keywords    = signatureDto.keywords,
                idRegex     = signatureDto.idRegex,
                aspectRatio = signatureDto.aspectRatio,
                hasMrz      = signatureDto.hasMrz,
                hasPdf417   = signatureDto.hasPdf417
            ),
            fields = fieldDtos.map { f ->
                TemplateField(
                    field      = f.field,
                    method     = f.method,
                    anchor     = f.anchor?.let { TemplateAnchor(it.label, it.position) },
                    region     = f.region?.let { TemplateRegion(it.x, it.y, it.w, it.h) },
                    validation = f.validation?.let {
                        TemplateValidation(it.regex, it.length, it.charset)
                    }
                )
            }
        )
    }
}
