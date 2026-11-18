package com.justdataplease.streetgym.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.justdataplease.streetgym.app.data.HomeWidget
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes

/**
 * What Swift does for the home-screen widget (iosApp/StreetGym/WidgetBridge.swift): writes the snapshot, the tile's
 * picture and the display face into the App Group's folder and asks WidgetKit to draw the widget again. Swift sees it as
 * the protocol `NativeWidget`. Without the App Group (an unsigned build, or none set up yet) Swift writes nothing.
 */
interface NativeWidget {
    /** [snapshot] is JSON; [figure] a PNG, null when it didn't change; [font] the face's file named [fontFile], or null. */
    fun save(snapshot: String, figure: NSData?, fontFile: String?, font: NSData?)
}

/** The app's [HomeWidget] over Swift's [NativeWidget]: the tile's drawing goes over as a PNG. */
internal class IosWidget(private val native: NativeWidget) : HomeWidget {
    override fun save(snapshot: String, figure: ImageBitmap?, fontFile: String?, font: ByteArray?) {
        native.save(snapshot, figure?.let(::png)?.let(::nsData), fontFile, font?.let(::nsData))
    }
}

/** [image] as PNG bytes, through Skia, which draws Compose on iPhone too. */
internal fun png(image: ImageBitmap): ByteArray? =
    runCatching { Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes }.getOrNull()

@OptIn(ExperimentalForeignApi::class)
internal fun nsData(bytes: ByteArray): NSData? =
    if (bytes.isEmpty()) null else bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
