package com.example.fakeproductdetector.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakeproductdetector.domain.model.ScanResult
import com.example.fakeproductdetector.domain.repository.ProductRepository
import com.example.fakeproductdetector.domain.usecase.GetScanHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// S: Single Responsibility — manages scan history list state and deletion for HistoryScreen only
// D: Dependency Inversion — depends on GetScanHistoryUseCase and ProductRepository interfaces
/**
 * Follows Unidirectional Data Flow (UDF) pattern:
 * - Events flow UP from [HistoryScreen] via public functions ([deleteItem])
 * - State flows DOWN to [HistoryScreen] via [history] StateFlow
 * - No direct state mutation from the UI layer
 */
/**
 * ViewModel for [HistoryScreen] that exposes the live scan history list and handles item deletion.
 *
 * Collects the [GetScanHistoryUseCase] Flow into a [StateFlow] so [HistoryScreen] can observe
 * history changes reactively. Deletion is delegated to [ProductRepository.deleteScan], which
 * also cleans up the associated local image file.
 *
 * @property getScanHistoryUseCase Use case that provides the live scan history Flow.
 * @property repository Repository used to delete individual scan records.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getScanHistoryUseCase: GetScanHistoryUseCase,
    private val repository: ProductRepository
) : ViewModel() {

    /** Immutable list of past scan results, ordered newest-first, observed by [HistoryScreen]. */
    val history: StateFlow<List<ScanResult>> = getScanHistoryUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Permanently deletes the scan record identified by [id] and its associated local image.
     *
     * @param id Unique identifier of the scan to delete.
     */
    fun deleteItem(id: String) {
        viewModelScope.launch { repository.deleteScan(id) }
    }
}