package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiService
import com.example.data.AppRepository
import com.example.data.StoryOptions
import com.example.data.StoryTargetWord
import com.example.data.VocabularyStory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StoryViewModel(
    application: Application,
    private val repository: AppRepository,
    private val apiKeyProvider: () -> String,
    private val languageProvider: () -> String = { Loc.currentLanguage }
) : AndroidViewModel(application) {

    val stories: StateFlow<List<VocabularyStory>> = repository.allVocabularyStories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationStatus = MutableStateFlow("")
    val generationStatus: StateFlow<String> = _generationStatus.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    private val _activeReaderStory = MutableStateFlow<VocabularyStory?>(null)
    val activeReaderStory: StateFlow<VocabularyStory?> = _activeReaderStory.asStateFlow()

    val isReaderOpen = MutableStateFlow(false)
    val isStoryListOpen = MutableStateFlow(false)

    private var generationJob: Job? = null

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        _generationStatus.value = ""
    }

    fun openReader(story: VocabularyStory) {
        _activeReaderStory.value = story
        isReaderOpen.value = true
    }

    fun closeReader() {
        isReaderOpen.value = false
        _activeReaderStory.value = null
    }

    fun openStoryList() {
        isStoryListOpen.value = true
    }

    fun closeStoryList() {
        isStoryListOpen.value = false
    }

    fun generateStory(
        targetWords: List<StoryTargetWord>,
        options: StoryOptions,
        onSuccess: ((VocabularyStory) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _generationError.value = null
            _generationStatus.value = if (languageProvider() == "ar") "جارٍ تأليف القصة بواسطة الذكاء الاصطناعي..." else "Generating story from vocabulary..."

            val result = GeminiService.generateVocabularyStory(
                targetWords = targetWords,
                options = options,
                customApiKey = apiKeyProvider().ifBlank { null },
                language = languageProvider(),
                onProgressUpdate = { status -> _generationStatus.value = status }
            )

            _isGenerating.value = false
            _generationStatus.value = ""

            result.fold(
                onSuccess = { createdStory ->
                    val id = repository.insertVocabularyStory(createdStory)
                    val saved = createdStory.copy(id = id)
                    onSuccess?.invoke(saved)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Failed to generate story."
                    _generationError.value = msg
                    onError?.invoke(msg)
                }
            )
        }
    }

    fun regenerateStory(
        story: VocabularyStory,
        onSuccess: ((VocabularyStory) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val words = story.getTargetWords()
        val options = story.getOptions()
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _generationError.value = null
            _generationStatus.value = if (languageProvider() == "ar") "جارٍ إعادة تأليف القصة بواسطة الذكاء الاصطناعي..." else "Regenerating story..."

            val result = GeminiService.generateVocabularyStory(
                targetWords = words,
                options = options,
                customApiKey = apiKeyProvider().ifBlank { null },
                language = languageProvider(),
                onProgressUpdate = { status -> _generationStatus.value = status }
            )

            _isGenerating.value = false
            _generationStatus.value = ""

            result.fold(
                onSuccess = { generatedStory ->
                    val updated = generatedStory.copy(
                        id = story.id,
                        createdAt = story.createdAt
                    )
                    repository.updateVocabularyStory(updated)
                    if (_activeReaderStory.value?.id == story.id) {
                        _activeReaderStory.value = updated
                    }
                    onSuccess?.invoke(updated)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Failed to regenerate story."
                    _generationError.value = msg
                    onError?.invoke(msg)
                }
            )
        }
    }

    fun updateStory(story: VocabularyStory) {
        viewModelScope.launch {
            repository.updateVocabularyStory(story)
            if (_activeReaderStory.value?.id == story.id) {
                _activeReaderStory.value = story
            }
        }
    }

    fun deleteStory(story: VocabularyStory) {
        viewModelScope.launch {
            repository.deleteVocabularyStory(story)
            if (_activeReaderStory.value?.id == story.id) {
                closeReader()
            }
        }
    }
}
