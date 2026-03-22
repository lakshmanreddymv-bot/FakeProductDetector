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
import kotlinx.coroutines.launch
import javax.inject.Inject

// S: Single Responsibility — manages TTS playback state and exposes the scan result for ResultScreen
// D: Dependency Inversion — depends on ProductRepository interface and TTSManager, not concrete data sources
/**
 * Follows Unidirectional Data Flow (UDF) pattern:
 * - Events flow UP from [ResultScreen] via public functions ([toggleMute], [speakResult], [stopSpeaking])
 * - State flows DOWN to [ResultScreen] via [isMuted] StateFlow
 * - No direct state mutation from the UI layer
 */
/**
 * ViewModel for [ResultScreen] that provides the scan result and manages TTS playback state.
 *
 * Loads the [ScanResult] identified by `scanId` from the navigation back-stack via
 * [SavedStateHandle] and exposes it as a [StateFlow]. Delegates TTS operations to [TTSManager]
 * while keeping the mute flag synchronised between the ViewModel state and the TTS singleton.
 *
 * @property savedStateHandle Provides the `scanId` navigation argument.
 * @property ttsManager Singleton TTS engine used to speak scan results aloud.
 */
@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ProductRepository,
    private val ttsManager: TTSManager
) : ViewModel() {

    /** The scan ID extracted from the navigation back-stack entry. */
    private val scanId: String = checkNotNull(savedStateHandle["scanId"])

    /**
     * Live [StateFlow] of the [ScanResult] identified by [scanId].
     *
     * Emits `null` while the database query is in flight or if the record has been deleted.
     */
    val result: StateFlow<ScanResult?> = repository
        .getScanById(scanId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _isMuted = MutableStateFlow(false)

    /** Immutable mute state observed by [ResultScreen] to toggle the volume icon. */
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    init {
        // Reset singleton mute flag so each new result screen auto-speaks by default.
        // _isMuted already initialises to false above; we only need to sync ttsManager.
        ttsManager.isMuted = false
    }

    /**
     * Toggles the TTS mute state. If muting, stops any speech currently in progress.
     */
    fun toggleMute() {
        val nowMuted = !_isMuted.value
        _isMuted.value = nowMuted
        ttsManager.isMuted = nowMuted
        if (nowMuted) ttsManager.stop()
    }

    /**
     * Speaks the result summary aloud, waiting for TTS to be ready if necessary.
     *
     * No-ops if the ViewModel is muted.
     *
     * @param result The [ScanResult] whose summary should be spoken.
     */
    fun speakResult(result: ScanResult) {
        viewModelScope.launch { ttsManager.speakResultWhenReady(result) }
    }

    /**
     * Stops any TTS speech currently in progress without changing the mute state.
     */
    fun stopSpeaking() {
        ttsManager.stop()
    }

    /**
     * Stops TTS playback when the ViewModel is cleared.
     *
     * Does not shut down [TTSManager] since it is a singleton shared across ViewModels.
     */
    override fun onCleared() {
        super.onCleared()
        // stop() only — TTSManager is @Singleton and must not be shut down per-ViewModel
        ttsManager.stop()
    }
}