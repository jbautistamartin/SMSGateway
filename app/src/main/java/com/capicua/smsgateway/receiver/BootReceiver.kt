// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.capicua.smsgateway.util.Constants
import com.capicua.smsgateway.worker.HealthMonitorWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Se ejecuta al arrancar el dispositivo o reinstalar la app.
 * WorkManager persiste sus colas en disco, pero el Worker periódico
 * necesita ser re-programado explícitamente tras reinicios en algunos fabricantes.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return

        Timber.d("BootReceiver: $action — programando HealthMonitorWorker")
        programarHealthMonitor(context)
    }

    companion object {
        /** Programa (o reemplaza) el Worker periódico de salud. */
        fun programarHealthMonitor(context: Context) {
            val restricciones = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val solicitud = PeriodicWorkRequestBuilder<HealthMonitorWorker>(
                Constants.HEALTH_MONITOR_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(restricciones)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.WORKER_HEALTH_TAG,
                ExistingPeriodicWorkPolicy.KEEP,    // no resetear si ya está programado
                solicitud
            )
        }
    }
}