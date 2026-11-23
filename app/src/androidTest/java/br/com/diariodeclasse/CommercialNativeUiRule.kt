package br.com.diariodeclasse

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Normal Activity/lifecycle/Room dispatchers. No Compose test clock or app content replacement.
 * Uses the official Accessibility extra-data testTag, never a DAO or auth/engine shortcut.
 * All deadlines are the existing probes' deadlines; polling waits for explicit state.
 */
class CommercialNativeUiRule:TestRule {
    private val launch=ActivityScenarioRule(MainActivity::class.java)
    private val instrument get()=InstrumentationRegistry.getInstrumentation()
    private val automation get()=instrument.uiAutomation
    val activity:MainActivity get(){lateinit var value:MainActivity;launch.scenario.onActivity{value=it};return value}
    override fun apply(base:Statement,description:Description)=object:Statement(){
        override fun evaluate(){
            val previous=automation.serviceInfo
            automation.serviceInfo=automation.serviceInfo.apply{flags=flags or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS}
            try{launch.apply(base,description).evaluate()}finally{automation.serviceInfo=previous}
        }
    }
    fun waitUntil(timeout:Long,condition:()->Boolean){
        val deadline=SystemClock.elapsedRealtime()+timeout
        while(!condition()){
            check(SystemClock.elapsedRealtime()<deadline){"Native UI state deadline exceeded"}
            SystemClock.sleep(10) // Bounded state polling, not a stabilization delay.
        }
    }
    var refreshedStructureChanges=0; private set
    var rejectedStaleNodes=0; private set
    private fun nodes(predicate:(AccessibilityNodeInfo)->Boolean):List<AccessibilityNodeInfo>{
        val result=mutableListOf<AccessibilityNodeInfo>()
        val roots=automation.windows.mapNotNull{it.root}.ifEmpty{listOfNotNull(automation.rootInActiveWindow)}
        fun visit(n:AccessibilityNodeInfo){
            // Virtual descendants may be re-created after logout. Refresh the child
            // list before traversing it; cached ancestors can hide the new login tree.
            val before=n.childCount
            if(!n.refresh()){rejectedStaleNodes++;n.recycle();return}
            if(before!=n.childCount)refreshedStructureChanges++
            if(n.packageName==BuildConfig.APPLICATION_ID&&predicate(n))result+=AccessibilityNodeInfo.obtain(n)
            for(i in 0 until n.childCount)n.getChild(i)?.let(::visit)
            n.recycle()
        }
        roots.forEach(::visit);return result
    }
    private fun tags(tag:String)=nodes{n->
        val key="androidx.compose.ui.semantics.testTag"
        // Android adds the requested-key argument; Bundle.EMPTY is immutable.
        val current=key !in n.availableExtraData||n.refreshWithExtraData(key,Bundle())
        current&&(n.viewIdResourceName==tag||n.extras.getCharSequence(key)?.toString()==tag)
    }
    private fun <T> inspect(tag:String,block:(AccessibilityNodeInfo)->T):T{
        val found=tags(tag)
        try{assertEquals("Expected exactly one UI tag: $tag",1,found.size);return block(found.single())}
        finally{found.forEach{it.recycle()}}
    }
    fun exists(tag:String):Boolean{val found=tags(tag);return try{assertTrue("Duplicate UI tag: $tag",found.size<=1);found.isNotEmpty()}finally{found.forEach{it.recycle()}}}
    fun textExists(text:String):Boolean{val found=nodes{it.text?.toString()==text||it.contentDescription?.toString()==text};return try{found.isNotEmpty()}finally{found.forEach{it.recycle()}}}
    fun enabled(tag:String)=inspect(tag){it.isEnabled}
    fun clickable(tag:String)=inspect(tag){it.isClickable||it.actionList.any{a->a.id==AccessibilityNodeInfo.ACTION_CLICK}}
    fun visible(tag:String)=inspect(tag){val r=Rect();it.getBoundsInScreen(r);it.isVisibleToUser&&!r.isEmpty}
    fun assertEnabled(tag:String){assertTrue("UI control must be enabled: $tag",enabled(tag))}
    fun assertDisabled(tag:String){assertFalse("UI control must be disabled: $tag",enabled(tag))}
    fun click(tag:String){
        assertEnabled(tag);assertTrue("UI control must be visible: $tag",visible(tag));assertTrue(clickable(tag))
        inspect(tag){assertTrue("UI click refused: $tag",it.performAction(AccessibilityNodeInfo.ACTION_CLICK))}
    }
    fun replaceText(tag:String,value:String){
        assertEnabled(tag)
        inspect(tag){n->assertTrue("Expected editable UI: $tag",n.isEditable)
            assertTrue("UI text input refused: $tag",n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}))}
    }
    /** Scroll only the visible dialog container until the requested control is actually visible. */
    fun scrollTo(tag:String,timeout:Long=15000){
        val deadline=SystemClock.elapsedRealtime()+timeout
        while(true){
            if(exists(tag)&&visible(tag))return
            check(SystemClock.elapsedRealtime()<deadline){"UI scroll target not reachable: $tag"}
            val found=tags(tag)
            val shown=try{found.singleOrNull()?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)==true}finally{found.forEach{it.recycle()}}
            if(!shown){
                val scrolls=nodes{it.isScrollable&&it.isVisibleToUser}
                try{scrolls.any{it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)}}
                finally{scrolls.forEach{it.recycle()}}
            }
            instrument.waitForIdleSync()
            SystemClock.sleep(10)
        }
    }
    /** Shape and known tags only: never field values, identities or session contents. */
    fun diagnostic():String {
        val known=listOf("entry-email","entry-password","entry-enter","nav-0","grade-conflict-dialog")
        val present=known.associateWith{exists(it)}
        return "tags=$present refreshed_structure_changes=$refreshedStructureChanges stale_nodes=$rejectedStaleNodes"
    }
    fun waitForIdle(){instrument.waitForIdleSync()}
}
