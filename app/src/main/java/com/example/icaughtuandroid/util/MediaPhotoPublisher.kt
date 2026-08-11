package com.example.icaughtuandroid.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.icaughtuandroid.data.IncidentStore
import java.io.File

object MediaPhotoPublisher {
    const val ALBUM_NAME = "iCaughtU"

    data class PublishResult(
        val ok: Boolean,
        val uri: Uri? = null,
        val detail: String
    )

    data class SyncResult(
        val published: Int,
        val alreadyPublished: Int,
        val failed: Int
    )

    fun publish(context: Context, source: File): PublishResult {
        if (!source.isFile) return PublishResult(false, detail = "gallery source file missing")

        val marker = markerFor(source)
        if (marker.isFile) {
            val saved = marker.readText().trim()
            return PublishResult(
                true,
                saved.takeIf { it.startsWith("content://") }?.let(Uri::parse),
                "already present in $ALBUM_NAME album"
            )
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishMediaStore(context, source, marker)
        } else {
            publishLegacy(context, source, marker)
        }
    }

    fun syncIncidentPhotos(context: Context): SyncResult {
        var published = 0
        var already = 0
        var failed = 0

        IncidentStore.incidentDirectory(context)
            .listFiles { file -> file.isFile && file.extension.equals("jpg", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.lastModified() }
            .forEach { photo ->
                val hadMarker = markerFor(photo).isFile
                val result = publish(context, photo)
                when {
                    result.ok && hadMarker -> already++
                    result.ok -> published++
                    else -> failed++
                }
            }

        return SyncResult(published, already, failed)
    }

    private fun publishMediaStore(context: Context, source: File, marker: File): PublishResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME")
            put(MediaStore.Images.Media.DATE_TAKEN, source.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var uri: Uri? = null
        return try {
            uri = resolver.insert(collection, values)
                ?: return PublishResult(false, detail = "MediaStore refused photo insertion")

            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "MediaStore output stream unavailable" }
                source.inputStream().use { input -> input.copyTo(output) }
            }

            ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }.also { resolver.update(uri, it, null, null) }

            marker.writeText(uri.toString())
            PublishResult(true, uri, "published to Pictures/$ALBUM_NAME")
        } catch (t: Throwable) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            PublishResult(false, detail = "gallery publish failed: ${t.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(context: Context, source: File, marker: File): PublishResult {
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return PublishResult(false, detail = "storage permission required on Android 9")
        }

        return try {
            val album = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                ALBUM_NAME
            ).apply { mkdirs() }
            val target = File(album, source.name)
            if (!target.exists()) source.copyTo(target, overwrite = false)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(target.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            marker.writeText(Uri.fromFile(target).toString())
            PublishResult(true, Uri.fromFile(target), "published to Pictures/$ALBUM_NAME")
        } catch (t: Throwable) {
            PublishResult(false, detail = "legacy gallery publish failed: ${t.message}")
        }
    }

    private fun markerFor(source: File): File = File(source.parentFile, ".${source.name}.published")
}
