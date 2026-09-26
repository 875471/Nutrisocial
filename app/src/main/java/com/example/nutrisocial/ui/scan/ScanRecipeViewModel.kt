package com.example.nutrisocial.ui.scan

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrisocial.data.ApiResult
import com.example.nutrisocial.data.OcrRecipeProposal
import com.example.nutrisocial.data.RecipeRepository
import com.example.nutrisocial.data.ocr.RecipeTextRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanUiState {
    data object Idle : ScanUiState
    /** ML Kit está leyendo la foto en el propio teléfono. */
    data object Recognizing : ScanUiState
    /** El servidor está separando ingredientes y pasos. */
    data object Analyzing : ScanUiState
    data object NoText : ScanUiState
    data class Error(val message: String, val canRetrySameImage: Boolean) : ScanUiState
    /** Propuesta lista para volcarla en el formulario (se consume una sola vez). */
    data class Ready(val proposal: OcrRecipeProposal) : ScanUiState
}

/** Pipeline de escaneo: foto → OCR on-device → POST /recipes/parse-ocr → propuesta editable. */
// La fábrica por defecto de viewModel() instancia los AndroidViewModel con un constructor (Application).
class ScanRecipeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecipeRepository()
    private val recognizer = RecipeTextRecognizer(application)

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var lastImage: Uri? = null
    private var lastText: String? = null
    private var job: Job? = null

    fun processImage(uri: Uri) {
        lastImage = uri
        lastText = null
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = ScanUiState.Recognizing
            val text = try {
                recognizer.recognize(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ScanUiState.Error("No se pudo leer la imagen. Prueba con otra foto.", canRetrySameImage = false)
                return@launch
            }
            if (text.isBlank()) {
                _state.value = ScanUiState.NoText
                return@launch
            }
            // Texto bruto en logcat (etiqueta NutriSocialOcr) para poder evaluar el OCR y la heurística.
            Log.d(LOG_TAG, "Texto reconocido:\n$text")
            lastText = text
            analyze(text)
        }
    }

    /** Reintenta con la misma imagen: si ya se había leído, solo repite la petición al servidor. */
    fun retry() {
        val text = lastText
        when {
            text != null -> {
                job?.cancel()
                job = viewModelScope.launch { analyze(text) }
            }
            lastImage != null -> processImage(lastImage!!)
        }
    }

    private suspend fun analyze(text: String) {
        _state.value = ScanUiState.Analyzing
        _state.value = when (val result = repository.parseOcr(text)) {
            is ApiResult.Success -> {
                val proposal = result.data
                if (proposal.ingredients.isEmpty() && proposal.steps.isEmpty()) ScanUiState.NoText
                else ScanUiState.Ready(proposal)
            }
            is ApiResult.Error -> ScanUiState.Error(result.message, canRetrySameImage = true)
        }
    }

    /** Tras volcar la propuesta en el formulario, la pantalla vuelve a su estado inicial. */
    fun onProposalConsumed() {
        _state.value = ScanUiState.Idle
    }

    fun reset() {
        job?.cancel()
        _state.value = ScanUiState.Idle
    }

    override fun onCleared() {
        recognizer.close()
    }
}

private const val LOG_TAG = "NutriSocialOcr"
