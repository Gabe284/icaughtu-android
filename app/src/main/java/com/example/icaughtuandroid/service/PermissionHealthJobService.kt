package com.example.icaughtuandroid.service

import android.app.job.JobParameters
import android.app.job.JobService
import com.example.icaughtuandroid.util.PermissionHealthMonitor

class PermissionHealthJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        PermissionHealthMonitor.checkNow(this)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
