package com.galaxy.dspot

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
    private var playlistPanel: View? = null
    private var searchPanel: View? = null
    private var loginPanel: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var trackUpdateRunnable: Runnable? = null
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

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
        spotify.handleCallback(uri) { success ->
            handler.post {
                if (success) {
                    isConnected = true
                    renderer.beatPulse = 3f
                    showToast3D("Spotify conectado!")
                    startTrackUpdates()
                    loginPanel?.let { dismissPanel(it); loginPanel = null }
                    handler.postDelayed({ if (playerPanel == null) showPlayerPanel() }, 600)
                } else {
                    showToast3D("Error al conectar con Spotify")
                }
            }
        }
    }

    // ─── HUD ──────────────────────────────────────────────────────────────────

    private fun buildHUD() {
        val title = TextView(this).apply {
            text = "3DSPOT"
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            alpha = 0.9f
            setPadding(dp(20), 0, 0, 0)
            setShadowLayer(12f, 0f, 0f, 0xFF1DB954.toInt())
        }
        overlayLayout.addView(title, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = dp(48)
        })

        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC050518.toInt())
            gravity = Gravity.CENTER
            elevation = 24f
        }
        navBar.addView(makeNavBtn("Musica") { togglePlayerPanel() })
        navBar.addView(makeNavBtn("Listas") { togglePlaylistPanel() })
        navBar.addView(makeNavBtn("Buscar") { toggleSearchPanel() })
        navBar.addView(makeNavBtn("Login") { toggleLoginPanel() })

        overlayLayout.addView(navBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(72)
        ).apply {
            gravity = Gravity.BOTTOM
        })
    }

    private fun makeNavBtn(label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener {
                renderer.beatPulse = 1.5f
                onClick()
            }
            addView(TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
            })
        }
    }

    // ─── Panel toggle ─────────────────────────────────────────────────────────

    private fun togglePlayerPanel() {
        if (playerPanel?.parent != null) { dismissPanel(playerPanel!!); playerPanel = null }
        else showPlayerPanel()
    }

    private fun showPlayerPanel() {
        playerPanel = buildPlayerPanel()
    }

    private fun togglePlaylistPanel() {
        if (playlistPanel?.parent != null) { dismissPanel(playlistPanel!!); playlistPanel = null }
        else { playlistPanel = buildPlaylistPanel() }
    }

    private fun toggleSearchPanel() {
        if (searchPanel?.parent != null) { dismissPanel(searchPanel!!); searchPanel = null }
        else { searchPanel = buildSearchPanel() }
    }

    private fun toggleLoginPanel() {
        if (loginPanel?.parent != null) { dismissPanel(loginPanel!!); loginPanel = null }
        else { loginPanel = buildLoginPanel() }
    }

    // ─── 3D Panel base ────────────────────────────────────────────────────────

    private fun make3DPanel(
        leftMargin: Int,
        topMargin: Int,
        width: Int,
        height: Int,
        rotY: Float = -8f,
        title: String,
        buildContent: (LinearLayout) -> Unit
    ): View {
        val container = FrameLayout(this)
        container.elevation = 16f

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEA06061A.toInt())
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        val topBorder = View(this).apply {
            setBackgroundColor(0xFF1DB954.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2))
        }
        panel.addView(topBorder)

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40))
            lp.setMargins(0, dp(6), 0, dp(10))
            layoutParams = lp
        }
        titleRow.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(8f, 0f, 0f, 0xFF1DB954.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(TextView(this).apply {
            text = "X"
            textSize = 16f
            setTextColor(0x88FFFFFF.toInt())
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener {
                if (container.parent is ViewGroup) {
                    dismissPanel(container)
                    when (title) {
                        "Reproduciendo" -> playerPanel = null
                        "Mis Playlists" -> playlistPanel = null
                        "Buscar" -> searchPanel = null
                        "Conectar Spotify" -> loginPanel = null
                    }
                }
            }
        })
        panel.addView(titleRow)

        buildContent(panel)

        container.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val params = FrameLayout.LayoutParams(width, height).apply {
            this.leftMargin = leftMargin
            this.topMargin = topMargin
        }

        container.rotationY = rotY
        container.cameraDistance = 8000f * resources.displayMetrics.density

        makeDraggable3D(container)
        overlayLayout.addView(container, params)

        container.alpha = 0f
        container.scaleX = 0.7f
        container.scaleY = 0.7f
        container.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        return container
    }

    private fun dismissPanel(view: View) {
        view.animate()
            .alpha(0f).scaleX(0.7f).scaleY(0.7f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { (view.parent as? ViewGroup)?.removeView(view) }
            .start()
    }

    // ─── Player Panel ─────────────────────────────────────────────────────────

    private fun buildPlayerPanel(): View {
        var trackNameTv: TextView? = null
        var artistTv: TextView? = null
        var btnPlay: TextView? = null

        val panel = make3DPanel(dp(16), dp(110), dp(340), dp(240), -6f, "Reproduciendo") { content ->

            val albumArtHolder = FrameLayout(this).apply {
                val lp = LinearLayout.LayoutParams(dp(64), dp(64))
                lp.gravity = Gravity.CENTER_HORIZONTAL
                lp.setMargins(0, 0, 0, dp(8))
                layoutParams = lp
            }
            albumArtHolder.addView(View(this).apply {
                setBackgroundColor(0xFF1DB954.toInt())
                layoutParams = FrameLayout.LayoutParams(dp(64), dp(64))
            })
            albumArtHolder.addView(TextView(this).apply {
                text = "~"
                textSize = 28f
                setTextColor(0xFF000000.toInt())
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
            content.addView(albumArtHolder)

            trackNameTv = TextView(this).apply {
                text = if (isConnected) "Cargando..." else "No conectado"
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            content.addView(trackNameTv)

            artistTv = TextView(this).apply {
                text = if (isConnected) "" else "Login para conectar Spotify"
                textSize = 12f
                setTextColor(0xBB1DB954.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, dp(2), 0, dp(12))
                layoutParams = lp
            }
            content.addView(artistTv)

            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            fun controlBtn(txt: String, isMain: Boolean, action: () -> Unit): TextView {
                return TextView(this).apply {
                    text = txt
                    textSize = if (isMain) 28f else 22f
                    gravity = Gravity.CENTER
                    setTextColor(if (isMain) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                    setBackgroundColor(if (isMain) 0xFF1DB954.toInt() else 0x33FFFFFF.toInt())
                    val size = if (isMain) dp(60) else dp(50)
                    val lp = LinearLayout.LayoutParams(size, size)
                    lp.setMargins(dp(6), 0, dp(6), 0)
                    layoutParams = lp
                    setOnClickListener { renderer.beatPulse = 2f; action() }
                }
            }

            val btnPrev = controlBtn("<<", false) { spotify.skipPrevious {} }
            btnPlay = controlBtn(if (isPlaying) "||" else ">", true) {
                spotify.playPause(isPlaying) { ok ->
                    if (ok) {
                        isPlaying = !isPlaying
                        handler.post { btnPlay?.text = if (isPlaying) "||" else ">" }
                    }
                }
            }
            val btnNext = controlBtn(">>", false) { spotify.skipNext {} }

            controls.addView(btnPrev)
            controls.addView(btnPlay)
            controls.addView(btnNext)
            content.addView(controls)
        }

        spotify.getCurrentTrack { json ->
            json?.let {
                try {
                    val item = it.optJSONObject("item") ?: return@let
                    val name = item.optString("name", "Desconocido")
                    val artist = item.optJSONArray("artists")
                        ?.optJSONObject(0)?.optString("name") ?: ""
                    isPlaying = it.optBoolean("is_playing", false)
                    handler.post {
                        trackNameTv?.text = name
                        artistTv?.text = artist
                        btnPlay?.text = if (isPlaying) "||" else ">"
                        renderer.beatPulse = 1f
                    }
                } catch (e: Exception) { /* ignore */ }
            }
        }
        return panel
    }

    // ─── Playlist Panel ───────────────────────────────────────────────────────

    private fun buildPlaylistPanel(): View {
        var listLayout: LinearLayout? = null

        val panel = make3DPanel(dp(20), dp(120), dp(330), dp(360), 6f, "Mis Playlists") { content ->
            if (!isConnected) {
                content.addView(TextView(this).apply {
                    text = "Conecta Spotify primero"
                    textSize = 13f
                    setTextColor(0xAAFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, dp(20), 0, 0)
                })
                return@make3DPanel
            }

            val scroll = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(290)
                )
            }
            listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            scroll.addView(listLayout)
            content.addView(scroll)

            listLayout!!.addView(TextView(this).apply {
                text = "Cargando playlists..."
                textSize = 12f
                setTextColor(0x88FFFFFF.toInt())
                gravity = Gravity.CENTER
            })

            spotify.getPlaylists { playlists ->
                handler.post {
                    listLayout!!.removeAllViews()
                    if (playlists.isEmpty()) {
                        listLayout!!.addView(TextView(this).apply {
                            text = "Sin playlists"
                            setTextColor(0x88FFFFFF.toInt())
                        })
                        return@post
                    }
                    playlists.forEach { pl ->
                        val name = pl.optString("name", "Playlist")
                        val tracks = pl.optJSONObject("tracks")?.optInt("total", 0) ?: 0
                        val uri = pl.optString("uri")

                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setBackgroundColor(0x15FFFFFF)
                            val lp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
                            )
                            lp.setMargins(0, dp(3), 0, dp(3))
                            layoutParams = lp
                            setPadding(dp(10), 0, dp(10), 0)
                            setOnClickListener {
                                spotify.playPlaylist(uri) {}
                                renderer.beatPulse = 3f
                            }
                        }

                        val textCol = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            val lp = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            )
                            lp.setMargins(dp(10), 0, 0, 0)
                            layoutParams = lp
                        }
                        textCol.addView(TextView(this).apply {
                            text = name
                            textSize = 13f
                            setTextColor(0xFFFFFFFF.toInt())
                            maxLines = 1
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        })
                        textCol.addView(TextView(this).apply {
                            text = "$tracks canciones"
                            textSize = 10f
                            setTextColor(0x881DB954.toInt())
                        })
                        row.addView(textCol)
                        row.addView(TextView(this).apply {
                            text = ">"
                            textSize = 14f
                            setTextColor(0xFF1DB954.toInt())
                        })
                        listLayout!!.addView(row)
                    }
                }
            }
        }
        return panel
    }

    // ─── Search Panel ─────────────────────────────────────────────────────────

    private fun buildSearchPanel(): View {
        var resultsLayout: LinearLayout? = null

        val panel = make3DPanel(dp(16), dp(115), dp(340), dp(400), -5f, "Buscar") { content ->
            if (!isConnected) {
                content.addView(TextView(this).apply {
                    text = "Conecta Spotify primero"
                    textSize = 13f
                    setTextColor(0xAAFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, dp(20), 0, 0)
                })
                return@make3DPanel
            }

            val searchRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
                lp.setMargins(0, 0, 0, dp(8))
                layoutParams = lp
            }

            val input = EditText(this).apply {
                hint = "Cancion, artista..."
                setHintTextColor(0x55FFFFFF)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                setBackgroundColor(0x22FFFFFF)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            }

            val btnSearch = TextView(this).apply {
                text = "Ir"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(0xFF000000.toInt())
                setBackgroundColor(0xFF1DB954.toInt())
                val lp = LinearLayout.LayoutParams(dp(44), dp(44))
                lp.setMargins(dp(6), 0, 0, 0)
                layoutParams = lp
            }

            searchRow.addView(input)
            searchRow.addView(btnSearch)
            content.addView(searchRow)

            val scroll = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(290)
                )
            }
            resultsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            scroll.addView(resultsLayout)
            content.addView(scroll)

            val doSearch = {
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    spotify.searchTracks(q) { tracks ->
                        handler.post {
                            resultsLayout!!.removeAllViews()
                            tracks.take(8).forEach { track ->
                                val tName = track.optString("name")
                                val tArtist = track.optJSONArray("artists")
                                    ?.optJSONObject(0)?.optString("name") ?: ""
                                val tUri = track.optString("uri")
                                val duration = track.optInt("duration_ms", 0) / 1000
                                val min = duration / 60
                                val sec = duration % 60

                                val row = LinearLayout(this).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = Gravity.CENTER_VERTICAL
                                    setBackgroundColor(0x15FFFFFF)
                                    val lp = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
                                    )
                                    lp.setMargins(0, dp(2), 0, dp(2))
                                    layoutParams = lp
                                    setPadding(dp(10), 0, dp(10), 0)
                                    setOnClickListener {
                                        spotify.playTrack(tUri) {}
                                        renderer.beatPulse = 3f
                                    }
                                }

                                val col = LinearLayout(this).apply {
                                    orientation = LinearLayout.VERTICAL
                                    val lp = LinearLayout.LayoutParams(
                                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                                    )
                                    lp.setMargins(dp(10), 0, 0, 0)
                                    layoutParams = lp
                                }
                                col.addView(TextView(this).apply {
                                    text = tName
                                    textSize = 12f
                                    setTextColor(0xFFFFFFFF.toInt())
                                    maxLines = 1
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                })
                                col.addView(TextView(this).apply {
                                    text = "$tArtist  -  $min:${sec.toString().padStart(2, '0')}"
                                    textSize = 10f
                                    setTextColor(0x881DB954.toInt())
                                })
                                row.addView(col)
                                row.addView(TextView(this).apply {
                                    text = ">"
                                    textSize = 14f
                                    setTextColor(0xFF1DB954.toInt())
                                })
                                resultsLayout!!.addView(row)
                            }
                        }
                    }
                }
            }

            btnSearch.setOnClickListener { renderer.beatPulse = 1.5f; doSearch() }
            input.setOnEditorActionListener { _, _, _ -> doSearch(); true }
        }
        return panel
    }

    // ─── Login Panel ──────────────────────────────────────────────────────────

    private fun buildLoginPanel(): View {
        val panel = make3DPanel(dp(30), dp(180), dp(310), dp(200), 0f, "Conectar Spotify") { content ->
            content.addView(TextView(this).apply {
                text = "Conecta tu cuenta de Spotify\npara controlar la musica en 3D"
                textSize = 13f
                setTextColor(0xCCFFFFFF.toInt())
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, dp(8), 0, dp(20))
                layoutParams = lp
            })

            val btnLogin = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setBackgroundColor(0xFF1DB954.toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
                layoutParams = lp
                setPadding(dp(20), 0, dp(20), 0)
                setOnClickListener {
                    renderer.beatPulse = 2f
                    startActivity(spotify.getAuthIntent())
                }
            }
            btnLogin.addView(TextView(this).apply {
                text = "Conectar con Spotify"
                textSize = 15f
                setTextColor(0xFF000000.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            content.addView(btnLogin)
        }
        return panel
    }

    // ─── 3D drag ──────────────────────────────────────────────────────────────

    private fun makeDraggable3D(view: View) {
        var dX = 0f
        var dY = 0f
        var lastRawX = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    lastRawX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.x = event.rawX + dX
                    v.y = event.rawY + dY
                    val vel = (event.rawX - lastRawX).coerceIn(-60f, 60f)
                    v.rotationY = vel * 0.25f
                    lastRawX = event.rawX
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ObjectAnimator.ofFloat(v, "rotationY", v.rotationY, -6f).apply {
                        duration = 400
                        interpolator = DecelerateInterpolator()
                        start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ─── Galaxy touch ─────────────────────────────────────────────────────────

    private fun setupGestureControls() {
        var lastX = 0f
        var lastY = 0f
        glView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; true }
                MotionEvent.ACTION_MOVE -> {
                    renderer.rotationY += (event.x - lastX) * 0.25f
                    renderer.rotationX += (event.y - lastY) * 0.15f
                    lastX = event.x
                    lastY = event.y
                    true
                }
                else -> false
            }
        }
    }

    // ─── Track polling ────────────────────────────────────────────────────────

    private fun startTrackUpdates() {
        trackUpdateRunnable?.let { handler.removeCallbacks(it) }
        trackUpdateRunnable = object : Runnable {
            override fun run() {
                spotify.getCurrentTrack { json ->
                    json?.let {
                        try {
                            val item = it.optJSONObject("item") ?: return@let
                            val name = item.optString("name")
                            val artist = item.optJSONArray("artists")
                                ?.optJSONObject(0)?.optString("name") ?: ""
                            isPlaying = it.optBoolean("is_playing", false)
                            handler.post {
                                playerPanel?.findViewWithTag<TextView>("track_name")?.text = name
                                playerPanel?.findViewWithTag<TextView>("artist_name")?.text = artist
                            }
                        } catch (e: Exception) { /* ignore */ }
                    }
                }
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(trackUpdateRunnable!!)
    }

    // ─── Toast ────────────────────────────────────────────────────────────────

    private fun showToast3D(msg: String) {
        val tv = TextView(this).apply {
            text = msg
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xEE1DB954.toInt())
            setPadding(dp(20), dp(10), dp(20), dp(10))
            alpha = 0f
            elevation = 32f
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(42)
        }
        overlayLayout.addView(tv, lp)
        tv.animate().alpha(1f).setDuration(300).withEndAction {
            handler.postDelayed({
                tv.animate().alpha(0f).setDuration(300)
                    .withEndAction { overlayLayout.removeView(tv) }.start()
            }, 2000)
        }.start()
    }

    // ─── Misc ─────────────────────────────────────────────────────────────────

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
    }

    private fun dp(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onResume() { super.onResume(); glView.onResume() }
    override fun onPause() { super.onPause(); glView.onPause() }
    override fun onDestroy() {
        super.onDestroy()
        trackUpdateRunnable?.let { handler.removeCallbacks(it) }
    }
}
