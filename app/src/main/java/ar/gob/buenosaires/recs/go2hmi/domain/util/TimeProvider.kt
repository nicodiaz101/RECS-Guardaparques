package ar.gob.buenosaires.recs.go2hmi.domain.util

/**
 * Interfaz simple para obtener la hora actual en milisegundos.
 *
 * Nos permite calcular la antigüedad de los paquetes sin quedar atados a
 * `System.currentTimeMillis()`, algo muy útil para simular el paso del tiempo en los tests.
 */
fun interface TimeProvider {
    fun nowEpochMs(): Long
}

/**
 * Implementación estándar para producción respaldada por el reloj del sistema.
 */
object SystemTimeProvider : TimeProvider {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
