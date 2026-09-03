package com.carlink.ui.settings

import android.content.Context
import androidx.core.content.edit
import com.carlink.protocol.InputKeyAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persistent map from Android key codes to projection actions.
 *
 * The map is intentionally app-local. Android does not expose a generic global
 * key remapper, but hardware/ADB key events delivered to MainActivity can be
 * routed through this store without changing the adapter protocol.
 */
class InputKeyMappingPreference private constructor(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _mappings = MutableStateFlow(readMappings())
    val mappings: StateFlow<Map<Int, InputKeyAction>> = _mappings

    fun getAction(keyCode: Int): InputKeyAction? = _mappings.value[keyCode]

    @Synchronized
    fun setMapping(keyCode: Int, action: InputKeyAction) {
        require(keyCode > 0) { "keyCode must be positive" }

        // One key has one action. Remove an existing assignment for this action
        // so the settings UI cannot create two competing keys accidentally.
        preferences.edit {
            _mappings.value.filterValues { it == action }.keys
                .filter { it != keyCode }
                .forEach { remove(keyName(it)) }
            putString(keyName(keyCode), action.name)
        }
        _mappings.value = readMappings()
    }

    @Synchronized
    fun removeMapping(action: InputKeyAction) {
        preferences.edit {
            _mappings.value.filterValues { it == action }.keys.forEach { remove(keyName(it)) }
        }
        _mappings.value = readMappings()
    }

    @Synchronized
    fun clear() {
        preferences.edit { clear() }
        _mappings.value = emptyMap()
    }

    private fun readMappings(): Map<Int, InputKeyAction> =
        preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_PREFIX) || value !is String) return@mapNotNull null
            val keyCode = key.removePrefix(KEY_PREFIX).toIntOrNull() ?: return@mapNotNull null
            val action = runCatching { InputKeyAction.valueOf(value) }.getOrNull() ?: return@mapNotNull null
            keyCode to action
        }.toMap()

    private fun keyName(keyCode: Int): String = "$KEY_PREFIX$keyCode"

    companion object {
        private const val PREFERENCES_NAME = "carlink_input_key_mappings"
        private const val KEY_PREFIX = "key_"

        @Volatile private var instance: InputKeyMappingPreference? = null

        fun getInstance(context: Context): InputKeyMappingPreference =
            instance ?: synchronized(this) {
                instance ?: InputKeyMappingPreference(context.applicationContext).also { instance = it }
            }
    }
}
