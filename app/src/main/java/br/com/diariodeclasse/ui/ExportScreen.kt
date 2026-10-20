package br.com.diariodeclasse.ui

import br.com.diariodeclasse.ui.theme.NoctuaSpace
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import kotlinx.coroutines.*
import java.io.File

@Composable fun ExportScreen(data:DiarySnapshot,c:SchoolClass,initialTerm:Int,notify:(String)->Unit,ready:Boolean=true,attendance:(()->Unit)?=null,busyChanged:(Boolean)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var model by rememberSaveable(c.id){mutableStateOf(ExportModel.NOTES.name)}
    var period by rememberSaveable(c.id){mutableIntStateOf(initialTerm)}
    var format by rememberSaveable(c.id){mutableStateOf(ExportFormat.XLSX.name)}
    var headers by rememberSaveable(c.id){mutableStateOf(true)};var callNumber by rememberSaveable(c.id){mutableStateOf(true)}
    var controls by rememberSaveable(c.id){mutableStateOf(false)};var notesPreview by rememberSaveable(c.id){mutableStateOf(false)}
    var allGrades by rememberSaveable(c.id){mutableStateOf(false)}
    var pendingPath by rememberSaveable(c.id){mutableStateOf<String?>(null)}
    var preparing by remember{mutableStateOf(false)};var error by rememberSaveable(c.id){mutableStateOf<String?>(null)}
    val busy=preparing||pendingPath!=null
    LaunchedEffect(busy){busyChanged(busy)}
    DisposableEffect(Unit){onDispose{busyChanged(false)}}
    val options=ExportOptions(ExportModel.valueOf(model),period.takeIf{it!=0},headers,callNumber)
    val table=remember(data,c,options){ExportProjection.build(data,c,options)}
    fun delivered(uri:Uri?) {
        val path=pendingPath ?: return
        scope.launch {
            try {
                val saved=withContext(Dispatchers.IO){ExportFiles.deliver(File(path),uri!=null){context.contentResolver.openOutputStream(requireNotNull(uri),"wt")}}
                if(saved)notify("Arquivo exportado.")else notify("Exportação cancelada.")
            }catch(e:Exception){if(e is CancellationException)throw e;error="Não foi possível gravar o arquivo. Nenhum registro escolar foi alterado."}
            finally{pendingPath=null}
        }
    }
    val xlsx=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.XLSX.mime),::delivered)
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime),::delivered)
    val scroll=rememberScrollState();val matrix=remember(table,notesPreview,allGrades){table.rows(notesPreview,allGrades)}
    LazyColumn(Modifier.fillMaxSize().testTag("export-screen"),contentPadding=PaddingValues(NoctuaSpace.page),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Section("Exportar", "${c.name} • ${c.subject} • ${c.year}")
            attendance?.let{OutlinedButton(onClick=it,enabled=ready&&!busy,modifier=Modifier.testTag("open-attendance-export")){Text("Exportar chamada (XLSX)")}}
            OutlinedButton(onClick={controls=!controls},enabled=ready&&!busy,modifier=Modifier.testTag("export-options")){Text(if(controls)"Recolher opções"else"Opções de exportação")}
            if(controls && !busy) {
                Choice("Período",listOf("1º bimestre","2º bimestre","3º bimestre","4º bimestre","Resumo anual"),if(period==0)"Resumo anual"else"${period}º bimestre"){period=if(it=="Resumo anual")0 else it.first().digitToInt()}
                Choice("Modelo",ExportModel.entries.map{it.label},ExportModel.valueOf(model).label){value->model=ExportModel.entries.first{it.label==value}.name;notesPreview=false}
                Choice("Formato",listOf("XLSX","CSV"),format){format=it}
                if(options.model!=ExportModel.FREQUENCY) Choice("Colunas de Copiar somente notas",listOf("Resultados vigentes","Todas as notas"),if(allGrades)"Todas as notas"else"Resultados vigentes"){allGrades=it=="Todas as notas"}
                Row {Checkbox(headers,{headers=it},enabled=ready);Text("Incluir cabeçalhos e máximos")}
                Row {Checkbox(callNumber,{callNumber=it},enabled=ready);Text("Incluir número da chamada")}
            }
            Text("${ExportModel.valueOf(model).label} • ${if(period==0)"Resumo anual"else"${period}º bimestre"} • $format • ${table.records.size} linhas",style=MaterialTheme.typography.titleSmall)
            Button(enabled=ready&&!busy,onClick={
                val frozen=table;val kind=ExportFormat.valueOf(format);preparing=true;error=null
                scope.launch {
                    var file:File?=null
                    try {
                        file=withContext(Dispatchers.IO){ExportFiles.prepare(File(context.cacheDir,"exports"),frozen,kind)}
                        pendingPath=file.absolutePath
                        val identity=(c.name+"-"+c.subject).replace(Regex("[^\\p{L}\\p{N} _.-]"),"_").take(90)
                        val name="Noctua-$identity-${c.year}-${if(period==0)"anual"else"bimestre-$period"}-${kind.name.lowercase()}.${kind.extension}"
                        if(kind==ExportFormat.XLSX)xlsx.launch(name)else csv.launch(name)
                    }catch(e:Exception){withContext(NonCancellable+Dispatchers.IO){file?.delete()};pendingPath=null;if(e is CancellationException)throw e;error="Não foi possível preparar a exportação."}
                    finally{preparing=false}
                }
            },modifier=Modifier.testTag("export-file")){Text(if(busy)"Aguardando gravação…"else if(!ready)"Aguardando salvamento…"else"Exportar arquivo")}
            fun copy(onlyNotes:Boolean) {
                try {val text=SpreadsheetWriter.clipboard(table,onlyNotes,allGrades);require(text.isNotEmpty())
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Noctua • ${c.name}",text));notify(if(onlyNotes)"Somente notas copiadas."else"Dados copiados.")
                }catch(_:Exception){error="Não foi possível copiar os dados."}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled=ready&&!busy&&table.records.isNotEmpty(),onClick={copy(false)},modifier=Modifier.testTag("export-copy")){Text("Copiar dados")}
                TextButton(enabled=ready&&!busy&&table.records.isNotEmpty()&&table.columns.any{it.grade},onClick={copy(true)},modifier=Modifier.testTag("export-copy-grades")){Text("Copiar somente notas")}
            }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("export-error"))}
            if(table.columns.any{it.grade})FilterChip(notesPreview,{notesPreview=!notesPreview},label={Text("Prévia somente notas")},enabled=ready&&!busy,modifier=Modifier.testTag("export-notes-preview"))
            Text(if(notesPreview)"Sem nomes ou cabeçalhos. Colunas: ${table.columns.filter{if(allGrades)it.grade else it.key.endsWith(":current")}.joinToString("; "){it.label}}"else"${matrix.size} linhas × ${table.columns.size} colunas",style=MaterialTheme.typography.bodySmall)
        }
        if(matrix.isEmpty())item{Text("Nenhuma linha para exportar.")}
        itemsIndexed(matrix,key={index,_->index}){index,row->
            Row(Modifier.horizontalScroll(scroll).testTag("export-preview-$index")){
                row.forEach{cell->Text(SpreadsheetWriter.plain(cell).ifEmpty{"—"},Modifier.width(160.dp).padding(6.dp),style=MaterialTheme.typography.bodySmall)}
            }
            HorizontalDivider()
        }
    }
}
