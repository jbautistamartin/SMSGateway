// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la comparación de versiones del aviso de actualización.
 *
 * Es la única pieza del comprobador que se puede probar sin red, y la que falla en silencio: si
 * se equivoca, la aplicación deja de avisar y nadie se entera hasta mirar los releases a mano.
 */
class VersionPublicadaTest {

    @Test
    fun `interpreta la etiqueta con y sin la v del tag`() {
        assertEquals(VersionPublicada(1, 0, 1), VersionPublicada.interpretar("v1.0.1"))
        assertEquals(VersionPublicada(1, 0, 1), VersionPublicada.interpretar("1.0.1"))
    }

    @Test
    fun `lo que falta vale cero`() {
        assertEquals(VersionPublicada(1, 0, 0), VersionPublicada.interpretar("1"))
        assertEquals(VersionPublicada(1, 2, 0), VersionPublicada.interpretar("1.2"))
    }

    @Test
    fun `descarta el sufijo de preestreno y el de metadatos`() {
        assertEquals(VersionPublicada(1, 1, 0), VersionPublicada.interpretar("v1.1.0-rc1"))
        assertEquals(VersionPublicada(1, 1, 0), VersionPublicada.interpretar("1.1.0+9f8e7d"))
    }

    @Test
    fun `una etiqueta que no es una version no se interpreta`() {
        assertNull(VersionPublicada.interpretar(null))
        assertNull(VersionPublicada.interpretar(""))
        assertNull(VersionPublicada.interpretar("latest"))
        assertNull(VersionPublicada.interpretar("1.0.1.2"))
        assertNull(VersionPublicada.interpretar("1.cero.1"))
    }

    /**
     * El caso por el que esta clase existe: como texto, «1.10.0» va *antes* de «1.9.0», así que
     * comparar cadenas dejaría de avisar en cuanto la *minor* pasara de nueve.
     */
    @Test
    fun `la decima version es mayor que la novena`() {
        assertTrue(VersionPublicada.interpretar("1.10.0")!! > VersionPublicada.interpretar("1.9.0")!!)
    }

    @Test
    fun `la mayor pesa mas que la menor y que el parche`() {
        assertTrue(VersionPublicada(2, 0, 0) > VersionPublicada(1, 99, 99))
    }

    @Test
    fun `el parche decide cuando lo demas empata`() {
        assertTrue(VersionPublicada.interpretar("v1.0.2")!! > VersionPublicada.interpretar("v1.0.1")!!)
    }

    @Test
    fun `la version instalada no se supera a si misma`() {
        val instalada = VersionPublicada.interpretar("1.0.1")!!
        assertTrue(VersionPublicada.interpretar("v1.0.1")!! <= instalada)
        assertTrue(VersionPublicada.interpretar("1.0.0")!! < instalada)
    }
}
