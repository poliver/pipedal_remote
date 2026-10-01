package com.twoplay.pipedal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twoplay.pipedal.model.WebProbe
import com.twoplay.pipedal.shim.createComposeView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ScannerFragment : Fragment(), IpAddressDialogFragment.IpAddressDialogFragmentResult {
    private val viewModel: ScannerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val hasWifiSettingsHandler = requireActivity().hasWifiSettingsHandler()

        return createComposeView {
            val uiState by viewModel.uiStateFlow.collectAsStateWithLifecycle()

            ScannerScreen(
                uiState = uiState,
                wifiSettingsAvailable = hasWifiSettingsHandler,
                onBack = ::disconnectAndFinish,
                onOpenWifiSettings = { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                onOpenIpAddress = {
                    IpAddressDialogFragment.execute(
                        this,
                        Preferences.getConnectionIpAddress(requireContext()),
                    )
                },
                onHelp = { HelpDialogFragment.execute(this) },
                onRestartScan = viewModel::restartScan,
                onStopScan = viewModel::stopScan,
                onDeviceClick = viewModel::onConnectionClicked,
                onDismissCancellationPrompt = viewModel::dismissCancellationPrompt,
            )
        }
    }

    override fun onResume() {
        super.onResume()

        val currentContext = context ?: return
        if (!Preferences.getDontshowHelpAgain(currentContext)) {
            Preferences.setDontShowHelpAgain(currentContext, true)
            HelpDialogFragment.execute(this)
        }
    }

    private fun disconnectAndFinish() {
        if (activity == null) {
            return
        }
        viewModel.disconnect {
            activity?.finish()
        }
    }

    override fun onIpAddressResult(ipAddress: String) {
        val webUrl = "  http://$ipAddress"
        try {
            WebProbe.checkForPiPedalWebsiteAsync(ipAddress)
                .andThen { result ->
                    if (isAdded) {
                        if (result) {
                            context?.let { currentContext ->
                                Preferences.setConnectionIpAddress(currentContext, ipAddress)
                                viewModel.setDirectConnection(ipAddress)
                            }
                        } else {
                            ErrorDialogFragment.execute(
                                this,
                                "PiPedal web server not found at that address.\n\n$webUrl",
                                "Error",
                            )
                        }
                    }
                }
                .andCatch { exception ->
                    if (isAdded) {
                        ErrorDialogFragment.execute(
                            this,
                            exception.message + "\n\n" + webUrl,
                            "Error",
                        )
                    }
                }
        } catch (_: Exception) {
            ErrorDialogFragment.execute(
                this,
                "PiPedal web server not found at that address.",
                "Error",
            )
        }

        Preferences.setConnectionIpAddress(requireContext(), ipAddress)
    }
}

private fun Context.hasWifiSettingsHandler() =
    packageManager
        .queryIntentActivities(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        .isNotEmpty()
