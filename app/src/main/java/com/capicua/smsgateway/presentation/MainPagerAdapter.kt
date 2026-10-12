// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.presentation

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.capicua.smsgateway.R
import com.capicua.smsgateway.presentation.about.AboutFragment
import com.capicua.smsgateway.presentation.dashboard.DashboardFragment
import com.capicua.smsgateway.presentation.logs.LogsFragment
import com.capicua.smsgateway.presentation.settings.SettingsFragment

/**
 * Adaptador de las pantallas principales para el ViewPager2 de [com.capicua.smsgateway.MainActivity].
 *
 * El orden de [PAGINAS] define a la vez el orden de deslizamiento y el orden de los
 * botones de la barra inferior, y [Pagina.menuItemId] es lo que permite sincronizar
 * ambos controles sin duplicar la correspondencia en la activity.
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = PAGINAS.size

    override fun createFragment(position: Int): Fragment = PAGINAS[position].crear()

    /** Una pantalla: el id de su entrada en `bottom_nav_menu` y cómo instanciar su fragment. */
    class Pagina(@IdRes val menuItemId: Int, val crear: () -> Fragment)

    companion object {
        val PAGINAS: List<Pagina> = listOf(
            Pagina(R.id.dashboardFragment) { DashboardFragment() },
            Pagina(R.id.logsFragment)      { LogsFragment() },
            Pagina(R.id.settingsFragment)  { SettingsFragment() },
            Pagina(R.id.aboutFragment)     { AboutFragment() }
        )
    }
}
