// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.data.update

import com.capicua.smsgateway.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que puede contestar [ComprobadorDeActualizaciones.comprobar]. */
sealed interface ResultadoActualizacion {

    /** La instalada es la última publicada. */
    data object AlDia : ResultadoActualizacion

    /** Hay una versión más nueva publicada. */
    data class Disponible(
        val version: VersionPublicada,
        val etiqueta: String,
        val pagina: String,
        val apkUrl: String?,
        val apkNombre: String?,
        val bytes: Long
    ) : ResultadoActualizacion

    /** Aún no tocaba, o el aviso automático está apagado. No es un error. */
    data object SinPreguntar : ResultadoActualizacion

    /** Se intentó y no se pudo: sin red, GitHub caído, respuesta ilegible. */
    data class NoSePudo(val motivo: String) : ResultadoActualizacion
}

/**
 * Pregunta a GitHub si hay publicada una versión más nueva que la instalada.
 *
 * Usa el mismo [OkHttpClient] con el que la aplicación llama a la API de reenvío, que ya viene
 * configurado y con sus tiempos límite: no hace falta un segundo cliente para una petición al
 * día. Lo que sí es distinto es el *User-Agent* — sin él, `api.github.com` responde 403.
 *
 * Tres cosas más, por si se toca esto:
 *
 * - **Una vez al día y con `If-None-Match`.** El límite sin autenticar son 60 peticiones por
 *   hora e IP y un 304 no gasta cuota. El botón de «Acerca de» salta el intervalo.
 * - **Sin token.** El repositorio es público; una credencial dentro del APK está regalada.
 * - **Un 404 significa que no hay releases**, no que algo vaya mal.
 *
 * Aquí solo se mira. Bajar e instalar es [DescargaDeApk], y nunca por su cuenta.
 */
@Singleton
class ComprobadorDeActualizaciones @Inject constructor(
    private val cliente: OkHttpClient,
    private val estado: EstadoActualizacion
) {
    suspend fun comprobar(forzar: Boolean): ResultadoActualizacion = withContext(Dispatchers.IO) {
        val memoria = estado.leer()
        val ahora = System.currentTimeMillis()

        if (!forzar) {
            if (!memoria.avisar) return@withContext ResultadoActualizacion.SinPreguntar
            if (ahora - memoria.ultimaComprobacionMs < INTERVALO_MS) {
                // No toca preguntar, pero lo que se encontró ayer sigue valiendo.
                return@withContext recordada(memoria) ?: ResultadoActualizacion.SinPreguntar
            }
        }

        val peticion = Request.Builder()
            .url(URL_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SMSGateway/${BuildConfig.VERSION_NAME}")
            .apply {
                if (!forzar && !memoria.etag.isNullOrBlank()) {
                    header("If-None-Match", memoria.etag)
                }
            }
            .build()

        try {
            cliente.newCall(peticion).execute().use { respuesta ->
                when {
                    respuesta.code == 304 -> {
                        estado.anotarComprobacion(ahora, memoria.etag)
                        recordada(memoria) ?: ResultadoActualizacion.AlDia
                    }

                    respuesta.code == 404 -> {
                        // Todavía no hay releases publicados.
                        estado.anotarComprobacion(ahora, null)
                        estado.olvidarNovedad()
                        ResultadoActualizacion.AlDia
                    }

                    !respuesta.isSuccessful -> {
                        estado.anotarComprobacion(ahora, memoria.etag)
                        ResultadoActualizacion.NoSePudo("GitHub respondió HTTP ${respuesta.code}")
                    }

                    else -> {
                        val cuerpo = respuesta.body?.string().orEmpty()
                        estado.anotarComprobacion(ahora, respuesta.header("ETag"))
                        interpretar(cuerpo)
                    }
                }
            }
        } catch (e: Exception) {
            // Sin red esto es lo normal, no una avería: se registra y la pantalla lo dice en
            // una línea si alguien entra a mirar.
            Timber.d(e, "No se pudo comprobar si hay versión nueva")
            estado.anotarComprobacion(ahora, memoria.etag)
            ResultadoActualizacion.NoSePudo(e.message ?: "error de red")
        }
    }

    /**
     * Saca de la respuesta lo poco que interesa.
     *
     * Se lee con [JSONObject] y no con un serializador: la respuesta de GitHub trae decenas de
     * campos, aquí hacen falta cuatro, y esta aplicación no arrastra `kotlinx.serialization`
     * para nada más.
     */
    private suspend fun interpretar(cuerpo: String): ResultadoActualizacion {
        val release = try {
            JSONObject(cuerpo)
        } catch (e: Exception) {
            Timber.w(e, "La respuesta de GitHub no se pudo interpretar")
            return ResultadoActualizacion.NoSePudo("respuesta ilegible")
        }

        val etiqueta = release.optString("tag_name")
        val publicada = VersionPublicada.interpretar(etiqueta)
        val instalada = VersionPublicada.interpretar(BuildConfig.VERSION_NAME)

        if (publicada == null || instalada == null || publicada <= instalada) {
            estado.olvidarNovedad()
            return ResultadoActualizacion.AlDia
        }

        var apkUrl: String? = null
        var apkNombre: String? = null
        var bytes = 0L

        val assets = release.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val nombre = asset.optString("name")
                if (nombre.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    apkNombre = nombre
                    bytes = asset.optLong("size")
                    break
                }
            }
        }

        val disponible = ResultadoActualizacion.Disponible(
            version = publicada,
            etiqueta = etiqueta,
            pagina = release.optString("html_url"),
            apkUrl = apkUrl,
            apkNombre = apkNombre,
            bytes = bytes
        )

        // Se apunta para poder seguir avisando mañana sin volver a preguntar.
        estado.anotarNovedad(
            etiqueta = disponible.etiqueta,
            pagina = disponible.pagina,
            apkUrl = disponible.apkUrl,
            apkNombre = disponible.apkNombre,
            bytes = disponible.bytes
        )

        return disponible
    }

    /**
     * El último release que se encontró, mientras siga siendo más nuevo que lo instalado.
     *
     * Es lo que mantiene el aviso puesto entre aperturas de la aplicación. También es la
     * respuesta correcta a un **304**: «el último release no ha cambiado» no significa que sea
     * el que está instalado. Y se cae solo cuando toca — en cuanto se instala la versión de la
     * que se avisaba, la comparación deja de cumplirse.
     */
    private fun recordada(memoria: EstadoActualizacion.Memoria): ResultadoActualizacion.Disponible? {
        val guardada = VersionPublicada.interpretar(memoria.etiqueta) ?: return null
        val instalada = VersionPublicada.interpretar(BuildConfig.VERSION_NAME) ?: return null
        if (guardada <= instalada) return null

        return ResultadoActualizacion.Disponible(
            version = guardada,
            etiqueta = memoria.etiqueta ?: "",
            pagina = memoria.pagina ?: "",
            apkUrl = memoria.apkUrl,
            apkNombre = memoria.apkNombre,
            bytes = memoria.bytes
        )
    }

    private companion object {
        const val URL_API = "https://api.github.com/repos/jbautistamartin/SMSGateway/releases/latest"
        const val INTERVALO_MS = 24 * 60 * 60 * 1000L
    }
}
