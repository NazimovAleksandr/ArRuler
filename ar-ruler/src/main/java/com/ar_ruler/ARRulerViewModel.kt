package com.ar_ruler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

@Suppress("ConvertObjectToDataObject")
sealed class ScreenState {
    object FindSurface : ScreenState()
    object TooDark : ScreenState()
    object AddPoint : ScreenState()
    class Result(val inc: String, val cm: String) : ScreenState()
    class Error(val message: String) : ScreenState()
}

class ARRulerViewModel : ViewModel() {

    private val _screenState: MutableStateFlow<ScreenState> = MutableStateFlow(ScreenState.FindSurface)
    val screenState: StateFlow<ScreenState> get() = _screenState.asStateFlow()

    fun checkCameraState(frame: Frame, camera: Camera, session: Session, drawRuler: (Frame, Camera) -> Unit) {
        val statePaused = camera.trackingState == TrackingState.PAUSED
        val reasonInsufficientLight = camera.trackingFailureReason == TrackingFailureReason.INSUFFICIENT_LIGHT
        val isDark = statePaused && reasonInsufficientLight

        when {
            _screenState.value is ScreenState.Result -> {}

            isDark -> _screenState.value = ScreenState.TooDark

            !hasTrackingPlane(session) -> _screenState.value = ScreenState.FindSurface

            hasTrackingPlane(session) -> {
                _screenState.value = ScreenState.AddPoint
                drawRuler(frame, camera)
            }
        }
    }

    private fun hasTrackingPlane(session: Session): Boolean {
        for (plane: Plane? in session.getAllTrackables(Plane::class.java)) {
            if (plane?.trackingState == TrackingState.TRACKING) {
                return true
            }
        }

        return false
    }

    fun showError(message: String) {
        _screenState.value = ScreenState.Error(message)
    }

    fun reset() {
        _screenState.value = ScreenState.AddPoint
    }

    fun measureDistanceOf2Points(anchorPointA: Anchor?, anchorPointB: Anchor?) {
        if (_screenState.value is ScreenState.Result) return

        viewModelScope.launch(Dispatchers.Main) {
            val distanceMeter = calculateDistance(
                AnchorNode(anchorPointA).worldPosition,
                AnchorNode(anchorPointB).worldPosition,
            )

            measureDistanceOf2Points(distanceMeter)
        }
    }

    private fun measureDistanceOf2Points(distanceMeter: Float) {
        val distanceInCentimeters =
            "${distanceInCentimeters(distanceMeter)} ${ARRulerFragment.stringValue.popUpCentimeters}"

        _screenState.value = ScreenState.Result(
            inc = distanceInInches(distanceMeter),
            cm = distanceInCentimeters,
        )
    }

    private fun distanceInCentimeters(distanceMeter: Float): String {
        return "%.1f".format(changeUnit(distanceMeter, "cm"))
    }

    private fun distanceInInches(distanceMeter: Float): String {
        return "%.1f".format(changeUnit(distanceMeter, "in"))
    }

    private fun changeUnit(distanceMeter: Float, unit: String): Float {
        return when (unit) {
            "in" -> distanceMeter * 100f / 2.54f
            "cm" -> distanceMeter * 100f
//            "mm" -> distanceMeter * 1000f
            else -> distanceMeter
        }
    }

    private fun calculateDistance(objectPose0: Vector3, objectPose1: Vector3): Float {
        return calculateDistance(
            objectPose0.x - objectPose1.x,
            objectPose0.y - objectPose1.y,
            objectPose0.z - objectPose1.z
        )
    }

    private fun calculateDistance(x: Float, y: Float, z: Float): Float {
        return sqrt(x.pow(2) + y.pow(2) + z.pow(2))
    }
}