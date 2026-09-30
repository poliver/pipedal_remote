package com.twoplay.pipedal.di

import com.twoplay.pipedal.model.DeviceConnectionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@EntryPoint
@InstallIn(ActivityComponent::class)
interface DeviceConnectionManagerEntryPoint {
    fun deviceConnectionManager(): DeviceConnectionManager
}
