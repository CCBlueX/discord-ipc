package net.ccbluex.discordipc

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.time.Instant

@JvmRecord
data class DiscordActivity(
    val type: Type = Type.PLAYING,
    val statusDisplayType: StatusDisplayType = StatusDisplayType.NAME,
    val state: String? = null,
    val details: String? = null,
    val startTimestamp: Instant? = null,
    val largeImage: Image? = null,
    val smallImage: Image? = null,
    val buttons: Collection<Button> = emptyList(),
) {

    init {
        require(buttons.size <= MAX_BUTTONS) { "Discord activities support at most $MAX_BUTTONS buttons" }
    }

    internal fun toJson(): JsonObject = JsonObject().apply {
        addProperty("type", type.value)
        addProperty("status_display_type", statusDisplayType.value)
        state?.takeIf(String::isNotBlank)?.let { addProperty("state", it) }
        details?.takeIf(String::isNotBlank)?.let { addProperty("details", it) }
        startTimestamp?.let { timestamp ->
            add("timestamps", JsonObject().apply {
                addProperty("start", timestamp.epochSecond)
            })
        }

        if (largeImage != null || smallImage != null) {
            add("assets", JsonObject().apply {
                largeImage?.let { image ->
                    addProperty("large_image", image.key)
                    image.tooltip?.takeIf(String::isNotBlank)?.let { addProperty("large_text", it) }
                }
                smallImage?.let { image ->
                    addProperty("small_image", image.key)
                    image.tooltip?.takeIf(String::isNotBlank)?.let { addProperty("small_text", it) }
                }
            })
        }

        if (buttons.isNotEmpty()) {
            add("buttons", JsonArray().apply {
                buttons.forEach { button ->
                    add(JsonObject().apply {
                        addProperty("label", button.label)
                        addProperty("url", button.url)
                    })
                }
            })
        }
    }

    enum class Type(val value: Int) {
        PLAYING(0),
        STREAMING(1),
        LISTENING(2),
        WATCHING(3),
        CUSTOM(4),
        COMPETING(5),
    }

    enum class StatusDisplayType(val value: Int) {
        NAME(0),
        STATE(1),
        DETAILS(2),
    }

    @JvmRecord
    data class Image(val key: String, val tooltip: String? = null) {
        init {
            require(key.isNotBlank()) { "Discord activity image key must not be blank" }
        }
    }

    @JvmRecord
    data class Button(val label: String, val url: String) {
        init {
            require(label.isNotBlank()) { "Discord activity button label must not be blank" }
            require(url.isNotBlank()) { "Discord activity button URL must not be blank" }
        }
    }

    private companion object {
        const val MAX_BUTTONS = 2
    }
}
