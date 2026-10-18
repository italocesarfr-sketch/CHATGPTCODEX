package br.com.diariodeclasse.export

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.*

/** Uma matriz tipada e imutável alimenta prévia, arquivos e área de transferência. */
enum class ExportModel(val label:String) { NOTES("Notas"), FREQUENCY("Frequência"), CONSOLIDATED("Consolidado") }
enum class ExportFormat(val extension:String,val mime:String) { XLSX("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), CSV("csv","text/csv") }
data class ExportOptions(val model:ExportModel=ExportModel.NOTES,val term:Int?=1,val headers:Boolean=true,val callNumber:Boolean=true)
enum class NumberStyle { DECIMAL, INTEGER, PERCENT }
data class ExportCell(val text:String?=null,val number:Double?=null,val style:NumberStyle=NumberStyle.DECIMAL) {
    init {require(text==null||number==null);require(number==null||number.isFinite())}
    companion object {val EMPTY=ExportCell();fun text(v:String)=ExportCell(text=v);fun number(v:Number?,style:NumberStyle=NumberStyle.DECIMAL)=ExportCell(number=v?.toDouble(),style=style)}
}
data class ExportColumn(val key:String,val label:String,val grade:Boolean=false,val maximum:Double?=null)
data class ExportRecord(val enrollmentId:String?,val cells:List<ExportCell>)
data class ExportTable(val title:String,val options:ExportOptions,val columns:List<ExportColumn>,val records:List<ExportRecord>) {
    fun rows(onlyGrades:Boolean=false,allGrades:Boolean=false):List<List<ExportCell>> {
        val indices=columns.indices.filter{!onlyGrades||(if(allGrades)columns[it].grade else columns[it].key.endsWith(":current"))}
        if(indices.isEmpty())return emptyList()
        val body=records.map{row->indices.map{row.cells[it]}}
        if(onlyGrades||!options.headers)return body
        val titleRow=List(columns.size){if(it==0)ExportCell.text(title)else ExportCell.EMPTY}
        val headings=columns.map{ExportCell.text(it.label)}
        val maxima=columns.mapIndexed{i,col->if(i==0)ExportCell.text("Pontuação máxima")else ExportCell.number(col.maximum)}
        return listOf(titleRow,headings)+(if(columns.any{it.maximum!=null})listOf(maxima)else emptyList())+body
    }
}
object ExportProjection {
    fun build(data:DiarySnapshot,c:SchoolClass,options:ExportOptions):ExportTable {
        require(data.classes.any{it.id==c.id});require(options.term==null||options.term in 1..4)
        val reports=SchoolReports(data,c);val periods=options.term?.let{listOf(it)} ?: (1..4).toList()
        val columns=mutableListOf<ExportColumn>();val values=mutableListOf<(Student)->ExportCell>()
        fun column(key:String,label:String,grade:Boolean=false,max:Double?=null,value:(Student)->ExportCell) {
            columns+=ExportColumn(key,label,grade,max);values+=value
        }
        if(options.callNumber) column("number","Nº da chamada"){ExportCell.number(it.number,NumberStyle.INTEGER)}
        column("name","Nome completo"){ExportCell.text(it.name)}
        column("enrollment","Situação / matrícula"){ExportCell.text("${it.status} • ${it.joined} a ${it.left ?: "sem saída"}")}
        val periodRows=periods.associateWith{n->data.roster(c.id).associate{it.id to reports.student(it,n)}}
        fun frequency(prefix:String,lookup:(Student)->FrequencySummary?) {
            column("${prefix}performed","$prefix • Aulas realizadas elegíveis"){ExportCell.number(lookup(it)?.performed,NumberStyle.INTEGER)}
            column("${prefix}valid","$prefix • Registros válidos"){ExportCell.number(lookup(it)?.valid,NumberStyle.INTEGER)}
            column("${prefix}present","$prefix • Presenças"){ExportCell.number(lookup(it)?.present,NumberStyle.INTEGER)}
            column("${prefix}absent","$prefix • Faltas"){ExportCell.number(lookup(it)?.absent,NumberStyle.INTEGER)}
            column("${prefix}justified","$prefix • Faltas justificadas"){ExportCell.number(lookup(it)?.justified,NumberStyle.INTEGER)}
            column("${prefix}effective","$prefix • Faltas efetivas"){ExportCell.number(lookup(it)?.effectiveAbsences,NumberStyle.INTEGER)}
            column("${prefix}nr","$prefix • Não registrados"){ExportCell.number(lookup(it)?.unregistered,NumberStyle.INTEGER)}
            column("${prefix}percent","$prefix • Frequência (%)"){ExportCell.number(lookup(it)?.percent,NumberStyle.PERCENT)}
        }
        periods.forEach{n->
            val prefix="${n}º bim.";fun row(s:Student)=periodRows.getValue(n).getValue(s.id)
            if(options.model!=ExportModel.FREQUENCY) {
                if(options.model==ExportModel.NOTES) reports.term(n).activities.forEach{a->
                    val origin=data.academic().classes.first{it.id==a.classId}
                    column("activity:${a.id}","$prefix • ${a.name} • ${origin.name} (máx. ${decimal(a.maximum)})",true,a.maximum){s->ExportCell.number(row(s).activities.firstOrNull{it.activity.id==a.id}?.applicableScore)}
                }
                column("$n:original","$prefix • Original",true){ExportCell.number(row(it).original)}
                column("$n:recomposition","$prefix • Recomposição",true){ExportCell.number(row(it).recomposition?.value)}
                column("$n:current","$prefix • Resultado vigente",true){ExportCell.number(row(it).total)}
                column("$n:status","$prefix • Situação dos lançamentos"){ExportCell.text(row(it).exportStatus())}
                column("$n:pending","$prefix • Notas pendentes"){ExportCell.number(row(it).grades.pending,NumberStyle.INTEGER)}
                column("$n:detail","$prefix • Pendências / aplicação"){s->ExportCell.text(row(s).activities.filter{it.state!=ActivityScoreState.RECORDED}.joinToString("; "){"${it.activity.name}: ${if(it.eligible)"Pendente"else"Não aplicável"}"})}
            }
            if(options.model!=ExportModel.NOTES)frequency(prefix){row(it).attendance}
        }
        if(options.term==null&&options.model!=ExportModel.NOTES) {
            val annual=data.roster(c.id).associate{it.id to reports.year(it).attendance}
            frequency("Ano"){annual[it.id]}
        }
        val records=ExportLayout.project(data,ExportLayout.rows(data,c.id)).filter{it.included}.map{slot->
            ExportRecord(slot.row.studentId,slot.student?.let{s->values.map{it(s)}} ?: List(columns.size){ExportCell.EMPTY})
        }
        val period=options.term?.let{"${it}º bimestre"} ?: "Quatro bimestres"
        return ExportTable("Noctua • ${c.name} • ${c.subject} • ${c.year} • $period • ${options.model.label}",options,columns.toList(),records)
    }
    fun decimal(value:Double)=java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.',',')
}
fun StudentPeriodReport.exportStatus():String=when {
    !hasData&&enrollment!=EnrollmentCoverage.OUTSIDE&&period!=null -> "Sem dados"
    state==GradeState.OUTSIDE_ENROLLMENT&&enrollment==EnrollmentCoverage.PARTIAL -> "Sem atividades aplicáveis"
    state==GradeState.OUTSIDE_ENROLLMENT -> "Fora da matrícula"
    state==GradeState.NO_ACTIVITIES -> "Sem atividades"
    state==GradeState.NO_SCORES -> "Notas não lançadas (${grades.pending} pend.)"
    state==GradeState.PARTIAL -> "Parcial (${grades.pending} pend.)"
    else -> "Notas completas"
}
