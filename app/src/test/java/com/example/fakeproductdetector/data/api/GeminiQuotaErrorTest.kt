package com.example.fakeproductdetector.data.api

import org.junit.Assert.*
import org.junit.Test

class GeminiQuotaErrorTest {

    @Test
    fun `TokenLimitPerMinute sealed class exists and is distinct`() {
        val error: GeminiQuotaError = GeminiQuotaError.TokenLimitPerMinute
        assertNotNull(error)
    }

    @Test
    fun `RequestsPerMinute sealed class exists and is distinct`() {
        val error: GeminiQuotaError = GeminiQuotaError.RequestsPerMinute
        assertNotNull(error)
    }

    @Test
    fun `DailyLimitExhausted sealed class exists and is distinct`() {
        val error: GeminiQuotaError = GeminiQuotaError.DailyLimitExhausted
        assertNotNull(error)
    }

    @Test
    fun `Generic object has a non-null message`() {
        assertNotNull(GeminiQuotaError.Generic.message)
        assertTrue(GeminiQuotaError.Generic.message!!.isNotEmpty())
    }

    @Test
    fun `Different quota errors are not equal`() {
        assertNotEquals(GeminiQuotaError.TokenLimitPerMinute, GeminiQuotaError.RequestsPerMinute)
        assertNotEquals(GeminiQuotaError.RequestsPerMinute, GeminiQuotaError.DailyLimitExhausted)
    }

    @Test
    fun `When expression covers all quota error types`() {
        val errors: List<GeminiQuotaError> = listOf(
            GeminiQuotaError.TokenLimitPerMinute,
            GeminiQuotaError.RequestsPerMinute,
            GeminiQuotaError.DailyLimitExhausted,
            GeminiQuotaError.Generic
        )
        errors.forEach { error ->
            val result = when (error) {
                GeminiQuotaError.TokenLimitPerMinute -> "token_rpm"
                GeminiQuotaError.RequestsPerMinute -> "req_rpm"
                GeminiQuotaError.DailyLimitExhausted -> "daily"
                GeminiQuotaError.Generic -> "generic"
            }
            assertNotNull(result)
        }
    }

    @Test
    fun `All quota errors are subtypes of Exception`() {
        val errors = listOf(
            GeminiQuotaError.TokenLimitPerMinute,
            GeminiQuotaError.RequestsPerMinute,
            GeminiQuotaError.DailyLimitExhausted,
            GeminiQuotaError.Generic
        )
        errors.forEach { assertTrue(it is Exception) }
    }
}