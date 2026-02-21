package com.eflglobal.visitorsapp.core.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Utilidad para generar códigos QR.
 *
 * Usa la librería ZXing para generar códigos QR como Bitmap.
 */
object QRCodeGenerator {

    /**
     * Genera un código QR a partir de un texto.
     *
     * @param content El contenido del QR (ej: "VISIT-{visitId}-{timestamp}")
     * @param size El tamaño en pixels del QR (ancho y alto)
     * @return Bitmap con el código QR generado
     */
    fun generateQRCode(
        content: String,
        size: Int = 512
    ): Bitmap {
        val hints = hashMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
        }

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)

        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                )
            }
        }

        return bitmap
    }

    /**
     * Genera un código QR con colores personalizados.
     *
     * @param content El contenido del QR
     * @param size El tamaño en pixels
     * @param foregroundColor Color del QR (por defecto negro)
     * @param backgroundColor Color del fondo (por defecto blanco)
     * @return Bitmap con el código QR generado
     */
    fun generateQRCodeWithColors(
        content: String,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val hints = hashMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
        }

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)

        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) foregroundColor else backgroundColor
                )
            }
        }

        return bitmap
    }
}

