package com.twoplay.pipedal

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.twoplay.pipedal.model.ConnectionStatus
import com.twoplay.pipedal.model.DeviceConnectionManager
import com.twoplay.pipedal.model.DisconnectCallback
import com.twoplay.pipedal.model.PiPedalConnection
import com.twoplay.pipedal.model.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.ArrayList
import java.util.Collections
import javax.inject.Inject
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
    private val initialUiState =
        buildUiState(
            scanState = initialScanState,
            devices = connectionManager.piPedalDevices.value,
            scanError = connectionManager.scanError.value.orEmpty(),
            previousCaption = ScannerUiState.Caption.SELECT_DEVICE,
        )
    private var lastCaption = initialUiState.caption

    val uiStateFlow: StateFlow<ScannerUiState> =
        combine(
                connectionManager.scanState.asFlow(),
                connectionManager.piPedalDevices.asFlow(),
                connectionManager.scanError.asFlow(),
                connectionManager.deviceStatusChanges.asFlow().map { Unit }.onStart { emit(Unit) },
            ) { scanState, devices, scanError, _ ->
                buildUiState(
                        scanState = scanState ?: ScanState.Uninitialized,
                        devices = devices,
                        scanError = scanError.orEmpty(),
                        previousCaption = lastCaption,
                    )
                    .also { state ->
                        lastCaption = state.caption
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = initialUiState,
            )

    // The Java Fragment observes this until its UI is migrated to Compose.
    val uiState: LiveData<ScannerUiState> = uiStateFlow.asLiveData()

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

    fun onConnectionClicked(deviceId: Long): ConnectionClickAction {
        val connection =
            connectionManager.piPedalDevices.value?.firstOrNull { it.id() == deviceId }
                ?: return ConnectionClickAction.NONE

        return when (connection.status) {
            ConnectionStatus.AvailableOnLocalNetwork,
            ConnectionStatus.Connected -> {
                connectionManager.setConnection(connection)
                ConnectionClickAction.NONE
            }
            ConnectionStatus.Connecting,
            ConnectionStatus.WaitingForIpAddress,
            ConnectionStatus.ConnectedNoServiceAddress -> ConnectionClickAction.PROMPT_TO_CANCEL
            ConnectionStatus.NotConnected,
            ConnectionStatus.Failed,
            ConnectionStatus.Unavailable -> ConnectionClickAction.NONE
        }
    }

    private fun buildUiState(
        scanState: ScanState,
        devices: List<PiPedalConnection>?,
        scanError: String,
        previousCaption: ScannerUiState.Caption,
    ): ScannerUiState {
        val hasDevices = !devices.isNullOrEmpty()
        var caption = previousCaption
        var content: ScannerUiState.Content
        var searchingMessage = ScannerUiState.SearchingMessage.SEARCHING
        var errorMessage = ScannerUiState.ErrorMessage.NONE

        when (scanState) {
            ScanState.SearchingForInstance -> {
                caption = ScannerUiState.Caption.RECONNECTING
                content = ScannerUiState.Content.SEARCHING
                searchingMessage = ScannerUiState.SearchingMessage.SEARCHING_FOR_DEVICE
            }
            ScanState.Searching -> {
                caption = ScannerUiState.Caption.SELECT_DEVICE
                content =
                    if (hasDevices) {
                        ScannerUiState.Content.DEVICE_LIST
                    } else {
                        ScannerUiState.Content.SEARCHING
                    }
            }
            ScanState.ErrorState -> {
                content = ScannerUiState.Content.ERROR
                errorMessage = ScannerUiState.ErrorMessage.SCAN_ERROR
            }
            else -> {
                caption = ScannerUiState.Caption.SELECT_DEVICE
                if (hasDevices) {
                    content = ScannerUiState.Content.DEVICE_LIST
                } else {
                    content = ScannerUiState.Content.ERROR
                    errorMessage = ScannerUiState.ErrorMessage.NO_DEVICES_FOUND
                }
            }
        }

        return ScannerUiState(
            devices = copyDevices(devices),
            content = content,
            caption = caption,
            searchingMessage = searchingMessage,
            errorMessage = errorMessage,
            scanError = scanError,
            showCancelButton = content == ScannerUiState.Content.SEARCHING,
        )
    }

    private fun copyDevices(devices: List<PiPedalConnection>?): List<ScannerDeviceUiState> {
        if (devices.isNullOrEmpty()) {
            return Collections.emptyList()
        }
        return Collections.unmodifiableList(
            ArrayList(
                devices.map { device ->
                    ScannerDeviceUiState(
                        id = device.id(),
                        instanceId = device.instanceId,
                        displayName = device.displayName,
                        status = device.status,
                    )
                }
            )
        )
    }

    enum class ConnectionClickAction {
        NONE,
        PROMPT_TO_CANCEL,
    }

    data class ScannerDeviceUiState(
        val id: Long,
        val instanceId: String?,
        val displayName: String?,
        val status: ConnectionStatus,
    )

    data class ScannerUiState(
        val devices: List<ScannerDeviceUiState>,
        val content: Content,
        val caption: Caption,
        val searchingMessage: SearchingMessage,
        val errorMessage: ErrorMessage,
        val scanError: String,
        val showCancelButton: Boolean,
    ) {
        enum class Content {
            SEARCHING,
            DEVICE_LIST,
            ERROR,
        }

        enum class Caption {
            SELECT_DEVICE,
            RECONNECTING,
        }

        enum class SearchingMessage {
            SEARCHING,
            SEARCHING_FOR_DEVICE,
        }

        enum class ErrorMessage {
            NONE,
            SCAN_ERROR,
            NO_DEVICES_FOUND,
        }
    }
}
