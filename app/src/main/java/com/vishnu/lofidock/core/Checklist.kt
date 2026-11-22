package com.vishnu.lofidock.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChecklistItem(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val createdAt: Long,
)

/** The study checklist as an immutable value: ordered items, each done or not. */
data class Checklist(val items: List<ChecklistItem> = emptyList()) {

    /** Adds a task to the end. Blank titles are ignored (the list comes back unchanged). */
    fun add(title: String, now: Long = System.currentTimeMillis(), id: String = UUID.randomUUID().toString()): Checklist {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return this
        return copy(items = items + ChecklistItem(id, trimmed.take(MAX_TITLE_LENGTH), isDone = false, createdAt = now))
    }

    fun toggle(id: String): Checklist =
        copy(items = items.map { if (it.id == id) it.copy(isDone = !it.isDone) else it })

    fun remove(id: String): Checklist = copy(items = items.filterNot { it.id == id })

    fun clearDone(): Checklist = copy(items = items.filterNot { it.isDone })

    val doneCount: Int get() = items.count { it.isDone }

    /** Share of tasks done, 0 for an empty list. */
    val progress: Double get() = if (items.isEmpty()) 0.0 else doneCount.toDouble() / items.size

    fun toJson(): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("isDone", item.isDone)
                put("createdAt", item.createdAt)
            })
        }
    }.toString()

    companion object {
        /** Long enough for any real task; keeps a pasted essay from swamping the face. */
        const val MAX_TITLE_LENGTH = 120

        /** Anything unreadable becomes an empty list rather than a crash at launch. */
        fun fromJson(json: String?): Checklist {
            if (json.isNullOrBlank()) return Checklist()
            return runCatching {
                val array = JSONArray(json)
                Checklist((0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    ChecklistItem(o.getString("id"), o.getString("title"), o.getBoolean("isDone"), o.getLong("createdAt"))
                })
            }.getOrDefault(Checklist())
        }
    }
} 
