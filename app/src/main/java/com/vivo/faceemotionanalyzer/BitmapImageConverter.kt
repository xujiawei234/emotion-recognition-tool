//package com.vivo.faceemotionanalyzer
//
//import android.graphics.Bitmap
//import android.graphics.ImageFormat
//import android.graphics.Matrix
//import android.media.Image
//import androidx.annotation.OptIn
//import androidx.camera.core.ExperimentalGetImage
//import com.google.mediapipe.framework.image.BitmapImageBuilder
//import com.google.mediapipe.framework.image.MPImage
//import androidx.camera.core.ImageProxy
//import java.nio.ByteBuffer
//
//object BitmapImageConverter {
//
//    @OptIn(ExperimentalGetImage::class)
//    fun convert(imageProxy: ImageProxy): MPImage {
//        val image = imageProxy.image ?: throw IllegalStateException("Image is null")
//
//        val bitmap = toBitmap(image)
//        val matrix = Matrix().apply {
//            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
//            postScale(-1f, 1f)
//        }
//        val rotatedBitmap = Bitmap.createBitmap(
//            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
//        )
//        return BitmapImageBuilder(rotatedBitmap).build()
//    }
//
//    private fun toBitmap(image: Image): Bitmap {
//        val yBuffer = image.planes[0].buffer
//        val uBuffer = image.planes[1].buffer
//        val vBuffer = image.planes[2].buffer
//
//        val ySize = yBuffer.remaining()
//        val uSize = uBuffer.remaining()
//        val vSize = vBuffer.remaining()
//
//        val nv21 = ByteArray(ySize + uSize + vSize)
//        yBuffer.get(nv21, 0, ySize)
//        vBuffer.get(nv21, ySize, vSize)
//        uBuffer.get(nv21, ySize + vSize, uSize)
//
//        val yuvImage = android.graphics.YuvImage(
//            nv21, ImageFormat.NV21, image.width, image.height, null
//        )
//        val outputStream = java.io.ByteArrayOutputStream()
//        yuvImage.compressToJpeg(
//            android.graphics.Rect(0, 0, yuvImage.width, yuvImage.height), 100, outputStream
//        )
//        val jpegByteArray = outputStream.toByteArray()
//        return android.graphics.BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.size)
//    }
//}