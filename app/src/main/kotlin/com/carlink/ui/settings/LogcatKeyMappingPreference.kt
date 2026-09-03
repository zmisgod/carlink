package com.carlink.ui.settings

import android.content.Context
import androidx.core.content.edit
import com.carlink.protocol.InputKeyAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class LogcatTriggerMode(
    val label: String,
) {
    CARPLAY_ONLY("CarPlay only"),
    LOCAL_ONLY("Local only (no car command)"),
}

/** A case-insensitive logcat line matcher mapped to one projection action. */
data class LogcatKeyMapping(
    val id: String,
    val pattern: String,
    val action: InputKeyAction,
    val mode: LogcatTriggerMode,
) {
    private val normalizedPattern = pattern.trim()
    private val wildcardRegex =
        normalizedPattern.takeIf { value -> value.any { it == '%' || it == '*' } }?.let { value ->
            Regex(
                buildString {
                    value.forEach { character ->
                        when (character) {
                            '%', '*' -> append(".*")
                            else -> append(Regex.escape(character.toString()))
                        }
                    }
                },
                RegexOption.IGNORE_CASE,
            )
        }

    fun matches(line: String): Boolean =
        if (wildcardRegex != null) {
            wildcardRegex.containsMatchIn(line)
        } else {
            line.contains(normalizedPattern, ignoreCase = true)
        }
}

/** Prevent wildcard-only rules from matching the entire log stream. */
fun isValidLogcatPattern(pattern: String): Boolean =
    pattern.trim().any { character -> character != '%' && character != '*' && !character.isWhitespace() }

class LogcatKeyMappingPreference private constructor(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _mappings = MutableStateFlow(readMappings())
    val mappings: StateFlow<List<LogcatKeyMapping>> = _mappings

    @Synchronized
    fun addMapping(
        pattern: String,
        action: InputKeyAction,
        mode: LogcatTriggerMode,
    ): LogcatKeyMapping {
        val normalizedPattern = pattern.trim()
        require(isValidLogcatPattern(normalizedPattern)) {
            "Logcat pattern must contain at least one non-wildcard character"
        }

        val mapping =
            LogcatKeyMapping(
                id = UUID.randomUUID().toString(),
                pattern = normalizedPattern,
                action = action,
                mode = mode,
            )
        val updated =
            _mappings.value.filterNot { existing ->
                existing.pattern.equals(normalizedPattern, ignoreCase = true)
            } + mapping
        saveMappings(updated)
        return mapping
    }

    @Synchronized
    fun removeMapping(id: String) {
        saveMappings(_mappings.value.filterNot { it.id == id })
    }

    private fun saveMappings(mappings: List<LogcatKeyMapping>) {
        val json = JSONArray()
        mappings.forEach { mapping ->
            json.put(
                JSONObject()
                    .put(JSON_ID, mapping.id)
                    .put(JSON_PATTERN, mapping.pattern)
                    .put(JSON_ACTION, mapping.action.name)
                    .put(JSON_MODE, mapping.mode.name),
            )
        }
        preferences.edit { putString(KEY_MAPPINGS, json.toString()) }
        _mappings.value = mappings
    }

    private fun readMappings(): List<LogcatKeyMapping> {
        val raw = preferences.getString(KEY_MAPPINGS, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val id = item.optString(JSON_ID).takeIf(String::isNotBlank) ?: continue
                    val pattern = item.optString(JSON_PATTERN).trim()
                    if (!isValidLogcatPattern(pattern)) continue
                    val action =
                        runCatching { InputKeyAction.valueOf(item.optString(JSON_ACTION)) }
                            .getOrNull() ?: continue
                    val mode =
                        runCatching { LogcatTriggerMode.valueOf(item.optString(JSON_MODE)) }
                            .getOrNull() ?: continue
                    add(LogcatKeyMapping(id, pattern, action, mode))
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFERENCES_NAME = "carlink_logcat_key_mappings"
        private const val KEY_MAPPINGS = "mappings"
        private const val JSON_ID = "id"
        private const val JSON_PATTERN = "pattern"
        private const val JSON_ACTION = "action"
        private const val JSON_MODE = "mode"

        @Volatile private var instance: LogcatKeyMappingPreference? = null

        fun getInstance(context: Context): LogcatKeyMappingPreference =
            instance ?: synchronized(this) {
                instance ?: LogcatKeyMappingPreference(context.applicationContext).also { instance = it }
            }
    }
}
