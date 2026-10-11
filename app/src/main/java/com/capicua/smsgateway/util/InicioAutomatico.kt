// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import timber.log.Timber

/**
 * Permiso de inicio automático de las capas de fabricante.
 *
 * Android entrega `BOOT_COMPLETED` a cualquier aplicación que lo declare, pero **MIUI,
 * HyperOS, EMUI, ColorOS y Funtouch no**: tienen una lista propia de «inicio automático» que
 * viene vacía, y lo que no está en ella no recibe el arranque ni puede despertar en segundo
 * plano. El síntoma es exactamente el que se ve: se reinicia el teléfono, entran SMS y no pasa
 * nada hasta que alguien abre la aplicación a mano.
 *
 * Eso **no se puede conceder desde el código** —no hay permiso que pedir, es un ajuste de la
 * capa del fabricante—, así que lo único que cabe hacer es llevar al operador a la pantalla
 * correcta de su teléfono. De ahí este ayudante, hermano de [OptimizacionBateria].
 *
 * Tampoco se puede **consultar** si está concedido: no hay API. Por eso la pantalla de ajustes
 * no enseña un estado como el de la batería, sino una explicación y un botón.
 */
object InicioAutomatico {

    /**
     * Pantallas de inicio automático por fabricante, de la más directa a la más genérica.
     *
     * Se lanzan y se captura [ActivityNotFoundException] en lugar de mirar primero el
     * fabricante o consultar al `PackageManager`: `Build.MANUFACTURER` miente a menudo en las
     * ROM derivadas, y desde Android 11 la visibilidad de paquetes puede responder que una
     * actividad no existe cuando sí está. Lanzar nunca se equivoca.
     */
    private val PANTALLAS = listOf(
        // Xiaomi / Redmi / POCO — MIUI y HyperOS
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // Huawei / Honor — EMUI
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        // Oppo / Realme — ColorOS
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        // Vivo — Funtouch
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // Samsung — One UI
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity"
    )

    /**
     * Abre la pantalla de inicio automático del fabricante; si no hay ninguna, la ficha de la
     * aplicación en Ajustes, desde la que cualquier Android llega a sus permisos.
     *
     * @return true si se abrió alguna pantalla.
     */
    fun abrirAjuste(context: Context): Boolean {
        for ((paquete, actividad) in PANTALLAS) {
            val intent = Intent().apply {
                component = ComponentName(paquete, actividad)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Timber.d("Este dispositivo no tiene $actividad, pruebo la siguiente")
            } catch (e: SecurityException) {
                // Algunas ROM declaran la actividad pero no dejan lanzarla desde fuera.
                Timber.d(e, "El sistema rechazó $actividad")
            }
        }

        val ficha = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(ficha)
            true
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Ni siquiera se pudo abrir la ficha de la aplicación")
            false
        }
    }
}
