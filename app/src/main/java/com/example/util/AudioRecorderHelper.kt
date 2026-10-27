package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Enregistreur audio résilient pour la reconnaissance vocale OpenAI Whisper.
 * Tente d'abord MediaRecorder (MPEG-4 / AAC .m4a), et bascule automatiquement
 * sur AudioRecord direct (PCM 16kHz .wav) si l'environnement ou l'émulateur le requiert.
 */
class AudioRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private var activeAudioFile: File? = null
    private var startTimeMillis = 0L

    @Volatile
    private var currentPcmAmplitude = 0

    companion object {
        private const val TAG = "AudioRecorderHelper"
        private const val SAMPLE_RATE = 16000
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): File? {
        stopRecordingInternal(deleteFile = true)
        startTimeMillis = System.currentTimeMillis()

        // 1. Essai MediaRecorder (AAC .m4a) avec VOICE_RECOGNITION puis MIC standard
        val sourcesToTry = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
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
                recorder.setAudioChannels(1) // Mono optimisé pour la voix
                recorder.setAudioSamplingRate(16000) // 16kHz optimal pour OpenAI Whisper
                recorder.setAudioEncodingBitRate(64000)
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

        // 2. Bascule de repli : AudioRecord vers fichier .wav standard
        return startPcmWavRecording()
    }

    @SuppressLint("MissingPermission")
    private fun startPcmWavRecording(): File? {
        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufSize, 4096)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord n'a pas pu être initialisé")
                return null
            }

            val wavFile = File(context.cacheDir, "speech_rec_${System.currentTimeMillis()}.wav")
            record.startRecording()
            audioRecord = record
            activeAudioFile = wavFile
            isRecording.set(true)

            recordingThread = thread(start = true, name = "AudioRecordWavThread") {
                writeAudioDataToWav(record, wavFile, bufferSize)
            }
            Log.d(TAG, "Enregistrement AudioRecord WAV démarré")
            return wavFile
        } catch (e: Exception) {
            Log.e(TAG, "Erreur fatale initialisation AudioRecord: ${e.message}", e)
            return null
        }
    }

    private fun writeAudioDataToWav(record: AudioRecord, outputFile: File, bufferSize: Int) {
        val audioData = ShortArray(bufferSize / 2)
        val byteData = ByteArray(bufferSize)
        val fos = FileOutputStream(outputFile)

        // Écriture de l'en-tête WAV factice (44 octets)
        writeWavHeader(fos, 0, 0, SAMPLE_RATE, 1, 16)

        var totalAudioLen = 0L
        try {
            while (isRecording.get()) {
                val readShorts = record.read(audioData, 0, audioData.size)
                if (readShorts > 0) {
                    var maxAmp = 0
                    for (i in 0 until readShorts) {
                        val sample = audioData[i]
                        val absSample = kotlin.math.abs(sample.toInt())
                        if (absSample > maxAmp) maxAmp = absSample

                        byteData[i * 2] = (sample.toInt() and 0xFF).toByte()
                        byteData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                    }
                    currentPcmAmplitude = maxAmp
                    val bytesToWrite = readShorts * 2
                    fos.write(byteData, 0, bytesToWrite)
                    totalAudioLen += bytesToWrite
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

    fun stopRecording(): File? {
        if (!isRecording.get()) return activeAudioFile
        isRecording.set(false)

        val elapsed = System.currentTimeMillis() - startTimeMillis
        if (elapsed < 500) {
            try { Thread.sleep(500 - elapsed) } catch (_: Exception) {}
        }

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

        audioRecord?.let { ar ->
            try {
                ar.stop()
            } catch (_: Exception) {}
            try {
                ar.release()
            } catch (_: Exception) {}
            audioRecord = null
        }

        try {
            recordingThread?.join(1000)
        } catch (_: Exception) {}
        recordingThread = null

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
