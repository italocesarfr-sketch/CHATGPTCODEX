package br.com.diariodeclasse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.R
import br.com.diariodeclasse.data.StudentDeletionPreview
import br.com.diariodeclasse.ui.theme.NoctuaSpace

@Composable internal fun StudentFormActions(saveEnabled: Boolean, saveLabel: String, save: () -> Unit,
    delete: (() -> Unit)? = null, deleteEnabled: Boolean = true) {
    val buttons: @Composable (Modifier) -> Unit = { item ->
        Button(enabled=saveEnabled,onClick=save,modifier=item.heightIn(min=48.dp).testTag("student-save")) {
            Text(saveLabel,Modifier.testTag("student-save-label"),textAlign=TextAlign.Center)
        }
        if(delete!=null) OutlinedButton(enabled=deleteEnabled,onClick=delete,
            contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
            colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error),
            modifier=item.heightIn(min=48.dp).testTag("student-delete")) {
            Icon(painterResource(R.drawable.ic_delete),null,Modifier.size(18.dp))
            Spacer(Modifier.width(NoctuaSpace.small))
            Text("Excluir aluno",Modifier.testTag("student-delete-label"),textAlign=TextAlign.Center)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("student-actions")) {
        if(delete==null || maxWidth<280.dp || LocalDensity.current.fontScale>1.2f || saveLabel!="Salvar aluno")
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(NoctuaSpace.medium)) { buttons(Modifier.fillMaxWidth()) }
        else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(NoctuaSpace.medium)) {
            buttons(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable internal fun StudentDeletionDialog(preview: StudentDeletionPreview?, loading: Boolean, error: String?,
    cancel: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest={if(!loading)cancel()},modifier=Modifier.testTag("student-delete-dialog"),
        title={Text("Excluir aluno?")},
        text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("student-delete-diagnosis"),
            verticalArrangement=Arrangement.spacedBy(NoctuaSpace.medium)) {
            if(preview==null) {
                if(loading) Text("Conferindo os registros desta matrícula…")
            } else {
                Text(preview.student.name,style=MaterialTheme.typography.titleMedium)
                Text("${preview.school.name} • ${preview.school.subject} • ${preview.school.year}")
                if(preview.counts.isEmpty()) Text("Este aluno não possui lançamentos nesta turma.")
                else {
                    Text("Registros nesta matrícula:")
                    preview.counts.forEach { (label,count) ->
                        Text("$label: $count",Modifier.testTag("student-delete-count-$label"))
                        if(label=="Registros de frequência") Text(preview.markCounts.entries.joinToString(" • ") { "${it.value} ${it.key}" })
                    }
                }
                if(preview.exportPosition) Text("A posição na planilha personalizada ficará vazia; os demais alunos manterão suas posições.")
                if(preview.blockedReason!=null) Text(preview.blockedReason,color=MaterialTheme.colorScheme.error)
                else {
                    Text("A exclusão apagará os registros exclusivos deste cadastro nesta turma. Esta ação não poderá ser desfeita pelo aplicativo.")
                    Text("Deseja realmente excluir?")
                }
            }
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("student-delete-error")) }
        }},
        dismissButton={TextButton(enabled=!loading,onClick=cancel,modifier=Modifier.heightIn(min=48.dp).testTag("student-delete-cancel")){Text("Não")}},
        confirmButton={TextButton(enabled=!loading&&preview!=null&&preview.blockedReason==null,onClick=confirm,
            colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error),
            modifier=Modifier.heightIn(min=48.dp).testTag("student-delete-confirm")){Text("Sim, excluir")}})
}
