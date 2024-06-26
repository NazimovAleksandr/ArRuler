package com.ar_ruler

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ar_ruler.databinding.FragmentArRulerBinding
import com.ar_ruler.halpers.DisplayRotationHelper
import com.ar_ruler.rendering.BackgroundRenderer
import com.ar_ruler.rendering.LineRenderer
import com.ar_ruler.rendering.ObjectRenderer
import com.ar_ruler.rendering.PlaneRenderer
import com.ar_ruler.rendering.PointCloudRenderer
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Config.CloudAnchorMode
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import javax.vecmath.Vector3f

class ARRulerFragment : Fragment(), GLSurfaceView.Renderer {

    companion object {
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA

        var onClickTutorial: ((View) -> Unit)? = null
        var onClickExercises: ((View) -> Unit)? = null

        var stringValue: StringValue = StringValue(
            a = "a",
            b = "b",
            addPointA = "Add Point A",
            addPointB = "Add Point B",
            moveAround = "Move Around",
            tooDark = "It's too dark",
            tooDarkDescription = "Additional lighting is needed. Please add more lighting",
            popUpInch = "in",
            popUpCentimeters = "centimeters",
            popUpTitle = "Get big and boost your size!",
            popUpSubtitle = "Say hello to epic growth with exercise program",
            popUpButton = "Exercises",
        )
    }

    private var _binding: FragmentArRulerBinding? = null
    private val binding: FragmentArRulerBinding get() = _binding!!

    private val viewModel: ARRulerViewModel by viewModels {
        ARRulerViewModelFactory()
    }

    private val vectorX by lazy { binding.root.width / 2f }
    private val vectorY by lazy { binding.root.height / 2f }

    private val displayRotationHelper: DisplayRotationHelper by lazy {
        DisplayRotationHelper(requireContext())
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission(), ::checkCameraPermission)

    private var installRequested = false

    private var session: Session? = null
//    private var future: Future? = null

    private var anchorGoal: Anchor? = null
    private var anchorPointA: Anchor? = null
    private var anchorPointB: Anchor? = null
    private var addPointA: Boolean = false
    private var addPointB: Boolean = false
    private val anchorMatrixGoal = FloatArray(16)
    private val anchorMatrixA = FloatArray(16)
    private val anchorMatrixB = FloatArray(16)

    private val backgroundRenderer: BackgroundRenderer = BackgroundRenderer() /* Основа (камера) */

    private val virtualGoal: ObjectRenderer = ObjectRenderer()
    private val virtualPoint: ObjectRenderer = ObjectRenderer()
    private val virtualLine: LineRenderer = LineRenderer()

    //private val planeRenderer: PlaneRenderer = PlaneRenderer() /* Сетка */
    private val pointCloudRenderer: PointCloudRenderer = PointCloudRenderer() /* Точки на плоскости */

    /*private var mLastFramePosition: FloatArray? = null
    private val bTouchDown = AtomicBoolean(false)
    private val mZeroMatrix = FloatArray(16)*/

    private val strokes: java.util.ArrayList<java.util.ArrayList<Vector3f>> = java.util.ArrayList()
    private var surfaceWidth = 0f
    private var surfaceHeight = 0f

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArRulerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initScreen()
        initGLSurfaceView()
        initListener()
        initViewModel()
    }

    override fun onResume() {
        super.onResume()
        startSession()
    }

    override fun onPause() {
        super.onPause()
        stopSession()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        try {
            backgroundRenderer.createOnGlThread(requireContext())
            pointCloudRenderer.createOnGlThread(requireContext())
            virtualGoal.createOnGlThread(requireContext(), "models/goal.obj", "models/object.png")
            virtualPoint.createOnGlThread(requireContext(), "models/point.obj", "models/object.png")

            virtualLine.createOnGlThread(requireContext())
        } catch (ignore: IOException) {
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)

        surfaceWidth = width.toFloat()
        surfaceHeight = height.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val session = this.session ?: return

        displayRotationHelper.updateSessionIfNeeded(session)

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId())

            val frame = session.update()
            val camera = frame.camera

            backgroundRenderer.draw(frame)

            viewModel.checkCameraState(frame, camera, session, ::drawRuler)
        } catch (ignore: Throwable) {
        }
    }

    private fun initScreen() {
        binding.findSurfaceText.text = stringValue.moveAround
        binding.descriptionAddPointButton.text = stringValue.addPointA

        binding.tooDark.text = stringValue.tooDark
        binding.tooDarkDescription.text = stringValue.tooDarkDescription

        binding.inch.text = stringValue.popUpInch
        binding.popUpTitle.text = stringValue.popUpTitle
        binding.popUpSubtitle.text = stringValue.popUpSubtitle
        binding.popUpButton.text = stringValue.popUpButton

        binding.popUpButton.paint.shader = LinearGradient(
            0f, 0f, 0f,
            binding.popUpButton.textSize,
            intArrayOf(
                resources.getColor(R.color.white, null),
                resources.getColor(R.color.light_blue, null),
            ),
            null, Shader.TileMode.CLAMP
        )
    }

    private fun initGLSurfaceView() {
        binding.glSurfaceView.preserveEGLContextOnPause = true
        binding.glSurfaceView.setEGLContextClientVersion(2)
        binding.glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0) // Alpha used for plane blending.
        binding.glSurfaceView.setRenderer(this)
        binding.glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        binding.glSurfaceView.setWillNotDraw(false)
    }

    private fun initListener() {
        binding.addPointButton.setOnClickListener {
            addPoint()
        }

        binding.backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.popUpButtonReset.setOnClickListener {
            onClear()
        }

        binding.popUpButtonCloseRuler.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.popUpButton.setOnClickListener {
            requireActivity().finish()
            onClickExercises?.invoke(it)
            onClickExercises = null
        }

        binding.tutorialButton.setOnClickListener(onClickTutorial)
        onClickTutorial = null
    }

    private fun initViewModel() {
        lifecycleScope.launch {
            viewModel.screenState.collect {
                hideAll()

                when (it) {
                    is ScreenState.FindSurface -> findSurface()
                    is ScreenState.TooDark -> tooDark()
                    is ScreenState.AddPoint -> addPointA()
                    is ScreenState.Result -> result(it)

                    is ScreenState.Error -> error(it.message)
                }
            }
        }
    }

    private fun hideAll() {
        binding.screenStateFindSurface.isVisible = false
        binding.screenStateTooDark.isVisible = false
        binding.screenStateAddPoint.isVisible = false
        binding.screenStateResult.isVisible = false
        binding.topButtons.isVisible = false
    }

    private fun findSurface() {
        binding.screenStateFindSurface.isVisible = true
        binding.topButtons.isVisible = true
    }

    private fun tooDark() {
        binding.screenStateTooDark.isVisible = true
    }

    private fun addPointA() {
        binding.descriptionAddPointButton.text = stringValue.addPointA
        binding.addPointButton.text = stringValue.a
        binding.screenStateAddPoint.isVisible = true
        binding.topButtons.isVisible = true
    }

    private fun result(screenState: ScreenState.Result) {
        binding.distanceInInches.text = screenState.inc
        binding.distanceInCentimeters.text = screenState.cm

        binding.screenStateResult.isVisible = true
    }

    private fun error(message: String) {
        binding.errorText.text = message
        binding.errorText.isVisible = true
    }

    private fun startSession() {
        checkArCore {
            binding.glSurfaceView.onResume()
            displayRotationHelper.onResume()
        }
    }

    private fun stopSession() {
        if (session != null) {
            displayRotationHelper.onPause()
            binding.glSurfaceView.onPause()
            session?.pause()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return (ContextCompat.checkSelfPermission(
            requireContext(),
            CAMERA_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun checkCameraPermission(value: Boolean) {
        if (!value) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), CAMERA_PERMISSION)) {
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = Uri.fromParts("package", requireActivity().packageName, null)
                requireActivity().startActivity(intent)
            }

            requireActivity().finish()
        }
    }

    private fun checkArCore(available: () -> Unit) {
        if (session == null) {
            var message: String? = null

            try {
                when (ArCoreApk.getInstance().requestInstall(requireActivity(), !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }

                    ArCoreApk.InstallStatus.INSTALLED -> {}
                }

                if (!hasCameraPermission()) {
                    requestCameraPermission.launch(CAMERA_PERMISSION)
                    return
                }

                session = Session(requireActivity())

                val config = Config(session)
                config.cloudAnchorMode = CloudAnchorMode.ENABLED
                session?.configure(config)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install ARCore"
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = "Please install ARCore"
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update ARCore"
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
            } catch (e: UnavailableDeviceNotCompatibleException) {
                message = "This device does not support AR"
            } catch (e: Exception) {
                message = "Failed to create AR session"
            }

            if (message != null) {
                viewModel.showError(message)
                return
            }
        }

        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            viewModel.showError("Camera not available. Try restarting the app.")
            session = null
            return
        }

        available.invoke()
    }

    private fun drawRuler(frame: Frame, camera: Camera) {
        centerTap(frame, camera) { it.also { anchorGoal = it } }

        when {
            addPointA && anchorPointA == null -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.descriptionAddPointButton.text = stringValue.addPointB
                    binding.addPointButton.text = stringValue.b
                }

                centerTap(frame, camera) { it.also { anchorPointA = it } }
            }

            addPointB && anchorPointB == null -> {
                centerTap(frame, camera) { it.also { anchorPointB = it } }
            }
        }

        val projectionMatrix = FloatArray(16)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

        val viewMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)

//            val colorCorrectionRgba = FloatArray(4)
//            frame.lightEstimate.getColorCorrection(colorCorrectionRgba, 0)
        frame.acquirePointCloud().use { pointCloud ->
            pointCloudRenderer.update(pointCloud)
            pointCloudRenderer.draw(viewMatrix, projectionMatrix)
        }

        if (anchorGoal?.trackingState == TrackingState.TRACKING) {
            anchorGoal?.pose?.toMatrix(anchorMatrixGoal, 0)

            virtualGoal.updateModelMatrix(anchorMatrixGoal, 1f)
            virtualGoal.draw(viewMatrix, projectionMatrix/*, colorCorrectionRgba*/)
        }

        if (anchorPointA?.trackingState == TrackingState.TRACKING) {
            anchorPointA?.pose?.toMatrix(anchorMatrixA, 0)

            virtualPoint.updateModelMatrix(anchorMatrixA, 1f)
            virtualPoint.draw(viewMatrix, projectionMatrix/*, colorCorrectionRgba*/)
        }

        if (anchorPointB?.trackingState == TrackingState.TRACKING) {
            anchorPointB?.pose?.toMatrix(anchorMatrixB, 0)

            virtualPoint.updateModelMatrix(anchorMatrixB, 1f)
            virtualPoint.draw(viewMatrix, projectionMatrix/*, colorCorrectionRgba*/)
        }

        when {
            anchorPointA != null && anchorPointB == null -> {
                drawLine(viewMatrix, projectionMatrix, anchorPointA, anchorGoal)
            }

            anchorPointA != null && anchorPointB != null -> {
                drawLine(viewMatrix, projectionMatrix, anchorPointA, anchorPointB)
                viewModel.measureDistanceOf2Points(anchorPointA, anchorPointB)
            }
        }
    }

    private fun centerTap(frame: Frame, camera: Camera, anchor: (Anchor) -> Anchor?) {
        if (camera.trackingState != TrackingState.TRACKING) return

        val hitTest = frame.hitTest(vectorX, vectorY)

        for (hit in hitTest) {
            val trackable = hit.trackable

            if (
                (trackable is Plane
                        && trackable.isPoseInPolygon(hit.hitPose)
                        && PlaneRenderer.calculateDistanceToPlane(hit.hitPose, camera.pose) > 0)
                || (trackable is Point
                        && trackable.orientationMode
                        == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
            ) {
                /*val modelAnchor = */anchor.invoke(trackable.createAnchor(hit.hitPose))
//                future = session?.hostCloudAnchorAsync(modelAnchor, 300, null)
                break
            }
        }
    }

    private fun drawLine(
        cameraView: FloatArray?,
        cameraPerspective: FloatArray?,
        anchor1: Anchor?,
        anchor2: Anchor?,
    ) {
        if (anchor1 == null) return
        if (anchor2 == null) return

        val point1 = Vector3f(anchor1.pose.tx(), anchor1.pose.ty(), anchor1.pose.tz())
        val point2 = Vector3f(anchor2.pose.tx(), anchor2.pose.ty(), anchor2.pose.tz())

        strokes.clear()
        strokes.add(
            ArrayList<Vector3f>().also {
                it.add(point1)
                it.add(point2)
            }
        )

//        lineRenderer.setColor(AppSettings.getColor())
        virtualLine.updateStrokes(listOf(listOf(point1, point2)))

        virtualLine.draw(
            cameraView,
            cameraPerspective,
            surfaceWidth,
            surfaceHeight
        )
    }

    private fun addPoint() {
        when {
            anchorPointA == null -> addPointA = true
            anchorPointB == null -> addPointB = true
        }
    }

    private fun onClear() {
        anchorPointA?.detach()
        anchorPointA = null
        anchorPointB?.detach()
        anchorPointB = null

        addPointA = false
        addPointB = false

        viewModel.reset()

        /*if (future != null) {
            future?.cancel()
            future = null
        }*/
    }
}