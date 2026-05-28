package com.example.myapplication

import android.content.Context
import androidx.core.content.edit

object DeviceNameStore {

    private const val PREFS_NAME = "device_custom_names"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private fun normalizeAddress(address: String): String =
        address.trim().uppercase()

    fun get(context: Context, address: String): String? {
        return prefs(context)
            .getString(normalizeAddress(address), null)
    }

    fun save(context: Context, address: String, name: String) {
        val normalizedAddress = normalizeAddress(address)
        val cleanedName = name.trim()

        prefs(context).edit {
            putString(normalizedAddress, cleanedName)
        }
    }

    fun remove(context: Context, address: String) {
        prefs(context).edit {
            remove(normalizeAddress(address))
        }
    }

    fun clearAll(context: Context) {
        prefs(context).edit {
            clear()
        }
    }

    fun hasCustomName(context: Context, address: String): Boolean {
        return prefs(context)
            .contains(normalizeAddress(address))
    }
}