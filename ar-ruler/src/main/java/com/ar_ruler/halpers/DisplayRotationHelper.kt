package com.ar_ruler.halpers

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.os.Build
import android.view.Display
import android.view.WindowManager
import com.google.ar.core.Session

class DisplayRotationHelper(
    context: Context,
) : DisplayListener {
    private var viewportChanged = false
    private var viewportWidth = 0
    private var viewportHeight = 0

    private var display: Display? = null
    private var displayManager: DisplayManager? = null
    private var cameraManager: CameraManager? = null

    init {
        displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        }  else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
    }

    override fun onDisplayAdded(displayId: Int) {}

    override fun onDisplayRemoved(displayId: Int) {}

    override fun onDisplayChanged(displayId: Int) {
        viewportChanged = true
    }

    fun onResume() {
        displayManager?.registerDisplayListener(this, null)
    }

    fun onPause() {
        displayManager?.unregisterDisplayListener(this)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    fun updateSessionIfNeeded(session: Session) {
        if (viewportChanged) {
            val displayRotation = display?.rotation ?: return
            session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
            viewportChanged = false
        }
    }

    /*fun getCameraSensorRelativeViewportAspectRatio(cameraId: String): Float {
        return when (getCameraSensorToDisplayRotation(cameraId)) {
            90, 270 -> viewportHeight.toFloat() / viewportWidth.toFloat()
            else -> viewportWidth.toFloat() / viewportHeight.toFloat()
        }
    }

    fun getCameraSensorToDisplayRotation(cameraId: String): Int {
        val characteristics: CameraCharacteristics? = try {
            cameraManager?.getCameraCharacteristics(cameraId)
        } catch (e: CameraAccessException) {
            null
        }

        val sensorOrientation = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val displayOrientation = toDegrees(display?.rotation)

        return (sensorOrientation - displayOrientation + 360) % 360
    }

    private fun toDegrees(rotation: Int?): Int {
        return when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }*/
}