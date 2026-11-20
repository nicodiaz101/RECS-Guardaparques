package ar.gob.buenosaires.recs.go2hmi.di

import android.content.Context
import ar.gob.buenosaires.recs.go2hmi.data.local.room.AppDatabase
import ar.gob.buenosaires.recs.go2hmi.data.repository.Go2RobotRepositoryImpl
import ar.gob.buenosaires.recs.go2hmi.data.simulator.Go2SimulationEngine
import ar.gob.buenosaires.recs.go2hmi.domain.repository.Go2RobotRepository
import ar.gob.buenosaires.recs.go2hmi.domain.repository.RobotSimulationController
import ar.gob.buenosaires.recs.go2hmi.domain.util.SystemTimeProvider
import ar.gob.buenosaires.recs.go2hmi.domain.util.TimeProvider

/**
 * Contenedor manual de dependencias compartido a nivel `Application`.
 *
 * Centraliza las instancias únicas (`AppDatabase`, `Go2SimulationEngine` y
 * `Go2RobotRepositoryImpl`) en lugar de crearlas dentro de `MainActivity`,
 * para que no se dupliquen los loops del simulador ni se pierda estado si se recrea la Activity.
 */
class AppContainer(context: Context) {

    val timeProvider: TimeProvider = SystemTimeProvider

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(context.applicationContext)
    }

    private val simulationEngine: Go2SimulationEngine by lazy {
        Go2SimulationEngine(timeProvider = timeProvider)
    }

    private val repositoryImpl: Go2RobotRepositoryImpl by lazy {
        Go2RobotRepositoryImpl(
            dataSource = simulationEngine,
            historyDao = database.historyEventDao(),
            timeProvider = timeProvider
        )
    }

    val robotRepository: Go2RobotRepository
        get() = repositoryImpl

    val simulationController: RobotSimulationController
        get() = repositoryImpl
}
