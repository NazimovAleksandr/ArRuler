package com.ar_ruler.rendering

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import de.javagl.obj.Obj
import de.javagl.obj.ObjData
import de.javagl.obj.ObjReader
import de.javagl.obj.ObjUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.sqrt

class ObjectRenderer {

    companion object {
        private val TAG = ObjectRenderer::class.java.simpleName
    }

    /**
     * Blend mode.
     *
     * @see .setBlendMode
     */
    enum class BlendMode {
        /** Multiplies the destination color by the source alpha.  */
        Shadow,

        /** Normal alpha blending.  */
        Grid
    }

    // Shader names.
    private val vertexShaderName = "shaders/object.vert"
    private val fragmentShaderName = "shaders/object.frag"

    private val coordsPerVertex = 3
    private val defaultColor = floatArrayOf(255f, 255f, 255f, 255f)

    // Note: the last component must be zero to avoid applying the translational part of the matrix.
    private val lightDirection = floatArrayOf(0.250f, 0.866f, 0.433f, 0.0f)
    private val viewLightDirection = FloatArray(4)

    // Object vertex buffer variables.
    private var vertexBufferId = 0
    private var verticesBaseAddress = 0
    private var texCoordsBaseAddress = 0
    private var normalsBaseAddress = 0
    private var indexBufferId = 0
    private var indexCount = 0

    private var program = 0
    private val textures = IntArray(1)

    // Shader location: model view projection matrix.
    private var modelViewUniform = 0
    private var modelViewProjectionUniform = 0

    // Shader location: object attributes.
    private var positionAttribute = 0
    private var normalAttribute = 0
    private var texCoordAttribute = 0

    // Shader location: texture sampler.
    private var textureUniform = 0

    // Shader location: environment properties.
    private var lightingParametersUniform = 0

    // Shader location: material properties.
    private var materialParametersUniform = 0

    // Shader location: color correction property
    private var colorCorrectionParameterUniform = 0

    // Shader location: object color property (to change the primary color of the object).
    private var colorUniform = 0

    private var blendMode: BlendMode? = null

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val modelViewProjectionMatrix = FloatArray(16)

    // Set some default material properties to use for lighting.
    private var ambient = 0.3f
    private var diffuse = 1.0f
    private var specular = 1.0f
    private var specularPower = 6.0f

    fun createOnGlThread(context: Context, objAssetName: String, diffuseTextureAssetName: String? = null) {
        val vertexShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, vertexShaderName)
        val fragmentShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, fragmentShaderName)

        program = GLES20.glCreateProgram()

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        GLES20.glUseProgram(program)
        ShaderUtil.checkGLError(TAG, "Program creation")

        modelViewUniform = GLES20.glGetUniformLocation(program, "u_ModelView")
        modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position")
        normalAttribute = GLES20.glGetAttribLocation(program, "a_Normal")
        texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "u_Texture")
        lightingParametersUniform = GLES20.glGetUniformLocation(program, "u_LightingParameters")
        materialParametersUniform = GLES20.glGetUniformLocation(program, "u_MaterialParameters")
        colorCorrectionParameterUniform = GLES20.glGetUniformLocation(program, "u_ColorCorrectionParameters")
        colorUniform = GLES20.glGetUniformLocation(program, "u_ObjColor")

        ShaderUtil.checkGLError(TAG, "Program parameters")

        // Read the texture.
        readTexture(context, diffuseTextureAssetName)

        // Read the obj file.
        val objInputStream = context.assets.open(objAssetName)
        var obj: Obj = ObjReader.read(objInputStream)

        // Prepare the Obj so that its structure is suitable for
        // rendering with OpenGL:
        // 1. Triangulate it
        // 2. Make sure that texture coordinates are not ambiguous
        // 3. Make sure that normals are not ambiguous
        // 4. Convert it to single-indexed data
        obj = ObjUtils.convertToRenderable(obj)

        // OpenGL does not use Java arrays. ByteBuffers are used instead to provide data in a format
        // that OpenGL understands.

        // Obtain the data from the OBJ, as direct buffers:
        val wideIndices: IntBuffer = ObjData.getFaceVertexIndices(obj, 3)
        val vertices: FloatBuffer = ObjData.getVertices(obj)
        val texCoords: FloatBuffer = ObjData.getTexCoords(obj, 2)
        val normals: FloatBuffer = ObjData.getNormals(obj)

        // Convert int indices to shorts for GL ES 2.0 compatibility
        val indices = ByteBuffer.allocateDirect(2 * wideIndices.limit())
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()

        while (wideIndices.hasRemaining()) {
            indices.put(wideIndices.get().toShort())
        }

        indices.rewind()

        val buffers = IntArray(2)
        GLES20.glGenBuffers(2, buffers, 0)

        vertexBufferId = buffers[0]
        indexBufferId = buffers[1]

        // Load vertex buffer
        verticesBaseAddress = 0
        texCoordsBaseAddress = 0 + 4 * vertices.limit()
        normalsBaseAddress = texCoordsBaseAddress + 4 * texCoords.limit()
        val totalBytes = normalsBaseAddress + 4 * normals.limit()

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalBytes, null, GLES20.GL_STATIC_DRAW)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, verticesBaseAddress, 4 * vertices.limit(), vertices)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, texCoordsBaseAddress, 4 * texCoords.limit(), texCoords)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, normalsBaseAddress, 4 * normals.limit(), normals)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // Load index buffer
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        indexCount = indices.limit()

        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * indexCount, indices, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        ShaderUtil.checkGLError(TAG, "OBJ buffer load")
        Matrix.setIdentityM(modelMatrix, 0)
    }

    private fun readTexture(context: Context, diffuseTextureAssetName: String?) {
        diffuseTextureAssetName ?: return

        val textureBitmap = BitmapFactory.decodeStream(
            context.assets.open(diffuseTextureAssetName)
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGenTextures(textures.size, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        textureBitmap.recycle()
        ShaderUtil.checkGLError(TAG, "Texture loading")
    }

    /**
     * Selects the blending mode for rendering.
     *
     * @param blendMode The blending mode. Null indicates no blending (opaque rendering).
     */
    fun setBlendMode(blendMode: BlendMode?) {
        this.blendMode = blendMode
    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param scaleFactor A separate scaling factor to apply before the `modelMatrix`.
     * @see Matrix
     */
    fun updateModelMatrix(modelMatrix: FloatArray?, scaleFactor: Float) {
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        scaleMatrix[0] = scaleFactor
        scaleMatrix[5] = scaleFactor
        scaleMatrix[10] = scaleFactor
        Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0)
    }

    /**
     * Sets the surface characteristics of the rendered model.
     *
     * @param ambient Intensity of non-directional surface illumination.
     * @param diffuse Diffuse (matte) surface reflectivity.
     * @param specular Specular (shiny) surface reflectivity.
     * @param specularPower Surface shininess. Larger values result in a smaller, sharper specular
     * highlight.
     */
    fun setMaterialProperties(
        ambient: Float, diffuse: Float, specular: Float, specularPower: Float,
    ) {
        this.ambient = ambient
        this.diffuse = diffuse
        this.specular = specular
        this.specularPower = specularPower
    }

    fun draw(
        cameraView: FloatArray,
        cameraPerspective: FloatArray,
        colorCorrectionRgba: FloatArray = defaultColor,
        objColor: FloatArray = defaultColor,
    ) {
        ShaderUtil.checkGLError(TAG, "Before draw")

        // Build the ModelView and ModelViewProjection matrices
        // for calculating object position and light.
        Matrix.multiplyMM(
            /* result = */ modelViewMatrix,
            /* resultOffset = */ 0,
            /* lhs = */ cameraView,
            /* lhsOffset = */ 0,
            /* rhs = */ modelMatrix,
            /* rhsOffset = */ 0
        )

        Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0)
        GLES20.glUseProgram(program)

        // Set the lighting environment properties.
        Matrix.multiplyMV(viewLightDirection, 0, modelViewMatrix, 0, lightDirection, 0)
        normalizeVec3(viewLightDirection)
        GLES20.glUniform4f(
            lightingParametersUniform,
            viewLightDirection[0],
            viewLightDirection[1],
            viewLightDirection[2],
            1f
        )

        GLES20.glUniform4fv(colorCorrectionParameterUniform, 1, colorCorrectionRgba, 0)
        GLES20.glUniform4fv(colorUniform, 1, objColor, 0)

        // Set the object material properties.
        GLES20.glUniform4f(materialParametersUniform, ambient, diffuse, specular, specularPower)

        // Attach the object texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glUniform1i(textureUniform, 0)

        // Set the vertex attributes.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glVertexAttribPointer(
            positionAttribute, coordsPerVertex, GLES20.GL_FLOAT, false, 0, verticesBaseAddress
        )
        GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 0, normalsBaseAddress)
        GLES20.glVertexAttribPointer(
            texCoordAttribute, 2, GLES20.GL_FLOAT, false, 0, texCoordsBaseAddress
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0)
        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0)

        GLES20.glEnableVertexAttribArray(positionAttribute)
        GLES20.glEnableVertexAttribArray(normalAttribute)
        GLES20.glEnableVertexAttribArray(texCoordAttribute)

        if (blendMode != null) {
            GLES20.glDepthMask(false)
            GLES20.glEnable(GLES20.GL_BLEND)

            when (blendMode) {
                BlendMode.Shadow ->           // Multiplicative blending function for Shadow.
                    GLES20.glBlendFunc(GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA)

                BlendMode.Grid ->           // Grid, additive blending function.
                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

                else -> {}
            }
        }

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        if (blendMode != null) {
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDepthMask(true)
        }

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionAttribute)
        GLES20.glDisableVertexAttribArray(normalAttribute)
        GLES20.glDisableVertexAttribArray(texCoordAttribute)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        ShaderUtil.checkGLError(TAG, "After draw")
    }

    private fun normalizeVec3(v: FloatArray) {
        val reciprocalLength = 1.0f / sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()).toFloat()
        v[0] *= reciprocalLength
        v[1] *= reciprocalLength
        v[2] *= reciprocalLength
    }
}