package me.retrodaredevil.solarthing.android.prefs.saving

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import me.retrodaredevil.solarthing.android.prefs.DefaultOptions
import me.retrodaredevil.solarthing.android.prefs.MiscProfile
import me.retrodaredevil.solarthing.android.prefs.ProfileHolder
import me.retrodaredevil.solarthing.android.prefs.SaveKeys

class PreferencesMiscProfileHolder
/**
 * @param settings The shared preferences instance
 * @param context The context used to check if fine location is accessible or null to prevent that check
 */
constructor(
        private val settings: SharedPreferences,
        private val context: Context?
) : ProfileHolder<MiscProfile> {
    override var profile: MiscProfile
        get() {
            val networkSwitchingEnabled =
                if(context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) false
                else settings.getBoolean(SaveKeys.networkSwitchingEnabled, DefaultOptions.networkSwitchingEnabled)

            return MiscProfile(
                    settings.getFloat(
                            SaveKeys.maxFragmentTimeMinutes,
                            DefaultOptions.maxFragmentTimeMinutes
                    ),
                    settings.getBoolean(
                            SaveKeys.startOnBoot,
                            DefaultOptions.startOnBoot
                    ),
                    networkSwitchingEnabled,
                    DefaultOptions.temperatureUnit
            )
        }
        set(value) {
            settings.edit().putFloat(SaveKeys.maxFragmentTimeMinutes, value.maxFragmentTimeMinutes).apply()
            settings.edit().putBoolean(SaveKeys.startOnBoot, value.startOnBoot).apply()
            settings.edit().putBoolean(SaveKeys.networkSwitchingEnabled, value.networkSwitchingEnabled).apply()
//            println("New value would have been $value")
        }

}
