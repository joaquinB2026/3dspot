package com.galaxy.dspot

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

class GalaxyRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private lateinit var starBuffer: FloatBuffer
    private lateinit var starColorBuffer: FloatBuffer
    private var starCount = 3000
    private var starProgram = 0

    var rotationY = 0f
    var rotationX = 15f
    var zoom = -6f
    private var time = 0f

    var onFrameCallback: (() -> Unit)? = null

    private val STAR_VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        uniform float uTime;
        attribute vec4 aPosition;
        attribute vec4 aColor;
        varying vec4 vColor;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            float twinkle = 0.6 + 0.4 * sin(uTime * 2.0 + aPosition.x * 10.0);
            vColor = vec4(aColor.rgb * twinkle, aColor.a);
            gl_PointSize = aColor.a * 4.0;
        }
    """.trimIndent()

    private val STAR_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec4 vColor;
        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord);
            if (dist > 0.5) discard;
            float alpha = 1.0 - smoothstep(0.2, 0.5, dist);
            gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.01f, 0.01f, 0.05f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        starProgram = buildProgram(STAR_VERTEX_SHADER, STAR_FRAGMENT_SHADER)
        generateGalaxy()
    }

    private fun generateGalaxy() {
        val positions = FloatArray(starCount * 3)
        val colors = FloatArray(starCount * 4)
        val arms = 3
        val random = Random(42)

        for (i in 0 until starCount) {
            val arm = i % arms
            val t = random.nextFloat()
            val angle = (arm * 2f * PI.toFloat() / arms) + t * 4f * PI.toFloat()
            val radius = t * 4f
            val spread = random.nextGaussian().toFloat() * 0.3f

            positions[i * 3] = cos(angle) * radius + spread
            positions[i * 3 + 1] = (random.nextFloat() - 0.5f) * 0.3f
            positions[i * 3 + 2] = sin(angle) * radius + spread

            val core = 1f - t
            colors[i * 4] = 0.4f + t * 0.6f
            colors[i * 4 + 1] = 0.3f + core * 0.4f
            colors[i * 4 + 2] = 1f
            colors[i * 4 + 3] = 0.5f + core * 0.5f
        }

        for (i in 0 until 200) {
            val idx = (starCount - 200 + i) * 3
            val cidx = (starCount - 200 + i) * 4
            val r = random.nextFloat() * 0.5f
            val a = random.nextFloat() * 2f * PI.toFloat()
            positions[idx] = cos(a) * r
            positions[idx + 1] = (random.nextFloat() - 0.5f) * 0.1f
            positions[idx + 2] = sin(a) * r
            colors[cidx] = 1f; colors[cidx+1] = 0.9f; colors[cidx+2] = 1f; colors[cidx+3] = 1f
        }

        starBuffer = floatBuffer(positions)
        starColorBuffer = floatBuffer(colors)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        time += 0.016f

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, zoom, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        val rotMat = FloatArray(16)
        Matrix.setRotateM(rotMat, 0, rotationX, 1f, 0f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, rotMat, 0)
        Matrix.setRotateM(rotMat, 0, rotationY, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, rotMat, 0)

        rotationY += 0.1f
        drawStars()
        onFrameCallback?.invoke()
    }

    private fun drawStars() {
        GLES20.glUseProgram(starProgram)
        val mvpLoc = GLES20.glGetUniformLocation(starProgram, "uMVPMatrix")
        val timeLoc = GLES20.glGetUniformLocation(starProgram, "uTime")
        val posLoc = GLES20.glGetAttribLocation(starProgram, "aPosition")
        val colLoc = GLES20.glGetAttribLocation(starProgram, "aColor")

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(timeLoc, time)

        starBuffer.position(0)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, starBuffer)
        GLES20.glEnableVertexAttribArray(posLoc)

        starColorBuffer.position(0)
        GLES20.glVertexAttribPointer(colLoc, 4, GLES20.GL_FLOAT, false, 0, starColorBuffer)
        GLES20.glEnableVertexAttribArray(colLoc)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 100f)
    }

    private fun buildProgram(vertSrc: String, fragSrc: String): Int {
        val vert = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        val frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vert)
        GLES20.glAttachShader(prog, frag)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun floatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }

    private fun Random.nextGaussian(): Double {
        var u: Double; var v: Double; var s: Double
        do {
            u = nextDouble() * 2 - 1
            v = nextDouble() * 2 - 1
            s = u * u + v * v
        } while (s >= 1 || s == 0.0)
        return u * sqrt(-2.0 * ln(s) / s)
    }
}
