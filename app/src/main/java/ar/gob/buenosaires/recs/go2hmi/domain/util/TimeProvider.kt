package ar.gob.buenosaires.recs.go2hmi.domain.util

/**
 * Abstracción de reloj del dominio.
 *
 * Permite desacoplar el cálculo de tiempos transcurridos (datos antiguos / timeouts)
 * de `System.currentTimeMillis()`, facilitando pruebas unitarias determinísticas
 * sin depender del reloj real del sistema operativo.
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
