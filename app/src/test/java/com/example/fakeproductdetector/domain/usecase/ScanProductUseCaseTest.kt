package com.example.fakeproductdetector.domain.usecase

import com.example.fakeproductdetector.domain.model.*
import com.example.fakeproductdetector.domain.repository.ProductRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Unit tests for ScanProductUseCase.
 * Uses mockito-kotlin to mock the repository.
 * Add to app/build.gradle.kts:
 *   testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
 *   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
 */
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
    fun `invoke returns Success when repository succeeds`() = runTest {
        whenever(mockRepository.scanProduct(any(), any(), any()))
            .thenReturn(Result.success(authenticResult))

        val result = useCase("content://image", "300450122377", Category.MEDICINE)

        assertTrue(result.isSuccess)
        assertEquals(Verdict.AUTHENTIC, result.getOrNull()?.verdict)
        assertEquals(95f, result.getOrNull()?.authenticityScore)
    }

    @Test
    fun `invoke returns Failure when repository throws`() = runTest {
        val exception = Exception("Network error")
        whenever(mockRepository.scanProduct(any(), any(), any()))
            .thenReturn(Result.failure(exception))

        val result = useCase("content://image", "300450122377", Category.MEDICINE)

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke returns LIKELY_FAKE verdict when product is counterfeit`() = runTest {
        whenever(mockRepository.scanProduct(any(), any(), any()))
            .thenReturn(Result.success(fakeResult))

        val result = useCase("content://image", "111", Category.LUXURY)

        assertTrue(result.isSuccess)
        assertEquals(Verdict.LIKELY_FAKE, result.getOrNull()?.verdict)
        assertEquals(2, result.getOrNull()?.redFlags?.size)
    }

    @Test
    fun `invoke delegates imageUri to repository correctly`() = runTest {
        whenever(mockRepository.scanProduct(any(), any(), any()))
            .thenReturn(Result.success(authenticResult))

        useCase("content://specific_image", "123", Category.FOOD)

        verify(mockRepository).scanProduct("content://specific_image", "123", Category.FOOD)
    }

    @Test
    fun `invoke with null barcode still calls repository`() = runTest {
        whenever(mockRepository.scanProduct(any(), isNull(), any()))
            .thenReturn(Result.success(authenticResult))

        val result = useCase("content://image", null, Category.OTHER)

        assertTrue(result.isSuccess)
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
            .thenReturn(Result.success(suspiciousResult))

        val result = useCase("content://image", "999", Category.ELECTRONICS)

        assertEquals(Verdict.SUSPICIOUS, result.getOrNull()?.verdict)
        assertEquals(55f, result.getOrNull()?.authenticityScore)
    }
}
