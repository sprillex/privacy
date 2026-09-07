package com.example.privacy.feature.discovery

import android.app.Application
import org.junit.Assert.assertNotNull
import org.junit.Test

class DiscoveryViewModelTest {

    @Test
    fun discoveryViewModel_hasSingleApplicationConstructor() {
        val constructor = DiscoveryViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(constructor)
    }
}
