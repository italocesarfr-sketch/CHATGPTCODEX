package br.com.diariodeclasse.ocr

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.diariodeclasse.DiaryApplication
import br.com.diariodeclasse.domain.RosterParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.File

class PhotoViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as DiaryApplication
    private val repository = app.repository
    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap = _bitmap.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _status = MutableStateFlow("")
    val status = _status.asStateFlow()
    private val folder = File(app.cacheDir, "photos").apply { mkdirs() }
    init {
        saved.get<String>("imagePath")?.let { path ->
            val file = File(path)
            if (file.exists() && file.parentFile == folder) load(file)
        }
    }
    fun cameraUri(): Uri {
        val file = File.createTempFile("camera-", ".jpg", folder)
        saved["cameraPath"] = file.absolutePath
        return FileProvider.getUriForFile(app, "${app.packageName}.photos", file)
    }
    fun cameraResult(success: Boolean) {
        val path = saved.get<String>("cameraPath") ?: return
        saved.remove<String>("cameraPath")
        val file = File(path)
        if (success && file.length() > 0) load(file) else file.delete()
    }
    fun select(uri: Uri) {
        if (_busy.value) return
        _busy.value = true; _status.value = "Abrindo fotografia…"
        viewModelScope.launch {
            var file: File? = null
            try {
                file = withContext(Dispatchers.IO) {
                    val dest = File.createTempFile("gallery-", ".jpg", folder)
                    try {
                        requireNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                            dest.outputStream().use { out ->
                                val buffer = ByteArray(8192); var total = 0L
                                while (true) {
                                    val size = input.read(buffer); if (size < 0) break
                                    total += size; require(total <= 30L * 1024 * 1024) { "Use uma fotografia de até 30 MB." }
                                    out.write(buffer, 0, size)
                                }
                            }
                        }
                        dest
                    } catch (error: Exception) { dest.delete(); throw error }
                }
                decodeAndShow(file)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { file?.delete(); _status.value = "Não foi possível abrir a imagem. Use JPEG, PNG ou WebP de até 30 MB." }
            finally { _busy.value = false }
        }
    }
    private fun load(file: File) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { decodeAndShow(file) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { file.delete(); _status.value = "Fotografia indisponível. Selecione ou fotografe novamente." }
            finally { _busy.value = false }
        }
    }
    private suspend fun decodeAndShow(file: File) {
        val decoded = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            require(bounds.outWidth in 1..30000 && bounds.outHeight in 1..30000) { "Imagem inválida." }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
            val original = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
            val matrix = Matrix()
            val orientation = ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true).also { if (it !== original) original.recycle() }
        }
        saved.get<String>("imagePath")?.takeIf { it != file.path }?.let { File(it).delete() }
        saved["imagePath"] = file.path
        _bitmap.value = decoded
        _status.value = "Selecione a região com números e nomes. Exclua os cabeçalhos e as colunas de frequência."
    }
    fun rotate() {
        if (_busy.value) return
        val original = _bitmap.value ?: return
        _bitmap.value = Bitmap.createBitmap(original, 0, 0, original.width, original.height, Matrix().apply { postRotate(90f) }, true)
    }
    fun discardImage() {
        _bitmap.value = null
        saved.remove<String>("imagePath")?.let { File(it).delete() }
        _status.value = ""
    }
    fun recognize(classId: String, left: Float, top: Float, right: Float, bottom: Float) {
        if (_busy.value) return
        val source = _bitmap.value ?: return
        _busy.value = true; _status.value = "Reconhecendo no próprio aparelho…"
        viewModelScope.launch {
            try {
                val candidates = withContext(Dispatchers.Default) {
                    val x = (left.coerceIn(0f, .99f) * source.width).toInt()
                    val y = (top.coerceIn(0f, .99f) * source.height).toInt()
                    val width = ((right.coerceIn(0f, 1f) * source.width).toInt() - x).coerceIn(1, source.width - x)
                    val height = ((bottom.coerceIn(0f, 1f) * source.height).toInt() - y).coerceIn(1, source.height - y)
                    require(width >= 30 && height >= 20) { "Amplie a região selecionada." }
                    val crop = Bitmap.createBitmap(source, x, y, width, height)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        val result = recognizer.process(InputImage.fromBitmap(crop, 0)).await()
                        val words = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
                            element.boundingBox?.let { box -> RosterParser.Word(element.text, box.left, box.top, box.right, box.bottom) }
                        }
                        RosterParser.parse(words)
                    } finally { recognizer.close(); if (crop !== source) crop.recycle() }
                }
                if (candidates.isEmpty()) {
                    _status.value = "Nenhum nome reconhecido. Ajuste o recorte, melhore a iluminação ou adicione os nomes manualmente."
                } else {
                    repository.appendDrafts(classId, candidates)
                    discardImage()
                    _status.value = "${candidates.size} nomes adicionados ao rascunho. Confira cada nome antes de confirmar."
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _status.value = "Não foi possível reconhecer. Ajuste o recorte e tente novamente; o rascunho foi preservado." }
            finally { _busy.value = false }
        }
    }
}
