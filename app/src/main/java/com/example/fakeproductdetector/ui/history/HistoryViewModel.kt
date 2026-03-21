package com.example.fakeproductdetector.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakeproductdetector.domain.model.ScanResult
import com.example.fakeproductdetector.domain.usecase.GetScanHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getScanHistoryUseCase: GetScanHistoryUseCase
) : ViewModel() {

    private val _history = MutableStateFlow<List<ScanResult>>(emptyList())
    val history: StateFlow<List<ScanResult>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            getScanHistoryUseCase().collect { results ->
                _history.value = results
            }
        }
    }
}