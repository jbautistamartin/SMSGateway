// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.presentation.settings

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.capicua.smsgateway.R
import com.capicua.smsgateway.data.config.AppConfig
import com.capicua.smsgateway.databinding.FragmentSettingsBinding
import com.capicua.smsgateway.util.InicioAutomatico
import com.capicua.smsgateway.util.OptimizacionBateria
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    private var cargaInicial = true

    // ── Selectores de fichero del sistema ─────────────────────────────────────
    //
    // La copia se escribe y se lee sobre el fichero que elige el operador, así que no hace
    // falta ningún permiso de almacenamiento.

    private val crearFichero = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let { viewModel.exportar(it, configDeLaPantalla()) } }

    private val abrirFichero = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importar(it) } }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observarConfiguracion()
        observarTestEstado()
        observarCopias()
        configurarBotonGuardar()
        configurarBotonProbar()
        configurarBateria()
        configurarAutoinicio()
        configurarCopia()

        binding.editTextUrlTemplate.doAfterTextChanged { viewModel.resetearTest() }
    }

    /**
     * El estado de la exención se refresca al volver a la pantalla porque se concede **fuera**
     * de la aplicación: el operador sale a Ajustes del sistema y vuelve, y lo que tiene que ver
     * al volver es el estado nuevo, no el que había cuando se fue.
     */
    override fun onResume() {
        super.onResume()
        pintarEstadoBateria()
    }

    // ── Exención de batería ───────────────────────────────────────────────────

    private fun configurarBateria() {
        binding.buttonBateria.setOnClickListener {
            if (!OptimizacionBateria.abrirAjuste(requireContext())) {
                Snackbar.make(
                    binding.root,
                    "Este teléfono no permite abrir ese ajuste desde la app. Búscalo en Ajustes → Batería → Optimización de batería.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Pinta el estado actual de la exención.
     *
     * Cuando ya está concedida el botón se queda, pero como acción secundaria: sirve para
     * revisarlo, y algunos fabricantes la revocan por su cuenta tras una actualización.
     */
    private fun pintarEstadoBateria() {
        val exenta = OptimizacionBateria.estaExenta(requireContext())

        binding.textViewBateriaEstado.text =
            if (exenta) "✓ Sin restricciones de batería" else "⚠ La optimización de batería está activa"
        binding.textViewBateriaEstado.setTextColor(
            resources.getColor(
                if (exenta) R.color.status_delivered else R.color.status_pending,
                null
            )
        )
        binding.textViewBateriaDetalle.text = if (exenta) {
            "El sistema no suspende la aplicación: los SMS llegan a la API en segundos aunque la pantalla esté apagada."
        } else {
            "Con el ahorro de batería activo, Android puede retrasar los envíos horas mientras la pantalla está apagada. Concede la exención para que salgan al momento."
        }
        binding.buttonBateria.text =
            if (exenta) "Revisar en Ajustes del sistema" else "Desactivar ahorro de batería"
    }

    // ── Inicio automático ─────────────────────────────────────────────────────

    /**
     * No hay estado que pintar: el permiso de inicio automático de los fabricantes no se puede
     * consultar desde la aplicación. Solo queda llevar al operador a la pantalla correcta.
     */
    private fun configurarAutoinicio() {
        binding.buttonAutoinicio.setOnClickListener {
            if (!InicioAutomatico.abrirAjuste(requireContext())) {
                Snackbar.make(
                    binding.root,
                    "Este teléfono no permite abrir esa pantalla desde la app. Búscala en Ajustes → Aplicaciones → SMS Gateway → Inicio automático.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    // ── Copia de seguridad ────────────────────────────────────────────────────

    private fun configurarCopia() {
        binding.buttonExportar.setOnClickListener {
            crearFichero.launch(viewModel.nombreFicheroCopia())
        }
        // Importar pisa lo que haya: se avisa antes, porque no hay vuelta atrás.
        binding.buttonImportar.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Importar")
                .setMessage("La configuración del fichero sustituye a la actual, incluida la plantilla de URL.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Importar") { _, _ ->
                    abrirFichero.launch(arrayOf("application/json", "text/plain"))
                }
                .show()
        }
    }

    private fun observarCopias() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.copias.collect { resultado ->
                    val mensaje = when (resultado) {
                        ResultadoCopia.Exportada -> "Copia guardada"
                        ResultadoCopia.Importada -> "Configuración restaurada"
                        is ResultadoCopia.Fallo -> "No se pudo completar: ${resultado.motivo}"
                    }
                    Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()

                    // Los campos en pantalla son los de antes de importar: hay que repintarlos
                    // con lo que acaba de entrar, o el siguiente «Guardar» desharía la copia.
                    //
                    // No se leen aquí: DataStore emite su valor nuevo cuando termina de
                    // escribir, y en este instante `config` puede seguir siendo el de antes.
                    // Rearmando la carga inicial, el repintado lo hace la emisión que llegue.
                    if (resultado == ResultadoCopia.Importada) {
                        cargaInicial = true
                    }
                }
            }
        }
    }

    private fun observarConfiguracion() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.config.collect { config ->
                    // config es null mientras DataStore no ha emitido su primer valor real;
                    // esperamos a ese primer valor antes de rellenar los campos.
                    if (config != null && cargaInicial) {
                        rellenarCampos(config)
                        cargaInicial = false
                    }
                }
            }
        }
    }

    private fun observarTestEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.testEstado.collect { estado ->
                    actualizarUiTest(estado)
                }
            }
        }
    }

    private fun actualizarUiTest(estado: TestConexionEstado) {
        val progress  = binding.progressTest
        val resultado = binding.textTestResultado

        when (estado) {
            TestConexionEstado.Inactivo -> {
                progress.visibility  = View.GONE
                resultado.visibility = View.GONE
            }
            TestConexionEstado.Cargando -> {
                progress.visibility  = View.VISIBLE
                resultado.visibility = View.GONE
                binding.buttonProbar.isEnabled = false
            }
            is TestConexionEstado.Respuesta -> {
                progress.visibility  = View.GONE
                resultado.visibility = View.VISIBLE
                binding.buttonProbar.isEnabled = true
                val ok = estado.codigoHttp in 200..299
                resultado.text = if (ok) "✓ HTTP ${estado.codigoHttp}" else "✗ HTTP ${estado.codigoHttp}"
                resultado.setTextColor(if (ok) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
            }
            is TestConexionEstado.ErrorRed -> {
                progress.visibility  = View.GONE
                resultado.visibility = View.VISIBLE
                binding.buttonProbar.isEnabled = true
                resultado.text = "✗ ${estado.detalle}"
                resultado.setTextColor(Color.parseColor("#F44336"))
            }
        }
    }

    private fun rellenarCampos(config: AppConfig) {
        binding.switchRecepcion.isChecked = config.recepcionActiva
        binding.editTextUrlTemplate.setText(config.urlTemplate)
        binding.switchAceptarCertsInvalidos.isChecked = config.aceptarCertificadosInvalidos
        binding.editTextTimeout.setText(config.timeoutSegundos.toString())
        binding.editTextMaxReintentos.setText(config.maxReintentos.toString())
        binding.editTextIntervalo.setText(config.intervaloReintentoSegundos.toString())
    }

    private fun configurarBotonProbar() {
        binding.buttonProbar.setOnClickListener {
            val urlTemplate  = binding.editTextUrlTemplate.text?.toString()?.trim() ?: ""
            val aceptarCerts = binding.switchAceptarCertsInvalidos.isChecked
            val timeout      = binding.editTextTimeout.text?.toString()?.toIntOrNull() ?: 30

            if (urlTemplate.isBlank() || !urlTemplate.contains("{mensaje}")) {
                Snackbar.make(binding.root, "Configura una URL válida con {mensaje} antes de probar", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.probarConexion(
                AppConfig(urlTemplate = urlTemplate, aceptarCertificadosInvalidos = aceptarCerts, timeoutSegundos = timeout)
            )
        }
    }

    /**
     * La configuración tal como está en el formulario ahora mismo.
     *
     * Lo usan guardar y exportar: exportar lo que hay escrito en pantalla, y no lo último
     * guardado, es lo que espera quien acaba de tocar un campo y pulsa «Exportar».
     */
    private fun configDeLaPantalla() = AppConfig(
        urlTemplate = binding.editTextUrlTemplate.text?.toString()?.trim() ?: "",
        timeoutSegundos = binding.editTextTimeout.text?.toString()?.toIntOrNull() ?: 30,
        maxReintentos = binding.editTextMaxReintentos.text?.toString()?.toIntOrNull() ?: 10,
        intervaloReintentoSegundos = binding.editTextIntervalo.text?.toString()?.toIntOrNull() ?: 30,
        aceptarCertificadosInvalidos = binding.switchAceptarCertsInvalidos.isChecked,
        recepcionActiva = binding.switchRecepcion.isChecked
    )

    private fun configurarBotonGuardar() {
        binding.buttonGuardar.setOnClickListener {
            val config = configDeLaPantalla()

            if (!validar(
                    config.urlTemplate,
                    config.timeoutSegundos,
                    config.maxReintentos,
                    config.intervaloReintentoSegundos
                )
            ) return@setOnClickListener

            viewModel.guardar(config)
            Snackbar.make(
                binding.root,
                if (config.recepcionActiva) {
                    "Configuración guardada"
                } else {
                    // Apagar la recepción no es un ajuste más: la aplicación deja de hacer su
                    // trabajo y eso tiene que quedar dicho, no deducido.
                    "Guardado. La recepción de SMS queda desactivada: no se enviará nada a la API."
                },
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun validar(urlTemplate: String, timeout: Int, maxRetries: Int, intervalo: Int): Boolean {
        var valido = true

        if (urlTemplate.isBlank()) {
            binding.tilUrlTemplate.error = "La plantilla de URL es obligatoria"
            valido = false
        } else if (!urlTemplate.startsWith("http://") && !urlTemplate.startsWith("https://")) {
            binding.tilUrlTemplate.error = "Debe comenzar con http:// o https://"
            valido = false
        } else if (!urlTemplate.contains("{mensaje}")) {
            binding.tilUrlTemplate.error = "Debe contener el marcador {mensaje}"
            valido = false
        } else {
            binding.tilUrlTemplate.error = null
        }

        if (timeout < 5 || timeout > 120) {
            binding.tilTimeout.error = "Debe estar entre 5 y 120 segundos"
            valido = false
        } else binding.tilTimeout.error = null

        if (maxRetries < 1 || maxRetries > 100) {
            binding.tilMaxReintentos.error = "Debe estar entre 1 y 100"
            valido = false
        } else binding.tilMaxReintentos.error = null

        if (intervalo < 5 || intervalo > 3600) {
            binding.tilIntervalo.error = "Debe estar entre 5 y 3600 segundos"
            valido = false
        } else binding.tilIntervalo.error = null

        return valido
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
