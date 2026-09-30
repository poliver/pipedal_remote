package com.twoplay.pipedal.model

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.twoplay.pipedal.Preferences
import com.twoplay.pipedal.Promise
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

private const val TAG = "DeviceConnectionManager"

/**
 * Coordinates PiPedal server discovery, selection, and the connection used by the WebView.
 *
 * Observe the exposed state to keep the UI in sync with scanning and connection changes. A
 * connection can come from a discovered server or from a configured direct IP address.
 */
interface DeviceConnectionManager : DeviceScanner.Listener {
    val scanState: MutableLiveData<ScanState>
    val piPedalDevices: LiveData<List<PiPedalConnection>>
    val deviceStatusChanges: LiveData<PiPedalConnection>
    val scanForDeviceMessage: LiveData<String>
    val scanError: MutableLiveData<String>
    val serviceConnection: MutableLiveData<DeviceConnection?>
    val showPageLoading: Boolean

    fun connectToDevice()

    fun restartScan()

    fun stopScan()

    fun setPageUnloadListener(listener: PageUnloadListener?)

    fun webCallbackChooseNewDevice()

    fun webCallbackOnLostConnection(isDisconnected: Boolean)

    fun setConnection(connection: PiPedalConnection?)

    fun setDirectConnection(ipAddress: String)

    fun showPageLoading(value: Boolean)

    fun p2pDisconnect(disconnectCallback: DisconnectCallback?)

    fun close()
}

@ActivityRetainedScoped
class DeviceConnectionManagerImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val deviceScanner: DeviceScanner,
) : DeviceConnectionManager {

    private val mutableDeviceStatusChanges = MutableLiveData<PiPedalConnection>()

    override val deviceStatusChanges: LiveData<PiPedalConnection>
        get() = mutableDeviceStatusChanges

    init {
        deviceScanner.setListener(this)
        deviceScanner.setStatusChangedListener { connection ->
            mutableDeviceStatusChanges.postValue(connection)
        }
    }

    override val scanState: MutableLiveData<ScanState>
        get() = deviceScanner.scanState

    override val scanError: MutableLiveData<String>
        get() = deviceScanner.scanError

    override val piPedalDevices: LiveData<List<PiPedalConnection>>
        get() = deviceScanner.piPedalDevices

    override val scanForDeviceMessage: LiveData<String>
        get() = deviceScanner.scanForDeviceMessage

    override val serviceConnection = MutableLiveData<DeviceConnection?>(null)

    private val wifiP2pManager = context.getSystemService<WifiP2pManager>()
    private val wifiP2pChannel = wifiP2pManager?.initialize(context, context.mainLooper, null)

    private var pageUnloadListener: PageUnloadListener? = null
    private var isWebViewDisconnected = false
    private var isWebPageValid = false
    override var showPageLoading = false
        private set

    private var currentConnection: DeviceConnection? = null

    override fun connectToDevice() {
        val directAddress = Preferences.getConnectionIpAddress(context)

        if (directAddress.isNotEmpty()) {
            deviceScanner.setScanState(ScanState.SearchingForInstance)
            WebProbe.checkForPiPedalWebsiteAsync(directAddress)
                .andThen { result ->
                    if (result) {
                        setDirectConnection(directAddress)
                    } else {
                        Preferences.setConnectionIpAddress(context, "")
                        connectToDeviceFromPreferences()
                    }
                }
                .andCatch {
                    Preferences.setConnectionIpAddress(context, "")
                    connectToDeviceFromPreferences()
                }
            return
        }
        connectToDeviceFromPreferences()
    }

    private fun connectToDeviceFromPreferences() {
        val selectedInstance = Preferences.getSelectedServerInstanceId(context)

        if (selectedInstance.isEmpty()) {
            deviceScanner.restartScan()
        } else {
            deviceScanner.searchForDevice(selectedInstance)
        }
    }

    override fun restartScan() {
        deviceScanner.restartScan()
    }

    override fun stopScan() {
        deviceScanner.stopScan()
    }

    override fun setPageUnloadListener(listener: PageUnloadListener?) {
        pageUnloadListener = listener
    }

    override fun webCallbackChooseNewDevice() {
        isWebPageValid = false
        Preferences.removeSelectedServer(context)
        currentConnection = null
        deviceScanner.restartScan()
    }

    override fun webCallbackOnLostConnection(isDisconnected: Boolean) {
        if (isDisconnected == isWebViewDisconnected) return

        isWebPageValid = !isDisconnected
        isWebViewDisconnected = isDisconnected
        currentConnection = null

        if (isDisconnected) {
            deviceScanner.restartScan()
        } else {
            stopScan()
            deviceScanner.setScanState(ScanState.ViewWeb)
        }
    }

    override fun onDeviceConnectionFound(connection: PiPedalConnection) {
        setConnection(connection)
    }

    override fun setConnection(connection: PiPedalConnection?) {
        if (connection == null) {
            currentConnection = null
            isWebPageValid = false
            serviceConnection.value = null
            return
        }

        isWebViewDisconnected = false

        val captured = currentConnection
        if (
            captured == null ||
                !connection.serviceLocations.any { it.address == captured.address } ||
                !isWebPageValid
        ) {
            val updatedConnection =
                DeviceConnection(
                    connection.displayName,
                    connection.instanceId,
                    connection.bestConnection,
                )

            currentConnection = updatedConnection
            serviceConnection.value = updatedConnection
            Log.d(TAG, "CONNECTION ADDRESS: " + updatedConnection.address)
            deviceScanner.stopScan(false)
            deviceScanner.setScanState(ScanState.WebViewLoading)
        }
    }

    override fun setDirectConnection(ipAddress: String) {
        isWebViewDisconnected = false
        val connection = DeviceConnection(ipAddress, "", "http://$ipAddress")
        currentConnection = connection
        serviceConnection.value = connection
        Log.d(TAG, "CONNECTION ADDRESS: " + connection.address)
        deviceScanner.stopScan(false)
        deviceScanner.setScanState(ScanState.WebViewLoading)
    }

    override fun showPageLoading(value: Boolean) {
        if (value != showPageLoading) {
            showPageLoading = value
            if (!value && scanState.value == ScanState.WebViewLoading) {
                deviceScanner.setScanState(ScanState.ViewWeb)
            }
        }
    }

    private fun hasPermission(): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override fun p2pDisconnect(disconnectCallback: DisconnectCallback?) {
        val unloadListener = pageUnloadListener
        if (unloadListener != null) {
            unloadListener
                .unloadPage()
                .andThen {
                    if (pageUnloadListener === unloadListener) {
                        pageUnloadListener = null
                    }
                    Log.i(TAG, "Page unloaded.")
                    disconnectWifiDirect(disconnectCallback)
                }
                .andCatch { error ->
                    if (pageUnloadListener === unloadListener) {
                        pageUnloadListener = null
                    }
                    Log.e(TAG, "Page unload failed: " + error.message)
                    disconnectWifiDirect(disconnectCallback)
                }
            return
        }

        disconnectWifiDirect(disconnectCallback)
    }

    @SuppressLint("MissingPermission")
    private fun disconnectWifiDirect(disconnectCallback: DisconnectCallback?) {
        val manager = wifiP2pManager
        val channel = wifiP2pChannel
        if (manager == null || channel == null) {
            disconnectCallback?.onDisconnected()
            return
        }

        if (!hasPermission()) {
            disconnectCallback?.onDisconnected()
            return
        }

        manager.requestGroupInfo(channel) { group ->
            group?.let {
                manager.removeGroup(
                    channel,
                    object : WifiP2pManager.ActionListener {
                        override fun onSuccess() = Unit

                        override fun onFailure(reason: Int) = Unit
                    },
                )
            }
            disconnectCallback?.onDisconnected()
        }
    }

    override fun close() {
        deviceScanner.setStatusChangedListener(null)
        p2pDisconnect(null)
        deviceScanner.close()
    }
}

fun interface PageUnloadListener {
    fun unloadPage(): Promise<Void>
}

fun interface DisconnectCallback {
    fun onDisconnected()
}
