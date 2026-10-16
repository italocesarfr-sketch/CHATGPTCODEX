package br.com.diariodeclasse

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.diariodeclasse.domain.RosterParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineOcrInstrumentedTest {
    @Test fun bundledModelRecognizesSyntheticRosterWithoutNetworkPermission() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        val bitmap = Bitmap.createBitmap(1600, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f; typeface = Typeface.DEFAULT }
        canvas.drawText("ESCOLA FICTICIA - LISTA DE CHAMADA", 40f, 80f, paint)
        canvas.drawText("01 ESTUDANTE FICTICIO ALFA", 40f, 200f, paint)
        canvas.drawText("02 ESTUDANTE FICTICIO BETA", 40f, 320f, paint)
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = client.process(InputImage.fromBitmap(bitmap, 0)).await()
            val words = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
                element.boundingBox?.let { RosterParser.Word(element.text, it.left, it.top, it.right, it.bottom) }
            }
            val candidates = RosterParser.parse(words)
            assertEquals(2, candidates.size)
            assertEquals("estudanteficticioalfa", RosterParser.key(candidates[0].name))
            assertEquals(1, candidates[0].number?.toInt() ?: -1)
            assertEquals(2, candidates[1].number?.toInt() ?: -1)
        } finally { client.close(); bitmap.recycle() }
    }
}
