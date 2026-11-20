package ar.gob.buenosaires.recs.go2hmi

import android.app.Application
import ar.gob.buenosaires.recs.go2hmi.di.AppContainer

/**
 * Clase `Application` de la app Guardaparques RECS.
 *
 * Mantiene el [AppContainer] único durante el ciclo de vida del proceso para que el
 * motor simulado del Go2 y el repositorio Room sobrevivan a rotaciones, cierre/reapertura
 * de `MainActivity` y navegación entre pantallas sin duplicar loops ni colecciones.
 */
class RecsGo2Application : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
