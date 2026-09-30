package com.twoplay.pipedal.model

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class Model @Inject constructor(private val deviceConnectionManager: DeviceConnectionManager) :
    ViewModel() {

    val scanState: MutableLiveData<ScanState>
        get() = deviceConnectionManager.scanState

    val serviceConnection: MutableLiveData<DeviceConnection?>
        get() = deviceConnectionManager.serviceConnection

    fun connectToDevice() {
        deviceConnectionManager.connectToDevice()
    }

    fun stopScan() {
        deviceConnectionManager.stopScan()
    }

    fun p2pDisconnect(disconnectCallback: DisconnectCallback?) {
        deviceConnectionManager.p2pDisconnect(disconnectCallback)
    }

    override fun onCleared() {
        deviceConnectionManager.close()
        super.onCleared()
    }
}
