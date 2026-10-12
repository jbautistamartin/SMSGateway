// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.capicua.smsgateway.data.config.ConfigDataStore
import com.capicua.smsgateway.receiver.BootReceiver
import com.capicua.smsgateway.util.ControlRecepcion
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class GatewayApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var configDataStore: ConfigDataStore

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Programar workers al arrancar la app
        // (BootReceiver los reprograma tras reboot; aquí cubrimos el primer inicio)
        BootReceiver.programarHealthMonitor(this)
        BootReceiver.iniciarOrphanRescue(this)
        sincronizarRecepcion()
    }

    /**
     * Deja el receptor de SMS como diga el ajuste guardado.
     *
     * El estado del componente lo persiste el sistema y sobrevive a los reinicios, así que esto
     * no hace falta casi nunca. Hace falta justo después de **reinstalar**: ahí el componente
     * vuelve al valor del manifiesto —habilitado— y quien hubiera apagado la recepción se la
     * encontraría encendida otra vez sin haber tocado nada.
     *
     * Va en su propio scope y no bloquea el arranque: DataStore lee de disco, y `onCreate` de
     * la Application corre en el hilo principal antes de que se pinte nada.
     */
    private fun sincronizarRecepcion() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val recepcionActiva = configDataStore.config.first().recepcionActiva
            if (recepcionActiva != ControlRecepcion.estaActiva(this@GatewayApplication)) {
                ControlRecepcion.aplicar(this@GatewayApplication, recepcionActiva)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()
}