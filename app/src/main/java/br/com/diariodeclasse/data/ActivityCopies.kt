package br.com.diariodeclasse.data

import br.com.diariodeclasse.domain.RosterParser
import java.time.LocalDate
import java.util.UUID

enum class ActivityScope(val label:String) {
    CURRENT("Turma atual"), ALL("Todas as compatíveis do mesmo ano"), CUSTOM("Selecionar turmas")
}
data class ActivityCreation(val source:SchoolClass,val template:Activity,val scope:ActivityScope,
    val selected:Set<String>,val dates:Map<String,String>,val targets:List<SchoolClass>,val periods:List<Term>,val activities:List<Activity>)

/** Planning is read-only. Repository revalidates the full selection in one Room transaction. */
object ActivityCopies {
    fun compatible(source:SchoolClass,target:SchoolClass)=source.stage==target.stage &&
        RosterParser.key(source.subject)==RosterParser.key(target.subject)
    fun candidates(data:DiarySnapshot,source:SchoolClass)=data.classes.filter {
        compatible(source,it) && data.classArchives.none {a->a.classId==it.id}
    }.sortedWith(compareBy<SchoolClass>{it.name}.thenBy{it.id})
    fun plan(data:DiarySnapshot,sourceId:String,template:Activity,scope:ActivityScope,selected:Set<String>,dates:Map<String,String> = emptyMap()):ActivityCreation {
        val source=requireNotNull(data.classes.singleOrNull{it.id==sourceId}) {"Turma de origem não encontrada."}
        require(template.classId==sourceId && template.name.isNotBlank() && template.type in activityTypes && template.maximum.isFinite() && template.maximum>0 && template.term in 1..4) {"Confira nome, tipo, pontuação e bimestre."}
        require(LocalDate.parse(template.date).toString()==template.date && LocalDate.parse(template.date).year==source.year) {"A data deve pertencer ao ano letivo da turma."}
        val ids=when(scope){ActivityScope.CURRENT->setOf(sourceId);ActivityScope.ALL->candidates(data,source).filter{it.year==source.year}.map{it.id}.toSet();ActivityScope.CUSTOM->selected}
        require(ids.isNotEmpty()) {"Selecione pelo menos uma turma."}
        val targets=ids.sorted().map { id ->
            val target=requireNotNull(data.classes.singleOrNull{it.id==id}) {"Uma turma selecionada não está mais disponível."}
            require(compatible(source,target)) {"${target.name}: etapa ou disciplina incompatível."}
            require(data.classArchives.none{it.classId==id}) {"${target.name}: turma arquivada."}
            val period=requireNotNull(data.terms.singleOrNull{it.classId==id&&it.number==template.term}) {"${target.name}: bimestre não encontrado."}
            val day=if(target.year==source.year)template.date else requireNotNull(dates[id]) {"${target.name}: informe uma data para o ano letivo ${target.year}."}
            require(LocalDate.parse(day).toString()==day && LocalDate.parse(day).year==target.year) {"${target.name}: data fora do ano letivo."}
            require(day in period.start..period.end) {"${target.name}: a data não pertence ao bimestre selecionado."}
            target
        }
        val copies=targets.map { target -> template.copy(classId=target.id,date=if(target.year==source.year)template.date else dates.getValue(target.id),id=if(target.id==sourceId)template.id else UUID.nameUUIDFromBytes("noctua-activity:${template.id}:${target.id}".toByteArray(Charsets.UTF_8)).toString()) }
        return ActivityCreation(source,template,scope,selected.toSet(),dates.toMap(),targets,data.terms.filter{it.classId in ids}.sortedWith(compareBy<Term>{it.classId}.thenBy{it.number}),copies)
    }
    fun duplicate(a:Activity,b:Activity)=a.classId==b.classId&&RosterParser.key(a.name)==RosterParser.key(b.name)&&a.type==b.type&&a.date==b.date&&a.term==b.term
}

enum class GradeRosterOrder(val label:String){NUMBER("Nº da chamada"),NAME("Nome A–Z")}
object GradeRoster {
    fun select(students:List<Student>,query:String,order:GradeRosterOrder):List<Student> {
        val key=RosterParser.key(query.trim());val number=query.trim().toIntOrNull()
        return students.filter{key.isEmpty()||if(number!=null)it.number==number else RosterParser.key(it.name).contains(key)}
            .sortedWith(if(order==GradeRosterOrder.NUMBER)compareBy<Student>{it.number}.thenBy{RosterParser.key(it.name)}.thenBy{it.id}
                else compareBy<Student>{RosterParser.key(it.name)}.thenBy{it.number}.thenBy{it.id})
    }
}
