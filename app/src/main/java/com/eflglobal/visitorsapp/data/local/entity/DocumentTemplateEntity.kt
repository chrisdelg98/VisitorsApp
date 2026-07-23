package com.eflglobal.visitorsapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room-cached copy of a published OCR template (one row per template item).
 *
 * The nested `signature` and `fields` objects are stored verbatim as JSON in
 * [signatureJson] / [fieldsJson] — this keeps the schema flat and avoids a
 * migration every time the backend adds a nested attribute. They are
 * (de)serialised in [com.eflglobal.visitorsapp.data.local.mapper.TemplateMapper].
 *
 * The whole table is REPLACED transactionally on every successful catalog sync
 * (the endpoint returns a full snapshot, not deltas).
 */
@Entity(
    tableName = "document_templates",
    indices = [
        Index(value = ["countryId"]),
        Index(value = ["side"])
    ]
)
data class DocumentTemplateEntity(
    @PrimaryKey val id: String,
    val documentTypeId: String?,
    val countryId: String?,
    val code: String?,
    val name: String?,
    val documentKind: String?,
    val subdivision: String?,
    val version: Int,
    val side: String,
    val extractionMethod: String,
    /** JSON of the `signature` object. */
    val signatureJson: String,
    /** JSON array of the `fields`. */
    val fieldsJson: String,
    val publishedAt: String?,
    val updatedAt: String?
)
