package com.example.firebaselabelapp.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "AppPrinterSettings"
        private const val KEY_PRINTER_ADDRESS = "selected_printer_address"
        private const val KEY_DENSITY = "selected_density"
        private const val KEY_TEXT_SIZE = "selected_text_size"
        private const val KEY_LABEL_SIZE = "selected_label_size"

        // --- NEW KEYS ---
        private const val KEY_OPENING_HOUR = "selected_opening_hour"
        private const val KEY_OPENING_MINUTE = "selected_opening_minute"
        private const val KEY_CLOSE_HOUR = "selected_close_hour"
        private const val KEY_CLOSE_MINUTE = "selected_close_minute"

        // Default values
        private const val DEFAULT_DENSITY = "Dense"
        private const val DEFAULT_TEXT_SIZE = 20f
        private const val DEFAULT_LABEL_SIZE = "30x20"

        // --- NEW DEFAULTS ---
        private const val DEFAULT_OPENING_HOUR = 8
        private const val DEFAULT_OPENING_MINUTE = 0
        private const val DEFAULT_CLOSE_HOUR = 22
        private const val DEFAULT_CLOSE_MINUTE = 0
    }

    // --- Printer Address ---
    fun savePrinterAddress(address: String?) {
        prefs.edit().putString(KEY_PRINTER_ADDRESS, address).apply()
    }

    fun getPrinterAddress(): String? {
        return prefs.getString(KEY_PRINTER_ADDRESS, null)
    }

    // --- Print Density ---
    fun saveDensity(density: String) {
        prefs.edit().putString(KEY_DENSITY, density).apply()
    }

    fun getDensity(): String {
        return prefs.getString(KEY_DENSITY, DEFAULT_DENSITY) ?: DEFAULT_DENSITY
    }

    // --- Text Size ---
    fun saveTextSize(size: Float) {
        prefs.edit().putFloat(KEY_TEXT_SIZE, size).apply()
    }

    fun getTextSize(): Float {
        return prefs.getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE)
    }

    // --- Label Size ---
    fun saveLabelSize(size: String) {
        prefs.edit().putString(KEY_LABEL_SIZE, size).apply()
    }

    fun getLabelSize(): String {
        return prefs.getString(KEY_LABEL_SIZE, DEFAULT_LABEL_SIZE) ?: DEFAULT_LABEL_SIZE
    }

    // --- Opening Time ---
    fun saveOpeningHour(hour: Int) {
        prefs.edit().putInt(KEY_OPENING_HOUR, hour).apply()
    }

    fun getOpeningHour(): Int {
        return prefs.getInt(KEY_OPENING_HOUR, DEFAULT_OPENING_HOUR)
    }

    fun saveOpeningMinute(minute: Int) {
        prefs.edit().putInt(KEY_OPENING_MINUTE, minute).apply()
    }

    fun getOpeningMinute(): Int {
        return prefs.getInt(KEY_OPENING_MINUTE, DEFAULT_OPENING_MINUTE)
    }

    // --- Close Time ---
    fun saveCloseHour(hour: Int) {
        prefs.edit().putInt(KEY_CLOSE_HOUR, hour).apply()
    }

    fun getCloseHour(): Int {
        return prefs.getInt(KEY_CLOSE_HOUR, DEFAULT_CLOSE_HOUR)
    }

    fun saveCloseMinute(minute: Int) {
        prefs.edit().putInt(KEY_CLOSE_MINUTE, minute).apply()
    }

    fun getCloseMinute(): Int {
        return prefs.getInt(KEY_CLOSE_MINUTE, DEFAULT_CLOSE_MINUTE)
    }
}