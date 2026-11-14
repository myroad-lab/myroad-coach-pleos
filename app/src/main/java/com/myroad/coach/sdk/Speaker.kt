package com.myroad.coach.sdk

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * 음성 안내.
 * 1순위: Pleos TextToSpeech SDK (ON_DEVICE 모드 → 앱 등록 불필요)
 * 2순위: Pleos TTS 초기화에 실패하면 안드로이드 기본 TTS로 대체 (에뮬레이터 환경 차이 대비)
 *
 * 말하기 전에 오디오 포커스(잠깐, 다른 소리는 줄이기)를 요청합니다.
 *  - 받지 못하면 말하지 않고 false를 돌려줍니다 → 화면 카드만 보여 줍니다.
 *  - 말하는 중 포커스를 잃으면(예: 내비 음성 안내, 통화) 바로 멈춥니다.
 *  - 다 읽으면 포커스를 돌려줍니다.
 * 음성 '인식'(STT)은 쓰지 않습니다.
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var pleosTts: TextToSpeech? = null
    private var pleosReady = false
    private var fallback: AndroidTts? = null

    var engineName: String = "준비 중"
        private set

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            stop()
        }
    }

    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    } else null

    private val pleosEvents = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = abandonFocus()
        override fun onError(errorType: ErrorType) = abandonFocus()
        override fun onStop() = abandonFocus()
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun initialize() {
        try {
            val tts = TextToSpeech(appContext, Mode.ON_DEVICE)
            pleosTts = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    pleosReady = true
                    engineName = "Pleos TTS"
                    runCatching { tts.setLocale(Locale.KOREAN) }
                    runCatching { tts.setAudioAttributes(attributes) }
                    runCatching { tts.addEventListener(pleosEvents) }
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "Pleos TTS 초기화 실패: $errorType → 기본 TTS로 대체")
                    initFallback()
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos TTS 사용 불가 → 기본 TTS로 대체", e)
            initFallback()
        }
    }

    private fun initFallback() {
        if (fallback != null) return
        fallback = AndroidTts(appContext) { status ->
            if (status == AndroidTts.SUCCESS) {
                fallback?.setLanguage(Locale.KOREAN)
                fallback?.setAudioAttributes(attributes)
                fallback?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = abandonFocus()
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = abandonFocus()
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = abandonFocus()
                })
                engineName = "Android TTS"
            }
        }
    }

    private fun requestFocus(): Boolean {
        val am = audio ?: return true
        val result = if (focusRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            am.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val am = audio ?: return
        if (focusRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            am.abandonAudioFocusRequest(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusListener)
        }
    }

    /** 말하기 시도. 오디오 포커스를 받지 못했거나 음성 엔진이 없으면 false (→ 화면 카드만) */
    fun speak(text: String): Boolean {
        if (!pleosReady && fallback == null) return false
        if (!requestFocus()) {
            Log.i(TAG, "오디오 포커스를 받지 못해 화면으로만 안내합니다")
            return false
        }
        try {
            if (pleosReady) {
                pleosTts?.speak(text, true)
                return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos TTS speak 실패", e)
        }
        val fb = fallback
        if (fb == null) {
            abandonFocus()
            return false
        }
        fb.speak(text, AndroidTts.QUEUE_FLUSH, null, "myroad")
        return true
    }

    /** 재탐색 등으로 안내가 필요 없어졌을 때 즉시 멈춤 */
    fun stop() {
        runCatching { pleosTts?.stop() }
        runCatching { fallback?.stop() }
        abandonFocus()
    }

    fun release() {
        stop()
        runCatching { pleosTts?.removeEventListener(pleosEvents) }
        runCatching { pleosTts?.release() }
        runCatching { fallback?.shutdown() }
    }

    companion object {
        private const val TAG = "MyRoadSpeaker"
    }
}
