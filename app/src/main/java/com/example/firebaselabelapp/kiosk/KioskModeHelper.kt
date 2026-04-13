package com.example.firebaselabelapp.kiosk

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.firebaselabelapp.R

/**
 * Helper class to manage kiosk mode setup and operation
 * Handles both device owner and device admin scenarios seamlessly
 */
class KioskModeHelper(private val context: Context) {

    companion object {
        private const val TAG = "KioskModeHelper"
    }

    private val kioskManager = KioskManager(context)

    /**
     * Check if kiosk mode is properly set up and ready to use
     */
    fun isKioskSetupComplete(): Boolean {
        val isPinSet = kioskManager.isPinSet()
        val hasAdminPrivileges = kioskManager.isDeviceAdminActive()
        val canUseLockTask = kioskManager.canUseLockTaskMode()

        Log.d(TAG, "Kiosk setup check - PIN: $isPinSet, Admin: $hasAdminPrivileges, LockTask: $canUseLockTask")

        return isPinSet && hasAdminPrivileges && canUseLockTask
    }

    /**
     * Guide user through kiosk setup process
     */
    fun setupKioskMode(activity: Activity, onComplete: (Boolean) -> Unit) {
        when {
            !kioskManager.isPinSet() -> {
                showPinSetupDialog(activity) { success ->
                    if (success) {
                        setupKioskMode(activity, onComplete) // Continue setup
                    } else {
                        onComplete(false)
                    }
                }
            }

            !kioskManager.isDeviceAdminActive() -> {
                setupAdminPrivileges(activity) { success ->
                    if (success) {
                        setupKioskMode(activity, onComplete) // Continue setup
                    } else {
                        onComplete(false)
                    }
                }
            }

            !kioskManager.canUseLockTaskMode() -> {
                showLockTaskSetupDialog(activity) { success ->
                    onComplete(success)
                }
            }

            else -> {
                // Setup complete
                onComplete(true)
            }
        }
    }

    /**
     * Start kiosk mode with appropriate setup for current privilege level
     */
    fun startKioskMode(activity: Activity): Boolean {
        return try {
            if (!isKioskSetupComplete()) {
                Log.e(TAG, "Cannot start kiosk mode: setup not complete")
                Toast.makeText(context, "Настройка режима киоска не завершена", Toast.LENGTH_LONG).show()
                return false
            }

            // Enable kiosk mode
            val enabled = kioskManager.enableKioskMode()
            if (!enabled) {
                Log.e(TAG, "Failed to enable kiosk mode")
                return false
            }

            // Start lock task mode
            kioskManager.startLockTaskMode(activity)

            // Show status message based on mode
            val mode = if (kioskManager.isDeviceOwner()) {
                "Режим киоска активирован (Владелец устройства)"
            } else {
                "Режим киоска активирован (Администратор устройства)"
            }

            Toast.makeText(context, mode, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Kiosk mode started successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start kiosk mode", e)
            Toast.makeText(context, "Ошибка запуска режима киоска", Toast.LENGTH_LONG).show()
            false
        }
    }

    /**
     * Stop kiosk mode
     */
    fun stopKioskMode(activity: Activity, pin: String): Boolean {
        return try {
            if (!kioskManager.verifyPin(pin)) {
                Toast.makeText(context, "Неверный PIN-код", Toast.LENGTH_SHORT).show()
                return false
            }

            // Stop lock task mode
            kioskManager.stopLockTaskMode(activity)

            // Disable kiosk mode
            val disabled = kioskManager.disableKioskMode()

            if (disabled) {
                Toast.makeText(context, "Режим киоска деактивирован", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Kiosk mode stopped successfully")
                true
            } else {
                Log.e(TAG, "Failed to disable kiosk mode")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop kiosk mode", e)
            Toast.makeText(context, "Ошибка остановки режима киоска", Toast.LENGTH_LONG).show()
            false
        }
    }

    /**
     * Show PIN setup dialog
     */
    private fun showPinSetupDialog(activity: Activity, onResult: (Boolean) -> Unit) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Настройка PIN-кода")
        builder.setMessage("Для активации режима киоска необходимо установить 6-значный PIN-код.")

        val input = android.widget.EditText(activity)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = "Введите 6-значный PIN"
        builder.setView(input)

        builder.setPositiveButton("Установить") { _, _ ->
            val pin = input.text.toString()
            if (kioskManager.isValidPinFormat(pin)) {
                val saved = kioskManager.savePin(pin)
                if (saved) {
                    Toast.makeText(context, "PIN-код установлен", Toast.LENGTH_SHORT).show()
                    onResult(true)
                } else {
                    Toast.makeText(context, "Ошибка установки PIN-кода", Toast.LENGTH_SHORT).show()
                    onResult(false)
                }
            } else {
                Toast.makeText(context, "PIN должен содержать 6 цифр", Toast.LENGTH_SHORT).show()
                onResult(false)
            }
        }

        builder.setNegativeButton("Отмена") { _, _ ->
            onResult(false)
        }

        builder.show()
    }

    /**
     * Setup admin privileges
     */
    private fun setupAdminPrivileges(activity: Activity, onResult: (Boolean) -> Unit) {
        if (kioskManager.isDeviceOwner()) {
            Log.d(TAG, "Already device owner")
            onResult(true)
            return
        }

        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Права администратора")

        val message = if (kioskManager.isDeviceOwner()) {
            "Приложение имеет права владельца устройства."
        } else {
            "Для работы режима киоска необходимы права администратора устройства.\n\n" +
                    "Это позволит приложению:\n" +
                    "• Блокировать выход из приложения\n" +
                    "• Скрывать системные элементы интерфейса\n" +
                    "• Предотвращать несанкционированный доступ"
        }

        builder.setMessage(message)

        builder.setPositiveButton("Предоставить права") { _, _ ->
            try {
                kioskManager.requestDeviceAdminPermission(activity)
                // Result will be handled in activity's onActivityResult
                onResult(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request admin permissions", e)
                Toast.makeText(context, "Ошибка запроса прав администратора", Toast.LENGTH_LONG).show()
                onResult(false)
            }
        }

        builder.setNegativeButton("Отмена") { _, _ ->
            onResult(false)
        }

        builder.show()
    }

    /**
     * Show lock task setup dialog
     */
    private fun showLockTaskSetupDialog(activity: Activity, onResult: (Boolean) -> Unit) {
        val isDeviceOwner = kioskManager.isDeviceOwner()

        if (isDeviceOwner) {
            // Device owner can use lock task without additional setup
            onResult(true)
            return
        }

        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Дополнительная настройка")
        builder.setMessage(
            "Для улучшения блокировки приложений рекомендуется включить службу специальных возможностей.\n\n" +
                    "Это обеспечит:\n" +
                    "• Более надежную блокировку неавторизованных приложений\n" +
                    "• Автоматический возврат в приложение киоска\n" +
                    "• Блокировку системных жестов\n\n" +
                    "Режим киоска будет работать и без этого, но с ограниченной функциональностью."
        )

        builder.setPositiveButton("Настроить службу") { _, _ ->
            try {
                val intent = kioskManager.getAccessibilitySettingsIntent()
                activity.startActivity(intent)
                Toast.makeText(
                    context,
                    "Найдите службу '${context.getString(R.string.app_name)}' и включите её",
                    Toast.LENGTH_LONG
                ).show()
                onResult(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open accessibility settings", e)
                Toast.makeText(context, "Ошибка открытия настроек", Toast.LENGTH_LONG).show()
                onResult(false)
            }
        }

        builder.setNeutralButton("Продолжить без службы") { _, _ ->
            onResult(true)
        }

        builder.setNegativeButton("Отмена") { _, _ ->
            onResult(false)
        }

        builder.show()
    }

    /**
     * Get current kiosk status for display
     */
    fun getKioskStatusSummary(): String {
        val status = StringBuilder()

        // Basic status
        status.appendLine("📱 Статус режима киоска:")
        status.appendLine("Активен: ${if (kioskManager.isKioskModeEnabled()) "✅ Да" else "❌ Нет"}")
        status.appendLine("PIN установлен: ${if (kioskManager.isPinSet()) "✅ Да" else "❌ Нет"}")

        // Admin status
        val adminStatus = when {
            kioskManager.isDeviceOwner() -> "✅ Владелец устройства"
            kioskManager.isDeviceAdminActive() -> "⚠️ Администратор устройства"
            else -> "❌ Нет прав администратора"
        }
        status.appendLine("Права: $adminStatus")

        // Features status
        status.appendLine("Блокировка задач: ${if (kioskManager.canUseLockTaskMode()) "✅ Доступна" else "❌ Недоступна"}")

        val accessibilityStatus = if (kioskManager.isAccessibilityServiceEnabled()) {
            "✅ Включена"
        } else {
            if (kioskManager.isDeviceOwner()) "⚠️ Не требуется" else "❌ Выключена"
        }
        status.appendLine("Служба доступности: $accessibilityStatus")

        // Overall readiness
        status.appendLine()
        val readiness = if (kioskManager.isKioskReady()) {
            "✅ Готов к использованию"
        } else {
            "⚠️ Требуется настройка"
        }
        status.appendLine("Состояние: $readiness")

        return status.toString()
    }

    /**
     * Check what setup steps are still needed
     */
    fun getRequiredSetupSteps(): List<String> {
        val steps = mutableListOf<String>()

        if (!kioskManager.isPinSet()) {
            steps.add("Установить PIN-код")
        }

        if (!kioskManager.isDeviceAdminActive()) {
            steps.add("Предоставить права администратора")
        }

        if (!kioskManager.canUseLockTaskMode()) {
            steps.add("Настроить блокировку задач")
        }

        if (!kioskManager.isDeviceOwner() && !kioskManager.isAccessibilityServiceEnabled()) {
            steps.add("Включить службу доступности (рекомендуется)")
        }

        return steps
    }

    /**
     * Emergency exit (for testing/debugging)
     */
    fun emergencyExit(activity: Activity): Boolean {
        return try {
            Log.w(TAG, "EMERGENCY EXIT REQUESTED")

            // Try force exit if device owner
            if (kioskManager.isDeviceOwner()) {
                kioskManager.forceExitKioskMode(activity)
            } else {
                // For device admin, try normal exit
                kioskManager.stopLockTaskMode(activity)
                kioskManager.disableKioskMode()
            }

            Toast.makeText(context, "Экстренный выход выполнен", Toast.LENGTH_SHORT).show()
            true

        } catch (e: Exception) {
            Log.e(TAG, "Emergency exit failed", e)
            false
        }
    }

    /**
     * Reset all kiosk settings (for testing/debugging)
     */
    fun resetKioskSettings(): Boolean {
        return try {
            Log.w(TAG, "RESETTING KIOSK SETTINGS")
            kioskManager.emergencyDisable()
            Toast.makeText(context, "Настройки киоска сброшены", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset kiosk settings", e)
            Toast.makeText(context, "Ошибка сброса настроек", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Handle activity result for admin permission request
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == 1001) { // REQUEST_CODE_ENABLE_ADMIN
            val granted = kioskManager.isDeviceAdminActive()
            if (granted) {
                Toast.makeText(context, "Права администратора предоставлены", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Device admin permissions granted")
            } else {
                Toast.makeText(context, "Права администратора не предоставлены", Toast.LENGTH_LONG).show()
                Log.w(TAG, "Device admin permissions denied")
            }
            return granted
        }
        return false
    }

    /**
     * Show detailed setup instructions
     */
    fun showSetupInstructions(activity: Activity) {
        val instructions = buildString {
            appendLine("📋 Инструкция по настройке режима киоска")
            appendLine()

            if (kioskManager.isDeviceOwner()) {
                appendLine("🎯 Режим: Владелец устройства")
                appendLine("Ваше приложение имеет максимальные права для управления устройством.")
                appendLine()
                appendLine("Требуется:")
                if (!kioskManager.isPinSet()) {
                    appendLine("• Установить PIN-код для выхода из режима киоска")
                }
                appendLine()
                appendLine("Возможности:")
                appendLine("• Полная блокировка системного интерфейса")
                appendLine("• Автоматический запрет других приложений")
                appendLine("• Отключение строки состояния и навигации")
                appendLine("• Защита от сброса настроек")
            } else {
                appendLine("🎯 Режим: Администратор устройства")
                appendLine("Приложение будет работать с ограниченными правами администратора.")
                appendLine()

                val steps = getRequiredSetupSteps()
                if (steps.isNotEmpty()) {
                    appendLine("Необходимые шаги:")
                    steps.forEach { step ->
                        appendLine("• $step")
                    }
                    appendLine()
                }

                appendLine("Возможности:")
                appendLine("• Блокировка выхода из приложения")
                appendLine("• Скрытие системного интерфейса")
                appendLine("• Автоматический возврат в приложение (с службой доступности)")
                appendLine("• Блокировка системных клавиш")
                appendLine()

                if (!kioskManager.isAccessibilityServiceEnabled()) {
                    appendLine("⚠️ Рекомендация:")
                    appendLine("Включите службу доступности для улучшенной защиты от выхода из режима киоска.")
                }
            }
        }

        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Настройка режима киоска")
        builder.setMessage(instructions)
        builder.setPositiveButton("Понятно") { dialog, _ ->
            dialog.dismiss()
        }

        if (!isKioskSetupComplete()) {
            builder.setNeutralButton("Начать настройку") { _, _ ->
                setupKioskMode(activity) { success ->
                    if (success) {
                        Toast.makeText(context, "Настройка завершена", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Настройка не завершена", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        builder.show()
    }

    /**
     * Check if device supports kiosk mode features
     */
    fun checkDeviceCompatibility(): Map<String, Boolean> {
        return mapOf(
            "deviceAdmin" to kioskManager.isDeviceAdminActive(),
            "deviceOwner" to kioskManager.isDeviceOwner(),
            "lockTask" to kioskManager.canUseLockTaskMode(),
            "accessibility" to kioskManager.isAccessibilityServiceEnabled(),
            "immersiveMode" to true, // Always supported on modern Android
            "pinSupport" to true // Always supported
        )
    }

    /**
     * Get recommendations for optimal kiosk setup
     */
    fun getSetupRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        if (!kioskManager.isDeviceOwner()) {
            recommendations.add("Рассмотрите возможность установки приложения как владельца устройства через ADB для максимальной функциональности")
        }

        if (!kioskManager.isAccessibilityServiceEnabled() && !kioskManager.isDeviceOwner()) {
            recommendations.add("Включите службу доступности для надежной блокировки приложений")
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            recommendations.add("Обновите Android до версии 6.0 или выше для лучшей поддержки режима киоска")
        }

        recommendations.add("Настройте автоматический запуск приложения после перезагрузки устройства")
        recommendations.add("Отключите автоматические обновления системы для стабильности киоска")
        recommendations.add("Настройте резервное копирование настроек киоска")

        return recommendations
    }

    /**
     * Validate current kiosk configuration
     */
    fun validateConfiguration(): Map<String, String> {
        val validation = mutableMapOf<String, String>()

        // Check PIN
        if (kioskManager.isPinSet()) {
            validation["pin"] = "✅ PIN-код установлен"
        } else {
            validation["pin"] = "❌ PIN-код не установлен"
        }

        // Check admin privileges
        when {
            kioskManager.isDeviceOwner() -> {
                validation["admin"] = "✅ Владелец устройства (максимальные права)"
            }
            kioskManager.isDeviceAdminActive() -> {
                validation["admin"] = "⚠️ Администратор устройства (ограниченные права)"
            }
            else -> {
                validation["admin"] = "❌ Нет прав администратора"
            }
        }

        // Check lock task
        if (kioskManager.canUseLockTaskMode()) {
            validation["lockTask"] = "✅ Блокировка задач доступна"
        } else {
            validation["lockTask"] = "❌ Блокировка задач недоступна"
        }

        // Check accessibility
        when {
            kioskManager.isDeviceOwner() -> {
                validation["accessibility"] = "ℹ️ Служба доступности не требуется (владелец устройства)"
            }
            kioskManager.isAccessibilityServiceEnabled() -> {
                validation["accessibility"] = "✅ Служба доступности включена"
            }
            else -> {
                validation["accessibility"] = "⚠️ Служба доступности выключена (рекомендуется включить)"
            }
        }

        // Overall status
        if (isKioskSetupComplete()) {
            validation["overall"] = "✅ Конфигурация готова к использованию"
        } else {
            validation["overall"] = "⚠️ Конфигурация требует дополнительной настройки"
        }

        return validation
    }
}