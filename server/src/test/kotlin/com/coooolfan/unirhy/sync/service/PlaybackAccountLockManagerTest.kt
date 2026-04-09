package com.coooolfan.unirhy.sync.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlaybackAccountLockManagerTest {
    @Test
    fun `unwrap account lock execution preserves nullable callback result`() {
        val result: String? = unwrapAccountLockExecution(
            accountId = 1L,
            execution = AccountLockExecution<String?>(null),
        )

        assertNull(result)
    }

    @Test
    fun `unwrap account lock execution returns non-null callback result`() {
        val result = unwrapAccountLockExecution(
            accountId = 1L,
            execution = AccountLockExecution("ok"),
        )

        assertEquals("ok", result)
    }

    @Test
    fun `unwrap account lock execution rejects missing transaction result`() {
        val exception = assertFailsWith<IllegalStateException> {
            unwrapAccountLockExecution<String>(
                accountId = 1L,
                execution = null,
            )
        }

        assertEquals("Account lock transaction returned null for accountId=1", exception.message)
    }
}
