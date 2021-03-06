/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus

import android.os.StrictMode
import androidx.preference.PreferenceManager
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.sink.AndroidLogSink
import mozilla.telemetry.glean.Glean
import mozilla.telemetry.glean.config.Configuration
import org.mozilla.focus.GleanMetrics.LegacyIds
import org.mozilla.focus.locale.LocaleAwareApplication
import org.mozilla.focus.search.SearchEngineManager
import org.mozilla.focus.session.VisibilityLifeCycleCallback
import org.mozilla.focus.telemetry.AppStartupTimeMeasurement
import org.mozilla.focus.telemetry.DataUploadPreference
import org.mozilla.focus.telemetry.DataUploadUpdateEvent
import org.mozilla.focus.telemetry.PREF_KEY_TELEMETRY
import org.mozilla.focus.telemetry.TelemetryWrapper
import org.mozilla.focus.utils.AppConstants
import org.mozilla.focus.widget.ServiceLocator
import java.util.UUID

class FocusApplication : LocaleAwareApplication() {

    lateinit var serviceLocator: ServiceLocator
        private set

    var visibilityLifeCycleCallback: VisibilityLifeCycleCallback? = null
        private set

    override fun onCreate() {
        AppStartupTimeMeasurement.applicationOnCreate()
        super.onCreate()

        PreferenceManager.setDefaultValues(this, R.xml.settings, false)

        enableStrictMode()
        Log.addSink(AndroidLogSink())

        serviceLocator = ServiceLocator.getAndInit(this)

        SearchEngineManager.getInstance().init(this)
        TelemetryWrapper.init(this)
        initGlean()
        DataUploadPreference.init(this.applicationContext)

        registerActivityLifecycleCallbacks(VisibilityLifeCycleCallback(this))
    }

    private fun initGlean() {
        DataUploadUpdateEvent.dataUploadLiveData.observeForever { uploadEnabled ->
            Glean.setUploadEnabled(uploadEnabled)
        }

        val telemetryKey = applicationContext.getString(PREF_KEY_TELEMETRY)
        val initialUploadEnabled =
            PreferenceManager.getDefaultSharedPreferences(this).getBoolean(telemetryKey, false)

        Glean.initialize(
            applicationContext = applicationContext,
            uploadEnabled = initialUploadEnabled,
            configuration = Configuration(channel = BuildConfig.BUILD_TYPE)
        )

        val legacyId = TelemetryWrapper.clientId
        LegacyIds.clientId.set(UUID.fromString(legacyId))
    }

    private fun enableStrictMode() {
        // Android/WebView sometimes commit strict mode violations, see e.g.
        // https://github.com/mozilla-mobile/focus-android/issues/660
        if (AppConstants.isReleaseBuild()) {
            return
        }

        val threadPolicyBuilder = StrictMode.ThreadPolicy.Builder().detectAll()
        val vmPolicyBuilder = StrictMode.VmPolicy.Builder().detectAll()

        threadPolicyBuilder.penaltyDialog()
        vmPolicyBuilder.penaltyLog()

        StrictMode.setThreadPolicy(threadPolicyBuilder.build())
        StrictMode.setVmPolicy(vmPolicyBuilder.build())
    }

    override fun onLowMemory() {
        super.onLowMemory()
        //        OkHttpWrapper.onLowMemory(); // Disabled because it throws NPE.
        // If you need to dump more memory, you may be able to clear the Picasso cache.
    }
}
