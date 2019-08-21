package me.retrodaredevil.solarthing.android.prefs

import android.content.Context
import me.retrodaredevil.couchdb.CouchProperties
import me.retrodaredevil.couchdb.CouchPropertiesBuilder

@Deprecated("Use profile interfaces instead")
class Prefs(private val context: Context) {
//    private val connectionPreferences by lazy { context.getSharedPreferences("connection_properties", 0) }
    private val settings by lazy { context.getSharedPreferences("settings", 0) }

    var maxFragmentTimeMinutes: Float
        get() = settings.getFloat(
            SaveKeys.maxFragmentTimeMinutes,
            DefaultOptions.maxFragmentTimeMinutes
        )
        set(value) = settings.edit().putFloat(SaveKeys.maxFragmentTimeMinutes, value).apply()


    var startOnBoot: Boolean
        get() = settings.getBoolean(
            SaveKeys.startOnBoot,
            DefaultOptions.startOnBoot
        )
        set(value) = settings.edit().putBoolean(SaveKeys.startOnBoot, value).apply()

}