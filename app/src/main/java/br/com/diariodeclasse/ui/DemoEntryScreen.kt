package br.com.diariodeclasse.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.R
import br.com.diariodeclasse.ui.theme.NoctuaSpace
import kotlinx.coroutines.launch

/** Somente navegação. Nenhuma credencial é salva, validada ou enviada. */
@Composable fun NoctuaStart(content: @Composable () -> Unit) {
    var entered by rememberSaveable { mutableStateOf(false) }
    if (entered) content() else DemoEntryScreen { entered = true }
}

@Composable fun DemoEntryScreen(enter: () -> Unit) {
    // Intencionalmente NÃO saveable: rotação, saída e novo processo descartam os campos.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun future() { focus.clearFocus(); keyboard?.hide(); scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar("Funcionalidade disponível em uma versão futura.") } }
    Scaffold(modifier = Modifier.fillMaxSize().testTag("demo-entry"), snackbarHost = { SnackbarHost(snack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(NoctuaSpace.page),
                verticalArrangement = Arrangement.spacedBy(NoctuaSpace.medium)) {
                Image(painterResource(R.drawable.noctua_logo), "Logo oficial do Noctua", Modifier.size(96.dp).align(Alignment.CenterHorizontally).testTag("entry-logo"), contentScale = ContentScale.Fit)
                Text("Noctua", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Prévia de entrada • acesso livre, sem autenticação.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("entry-email"))
                OutlinedTextField(password, { password = it }, label = { Text("Senha") }, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    trailingIcon = { TextButton(onClick = { visible = !visible }, modifier = Modifier.heightIn(min = 48.dp).testTag("entry-show-password")) { Text(if(visible) "Ocultar" else "Mostrar") } },
                    modifier = Modifier.fillMaxWidth().testTag("entry-password"))
                Button(onClick = { focus.clearFocus(); keyboard?.hide(); email = ""; password = ""; enter() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("entry-enter")) { Text("Entrar") }
                OutlinedButton(onClick = ::future, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("entry-create")) { Text("Criar conta") }
                TextButton(onClick = ::future, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("entry-forgot")) { Text("Esqueci minha senha") }
            }
        }
    }
}
