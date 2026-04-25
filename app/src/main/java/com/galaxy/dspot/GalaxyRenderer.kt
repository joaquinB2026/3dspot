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
    private val tempMatrix = FloatArray(16)

    private lateinit var starBuffer: FloatBuffer
    private lateinit var starColorBuffer: FloatBuffer
    private var starCount = 4000
    private var starProgram = 0

    var rotationY = 0f
    var rotationX = 20f
    var zoom = -7f
    private var time = 0f

    // Music reactivity
    var beatPulse = 0f
    var bassLevel = 0f

    private val VERT = """
        uniform mat4 uMVPMatrix;
        uniform float uTime;
        uniform float uPulse;
        attribute vec4 aPosition;
        attribute vec4 aColor;
        varying vec4 vColor;
        void main() {
            vec4 pos = aPosition;
            float dist = length(pos.xz);
            float wave = sin(uTime * 1.5 + dist * 2.0) * 0.04 * uPulse;
            pos.y += wave;
            gl_Position = uMVPMatrix * pos;
            float twinkle = 0.55 + 0.45 * sin(uTime * 3.0 + aPosition.x * 13.7 + aPosition.z * 9.3);
            vColor = vec4(aColor.rgb * twinkle, aColor.a);
            float size = aColor.a * 5.5 + uPulse * 1.5;
            gl_PointSize = size;
        }
    """.trimIndent()

    private val FRAG = """
        precision mediump float;
        varying vec4 vColor;
        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord);
            if (dist > 0.5) discard;
            float alpha = 1.0 - smoothstep(0.15, 0.5, dist);
            gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.008f, 0.008f, 0.035f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        starProgram = buildProgram(VERT, FRAG)
        generateGalaxy()
    }

    private fun generateGalaxy() {
        val positions = FloatArray(starCount * 3)
        val colors = FloatArray(starCount * 4)
        val arms = 4
        val rng = Random(123)

        for (i in 0 until starCount - 300) {
            val arm = i % arms
            val t = rng.nextFloat()
            val angle = (arm * 2f * PI.toFloat() / arms) + t * 5f * PI.toFloat()
            val radius = 0.3f + t * 4.5f
            val scatter = nextGaussian(rng).toFloat() * (0.15f + t * 0.35f)

            positions[i * 3 + 0] = cos(angle) * radius + scatter
            positions[i * 3 + 1] = nextGaussian(rng).toFloat() * 0.18f
            positions[i * 3 + 2] = sin(angle) * radius + scatter

            val core = (1f - t).coerceIn(0f, 1f)
            val hue = arm.toFloat() / arms
            // Teal/blue/purple palette matching Spotify dark
            colors[i * 4 + 0] = 0.1f + hue * 0.3f + t * 0.3f
            colors[i * 4 + 1] = 0.4f + core * 0.4f
            colors[i * 4 + 2] = 0.8f + core * 0.2f
            colors[i * 4 + 3] = 0.4f + core * 0.6f
        }

        // Bright core cluster
        for (i in (starCount - 300) until starCount) {
            val r = rng.nextFloat() * 0.6f
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val idx = i * 3
            val cidx = i * 4
            positions[idx + 0] = cos(a) * r
            positions[idx + 1] = (rng.nextFloat() - 0.5f) * 0.15f
            positions[idx + 2] = sin(a) * r
            // Spotify green glow for core
            colors[cidx + 0] = 0.1f + rng.nextFloat() * 0.2f
            colors[cidx + 1] = 0.7f + rng.nextFloat() * 0.3f
            colors[cidx + 2] = 0.3f + rng.nextFloat() * 0.3f
            colors[cidx + 3] = 0.7f + rng.nextFloat() * 0.3f
        }

        starBuffer = floatBuffer(positions)
        starColorBuffer = floatBuffer(colors)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        time += 0.014f
        beatPulse = (beatPulse * 0.92f)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, zoom, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        Matrix.setRotateM(tempMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, tempMatrix, 0)
        Matrix.setRotateM(tempMatrix, 0, rotationY, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, tempMatrix, 0)

        rotationY += 0.08f
        drawStars()
    }

    private fun drawStars() {
        GLES20.glUseProgram(starProgram)
        val mvpLoc = GLES20.glGetUniformLocation(starProgram, "uMVPMatrix")
        val timeLoc = GLES20.glGetUniformLocation(starProgram, "uTime")
        val pulseLoc = GLES20.glGetUniformLocation(starProgram, "uPulse")
        val posLoc = GLES20.glGetAttribLocation(starProgram, "aPosition")
        val colLoc = GLES20.glGetAttribLocation(starProgram, "aColor")

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(timeLoc, time)
        GLES20.glUniform1f(pulseLoc, 1f + beatPulse)

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
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, compileShader(GLES20.GL_VERTEX_SHADER, vertSrc))
        GLES20.glAttachShader(prog, compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc))
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun floatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(data); position(0) }

    private fun nextGaussian(rng: Random): Double {
        var u: Double; var v: Double; var s: Double
        do { u = rng.nextDouble() * 2 - 1; v = rng.nextDouble() * 2 - 1; s = u * u + v * v }
        while (s >= 1 || s == 0.0)
        return u * sqrt(-2.0 * ln(s) / s)
    }
}
