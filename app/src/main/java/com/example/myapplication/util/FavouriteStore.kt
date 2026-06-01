package com.example.myapplication.util

import android.content.Context
import androidx.core.content.edit

object FavoriteStore {
    private const val PREF_NAME = "device_favorites"

    fun isFavorite(context: Context, address: String): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(address, false)

    fun toggle(context: Context, address: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val new = !prefs.getBoolean(address, false)
        prefs.edit { putBoolean(address, new) }
        return new
    }
}