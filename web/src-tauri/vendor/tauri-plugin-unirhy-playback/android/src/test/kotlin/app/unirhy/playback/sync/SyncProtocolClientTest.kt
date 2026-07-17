package app.unirhy.playback.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncProtocolClientTest {

    @Test
    fun `reconnect delays follow the 1s 2s 5s ladder and cap at 5s`() {
        assertEquals(1_000L, SyncProtocolClient.reconnectDelayMs(0))
        assertEquals(2_000L, SyncProtocolClient.reconnectDelayMs(1))
        assertEquals(5_000L, SyncProtocolClient.reconnectDelayMs(2))
        assertEquals(5_000L, SyncProtocolClient.reconnectDelayMs(3))
        assertEquals(5_000L, SyncProtocolClient.reconnectDelayMs(100))
    }

    @Test
    fun `ws url derives from the api base url scheme`() {
        assertEquals(
            "ws://backend.local:8080/ws/playback-sync",
            SyncProtocolClient.buildWsUrl("http://backend.local:8080"),
        )
        assertEquals(
            "wss://music.example.com/ws/playback-sync",
            SyncProtocolClient.buildWsUrl("https://music.example.com/"),
        )
    }
}
