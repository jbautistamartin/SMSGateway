// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.presentation.common

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * Envoltorio para una vista que hace scroll en el **mismo eje** que el `ViewPager2` que la
 * contiene.
 *
 * Sin él los dos gestos son el mismo gesto y gana el pager: la fila de filtros de la pantalla
 * de Logs dejaría de poder desplazarse, porque arrastrar sobre ella cambiaría de pestaña. Este
 * contenedor arbitra: mientras el hijo pueda seguir desplazándose en la dirección del dedo, se
 * queda el gesto; cuando llega a su extremo, lo suelta y el pager cambia de página. Los gestos
 * perpendiculares se ceden siempre.
 *
 * Es la solución oficial de AndroidX para el scroll anidado en `ViewPager2` (clase
 * `NestedScrollableHost` de sus ejemplos), adaptada aquí en nombres y comentarios. El doble de
 * `touchSlop` para el eje del pager no es arbitrario: es el margen que `ViewPager2` se reserva
 * para decidir que un arrastre es un cambio de página.
 */
class NestedScrollableHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val touchSlop = ViewConfiguration.get(context).scaledPagingTouchSlop
    private var inicioX = 0f
    private var inicioY = 0f

    /** El `ViewPager2` que nos contiene, si hay alguno; null si esta vista se usa fuera de uno. */
    private val pagerContenedor: ViewPager2?
        get() {
            var vista: View? = parent as? View
            while (vista != null && vista !is ViewPager2) {
                vista = vista.parent as? View
            }
            return vista as? ViewPager2
        }

    private val hijo: View?
        get() = if (childCount > 0) getChildAt(0) else null

    /** ¿Puede el hijo seguir desplazándose en el eje [orientacion] hacia donde va el dedo? */
    private fun elHijoPuedeDesplazarse(orientacion: Int, desplazamiento: Float): Boolean {
        val direccion = -desplazamiento.sign.toInt()
        return when (orientacion) {
            ViewPager2.ORIENTATION_HORIZONTAL -> hijo?.canScrollHorizontally(direccion) ?: false
            else -> hijo?.canScrollVertically(direccion) ?: false
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        repartirGesto(ev)
        return super.onInterceptTouchEvent(ev)
    }

    private fun repartirGesto(ev: MotionEvent) {
        val orientacion = pagerContenedor?.orientation ?: return

        // Si el hijo no puede desplazarse en el eje del pager, no hay nada que arbitrar.
        if (!elHijoPuedeDesplazarse(orientacion, -1f) &&
            !elHijoPuedeDesplazarse(orientacion, 1f)
        ) {
            return
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                inicioX = ev.x
                inicioY = ev.y
                // Se retiene el gesto hasta saber de qué tipo es; ACTION_MOVE lo decide.
                parent.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - inicioX
                val dy = ev.y - inicioY
                val pagerHorizontal = orientacion == ViewPager2.ORIENTATION_HORIZONTAL

                val escaladoX = dx.absoluteValue * if (pagerHorizontal) 0.5f else 1f
                val escaladoY = dy.absoluteValue * if (pagerHorizontal) 1f else 0.5f

                if (escaladoX > touchSlop || escaladoY > touchSlop) {
                    if (pagerHorizontal == (escaladoY > escaladoX)) {
                        // Gesto perpendicular al pager: no es nuestro, que lo intercepte quien toque.
                        parent.requestDisallowInterceptTouchEvent(false)
                    } else {
                        // Gesto paralelo: se queda el hijo mientras le quede recorrido.
                        val leQuedaRecorrido =
                            elHijoPuedeDesplazarse(orientacion, if (pagerHorizontal) dx else dy)
                        parent.requestDisallowInterceptTouchEvent(leQuedaRecorrido)
                    }
                }
            }
        }
    }
}
