package com.galaxy.dspot

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: GalaxyRenderer
    private lateinit var spotify: SpotifyManager
    private lateinit var webView: WebView
    
    private val handler = Handler(Looper.getMainLooper())
    private var trackUpdateRunnable: Runnable? = null
    var isPlaying = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)

        spotify = SpotifyManager(this)
        val rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        // 1. Motor Nativo 3D (Fondo de Estrellas)
        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(2)
        renderer = GalaxyRenderer(this)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        rootLayout.addView(glView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // 2. Interfaz 3D de Alto Rendimiento (CSS3D en WebView)
        WebView.setWebContentsDebuggingEnabled(true)
        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Cuando termina de cargar el HTML, verificamos si ya había sesión
                    if (spotify.isAlreadyConnected()) {
                        notifyWebConnected(true)
                        startTrackUpdates()
                        renderer.beatPulse = 2f
                    }
                }
            }
            // Conectamos Kotlin con JavaScript
            addJavascriptInterface(WebAppInterface(), "Android")
        }
        rootLayout.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Cargamos el archivo HTML local que creaste
        webView.loadUrl("file:///android_asset/index.html")
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) { 
        super.onNewIntent(intent)
        handleIntent(intent) 
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            spotify.handleCallback(uri) { success ->
                runOnUiThread {
                    if (success) {
                        renderer.beatPulse = 3f
                        notifyWebConnected(true)
                        startTrackUpdates()
                    } else {
                        Toast.makeText(this, "Error conectando a Spotify", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun notifyWebConnected(isConnected: Boolean) {
        webView.evaluateJavascript("javascript:setConnected($isConnected);", null)
    }

    private fun startTrackUpdates() {
        trackUpdateRunnable?.let { handler.removeCallbacks(it) }
        trackUpdateRunnable = object : Runnable {
            override fun run() {
                spotify.getCurrentTrack { json ->
                    json?.let {
                        try {
                            val item = it.optJSONObject("item") ?: return@let
                            isPlaying = it.optBoolean("is_playing", false)
                            
                            val trackName = item.optString("name", "Desconocido")
                            val artistName = item.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: ""
                            
                            // Limpiamos los strings para evitar que rompan el JavaScript
                            val safeTrack = trackName.replace("'", "\\'").replace("\n", " ")
                            val safeArtist = artistName.replace("'", "\\'").replace("\n", " ")

                            runOnUiThread {
                                webView.evaluateJavascript("javascript:updateTrack('$safeTrack', '$safeArtist', $isPlaying);", null)
                                if (isPlaying) renderer.beatPulse = 0.4f
                            }
                        } catch (_: Exception) {}
                    }
                }
                handler.postDelayed(this, 5000) // Actualiza cada 5 segundos
            }
        }
        handler.post(trackUpdateRunnable!!)
    }

    // --- PUENTE: Estas funciones son llamadas DESDE el archivo index.html ---
    inner class WebAppInterface {
        @JavascriptInterface
        fun login() {
            runOnUiThread {
                renderer.beatPulse = 2f
                startActivity(spotify.getAuthIntent())
            }
        }

        @JavascriptInterface
        fun playPause() {
            spotify.playPause(isPlaying) { ok ->
                if (ok) {
                    isPlaying = !isPlaying
                    runOnUiThread { startTrackUpdates() } // Forzamos refresh visual rápido
                }
            }
        }

        @JavascriptInterface
        fun skipNext() { 
            spotify.skipNext { runOnUiThread { renderer.beatPulse = 2f; startTrackUpdates() } } 
        }

        @JavascriptInterface
        fun skipPrev() { 
            spotify.skipPrevious { runOnUiThread { renderer.beatPulse = 2f; startTrackUpdates() } } 
        }

        @JavascriptInterface
        fun rotateGalaxy(dx: Float, dy: Float) {
            // El usuario está rotando la interfaz web, rotamos la galaxia en la misma dirección
            renderer.rotationY += dx * 0.15f
            renderer.rotationX += dy * 0.10f
        }
        
        @JavascriptInterface
        fun openSpotifyApp() {
            runOnUiThread {
                val pkg = "com.spotify.music"
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) { 
                    startActivity(intent) 
                } else {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) }
                    catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))) }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); glView.onResume() }
    override fun onPause() { super.onPause(); glView.onPause() }
    override fun onDestroy() { super.onDestroy(); trackUpdateRunnable?.let { handler.removeCallbacks(it) } }
}
