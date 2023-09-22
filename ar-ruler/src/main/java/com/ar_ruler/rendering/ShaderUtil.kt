package com.ar_ruler.rendering

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object ShaderUtil {
    fun loadGLShader(tag: String?, context: Context, type: Int, filename: String): Int {
        val code = readShaderFileFromAssets(context, filename)
        var shader = GLES20.glCreateShader(type)

        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)

        // Get the compilation status.
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(tag, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            shader = 0
        }

        if (shader == 0) {
            throw RuntimeException("Error creating shader.")
        }

        return shader
    }

    fun checkGLError(tag: String?, label: String) {
        var lastError = GLES20.GL_NO_ERROR
        // Drain the queue of all errors.
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            Log.e(tag, "$label: glError $error")
            lastError = error
        }
        if (lastError != GLES20.GL_NO_ERROR) {
            throw RuntimeException("$label: glError $lastError")
        }
    }

    private fun readShaderFileFromAssets(context: Context, filename: String): String {
        context.assets.open(filename).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val sb = StringBuilder()
                var line = ""

                while (reader.readLine()?.also { line = it } != null) {
                    val tokens = line.split(" ".toRegex()).toTypedArray()
                    if (tokens[0] == "#include") {
                        var includeFilename = tokens[1]
                        includeFilename = includeFilename.replace("\"", "")
                        if (includeFilename == filename) {
                            throw IOException("Do not include the calling file.")
                        }
                        sb.append(readShaderFileFromAssets(context, includeFilename))
                    } else {
                        sb.append(line).append("\n")
                    }
                }

                return sb.toString()
            }
        }
    }
}