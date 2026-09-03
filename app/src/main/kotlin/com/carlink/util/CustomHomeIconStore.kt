package com.carlink.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.edit
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Stores the user-selected CarPlay home icon in app-private storage. */
object CustomHomeIconStore {
    private const val PREFERENCES_NAME = "carlink_custom_home_icon"
    private const val ENABLED_KEY = "enabled"
    private const val FILE_NAME = "custom_home_icon.png"
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_DIMENSION = 4096

    private fun iconFile(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    fun loadBytes(context: Context): ByteArray? {
        val appContext = context.applicationContext
        if (!appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(ENABLED_KEY, false)
        ) {
            return null
        }
        return iconFile(appContext).takeIf { it.isFile && it.length() <= MAX_BYTES }?.readBytes()
            ?.takeIf(::isValidImage)
    }

    fun hasIcon(context: Context): Boolean = loadBytes(context) != null

    /** Reads and validates an image selected through the Android document picker. */
    fun readUri(context: Context, uri: Uri): ByteArray? =
        try {
            context.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BYTES) return null
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                if (bytes.isEmpty()) return null
                if (!isValidImage(bytes)) return null
                bytes
            }
        } catch (_: Exception) {
            null
        }

    /** Atomically replaces the stored icon. A null value restores the bundled icon. */
    fun saveBytes(context: Context, bytes: ByteArray?) {
        val appContext = context.applicationContext
        val file = iconFile(appContext)
        if (bytes == null) {
            file.delete()
            appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit { putBoolean(ENABLED_KEY, false) }
            return
        }
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Invalid icon data" }
        require(isValidImage(bytes)) { "Icon is not a valid image" }
        val temporary = File(appContext.filesDir, "$FILE_NAME.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw IOException("Unable to replace custom home icon")
        }
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(ENABLED_KEY, true) }
    }

    fun clear(context: Context) = saveBytes(context, null)

    private fun isValidImage(bytes: ByteArray): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth in 1..MAX_DIMENSION && options.outHeight in 1..MAX_DIMENSION
    }
}
