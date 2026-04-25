package com.galaxy.dspot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: GalaxyRenderer
    private lateinit var spotify: SpotifyManager
    private lateinit var btManager: BTManager
    private lateinit var rootLayout: FrameLayout
    private lateinit var overlayLayout: FrameLayout

    private var playerPanel: View? = null
    private var bluetoothPanel: View? = null
    private var searchPanel: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var trackUpdateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        spotify = SpotifyManager(this)
        btManager = BTManager(this)

        rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(2)
        renderer = GalaxyRenderer(this)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        rootLayout.addView(glView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        overlayLayout = FrameLayout(this)
        rootLayout.addView(overlayLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setupGestureControls()
        requestPermissions()
        buildHUD()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { handleCallback(it) }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { handleCallback(it) }
    }

    private fun handleCallback(uri: Uri) {
        if (spotify.handleCallback(uri)) {
            handler.post {
                Toast.makeText(this, "✅ Spotify connected!", Toast.LENGTH_SHORT).show()
                startTrackUpdates()
            }
        }
    }

    private fun buildHUD() {
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC0A0A1E.toInt())
            gravity = Gravity.CENTER
        }
        val navParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(64)
        ).apply { gravity = Gravity.BOTTOM }

        navBar.addView(makeNavButton("🎵", "Music") { togglePlayerPanel() })
        navBar.addView(makeNavButton("📡", "Devices") { toggleBluetoothPanel() })
        navBar.addView(makeNavButton("🔍", "Search") { toggleSearchPanel() })
        navBar.addView(makeNavButton("🔑", "Login") { loginSpotify() })
        overlayLayout.addView(navBar, navParams)

        val title = TextView(this).apply {
            text = "3DSPOT"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            alpha = 0.8f
            setPadding(dpToPx(20), 0, 0, 0)
        }
        val titleParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.START; topMargin = dpToPx(48) }
        overlayLayout.addView(title, titleParams)
    }

    private fun makeNavButton(emoji: String, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { onClick() }
            addView(TextView(context).apply { text = emoji; textSize = 22f; gravity = Gravity.CENTER })
            addView(TextView(context).apply { text = label; textSize = 9f; setTextColor(0xAAFFFFFF.toInt()); gravity = Gravity.CENTER })
        }
    }

    private fun togglePlayerPanel() {
        if (playerPanel?.parent != null) { overlayLayout.removeView(playerPanel); playerPanel = null }
        else { playerPanel = buildPlayerPanel() }
    }

    private fun buildPlayerPanel(): View {
        val panel = buildDraggablePanel(dpToPx(20), dpToPx(120), dpToPx(340), dpToPx(220))
        val trackName = panel.findViewWithTag<TextView>("track_name")
        val artistName = panel.findViewWithTag<TextView>("artist_name")
        val btnPlay = panel.findViewWithTag<Button>("btn_play")
        val btnNext = panel.findViewWithTag<Button>("btn_next")
        val btnPrev = panel.findViewWithTag<Button>("btn_prev")

        btnPlay?.setOnClickListener {
            spotify.playPause(isPlaying) { success ->
                if (success) { isPlaying = !isPlaying; handler.post { btnPlay.text = if (isPlaying) "⏸" else "▶️" } }
            }
        }
        btnNext?.setOnClickListener { spotify.skipNext {} }
        btnPrev?.setOnClickListener { spotify.skipPrevious {} }

        spotify.getCurrentTrack { json ->
            json?.let {
                try {
                    val item = it.optJSONObject("item")
                    val name = item?.optString("name") ?: "Unknown"
                    val artist = item?.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: ""
                    isPlaying = it.optBoolean("is_playing", false)
                    handler.post {
                        trackName?.text = name
                        artistName?.text = artist
                        btnPlay?.text = if (isPlaying) "⏸" else "▶️"
                    }
                } catch (e: Exception) {}
            }
        }
        return panel
    }

    private fun toggleBluetoothPanel() {
        if (bluetoothPanel?.parent != null) { overlayLayout.removeView(bluetoothPanel); bluetoothPanel = null }
        else { bluetoothPanel = buildBluetoothPanel() }
    }

    private fun buildBluetoothPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE0D0D20.toInt())
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }
        val params = FrameLayout.LayoutParams(dpToPx(300), dpToPx(350)).apply {
            leftMargin = dpToPx(40); topMargin = dpToPx(130)
        }
        panel.addView(makePanelTitle("📡 Bluetooth Devices"))

        val devices = btManager.getPairedDevices()
        if (devices.isEmpty()) {
            panel.addView(TextView(this).apply {
                text = "No paired devices found"
                setTextColor(0xAAFFFFFF.toInt()); textSize = 13f
                setPadding(0, dpToPx(12), 0, 0)
            })
        } else {
            devices.forEach { device ->
                val connected = btManager.isConnected(device)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(if (connected) 0x224F8AFF.toInt() else 0x11FFFFFF.toInt())
                    setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, dpToPx(4), 0, dpToPx(4)); layoutParams = lp
                }
                row.addView(TextView(this).apply {
                    text = "${btManager.getDeviceType(device)} ${btManager.getDeviceName(device)}"
                    setTextColor(0xFFFFFFFF.toInt()); textSize = 13f
                })
                row.addView(TextView(this).apply {
                    text = if (connected) "● Connected" else "○ Not connected"
                    setTextColor(if (connected) 0xFF00E5A0.toInt() else 0x88FFFFFF.toInt()); textSize = 11f
                })
                panel.addView(row)
            }
        }
        makeDraggable(panel, params)
        overlayLayout.addView(panel, params)
        return panel
    }

    private fun toggleSearchPanel() {
        if (searchPanel?.parent != null) { overlayLayout.removeView(searchPanel); searchPanel = null }
        else { searchPanel = buildSearchPanel() }
    }

    private fun buildSearchPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE0D0D20.toInt())
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }
        val params = FrameLayout.LayoutParams(dpToPx(320), dpToPx(400)).apply {
            leftMargin = dpToPx(30); topMargin = dpToPx(130)
        }
        panel.addView(makePanelTitle("🔍 Search Tracks"))

        val searchField = EditText(this).apply {
            hint = "Song or artist..."
            setHintTextColor(0x66FFFFFF); setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x22FFFFFF)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dpToPx(8), 0, dpToPx(8)); layoutParams = lp
        }
        panel.addView(searchField)

        val resultsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val btnSearch = Button(this).apply {
            text = "Search"
            setBackgroundColor(0xFF1DB954.toInt()); setTextColor(0xFF000000.toInt())
            setOnClickListener {
                val q = searchField.text.toString()
                if (q.isNotEmpty()) {
                    spotify.searchTracks(q) { tracks ->
                        handler.post {
                            resultsLayout.removeAllViews()
                            tracks.take(6).forEach { track ->
                                val name = track.optString("name")
                                val artist = track.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: ""
                                val uri = track.optString("uri")
                                resultsLayout.addView(TextView(this).apply {
                                    text = "▶ $name\n$artist"
                                    setTextColor(0xFFFFFFFF.toInt()); textSize = 12f
                                    setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                                    setBackgroundColor(0x11FFFFFF)
                                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                    lp.setMargins(0, dpToPx(3), 0, dpToPx(3)); layoutParams = lp
                                    setOnClickListener { spotify.playTrack(uri) {} }
                                })
                            }
                        }
                    }
                }
            }
        }
        panel.addView(btnSearch)
        panel.addView(resultsLayout)
        makeDraggable(panel, params)
        overlayLayout.addView(panel, params)
        return panel
    }

    private fun buildDraggablePanel(x: Int, y: Int, w: Int, h: Int): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE0D0D20.toInt())
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }
        val params = FrameLayout.LayoutParams(w, h).apply { leftMargin = x; topMargin = y }
        panel.addView(makePanelTitle("🎵 Now Playing"))

        val trackName = TextView(this).apply {
            tag = "track_name"; text = "Not connected"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dpToPx(12), 0, dpToPx(4)); layoutParams = lp
        }
        panel.addView(trackName)

        val artistName = TextView(this).apply {
            tag = "artist_name"; text = "Login to Spotify first"
            setTextColor(0xAA1DB954.toInt()); textSize = 13f
        }
        panel.addView(artistName)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dpToPx(20), 0, 0); layoutParams = lp
        }
        controls.addView(Button(this).apply {
            tag = "btn_prev"; text = "⏮"; textSize = 20f
            setBackgroundColor(0x33FFFFFF); setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dpToPx(70), dpToPx(50)).apply { setMargins(dpToPx(4),0,dpToPx(4),0) }
        })
        controls.addView(Button(this).apply {
            tag = "btn_play"; text = "▶️"; textSize = 20f
            setBackgroundColor(0xFF1DB954.toInt()); setTextColor(0xFF000000.toInt())
            layoutParams = LinearLayout.LayoutParams(dpToPx(80), dpToPx(50)).apply { setMargins(dpToPx(4),0,dpToPx(4),0) }
        })
        controls.addView(Button(this).apply {
            tag = "btn_next"; text = "⏭"; textSize = 20f
            setBackgroundColor(0x33FFFFFF); setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dpToPx(70), dpToPx(50)).apply { setMargins(dpToPx(4),0,dpToPx(4),0) }
        })
        panel.addView(controls)
        makeDraggable(panel, params)
        overlayLayout.addView(panel, params)
        return panel
    }

    private fun makePanelTitle(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f
        setTextColor(0xFFFFFFFF.toInt())
        setTypeface(null, android.graphics.Typeface.BOLD); alpha = 0.9f
    }

    private fun makeDraggable(view: View, params: FrameLayout.LayoutParams) {
        var dX = 0f; var dY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dX = v.x - event.rawX; dY = v.y - event.rawY; true }
                MotionEvent.ACTION_MOVE -> { v.x = event.rawX + dX; v.y = event.rawY + dY; true }
                else -> false
            }
        }
    }

    private fun setupGestureControls() {
        var lastX = 0f; var lastY = 0f
        glView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; true }
                MotionEvent.ACTION_MOVE -> {
                    renderer.rotationY += (event.x - lastX) * 0.3f
                    renderer.rotationX += (event.y - lastY) * 0.3f
                    lastX = event.x; lastY = event.y; true
                }
                else -> false
            }
        }
    }

    private fun loginSpotify() { startActivity(spotify.getAuthIntent()) }

    private fun startTrackUpdates() {
        trackUpdateRunnable = object : Runnable {
            override fun run() {
                spotify.getCurrentTrack { json ->
                    json?.let {
                        try {
                            val item = it.optJSONObject("item") ?: return@let
                            val name = item.optString("name")
                            val artist = item.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: ""
                            isPlaying = it.optBoolean("is_playing", false)
                            handler.post {
                                playerPanel?.findViewWithTag<TextView>("track_name")?.text = name
                                playerPanel?.findViewWithTag<TextView>("artist_name")?.text = artist
                            }
                        } catch (e: Exception) {}
                    }
                }
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(trackUpdateRunnable!!)
    }

    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
        val needed = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onResume() { super.onResume(); glView.onResume() }
    override fun onPause() { super.onPause(); glView.onPause() }
    override fun onDestroy() { super.onDestroy(); trackUpdateRunnable?.let { handler.removeCallbacks(it) } }
}
