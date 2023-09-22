package com.ar_ruler.rendering

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.vecmath.Vector3f

class LineRenderer {

    companion object {
        private val TAG = LineRenderer::class.java.simpleName

        private const val VERTEX_SHADER_NAME = "shaders/line.vert"
        private const val FRAGMENT_SHADER_NAME = "shaders/line.frag"

        private const val FLOATS_PER_POINT = 3
        private const val BYTES_PER_FLOAT = 4
        private const val BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT
    }

    private val mModelMatrix = FloatArray(16)
    private val mModelViewMatrix = FloatArray(16)
    private val mModelViewProjectionMatrix = FloatArray(16)

    private var mPositionAttribute = 0
    private var mPreviousAttribute = 0
    private var mNextAttribute = 0
    private var mSideAttribute = 0
    private var mWidthAttribute = 0

    private var mCountersAttribute = 0

    private var mProjectionUniform = 0
    private var mModelViewUniform = 0
    private var mResolutionUniform = 0
    private var mLineWidthUniform = 0
    private var mColorUniform = 0
    private var mOpacityUniform = 0
    private var mNearUniform = 0
    private var mFarUniform = 0
    private var mSizeAttenuationUniform = 0
    private var mDrawModeUniform = 0
    private var mNearCutoffUniform = 0
    private var mFarCutoffUniform = 0

    private var mVisibility = 0
    private var mAlphaTest = 0

    private var mPositions: FloatArray? = null
    private var mCounters: FloatArray? = null
    private var mNext: FloatArray? = null
    private var mSide: FloatArray? = null
    private var mWidth: FloatArray? = null
    private var mPrevious: FloatArray? = null

    private var mPositionAddress = 0
    private var mPreviousAddress = 0
    private var mNextAddress = 0
    private var mSideAddress = 0
    private var mWidthAddress = 0
    private var mCounterAddress = 0

    private var mNumPoints = 0
    private var mNumBytes = 0

    private var mVbo = 0
    private var mVboSize = 0

    private var mProgramName = 0
    private var lineWidth = .2f

    private var color: Vector3f = Vector3f(1f, 1f, 1f)

    private var mLineDepthScaleUniform = 0
    private var mLineDepthScale = .1f

    fun createOnGlThread(context: Context) {
        ShaderUtil.checkGLError(TAG, "before create")
        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        mVbo = buffers[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo)
        mVboSize = 0
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        ShaderUtil.checkGLError(TAG, "buffer alloc")

        val vertexShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME)
        val fragmentShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME)

        mProgramName = GLES20.glCreateProgram()

        GLES20.glAttachShader(mProgramName, vertexShader)
        GLES20.glAttachShader(mProgramName, fragmentShader)
        GLES20.glLinkProgram(mProgramName)
        GLES20.glUseProgram(mProgramName)
        ShaderUtil.checkGLError(TAG, "program")

        mPositionAttribute = GLES20.glGetAttribLocation(mProgramName, "position")
        mPreviousAttribute = GLES20.glGetAttribLocation(mProgramName, "previous")
        mNextAttribute = GLES20.glGetAttribLocation(mProgramName, "next")
        mSideAttribute = GLES20.glGetAttribLocation(mProgramName, "side")
        mWidthAttribute = GLES20.glGetAttribLocation(mProgramName, "width")
        mCountersAttribute = GLES20.glGetAttribLocation(mProgramName, "counters")
        mProjectionUniform = GLES20.glGetUniformLocation(mProgramName, "projectionMatrix")
        mModelViewUniform = GLES20.glGetUniformLocation(mProgramName, "modelViewMatrix")
        mResolutionUniform = GLES20.glGetUniformLocation(mProgramName, "resolution")
        mLineWidthUniform = GLES20.glGetUniformLocation(mProgramName, "lineWidth")
        mColorUniform = GLES20.glGetUniformLocation(mProgramName, "color")
        mOpacityUniform = GLES20.glGetUniformLocation(mProgramName, "opacity")
        mNearUniform = GLES20.glGetUniformLocation(mProgramName, "near")
        mFarUniform = GLES20.glGetUniformLocation(mProgramName, "far")
        mSizeAttenuationUniform = GLES20.glGetUniformLocation(mProgramName, "sizeAttenuation")
        mVisibility = GLES20.glGetUniformLocation(mProgramName, "visibility")
        mAlphaTest = GLES20.glGetUniformLocation(mProgramName, "alphaTest")
        mDrawModeUniform = GLES20.glGetUniformLocation(mProgramName, "drawMode")
        mNearCutoffUniform = GLES20.glGetUniformLocation(mProgramName, "nearCutOff")
        mFarCutoffUniform = GLES20.glGetUniformLocation(mProgramName, "farCutOff")
        mLineDepthScaleUniform = GLES20.glGetUniformLocation(mProgramName, "lineDepthScale")

        ShaderUtil.checkGLError(TAG, "program  params")
        Matrix.setIdentityM(mModelMatrix, 0)
    }

    @Suppress("unused")
    fun setColor(color: Vector3f) {
        this.color = Vector3f(color)
    }

    fun draw(
        cameraView: FloatArray?,
        cameraPerspective: FloatArray?,
        screenWidth: Float,
        screenHeight: Float,
    ) {
        upload()

        Matrix.multiplyMM(mModelViewMatrix, 0, cameraView, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mModelViewProjectionMatrix, 0, cameraPerspective, 0, mModelViewMatrix, 0)
        ShaderUtil.checkGLError(TAG, "Before draw")
        GLES20.glUseProgram(mProgramName)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo)
        GLES20.glVertexAttribPointer(mPositionAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, mPositionAddress)
        GLES20.glVertexAttribPointer(mPreviousAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, mPreviousAddress)
        GLES20.glVertexAttribPointer(mNextAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, mNextAddress)
        GLES20.glVertexAttribPointer(mSideAttribute, 1, GLES20.GL_FLOAT, false, BYTES_PER_FLOAT, mSideAddress)
        GLES20.glVertexAttribPointer(mWidthAttribute, 1, GLES20.GL_FLOAT, false, BYTES_PER_FLOAT, mWidthAddress)
        GLES20.glVertexAttribPointer(mCountersAttribute, 1, GLES20.GL_FLOAT, false, BYTES_PER_FLOAT, mCounterAddress)
        GLES20.glUniformMatrix4fv(mModelViewUniform, 1, false, mModelViewMatrix, 0)
        GLES20.glUniformMatrix4fv(mProjectionUniform, 1, false, cameraPerspective, 0)
        GLES20.glUniform2f(mResolutionUniform, screenWidth, screenHeight)
        GLES20.glUniform1f(mLineWidthUniform, 0.01f)
        GLES20.glUniform3f(mColorUniform, color.x, color.y, color.z)
        GLES20.glUniform1f(mOpacityUniform, 1.0f)
        GLES20.glUniform1f(mNearUniform, 0.001f)
        GLES20.glUniform1f(mFarUniform, 10000.0f)
        GLES20.glUniform1f(mSizeAttenuationUniform, 1.0f)
        GLES20.glUniform1f(mVisibility, 1.0f)
        GLES20.glUniform1f(mAlphaTest, 1.0f)
//        GLES20.glUniform1f(mDrawModeUniform, if (mDrawMode) 1.0f else 0.0f)
//        GLES20.glUniform1f(mNearCutoffUniform, mDrawDistance - 0.0075f)
//        GLES20.glUniform1f(mFarCutoffUniform, mDrawDistance + 0.0075f)
        GLES20.glUniform1f(mLineDepthScaleUniform, mLineDepthScale)
        GLES20.glEnableVertexAttribArray(mPositionAttribute)
        GLES20.glEnableVertexAttribArray(mPreviousAttribute)
        GLES20.glEnableVertexAttribArray(mNextAttribute)
        GLES20.glEnableVertexAttribArray(mSideAttribute)
        GLES20.glEnableVertexAttribArray(mWidthAttribute)
        GLES20.glEnableVertexAttribArray(mCountersAttribute)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mNumBytes)
        GLES20.glDisableVertexAttribArray(mCountersAttribute)
        GLES20.glDisableVertexAttribArray(mWidthAttribute)
        GLES20.glDisableVertexAttribArray(mSideAttribute)
        GLES20.glDisableVertexAttribArray(mNextAttribute)
        GLES20.glDisableVertexAttribArray(mPreviousAttribute)
        GLES20.glDisableVertexAttribArray(mPositionAttribute)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        ShaderUtil.checkGLError(TAG, "Draw")
    }

    fun updateStrokes(strokes: List<List<Vector3f>>) {
        mNumPoints = 0

        for (l in strokes) {
            mNumPoints += l.size * 2 + 2
        }

        ensureCapacity(mNumPoints)

        var offset = 0

        for (l in strokes) {
            offset = addLine(l, offset)
        }

        mNumBytes = offset
    }

    private fun ensureCapacity(numPoints: Int) {
        var count = mSide?.size ?: 1024

        while (count < numPoints) {
            count += 1024
        }

        if (mSide == null || mSide!!.size < count) {
            mPositions = FloatArray(count * 3)
            mNext = FloatArray(count * 3)
            mPrevious = FloatArray(count * 3)
            mCounters = FloatArray(count)
            mSide = FloatArray(count)
            mWidth = FloatArray(count)
        }
    }

    private fun addLine(line: List<Vector3f>, offset: Int): Int {
        if (line.size < 2) return offset

        var resultOffset = offset
        val lineSize = line.size

        for (i in 0 until lineSize) {

            val iM1 = if (i - 1 < 0) 0 else i - 1
            val iP1 = if (i + 1 > lineSize - 1) i else i + 1

            val c = i.toFloat() / lineSize
            val current: Vector3f = line[i]
            val previous: Vector3f = line[iM1]
            val next: Vector3f = line[iP1]

            if (i == 0) {
                setMemory(resultOffset++, current, previous, next, c, lineWidth, 1f)
            }

            setMemory(resultOffset++, current, previous, next, c, lineWidth, 1f)
            setMemory(resultOffset++, current, previous, next, c, lineWidth, -1f)

            if (i == lineSize - 1) {
                setMemory(resultOffset++, current, previous, next, c, lineWidth, -1f)
            }
        }

        return resultOffset
    }

    private fun setMemory(
        index: Int,
        pos: Vector3f,
        prev: Vector3f,
        next: Vector3f,
        counter: Float,
        width: Float,
        side: Float,
    ) {
        mPositions?.set(index * 3, pos.x)
        mPositions?.set(index * 3 + 1, pos.y)
        mPositions?.set(index * 3 + 2, pos.z)
        mNext?.set(index * 3, next.x)
        mNext?.set(index * 3 + 1, next.y)
        mNext?.set(index * 3 + 2, next.z)
        mPrevious?.set(index * 3, prev.x)
        mPrevious?.set(index * 3 + 1, prev.y)
        mPrevious?.set(index * 3 + 2, prev.z)
        mCounters?.set(index, counter)
        mSide?.set(index, side)
        mWidth?.set(index, width)
    }

    private fun upload() {
        val current = toFloatBuffer(mPositions)
        val next = toFloatBuffer(mNext)
        val previous = toFloatBuffer(mPrevious)
        val side = toFloatBuffer(mSide)
        val width = toFloatBuffer(mWidth)
        val counter = toFloatBuffer(mCounters)

        mPositionAddress = 0
        mNextAddress = 0 + mNumBytes * 3 * BYTES_PER_FLOAT
        mPreviousAddress = mNextAddress + mNumBytes * 3 * BYTES_PER_FLOAT
        mSideAddress = mPreviousAddress + mNumBytes * 3 * BYTES_PER_FLOAT
        mWidthAddress = mSideAddress + mNumBytes * BYTES_PER_FLOAT
        mCounterAddress = mWidthAddress + mNumBytes * BYTES_PER_FLOAT
        mVboSize = mCounterAddress + mNumBytes * BYTES_PER_FLOAT

        ShaderUtil.checkGLError(TAG, "before update")

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, mPositionAddress, mNumBytes * 3 * BYTES_PER_FLOAT,
            current
        )
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, mNextAddress, mNumBytes * 3 * BYTES_PER_FLOAT,
            next
        )
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, mPreviousAddress, mNumBytes * 3 * BYTES_PER_FLOAT,
            previous
        )
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, mSideAddress, mNumBytes * BYTES_PER_FLOAT,
            side
        )
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, mWidthAddress, mNumBytes * BYTES_PER_FLOAT,
            width
        )
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, mCounterAddress, mNumBytes * BYTES_PER_FLOAT,
            counter
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        ShaderUtil.checkGLError(TAG, "after update")
    }

    private fun toFloatBuffer(data: FloatArray?): FloatBuffer {
        val bb = ByteBuffer.allocateDirect((data?.size ?: 1) * BYTES_PER_FLOAT)
        bb.order(ByteOrder.nativeOrder())

        val buff: FloatBuffer = bb.asFloatBuffer()
        buff.put(data)
        buff.position(0)

        return buff
    }
}