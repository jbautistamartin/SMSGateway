// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.presentation.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capicua.smsgateway.data.config.AppConfig
import com.capicua.smsgateway.data.config.ConfigBackup
import com.capicua.smsgateway.data.config.ConfigDataStore
import com.capicua.smsgateway.util.ControlRecepcion
import com.capicua.smsgateway.util.HttpClientFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

/** Resultado de una copia de seguridad, para contarlo una sola vez en la pantalla. */
sealed interface ResultadoCopia {
    data object Exportada : ResultadoCopia
    data object Importada : ResultadoCopia
    data class Fallo(val motivo: String) : ResultadoCopia
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val configDataStore: ConfigDataStore,
    private val configBackup: ConfigBackup,
    private val okHttpClient: OkHttpClient
) : AndroidViewModel(application) {

    /**
     * Estado actual de la configuración.
     * Emite null mientras DataStore no ha cargado aún su primer valor,
     * para que el fragment no rellene los campos con valores vacíos por defecto
     * antes de que llegue el valor real persistido.
     */
    val config: StateFlow<AppConfig?> = configDataStore.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _testEstado = MutableStateFlow<TestConexionEstado>(TestConexionEstado.Inactivo)
    /** Estado del envío de prueba a la API. */
    val testEstado: StateFlow<TestConexionEstado> = _testEstado.asStateFlow()

    private val _copias = MutableSharedFlow<ResultadoCopia>(extraBufferCapacity = 4)
    /** Resultado de la última exportación o importación. */
    val copias: SharedFlow<ResultadoCopia> = _copias.asSharedFlow()

    /**
     * Guarda la configuración editada.
     *
     * El interruptor de recepción no es un valor más: además de persistirlo hay que encender o
     * apagar el receptor de SMS, que es lo que de verdad corta la entrada de mensajes.
     */
    fun guardar(config: AppConfig) {
        viewModelScope.launch {
            configDataStore.guardar(config)
            ControlRecepcion.aplicar(getApplication(), config.recepcionActiva)
        }
    }

    /** Nombre sugerido para el fichero de copia. */
    fun nombreFicheroCopia(): String = configBackup.nombreFichero()

    fun exportar(destino: Uri, config: AppConfig) {
        viewModelScope.launch {
            _copias.emit(
                configBackup.exportar(destino, config).fold(
                    onSuccess = { ResultadoCopia.Exportada },
                    onFailure = { ResultadoCopia.Fallo(it.message ?: "no se pudo exportar") }
                )
            )
        }
    }

    /**
     * Importa una copia y la guarda.
     *
     * El interruptor de recepción que traiga el fichero se aplica también al receptor:
     * guardarlo y no tocar el componente dejaría la aplicación diciendo una cosa y haciendo
     * otra.
     */
    fun importar(origen: Uri) {
        viewModelScope.launch {
            _copias.emit(
                configBackup.importar(origen).fold(
                    onSuccess = { importada ->
                        configDataStore.guardar(importada)
                        ControlRecepcion.aplicar(getApplication(), importada.recepcionActiva)
                        ResultadoCopia.Importada
                    },
                    onFailure = { ResultadoCopia.Fallo(it.message ?: "no se pudo importar") }
                )
            )
        }
    }

    /**
     * Realiza un GET de prueba a la URL configurada usando datos ficticios,
     * y actualiza [testEstado] con el resultado.
     *
     * Se usa para validar la conectividad antes de recibir SMS reales.
     */
    fun probarConexion(config: AppConfig) {
        if (_testEstado.value == TestConexionEstado.Cargando) return

        viewModelScope.launch {
            _testEstado.value = TestConexionEstado.Cargando

            val url = config.construirUrl(
                telefono = "+34000000000",
                mensaje  = "SMS de prueba desde SMSGateway",
                fecha    = Instant.now().toString()
            )

            Timber.d("Test de conexión → $url")

            val client = HttpClientFactory.crear(okHttpClient, config)

            try {
                val codigo = withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(url).get().build())
                        .execute().use { it.code }
                }
                Timber.i("Test de conexión → HTTP $codigo")
                _testEstado.value = TestConexionEstado.Respuesta(codigo)

            } catch (e: IOException) {
                val detalle = e.message ?: "Error de red"
                Timber.w("Test de conexión → error: $detalle")
                _testEstado.value = TestConexionEstado.ErrorRed(detalle)
            }
        }
    }

    /** Reinicia el estado del test (útil al cambiar la URL). */
    fun resetearTest() {
        _testEstado.value = TestConexionEstado.Inactivo
    }
}
