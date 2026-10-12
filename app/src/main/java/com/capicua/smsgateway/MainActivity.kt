// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import androidx.lifecycle.lifecycleScope
import com.capicua.smsgateway.data.update.ComprobadorDeActualizaciones
import com.capicua.smsgateway.data.update.ResultadoActualizacion
import com.capicua.smsgateway.databinding.ActivityMainBinding
import com.capicua.smsgateway.presentation.MainPagerAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Activity principal y única de la aplicación.
 *
 * Responsabilidades:
 * 1. Configurar la navegación por fragmentos (ViewPager2 + Bottom Navigation).
 * 2. Solicitar los permisos en tiempo de ejecución necesarios para recibir SMS.
 *
 * En un dispositivo dedicado estos permisos se pueden pre-conceder vía MDM o ADB:
 *   adb shell pm grant com.capicua.smsgateway android.permission.RECEIVE_SMS
 *   adb shell pm grant com.capicua.smsgateway android.permission.READ_SMS
 *   adb shell pm grant com.capicua.smsgateway android.permission.POST_NOTIFICATIONS
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /**
     * El comprobador de versiones. Se pregunta desde aquí, y no solo desde «Acerca de», para que
     * saber que hay una versión nueva no dependa de que alguien abra esa pestaña.
     */
    @Inject
    lateinit var comprobadorDeActualizaciones: ComprobadorDeActualizaciones

    // ── Launcher de permisos ──────────────────────────────────────────────────

    private val solicitarPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        resultados.forEach { (permiso, concedido) ->
            Timber.d("Permiso '$permiso': ${if (concedido) "CONCEDIDO" else "DENEGADO"}")
        }

        val smsRecepcionDenegado = resultados[Manifest.permission.RECEIVE_SMS] == false
        if (smsRecepcionDenegado) {
            mostrarDialogoPermisoObligatorio()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarInsets()
        configurarNavegacion()
        verificarYSolicitarPermisos()
        comprobarSiHayVersionNueva()
    }

    /**
     * Pregunta —una vez al día como mucho, y solo si el aviso está encendido— si hay publicada
     * una versión más nueva, y lo marca con un punto en la pestaña de «Acerca de», que es donde
     * está la tarjeta que lo explica y deja instalarla.
     *
     * Ni diálogo ni notificación: este teléfono trabaja solo y lo que se ponga delante al
     * arrancar tapa justo lo que se viene a mirar. Sin red, no se dice nada.
     */
    private fun comprobarSiHayVersionNueva() {
        lifecycleScope.launch {
            val resultado = comprobadorDeActualizaciones.comprobar(forzar = false)
            if (resultado !is ResultadoActualizacion.Disponible) return@launch

            Timber.i("Versión nueva publicada: ${resultado.etiqueta}")
            binding.bottomNavigation.getOrCreateBadge(R.id.aboutFragment)
        }
    }

    // ── Insets del sistema (edge-to-edge) ─────────────────────────────────────

    /**
     * Con `targetSdk = 35` Android 15 fuerza el modo edge-to-edge: la ventana ocupa
     * toda la pantalla y el contenido queda por debajo de la barra de estado, del
     * notch/recorte de cámara y de la barra de navegación.
     *
     * Aquí se desactiva el ajuste automático del decor (para que el comportamiento
     * sea idéntico en todas las versiones, no solo en Android 15+) y se aplica el
     * relleno a mano:
     *
     * - Arriba y laterales: barra de estado + recorte de pantalla (notch), de modo
     *   que ningún fragmento quede tapado.
     * - Abajo: la barra de navegación se compensa en la BottomNavigationView, así su
     *   fondo sigue llegando al borde pero los iconos quedan por encima del gesto.
     * - Teclado: al dejar de aplicarse `adjustResize` en edge-to-edge, el IME se
     *   compensa como relleno inferior de la raíz para que los campos de Ajustes
     *   sigan visibles mientras se escribe.
     *
     * El listener devuelve `CONSUMED` para que ninguna vista hija vuelva a aplicar
     * los mismos insets (la BottomNavigationView de Material lo hace por su cuenta).
     */
    private fun configurarInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // La paleta de la app es clara en ambos modos, así que los iconos de las
        // barras del sistema deben dibujarse en oscuro para que se vean.
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { raiz, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

            raiz.setPadding(barras.left, barras.top, barras.right, teclado)

            // El inset del teclado ya incluye la barra de navegación: no se suma dos veces.
            binding.bottomNavigation.updatePadding(
                bottom = if (teclado > 0) 0 else barras.bottom
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    // ── Navegación ────────────────────────────────────────────────────────────

    /**
     * Las cuatro pantallas son páginas de un ViewPager2, así que se puede pasar de una
     * a otra deslizando el dedo a izquierda o derecha, además de pulsando en la barra
     * inferior. Ambos controles se mantienen sincronizados en los dos sentidos.
     */
    private fun configurarNavegacion() {
        val paginas = MainPagerAdapter.PAGINAS

        binding.viewPager.adapter = MainPagerAdapter(this)

        // Pulsación en la barra inferior → salta a la página correspondiente.
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val posicion = paginas.indexOfFirst { it.menuItemId == item.itemId }
            if (posicion < 0) return@setOnItemSelectedListener false
            // setCurrentItem(..., false) evita animar el recorrido completo cuando se
            // salta entre pestañas no adyacentes.
            binding.viewPager.setCurrentItem(posicion, false)
            true
        }

        // Deslizamiento → marca el botón correspondiente. Se marca el MenuItem en vez de
        // usar selectedItemId para no volver a disparar el listener de arriba.
        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.bottomNavigation.menu
                        .findItem(paginas[position].menuItemId)
                        .isChecked = true
                }
            }
        )
    }

    // ── Permisos ──────────────────────────────────────────────────────────────

    private fun verificarYSolicitarPermisos() {
        val permisosPendientes = permisosNecesarios().filter { permiso ->
            ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED
        }

        if (permisosPendientes.isEmpty()) {
            Timber.d("Todos los permisos ya concedidos")
            return
        }

        Timber.d("Solicitando ${permisosPendientes.size} permiso(s): $permisosPendientes")
        solicitarPermisos.launch(permisosPendientes.toTypedArray())
    }

    /**
     * Lista de permisos en tiempo de ejecución que la aplicación necesita.
     * POST_NOTIFICATIONS solo es obligatorio en Android 13+ (API 33).
     */
    private fun permisosNecesarios(): List<String> = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun mostrarDialogoPermisoObligatorio() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permiso obligatorio")
            .setMessage(
                "Esta aplicación necesita el permiso para recibir SMS. " +
                "Sin él no puede funcionar como gateway. " +
                "Concédelo en Ajustes → Aplicaciones → SMSGateway → Permisos."
            )
            .setPositiveButton("Entendido", null)
            .show()
    }
}