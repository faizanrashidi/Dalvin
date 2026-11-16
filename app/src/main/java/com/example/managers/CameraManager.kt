package com.example.managers

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class CameraManager(private val context: Context) {

    fun openSystemCamera(videoMode: Boolean = false): String {
        val action = if (videoMode) {
            MediaStore.ACTION_VIDEO_CAPTURE
        } else {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        }
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching {
            context.startActivity(intent)
            if (videoMode) "Opened system video camera." else "Opened system camera."
        }.getOrElse {
            "Camera app unavailable: ${it.localizedMessage}"
        }
    }

    fun openSystemGallery(): String {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened system Gallery."
        }.getOrElse {
            "Gallery app unavailable; use NOVA Photo Picker."
        }
    }

    fun capturePhoto(
        imageCapture: ImageCapture,
        onSaved: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val name = "NOVA_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NOVA")
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri
                    if (uri != null) onSaved(uri) else onError("Photo saved without URI.")
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception.localizedMessage ?: "Capture failed")
                }
            }
        )
    }

    fun shareMediaUri(uri: Uri, mimeType: String = "image/*", targetPackage: String? = null): String {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!targetPackage.isNullOrBlank()) setPackage(targetPackage)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, "Share Media via NOVA").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "Opened media share sheet."
        }.getOrElse {
            "Unable to share media: ${it.localizedMessage}"
        }
    }
}
