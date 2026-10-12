// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.data.repository

import com.capicua.smsgateway.domain.model.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Tests del modelo de dominio del SMS.
 *
 * Eran un esqueleto escrito contra la versión inglesa del modelo (`sender`, `body`, `status`),
 * que dejó de existir cuando los miembros pasaron a español, así que **no compilaban** y con
 * ellos no compilaba ninguna prueba de este módulo. Ahora comprueban lo que de verdad se puede
 * comprobar sin Room ni Android: que un SMS recién recibido nace sin enviar, sin intentos y sin
 * error, que es la premisa del patrón *outbox*.
 *
 * Las pruebas del repositorio de verdad necesitan una base de datos Room en memoria y por tanto
 * instrumentación; mientras no estén, esto no pretende sustituirlas.
 */
class SmsRepositoryTest {

    private fun smsDePrueba(
        id: String = UUID.randomUUID().toString(),
        telefono: String = "+34600112233",
        mensaje: String = "Tu codigo es 4821"
    ): SmsMessage = SmsMessage(
        id = id,
        telefono = telefono,
        mensaje = mensaje,
        fechaRecepcion = Instant.now()
    )

    @Test
    fun `un SMS recien recibido guarda lo que llego`() {
        val sms = smsDePrueba(telefono = "+34600000000", mensaje = "Hola Gateway")

        assertEquals("+34600000000", sms.telefono)
        assertEquals("Hola Gateway", sms.mensaje)
        assertNotNull(sms.id)
        assertNotNull(sms.fechaRecepcion)
    }

    /**
     * El SMS se persiste **antes** de intentar enviarlo, así que su estado inicial es siempre
     * «pendiente»: sin enviar, sin fecha de envío, sin intentos y sin error.
     */
    @Test
    fun `nace pendiente de enviar`() {
        val sms = smsDePrueba()

        assertFalse(sms.enviado)
        assertNull(sms.fechaEnvio)
        assertEquals(0, sms.intentos)
        assertNull(sms.ultimoError)
    }

    /** El id es la clave de idempotencia frente a la API: dos SMS nunca lo comparten. */
    @Test
    fun `cada SMS trae su propio identificador`() {
        assertEquals(2, setOf(smsDePrueba().id, smsDePrueba().id).size)
    }
}
