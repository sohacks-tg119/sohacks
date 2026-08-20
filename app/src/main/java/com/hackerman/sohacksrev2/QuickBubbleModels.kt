package com.hackerman.sohacksrev2

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A single user-facing operation in a Quick Bubble sequence. */
enum class QuickBubbleActionType {
    ECO,
    NORMAL,
    SPORT,
    DEV,
    LOCK,
    UNLOCK,
    SPEED,
    ADVANCED_MODE,
    EXTRA,
    EXIT_APP
}

data class QuickBubbleAction(
    val type: QuickBubbleActionType,
    val value: String? = null
) {
    fun label(model: ScooterModel): String = when (type) {
        QuickBubbleActionType.ECO -> "Eco"
        QuickBubbleActionType.NORMAL -> "Normal"
        QuickBubbleActionType.SPORT -> "Sport"
        QuickBubbleActionType.DEV -> "Dev"
        QuickBubbleActionType.LOCK -> "Sperren"
        QuickBubbleActionType.UNLOCK -> "Entsperren"
        QuickBubbleActionType.SPEED -> "${value?.toIntOrNull() ?: 0} km/h"
        QuickBubbleActionType.ADVANCED_MODE -> "Mode ${value?.toIntOrNull() ?: 0}"
        QuickBubbleActionType.EXTRA -> model.extraCommands
            .firstOrNull { it.id == value }
            ?.label
            ?: "Kommando"
        QuickBubbleActionType.EXIT_APP -> "App beenden"
    }

    fun isAvailableFor(model: ScooterModel): Boolean = when (type) {
        QuickBubbleActionType.ECO -> model.commands.eco != null
        QuickBubbleActionType.NORMAL -> model.commands.normal != null
        QuickBubbleActionType.SPORT -> model.commands.sport != null
        QuickBubbleActionType.DEV -> model.commands.dev != null
        QuickBubbleActionType.LOCK -> model.commands.lock != null
        QuickBubbleActionType.UNLOCK -> model.commands.unlock != null
        QuickBubbleActionType.SPEED -> value?.toIntOrNull() in model.supportedSpeeds
        QuickBubbleActionType.ADVANCED_MODE -> value?.toIntOrNull() in 1..model.maxAdvancedMode
        QuickBubbleActionType.EXTRA -> model.extraCommands.any { it.id == value }
        QuickBubbleActionType.EXIT_APP -> true
    }
}

data class QuickBubble(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Int = DEFAULT_COLOR,
    val actions: List<QuickBubbleAction>,
    /** Horizontal position from 0 (left) to 1 (right), relative to free layer space. */
    val positionX: Float = DEFAULT_POSITION_X,
    /** Vertical position from 0 (top) to 1 (bottom), relative to free layer space. */
    val positionY: Float = DEFAULT_POSITION_Y,
    val sizeDp: Int = DEFAULT_SIZE_DP
) {
    fun normalized(): QuickBubble = copy(
        name = name.trim().take(MAX_NAME_LENGTH).ifEmpty { "Quick" },
        positionX = positionX.coerceIn(0f, 1f),
        positionY = positionY.coerceIn(0f, 1f),
        sizeDp = sizeDp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
    )

    companion object {
        const val DEFAULT_COLOR: Int = -13248870 // #FF35D69A
        const val DEFAULT_POSITION_X = 0.08f
        const val DEFAULT_POSITION_Y = 0.2f
        const val DEFAULT_SIZE_DP = 64
        const val MIN_SIZE_DP = 48
        const val MAX_SIZE_DP = 120
        const val MAX_NAME_LENGTH = 10
    }
}

/** A scope is deliberately both model- and physical-device-specific. */
data class QuickBubbleScope(
    val modelId: String,
    val deviceAddress: String?
) {
    val storageKey: String
        get() = "bubbles::$modelId::${deviceAddress?.uppercase() ?: NO_DEVICE}"

    companion object {
        private const val NO_DEVICE = "no-device"

        fun current(context: Context): QuickBubbleScope {
            val preferences = context.getSharedPreferences(AppPreferences.NAME, Context.MODE_PRIVATE)
            return QuickBubbleScope(
                modelId = preferences.getString(
                    AppPreferences.KEY_MODEL_ID,
                    ScooterCommandCatalog.defaultModel.id
                ) ?: ScooterCommandCatalog.defaultModel.id,
                deviceAddress = preferences.getString(AppPreferences.KEY_DEVICE_ADDRESS, null)
            )
        }
    }
}

/** JSON-backed persistence kept separate from general app preferences. */
class QuickBubbleStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(scope: QuickBubbleScope): List<QuickBubble> {
        val raw = preferences.getString(scope.storageKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    decodeBubble(array.optJSONObject(index) ?: continue)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(scope: QuickBubbleScope, bubbles: List<QuickBubble>) {
        val array = JSONArray()
        bubbles.map(QuickBubble::normalized).forEach { array.put(encodeBubble(it)) }
        preferences.edit().putString(scope.storageKey, array.toString()).apply()
    }

    fun upsert(scope: QuickBubbleScope, bubble: QuickBubble) {
        val bubbles = load(scope).toMutableList()
        val index = bubbles.indexOfFirst { it.id == bubble.id }
        if (index >= 0) bubbles[index] = bubble.normalized() else bubbles += bubble.normalized()
        save(scope, bubbles)
    }

    fun delete(scope: QuickBubbleScope, bubbleId: String) {
        save(scope, load(scope).filterNot { it.id == bubbleId })
    }

    private fun encodeBubble(bubble: QuickBubble): JSONObject = JSONObject().apply {
        put("id", bubble.id)
        put("name", bubble.name)
        put("color", bubble.color)
        put("x", bubble.positionX.toDouble())
        put("y", bubble.positionY.toDouble())
        put("size", bubble.sizeDp)
        put("actions", JSONArray().apply {
            bubble.actions.forEach { action ->
                put(JSONObject().apply {
                    put("type", action.type.name)
                    action.value?.let { put("value", it) }
                })
            }
        })
    }

    private fun decodeBubble(json: JSONObject): QuickBubble? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = json.optString("name").takeIf { it.isNotBlank() } ?: "Quick"
        val actionArray = json.optJSONArray("actions") ?: JSONArray()
        val actions = buildList {
            for (index in 0 until actionArray.length()) {
                val actionJson = actionArray.optJSONObject(index) ?: continue
                val type = runCatching {
                    QuickBubbleActionType.valueOf(actionJson.optString("type"))
                }.getOrNull() ?: continue
                val value = actionJson.optString("value").takeIf { it.isNotBlank() }
                add(QuickBubbleAction(type, value))
            }
        }
        return QuickBubble(
            id = id,
            name = name,
            color = json.optInt("color", QuickBubble.DEFAULT_COLOR),
            actions = actions,
            positionX = json.optDouble("x", QuickBubble.DEFAULT_POSITION_X.toDouble()).toFloat(),
            positionY = json.optDouble("y", QuickBubble.DEFAULT_POSITION_Y.toDouble()).toFloat(),
            sizeDp = json.optInt("size", QuickBubble.DEFAULT_SIZE_DP)
        ).normalized()
    }

    companion object {
        private const val PREFERENCES_NAME = "quick_bubbles"
    }
}
