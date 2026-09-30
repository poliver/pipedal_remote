package com.twoplay.pipedal.di

import com.twoplay.pipedal.model.DeviceConnectionManager
import com.twoplay.pipedal.model.DeviceConnectionManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

@Module
@InstallIn(ActivityRetainedComponent::class)
interface DeviceConnectionManagerModule {
    @Binds
    fun bindDeviceConnectionManager(impl: DeviceConnectionManagerImpl): DeviceConnectionManager
}
