// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.capicua.smsgateway.data.repository.LogRepository
import com.capicua.smsgateway.data.repository.SmsRepository
import com.capicua.smsgateway.domain.model.LogEntry
import com.capicua.smsgateway.domain.model.LogTipo
import com.capicua.smsgateway.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.guava.await
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Worker periódico que actúa como red de seguridad del gateway.
 *
 * Ejecuta cada [Constants.HEALTH_MONITOR_INTERVAL_MINUTES] minutos y:
 * 1. Busca SMS pendientes sin Worker activo → los re-encola
 * 2. Limpia SMS enviados más antiguos que la política de retención
 * 3. Limpia logs más antiguos que la política de retención
 *
 * Esto garantiza que ningún SMS quede atascado indefinidamente incluso si el
 * proceso fue terminado abruptamente entre el INSERT en Room y el enqueue en WorkManager.
 */
@HiltWorker
class HealthMonitorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val smsRepository: SmsRepository,
    private val logRepository: LogRepository,
    private val workManager: WorkManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("HealthMonitorWorker: inicio")

        var reencolados = 0

        // ── 1. Re-encolar SMS pendientes sin Worker activo ────────────────────
        val pendientes = smsRepository.obtenerPendientes()
        for (sms in pendientes) {
            val nombreUnico = "${Constants.WORKER_DISPATCH_TAG}_${sms.id}"
            val workerInfos = workManager
                .getWorkInfosForUniqueWork(nombreUnico)
                .await()

            val tieneWorkerActivo = workerInfos.any { info ->
                info.state in listOf(
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.BLOCKED
                )
            }

            if (!tieneWorkerActivo) {
                smsRepository.encolarEnvio(sms.id)
                reencolados++
                Timber.d("HealthMonitorWorker: re-encolado SMS ${sms.id} (intentos: ${sms.intentos})")
            }
        }

        if (reencolados > 0) {
            logRepository.insertar(
                LogEntry(
                    tipo      = LogTipo.SISTEMA,
                    detalle   = "HealthMonitor: $reencolados SMS re-encolados de ${pendientes.size} pendientes",
                    timestamp = Instant.now()
                )
            )
        }

        // ── 2. Limpiar SMS enviados antiguos ──────────────────────────────────
        val corteRetencionSms = Instant.now()
            .minus(Constants.RETENTION_DELIVERED_DAYS, ChronoUnit.DAYS)
        smsRepository.limpiarEnviadosAntiguos(corteRetencionSms.toEpochMilli())

        // ── 3. Limpiar logs antiguos ──────────────────────────────────────────
        val corteLogs = Instant.now()
            .minus(Constants.RETENTION_LOGS_DAYS, ChronoUnit.DAYS)
        logRepository.eliminarAntiguos(corteLogs)

        Timber.d("HealthMonitorWorker: completado (re-encolados: $reencolados, pendientes: ${pendientes.size})")
        return Result.success()
    }
}