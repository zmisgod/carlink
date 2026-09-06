package com.carlink.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.scale
import com.carlink.logging.logWarn
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Utility for loading icon assets for adapter initialization.
 *
 * Icons are uploaded to the adapter's own filesystem at /etc/icon_*.png during FULL
 * init via [FileAddress.ICON_120/180/256]. The 256px output is also written to
 * [FileAddress.OEM_ICON], which is the path CarPlay reads from airplay.conf.
 * The adapter firmware consumes them as OEM/brand-logo assets for its AirPlay
 * configuration (LogoType=1, CustomCarLogo=1 per config_key_analysis.md). They are
 * NOT used by iOS/Android device-picker UIs on the phone side — that is the
 * adapter's own identity it advertises.
 *
 * Three sizes match standard iOS app-icon raster scales: 120=60@2x, 180=60@3x,
 * 256 is the marketing master. The adapter firmware picks the size that matches
 * its display density; shipping all three covers the known head-unit variants.
 *
 * Missing-asset contract: any file that fails to load returns null; the
 * MessageSerializer serializes only the icons present (`?.let { serializeFile(...) }`),
 * so partial coverage silently uploads what we have. No user-visible error.
 *
 * Filename-case note: the APK asset names use uppercase ICON_NNNxNNN.png while
 * FileAddress.ICON_* paths use lowercase icon_NNNxNNN.png. The serializer writes
 * to the FileAddress path, so the on-adapter filename is always lowercase
 * regardless of the APK casing.
 */
object IconAssets {
    private const val ICON_120_ASSET = "ICON_120x120.png"
    private const val ICON_180_ASSET = "ICON_180x180.png"
    private const val ICON_256_ASSET = "ICON_256x256.png"
    private const val MAX_DECODE_DIMENSION = 2048

    /**
     * Load icon data from the user's custom image or the bundled assets.
     *
     * Called from MainActivity.initializeCarlinkManager on the main thread via
     * window.decorView.post { }. Total read size ~20 KiB across three APK-bundled
     * PNGs; negligible blocking in practice, but technically main-thread I/O.
     * Fire-and-forget trade-off: keeps startup sequencing simple.
     *
     * The OEM icon deliberately reuses the 256px output, so no fourth return value is
     * needed and all adapter icon paths stay visually identical.
     *
     * @param context Android context for asset access
     * @param customIconData Optional user-selected image. It is center-cropped to a square and
     * resized to the three adapter icon sizes.
     * @return Triple of (icon120, icon180, icon256) byte arrays, or nulls if loading fails
     */
    fun loadIcons(
        context: Context,
        customIconData: ByteArray? = null,
    ): Triple<ByteArray?, ByteArray?, ByteArray?> {
        customIconData?.let { custom ->
            resizeCustomIcon(custom)?.let { source ->
                return try {
                    val icons = Triple(
                        encodePng(source, 120),
                        encodePng(source, 180),
                        encodePng(source, 256),
                    )
                    source.recycle()
                    icons
                } catch (_: Exception) {
                    source.recycle()
                    loadBundledIcons(context)
                }
            }
            logWarn("[IconAssets] Custom home icon could not be decoded; using bundled icon")
        }
        return loadBundledIcons(context)
    }

    private fun loadBundledIcons(context: Context): Triple<ByteArray?, ByteArray?, ByteArray?> {
        val icon120 = loadAsset(context, ICON_120_ASSET)
        val icon180 = loadAsset(context, ICON_180_ASSET)
        val icon256 = loadAsset(context, ICON_256_ASSET)
        return Triple(icon120, icon180, icon256)
    }

    /** Center-crops non-square uploads, then creates one square master for all adapter sizes. */
    private fun resizeCustomIcon(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Decode large picker selections at a power-of-two sample. The final adapter master is
        // only 256px, so retaining source dimensions above 2048px wastes memory and can trigger
        // an OOM on AAOS devices with a constrained app heap.
        var sampleSize = 1
        val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
        while (largestDimension / sampleSize > MAX_DECODE_DIMENSION) sampleSize *= 2
        val decoded = BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        val side = minOf(decoded.width, decoded.height)
        if (side <= 0) {
            decoded.recycle()
            return null
        }
        val cropped = if (decoded.width == side && decoded.height == side) {
            decoded
        } else {
            Bitmap.createBitmap(
                decoded,
                (decoded.width - side) / 2,
                (decoded.height - side) / 2,
                side,
                side,
            ).also { decoded.recycle() }
        }
        // Composite before resizing. Transparent pixels in PNGs often retain a white RGB
        // matte even though their alpha is zero; resizing such a bitmap first can blend that
        // matte into the rounded edge and produce a visible white halo on the car display.
        val opaque = makeOpaque(cropped)
        val scaled = opaque.scale(256, 256).also {
            if (it !== opaque) opaque.recycle()
        }
        return scaled
    }

    /**
     * CarPlay composites transparent icon pixels against a white tile background. Fill the
     * transparent area with a vertical gradient sampled from the source so rounded-corner
     * artwork remains full-bleed while retaining its original visual palette.
     */
    private fun makeOpaque(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        output.setHasAlpha(false)
        val canvas = Canvas(output)
        val topColor = sampleOpaqueColor(bitmap, fromTop = true)
        val bottomColor = sampleOpaqueColor(bitmap, fromTop = false)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                bitmap.height.toFloat(),
                topColor,
                bottomColor,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), backgroundPaint)
        canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
        return output
    }

    private fun sampleOpaqueColor(bitmap: Bitmap, fromTop: Boolean): Int {
        val yRange = if (fromTop) 0 until bitmap.height else (bitmap.height - 1 downTo 0)
        val centerX = bitmap.width / 2
        for (y in yRange) {
            for (distance in 0..centerX) {
                val left = (centerX - distance).coerceAtLeast(0)
                val right = (centerX + distance).coerceAtMost(bitmap.width - 1)
                val leftColor = bitmap.getPixel(left, y)
                if (android.graphics.Color.alpha(leftColor) > 0) return leftColor or 0xFF000000.toInt()
                val rightColor = bitmap.getPixel(right, y)
                if (android.graphics.Color.alpha(rightColor) > 0) return rightColor or 0xFF000000.toInt()
            }
        }
        return 0xFF202124.toInt()
    }

    private fun encodePng(master: Bitmap, size: Int): ByteArray {
        val bitmap = if (size == master.width) master else master.scale(size, size)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode icon" }
            if (bitmap !== master) bitmap.recycle()
            output.toByteArray()
        }
    }

    // Emits logWarn WITHOUT a tag = argument — goes to the default/unfiltered pool
    // rather than a subsystem tag like Logger.Tags.CONFIG. Inconsistent with the
    // project convention of explicit tags; preserved here to avoid changing behavior
    // but worth aligning to `tag = Logger.Tags.CONFIG` next time this file is touched.
    private fun loadAsset(
        context: Context,
        fileName: String,
    ): ByteArray? =
        try {
            context.assets.open(fileName).use { it.readBytes() }
        } catch (e: IOException) {
            logWarn("[IconAssets] Failed to load asset '$fileName': ${e.message}")
            null
        }
}
