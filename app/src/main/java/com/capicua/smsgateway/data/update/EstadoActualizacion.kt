// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que el comprobador de versiones recuerda: si puede preguntar, cuándo preguntó y el `ETag`
 * de la última respuesta.
 *
 * Comparte el DataStore de los ajustes pero **no es un ajuste de la pasarela**: no tiene que ver
 * con la URL, los reintentos ni el tiempo límite, y por eso no entra en [
 * com.capicua.smsgateway.data.config.AppConfig] ni en la copia de seguridad que se lleva la
 * configuración a otro teléfono.
 */
@Singleton
class EstadoActualizacion @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    data class Memoria(
        val avisar: Boolean,
        val ultimaComprobacionMs: Long,
        val etag: String?,
        /**
         * La etiqueta del último release que se encontró, o null si no había ninguno más nuevo.
         *
         * Se guarda porque el aviso tiene que seguir puesto al volver a abrir la aplicación:
         * dentro del mismo día no se vuelve a preguntar, y sin esto el punto de la pestaña
         * desaparecía en la segunda apertura.
         */
        val etiqueta: String?,
        val pagina: String?,
        val apkUrl: String?,
        val apkNombre: String?,
        val bytes: Long
    )

    /** Si la aplicación puede preguntar por su cuenta. El interruptor de «Acerca de» lo escucha. */
    val avisar: Flow<Boolean> = dataStore.data.map { prefs -> prefs[Claves.AVISAR] ?: true }

    suspend fun leer(): Memoria {
        val prefs = dataStore.data.first()
        return Memoria(
            avisar = prefs[Claves.AVISAR] ?: true,
            ultimaComprobacionMs = prefs[Claves.ULTIMA_COMPROBACION] ?: 0L,
            etag = prefs[Claves.ETAG],
            etiqueta = prefs[Claves.ETIQUETA],
            pagina = prefs[Claves.PAGINA],
            apkUrl = prefs[Claves.APK_URL],
            apkNombre = prefs[Claves.APK_NOMBRE],
            bytes = prefs[Claves.APK_BYTES] ?: 0L
        )
    }

    suspend fun cambiarAvisos(activado: Boolean) {
        dataStore.edit { prefs -> prefs[Claves.AVISAR] = activado }
    }

    /** Lo que limita el ritmo es haber preguntado, así que esto se apunta también sin novedades. */
    suspend fun anotarComprobacion(ahoraMs: Long, etag: String?) {
        dataStore.edit { prefs ->
            prefs[Claves.ULTIMA_COMPROBACION] = ahoraMs
            if (etag.isNullOrBlank()) prefs.remove(Claves.ETAG) else prefs[Claves.ETAG] = etag
        }
    }

    /** Apunta el release encontrado, para seguir avisando de él sin volver a preguntar. */
    suspend fun anotarNovedad(
        etiqueta: String,
        pagina: String,
        apkUrl: String?,
        apkNombre: String?,
        bytes: Long
    ) {
        dataStore.edit { prefs ->
            prefs[Claves.ETIQUETA] = etiqueta
            prefs[Claves.PAGINA] = pagina
            prefs[Claves.APK_BYTES] = bytes
            if (apkUrl.isNullOrBlank()) {
                prefs.remove(Claves.APK_URL)
            } else {
                prefs[Claves.APK_URL] = apkUrl
            }
            if (apkNombre.isNullOrBlank()) {
                prefs.remove(Claves.APK_NOMBRE)
            } else {
                prefs[Claves.APK_NOMBRE] = apkNombre
            }
        }
    }

    /**
     * Olvida el release que se recordaba.
     *
     * Se llama cuando GitHub contesta que no hay nada más nuevo, que es justo lo que pasa en
     * cuanto se instala la versión de la que se estaba avisando: el aviso se va con ella.
     */
    suspend fun olvidarNovedad() {
        dataStore.edit { prefs ->
            prefs.remove(Claves.ETIQUETA)
            prefs.remove(Claves.PAGINA)
            prefs.remove(Claves.APK_URL)
            prefs.remove(Claves.APK_NOMBRE)
            prefs.remove(Claves.APK_BYTES)
        }
    }

    private object Claves {
        val AVISAR = booleanPreferencesKey("actualizacion_avisar")
        val ULTIMA_COMPROBACION = longPreferencesKey("actualizacion_ultima_comprobacion")
        val ETAG = stringPreferencesKey("actualizacion_etag")
        val ETIQUETA = stringPreferencesKey("actualizacion_etiqueta")
        val PAGINA = stringPreferencesKey("actualizacion_pagina")
        val APK_URL = stringPreferencesKey("actualizacion_apk_url")
        val APK_NOMBRE = stringPreferencesKey("actualizacion_apk_nombre")
        val APK_BYTES = longPreferencesKey("actualizacion_apk_bytes")
    }
}
