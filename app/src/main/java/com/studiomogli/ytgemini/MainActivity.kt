package com.studiomogli.ytgemini

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val GEMINI_APP_URL = "https://gemini.google.com/app"
        private const val YT_STUDIO_URL = "https://studio.youtube.com"
        private const val STUDIO_MOGLI_REFER_URL = "https://studiomogli.com/refer-and-earn"
        private const val STUDIO_MOGLI_HOME_URL = "https://studiomogli.com"
        private const val GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/harekrishna1602/yt-gemini/releases/latest"

        private val AUTH_HOSTS = listOf(
            "accounts.google.com",
            "accounts.youtube.com",
            "myaccount.google.com"
        )
    }

    private lateinit var store: CreatorStore
    private lateinit var bottomNav: BottomNavigationView

    // 5 Isolated Tab Containers
    private lateinit var tabRadar: ScrollView
    private lateinit var tabWorkbench: ScrollView
    private lateinit var tabGemini: LinearLayout
    private lateinit var tabAssets: ScrollView
    private lateinit var tabStudio: LinearLayout

    // Radar Views
    private lateinit var txtPublishedCount: TextView
    private lateinit var txtStreakCount: TextView
    private lateinit var txtWatchVsCreate: TextView
    private lateinit var txtForceStatus: TextView
    private lateinit var txtBadgesList: TextView
    private lateinit var btnUpdateApp: Button

    // Workbench Views
    private lateinit var txtActiveSlots: TextView
    private lateinit var workbenchListContainer: LinearLayout
    private lateinit var shippedVaultContainer: LinearLayout

    // Gemini Lab Views
    private lateinit var geminiWebView: WebView
    private lateinit var geminiApiScroll: ScrollView
    private lateinit var txtGeminiApiOutput: TextView
    private var geminiLoaded = false
    private var pendingPrompt = ""

    // Assets Views
    private lateinit var assetsGrid: GridLayout
    private lateinit var txtAssetsEmpty: TextView

    // Studio Views
    private lateinit var studioWebView: WebView
    private var studioLoaded = false

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var voiceTargetEditText: EditText? = null
    private var updateDownloadUrl: String? = null
    private var currentTabId: Int = R.id.nav_radar

    private val createTimerHandler = Handler(Looper.getMainLooper())
    private val createTimerRunnable = object : Runnable {
        override fun run() {
            if (currentTabId == R.id.nav_workbench) {
                store.addCreateSecondsToday(5)
                refreshRadarStats()
            }
            createTimerHandler.postDelayed(this, 5000L)
        }
    }

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        CookieManager.getInstance().flush()
        if (result.resultCode == Activity.RESULT_OK) {
            if (geminiLoaded) geminiWebView.reload()
            if (studioLoaded) studioWebView.reload()
        }
    }

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim() ?: ""
            if (spoken.isNotEmpty()) {
                val target = voiceTargetEditText
                if (target != null) {
                    val existing = target.text.toString().trim()
                    val combined = if (existing.isEmpty()) spoken else "$existing\n$spoken"
                    target.setText(combined)
                    target.setSelection(combined.length)
                }
            }
        }
    }

    private val pickAssetLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            saveAssetLocally(uri)
            renderAssetsGallery()
        }
    }

    private val webFileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = CreatorStore(this)

        bottomNav = findViewById(R.id.bottomNav)
        tabRadar = findViewById(R.id.tabRadar)
        tabWorkbench = findViewById(R.id.tabWorkbench)
        tabGemini = findViewById(R.id.tabGemini)
        tabAssets = findViewById(R.id.tabAssets)
        tabStudio = findViewById(R.id.tabStudio)

        txtPublishedCount = findViewById(R.id.txtPublishedCount)
        txtStreakCount = findViewById(R.id.txtStreakCount)
        txtWatchVsCreate = findViewById(R.id.txtWatchVsCreate)
        txtForceStatus = findViewById(R.id.txtForceStatus)
        txtBadgesList = findViewById(R.id.txtBadgesList)
        btnUpdateApp = findViewById(R.id.btnUpdateApp)

        txtActiveSlots = findViewById(R.id.txtActiveSlots)
        workbenchListContainer = findViewById(R.id.workbenchListContainer)
        shippedVaultContainer = findViewById(R.id.shippedVaultContainer)

        geminiWebView = findViewById(R.id.geminiWebView)
        geminiApiScroll = findViewById(R.id.geminiApiScroll)
        txtGeminiApiOutput = findViewById(R.id.txtGeminiApiOutput)

        assetsGrid = findViewById(R.id.assetsGrid)
        txtAssetsEmpty = findViewById(R.id.txtAssetsEmpty)

        studioWebView = findViewById(R.id.studioWebView)

        setupRadarTab()
        setupWorkbenchTab()
        setupGeminiTab()
        setupAssetsTab()
        setupStudioTab()
        setupBottomNavigation()
        registerBackHandler()

        refreshRadarStats()
        renderWorkbench()
        renderAssetsGallery()

        if (savedInstanceState == null) {
            showWelcomeIfFirstRun()
            checkForGitHubUpdateSilently()
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            switchTab(item.itemId)
            true
        }
        switchTab(R.id.nav_radar)
    }

    private fun switchTab(tabId: Int) {
        currentTabId = tabId
        tabRadar.visibility = if (tabId == R.id.nav_radar) View.VISIBLE else View.GONE
        tabWorkbench.visibility = if (tabId == R.id.nav_workbench) View.VISIBLE else View.GONE
        tabGemini.visibility = if (tabId == R.id.nav_gemini) View.VISIBLE else View.GONE
        tabAssets.visibility = if (tabId == R.id.nav_assets) View.VISIBLE else View.GONE
        tabStudio.visibility = if (tabId == R.id.nav_studio) View.VISIBLE else View.GONE

        if (tabId == R.id.nav_radar) refreshRadarStats()
        if (tabId == R.id.nav_workbench) renderWorkbench()
        if (tabId == R.id.nav_assets) renderAssetsGallery()

        if (tabId == R.id.nav_gemini && !geminiLoaded && !store.isUseApiKey()) {
            geminiLoaded = true
            geminiWebView.loadUrl(GEMINI_APP_URL)
        }
        if (tabId == R.id.nav_studio && !studioLoaded) {
            studioLoaded = true
            studioWebView.loadUrl(YT_STUDIO_URL)
        }
    }

    // ==================== TAB 1: RADAR ====================
    private fun setupRadarTab() {
        findViewById<Button>(R.id.btnLogWatch15).setOnClickListener {
            store.addWatchMinsToday(15)
            refreshRadarStats()
            Toast.makeText(this, "Logged +15 min watch time", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnBingeToBuild).setOnClickListener {
            showBingeToBuildDialog()
        }

        findViewById<Button>(R.id.btnQuickVoiceScript).setOnClickListener {
            bottomNav.selectedItemId = R.id.nav_workbench
            showNewProjectDialog()
        }

        findViewById<Button>(R.id.btnEditCreatorProfile).setOnClickListener {
            showCreatorProfileDialog()
        }

        findViewById<Button>(R.id.btnStudioMogliCard).setOnClickListener {
            showStudioMogliDialog()
        }

        btnUpdateApp.setOnClickListener {
            val url = updateDownloadUrl
            if (!url.isNullOrBlank()) openExternalBrowser(url)
        }
    }

    private fun refreshRadarStats() {
        val published = store.getPublishedProjects().size
        val streak = store.getStreakDays()
        val watchMins = store.getWatchMinsToday()
        val createMins = store.getCreateMinsToday()
        val profile = store.getProfile()

        txtPublishedCount.text = published.toString()
        txtStreakCount.text = "${streak}d"
        txtWatchVsCreate.text = "Watched: ${watchMins}m  •  Created: ${createMins}m / ${profile.dailyGoalMinutes}m goal"

        if (profile.forceCreateEnabled && watchMins > createMins && watchMins >= 15) {
            txtForceStatus.text = "ALERT: You watched ${watchMins}m today vs ${createMins}m creating. Tap Binge-to-Build to flip this into a video!"
            txtForceStatus.setTextColor(0xFFFF5252.toInt())
        } else {
            txtForceStatus.text = "Force-Create Mode: Active (${profile.dailyGoalMinutes}m daily creation target)"
            txtForceStatus.setTextColor(0xFF81C784.toInt())
        }

        val badges = mutableListOf<String>()
        if (published >= 1) badges.add("First Blood (1)")
        if (published >= 5) badges.add("Momentum (5)")
        if (published >= 10) badges.add("Consistent (10)")
        if (published >= 25) badges.add("Builder (25)")
        if (streak >= 7) badges.add("7-Day Streak")
        txtBadgesList.text = if (badges.isEmpty()) {
            "No badges unlocked yet — ship your first video in Workbench!"
        } else {
            badges.joinToString("  •  ")
        }
    }

    private fun showBingeToBuildDialog() {
        val input = EditText(this).apply {
            hint = "What topic/videos were you just watching?"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setPadding(36, 28, 36, 28)
        }
        AlertDialog.Builder(this)
            .setTitle("Binge-to-Build Flip")
            .setMessage("Tell Gemini what you've been watching too much of. It will flip it into 3 video angles tailored to your exact setup.")
            .setView(input)
            .setPositiveButton("Flip in Gemini") { _, _ ->
                val topic = input.text.toString().trim().ifEmpty { "trending videos in my niche" }
                val prompt = PromptFrameworks.bingeToBuildFlip(
                    store.getProfile(),
                    store.getWatchMinsToday(),
                    store.getCreateMinsToday(),
                    topic
                )
                sendPromptToGeminiTab(prompt)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreatorProfileDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_creator_profile, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val p = store.getProfile()
        val edtNiche = view.findViewById<EditText>(R.id.edtNiche)
        val edtHardware = view.findViewById<EditText>(R.id.edtHardware)
        val edtTools = view.findViewById<EditText>(R.id.edtTools)
        val edtGoal = view.findViewById<EditText>(R.id.edtDailyGoal)
        val chkFaceless = view.findViewById<CheckBox>(R.id.chkFaceless)
        val chkForce = view.findViewById<CheckBox>(R.id.chkForceCreate)

        edtNiche.setText(p.channelNiche)
        edtHardware.setText(p.hardwareSpecs)
        edtTools.setText(p.toolsAndApps)
        edtGoal.setText(p.dailyGoalMinutes.toString())
        chkFaceless.isChecked = p.isFaceless
        chkForce.isChecked = p.forceCreateEnabled

        view.findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            p.channelNiche = edtNiche.text.toString().trim()
            p.hardwareSpecs = edtHardware.text.toString().trim()
            p.toolsAndApps = edtTools.text.toString().trim()
            p.dailyGoalMinutes = edtGoal.text.toString().toIntOrNull()?.coerceAtLeast(10) ?: 45
            p.isFaceless = chkFaceless.isChecked
            p.forceCreateEnabled = chkForce.isChecked
            store.saveProfile(p)
            refreshRadarStats()
            dialog.dismiss()
            Toast.makeText(this, "Creator Setup saved — Gemini will respect your constraints", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btnCloseProfile).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ==================== TAB 2: WORKBENCH ====================
    private fun setupWorkbenchTab() {
        findViewById<Button>(R.id.btnNewProject).setOnClickListener {
            showNewProjectDialog()
        }
        findViewById<Button>(R.id.btnPitchIdeasInGemini).setOnClickListener {
            val prompt = PromptFrameworks.constraintFirstIdeas(store.getProfile())
            sendPromptToGeminiTab(prompt)
        }
    }

    private fun renderWorkbench() {
        val active = store.getActiveProjects()
        val published = store.getPublishedProjects()
        txtActiveSlots.text = "Active Projects: ${active.size} / ${CreatorStore.MAX_ACTIVE_PROJECTS}"

        workbenchListContainer.removeAllViews()
        if (active.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No active projects. Tap '+ New Video Idea' to lock in your next upload."
                textSize = 13f
                setTextColor(0xFF9E9E9E.toInt())
                setPadding(8, 16, 8, 16)
            }
            workbenchListContainer.addView(empty)
        } else {
            active.forEach { proj ->
                val card = LayoutInflater.from(this).inflate(R.layout.item_workbench_project, workbenchListContainer, false)
                card.findViewById<TextView>(R.id.txtProjStage).text = proj.stage
                card.findViewById<TextView>(R.id.txtProjAngle).text = proj.coreAngle
                val titlePreview = proj.finalTitle.ifBlank { "No title packaged yet" }
                card.findViewById<TextView>(R.id.txtProjMeta).text = "Title: $titlePreview"
                card.setOnClickListener { showProjectEditorDialog(proj) }
                workbenchListContainer.addView(card)
            }
        }

        shippedVaultContainer.removeAllViews()
        if (published.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Shipped Vault is empty. Publish a project to earn your first badge."
                textSize = 12f
                setTextColor(0xFF777777.toInt())
                setPadding(8, 10, 8, 10)
            }
            shippedVaultContainer.addView(empty)
        } else {
            published.forEach { proj ->
                val row = TextView(this).apply {
                    text = "✓ ${proj.finalTitle.ifBlank { proj.coreAngle }}"
                    textSize = 13f
                    setTextColor(0xFF81C784.toInt())
                    setPadding(8, 10, 8, 10)
                    setOnClickListener { showProjectEditorDialog(proj) }
                }
                shippedVaultContainer.addView(row)
            }
        }
    }

    private fun showNewProjectDialog() {
        if (store.getActiveProjects().size >= CreatorStore.MAX_ACTIVE_PROJECTS) {
            Toast.makeText(this, "Max 3 active projects! Publish or delete one first.", Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(this).apply {
            hint = "1-sentence Core Video Angle..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setPadding(36, 28, 36, 28)
        }
        AlertDialog.Builder(this)
            .setTitle("New Workbench Project")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val angle = input.text.toString().trim()
                if (angle.isNotEmpty()) {
                    val proj = VideoProject(id = "proj_${System.currentTimeMillis()}", coreAngle = angle)
                    store.upsertProject(proj)
                    renderWorkbench()
                    showProjectEditorDialog(proj)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProjectEditorDialog(project: VideoProject) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_project_editor, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val edtAngle = view.findViewById<EditText>(R.id.edtProjAngle)
        val edtScript = view.findViewById<EditText>(R.id.edtVoiceScript)
        val edtTitle = view.findViewById<EditText>(R.id.edtFinalTitle)
        val txtTitleWarn = view.findViewById<TextView>(R.id.txtTitleCharWarn)
        val edtThumb = view.findViewById<EditText>(R.id.edtThumbText)
        val txtRepeatWarn = view.findViewById<TextView>(R.id.txtNoRepeatWarn)
        val btnVoice = view.findViewById<Button>(R.id.btnVoiceDictate)
        val btnHookGemini = view.findViewById<Button>(R.id.btnStructureScriptGemini)
        val btnPackGemini = view.findViewById<Button>(R.id.btnPackagingGemini)
        val btnPublish = view.findViewById<Button>(R.id.btnMarkPublished)
        val btnSave = view.findViewById<Button>(R.id.btnSaveProject)
        val btnDelete = view.findViewById<TextView>(R.id.btnDeleteProject)

        edtAngle.setText(project.coreAngle)
        edtScript.setText(project.voiceScript)
        edtTitle.setText(project.finalTitle)
        edtThumb.setText(project.thumbText)

        fun runPackagingChecks() {
            val t = edtTitle.text.toString().trim()
            val th = edtThumb.text.toString().trim()
            txtTitleWarn.text = if (t.length > 55) "Warning: ${t.length} chars (may cut off on mobile)" else "${t.length}/55 chars"
            txtTitleWarn.setTextColor(if (t.length > 55) 0xFFFF5252.toInt() else 0xFF81C784.toInt())

            val titleWords = t.lowercase(Locale.US).split(Regex("\\W+")).filter { it.length > 2 }.toSet()
            val thumbWords = th.lowercase(Locale.US).split(Regex("\\W+")).filter { it.length > 2 }
            val repeated = thumbWords.filter { it in titleWords }
            val wordCount = if (th.isEmpty()) 0 else th.split(Regex("\\s+")).size
            txtRepeatWarn.text = when {
                wordCount > 4 -> "Warning: $wordCount words (keep thumbnail <= 4 words)"
                repeated.isNotEmpty() -> "No-Repeat Alert: '${repeated.joinToString()}' repeated from Title!"
                else -> "Packaging looks clean ($wordCount/4 words)"
            }
            txtRepeatWarn.setTextColor(if (wordCount > 4 || repeated.isNotEmpty()) 0xFFFF5252.toInt() else 0xFF81C784.toInt())
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { runPackagingChecks() }
            override fun afterTextChanged(s: Editable?) {}
        }
        edtTitle.addTextChangedListener(watcher)
        edtThumb.addTextChangedListener(watcher)
        runPackagingChecks()

        btnVoice.setOnClickListener {
            voiceTargetEditText = edtScript
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your video script naturally...")
            }
            try {
                voiceLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Voice typing not available on this device", Toast.LENGTH_SHORT).show()
            }
        }

        fun syncFields() {
            project.coreAngle = edtAngle.text.toString().trim().ifEmpty { project.coreAngle }
            project.voiceScript = edtScript.text.toString().trim()
            project.finalTitle = edtTitle.text.toString().trim()
            project.thumbText = edtThumb.text.toString().trim()
            if (project.stage != VideoProject.STAGE_PUBLISHED) {
                project.stage = when {
                    project.finalTitle.isNotEmpty() -> VideoProject.STAGE_PACKAGING
                    project.voiceScript.isNotEmpty() -> VideoProject.STAGE_SCRIPT
                    else -> VideoProject.STAGE_IDEA
                }
            }
            store.upsertProject(project)
        }

        btnHookGemini.setOnClickListener {
            syncFields()
            dialog.dismiss()
            sendPromptToGeminiTab(PromptFrameworks.voiceScriptToHook(store.getProfile(), project))
        }

        btnPackGemini.setOnClickListener {
            syncFields()
            dialog.dismiss()
            sendPromptToGeminiTab(PromptFrameworks.noRepeatPackaging(store.getProfile(), project))
        }

        btnPublish.setOnClickListener {
            syncFields()
            project.stage = VideoProject.STAGE_PUBLISHED
            project.publishedAt = System.currentTimeMillis()
            store.upsertProject(project)
            renderWorkbench()
            refreshRadarStats()
            dialog.dismiss()
            Toast.makeText(this, "Video Shipped! Achievement progress updated.", Toast.LENGTH_LONG).show()
        }

        btnSave.setOnClickListener {
            syncFields()
            renderWorkbench()
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            store.deleteProject(project.id)
            renderWorkbench()
            dialog.dismiss()
        }

        dialog.show()
    }

    // ==================== TAB 3: GEMINI LAB ====================
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGeminiTab() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(geminiWebView, true)

        geminiWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = buildCleanMobileUa()
        }

        geminiWebView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                webFileChooserLauncher.launch("image/*")
                return true
            }
        }

        geminiWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val host = uri.host?.lowercase() ?: return false
                if (AUTH_HOSTS.any { host == it || host.endsWith(".$it") }) {
                    launchIsolatedLogin(uri.toString())
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                if (url != null && url.contains("gemini.google.com") && pendingPrompt.isNotBlank()) {
                    view?.postDelayed({ injectPromptIntoGemini(pendingPrompt) }, 1200L)
                }
            }
        }

        findViewById<TextView>(R.id.btnSendPendingPrompt).setOnClickListener {
            if (pendingPrompt.isNotBlank()) {
                copyToClipboard("YT Gemini Prompt", pendingPrompt)
                injectPromptIntoGemini(pendingPrompt)
                Toast.makeText(this, "Prompt sent & copied", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Trigger a tool from Radar or Workbench first", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.btnExportToWorkbench).setOnClickListener {
            exportClipboardOrApiToWorkbench()
        }

        findViewById<TextView>(R.id.btnGeminiModeSettings).setOnClickListener {
            showGeminiApiSettingsDialog()
        }
    }

    private fun sendPromptToGeminiTab(prompt: String) {
        pendingPrompt = prompt
        copyToClipboard("YT Gemini Prompt", prompt)
        bottomNav.selectedItemId = R.id.nav_gemini

        if (store.isUseApiKey() && store.getGeminiApiKey().isNotBlank()) {
            geminiWebView.visibility = View.GONE
            geminiApiScroll.visibility = View.VISIBLE
            txtGeminiApiOutput.text = "Generating with Gemini API..."
            callGeminiRestApi(store.getGeminiApiKey(), prompt)
        } else {
            geminiApiScroll.visibility = View.GONE
            geminiWebView.visibility = View.VISIBLE
            if (!geminiLoaded) {
                geminiLoaded = true
                geminiWebView.loadUrl(GEMINI_APP_URL)
            } else {
                injectPromptIntoGemini(prompt)
            }
        }
    }

    private fun injectPromptIntoGemini(prompt: String) {
        val escaped = JSONObject.quote(prompt)
        val js = "(function(){" +
            "var p=" + escaped + ";" +
            "var box=document.querySelector('rich-textarea div[contenteditable=\"true\"], div[contenteditable=\"true\"][role=\"textbox\"], textarea');" +
            "if(!box)return;" +
            "box.focus();" +
            "if(box.tagName==='TEXTAREA'){box.value=p;}else{box.innerText=p;}" +
            "box.dispatchEvent(new Event('input',{bubbles:true}));" +
            "setTimeout(function(){" +
            "var btn=document.querySelector('button[aria-label*=\"Send\"], button.send-button, button[data-test-id=\"send-button\"]');" +
            "if(btn&&!btn.disabled)btn.click();" +
            "},400);" +
            "})();"
        geminiWebView.evaluateJavascript(js, null)
    }

    private fun callGeminiRestApi(apiKey: String, prompt: String) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(
                            JSONObject().put("text", prompt)
                        ))
                    ))
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream.bufferedReader().use { it.readText() }
                val out = if (code in 200..299) {
                    JSONObject(resp).optJSONArray("candidates")
                        ?.optJSONObject(0)?.optJSONObject("content")
                        ?.optJSONArray("parts")?.optJSONObject(0)
                        ?.optString("text", "No response") ?: "No response"
                } else {
                    "Gemini API Error ($code):\n$resp"
                }
                runOnUiThread { txtGeminiApiOutput.text = out }
            } catch (e: Exception) {
                runOnUiThread { txtGeminiApiOutput.text = "Network error: ${e.message}" }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun exportClipboardOrApiToWorkbench() {
        val textToExport = if (store.isUseApiKey() && txtGeminiApiOutput.text.isNotBlank()) {
            txtGeminiApiOutput.text.toString()
        } else {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: ""
        }

        if (textToExport.isBlank()) {
            Toast.makeText(this, "Copy Gemini's reply first, then tap Export to Workbench", Toast.LENGTH_LONG).show()
            return
        }

        val active = store.getActiveProjects()
        if (active.isEmpty()) {
            val proj = VideoProject(
                id = "proj_${System.currentTimeMillis()}",
                coreAngle = textToExport.lines().firstOrNull()?.take(90) ?: "New Gemini Idea",
                voiceScript = textToExport,
                stage = VideoProject.STAGE_SCRIPT
            )
            store.upsertProject(proj)
            Toast.makeText(this, "Exported to new Workbench project!", Toast.LENGTH_SHORT).show()
            bottomNav.selectedItemId = R.id.nav_workbench
        } else {
            val target = active.first()
            target.voiceScript = if (target.voiceScript.isBlank()) textToExport else "${target.voiceScript}\n\n--- Gemini Export ---\n$textToExport"
            target.stage = VideoProject.STAGE_SCRIPT
            store.upsertProject(target)
            Toast.makeText(this, "Appended to '${target.coreAngle.take(25)}'!", Toast.LENGTH_SHORT).show()
            bottomNav.selectedItemId = R.id.nav_workbench
        }
    }

    private fun showGeminiApiSettingsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_gemini_settings, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val chk = view.findViewById<CheckBox>(R.id.chkUseApiKey)
        val edt = view.findViewById<EditText>(R.id.edtGeminiApiKey)
        chk.isChecked = store.isUseApiKey()
        edt.setText(store.getGeminiApiKey())
        view.findViewById<Button>(R.id.btnSaveGeminiSettings).setOnClickListener {
            store.saveGeminiSettings(chk.isChecked, edt.text.toString())
            dialog.dismiss()
            Toast.makeText(this, if (chk.isChecked) "Direct API Mode Enabled" else "Logged-In Gemini Mode Enabled", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btnCloseGeminiSettings).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ==================== TAB 4: ASSETS (FACE / MASK GALLERY) ====================
    private fun setupAssetsTab() {
        findViewById<Button>(R.id.btnAddAssetPhoto).setOnClickListener {
            pickAssetLauncher.launch("image/*")
        }
    }

    private fun getAssetsDir(): File {
        val dir = File(filesDir, "creator_assets")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun saveAssetLocally(uri: Uri) {
        try {
            val outFile = File(getAssetsDir(), "asset_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            Toast.makeText(this, "Saved to Creator Assets Gallery", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderAssetsGallery() {
        assetsGrid.removeAllViews()
        val files = getAssetsDir().listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        txtAssetsEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE

        files.forEach { file ->
            val img = ImageView(this).apply {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                val size = (resources.displayMetrics.widthPixels - 96) / 3
                val lp = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(8, 8, 8, 8)
                }
                layoutParams = lp
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Remove Asset?")
                        .setPositiveButton("Delete") { _, _ ->
                            file.delete()
                            renderAssetsGallery()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }
            assetsGrid.addView(img)
        }
    }

    // ==================== TAB 5: YOUTUBE STUDIO ====================
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupStudioTab() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(studioWebView, true)

        studioWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = buildDesktopChromeUa()
        }

        studioWebView.webChromeClient = WebChromeClient()
        studioWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val host = uri.host?.lowercase() ?: return false
                if (AUTH_HOSTS.any { host == it || host.endsWith(".$it") }) {
                    launchIsolatedLogin(uri.toString())
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
            }
        }
    }

    // ==================== SHARED UTILITIES ====================
    private fun showWelcomeIfFirstRun() {
        val currentCode = getCurrentVersionCode()
        if (store.getLastSeenVersion() != currentCode) {
            store.setLastSeenVersion(currentCode)
            showTrustDialog()
        }
    }

    private fun showTrustDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_trust_welcome, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<Button>(R.id.btnTrustGotIt).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showStudioMogliDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_studio_mogli, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<Button>(R.id.btnReferAndEarn).setOnClickListener {
            openExternalBrowser(STUDIO_MOGLI_REFER_URL)
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnReadTrustGuarantee).setOnClickListener {
            dialog.dismiss()
            showTrustDialog()
        }
        view.findViewById<Button>(R.id.btnVisitStudioMogli).setOnClickListener {
            openExternalBrowser(STUDIO_MOGLI_HOME_URL)
            dialog.dismiss()
        }
        view.findViewById<TextView>(R.id.btnCloseCredits).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun checkForGitHubUpdateSilently() {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "YTGemini-StudioMogli")
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@Thread
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name", "").trim()
                if (tag.isEmpty() || !isNewerVersion(tag, getCurrentVersionName())) return@Thread
                var url = json.optString("html_url", "")
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        if (a.optString("name", "").lowercase().endsWith(".apk")) {
                            url = a.optString("browser_download_url", url)
                            break
                        }
                    }
                }
                if (url.isNotBlank()) {
                    runOnUiThread {
                        updateDownloadUrl = url
                        btnUpdateApp.visibility = View.VISIBLE
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun isNewerVersion(latestRaw: String, currentRaw: String): Boolean {
        val lParts = latestRaw.removePrefix("v").removePrefix("V").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val cParts = currentRaw.removePrefix("v").removePrefix("V").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(lParts.size, cParts.size)) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun getCurrentVersionCode(): Long = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (e: Exception) { 1L }

    private fun getCurrentVersionName(): String = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
        }
        info.versionName ?: "1.0.0"
    } catch (e: Exception) { "1.0.0" }

    private fun launchIsolatedLogin(authUrl: String) {
        val intent = Intent(this, LoginActivity::class.java).apply {
            putExtra(LoginActivity.EXTRA_AUTH_URL, authUrl)
        }
        loginLauncher.launch(intent)
    }

    private fun buildCleanMobileUa(): String {
        val realDefaultUa = WebSettings.getDefaultUserAgent(this)
        return realDefaultUa
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildDesktopChromeUa(): String {
        val defaultUa = WebSettings.getDefaultUserAgent(this)
        val chromeMatch = Regex("Chrome/[0-9.]+").find(defaultUa)?.value ?: "Chrome/134.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chromeMatch Safari/537.36"
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun openExternalBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    currentTabId == R.id.nav_gemini && geminiWebView.canGoBack() -> geminiWebView.goBack()
                    currentTabId == R.id.nav_studio && studioWebView.canGoBack() -> studioWebView.goBack()
                    currentTabId != R.id.nav_radar -> bottomNav.selectedItemId = R.id.nav_radar
                    else -> finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        createTimerHandler.removeCallbacks(createTimerRunnable)
        createTimerHandler.postDelayed(createTimerRunnable, 5000L)
    }

    override fun onPause() {
        createTimerHandler.removeCallbacks(createTimerRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        createTimerHandler.removeCallbacks(createTimerRunnable)
        geminiWebView.destroy()
        studioWebView.destroy()
        super.onDestroy()
    }
}
