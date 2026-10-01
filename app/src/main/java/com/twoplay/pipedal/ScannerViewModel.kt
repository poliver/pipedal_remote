package com.twoplay.pipedal

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.twoplay.pipedal.model.ConnectionStatus
import com.twoplay.pipedal.model.DeviceConnectionManager
import com.twoplay.pipedal.model.DisconnectCallback
import com.twoplay.pipedal.model.PiPedalConnection
import com.twoplay.pipedal.model.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Holds scanner presentation state and scan actions independently of the Fragment's view system.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(private val connectionManager: DeviceConnectionManager) :
    ViewModel() {
    private val initialScanState = connectionManager.scanState.value ?: ScanState.Uninitialized

    private val pendingCancellationDeviceId = MutableStateFlow<Long?>(null)

    val uiStateFlow: StateFlow<ScannerScreenUiState> =
        combine(
            connectionManager.scanState.asFlow(),
            connectionManager.piPedalDevices.asFlow(),
            connectionManager.scanError.asFlow(),
            connectionManager.deviceStatusChanges.asFlow().map { }.onStart { emit(Unit) },
            pendingCancellationDeviceId,
        ) { scanState, devices, scanError, _, pendingCancellationDeviceId ->
            val screenState =
                buildUiState(
                    scanState = scanState ?: ScanState.Uninitialized,
                    devices = devices,
                    scanError = scanError.orEmpty(),
                )
            ScannerScreenUiState(
                screen = screenState,
                pendingCancellationDeviceId = pendingCancellationDeviceId,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue =
                    ScannerScreenUiState(
                        screen = buildUiState(
                            scanState = initialScanState,
                            devices = connectionManager.piPedalDevices.value,
                            scanError = connectionManager.scanError.value.orEmpty(),
                        ),
                        pendingCancellationDeviceId = null,
                    ),
            )

    fun restartScan() {
        connectionManager.restartScan()
    }

    fun stopScan() {
        connectionManager.stopScan()
    }

    fun disconnect(callback: DisconnectCallback) {
        connectionManager.p2pDisconnect(callback)
    }

    fun setDirectConnection(ipAddress: String) {
        connectionManager.setDirectConnection(ipAddress)
    }

    fun onConnectionClicked(deviceId: Long) {
        val connection =
            connectionManager.piPedalDevices.value?.firstOrNull { it.id() == deviceId }
                ?: run {
                    pendingCancellationDeviceId.value = null
                    return
                }

        when (connection.status) {
            ConnectionStatus.AvailableOnLocalNetwork,
            ConnectionStatus.Connected -> {
                pendingCancellationDeviceId.value = null
                connectionManager.setConnection(connection)
            }

            ConnectionStatus.Connecting,
            ConnectionStatus.WaitingForIpAddress,
            ConnectionStatus.ConnectedNoServiceAddress -> {
                pendingCancellationDeviceId.value = deviceId
            }

            ConnectionStatus.NotConnected,
            ConnectionStatus.Failed,
            ConnectionStatus.Unavailable -> {
                pendingCancellationDeviceId.value = null
            }
        }
    }

    fun dismissCancellationPrompt() {
        // TODO This was a no-op prior to the Compose migration (only a confirmation dialog was
        // shown), and that behavior has been preserved here.
        pendingCancellationDeviceId.value = null
    }

    private fun buildUiState(
        scanState: ScanState,
        devices: List<PiPedalConnection>?,
        scanError: String,
    ): ScannerUiState {
        val mappedDevices =
            devices.orEmpty().map {
                ScannedDevice(
                    id = it.id(),
                    instanceId = it.instanceId,
                    displayName = it.displayName,
                    status = it.status,
                )
            }
        val hasDevices = mappedDevices.isNotEmpty()

        return when (scanState) {
            ScanState.SearchingForInstance ->
                ScannerUiState.Searching.ForInstance

            ScanState.Searching ->
                if (hasDevices) {
                    ScannerUiState.DeviceList(mappedDevices)
                } else {
                    ScannerUiState.Searching.Default
                }

            ScanState.ErrorState ->
                ScannerUiState.Error.ScanError(message = scanError)

            else ->
                if (hasDevices) {
                    ScannerUiState.DeviceList(mappedDevices)
                } else {
                    ScannerUiState.Error.NoDevicesFound
                }
        }
    }

    data class ScannerScreenUiState(
        val screen: ScannerUiState,
        val pendingCancellationDeviceId: Long?,
    )

    data class ScannedDevice(
        val id: Long,
        val instanceId: String?,
        val displayName: String?,
        val status: ConnectionStatus,
    )

    sealed class ScannerUiState {
        @get:StringRes
        open val captionResId: Int = R.string.select_a_device_to_connect_to

        sealed class Searching : ScannerUiState() {
            @get:StringRes
            open val messageResId: Int = R.string.searching

            data object Default : Searching()

            data object ForInstance : Searching() {
                @StringRes
                override val captionResId: Int = R.string.reconnecting
                @StringRes
                override val messageResId: Int = R.string.searching_for_device
            }
        }

        data class DeviceList(val devices: List<ScannedDevice>) : ScannerUiState()

        sealed class Error : ScannerUiState() {
            data class ScanError(
                val message: String,
            ) : Error()

            data object NoDevicesFound : Error()
        }
    }
}
