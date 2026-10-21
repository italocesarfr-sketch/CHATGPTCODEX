package br.com.diariodeclasse.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.ui.theme.NoctuaSpace
import kotlinx.coroutines.*
import java.io.File

@Composable fun AttendanceExportScreen(data:DiarySnapshot,c:SchoolClass,term:Int,notify:(String)->Unit,ready:Boolean,annual:Boolean=false,plans:Boolean=false,busyChanged:(Boolean)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val period=data.terms.firstOrNull{it.classId==c.id&&it.number==term}
    var start by rememberSaveable(c.id){mutableStateOf(shortDate(period?.start ?: "${c.year}-01-01"))}
    var end by rememberSaveable(c.id){mutableStateOf(shortDate(period?.end ?: "${c.year}-12-31"))}
    var pending by rememberSaveable(c.id){mutableStateOf<String?>(null)}
    var preparing by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var periodMode by rememberSaveable(c.id){mutableStateOf("Bimestre")}
    var chosenTerm by rememberSaveable(c.id){mutableStateOf(term.toString())}
    val planTerm=data.terms.firstOrNull{it.classId==c.id&&it.number==chosenTerm.toInt()}
    val planStart=if(plans&&periodMode=="Bimestre")planTerm?.start else runCatching{iso(start)}.getOrNull()
    val planEnd=if(plans&&periodMode=="Bimestre")planTerm?.end else runCatching{iso(end)}.getOrNull()
    val busy=preparing||pending!=null
    LaunchedEffect(busy){busyChanged(busy)};DisposableEffect(Unit){onDispose{busyChanged(false)}}
    val result=remember(data,c,start,end,annual,plans,planStart,planEnd){runCatching{if(plans)LessonPlanExportProjection.build(data,c,requireNotNull(planStart),requireNotNull(planEnd))else if(annual)AnnualExportProjection.build(data,c)else AttendanceExportProjection.build(data,c,iso(start),iso(end))}}
    val table=result.getOrNull();val matrix=remember(table,plans){table?.rows().orEmpty().let{if(plans)it.drop(1)else it}}
    val canExport=ready&&!busy&&table!=null&&table.columns.size>2&&table.records.isNotEmpty()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.XLSX.mime)){uri:Uri?->
        val path=pending
        if(path!=null)scope.launch {
            try {
                val saved=withContext(Dispatchers.IO){ExportFiles.deliver(File(path),uri!=null){context.contentResolver.openOutputStream(requireNotNull(uri),"wt")}}
                notify(if(saved&&plans) "Planos de aula exportados." else if(saved) "${if(annual)"Relatório anual"else"Chamada"} exportad${if(annual)"o"else"a"}."else"Exportação cancelada.")
            }catch(e:Exception){if(e is CancellationException)throw e;error="Não foi possível gravar a planilha. Os registros foram preservados."}
            finally{pending=null}
        }
    }
    fun export(share:Boolean) {
        val frozen=table ?: return
        preparing=true;error=null
        scope.launch {
            var file:File?=null
            try {
                file=withContext(Dispatchers.IO){if(share)SpreadsheetShare.prepare(context,frozen)else ExportFiles.prepare(File(context.cacheDir,"exports"),frozen,ExportFormat.XLSX)}
                if(share)context.startActivity(Intent.createChooser(SpreadsheetShare.intent(context,file),if(plans)"Compartilhar planos de aula"else if(annual)"Compartilhar relatório anual"else"Compartilhar chamada"))
                else {pending=file.absolutePath;picker.launch(if(plans)"Noctua-planos-${c.year}-${planStart}-a-${planEnd}.xlsx"else if(annual)"Noctua-relatorio-anual-${c.year}.xlsx"else"Noctua-chamada-${c.year}-${iso(start)}-a-${iso(end)}.xlsx")}
            }catch(e:Exception){withContext(NonCancellable+Dispatchers.IO){file?.delete()};pending=null;if(e is CancellationException)throw e;error="Não foi possível preparar a planilha."}
            finally{preparing=false}
        }
    }
    val scroll=rememberScrollState()
    LazyColumn(Modifier.fillMaxSize().testTag(if(plans)"plans-export-screen"else if(annual)"annual-export-screen"else"attendance-export-screen"),contentPadding=PaddingValues(NoctuaSpace.page),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Section(if(plans)"Exportar planos de aula"else if(annual)"Exportar relatório anual"else"Exportar chamada", "${c.name} • ${c.subject} • ${c.year}")
            if(plans) {
                Choice("Período",listOf("Bimestre","Intervalo de datas"),periodMode){if(!busy)periodMode=it}
                if(periodMode=="Bimestre") Choice("Bimestre",listOf("1","2","3","4"),chosenTerm){if(!busy)chosenTerm=it}
            }
            if(!annual&&(!plans||periodMode=="Intervalo de datas")) {
            Field("Data inicial (dd/mm/aaaa)",start,{start=it},modifier=Modifier.testTag("attendance-export-start"),enabled=!busy)
            Field("Data final (dd/mm/aaaa)",end,{end=it},modifier=Modifier.testTag("attendance-export-end"),enabled=!busy)
            }
            if(table==null)Text("Informe datas válidas, em ordem, dentro de ${c.year}.",color=MaterialTheme.colorScheme.error)
            else if(table.columns.size<=2||table.records.isEmpty())Text(if(plans)"Nenhum plano de aula neste período."else if(annual)"Nenhum aluno incluído na exportação."else"Nenhum horário realizado elegível para exportar neste período.")
            Button(enabled=canExport,onClick={export(false)},modifier=Modifier.testTag("attendance-export-file")){Text(if(busy)"Aguardando…"else"Salvar XLSX")}
            OutlinedButton(enabled=canExport,onClick={export(true)},modifier=Modifier.testTag("attendance-export-share")){Text("Compartilhar XLSX")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("attendance-export-error"))}
            Text(if(plans)"Prévia dos planos de aula"else if(annual)"Prévia do relatório anual"else"Prévia da chamada",style=MaterialTheme.typography.titleMedium)
        }
        itemsIndexed(matrix){index,row->Row(Modifier.horizontalScroll(scroll).testTag("attendance-export-row-$index")){
            row.forEach{Text(SpreadsheetWriter.plain(it).ifEmpty{"—"},Modifier.width(150.dp).padding(6.dp),style=MaterialTheme.typography.bodySmall)}
        };HorizontalDivider()}
    }
}
