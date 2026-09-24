package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

class WebAppInterface(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun triggerGoalVibration() {
        // Pattern sama seperti di website turnamen:
        // 3 detik getar, 400ms jeda, 3 detik getar
        vibratePattern(longArrayOf(0, 3000, 400, 3000))
    }

    @JavascriptInterface
    fun vibrate(patternJson: String?) {
        try {
            if (patternJson.isNullOrBlank()) {
                vibratePattern(longArrayOf(0, 3000, 400, 3000))
                return
            }
            val clean = patternJson.trim()
            val timings = if (clean.startsWith("[")) {
                val array = JSONArray(clean)
                val list = mutableListOf<Long>()
                list.add(0L) // initial delay
                for (i in 0 until array.length()) {
                    list.add(array.getLong(i))
                }
                list.toLongArray()
            } else {
                val ms = clean.toLongOrNull() ?: 500L
                longArrayOf(0, ms)
            }
            vibratePattern(timings)
        } catch (_: Exception) {
            vibratePattern(longArrayOf(0, 3000, 400, 3000))
        }
    }

    @JavascriptInterface
    fun shareFile(
        base64Data: String?,
        fileNameParam: String?,
        mimeTypeParam: String?,
        titleParam: String?,
        textParam: String?
    ) {
        if (base64Data.isNullOrBlank()) return

        mainHandler.post {
            try {
                val cleanBase64 = if (base64Data.contains(",")) {
                    base64Data.substringAfter(",")
                } else {
                    base64Data
                }
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

                val fileName = if (!fileNameParam.isNullOrBlank()) {
                    fileNameParam.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                } else {
                    "hasil-pertandingan.png"
                }

                val cacheDir = File(context.cacheDir, "shared_images").apply {
                    if (!exists()) mkdirs()
                }
                val imageFile = File(cacheDir, fileName)
                FileOutputStream(imageFile).use { it.write(bytes) }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )

                val mimeType = if (!mimeTypeParam.isNullOrBlank()) mimeTypeParam else "image/png"
                val shareTitle = titleParam ?: "Hasil Pertandingan"
                val shareText = textParam ?: ""

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    if (shareTitle.isNotBlank()) {
                        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                    }
                    if (shareText.isNotBlank()) {
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Bagikan Hasil Pertandingan").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Gagal membagikan hasil", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun shareText(titleParam: String?, textParam: String?, urlParam: String?) {
        mainHandler.post {
            try {
                val shareTitle = titleParam ?: "Hasil Pertandingan"
                val combinedText = buildString {
                    if (!textParam.isNullOrBlank()) append(textParam)
                    if (!urlParam.isNullOrBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(urlParam)
                    }
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    if (shareTitle.isNotBlank()) {
                        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                    }
                    putExtra(Intent.EXTRA_TEXT, combinedText)
                }

                val chooser = Intent.createChooser(shareIntent, "Bagikan Hasil Pertandingan").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun vibratePattern(timings: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
