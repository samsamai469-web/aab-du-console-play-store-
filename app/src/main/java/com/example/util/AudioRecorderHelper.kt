package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Enregistreur audio résilient haute définition pour la reconnaissance vocale multilingue.
 * Priorise AudioRecord PCM 16kHz .wav sans latence de démarrage et bascule sur MediaRecorder si requis.
 */
class AudioRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private var activeAudioFile: File? = null
    private var startTimeMillis = 0L

    @Volatile
    private var currentPcmAmplitude = 0

    @Volatile
    private var nonZeroSampleFrames = 0

    @Volatile
    private var peakObservedSample = 0

    companion object {
        private const val TAG = "AudioRecorderHelper"
        private const val SAMPLE_RATE = 16000
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): File? {
        stopRecordingInternal(deleteFile = true)
        startTimeMillis = System.currentTimeMillis()
        currentPcmAmplitude = 0
        nonZeroSampleFrames = 0
        peakObservedSample = 0

        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null && am.mode == AudioManager.MODE_IN_COMMUNICATION) {
                am.mode = AudioManager.MODE_NORMAL
            }
        } catch (_: Exception) {}

        // 1. Priorité absolue : Enregistrement PCM WAV 16kHz sans perte avec DSP matériel (NoiseSuppressor + Gain Auto)
        // Aucun délai d'encodeur AAC au démarrage (ne coupe jamais la 1re syllabe "Ne y...") et fidélité maximale pour le Mooré
        val wavFile = startPcmWavRecording()
        if (wavFile != null) {
            return wavFile
        }

        // 2. Repli : MediaRecorder (AAC .m4a)
        val sourcesToTry = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )

        for (audioSource in sourcesToTry) {
            try {
                val file = File(context.cacheDir, "speech_rec_${System.currentTimeMillis()}.m4a")
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                recorder.setAudioSource(audioSource)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioChannels(1)
                recorder.setAudioSamplingRate(16000)
                recorder.setAudioEncodingBitRate(96000)
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare()
                recorder.start()

                mediaRecorder = recorder
                activeAudioFile = file
                isRecording.set(true)
                Log.d(TAG, "Enregistrement MediaRecorder AAC démarré avec source audio $audioSource")
                return file
            } catch (e: Exception) {
                Log.w(TAG, "MediaRecorder source $audioSource échoué: ${e.message}")
                try {
                    mediaRecorder?.release()
                } catch (_: Exception) {}
                mediaRecorder = null
            }
        }

        return null
    }

    @SuppressLint("MissingPermission")
    private fun startPcmWavRecording(): File? {
        // Priorité à MIC (universel sur smartphones et émulateurs, avec meilleur routage matériel),
        // puis VOICE_RECOGNITION et VOICE_COMMUNICATION en repli.
        val sources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )
        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) return null
        val bufferSize = maxOf(minBufSize * 2, 8192)

        for (src in sources) {
            try {
                val record = AudioRecord(
                    src,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    attachHardwareAudioEffects(record.audioSessionId)

                    val wavFile = File(context.cacheDir, "speech_rec_${System.currentTimeMillis()}.wav")
                    record.startRecording()
                    audioRecord = record
                    activeAudioFile = wavFile
                    isRecording.set(true)

                    recordingThread = thread(start = true, name = "AudioRecordWavThread") {
                        writeAudioDataToWav(record, wavFile, bufferSize)
                    }
                    Log.d(TAG, "Enregistrement AudioRecord WAV 16kHz sans perte démarré (source=$src)")
                    return wavFile
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Essai AudioRecord source $src échoué: ${e.message}")
            }
        }
        return null
    }

    private fun attachHardwareAudioEffects(audioSessionId: Int) {
        try {
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (_: Exception) {}
    }

    private fun writeAudioDataToWav(record: AudioRecord, outputFile: File, bufferSize: Int) {
        // Trames ultra-courtes de 30 ms (480 échantillons à 16 kHz = 960 octets)
        val frameShorts = 480
        val audioData = ShortArray(frameShorts)
        val fos = FileOutputStream(outputFile)

        // Écriture de l'en-tête WAV factice (44 octets)
        writeWavHeader(fos, 0, 0, SAMPLE_RATE, 1, 16)

        var totalAudioLen = 0L

        try {
            while (isRecording.get()) {
                val readShorts = record.read(audioData, 0, frameShorts)
                if (readShorts > 0) {
                    var sumBoostedSquares = 0.0
                    var framePeak = 0
                    val frameBytes = ByteArray(readShorts * 2)

                    for (i in 0 until readShorts) {
                        val rawSample = audioData[i].toInt()
                        if (rawSample != 0) {
                            val absRaw = kotlin.math.abs(rawSample)
                            if (absRaw > peakObservedSample) {
                                peakObservedSample = absRaw
                            }
                        }
                        // Pré-amplification vocale linéaire (x2.2) avec soft-knee pour micros discrets
                        val amplified = (rawSample * 11) / 5
                        val boosted = when {
                            amplified > 24000 -> 24000 + ((amplified - 24000) / 3).coerceAtMost(8767)
                            amplified < -24000 -> -24000 + ((amplified + 24000) / 3).coerceAtLeast(-8768)
                            else -> amplified
                        }.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        val absBoosted = kotlin.math.abs(boosted)
                        if (absBoosted > framePeak) {
                            framePeak = absBoosted
                        }
                        sumBoostedSquares += (boosted.toDouble() * boosted.toDouble())
                        frameBytes[i * 2] = (boosted and 0xFF).toByte()
                        frameBytes[i * 2 + 1] = ((boosted shr 8) and 0xFF).toByte()
                    }

                    val boostedRms = kotlin.math.sqrt(sumBoostedSquares / readShorts).toInt()
                    val combinedLevel = maxOf(boostedRms, framePeak / 3)
                    if (framePeak > 0) {
                        nonZeroSampleFrames++
                    }
                    currentPcmAmplitude = combinedLevel.coerceAtMost(32767)

                    // Écriture directe dès la milliseconde 0 pour ne jamais couper le début de la parole
                    fos.write(frameBytes)
                    totalAudioLen += frameBytes.size
                } else if (readShorts < 0) {
                    Log.w(TAG, "AudioRecord.read erreur: $readShorts")
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Interruption boucle AudioRecord: ${e.message}")
        } finally {
            try {
                fos.close()
            } catch (_: Exception) {}
            updateWavHeader(outputFile, totalAudioLen, SAMPLE_RATE, 1, 16)
        }
    }

    fun getMaxAmplitude(): Int {
        if (!isRecording.get()) return 0
        mediaRecorder?.let {
            return try {
                it.maxAmplitude
            } catch (_: Exception) {
                0
            }
        }
        return currentPcmAmplitude
    }

    fun hadNonZeroAudio(): Boolean {
        return mediaRecorder != null || (nonZeroSampleFrames > 0 && peakObservedSample > 0)
    }

    fun stopRecording(): File? {
        if (!isRecording.get()) return activeAudioFile
        isRecording.set(false)

        mediaRecorder?.let { mr ->
            try {
                mr.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Erreur arrêt MediaRecorder: ${e.message}")
            }
            try {
                mr.release()
            } catch (_: Exception) {}
            mediaRecorder = null
        }

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}

        // Attend que le thread d'écriture ferme le flux et mette à jour l'en-tête WAV (taille réelle)
        // AVANT de libérer AudioRecord et de retourner le fichier à Whisper
        try {
            recordingThread?.join(900)
        } catch (_: Exception) {}
        recordingThread = null

        try { noiseSuppressor?.release() } catch (_: Exception) {}
        noiseSuppressor = null
        try { automaticGainControl?.release() } catch (_: Exception) {}
        automaticGainControl = null
        try { echoCanceler?.release() } catch (_: Exception) {}
        echoCanceler = null

        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        return activeAudioFile
    }

    private fun stopRecordingInternal(deleteFile: Boolean) {
        val file = stopRecording()
        if (deleteFile && file != null && file.exists()) {
            file.delete()
        }
        activeAudioFile = null
    }

    fun cancel() {
        stopRecordingInternal(deleteFile = true)
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = longSampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0 // PCM = 1
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0
        header[34] = bitsPerSample.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
        out.write(header, 0, 44)
    }

    private fun updateWavHeader(wavFile: File, totalAudioLen: Long, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        try {
            val totalDataLen = totalAudioLen + 36
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val raf = RandomAccessFile(wavFile, "rw")
            raf.seek(4)
            raf.write((totalDataLen and 0xff).toInt())
            raf.write(((totalDataLen shr 8) and 0xff).toInt())
            raf.write(((totalDataLen shr 16) and 0xff).toInt())
            raf.write(((totalDataLen shr 24) and 0xff).toInt())
            raf.seek(40)
            raf.write((totalAudioLen and 0xff).toInt())
            raf.write(((totalAudioLen shr 8) and 0xff).toInt())
            raf.write(((totalAudioLen shr 16) and 0xff).toInt())
            raf.write(((totalAudioLen shr 24) and 0xff).toInt())
            raf.close()
        } catch (e: Exception) {
            Log.w(TAG, "Erreur réécriture header WAV: ${e.message}")
        }
    }
}
