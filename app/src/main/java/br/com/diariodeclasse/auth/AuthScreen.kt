package br.com.diariodeclasse.auth

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.diariodeclasse.DiaryApplication
import br.com.diariodeclasse.R
import br.com.diariodeclasse.ui.DiaryApp
import br.com.diariodeclasse.ui.DiaryViewModel
import kotlinx.coroutines.launch

@Composable fun AuthRoot(app:DiaryApplication) {
    val state by app.auth.state.collectAsState()
    val containers:AccountViewModels=viewModel()
    val user=state.user
    val claimRequired=user?.let{app.workspaces.needsClaim(it)} ?: false
    var workspace by remember{mutableStateOf<AccountWorkspace?>(null)}
    var failure by remember{mutableStateOf("")}
    LaunchedEffect(user?.id,state.recovery,claimRequired) {
        workspace=null;failure=""
        if(user==null||state.recovery||claimRequired){containers.lock();app.workspaces.lock()}
        else if(!app.workspaces.needsClaim(user))try{workspace=app.workspaces.activate(user)}catch(_:Exception){failure="Não foi possível abrir esta conta. Os registros foram preservados."}
    }
    when {
        state.storageFailure -> AuthPanel("Dados protegidos") { Text(state.message);Text("Não desinstale nem limpe os dados do aplicativo. Guarde seu backup e procure suporte.") }
        state.initializing -> AuthPanel("Conectando") { CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally)) }
        user==null||state.recovery -> AuthForm(state,app.auth)
        claimRequired -> LegacyClaim(app,user){ workspace=app.workspaces.activate(user) }
        failure.isNotEmpty() -> AuthPanel("Acesso indisponível"){Text(failure);TextButton(onClick={app.authScope.launch{app.auth.logout()}}){Text("Sair da conta")}}
        workspace?.user?.id==user.id && app.auth.canUse(user.id) -> key(user.id) {
            val owner=containers.owner(user.id,androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app))
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                val model:DiaryViewModel=viewModel()
                DiaryApp(model)
            }
        }
        else -> AuthPanel("Abrindo seus registros"){CircularProgressIndicator()}
    }
}
@Composable internal fun AuthPanel(title:String,content:@Composable ColumnScope.()->Unit) {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),contentAlignment=Alignment.Center) {
            Column(Modifier.widthIn(max=440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.noctua_logo),"Logo oficial do Noctua",Modifier.size(88.dp).align(Alignment.CenterHorizontally).testTag("entry-logo"))
                Text(title,style=MaterialTheme.typography.headlineMedium,modifier=if(title=="Conectando")Modifier.align(Alignment.CenterHorizontally)else Modifier)
                content()
            }
        }
    }
}
@Composable fun AuthForm(state:AuthState,auth:AuthController) {
    var mode by rememberSaveable{mutableStateOf("login")}
    var email by remember{mutableStateOf("")};var name by remember{mutableStateOf("")}
    var password by remember{mutableStateOf("")};var confirmation by remember{mutableStateOf("")}
    var visible by remember{mutableStateOf(false)}
    var confirmationVisible by remember{mutableStateOf(false)}
    val localScope=rememberCoroutineScope()
    val scope=(LocalContext.current.applicationContext as? DiaryApplication)?.authScope ?: localScope
    LaunchedEffect(state.recovery){if(state.recovery)mode="login"}
    val current=if(state.recovery)"reset"else mode
    fun back(){password="";confirmation="";visible=false;confirmationVisible=false;mode="login";if(state.recovery)scope.launch{auth.logout()}}
    androidx.activity.compose.BackHandler(current!="login"&&!state.loading){back()}
    fun submit() { val p=password;val c=confirmation;password="";confirmation="";scope.launch{
        when(current){"signup"->auth.signup(name,email,p,c);"forgot"->auth.recover(email);"reset"->auth.newPassword(p,c);else->auth.login(email,p)}
    } }
    AuthPanel(when(current){"signup"->"Criar conta";"forgot"->"Recuperar senha";"reset"->"Nova senha";else->"Noctua"}) {
        if(state.message.isNotBlank())Text(state.message,Modifier.testTag("auth-message"),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(current=="signup")OutlinedTextField(name,{name=it},label={Text("Nome completo")},modifier=Modifier.fillMaxWidth().testTag("auth-name"),enabled=!state.loading)
        if(current!="reset")OutlinedTextField(email,{email=it},label={Text("E-mail")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth().testTag("entry-email"),enabled=!state.loading)
        if(current!="forgot") {
            OutlinedTextField(password,{password=it},label={Text("Senha")},singleLine=true,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
                trailingIcon={TextButton(onClick={visible=!visible},Modifier.heightIn(min=48.dp).testTag("entry-show-password")){Text(if(visible)"Ocultar"else"Mostrar")}},modifier=Modifier.fillMaxWidth().testTag("entry-password"),enabled=!state.loading)
            if(current in listOf("signup","reset"))OutlinedTextField(confirmation,{confirmation=it},label={Text("Confirmar senha")},singleLine=true,visualTransformation=if(confirmationVisible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
                trailingIcon={TextButton(onClick={confirmationVisible=!confirmationVisible},Modifier.heightIn(min=48.dp).testTag("auth-show-confirmation")){Text(if(confirmationVisible)"Ocultar"else"Mostrar")}},modifier=Modifier.fillMaxWidth().testTag("auth-confirm-password"),enabled=!state.loading)
        }
        if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick=::submit,enabled=!state.loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("entry-enter")) {Text(when(current){"signup"->"Criar conta";"forgot"->"Enviar recuperação";"reset"->"Salvar nova senha";else->"Entrar"})}
        if(current=="login") {
            OutlinedButton(onClick={password="";confirmation="";visible=false;mode="signup"},enabled=!state.loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("entry-create")){Text("Criar conta")}
            TextButton(onClick={password="";confirmation="";visible=false;mode="forgot"},enabled=!state.loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("entry-forgot")){Text("Esqueci minha senha")}
        } else TextButton(onClick=::back,enabled=!state.loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Voltar para entrar")}
    }
}
@Composable internal fun LegacyClaim(app:DiaryApplication,user:AuthUser,opened:suspend()->Unit) {
    val context=LocalContext.current
    val scope=app.authScope
    var password by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var approvedId by rememberSaveable{mutableStateOf<String?>(null)}
    var approvedAt by rememberSaveable{mutableStateOf(0L)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        if(uri!=null&&approvedId==user.id&&System.currentTimeMillis()-approvedAt in 0..300_000&&app.auth.state.value.user?.id==user.id) {
            busy=true;val phrase=password.toCharArray();password="";approvedId=null
            scope.launch{try{app.workspaces.claim(user,uri,phrase);opened()}catch(_:Exception){message="Não foi possível validar o backup e vincular. Os dados anteriores continuam protegidos."}finally{phrase.fill('\u0000');busy=false}}
        } else {approvedId=null;message="Vinculação cancelada. Os dados não foram alterados."}
    }
    val credential=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==Activity.RESULT_OK&&approvedId==user.id&&app.auth.state.value.user?.id==user.id){approvedAt=System.currentTimeMillis();save.launch("noctua-antes-da-vinculacao.noctua")}else{approvedId=null;message="Vinculação não autorizada."}
    }
    AuthPanel("Seus dados anteriores") {
        Text("Vincular os registros deste aparelho à conta ${user.email}?")
        Text("Você confirmará o bloqueio do Android e salvará um backup protegido antes de vincular.")
        OutlinedTextField(password,{password=it},label={Text("Senha do backup")},supportingText={Text("12 ou mais caracteres")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth().testTag("auth-claim-password"),enabled=!busy)
        if(message.isNotBlank())Text(message)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(enabled=!busy&&password.length>=12,onClick={
            val guard=context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if(!guard.isDeviceSecure)message="Configure um PIN ou senha de bloqueio no Android para autorizar a vinculação."
            else {
                val intent=guard.createConfirmDeviceCredentialIntent("Vincular dados do Noctua",user.email)
                if(intent==null)message="O Android não disponibilizou a confirmação de bloqueio."
                else {approvedId=user.id;credential.launch(intent)}
            }
        },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("auth-claim-legacy")){Text("Criar backup e vincular")}
        TextButton(enabled=!busy,onClick={scope.launch{app.auth.logout()}},modifier=Modifier.fillMaxWidth()){Text("Sair sem vincular")}
    }
}
