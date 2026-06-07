package com.alumanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Reconnaissance faciale embarquée : détection (ML Kit) + embedding (TFLite).
 * Le modèle est chargé depuis assets (facenet.tflite / mobile_face_net.tflite).
 * S'adapte automatiquement à la taille d'entrée et à la dimension de sortie du modèle.
 */
object FaceRecognizer {

    private var interpreter: Interpreter? = null
    private var inputSize = 112
    private var embSize = 192
    private var triedLoad = false
    var loaded = false; private set

    private val MODEL_NAMES = listOf(
        "facenet.tflite", "mobile_face_net.tflite", "mobilefacenet.tflite", "face.tflite"
    )

    @Synchronized
    private fun ensureLoaded(ctx: Context) {
        if (triedLoad) return
        triedLoad = true
        for (name in MODEL_NAMES) {
            try {
                val afd = ctx.assets.openFd(name)
                FileInputStream(afd.fileDescriptor).use { fis ->
                    val mbb = fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                    val itp = Interpreter(mbb, Interpreter.Options())
                    val inShape = itp.getInputTensor(0).shape()   // [1,H,W,3]
                    inputSize = if (inShape.size >= 3) inShape[1] else 112
                    val outShape = itp.getOutputTensor(0).shape() // [1,emb]
                    embSize = outShape[outShape.size - 1]
                    interpreter = itp
                    loaded = true
                }
                break
            } catch (e: Exception) { /* essayer le nom suivant */ }
        }
    }

    fun available(ctx: Context): Boolean { ensureLoaded(ctx); return loaded }

    /** Détecte le plus grand visage et renvoie son embedding (callback sur le thread principal). */
    fun embed(ctx: Context, bmp: Bitmap, onResult: (FloatArray?) -> Unit) {
        ensureLoaded(ctx)
        if (!loaded) { onResult(null); return }
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        val detector = FaceDetection.getClient(opts)
        detector.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face == null) { onResult(null); return@addOnSuccessListener }
                try { onResult(runModel(cropFace(bmp, face.boundingBox))) }
                catch (e: Exception) { onResult(null) }
            }
            .addOnFailureListener { onResult(null) }
    }

    private fun cropFace(bmp: Bitmap, box: Rect): Bitmap {
        val m = (box.width() * 0.15f).toInt()
        val l = (box.left - m).coerceAtLeast(0)
        val t = (box.top - m).coerceAtLeast(0)
        val r = (box.right + m).coerceAtMost(bmp.width)
        val b = (box.bottom + m).coerceAtMost(bmp.height)
        val w = (r - l).coerceAtLeast(1); val h = (b - t).coerceAtLeast(1)
        return Bitmap.createBitmap(bmp, l, t, w, h)
    }

    private fun runModel(face: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(face, inputSize, inputSize, true)
        val input = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(3) } } }
        val px = IntArray(inputSize * inputSize)
        resized.getPixels(px, 0, inputSize, 0, 0, inputSize, inputSize)
        var i = 0
        for (y in 0 until inputSize) for (x in 0 until inputSize) {
            val p = px[i++]
            input[0][y][x][0] = (((p shr 16) and 0xFF) - 127.5f) / 128f
            input[0][y][x][1] = (((p shr 8) and 0xFF) - 127.5f) / 128f
            input[0][y][x][2] = ((p and 0xFF) - 127.5f) / 128f
        }
        val out = Array(1) { FloatArray(embSize) }
        interpreter!!.run(input, out)
        return l2norm(out[0])
    }

    private fun l2norm(v: FloatArray): FloatArray {
        var s = 0f; for (x in v) s += x * x
        val n = sqrt(s.toDouble()).toFloat().coerceAtLeast(1e-9f)
        return FloatArray(v.size) { v[it] / n }
    }

    /** Similarité cosinus entre deux embeddings L2-normalisés (= produit scalaire). */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        var s = 0f; for (i in a.indices) s += a[i] * b[i]
        return s
    }
}
