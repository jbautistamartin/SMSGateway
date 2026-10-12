// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Baja el APK del release y se lo pasa al instalador del sistema.
 *
 * La descarga va por `DownloadManager`, el servicio del sistema: sobrevive a que el proceso
 * muera, reintenta solo y enseña su propia notificación. El fichero cae en la carpeta privada de
 * la aplicación en el almacenamiento externo, que no pide ningún permiso. No se usa el
 * [OkHttpClient][okhttp3.OkHttpClient] de la pasarela porque ese está para las llamadas a la
 * API, con sus tiempos límite pensados para una petición pequeña, no para tres megas.
 *
 * Instalar en silencio no se puede —solo lo hace un dueño del dispositivo—, así que lo que se
 * ofrece es abrir el diálogo del sistema. Eso exige `REQUEST_INSTALL_PACKAGES` en el manifiesto
 * y, desde Android 8, autorización explícita para esta aplicación.
 *
 * Android solo deja actualizar un APK **firmado con la misma clave**: ahí está la garantía de
 * que nadie cuela un APK falso, y también el motivo por el que esto no entra encima de una
 * compilación de depuración (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
 */
@Singleton
class DescargaDeApk @Inject constructor(
    @ApplicationContext private val contexto: Context
) {
    sealed interface Paso {
        data class Progreso(val porcentaje: Int) : Paso
        data class Lista(val uri: Uri) : Paso
        data class Fallo(val motivo: String) : Paso
    }

    /**
     * Encola la descarga y va diciendo cómo va.
     *
     * Se pregunta a `DownloadManager` cada [PASO_MS] en vez de esperar su *broadcast* de
     * descarga terminada: así hay porcentaje que pintar, y el flujo muere con la pantalla.
     */
    fun descargar(disponible: ResultadoActualizacion.Disponible): Flow<Paso> = flow {
        val url = disponible.apkUrl
        if (url.isNullOrBlank()) {
            emit(Paso.Fallo("ese release no publica ningún APK"))
            return@flow
        }

        val gestor = contexto.getSystemService(DownloadManager::class.java)
        if (gestor == null) {
            emit(Paso.Fallo("el dispositivo no tiene gestor de descargas"))
            return@flow
        }

        val nombre = disponible.apkNombre ?: "smsgateway-${disponible.version}.apk"

        // Una descarga anterior con el mismo nombre haría que DownloadManager guardase
        // «nombre-1.apk» y acabaríamos instalando el de antes.
        runCatching { File(contexto.getExternalFilesDir(null), nombre).delete() }

        val id = try {
            gestor.enqueue(
                DownloadManager.Request(Uri.parse(url))
                    .setTitle(nombre)
                    .setMimeType(TIPO_APK)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(contexto, null, nombre)
            )
        } catch (e: Exception) {
            Timber.w(e, "No se pudo encolar la descarga del APK")
            emit(Paso.Fallo(e.message ?: "no se pudo empezar la descarga"))
            return@flow
        }

        while (currentCoroutineContext().isActive) {
            val fila = runCatching {
                gestor.query(DownloadManager.Query().setFilterById(id))
            }.getOrNull()

            if (fila == null || !fila.moveToFirst()) {
                fila?.close()
                emit(Paso.Fallo("la descarga desapareció"))
                return@flow
            }

            val estado = fila.getInt(fila.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bajados = fila.getLong(
                fila.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = fila.getLong(
                fila.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            val razon = fila.getInt(fila.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            fila.close()

            when (estado) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = gestor.getUriForDownloadedFile(id)
                    emit(if (uri != null) Paso.Lista(uri) else Paso.Fallo("terminó sin fichero"))
                    return@flow
                }

                DownloadManager.STATUS_FAILED -> {
                    emit(Paso.Fallo("la descarga falló (código $razon)"))
                    return@flow
                }

                else -> {
                    emit(Paso.Progreso(if (total > 0) ((bajados * 100) / total).toInt() else 0))
                    delay(PASO_MS)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Si el sistema deja a esta aplicación abrir el instalador (Android 8 y posteriores). */
    fun puedeInstalar(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            contexto.packageManager.canRequestPackageInstalls()

    /** Los ajustes donde se autoriza a esta aplicación a instalar paquetes. */
    fun intentDeAutorizacion(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${contexto.packageName}")
    )

    /** El diálogo de instalación del sistema para el APK ya descargado. */
    fun intentDeInstalacion(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, TIPO_APK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private companion object {
        const val TIPO_APK = "application/vnd.android.package-archive"
        const val PASO_MS = 400L
    }
}
