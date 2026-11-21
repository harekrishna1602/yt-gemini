package com.studiomogli.ytgemini

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CreatorProfile(
    var channelNiche: String = "",
    var hardwareSpecs: String = "",
    var toolsAndApps: String = "",
    var isFaceless: Boolean = true,
    var dailyGoalMinutes: Int = 45,
    var forceCreateEnabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("channelNiche", channelNiche)
        put("hardwareSpecs", hardwareSpecs)
        put("toolsAndApps", toolsAndApps)
        put("isFaceless", isFaceless)
        put("dailyGoalMinutes", dailyGoalMinutes)
        put("forceCreateEnabled", forceCreateEnabled)
    }

    companion object {
        fun fromJson(obj: JSONObject?): CreatorProfile {
            if (obj == null) return CreatorProfile()
            return CreatorProfile(
                channelNiche = obj.optString("channelNiche", ""),
                hardwareSpecs = obj.optString("hardwareSpecs", ""),
                toolsAndApps = obj.optString("toolsAndApps", ""),
                isFaceless = obj.optBoolean("isFaceless", true),
                dailyGoalMinutes = obj.optInt("dailyGoalMinutes", 45),
                forceCreateEnabled = obj.optBoolean("forceCreateEnabled", true)
            )
        }
    }
}

data class VideoProject(
    val id: String,
    var coreAngle: String,
    var targetAudience: String = "",
    var stage: String = STAGE_IDEA,
    var voiceScript: String = "",
    var finalTitle: String = "",
    var thumbText: String = "",
    var linkedAssetPath: String = "",
    var publishedUrl: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var publishedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("coreAngle", coreAngle)
        put("targetAudience", targetAudience)
        put("stage", stage)
        put("voiceScript", voiceScript)
        put("finalTitle", finalTitle)
        put("thumbText", thumbText)
        put("linkedAssetPath", linkedAssetPath)
        put("publishedUrl", publishedUrl)
        put("createdAt", createdAt)
        put("publishedAt", publishedAt)
    }

    companion object {
        const val STAGE_IDEA = "IDEA"
        const val STAGE_SCRIPT = "SCRIPT"
        const val STAGE_PACKAGING = "PACKAGING"
        const val STAGE_PUBLISHED = "PUBLISHED"

        fun fromJson(obj: JSONObject): VideoProject = VideoProject(
            id = obj.optString("id", "proj_${System.currentTimeMillis()}"),
            coreAngle = obj.optString("coreAngle", ""),
            targetAudience = obj.optString("targetAudience", ""),
            stage = obj.optString("stage", STAGE_IDEA),
            voiceScript = obj.optString("voiceScript", ""),
            finalTitle = obj.optString("finalTitle", ""),
            thumbText = obj.optString("thumbText", ""),
            linkedAssetPath = obj.optString("linkedAssetPath", ""),
            publishedUrl = obj.optString("publishedUrl", ""),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            publishedAt = obj.optLong("publishedAt", 0L)
        )
    }
}

class CreatorStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "ytgemini_creator_os_prefs"
        private const val KEY_PROFILE = "creator_profile_json"
        private const val KEY_PROJECTS = "workbench_projects_json"
        private const val KEY_STATS_DATE = "stats_date"
        private const val KEY_WATCH_MINS_TODAY = "watch_mins_today"
        private const val KEY_CREATE_SECS_TODAY = "create_secs_today"
        private const val KEY_STREAK_DAYS = "streak_days"
        private const val KEY_LAST_ACTIVE_DATE = "last_active_date"
        private const val KEY_USE_API_KEY = "use_gemini_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_LAST_SEEN_VERSION = "last_seen_version_code"
        const val MAX_ACTIVE_PROJECTS = 3
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun ensureDateCurrent() {
        val today = todayKey()
        if (prefs.getString(KEY_STATS_DATE, "") != today) {
            prefs.edit()
                .putString(KEY_STATS_DATE, today)
                .putInt(KEY_WATCH_MINS_TODAY, 0)
                .putInt(KEY_CREATE_SECS_TODAY, 0)
                .apply()
        }
    }

    fun getProfile(): CreatorProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return CreatorProfile()
        return try {
            CreatorProfile.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            CreatorProfile()
        }
    }

    fun saveProfile(profile: CreatorProfile) {
        prefs.edit().putString(KEY_PROFILE, profile.toJson().toString()).apply()
    }

    fun getProjects(): MutableList<VideoProject> {
        val raw = prefs.getString(KEY_PROJECTS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<VideoProject>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(VideoProject.fromJson(obj))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveProjects(projects: List<VideoProject>) {
        val arr = JSONArray()
        projects.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PROJECTS, arr.toString()).apply()
    }

    fun getActiveProjects(): List<VideoProject> =
        getProjects().filter { it.stage != VideoProject.STAGE_PUBLISHED }

    fun getPublishedProjects(): List<VideoProject> =
        getProjects().filter { it.stage == VideoProject.STAGE_PUBLISHED }

    fun upsertProject(project: VideoProject) {
        val list = getProjects()
        val idx = list.indexOfFirst { it.id == project.id }
        if (idx >= 0) list[idx] = project else list.add(0, project)
        saveProjects(list)
        touchDailyStreak()
    }

    fun deleteProject(projectId: String) {
        val list = getProjects()
        list.removeAll { it.id == projectId }
        saveProjects(list)
    }

    fun getWatchMinsToday(): Int {
        ensureDateCurrent()
        return prefs.getInt(KEY_WATCH_MINS_TODAY, 0)
    }

    fun addWatchMinsToday(deltaMins: Int) {
        ensureDateCurrent()
        val updated = (prefs.getInt(KEY_WATCH_MINS_TODAY, 0) + deltaMins).coerceAtLeast(0)
        prefs.edit().putInt(KEY_WATCH_MINS_TODAY, updated).apply()
    }

    fun getCreateMinsToday(): Int {
        ensureDateCurrent()
        return prefs.getInt(KEY_CREATE_SECS_TODAY, 0) / 60
    }

    fun addCreateSecondsToday(deltaSecs: Int) {
        ensureDateCurrent()
        val updated = prefs.getInt(KEY_CREATE_SECS_TODAY, 0) + deltaSecs
        prefs.edit().putInt(KEY_CREATE_SECS_TODAY, updated).apply()
        if (updated >= 60) touchDailyStreak()
    }

    fun getStreakDays(): Int = prefs.getInt(KEY_STREAK_DAYS, 0)

    private fun touchDailyStreak() {
        val today = todayKey()
        val last = prefs.getString(KEY_LAST_ACTIVE_DATE, "")
        if (last != today) {
            val current = prefs.getInt(KEY_STREAK_DAYS, 0)
            prefs.edit()
                .putString(KEY_LAST_ACTIVE_DATE, today)
                .putInt(KEY_STREAK_DAYS, current + 1)
                .apply()
        }
    }

    fun isUseApiKey(): Boolean = prefs.getBoolean(KEY_USE_API_KEY, false)
    fun getGeminiApiKey(): String = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    fun saveGeminiSettings(useApi: Boolean, key: String) {
        prefs.edit()
            .putBoolean(KEY_USE_API_KEY, useApi)
            .putString(KEY_GEMINI_API_KEY, key.trim())
            .apply()
    }

    fun getLastSeenVersion(): Long = prefs.getLong(KEY_LAST_SEEN_VERSION, -1L)
    fun setLastSeenVersion(code: Long) {
        prefs.edit().putLong(KEY_LAST_SEEN_VERSION, code).apply()
    }
}
