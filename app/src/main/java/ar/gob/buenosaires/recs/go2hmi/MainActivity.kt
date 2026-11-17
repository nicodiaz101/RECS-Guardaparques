package ar.gob.buenosaires.recs.go2hmi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.gob.buenosaires.recs.go2hmi.data.local.room.AppDatabase
import ar.gob.buenosaires.recs.go2hmi.data.repository.Go2RobotRepositoryImpl
import ar.gob.buenosaires.recs.go2hmi.data.simulator.Go2SimulationEngine
import ar.gob.buenosaires.recs.go2hmi.ui.screens.HistoryLogScreen
import ar.gob.buenosaires.recs.go2hmi.ui.screens.MainSupervisionScreen
import ar.gob.buenosaires.recs.go2hmi.ui.screens.RobotSelectionScreen
import ar.gob.buenosaires.recs.go2hmi.ui.theme.Go2HmiTheme
import ar.gob.buenosaires.recs.go2hmi.ui.viewmodel.OperatorViewModel

private enum class AppDestination(
    val label: String,
    val icon: ImageVector
) {
    UNITS("Unidades", Icons.Rounded.SmartToy),
    SUPERVISION("Supervisión HMI", Icons.Rounded.Dashboard),
    HISTORY("Historial Local", Icons.Rounded.History)
}

class MainActivity : ComponentActivity() {

    private val viewModel: OperatorViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        val engine = Go2SimulationEngine()
        val repository = Go2RobotRepositoryImpl(
            engine = engine,
            historyDao = db.historyEventDao()
        )
        OperatorViewModel.Factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Prohibición de Dynamic Color (dynamicColor = false estricto)
            Go2HmiTheme(dynamicColor = false) {
                RecsGo2HmiApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun RecsGo2HmiApp(viewModel: OperatorViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.UNITS) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                AppDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDestination == dest,
                        onClick = { currentDestination = dest },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label
                            )
                        },
                        label = {
                            Text(
                                text = dest.label,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                AppDestination.UNITS -> RobotSelectionScreen(
                    telemetry = uiState.telemetry,
                    onOpenConsole = { currentDestination = AppDestination.SUPERVISION }
                )

                AppDestination.SUPERVISION -> MainSupervisionScreen(
                    uiState = uiState,
                    onNavigateBackToUnits = { currentDestination = AppDestination.UNITS },
                    onCommandRequested = viewModel::onCommandRequested,
                    onConfirmPendingCommand = viewModel::confirmPendingCommand,
                    onDismissPendingConfirmation = viewModel::dismissPendingConfirmation,
                    onDismissCriticalAlertDialog = viewModel::dismissCriticalAlertDialog,
                    onTriggerScenario = viewModel::triggerScenario,
                    onToggleForcedDisconnection = viewModel::toggleForcedDisconnection,
                    onToggleForceRejectNextCommand = viewModel::toggleForceRejectNextCommand
                )

                AppDestination.HISTORY -> HistoryLogScreen(
                    events = uiState.historyEvents,
                    onClearHistory = viewModel::clearHistory
                )
            }
        }
    }
}
