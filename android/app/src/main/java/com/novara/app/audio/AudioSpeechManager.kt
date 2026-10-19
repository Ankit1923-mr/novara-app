package com.novara.app.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AudioPlaybackState { IDLE, LOADING, PLAYING, ERROR }

/**
 * Priority 2 — Native Android TextToSpeech Helper for Spanish Phrase Playback
 */
class SpanishTextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _playbackState = MutableStateFlow(AudioPlaybackState.IDLE)
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val spanishLocale = Locale("es", "ES")
            val langResult = tts?.setLanguage(spanishLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to generic Spanish
                tts?.setLanguage(Locale("es"))
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _playbackState.value = AudioPlaybackState.PLAYING
                }

                override fun onDone(utteranceId: String?) {
                    _playbackState.value = AudioPlaybackState.IDLE
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _playbackState.value = AudioPlaybackState.ERROR
                    _errorMessage.value = "Playback error occurred"
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _playbackState.value = AudioPlaybackState.ERROR
                    _errorMessage.value = "Playback error code: $errorCode"
                }
            })

            isInitialized = true
            _playbackState.value = AudioPlaybackState.IDLE
        } else {
            _playbackState.value = AudioPlaybackState.ERROR
            _errorMessage.value = "TextToSpeech initialization failed"
        }
    }

    fun speakSpanish(phrase: String) {
        if (phrase.isBlank()) return
        if (!isInitialized) {
            _playbackState.value = AudioPlaybackState.LOADING
            return
        }

        stop()
        _playbackState.value = AudioPlaybackState.PLAYING
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "novara_utt_" + System.currentTimeMillis())
        }
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "novara_utt_" + System.currentTimeMillis())
    }

    fun stop() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
        _playbackState.value = AudioPlaybackState.IDLE
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

/**
 * Priority 3 — Android SpeechRecognizer for Spanish Voice Input
 */
class SpanishSpeechRecognizerManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStateChanged: (Boolean) -> Unit
    ) {
        if (!isRecognitionAvailable()) {
            onError("Speech recognition is not available on this device.")
            return
        }

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onListeningStateChanged(true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    onListeningStateChanged(false)
                }

                override fun onError(error: Int) {
                    onListeningStateChanged(false)
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please try speaking again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. Please speak louder."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        else -> "Speech recognition error ($error)."
                    }
                    onError(errorMsg)
                }

                override fun onResults(results: Bundle?) {
                    onListeningStateChanged(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()
                    if (!spokenText.isNullOrBlank()) {
                        onResult(spokenText)
                    } else {
                        onError("Could not recognize spoken text.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "es-ES")
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
