package com.barley.musicplayer.presentation.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barley.musicplayer.data.model.Track
import com.barley.musicplayer.data.search.RecentSearchRepository
import com.barley.musicplayer.data.youtube.YoutubeExtractorService
import com.barley.musicplayer.playback.PlayerController
import com.barley.musicplayer.playback.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val youtubeExtractorService: YoutubeExtractorService,
    private val playerController: PlayerController,
    private val recentSearchRepository: RecentSearchRepository,
) : ViewModel() {

    var query by mutableStateOf("")
        private set

    private val _results = MutableStateFlow<List<Track>>(emptyList())
    val results: StateFlow<List<Track>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val playerState: StateFlow<PlayerUiState> = playerController.state

    val recentQueries: StateFlow<List<String>> = recentSearchRepository.recentQueries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        playerController.connect()
    }

    fun onQueryChange(newQuery: String) {
        query = newQuery
        if (newQuery.isBlank()) {
            _results.value = emptyList()
            _errorMessage.value = null
        }
    }

    fun onSearch() {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                _results.value = youtubeExtractorService.search(trimmed)
                recentSearchRepository.addQuery(trimmed)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "검색 중 오류가 발생했어요"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onRecentQueryClick(recentQuery: String) {
        query = recentQuery
        onSearch()
    }

    fun clearRecentQueries() {
        viewModelScope.launch { recentSearchRepository.clear() }
    }

    fun onTrackClick(index: Int) {
        playerController.playQueue(_results.value, index)
    }

    fun togglePlayPause() = playerController.togglePlayPause()

    fun skipNext() = playerController.skipNext()

    fun skipPrevious() = playerController.skipPrevious()

    override fun onCleared() {
        playerController.release()
        super.onCleared()
    }
}
