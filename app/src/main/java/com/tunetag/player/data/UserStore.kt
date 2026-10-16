package com.tunetag.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class UserStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("tunetag_user", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(decode(prefs.getString("state", null)))
    val state: StateFlow<UserState> = _state.asStateFlow()

    fun update(block: (UserState) -> UserState) {
        synchronized(this) {
            val n = block(_state.value)
            _state.value = n
            prefs.edit().putString("state", encode(n)).apply()
        }
    }

    // --- queue persistence ---
    fun saveQueue(ids: List<String>) {
        prefs.edit().putString("queue", ids.joinToString(",")).apply()
    }

    fun savePosition(index: Int, pos: Long) {
        prefs.edit().putInt("q_index", index).putLong("q_pos", pos).apply()
    }

    fun loadQueue(): Triple<List<String>, Int, Long> {
        val ids = prefs.getString("queue", "").orEmpty().split(",").filter { it.isNotBlank() }
        return Triple(ids, prefs.getInt("q_index", 0), prefs.getLong("q_pos", 0L))
    }

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private fun encode(s: UserState): String {
        val o = JSONObject()
        val pl = JSONArray()
        s.playlists.forEach { p ->
            pl.put(JSONObject().put("id", p.id).put("name", p.name).put("paths", JSONArray(p.paths)))
        }
        o.put("pl", pl)
        val st = s.settings
        o.put(
            "set", JSONObject()
                .put("itunes", st.useItunes).put("deezer", st.useDeezer).put("mb", st.useMusicBrainz)
                .put("country", st.country).put("minScore", st.minScore)
                .put("sort", st.sort.name).put("desc", st.sortDesc)
                .put("theme", st.themeMode)
                .put("seekSec", st.seekSeconds).put("grid", st.albumGrid)
                .put("style", st.playerStyle).put("ipodSound", st.ipodSound).put("ipodHaptics", st.ipodHaptics)
        )
        return o.toString()
    }

    private fun decode(raw: String?): UserState {
        if (raw == null) return UserState()
        return try {
            val o = JSONObject(raw)
            val playlists = ArrayList<Playlist>()
            o.optJSONArray("pl")?.let { a ->
                for (i in 0 until a.length()) {
                    val p = a.getJSONObject(i)
                    playlists.add(
                        Playlist(p.getString("id"), p.getString("name"), p.optJSONArray("paths")?.strings() ?: emptyList())
                    )
                }
            }
            val s = o.optJSONObject("set") ?: JSONObject()
            val d = AppSettings()
            UserState(
                playlists,
                AppSettings(
                    useItunes = s.optBoolean("itunes", d.useItunes),
                    useDeezer = s.optBoolean("deezer", d.useDeezer),
                    useMusicBrainz = s.optBoolean("mb", d.useMusicBrainz),
                    country = s.optString("country", d.country),
                    minScore = s.optInt("minScore", d.minScore),
                    sort = runCatching { SortKey.valueOf(s.optString("sort", d.sort.name)) }.getOrDefault(d.sort),
                    sortDesc = s.optBoolean("desc", d.sortDesc),
                    themeMode = s.optInt("theme", d.themeMode),
                    seekSeconds = s.optInt("seekSec", d.seekSeconds), // new key: resets everyone to the 5s default
                    albumGrid = s.optBoolean("grid", d.albumGrid),
                    playerStyle = s.optInt("style", d.playerStyle),
                    ipodSound = s.optBoolean("ipodSound", d.ipodSound),
                    ipodHaptics = s.optBoolean("ipodHaptics", d.ipodHaptics),
                )
            )
        } catch (_: Exception) {
            UserState()
        }
    }
}
