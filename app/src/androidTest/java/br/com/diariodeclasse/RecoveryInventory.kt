package br.com.diariodeclasse

import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.encodeToString

internal fun recoveryNeed(value:Boolean,code:String) {check(value){"RECOVERY_"+code}}
internal fun recoveryEligible(s:AcademicProtocolStatus) {
    recoveryNeed(s.protocol==2&&s.academicScope==5&&s.testAllowed,"SCOPE_NOT_READY")
    recoveryNeed(s.academicSequence in 1..256,"LIMIT")
}
internal data class H05Inventory(val versions:Map<AcademicKey,AcademicVersion>,val pages:List<AcademicPage>,val head:Long) {
    fun rows()=versions.values.filter{it.payload!=null}.map { val c=PhaseBGuard.normalized(it.change());AcademicRow(c.kind,c.key,c.payload!!) }
}

internal suspend fun recoveryInventory(remote:H05ReadOnlyRemote,token:String):H05Inventory {
            val status=remote.status(token);recoveryEligible(status)
            var cursor=0L;var calls=0;var bytes=0L;var received=0
            val versions=linkedMapOf<AcademicKey,AcademicVersion>();val pages=mutableListOf<AcademicPage>()
            while(cursor<status.academicSequence) {
                var offset=0;var operation="";var total=0;val records=mutableListOf<AcademicVersion>()
                do {
                    recoveryNeed(++calls<=128,"LIMIT")
                    val list=remote.pullE(token,cursor,offset);recoveryNeed(list.size==1,"REMOTE_CHANGED")
                    val f=list.single();f.validate();recoveryNeed(f.sequence==cursor+1&&f.offset==offset,"INVALID_DATA")
                    if(offset==0){operation=f.operation;total=f.total;recoveryNeed(total<=1000,"LIMIT")}
                    recoveryNeed(f.operation==operation&&f.total==total,"INVALID_DATA")
                    received+=f.records.size;recoveryNeed(received<=4096,"LIMIT")
                    bytes+=AcademicRecords.json.encodeToString(f).toByteArray().size;recoveryNeed(bytes<=8*1024*1024,"LIMIT")
                    records+=f.records;offset+=f.records.size
                }while(offset<total)
                recoveryNeed(offset==total,"INVALID_DATA")
                val page=AcademicPage(operation,cursor+1,records);page.validate(AcademicPaging.MAX_RECORDS)
                for(v in records){recoveryNeed(v.revision==(versions[v.identity]?.revision ?: 0L)+1,"INVALID_DATA");versions[v.identity]=v}
                pages+=page;cursor++
            }
            recoveryNeed(remote.pullE(token,cursor,0).isEmpty(),"REMOTE_CHANGED")
            val after=remote.status(token);recoveryEligible(after);recoveryNeed(after==status,"REMOTE_CHANGED")
            recoveryNeed(versions.values.count{it.payload!=null}<=1000,"LIMIT")
            return H05Inventory(versions,pages,cursor).also { AcademicRecords.snapshot(it.rows()) }
        }
