package com.adhkar.app

import android.content.Context

object Prefs {
    private const val FILE = "adhkar_prefs"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)

    fun setEnabled(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("enabled", value).apply()
    }

    // Returns the current index for the given list and advances it, so every unlock shows the next dhikr.
    fun nextIndex(c: Context, key: String, size: Int): Int {
        val i = sp(c).getInt(key, 0) % size
        sp(c).edit().putInt(key, i + 1).apply()
        return i
    }
}
