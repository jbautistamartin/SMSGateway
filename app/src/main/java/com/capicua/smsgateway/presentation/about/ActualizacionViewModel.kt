// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.presentation.about

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capicua.smsgateway.data.update.ComprobadorDeActualizaciones
import com.capicua.smsgateway.data.update.DescargaDeApk
import com.capicua.smsgateway.data.update.EstadoActualizacion
import com.capicua.smsgateway.data.update.ResultadoActualizacion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * El estado de la tarjeta «Actualizaciones» de la pestaña «Acerca de».
 *
 * Al crearse comprueba **respetando el intervalo de un día**, así que entrar y salir de la
 * pestaña no son diez preguntas a GitHub. El botón sí fuerza, porque es una pregunta explícita.
 */
@HiltViewModel
class ActualizacionViewModel @Inject constructor(
    private val comprobador: ComprobadorDeActualizaciones,
    private val descargas: DescargaDeApk,
    private val estado: EstadoActualizacion
) : ViewModel() {

    data class Estado(
        val avisar: Boolean = true,
        val comprobando: Boolean = false,
        val resultado: ResultadoActualizacion? = null,
        val porcentaje: Int? = null,
        val error: String? = null,
        /** APK listo para instalar. La pantalla lo usa una vez y avisa de que ya está. */
        val apkListo: Uri? = null
    )

    private val _ui = MutableStateFlow(Estado())
    val ui: StateFlow<Estado> = _ui.asStateFlow()

    private var descarga: Job? = null

    init {
        viewModelScope.launch {
            estado.avisar.collect { avisar -> _ui.update { it.copy(avisar = avisar) } }
        }
        comprobar(forzar = false)
    }

    fun comprobar(forzar: Boolean) {
        viewModelScope.launch {
            _ui.update { it.copy(comprobando = true, error = null) }
            val resultado = comprobador.comprobar(forzar)
            _ui.update { anterior ->
                anterior.copy(
                    comprobando = false,
                    // «No tocaba preguntar» no borra lo que ya se sabía.
                    resultado = if (resultado is ResultadoActualizacion.SinPreguntar) {
                        anterior.resultado
                    } else {
                        resultado
                    }
                )
            }
        }
    }

    fun cambiarAvisos(activado: Boolean) {
        viewModelScope.launch { estado.cambiarAvisos(activado) }
    }

    fun descargar() {
        val disponible = _ui.value.resultado as? ResultadoActualizacion.Disponible ?: return
        if (descarga?.isActive == true) return

        descarga = viewModelScope.launch {
            descargas.descargar(disponible).collect { paso ->
                when (paso) {
                    is DescargaDeApk.Paso.Progreso ->
                        _ui.update { it.copy(porcentaje = paso.porcentaje, error = null) }

                    is DescargaDeApk.Paso.Lista ->
                        _ui.update { it.copy(porcentaje = null, apkListo = paso.uri) }

                    is DescargaDeApk.Paso.Fallo ->
                        _ui.update { it.copy(porcentaje = null, error = paso.motivo) }
                }
            }
        }
    }

    /** La pantalla ya abrió el instalador: que no se vuelva a abrir al volver a la pestaña. */
    fun apkConsumido() {
        _ui.update { it.copy(apkListo = null) }
    }

    fun puedeInstalar(): Boolean = descargas.puedeInstalar()

    fun intentDeAutorizacion(): Intent = descargas.intentDeAutorizacion()

    fun intentDeInstalacion(uri: Uri): Intent = descargas.intentDeInstalacion(uri)
}
