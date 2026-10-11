// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.presentation.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.capicua.smsgateway.BuildConfig
import com.capicua.smsgateway.R
import com.capicua.smsgateway.databinding.FragmentAboutBinding
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textVersion.text = "Versión ${BuildConfig.VERSION_NAME}"
        binding.buttonDonativo.setOnClickListener { abrirDonativo() }
    }

    /**
     * Abre el enlace del donativo en el navegador del dispositivo.
     *
     * Se lanza y se captura [ActivityNotFoundException] en lugar de preguntar antes al
     * `PackageManager`: desde Android 11 la visibilidad de paquetes puede responder que no hay
     * navegador cuando sí lo hay, mientras que lanzar nunca se equivoca. Si de verdad no hay
     * ninguno se avisa, porque un botón que no hace nada parece la aplicación rota.
     */
    private fun abrirDonativo() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_DONATIVO)))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No hay navegador que abra $URL_DONATIVO")
            Snackbar.make(
                binding.root,
                R.string.about_donativo_sin_navegador,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        /** Enlace del donativo que se abre desde «Acerca de». */
        const val URL_DONATIVO = "https://paypal.me/jbautistamartin"
    }
}
