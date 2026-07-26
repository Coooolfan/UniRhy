package com.coooolfan.unirhy.service.plugin.hostapi

import kotlin.test.Test
import kotlin.test.assertEquals

class PluginDataStoreTest {

    /**
     * `listKeys` / `countKeys` 用 `LEFT(key, n) = prefix` 做前缀过滤，
     * 其中 n 必须是 Postgres 口径的字符数（码点数）而非 Kotlin 的 UTF-16 单元数。
     * 用 `prefix.length` 会让含非 BMP 字符的前缀多截一位，过滤结果错位。
     */
    @Test
    fun `prefix length counts code points, not UTF-16 units`() {
        assertEquals(3, prefixLength("abc"))
        assertEquals(0, prefixLength(""))

        // 单个非 BMP 字符：UTF-16 占 2 个单元，但只是 1 个字符
        assertEquals(2, "🎵".length)
        assertEquals(1, prefixLength("🎵"))

        assertEquals(2, prefixLength("🎵a"))
    }
}
