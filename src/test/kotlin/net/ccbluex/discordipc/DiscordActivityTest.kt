package net.ccbluex.discordipc

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DiscordActivityTest {

    @Test
    fun `serializes supported rich presence fields`() {
        val activity = DiscordActivity(
            type = DiscordActivity.Type.COMPETING,
            statusDisplayType = DiscordActivity.StatusDisplayType.DETAILS,
            state = "Playing",
            details = "LiquidBounce",
            startTimestamp = Instant.ofEpochMilli(1_750_000_000_123L),
            largeImage = DiscordActivity.Image("liquidbounce", "Minecraft 26.2"),
            buttons = listOf(DiscordActivity.Button("Website", "https://liquidbounce.net")),
        ).toJson()

        assertEquals(5, activity["type"].asInt)
        assertEquals(2, activity["status_display_type"].asInt)
        assertEquals(1_750_000_000L, activity["timestamps"].asJsonObject["start"].asLong)
        assertEquals("liquidbounce", activity["assets"].asJsonObject["large_image"].asString)
        assertEquals("Website", activity["buttons"].asJsonArray[0].asJsonObject["label"].asString)
    }

    @Test
    fun `omits blank optional text`() {
        val activity = DiscordActivity(state = "", details = " ").toJson()

        assertFalse(activity.has("state"))
        assertFalse(activity.has("details"))
    }
}
