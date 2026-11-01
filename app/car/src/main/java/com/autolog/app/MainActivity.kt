package com.autolog.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.autolog.app.domain.EndpointType
import com.autolog.app.pairing.PairingController
import com.autolog.app.ui.AutoLogTheme
import com.autolog.app.ui.ConsentDialog
import com.autolog.app.ui.DriverPickerDialog
import com.autolog.app.ui.HomeActions
import com.autolog.app.ui.HomeScreen
import com.autolog.app.ui.HomeState
import com.autolog.app.ui.MainViewModel
import com.autolog.app.ui.PairingScreen
import com.autolog.app.ui.RepairWarningDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.onAppVisible() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoLogTheme {
                Surface(Modifier.fillMaxSize()) { AppContent(viewModel) }
            }
        }
        requestMissingPermissions()
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppVisible()
    }

    /** 위치(Pleos FusedLocation)·마이크(도착 확인 응답)·알림(기록 상태). */
    private fun requestMissingPermissions() {
        val wanted = listOf(FUSED_LOCATION, Manifest.permission.RECORD_AUDIO) +
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        val needed = wanted
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private companion object {
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
    }
}

@Composable
private fun AppContent(viewModel: MainViewModel) {
    val paired by viewModel.paired.collectAsState()
    val pairingState by viewModel.pairing.state.collectAsState()
    var repairing by remember { mutableStateOf(false) }
    val message by viewModel.message.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(pairingState) {
        if (pairingState is PairingController.State.Paired) repairing = false
    }

    when {
        paired == null -> Unit
        paired == false || repairing -> PairingScreen(
            state = pairingState,
            onStart = viewModel::startPairing,
            onCancel = if (repairing) ({ viewModel.pairing.cancel(); repairing = false }) else null,
        )
        else -> Home(viewModel, onRepair = { repairing = true; viewModel.startPairing() })
    }
}

@Composable
private fun Home(viewModel: MainViewModel, onRepair: () -> Unit) {
    val vehicle by viewModel.vehicle.collectAsState()
    val driver by viewModel.currentDriver.collectAsState()
    val drivers by viewModel.drivers.collectAsState()
    val status by viewModel.recorderStatus.collectAsState()
    val pending by viewModel.pendingCount.collectAsState()
    val unconfirmed by viewModel.unconfirmed.collectAsState()
    val recent by viewModel.recentTrips.collectAsState()
    val places by viewModel.places.collectAsState()
    val consentFor by viewModel.consentFor.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var repairWarning by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    HomeScreen(
        state = HomeState(
            vehicle = vehicle,
            driver = driver,
            status = status,
            pendingCount = pending,
            unconfirmed = unconfirmed,
            recent = recent,
            homeRegistered = places.any { it.type == EndpointType.HOME },
        ),
        actions = HomeActions(
            pickDriver = { picking = true },
            choosePurpose = { trip, purpose -> viewModel.choosePurpose(trip, purpose) },
            registerHome = viewModel::registerHomeHere,
            exportCsv = viewModel::exportThisMonth,
            repair = { scope.launch { repairWarning = viewModel.pendingBeforeRepair() } },
        ),
    )
    if (picking) {
        DriverPickerDialog(
            drivers = drivers,
            currentId = driver?.id,
            onPick = { picked ->
                picking = false
                viewModel.selectDriver(picked)
            },
            onDismiss = { picking = false },
        )
    }
    consentFor?.let { ConsentDialog(it, viewModel::acceptConsent, viewModel::declineConsent) }
    if (repairWarning >= 0) {
        RepairWarningDialog(
            pending = repairWarning,
            onConfirm = {
                repairWarning = -1
                onRepair()
            },
            onDismiss = { repairWarning = -1 },
        )
    }
}
