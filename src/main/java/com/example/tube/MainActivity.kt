package com.example.tube

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.net.URLEncoder
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.MediaSession

/**
 * YouTube's own mobile site and player on the watch. Wear OS ships no WebView, so the page runs in a bundled GeckoView.
 * Search by voice, YouTube's fullscreen button goes edge to edge, and the crown is the volume while a video plays.
 */
class MainActivity : Activity() {
    private val session = GeckoSession()
    private lateinit var search: TextView
    private var playing = false
    private var fullScreen = false
    private var canGoBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gecko = runtime ?: GeckoRuntime.create(applicationContext).also { runtime = it }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                this@MainActivity.fullScreen = fullScreen
                search.visibility = if (fullScreen) View.GONE else View.VISIBLE
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { this@MainActivity.canGoBack = canGoBack }
        }
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) { playing = true }
            override fun onPause(session: GeckoSession, mediaSession: MediaSession) { playing = false }
            override fun onStop(session: GeckoSession, mediaSession: MediaSession) { playing = false }
        }
        // The Wear keyboard's text never commits into GeckoView's page fields, so a page that wants the keyboard gets a
        // native text box instead, and its text is typed into the page as key presses.
        session.textInput.setDelegate(object : GeckoSession.TextInputDelegate {
            override fun showSoftInput(session: GeckoSession) { runOnUiThread { typeIntoPage() } }
        })
        session.open(gecko)

        val page = GeckoView(this).apply { setSession(this@MainActivity.session) }
        search = TextView(this).apply {
            text = "🔍"
            textSize = 20f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(200, 30, 30, 30)) }
            contentDescription = "Search YouTube by voice"
            setOnClickListener { askForSearch() }
        }
        val size = (48 * resources.displayMetrics.density).toInt()
        setContentView(FrameLayout(this).apply {
            addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            // Left edge, mid-height: clear of YouTube's header and bottom tabs on the round screen.
            addView(search, FrameLayout.LayoutParams(size, size, Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = size / 8 })
        })
        if (savedInstanceState == null) session.loadUri(youtubeLink(intent) ?: HOME)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        youtubeLink(intent)?.let(session::loadUri)
    }

    /** A YouTube link handed to Tube (by adb, or later by Buddy); anything else is ignored. */
    private fun youtubeLink(intent: Intent?): String? =
        intent?.dataString?.takeIf { it.startsWith("https://m.youtube.com/") || it.startsWith("https://www.youtube.com/") || it.startsWith("https://youtu.be/") }

    private var typing: AlertDialog? = null
    private var typedAt = 0L

    private fun typeIntoPage() {
        // Closing the box hands focus back to the page field, which asks for the keyboard again: not straight away.
        if (typing?.isShowing == true || System.currentTimeMillis() - typedAt < 1500) return
        val box = EditText(this).apply {
            // No suggestions or learning: this box also takes passwords.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSingleLine = true
        }
        var done = false
        val dialog = AlertDialog.Builder(this).setView(box).setPositiveButton("✓") { _, _ -> done = true }.create()
        box.setOnEditorActionListener { _, _, _ -> done = true; dialog.dismiss(); true }
        // The page only takes text once it has the window focus back, so the text goes in after the box closes.
        dialog.setOnDismissListener {
            typedAt = System.currentTimeMillis()
            if (done) window.decorView.postDelayed({ commit(box.text.toString()) }, 300)
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        box.requestFocus()
        typing = dialog
    }

    /** Types text into the focused page field as key presses through the window, the one path the page reliably takes. */
    private fun commit(text: String) {
        val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray()) ?: return
        for (event in events) window.decorView.dispatchKeyEvent(event)
        typedAt = System.currentTimeMillis() // the page asks for the keyboard again as it is typed into
    }

    private fun askForSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search YouTube")
        try {
            startActivityForResult(intent, SPEECH)
        } catch (_: Exception) {
            session.loadUri(HOME) // no voice input on this watch: YouTube's own search box still works
        }
    }

    @Deprecated("Activity result API is not needed for one request")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val query = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (requestCode == SPEECH && resultCode == RESULT_OK && !query.isNullOrEmpty()) {
            session.loadUri("$HOME/results?search_query=${URLEncoder.encode(query, "UTF-8")}")
        }
    }

    /** The crown is the volume while a video plays or is fullscreen, and scrolls the page otherwise. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) && (playing || fullScreen)) {
            val clockwise = event.getAxisValue(MotionEvent.AXIS_SCROLL) < 0 // ponytail: flip this if the crown feels reversed
            getSystemService(AudioManager::class.java).adjustStreamVolume(
                AudioManager.STREAM_MUSIC, if (clockwise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    @Deprecated("Back is handled here so it steps through the page first")
    override fun onBackPressed() {
        when {
            fullScreen -> session.exitFullScreen()
            canGoBack -> session.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }

    private companion object {
        const val HOME = "https://m.youtube.com"
        const val SPEECH = 1
        var runtime: GeckoRuntime? = null // one engine per process
    }
}
