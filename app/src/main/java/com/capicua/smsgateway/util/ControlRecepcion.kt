// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.capicua.smsgateway.receiver.SmsReceiver
import timber.log.Timber

/**
 * Enciende y apaga la lectura de SMS entrantes.
 *
 * **No es una comprobación dentro del receptor, es apagar el receptor.** Un `if` al principio
 * de `onReceive` dejaría a la aplicación despertándose con cada mensaje que entra en el
 * teléfono para no hacer nada con él, y obligaría a leer DataStore —que es asíncrono— en el
 * hilo principal y con los diez segundos contados del broadcast. Deshabilitando el componente,
 * el sistema deja de entregarle el broadcast: ni se arranca el proceso, ni hay nada que
 * decidir, ni se puede colar un SMS por una carrera entre el ajuste y el mensaje.
 *
 * Lo que apaga es la **entrada**. Lo que ya estaba guardado y encolado se sigue reenviando:
 * descartarlo sería perder mensajes que la aplicación ya había aceptado.
 *
 * El estado del componente lo guarda el `PackageManager`, no la aplicación, así que **sobrevive
 * a los reinicios** por sí solo. Aun así [aplicar] se vuelve a llamar al arrancar la aplicación
 * y desde el `BootReceiver`, porque una reinstalación sí lo devuelve a su valor del manifiesto
 * y el ajuste guardado tiene que seguir mandando.
 */
object ControlRecepcion {

    /** Deja el receptor de SMS encendido o apagado, según [activa]. */
    fun aplicar(context: Context, activa: Boolean) {
        val estado = if (activa) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, SmsReceiver::class.java),
                estado,
                // Sin DONT_KILL_APP el sistema mata el proceso al cambiar el componente, y el
                // cambio se haría desde la pantalla de ajustes: la aplicación se cerraría sola
                // delante del operador justo al tocar el interruptor.
                PackageManager.DONT_KILL_APP
            )
            Timber.i("Recepción de SMS ${if (activa) "activada" else "desactivada"}")
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "No se pudo cambiar el estado del receptor de SMS")
        }
    }

    /**
     * true si el receptor está habilitado ahora mismo.
     *
     * `COMPONENT_ENABLED_STATE_DEFAULT` cuenta como habilitado: es lo que devuelve mientras
     * nadie haya tocado el componente, y en el manifiesto está habilitado.
     */
    fun estaActiva(context: Context): Boolean {
        val estado = context.packageManager.getComponentEnabledSetting(
            ComponentName(context, SmsReceiver::class.java)
        )
        return estado != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
}
