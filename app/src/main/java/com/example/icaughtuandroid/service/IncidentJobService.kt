package com.example.icaughtuandroid.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import com.example.icaughtuandroid.data.IncidentStore
import com.example.icaughtuandroid.data.Prefs
import com.example.icaughtuandroid.util.IncidentDelivery
import com.example.icaughtuandroid.util.LocationUtil
import com.example.icaughtuandroid.util.NotificationUtil
import kotlin.concurrent.thread

class IncidentJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "icu-incident-job") {
            val attempts = params.extras.getInt(KEY_ATTEMPTS, 1)
            val source = params.extras.getString(KEY_SOURCE, "failed_unlock") ?: "failed_unlock"
            val prefs = Prefs(this)
            val location = if (prefs.includeLocation) LocationUtil.bestLastKnownLocation(this) else null
            val result = IncidentDelivery.sendAll(
                this,
                source,
                attempts,
                location?.latitude,
                location?.longitude,
                null
            )
            IncidentStore.append(
                this,
                source,
                attempts,
                if (result.ok) "processed" else "delivery_failed",
                location?.latitude,
                location?.longitude,
                detail = result.detail
            )
            NotificationUtil.event(
                this,
                "Security incident recorded",
                if (result.ok) "Failed unlock attempt #$attempts was recorded." else "Incident recorded; one or more deliveries failed."
            )
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val KEY_ATTEMPTS = "attempts"
        private const val KEY_SOURCE = "source"

        fun enqueue(context: Context, attempts: Int, source: String) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val extras = PersistableBundle().apply {
                putInt(KEY_ATTEMPTS, attempts)
                putString(KEY_SOURCE, source)
            }
            val prefs = Prefs(context)
            val networkDelivery =
                (prefs.webhookEnabled && prefs.webhookUrl.isNotBlank()) ||
                    prefs.emailEnabled ||
                    prefs.ntfyIncidentEnabled
            val jobId = ((System.currentTimeMillis() and 0x7fffffffL) % Int.MAX_VALUE).toInt()
            val job = JobInfo.Builder(jobId, ComponentName(context, IncidentJobService::class.java))
                .setExtras(extras)
                .setMinimumLatency(0)
                .setOverrideDeadline(5_000)
                .setRequiredNetworkType(if (networkDelivery) JobInfo.NETWORK_TYPE_ANY else JobInfo.NETWORK_TYPE_NONE)
                .build()
            scheduler.schedule(job)
        }
    }
}
