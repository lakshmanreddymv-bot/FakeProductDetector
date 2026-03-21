package com.example.fakeproductdetector.domain.usecase

import com.example.fakeproductdetector.domain.model.*
import com.example.fakeproductdetector.domain.repository.ProductRepository
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ScanProductUseCaseTest {

    private lateinit var mockRepository: ProductRepository
    private lateinit var useCase: ScanProductUseCase

    private val fakeProduct = Product(
        id = "p1",
        name = "Tylenol",
        barcode = "300450122377",
        imageUri = "content://image",
        category = Category.MEDICINE,
        scannedAt = 1000L
    )

    private val authenticResult = ScanResult(
        id = "r1",
        product = fakeProduct,
        authenticityScore = 95f,
        verdict = Verdict.AUTHENTIC,
        redFlags = emptyList(),
        explanation = "Looks authentic",
        scannedAt = 1000L
    )

    private val fakeResult = ScanResult(
        id = "r2",
        product = fakeProduct,
        authenticityScore = 10f,
        verdict = Verdict.LIKELY_FAKE,
        redFlags = listOf("Blurry barcode", "Wrong font"),
        explanation = "Multiple counterfeiting indicators",
        scannedAt = 1000L
    )

    @Before
    fun setUp() {
        mockRepository = mock()
        useCase = ScanProductUseCase(mockRepository)
    }

    @Test
    fun `invoke emits result when repository succeeds`() = runTest {
        whenever(mockRepository.scanProduct(any(), any(), any()))
            .thenReturn(flowOf(ScanEvent.Result(authenticResult)))

        val result = useCase("content://image", "300450122377", Category.MEDICINE)
            .filterIsInstance<ScanEvent.Result>().first().scanResult

        assertEquals(Verdict.AUTHENTIC, result.verdict)
        assertEquals(95f, result.authenticityScore)
    }

    @Test
    fun `invoke propagates exception when repository flow throws`() = runTest {
        val exception = RuntimeException("Network error")
        whenever(mockRepository.scanProduct(any(), any(), any()))
            .thenReturn(flow { throw exception })

        val outcome = runCatching {
            useCase("content://image", "300450122377", Category.MEDICINE).first()
        }

        assertTrue(outcome.isFailure)
        assertEquals("Network error", outcome.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke emits LIKELY_FAKE verdict when product is counterfeit`() = runTest {
        whenever(mockRepository.scanProduct(any(), any(), any()))
            .thenReturn(flowOf(ScanEvent.Result(fakeResult)))

        val result = useCase("content://image", "111", Category.LUXURY)
            .filterIsInstance<ScanEvent.Result>().first().scanResult

        assertEquals(Verdict.LIKELY_FAKE, result.verdict)
        assertEquals(2, result.redFlags.size)
    }

    @Test
    fun `invoke delegates imageUri to repository correctly`() = runTest {
        whenever(mockRepository.scanProduct(any(), any(), any()))
            .thenReturn(flowOf(ScanEvent.Result(authenticResult)))

        useCase("content://specific_image", "123", Category.FOOD)
            .filterIsInstance<ScanEvent.Result>().first()

        verify(mockRepository).scanProduct("content://specific_image", "123", Category.FOOD)
    }

    @Test
    fun `invoke with null barcode still calls repository`() = runTest {
        whenever(mockRepository.scanProduct(any(), isNull(), any()))
            .thenReturn(flowOf(ScanEvent.Result(authenticResult)))

        val result = useCase("content://image", null, Category.OTHER)
            .filterIsInstance<ScanEvent.Result>().first().scanResult

        assertEquals(Verdict.AUTHENTIC, result.verdict)
        verify(mockRepository).scanProduct("content://image", null, Category.OTHER)
    }

    @Test
    fun `invoke handles SUSPICIOUS verdict`() = runTest {
        val suspiciousResult = authenticResult.copy(
            verdict = Verdict.SUSPICIOUS,
            authenticityScore = 55f,
            redFlags = listOf("Packaging slightly off")
        )
        whenever(mockRepository.scanProduct(any(), any(), any()))
            .thenReturn(flowOf(ScanEvent.Result(suspiciousResult)))

        val result = useCase("content://image", "999", Category.ELECTRONICS)
            .filterIsInstance<ScanEvent.Result>().first().scanResult

        assertEquals(Verdict.SUSPICIOUS, result.verdict)
        assertEquals(55f, result.authenticityScore)
    }

    @Test
    fun `invoke passes through Progress events before Result`() = runTest {
        val events = listOf(
            ScanEvent.Progress("Analyzing with Gemini…"),
            ScanEvent.Progress("Verifying with Claude…"),
            ScanEvent.Result(authenticResult)
        )
        whenever(mockRepository.scanProduct(any(), any(), any()))
            .thenReturn(flowOf(*events.toTypedArray()))

        val result = useCase("content://image", "123", Category.MEDICINE)
            .filterIsInstance<ScanEvent.Result>().first().scanResult

        assertEquals(Verdict.AUTHENTIC, result.verdict)
    }
}