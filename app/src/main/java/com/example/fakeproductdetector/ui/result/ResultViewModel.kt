package com.example.fakeproductdetector.ui.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakeproductdetector.domain.model.ScanResult
import com.example.fakeproductdetector.domain.repository.ProductRepository
import com.example.fakeproductdetector.ui.tts.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ProductRepository,
    private val ttsManager: TTSManager
) : ViewModel() {

    private val scanId: String = checkNotNull(savedStateHandle["scanId"])

    val result: StateFlow<ScanResult?> = repository
        .getScanById(scanId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    fun toggleMute() {
        val nowMuted = !_isMuted.value
        _isMuted.value = nowMuted
        ttsManager.isMuted = nowMuted
        if (nowMuted) ttsManager.stop()
    }

    fun speakResult(result: ScanResult) {
        ttsManager.speakResult(result)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}