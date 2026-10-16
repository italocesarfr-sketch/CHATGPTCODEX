package br.com.diariodeclasse.ocr

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.RosterParser
import br.com.diariodeclasse.ui.*

@Composable fun PhotoImportScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, done: () -> Unit) {
    val photo: PhotoViewModel = viewModel(key = "photo-${c.id}")
    val bitmap by photo.bitmap.collectAsStateWithLifecycle()
    val busy by photo.busy.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val status by photo.status.collectAsStateWithLifecycle()
    val rows = data.drafts.filter { it.classId == c.id }
    val existing = data.roster(c.id)
    var joined by rememberSaveable(c.id) { mutableStateOf(shortDate(defaultEnrollment(c.year))) }
    var confirmation by remember { mutableStateOf(false) }; var discard by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ImportDraft?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) photo.select(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { photo.cameraResult(it) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Section("Importar alunos por fotografia", "${c.name} • revisão obrigatória")
            Info("Fotografe uma página por vez, com boa iluminação. O reconhecimento é feito offline no aparelho. Nenhum aluno será cadastrado antes da confirmação.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    try { camera.launch(photo.cameraUri()) } catch (_: Exception) { photo.cameraResult(false); vm.message("Não foi possível abrir a câmera. Selecione uma fotografia da galeria.") }
                }, modifier = Modifier.weight(1f)) { Text("Fotografar") }
                OutlinedButton(enabled = !busy, onClick = {
                    try { gallery.launch(arrayOf("image/*")) } catch (_: Exception) { vm.message("Nenhum seletor de imagens disponível.") }
                }, modifier = Modifier.weight(1f)) { Text("Galeria") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
        }
        bitmap?.let { image -> item {
            CropPanel(image, busy, rotate = photo::rotate, discard = photo::discardImage) { l, t, r, b -> photo.recognize(c.id, l, t, r, b) }
        } }
        item {
            Section("Conferência", "${rows.size} nomes • ${rows.map { it.page }.filter { it > 0 }.distinct().size} páginas • ${rows.count { it.reviewed }} conferidos")
            Text("A ordem das linhas e das páginas é mantida. Todas as leituras começam destacadas: o OCR não oferece uma medida confiável de certeza por nome.", style = MaterialTheme.typography.bodySmall)
            Field("Data de ingresso (dd/mm/aaaa)", joined, { joined = it })
            OutlinedButton(enabled = !busy, onClick = { vm.act {
                val current = vm.repository.dao.draftRows(c.id)
                vm.repository.dao.save(ImportDraft(classId = c.id, name = "", number = null,
                    position = (current.maxOfOrNull { it.position } ?: -1) + 1, page = 0))
            } }, modifier = Modifier.fillMaxWidth()) { Text("+ Adicionar aluno manualmente") }
        }
        items(rows, key = { it.id }) { row ->
            val key = RosterParser.key(row.name)
            val duplicate = key.isNotEmpty() && (existing.any { RosterParser.possibleDuplicate(it.name, row.name) } || rows.any { it.id != row.id && RosterParser.possibleDuplicate(it.name, row.name) })
            val numberConflict = row.number != null && (existing.any { it.number == row.number } || rows.any { it.id != row.id && it.number == row.number })
            DraftRow(vm, row, duplicate, numberConflict, !busy) { deleting = row }
        }
        item {
            if (rows.isNotEmpty()) {
                Info("Para outra página, toque em Fotografar ou Galeria. Números em branco serão preenchidos após o maior número informado. Números repetidos precisam ser corrigidos.")
                Button(enabled = !busy && !saving && rows.all { it.reviewed && it.name.isNotBlank() },
                    onClick = { confirmation = true }, modifier = Modifier.fillMaxWidth()) { Text("Confirmar e cadastrar ${rows.size} alunos") }
                TextButton(enabled = !busy, onClick = { discard = true }, modifier = Modifier.fillMaxWidth()) { Text("Descartar rascunho de importação") }
            }
            Text("O rascunho é salvo no aparelho. Voltar à turma não confirma a lista.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmation) Confirm("Cadastrar alunos na turma ${c.name}?", "Serão cadastrados ${rows.size} alunos com ingresso em $joined. Os nomes e números devem ter sido conferidos por você.", { confirmation = false }) {
        confirmation = false
        vm.act(done = { photo.discardImage(); vm.message("Alunos importados e disponíveis para chamadas e avaliações."); done() }) {
            vm.repository.confirmImport(c.id, iso(joined))
        }
    }
    if (discard) Confirm("Descartar rascunho?", "Os nomes ainda não confirmados serão removidos. Alunos já cadastrados serão preservados.", { discard = false }) {
        vm.act(done = { discard = false; photo.discardImage() }) { vm.repository.dao.clearDraft(c.id) }
    }
    deleting?.let { row -> Confirm("Remover esta linha?", "A linha será removida apenas da lista provisória.", { deleting = null }) {
        vm.act(done = { deleting = null }) { vm.repository.dao.deleteDraft(row.id) }
    } }
}

@Composable private fun DraftRow(vm: DiaryViewModel, row: ImportDraft, duplicate: Boolean, conflict: Boolean, enabled: Boolean, delete: () -> Unit) {
    var name by rememberSaveable(row.id) { mutableStateOf(row.name) }
    var number by rememberSaveable(row.id) { mutableStateOf(row.number?.toString() ?: "") }
    var reviewed by rememberSaveable(row.id) { mutableStateOf(row.reviewed) }
    var homonym by rememberSaveable(row.id) { mutableStateOf(row.allowHomonym) }
    val validNumber = number.isBlank() || number.toIntOrNull()?.let { it > 0 } == true
    fun persist() {
        val update = row.copy(name = name, number = number.toIntOrNull(), reviewed = reviewed && validNumber && name.trim().length >= 2, allowHomonym = homonym)
        vm.act { vm.repository.dao.save(update) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = if (reviewed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Linha ${row.position + 1} • ${if (row.page == 0) "manual" else "página ${row.page}"}${if (!reviewed) " • CONFERIR" else " • conferida"}", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(value = name, onValueChange = { name = it; reviewed = false; persist() }, label = { Text("Nome completo") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = number, onValueChange = { value ->
                if (value.length <= 4 && value.all { it.isDigit() }) { number = value; reviewed = false; persist() }
            }, label = { Text("Número na chamada (opcional)") }, isError = !validNumber || conflict, enabled = enabled,
                supportingText = { if (conflict) Text("Número repetido: corrija ou deixe em branco.") }, modifier = Modifier.fillMaxWidth())
            if (!reviewed) Text(row.reason, style = MaterialTheme.typography.bodySmall)
            if (duplicate) {
                Text("Possível duplicidade: este nome aparece na turma ou em outra página.", color = MaterialTheme.colorScheme.error)
                Row {
                    Checkbox(homonym, { homonym = it; reviewed = false; persist() }, enabled = enabled)
                    Text("É outro aluno (nome igual ou parecido)", Modifier.padding(top = 10.dp))
                }
            }
            Row {
                Checkbox(reviewed, { reviewed = it; persist() }, enabled = enabled && validNumber && name.trim().length >= 2 && !conflict && (!duplicate || homonym))
                Text("Conferi o nome e o número", Modifier.padding(top = 12.dp))
            }
            TextButton(onClick = delete, enabled = enabled) { Text("Excluir linha") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CropPanel(bitmap: Bitmap, busy: Boolean, rotate: () -> Unit, discard: () -> Unit, recognize: (Float, Float, Float, Float) -> Unit) {
    var horizontal by remember(bitmap) { mutableStateOf(0f..1f) }
    var vertical by remember(bitmap) { mutableStateOf(0f..1f) }
    var start by remember(bitmap) { mutableStateOf(Offset.Zero) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("Recortar região dos nomes")
        Text("Arraste sobre a imagem para selecionar. Use os controles abaixo para ajustar as bordas.", style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
            Image(bitmap.asImageBitmap(), contentDescription = "Fotografia da lista de chamada para recorte", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.matchParentSize().pointerInput(bitmap, busy) {
                if (!busy) detectDragGestures(onDragStart = { position ->
                    start = Offset((position.x / size.width).coerceIn(0f, 1f), (position.y / size.height).coerceIn(0f, 1f))
                }, onDrag = { change, _ ->
                    change.consume()
                    val end = Offset((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                    horizontal = minOf(start.x, end.x)..maxOf(start.x, end.x)
                    vertical = minOf(start.y, end.y)..maxOf(start.y, end.y)
                })
            }) {
                val x = horizontal.start * size.width; val r = horizontal.endInclusive * size.width
                val y = vertical.start * size.height; val b = vertical.endInclusive * size.height
                val shade = Color.Black.copy(alpha = .55f)
                drawRect(shade, Offset.Zero, Size(size.width, y))
                drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
                drawRect(shade, Offset(0f, y), Size(x, b - y))
                drawRect(shade, Offset(r, y), Size(size.width - r, b - y))
                drawRect(Color(0xFF67FFB8), Offset(x, y), Size(r - x, b - y), style = Stroke(3.dp.toPx()))
            }
        }
        Text("Bordas esquerda e direita", style = MaterialTheme.typography.labelMedium)
        RangeSlider(value = horizontal, onValueChange = { horizontal = it }, enabled = !busy, valueRange = 0f..1f)
        Text("Bordas superior e inferior", style = MaterialTheme.typography.labelMedium)
        RangeSlider(value = vertical, onValueChange = { vertical = it }, enabled = !busy, valueRange = 0f..1f)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(enabled = !busy, onClick = rotate) { Text("Girar 90°") }
            TextButton(enabled = !busy, onClick = discard) { Text("Descartar foto") }
        }
        Button(enabled = !busy && horizontal.endInclusive - horizontal.start > .02f && vertical.endInclusive - vertical.start > .02f,
            onClick = { recognize(horizontal.start, vertical.start, horizontal.endInclusive, vertical.endInclusive) }, modifier = Modifier.fillMaxWidth()) { Text("Reconhecer nomes deste recorte") }
    }
}
