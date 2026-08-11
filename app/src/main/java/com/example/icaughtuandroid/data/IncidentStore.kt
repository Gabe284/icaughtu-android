package com.example.icaughtuandroid.data

import android.content.Context
import java.io.File
import java.time.Instant

object IncidentStore {
    private const val FILE_NAME = "incidents.log"

    @Synchronized
    fun append(
        context: Context,
        source: String,
        attempts: Int,
        result: String,
        latitude: Double? = null,
        longitude: Double? = null,
        photoName: String? = null,
        detail: String? = null
    ) {
        val storageContext = context.createDeviceProtectedStorageContext()
        val file = File(storageContext.filesDir, FILE_NAME)
        val line = buildString {
            append(Instant.now().toString())
            append(" | source=").append(source)
            append(" | attempts=").append(attempts)
            append(" | result=").append(result)
            if (latitude != null && longitude != null) {
                append(" | location=").append(latitude).append(',').append(longitude)
            }
            if (!photoName.isNullOrBlank()) append(" | photo=").append(photoName)
            if (!detail.isNullOrBlank()) append(" | ").append(detail.replace('\n', ' '))
            append('\n')
        }
        file.appendText(line)
    }

    fun readRecent(context: Context, maxLines: Int = 12): String {
        val storageContext = context.createDeviceProtectedStorageContext()
        val file = File(storageContext.filesDir, FILE_NAME)
        if (!file.exists()) return "No incidents recorded."
        return file.readLines().takeLast(maxLines).reversed().joinToString("\n")
    }

    fun incidentDirectory(context: Context): File {
        val storageContext = context.createDeviceProtectedStorageContext()
        return File(storageContext.filesDir, "incident_photos").apply { mkdirs() }
    }
}
