package org.wishy.browser

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val title: String, val url: String)

/**
 * Tiny local bookmark store: a JSON list in the app's private
 * SharedPreferences. Nothing is ever uploaded, synced or shared, and
 * (unlike history) an entry only exists because the user explicitly
 * pressed the star. Newest bookmarks come first.
 */
class BookmarkStore(context: Context) {

    private val prefs = context.getSharedPreferences("wishy_bookmarks", Context.MODE_PRIVATE)
    private val items: MutableList<Bookmark> = load()

    fun all(): List<Bookmark> = items.toList()

    fun contains(url: String): Boolean = items.any { same(it.url, url) }

    fun add(title: String?, url: String) {
        if (contains(url)) return
        val name = title?.trim().takeUnless { it.isNullOrEmpty() }
            ?: Uri.parse(url).host
            ?: url
        items.add(0, Bookmark(name, url))
        save()
    }

    fun remove(url: String) {
        if (items.removeAll { same(it.url, url) }) save()
    }

    /** @return true if the page is now bookmarked, false if it was removed. */
    fun toggle(title: String?, url: String): Boolean {
        return if (contains(url)) {
            remove(url)
            false
        } else {
            add(title, url)
            true
        }
    }

    // "https://example.com" and "https://example.com/" are the same page.
    private fun same(a: String, b: String) =
        a.trim().trimEnd('/').equals(b.trim().trimEnd('/'), ignoreCase = true)

    private fun load(): MutableList<Bookmark> {
        val list = mutableListOf<Bookmark>()
        try {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Bookmark(o.getString("title"), o.getString("url")))
            }
        } catch (_: Exception) {
            // Corrupt data: start empty rather than crash the browser.
        }
        return list
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("title", it.title).put("url", it.url))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
