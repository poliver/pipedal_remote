package com.twoplay.pipedal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import com.twoplay.pipedal.model.ConnectionStatus
import com.twoplay.pipedal.ScannerViewModel.ScannedDevice
import com.twoplay.pipedal.ScannerViewModel.ScannerScreenUiState
import com.twoplay.pipedal.ScannerViewModel.ScannerUiState
import com.twoplay.pipedal.theme.ThemeWrapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    uiState: ScannerScreenUiState,
    wifiSettingsAvailable: Boolean,
    onBack: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenIpAddress: () -> Unit,
    onHelp: () -> Unit,
    onRestartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (Long) -> Unit,
    onDismissCancellationPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = uiState.screen

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_black_24dp),
                            contentDescription = stringResource(R.string.close_button_description),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            ScanningPanel(
                state = state,
                wifiSettingsAvailable = wifiSettingsAvailable,
                onOpenWifiSettings = onOpenWifiSettings,
                onOpenIpAddress = onOpenIpAddress,
                onHelp = onHelp,
                onRestartScan = onRestartScan,
                onStopScan = onStopScan,
                onDeviceClick = onDeviceClick,
            )
        }
    }

    uiState.pendingCancellationDeviceId?.let {
        AlertDialog(
            onDismissRequest = onDismissCancellationPrompt,
            text = {
                Text("Do you want to cancel the connection attempt?")
            },
            confirmButton = {
                TextButton(onClick = onDismissCancellationPrompt) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCancellationPrompt) {
                    Text("No")
                }
            },
        )
    }
}

@Composable
private fun ScanningPanel(
    state: ScannerUiState,
    wifiSettingsAvailable: Boolean,
    onOpenWifiSettings: () -> Unit,
    onOpenIpAddress: () -> Unit,
    onHelp: () -> Unit,
    onRestartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (Long) -> Unit,
) {
    Surface(color = Color.Transparent, contentColor = contentColorFor(MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            Text(
                text = stringResource(state.captionResId),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    is ScannerUiState.Searching -> SearchingContent(state)
                    is ScannerUiState.DeviceList -> DeviceListContent(state.devices, onDeviceClick)
                    is ScannerUiState.Error ->
                        ErrorContent(
                            state = state,
                            wifiSettingsAvailable = wifiSettingsAvailable,
                            onOpenWifiSettings = onOpenWifiSettings,
                            onOpenIpAddress = onOpenIpAddress,
                        )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onHelp) {
                    Icon(
                        painter = painterResource(R.drawable.ic_help_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.help))
                }

                Spacer(Modifier.weight(1f))

                if (state is ScannerUiState.Searching) {
                    TextButton(onClick = onStopScan) {
                        Text(stringResource(R.string.cancel))
                    }
                } else {
                    TextButton(onClick = onRestartScan) {
                        Text(stringResource(R.string.scan_again))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchingContent(state: ScannerUiState.Searching) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(state.messageResId),
            modifier = Modifier.alpha(0.75f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceListContent(
    devices: List<ScannedDevice>,
    onDeviceClick: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = devices,
            key = ScannedDevice::id,
        ) { device ->
            DeviceCard(device = device, onClick = { onDeviceClick(device.id) })
        }
    }
}

@Composable
private fun DeviceCard(
    device: ScannedDevice,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseOnSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.inverseSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_wifi_normal_black_24dp),
                    contentDescription = stringResource(R.string.device_content_description),
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }

            Spacer(Modifier.width(32.dp))

            Text(
                text = device.displayName.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun ErrorContent(
    state: ScannerUiState.Error,
    wifiSettingsAvailable: Boolean,
    onOpenWifiSettings: () -> Unit,
    onOpenIpAddress: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val errorText =
            when (state) {
                is ScannerUiState.Error.ScanError -> state.message
                ScannerUiState.Error.NoDevicesFound -> stringResource(R.string.no_devices_found)
            }

        Text(
            text = errorText,
            modifier = Modifier
                .fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.connect_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(16.dp))

        if (wifiSettingsAvailable) {
            TextButton(onClick = onOpenWifiSettings) {
                Text(stringResource(R.string.connect_to_wi_fi_network))
            }
        }

        TextButton(onClick = onOpenIpAddress) {
            Text(stringResource(R.string.connect_to_ip_address))
        }
    }
}

@PreviewWrapper(ThemeWrapper::class)
@Preview(name = "Searching", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ScannerSearchingPreview() {
    ScannerScreenPreviewContent(
        ScannerUiState.Searching.Default
    )
}

@PreviewWrapper(ThemeWrapper::class)
@Preview(name = "Device list", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ScannerDeviceListPreview() {
    ScannerScreenPreviewContent(
        ScannerUiState.DeviceList(
            listOf(
                ScannedDevice(
                    id = 1L,
                    instanceId = "pipedal-studio",
                    displayName = "PiPedal Studio",
                    status = ConnectionStatus.AvailableOnLocalNetwork,
                ),
                ScannedDevice(
                    id = 2L,
                    instanceId = "pipedal-booth",
                    displayName = "PiPedal Booth",
                    status = ConnectionStatus.AvailableOnLocalNetwork,
                ),
            )
        )
    )
}

@PreviewWrapper(ThemeWrapper::class)
@Preview(name = "No devices found", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ScannerNoDevicesPreview() {
    ScannerScreenPreviewContent(ScannerUiState.Error.NoDevicesFound)
}

@Composable
private fun ScannerScreenPreviewContent(
    state: ScannerUiState
) {
        ScannerScreen(
            uiState =
                ScannerScreenUiState(
                    screen = state,
                    pendingCancellationDeviceId = null,
                ),
            wifiSettingsAvailable = false,
            onBack = {},
            onOpenWifiSettings = {},
            onOpenIpAddress = {},
            onHelp = {},
            onRestartScan = {},
            onStopScan = {},
            onDeviceClick = {},
            onDismissCancellationPrompt = {},
            modifier = Modifier.paint(
                painter = painterResource(id = R.drawable.bg_image),
                contentScale = ContentScale.Crop
            )
        )
}
