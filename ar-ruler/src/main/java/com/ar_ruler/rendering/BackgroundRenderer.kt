package com.ar_ruler.rendering

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BackgroundRenderer {

    companion object {
        private val TAG = BackgroundRenderer::class.java.simpleName

        private const val VERTEX_SHADER_NAME = "shaders/screenquad.vert"
        private const val FRAGMENT_SHADER_NAME = "shaders/screenquad.frag"

        private const val COORDS_PER_VERTEX = 2
        private const val TEXCOORDS_PER_VERTEX = 2
        private const val FLOAT_SIZE = 4

        private val QUAD_COORDS = floatArrayOf(
            -1.0f, -1.0f, -1.0f, +1.0f, +1.0f, -1.0f, +1.0f, +1.0f
        )
    }

    private var quadCoords: FloatBuffer? = null
    private var quadTexCoords: FloatBuffer? = null

    private var quadProgram = 0

    private var quadPositionParam = 0
    private var quadTexCoordParam = 0
    private var textureId = -1
    private var suppressTimestampZeroRendering = true

    fun getTextureId(): Int {
        return textureId
    }

    fun createOnGlThread(context: Context) {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        val textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(textureTarget, textureId)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        val numVertices = 4
        if (numVertices != QUAD_COORDS.size / COORDS_PER_VERTEX) {
            throw RuntimeException("Unexpected number of vertices in BackgroundRenderer.")
        }
        val bbCoords = ByteBuffer.allocateDirect(QUAD_COORDS.size * FLOAT_SIZE)
        bbCoords.order(ByteOrder.nativeOrder())
        quadCoords = bbCoords.asFloatBuffer()
        quadCoords?.put(QUAD_COORDS)
        quadCoords?.position(0)
        val bbTexCoordsTransformed = ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE)
        bbTexCoordsTransformed.order(ByteOrder.nativeOrder())
        quadTexCoords = bbTexCoordsTransformed.asFloatBuffer()
        val vertexShader = ShaderUtil.loadGLShader(
            TAG,
            context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME
        )
        val fragmentShader = ShaderUtil.loadGLShader(
            TAG,
            context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME
        )
        quadProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(quadProgram, vertexShader)
        GLES20.glAttachShader(quadProgram, fragmentShader)
        GLES20.glLinkProgram(quadProgram)
        GLES20.glUseProgram(quadProgram)
        ShaderUtil.checkGLError(TAG, "Program creation")
        quadPositionParam = GLES20.glGetAttribLocation(quadProgram, "a_Position")
        quadTexCoordParam = GLES20.glGetAttribLocation(quadProgram, "a_TexCoord")
        ShaderUtil.checkGLError(TAG, "Program parameters")
    }

    fun suppressTimestampZeroRendering(suppressTimestampZeroRendering: Boolean) {
        this.suppressTimestampZeroRendering = suppressTimestampZeroRendering
    }

    /**
     * Draws the AR background image. The image will be drawn such that virtual content rendered with
     * the matrices provided by [com.google.ar.core.Camera.getViewMatrix] and
     * [com.google.ar.core.Camera.getProjectionMatrix] will
     * accurately follow static physical objects. This must be called **before** drawing virtual
     * content.
     */
    fun draw(frame: Frame) {
        // If display rotation changed (also includes view size change), we need to re-query the uv
        // coordinates for the screen rect, as they may have changed as well.
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED,
                quadTexCoords
            )
        }
        if (frame.timestamp == 0L && suppressTimestampZeroRendering) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            return
        }
        draw()
    }

    /**
     * Draws the camera image using the currently configured [BackgroundRenderer.quadTexCoords]
     * image texture coordinates.
     *
     *
     * The image will be center cropped if the camera sensor aspect ratio does not match the screen
     * aspect ratio, which matches the cropping behavior of [ ][Frame.transformCoordinates2d].
     */
    fun draw(
        imageWidth: Int, imageHeight: Int, screenAspectRatio: Float, cameraToDisplayRotation: Int,
    ) {
        // Crop the camera image to fit the screen aspect ratio.
        val imageAspectRatio = imageWidth.toFloat() / imageHeight
        val croppedWidth: Float
        val croppedHeight: Float
        if (screenAspectRatio < imageAspectRatio) {
            croppedWidth = imageHeight * screenAspectRatio
            croppedHeight = imageHeight.toFloat()
        } else {
            croppedWidth = imageWidth.toFloat()
            croppedHeight = imageWidth / screenAspectRatio
        }
        val u = (imageWidth - croppedWidth) / imageWidth * 0.5f
        val v = (imageHeight - croppedHeight) / imageHeight * 0.5f
        val texCoordTransformed: FloatArray = when (cameraToDisplayRotation) {
                90 -> floatArrayOf(1 - u, 1 - v, u, 1 - v, 1 - u, v, u, v)
                180 -> floatArrayOf(1 - u, v, 1 - u, 1 - v, u, v, u, 1 - v)
                270 -> floatArrayOf(u, v, 1 - u, v, u, 1 - v, 1 - u, 1 - v)
                0 -> floatArrayOf(u, 1 - v, u, v, 1 - u, 1 - v, 1 - u, v)
                else -> throw IllegalArgumentException("Unhandled rotation: $cameraToDisplayRotation")
            }

        // Write image texture coordinates.
        quadTexCoords?.position(0)
        quadTexCoords?.put(texCoordTransformed)
        draw()
    }

    /**
     * Draws the camera background image using the currently configured [ ][BackgroundRenderer.quadTexCoords] image texture coordinates.
     */
    private fun draw() {
        // Ensure position is rewound before use.
        quadTexCoords?.position(0)

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUseProgram(quadProgram)

        // Set the vertex positions.
        GLES20.glVertexAttribPointer(
            quadPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadCoords
        )

        // Set the texture coordinates.
        GLES20.glVertexAttribPointer(
            quadTexCoordParam, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadTexCoords
        )

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(quadPositionParam)
        GLES20.glEnableVertexAttribArray(quadTexCoordParam)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(quadPositionParam)
        GLES20.glDisableVertexAttribArray(quadTexCoordParam)

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw")
    }
}