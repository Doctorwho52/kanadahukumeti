package com.kanadahukumeti

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class CanliTVPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CanliTV())
    }
}