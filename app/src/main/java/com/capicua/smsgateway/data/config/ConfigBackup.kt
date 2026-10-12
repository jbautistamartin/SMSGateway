// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.data.config

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exporta e importa la configuración como JSON sobre un Uri del Storage Access Framework
 * (el fichero lo elige el operador, así que no hace falta ningún permiso de almacenamiento).
 *
 * Se escribe con `org.json` y no con una librería de serialización porque es la única cosa del
 * proyecto que lo necesita: traerse kotlinx.serialization entera por seis campos engordaría el
 * APK sin dar nada a cambio.
 *
 * **El fichero lleva la plantilla de URL**, que suele incluir el dominio y a veces una clave de
 * la API. Es información sensible y así se avisa en la pantalla de ajustes.
 */
@Singleton
class ConfigBackup @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** Versión del formato: permite migrar si algún día cambian los campos. */
        const val VERSION = 1

        private const val CAMPO_VERSION = "version"
        private const val CAMPO_EXPORTADO = "exportado"
        private const val CAMPO_URL = "urlTemplate"
        private const val CAMPO_TIMEOUT = "timeoutSegundos"
        private const val CAMPO_MAX_REINTENTOS = "maxReintentos"
        private const val CAMPO_INTERVALO = "intervaloReintentoSegundos"
        private const val CAMPO_CERTS_INVALIDOS = "aceptarCertificadosInvalidos"
        private const val CAMPO_RECEPCION = "recepcionActiva"
    }

    /** Nombre sugerido para el fichero. */
    fun nombreFichero(): String = "smsgateway-configuracion-${Instant.now().epochSecond}.json"

    suspend fun exportar(destino: Uri, config: AppConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // "wt" trunca: si se sobrescribe un fichero mayor no quedan restos del anterior.
                context.contentResolver.openOutputStream(destino, "wt")?.use { salida ->
                    salida.write(aJson(config).toByteArray())
                } ?: return@withContext Result.failure(
                    Exception("no se pudo abrir el fichero de destino")
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Error exportando la configuracion")
                Result.failure(e)
            }
        }

    suspend fun importar(origen: Uri): Result<AppConfig> = withContext(Dispatchers.IO) {
        try {
            val texto = context.contentResolver.openInputStream(origen)?.use { entrada ->
                entrada.readBytes().decodeToString()
            } ?: return@withContext Result.failure(Exception("no se pudo abrir el fichero"))
            Result.success(desdeJson(texto))
        } catch (e: Exception) {
            Timber.e(e, "Error importando la configuracion")
            Result.failure(e)
        }
    }

    private fun aJson(config: AppConfig): String = JSONObject().apply {
        put(CAMPO_VERSION, VERSION)
        put(CAMPO_EXPORTADO, Instant.now().toString())
        put(CAMPO_URL, config.urlTemplate)
        put(CAMPO_TIMEOUT, config.timeoutSegundos)
        put(CAMPO_MAX_REINTENTOS, config.maxReintentos)
        put(CAMPO_INTERVALO, config.intervaloReintentoSegundos)
        put(CAMPO_CERTS_INVALIDOS, config.aceptarCertificadosInvalidos)
        put(CAMPO_RECEPCION, config.recepcionActiva)
    }.toString(2)

    /** Lectura tolerante: los campos que falten se quedan con su valor por defecto. */
    private fun desdeJson(texto: String): AppConfig {
        val json = JSONObject(texto)
        if (!json.has(CAMPO_URL)) {
            throw IllegalArgumentException("el fichero no contiene una configuración de SMS Gateway")
        }
        val porDefecto = AppConfig()
        return AppConfig(
            urlTemplate = json.optString(CAMPO_URL, porDefecto.urlTemplate).trim(),
            timeoutSegundos = json.optInt(CAMPO_TIMEOUT, porDefecto.timeoutSegundos),
            maxReintentos = json.optInt(CAMPO_MAX_REINTENTOS, porDefecto.maxReintentos),
            intervaloReintentoSegundos =
                json.optInt(CAMPO_INTERVALO, porDefecto.intervaloReintentoSegundos),
            aceptarCertificadosInvalidos =
                json.optBoolean(CAMPO_CERTS_INVALIDOS, porDefecto.aceptarCertificadosInvalidos),
            recepcionActiva = json.optBoolean(CAMPO_RECEPCION, porDefecto.recepcionActiva)
        )
    }
}
