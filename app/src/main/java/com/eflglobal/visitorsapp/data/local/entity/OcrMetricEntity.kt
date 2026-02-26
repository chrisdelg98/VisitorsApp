package com.eflglobal.visitorsapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_metrics")
data class OcrMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,

    val detectedCountry: String,
    val detectedDocType: String,
    val classificationConfidence: Float,

    val firstNameConfidence: Float,
    val lastNameConfidence: Float,
    val docNumberConfidence: Float,

    val firstNameSource: String,
    val lastNameSource: String,
    val docNumberSource: String,

    val firstNameAutoFilled: Boolean,
    val lastNameAutoFilled: Boolean,
    val docNumberAutoFilled: Boolean,

    val firstNameCorrected: Boolean,
    val lastNameCorrected: Boolean,
    val docNumberCorrected: Boolean,

    val ocrCharCount: Int,
    val ocrLineCount: Int,
    val hasMrz: Boolean
)

