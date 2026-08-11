package com.example.icaughtuandroid.service

import android.Manifest
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import com.example.icaughtuandroid.data.IncidentStore
import com.example.icaughtuandroid.data.Prefs
import com.example.icaughtuandroid.util.LocationUtil
import com.example.icaughtuandroid.util.NotificationUtil
import com.example.icaughtuandroid.util.IncidentDelivery
import com.example.icaughtuandroid.util.MediaPhotoPublisher
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class IncidentCaptureService : Service() {
    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private val completed = AtomicBoolean(false)
    private var attempts: Int = 1
    private var source: String = "failed_unlock"

    override fun onCreate() {
        super.onCreate()
        NotificationUtil.ensureChannels(this)
        cameraThread = HandlerThread("icu-camera").also { it.start() }
        cameraHandler = Handler(cameraThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        attempts = intent?.getIntExtra(EXTRA_ATTEMPTS, 1) ?: 1
        source = intent?.getStringExtra(EXTRA_SOURCE) ?: "failed_unlock"

        val dpm = getSystemService(DevicePolicyManager::class.java)
        val deviceOwner = dpm.isDeviceOwnerApp(packageName)
        val manualTest = source == "manual_test"
        val cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (!cameraGranted || (!deviceOwner && !manualTest)) {
            IncidentStore.append(this, source, attempts, "photo_unavailable", detail = "Device Owner or visible manual test required")
            IncidentJobService.enqueue(this, attempts, source)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startCaptureForeground()
        } catch (t: Throwable) {
            IncidentStore.append(this, source, attempts, "foreground_start_failed", detail = t.message)
            IncidentJobService.enqueue(this, attempts, source)
            stopSelf()
            return START_NOT_STICKY
        }

        cameraHandler.postDelayed({ complete(null, "camera timeout") }, 12_000)
        openFrontCamera()
        return START_NOT_STICKY
    }

    private fun startCaptureForeground() {
        val notification = NotificationUtil.captureNotification(this, "Capturing a security incident")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasLocation) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            startForeground(NotificationUtil.CAPTURE_NOTIFICATION_ID, notification, types)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NotificationUtil.CAPTURE_NOTIFICATION_ID, notification)
        }
    }

    private fun openFrontCamera() {
        val manager = getSystemService(CameraManager::class.java)
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
        if (cameraId == null) {
            complete(null, "no front camera")
            return
        }

        val characteristics = manager.getCameraCharacteristics(cameraId)
        val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.JPEG)
            ?.toList()
            .orEmpty()
        val preferred = sizes.filter { it.width.toLong() * it.height <= 2_100_000L }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.minByOrNull { it.width.toLong() * it.height }

        if (preferred == null) {
            complete(null, "camera has no JPEG output")
            return
        }

        imageReader = ImageReader.newInstance(preferred.width, preferred.height, ImageFormat.JPEG, 2).also { reader ->
            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val file = File(
                        IncidentStore.incidentDirectory(this),
                        "incident-${System.currentTimeMillis()}.jpg"
                    )
                    FileOutputStream(file).use { it.write(bytes) }
                    complete(file, "photo captured")
                } catch (t: Throwable) {
                    complete(null, "photo save failed: ${t.message}")
                } finally {
                    image.close()
                }
            }, cameraHandler)
        }

        try {
            @Suppress("MissingPermission")
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    complete(null, "camera disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    complete(null, "camera error $error")
                }
            }, cameraHandler)
        } catch (t: Throwable) {
            complete(null, "open camera failed: ${t.message}")
        }
    }

    private fun createCaptureSession(camera: CameraDevice) {
        val reader = imageReader ?: return complete(null, "image reader missing")
        try {
            @Suppress("DEPRECATION")
            camera.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }.build()
                        session.capture(request, object : CameraCaptureSession.CaptureCallback() {}, cameraHandler)
                    } catch (t: Throwable) {
                        complete(null, "capture failed: ${t.message}")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    complete(null, "camera session configuration failed")
                }
            }, cameraHandler)
        } catch (t: Throwable) {
            complete(null, "camera session failed: ${t.message}")
        }
    }

    private fun complete(photo: File?, cameraDetail: String) {
        if (!completed.compareAndSet(false, true)) return
        closeCamera()

        thread(name = "icu-incident-upload") {
            val prefs = Prefs(this)
            val gallery = photo?.let { MediaPhotoPublisher.publish(this, it) }
            val location = if (prefs.includeLocation) LocationUtil.bestLastKnownLocation(this) else null
            val result = IncidentDelivery.sendAll(
                this,
                source,
                attempts,
                location?.latitude,
                location?.longitude,
                photo
            )
            IncidentStore.append(
                this,
                source,
                attempts,
                if (result.ok) "processed" else "delivery_failed",
                location?.latitude,
                location?.longitude,
                photo?.name,
                "$cameraDetail; ${gallery?.detail ?: "no gallery photo"}; ${result.detail}"
            )
            NotificationUtil.event(
                this,
                "Security incident recorded",
                buildString {
                    append("Attempt #").append(attempts)
                    if (photo != null) append("; front-camera photo saved")
                    if (gallery?.ok == true) append("; added to iCaughtU album")
                    if (!result.ok) append("; delivery failed")
                }
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun closeCamera() {
        runCatching { captureSession?.close() }
        runCatching { cameraDevice?.close() }
        runCatching { imageReader?.close() }
        captureSession = null
        cameraDevice = null
        imageReader = null
    }

    override fun onDestroy() {
        closeCamera()
        if (::cameraThread.isInitialized) cameraThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_ATTEMPTS = "attempts"
        const val EXTRA_SOURCE = "source"
    }
}
