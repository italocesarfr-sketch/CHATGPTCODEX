package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.json.*

internal class BTransitionBlocked(val code:String):Exception("BT_"+code)
internal fun btNeed(ok:Boolean,code:String){if(!ok)throw BTransitionBlocked(code)}

/** Preparation ONLY. No hosted binding or automatic consent is exposed by this helper. */
internal class BTransitionRemote(private val source:AcademicRemote,private val bToken:String):AcademicRemote {
    init{check(BuildConfig.DEBUG&&!BuildConfig.HOMOLOGACAO)}
    var reads=0;private set
    var academicWrites=0;private set
    val scopes=mutableListOf<Int>()
    private var permit:Pair<Int,Long>?=null
    private fun identity(token:String){btNeed(token==bToken,"OWNER")}
    private fun read(token:String){identity(token);btNeed(reads<256,"LIMIT");reads++}
    private fun deny():Nothing{academicWrites++;throw BTransitionBlocked("WRITE_BLOCKED")}
    override suspend fun status(token:String):AcademicProtocolStatus {read(token);return source.status(token)}
    override suspend fun pull(token:String,after:Long):List<AcademicPage>{read(token);return source.pull(token,after)}
    override suspend fun pullC(token:String,after:Long):List<AcademicPage>{read(token);return source.pullC(token,after)}
    override suspend fun pullD(token:String,after:Long):List<AcademicPage>{read(token);return source.pullD(token,after)}
    override suspend fun pullE(token:String,after:Long,offset:Int):List<AcademicFragment>{read(token);return source.pullE(token,after,offset)}
    suspend fun arm(db:DiaryDatabase,owner:String,expectedB:String,target:Int,consent:Boolean) {
        permit=null // A failed review never retains a prior permission.
        btNeed(consent,"CONSENT");btNeed(owner==expectedB,"OWNER");btNeed(target in 3..5,"ORDER")
        val s=status(bToken);btNeed(s.protocol==2&&s.testAllowed,"ELIGIBILITY")
        val dao=db.academicDao();val c=dao.control();btNeed(c!=null&&c.owner==owner,"OWNER")
        val active=mapOf(3 to "ACTIVE",4 to "ACTIVE_C",5 to "ACTIVE_D").getValue(target)
        val prepared=mapOf(3 to "PREPARED_C",4 to "PREPARED_D",5 to "PREPARED_E").getValue(target)
        btNeed(c!!.stage in setOf(active,prepared),"LOCAL_STAGE")
        btNeed(s.academicScope==target-1 || s.academicScope==target&&c.stage==prepared,"REMOTE_SCOPE")
        btNeed(c.message in setOf("","PENDING")&&c.stage!="RESTORE","CONFLICT_OR_RESTORE")
        btNeed(dao.pendingHeaders().isEmpty()&&dao.incomingHeaders().isEmpty()&&dao.stagedParts().isEmpty()&&dao.decisionHeaders().isEmpty(),"QUEUES")
        btNeed(db.syncDao().operations().isEmpty()&&db.syncDao().states().none{it.conflict!=null}&&db.syncDao().control()?.enabled!=true,"V1")
        btNeed(c.cursor==s.academicSequence,"CURSOR")
        val states=dao.states();btNeed(states.all{it.owner==owner},"OWNER")
        btNeed(states.all{it.localVersion==it.acknowledged&&it.revision>0},"UNACKNOWLEDGED")
        val rows=AcademicRecords.rows(ClassLifecycle.snapshot(DiaryRepository(db).readSnapshot()).canonical())
        val bases=states.mapNotNull { r->r.base?.let{AcademicRow(r.kind,r.identity().key,AcademicRecords.json.parseToJsonElement(it).jsonObject)}}
        btNeed(rows.associateBy{it.identity}==bases.associateBy{it.identity},"UNSYNCED_LOCAL")
        btNeed(c.cursor in 0..64&&states.size<=256&&rows.size<=256,"LIMIT")
        val pages=if(s.academicScope==5)p1Inventory(H05ReadOnlyRemote(this),bToken).pages else {
            val result=mutableListOf<AcademicPage>()
            for(seq in 1..c.cursor) {
                val batch=when(s.academicScope){2->pull(bToken,seq-1);3->pullC(bToken,seq-1);else->pullD(bToken,seq-1)}
                btNeed(batch.size==1&&batch.single().sequence==seq,"RECEIPTS");result+=batch.single()
            };result
        }
        val latest=linkedMapOf<AcademicKey,AcademicVersion>()
        for(page in pages) {
            page.validate();val received=dao.applied(page.sequence)
            val digest=br.com.diariodeclasse.backup.BackupCodec.hash(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(AcademicPage.serializer(),page)).toByteArray())
            btNeed(received?.owner==owner&&received.operation==page.operation&&received.digest==digest,"RECEIPTS")
            for(record in page.records){btNeed(record.revision==(latest[record.identity]?.revision ?: 0L)+1,"REVISION");latest[record.identity]=record}
        }
        btNeed(latest.keys==states.map{it.identity()}.toSet(),"REVISION")
        for(record in states) {
            val version=latest.getValue(record.identity())
            btNeed(version.revision==record.revision&&record.base?.let{AcademicRecords.json.parseToJsonElement(it)}==version.payload?.let{PhaseBGuard.normalized(version.change()).payload},"REVISION")
        }
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{btNeed(!it.moveToFirst(),"REFERENCES")}
        permit=target to s.academicSequence
    }
    fun disarm(){permit=null}
    private suspend fun enable(token:String,target:Int,sequence:Long):AcademicProtocolStatus {
        identity(token);btNeed(permit==(target to sequence),"CONSENT");permit=null
        val before=status(token);btNeed(before.protocol==2&&before.testAllowed&&before.academicScope==target-1&&before.academicSequence==sequence,"STALE")
        btNeed(target !in scopes&&scopes.size<3,"REPEAT")
        scopes+=target // A lost response is never blindly resent by this helper.
        return when(target){3->source.enableC(token,sequence);4->source.enableD(token,sequence);else->source.enableE(token,sequence)}.also {
            btNeed(it.protocol==2&&it.academicScope==target&&it.academicSequence==sequence&&it.testAllowed,"CONFIRMATION")
        }
    }
    override suspend fun enableC(token:String,expectedSequence:Long)=enable(token,3,expectedSequence)
    override suspend fun enableD(token:String,expectedSequence:Long)=enable(token,4,expectedSequence)
    override suspend fun enableE(token:String,expectedSequence:Long)=enable(token,5,expectedSequence)
    override suspend fun activate(token:String,expected:AcademicProtocolStatus):AcademicProtocolStatus=deny()
    override suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply=deny()
    override suspend fun pushC(token:String,batch:AcademicBatch):AcademicPushReply=deny()
    override suspend fun pushD(token:String,batch:AcademicBatch):AcademicPushReply=deny()
    override suspend fun stageE(token:String,batch:AcademicBatch,admitted:()->Boolean):Unit=deny()
    override suspend fun pushE(token:String,batch:AcademicBatch,admitted:()->Boolean):AcademicPushReply=deny()
    override suspend fun resolveE(token:String,operation:String,request:JsonObject,cancel:Boolean):AcademicPushReply=deny()
    override suspend fun decisionE(token:String,operation:String):AcademicPushReply?=throw BTransitionBlocked("DECISION_BLOCKED")
}
