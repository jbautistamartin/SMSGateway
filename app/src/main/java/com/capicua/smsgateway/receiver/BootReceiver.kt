// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.capicua.smsgateway.util.Constants
import com.capicua.smsgateway.worker.HealthMonitorWorker
import com.capicua.smsgateway.worker.OrphanRescueWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Se ejecuta al arrancar el dispositivo o reinstalar la app.
 * WorkManager persiste sus colas en disco, pero el Worker periódico
 * necesita ser re-programado explícitamente tras reinicios en algunos fabricantes.
 *
 * **Recibir SMS no depende de esto.** El receptor de SMS está declarado en el manifiesto, así
 * que el sistema lo arranca por su cuenta aunque la aplicación no se haya abierto desde el
 * reinicio. Lo que esto reprograma son los trabajos de fondo —el rescate de huérfanos y el
 * vigilante de salud—, que sí se pierden al apagar el teléfono.
 *
 * Hay dos motivos por los que, aun así, puede no arrancar nada tras un reinicio, y ninguno se
 * arregla desde aquí:
 *
 * 1. **El inicio automático del fabricante.** MIUI, HyperOS, EMUI y compañía no entregan el
 *    arranque a lo que no esté en su lista, que viene vacía. La pantalla de ajustes lleva
 *    hasta ella con `InicioAutomatico`.
 * 2. **El estado «detenida».** Una aplicación recién instalada que nadie ha abierto todavía, o
 *    una que se ha forzado a detener, no recibe ningún broadcast —tampoco el de SMS— hasta que
 *    se abre una primera vez. Es de Android y no tiene vuelta: después de instalar hay que
 *    abrirla una vez.
 *
 * Se escuchan también los arranques rápidos propietarios (`QUICKBOOT_POWERON`), que algunas
 * ROM mandan **en lugar** de `BOOT_COMPLETED` cuando el apagado anterior fue el suyo.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ACCIONES) return

        Timber.d("BootReceiver: $action — programando workers")
        programarHealthMonitor(context)
        iniciarOrphanRescue(context)
    }

    companion object {

        /** Lo que se considera «ha arrancado el teléfono» o «me acaban de reinstalar». */
        private val ACCIONES = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        /** Programa (o reemplaza) el Worker periódico de limpieza (mínimo 15 min). */
        fun programarHealthMonitor(context: Context) {
            val solicitud = PeriodicWorkRequestBuilder<HealthMonitorWorker>(
                Constants.HEALTH_MONITOR_INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.WORKER_HEALTH_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                solicitud
            )
        }

        /**
         * Lanza la primera iteración del bucle de rescate de SMS huérfanos.
         * El propio [OrphanRescueWorker] se auto-encadena cada 30 segundos.
         * Se usa KEEP para no interrumpir una ejecución ya en curso.
         */
        fun iniciarOrphanRescue(context: Context) {
            val solicitud = OneTimeWorkRequestBuilder<OrphanRescueWorker>().build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                Constants.WORKER_ORPHAN_RESCUE_TAG,
                ExistingWorkPolicy.KEEP,
                solicitud
            )
        }
    }
}