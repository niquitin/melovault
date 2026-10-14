package com.bsdung.musicassistantcar

import android.app.Application
import com.bsdung.musicassistantcar.data.AndroidVpnRouteVerifier

class MeloVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidVpnRouteVerifier.initialize(applicationContext)
    }
}
