package com.twoplay.pipedal.model

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.twoplay.pipedal.ErrorDialogFragment
import com.twoplay.pipedal.PiPedalApplication
import com.twoplay.pipedal.Preferences
import com.twoplay.pipedal.Promise

private const val TAG = "PiPedalModel"

class Model(application: Application) : AndroidViewModel(application) {

    private val scanner: DeviceScanner = DeviceScanner(this, application)
    private val wifiP2pManager: WifiP2pManager =
        application.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val wifiP2pChannel: WifiP2pManager.Channel =
        wifiP2pManager.initialize(application, application.mainLooper, null)

    var scanState: MutableLiveData<ScanState?> = MutableLiveData(ScanState.Uninitialized)
        private set

    var scanError: MutableLiveData<String?> = MutableLiveData("")
        private set

    private var pageUnloadListener: PageUnloadListener? = null

    private var activity: FragmentActivity? = null

    private var choosingNewDevice = false
    private var isWebViewDisconnected = false
    private var isWebPageValid = false
    private var showPageLoading_ = false

    var serviceConnection: MutableLiveData<DeviceConnection?> = MutableLiveData(null)

    private var currentConnection: DeviceConnection? = null

    override fun onCleared() {
        p2pDisconnect(null)
        super.onCleared()
    }

    fun setPageUnloadListener(listener: PageUnloadListener?) {
        this.pageUnloadListener = listener
    }

    fun connectToDevice(context: Context) {
        val context = context.applicationContext
        val directAddress = Preferences.getConnectionIpAddress(context)

        if (directAddress.isNotEmpty()) {
            setScanState(ScanState.SearchingForInstance)
            WebProbe.checkForPiPedalWebsiteAsync(directAddress)
                .andThen { result ->
                    if (result) {
                        setDirectConnection(directAddress)
                    } else {
                        Preferences.setConnectionIpAddress(context, "")
                        connectToDevice2(context)
                    }
                }
                .andCatch { e: Exception? ->
                    Preferences.setConnectionIpAddress(context, "")
                    connectToDevice2(context)
                }
            return
        }
        connectToDevice2(context)
    }

    private fun connectToDevice2(context: Context?) {
        val selectedInstance = Preferences.getSelectedServerInstanceId(context)

        if (selectedInstance.isEmpty()) {
            scanner.restartScan()
        } else {
            scanner.searchForDevice(selectedInstance)
        }
    }

    fun getDeviceScanner(): DeviceScanner {
        return scanner
    }

    fun stopScan() {
        scanner.stopScan()
    }

    // relayed from the main activity.

    private var pendingTitle: String? = null
    private var pendingError: String? = null

    fun onActivityPause() {
        this.activity = null
    }

    fun onActivityResume(activity: Activity) {
        this.activity = activity as FragmentActivity
        pendingError?.let { showError(it, pendingTitle) }
    }

    @JvmOverloads
    fun setScanState(scanState: ScanState?, errorText: String? = "") {
        this.scanState.value = scanState
        this.scanError.value = errorText
    }

    fun showError(error: String?, title: String?) {
        pendingTitle = title
        pendingError = error

        activity?.let {
            val thisError = pendingError
            val thisTitle = pendingTitle
            pendingError = null
            pendingTitle = null
            ErrorDialogFragment.execute(activity, thisError, thisTitle)
        }
    }

    fun webCallbackChooseNewDevice(activity: Activity?) {
        isWebPageValid = false

        Preferences.removeSelectedServer(PiPedalApplication.getContext())
        currentConnection = null
        scanner.restartScan()
        choosingNewDevice = true
    }

    fun webCallbackOnLostConnection(isDisconnected: Boolean) {
        if (isDisconnected == isWebViewDisconnected) return

        isWebPageValid = !isDisconnected
        isWebViewDisconnected = isDisconnected
        currentConnection = null

        if (isDisconnected) {
            scanner.restartScan()
            choosingNewDevice = false
        } else {
            stopScan()
            setScanState(ScanState.ViewWeb)
        }
    }

    fun onP2pBroadcastReceived(context: Context?, intent: Intent?): Boolean {
        return false
    }

    fun setConnection(connection: PiPedalConnection?) {
        if (connection == null) {
            this.currentConnection = null
            this.isWebPageValid = false
            serviceConnection.value = null
            return
        }

        isWebViewDisconnected = false

        currentConnection.let { captured ->
            if (
                captured == null ||
                    !connection.serviceLocations.any { it.address == captured.address } ||
                    !isWebPageValid
            ) {
                val temp =
                    DeviceConnection(
                        connection.displayName,
                        connection.instanceId,
                        connection.bestConnection,
                    )

                currentConnection = temp

                serviceConnection.value = temp
                Log.d(TAG, "CONNECTION ADDRESS: " + temp.address)
                scanner.stopScan(false)
                setScanState(ScanState.WebViewLoading)
            }
        }
    }

    fun setDirectConnection(ipAddress: String) {
        isWebViewDisconnected = false
        val t = DeviceConnection(ipAddress, "", "http://$ipAddress")
        currentConnection = t
        serviceConnection.value = t
        Log.d(TAG, "CONNECTION ADDRESS: " + t.address)
        scanner.stopScan(false)
        setScanState(ScanState.WebViewLoading)
    }

    fun showPageLoading() = showPageLoading_

    fun showPageLoading(value: Boolean) {
        if (value != showPageLoading_) {
            showPageLoading_ = value
            if (!value and (scanState.getValue() == ScanState.WebViewLoading)) {
                setScanState(ScanState.ViewWeb)
            }
        }
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ActivityCompat.checkSelfPermission(
                    PiPedalApplication.getContext(),
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }
        }
        if (
            ActivityCompat.checkSelfPermission(
                PiPedalApplication.getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun p2pDisconnect(disconnectCallback: DisconnectCallback?) {
        pageUnloadListener
            ?.unloadPage()
            ?.andThen { _ ->
                pageUnloadListener = null
                Log.i(TAG, "Page unloaded.")
                p2pDisconnect(disconnectCallback)
            }
            ?.andCatch { e ->
                Log.e(TAG, "Page unload failed.." + e.message)
                pageUnloadListener = null
                p2pDisconnect(disconnectCallback)
            }

        if (wifiP2pManager != null && wifiP2pChannel != null) {
            // prepare for this to go into gc..
            val wifiP2pManager = this.wifiP2pManager
            val wifiP2pChannel = this.wifiP2pChannel

            if (!hasPermission()) {
                disconnectCallback?.onDisconnected()
                return
            }

            wifiP2pManager.requestGroupInfo(wifiP2pChannel) { group ->
                group?.let {
                    wifiP2pManager.removeGroup(
                        wifiP2pChannel,
                        object : ActionListener {
                            override fun onSuccess() = Unit

                            override fun onFailure(reason: Int) = Unit
                        },
                    )
                }

                disconnectCallback?.onDisconnected()
            }
        }
    }
}

interface PageUnloadListener {
    fun unloadPage(): Promise<Void>
}

interface DisconnectCallback {
    fun onDisconnected()
}
