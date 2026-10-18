package br.com.diariodeclasse

import android.content.Context
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Verifica os recursos instalados e a UI real do Android; nunca altera dados escolares. */
class OfficialBrandInstrumentedTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val automation get()=instrumentation.uiAutomation
    private fun save(name:String,bitmap:Bitmap) {
        val folder=File(context.filesDir,"brand-screenshots");folder.mkdirs()
        File(folder,"$name.png").outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
    private fun launcherIcon():AdaptiveIconDrawable {
        val app=context.packageManager.getApplicationInfo(context.packageName,0)
        assertTrue("O sistema pode preferir o ícone padrão ou o redondo",app.icon in setOf(R.mipmap.ic_launcher,R.mipmap.ic_launcher_round))
        val actual=context.packageManager.getApplicationIcon(app)
        assertTrue("O Android deve carregar o ícone adaptativo oficial",actual is AdaptiveIconDrawable)
        return actual as AdaptiveIconDrawable
    }
    @Test fun packagedOriginalHasExactlyTheOwnersPixelsAndManifestUsesAdaptiveIcons() {
        val image=BitmapFactory.decodeResource(context.resources,R.drawable.noctua_logo,BitmapFactory.Options().apply {inScaled=false})
        assertEquals(1254,image.width);assertEquals(1254,image.height)
        val hash=MessageDigest.getInstance("SHA-256");val pixels=IntArray(image.width);val rgb=ByteArray(image.width*3)
        for(y in 0 until image.height) {
            image.getPixels(pixels,0,image.width,0,y,image.width,1)
            for(x in pixels.indices) {rgb[x*3]=Color.red(pixels[x]).toByte();rgb[x*3+1]=Color.green(pixels[x]).toByte();rgb[x*3+2]=Color.blue(pixels[x]).toByte()}
            hash.update(rgb)
        }
        assertEquals("7f9f46c80d1f0118b425722b23998cdab0f39dc36f87a396b100a9a91278cf0a",hash.digest().joinToString("") {"%02x".format(it)})
        image.recycle()
        val icon=launcherIcon();assertTrue(icon.foreground is InsetDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)
        val launchers=context.getSystemService(LauncherApps::class.java).getActivityList(context.packageName,Process.myUserHandle())
        assertEquals(1,launchers.size);assertTrue(launchers.single().getIcon(0) is AdaptiveIconDrawable)
        val size=432;icon.setBounds(0,0,size,size)
        val rendered=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);icon.draw(Canvas(rendered))
        val region=Region().apply {setPath(icon.iconMask,Region(0,0,size,size))}
        val bounds=(icon.foreground as InsetDrawable).drawable!!.bounds
        assertEquals(bounds.width(),bounds.height())
        // Pontos extremos da coruja/N e centros dos olhos na imagem original.
        for((x,y) in listOf(250 to 175,1002 to 175,307 to 1050,469 to 1058,942 to 1054,627 to 638,490 to 480,765 to 480)) {
            val px=bounds.left+x*bounds.width()/1254;val py=bounds.top+y*bounds.height()/1254
            assertTrue("A máscara não pode cortar a coruja/N ($x,$y)",region.contains(px,py))
        }
        rendered.recycle()
        saveMaskPreview()
    }
    private fun saveMaskPreview() {
        val width=800;val height=480;val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=20f}
        for(row in 0..1) {
            paint.color=if(row==0)Color.rgb(245,246,249)else Color.rgb(11,18,32)
            canvas.drawRect(0f,row*240f,width.toFloat(),(row+1)*240f,paint)
            for(col in 0..3) {
                val x=col*200f+28f;val y=row*240f+30f
                val path=Path().apply {when(col) {
                    0->addCircle(72f,72f,72f,Path.Direction.CW)
                    1->addRect(0f,0f,144f,144f,Path.Direction.CW)
                    2->addRoundRect(0f,0f,144f,144f,36f,36f,Path.Direction.CW)
                    else->addRoundRect(RectF(0f,0f,144f,144f),floatArrayOf(72f,72f,72f,72f,16f,16f,72f,72f),Path.Direction.CW)
                }}
                val icon=launcherIcon();icon.setBounds(0,0,144,144)
                canvas.save();canvas.translate(x,y);canvas.clipPath(path)
                icon.background.draw(canvas);icon.foreground.draw(canvas);canvas.restore()
                paint.color=if(row==0)Color.rgb(11,18,32)else Color.WHITE
                canvas.drawText(listOf("Círculo","Quadrado","Arredondado","Gota")[col],col*200f+20f,row*240f+208f,paint)
            }
        }
        save("01-mascaras-claro-escuro",bitmap);bitmap.recycle()
    }
    private fun shell(command:String) {
        automation.executeShellCommand(command).use {descriptor ->FileInputStream(descriptor.fileDescriptor).use {it.readBytes()}}
    }
    private fun waitNode(message:String,find:()->AccessibilityNodeInfo?):AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+12000
        while(SystemClock.uptimeMillis()<deadline) {find()?.let {return it};SystemClock.sleep(80)}
        throw AssertionError(message)
    }
    private fun label(root:AccessibilityNodeInfo?)=root?.findAccessibilityNodeInfosByText("Noctua")?.firstOrNull {it.isVisibleToUser && (it.text?.toString()=="Noctua" || it.contentDescription?.toString()=="Noctua")}
    @Test fun launcherHomeAndAndroidAppSettingsDisplayTheOfficialInstalledIcon() {
        assumeTrue("Automação do launcher somente no emulador descartável",Build.HARDWARE in setOf("ranchu","goldfish"))
        automation.serviceInfo=automation.serviceInfo.apply {flags=flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS}
        try {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitNode("O launcher Android não abriu") {automation.rootInActiveWindow?.takeIf {it.packageName.toString().contains("launcher")}}
            val screen=automation.takeScreenshot()!!;val width=screen.width;val height=screen.height;screen.recycle()
            shell("input swipe ${width/2} ${height*9/10} ${width/2} ${height/5} 450")
            val icon=waitNode("Noctua não apareceu na lista de apps do launcher") {label(automation.rootInActiveWindow)}
            save("02-launcher-lista-apps",automation.takeScreenshot()!!)
            val bounds=Rect();icon.getBoundsInScreen(bounds)
            shell("input draganddrop ${bounds.centerX()} ${bounds.centerY()} ${width*3/4} ${height*45/100} 1400")
            waitNode("O ícone do Noctua deve estar na tela inicial") {
                val root=automation.rootInActiveWindow
                val workspace=root?.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/workspace")?.firstOrNull()
                    ?:root?.findAccessibilityNodeInfosByViewId("com.google.android.apps.nexuslauncher:id/workspace")?.firstOrNull()
                label(workspace)
            }
            save("03-launcher-tela-inicial",automation.takeScreenshot()!!)
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitNode("As configurações do Android devem identificar Noctua") {
                automation.rootInActiveWindow?.takeIf {it.packageName.toString()=="com.android.settings"}?.let(::label)
            }
            save("04-configuracoes-android",automation.takeScreenshot()!!)
        } finally {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
        }
    }
}
