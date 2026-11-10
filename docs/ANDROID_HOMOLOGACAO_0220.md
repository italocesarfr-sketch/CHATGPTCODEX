# 0.22.0 — Variante Android paralela de homologação

> **Atualização registrada em 09/11/2026 (America/Sao_Paulo):** o APK e os hashes
> desta página continuam os mesmos; não foi gerado novo APK paralelo de
> homologação. Builds DEBUG da regressão são restritos à CI. O proprietário
> relatou homologação funcional em dois emuladores. Consulte a
> [matriz consolidada H01–H13](CONSOLIDACAO_HOMOLOGACAO_0220.md) para distinguir
> esse relato das evidências locais e dos ensaios de segurança ainda pendentes.

Branch `sync/academic-complete-0220`. Base de publicação SQL `e71405e`;
implementação Android `69e86e60bce21e07ab5d77af8fe479e1539bdec5`, capturas/teste
nativo `74d8ca4`, entrada manual na workflow existente `7d00bd0` e preparação
do SDK corrigida em `e8fa20406e63727f71de040461670f46bc172d1b`.
**Preparação experimental. H01–H04 hospedados ainda não aprovados.**

## Identidade e isolamento

| Propriedade | Homologação | Oficial preservado |
|---|---|---|
| Build type | `homologacao`, DEBUG | `debug` / `release`, sem renomear tarefas existentes |
| applicationId | `br.com.diariodeclasse.homologacao` | `br.com.diariodeclasse` |
| Nome launcher/banner | **NOcTuA Homologação** | Visual/nome oficiais existentes |
| Versão | `0.22.0-dev-e2-homologacao`, c45 | `0.22.0-dev-e2`, c45 nesta branch experimental |
| Supabase | `https://sscexzxtqdcsqdlcytum.supabase.co` | Configuração anterior de noctua-auth |
| Callback | `br.com.diariodeclasse.homologacao://auth/callback` | `br.com.diariodeclasse://auth/callback` |
| Provider de exportação | `br.com.diariodeclasse.homologacao.exports` | Autoridade oficial preservada |
| Assinatura | Android Debug, independente do certificado permanente | Configuração estável não alterada |

Não há sharedUserId. O Android atribui UID/data directory próprios; Room,
SharedPreferences, caches, arquivos noBackup e chaves Android Keystore são
separados por aplicativo. Dentro da variante, continuam separados por UUID de
conta os workspaces já existentes. Não existe importação automática do banco ou
sessão do aplicativo oficial. Backups externos só são abertos por ação explícita
com as proteções anteriores; **não importar backups reais nesta variante**.

Room 14, migrações, esquema lógico/formato 12 do backup, criptografia e regras
acadêmicas não foram modificados. O banner usa Material 3, tipografia e cores do
tema; é visível antes/depois do login. Impeccable 4.7 foi consultada para a
identificação discreta, insets e teste de legibilidade com fonte ampliada.

## Configuração pública: falha segura

O build aceita exclusivamente a chave **publishable** do projeto
`noctua-homologacao`; nenhum segredo administrativo é necessário.

Ordem das fontes de configuração:

1. Variável de ambiente `NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY`.
2. Propriedade `noctua.homologacao.publishableKey` no `gradle.properties` privado do
   usuário de build, fora do repositório.

A URL/ref de homologação são fixas, não substituíveis por variável. A chave não
está versionada. Valor ausente, formato inválido, chave administrativa/legada JWT
ou a chave pública oficial bloqueiam APK/instalação. Entrada inválida não é
embutida nem em classes geradas para teste. Nenhum valor é impresso no erro.

`AuthEnvironment` também valida identidade, URL exata, callback, DEBUG e fingerprint
proibido antes da inicialização/requisições. Todos os clientes usam a configuração
corrente: SupabaseAuthClient, SupabaseClassSync e SupabaseAcademicSync. Homologação
não admite nem o override HTTP local usado pelas fixtures do CI debug; não herda
`debug_network_security`. Redirect HTTP está desativado nos três transportes.

A validação local de formato/fingerprint não prova que uma chave opaca pertence
àquele projeto; o administrador deve copiá-la do projeto correto. Uma chave de
outro projeto não cria fallback: a URL continua fixa e a operação será recusada.
Não usar service_role, sb_secret_, senha de banco, access token ou refresh token.

### Como configurar e compilar

Obter em **noctua-homologacao → Settings/API Keys → Publishable key**. Configurar
uma das fontes acima sem colar valores no código, relatórios ou linha de comando
que fique no histórico. Não reutilizar a chave do projeto de produção.

```bash
./gradlew :app:validateHomologacaoConfiguration
./gradlew testDebugUnitTest testHomologacaoUnitTest assembleHomologacao
python3 scripts/validate-homologacao.py \
  --apk app/build/outputs/apk/homologacao/app-homologacao.apk \
  --build-tools "$ANDROID_HOME/build-tools/35.0.0"
sha256sum app/build/outputs/apk/homologacao/app-homologacao.apk
```

O validador offline confere package, recursos/label, callback, UID compartilhado,
provider, backup/cleartext, origem, assinatura e ausência dos marcadores de chave
administrativa/fixtures/endpoint e chave de produção no DEX. Só informa presença
da chave e hashes; não revela seu valor. `--allow-missing-key` é apenas inspeção
estática de manifesto/classes; não admite APK.

A variável não estava disponível no ambiente local; o bloqueio de empacotamento
foi comprovado sem contorná-lo com chave fictícia. Posteriormente o Actions recebeu
configuração pública válida e gerou/verificou o APK na execução **42438137774**.
O valor não foi copiado para o repositório, relatório ou ambiente local. A validação
estática não prova autenticação nem associação da chave ao projeto: isso continua
sujeito ao primeiro ensaio real.

### GitHub Actions sem modificar main

Workflow reutilizável `.github/workflows/homologacao.yml`; entrada manual pelo
arquivo **android.yml já existente** evita depender de registrar novo workflow
na branch principal.

1. No repositório, configurar o secret Actions `NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY`
   com a chave pública do projeto de homologação. É configuração pública protegida
   pelo mecanismo de secrets; nenhuma credencial administrativa deve ser adicionada.
2. Actions → **Compilar APK Android** → Run workflow → branch
   `sync/academic-complete-0220` → marcar **homologacao_only**.
3. Essa opção só executa o build paralelo. Não faz login, não cria contas, não
   executa SQL e não consome arquivos de credenciais live. Sem chave válida, falha
   antes de empacotar. A execução normal sem essa opção mantém a regressão existente.
4. Baixar somente o artefato `noctua-homologacao-paralela-debug` e conferir seu
   SHA/relatório. `noctua-apk-teste` da regressão comum é outro package e não deve
   ser usado para instalação paralela.

A variável configurada no ambiente Codex e o secret Actions são mecanismos
independentes; nenhum valor é copiado automaticamente entre eles.

## Retorno PKCE a autorizar posteriormente

Callback base **exato aceito pelo Android**:

```text
br.com.diariodeclasse.homologacao://auth/callback
```

Em **noctua-homologacao → Authentication → URL Configuration → Redirect URLs**,
autorizar esse retorno. Como o aplicativo envia `?state=<valor aleatório>`, a
allowlist pode precisar do padrão restrito ao mesmo prefixo:

```text
br.com.diariodeclasse.homologacao://auth/callback**
```

Esse padrão serve apenas para o sufixo de estado na configuração Supabase; o
cliente continua exigindo scheme/host/path exatos. Não autorizar localhost ou URL
HTTP genérica, não alterar o projeto de produção. Confirmar o padrão efetivo no
ensaio de e-mail; não houve configuração remota nesta rodada.

`PendingLink` mantém S256/verifier, comparação constante do state, validade de
30 minutos, code obrigatório, chaves únicas, ausência de fragment/userinfo/porta e
rejeição de caminhos adicionais. O scheme vem do BuildConfig; as variantes não
aceitam o retorno uma da outra. O manifesto usa o mesmo scheme. Nenhum fluxo aceita
token de sessão em fragmento como alternativa ao PKCE.

## APK experimental verificado e instalação paralela

- Código do artefato: `e8fa20406e63727f71de040461670f46bc172d1b`.
- [Build específico aprovado — 42438137774](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42438137774).
- [Baixar noctua-homologacao-paralela-debug](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42438137774/artifacts/13599694535).
- Arquivo: `app-homologacao.apk`, **12.954.702 bytes**.
- SHA-256 do **APK**:
  `d1f57c8dbf43387aa82232d59bb1ced0c7ca56bcbdf738d3b8bf622b9941f87b`.
- SHA-256 do **ZIP** publicado pelo Actions:
  `011345fce0a252d45979f52dbbd35bbcc4da59f7814a461d59f78c83e489f95d`.
- Certificado Debug (`C=US, O=Android, CN=Android Debug`), SHA-256:
  `b6dd351f4e3e1e633c7447e02f449316779e8ff0b14707f9981c80e363d92189`.

O ZIP foi baixado e seu hash conferido; o hash do APK coincide com o manifesto e
com a saída SHA do CI. AAPT confirmou nome/applicationId/versão; apksigner confirmou
a assinatura. O validador do job também verificou manifesto, ausência do endpoint
/chave de produção e dos marcadores proibidos no DEX. Evidência sem chave em
[ANDROID_HOMOLOGACAO_0220_APK.json](evidence/ANDROID_HOMOLOGACAO_0220_APK.json).
Os testes JVM debug e homologacao desse build tiveram, cada um, 288 casos:
**283 aprovados, cinco condicionais, zero falhas**. Não são 566 casos únicos.
Não houve instalação ou requisição Auth hospedada. A coexistência no dispositivo
continua pendente de ensaio, mesmo com identidades/assinatura verificadas.

O APK usa certificado Debug da execução. Guardar esse artefato/hash para o ensaio;
um build futuro pode receber outro certificado Debug. Não substituir o app oficial.


Conferir `applicationId=br.com.diariodeclasse.homologacao`, label e assinatura Debug.
Instalar em aparelho de teste, sem desinstalar, limpar, atualizar ou substituir o
aplicativo oficial. Em uma instalação inicial pode-se usar:

```bash
adb install app/build/outputs/apk/homologacao/app-homologacao.apk
```

Nunca aplicar `pm clear`, uninstall, downgrade ou APK debug oficial para resolver
problemas da instalação original. Se uma homologação anterior tiver outro
certificado, interromper e preservar seus dados; não desinstalar por rotina.
A coexistência real e o comportamento do Keystore/UID nos aparelhos ainda precisam
ser observados; o isolamento de package/provider foi conferido estaticamente.

## Elegibilidade e consentimento A–E

Mantidos: BuildConfig.DEBUG, workspace não legado, UUID diferente do proprietário
legado, sessão confirmada/ativa, allowlist remota, barreiras v1/v2/escopos,
consentimento explícito e dados fictícios. Homologação é DEBUG deliberadamente,
sem transformar release oficial em elegível. Não houve SQL, alteração da allowlist
ou criação de conta. Relato do administrador: 16 tabelas protegidas e duas contas
confirmadas autorizadas; não é resultado de teste executado pelo aplicativo.

## H01–H04 preparados, ainda não homologados

Atualização de 10/11: isolamento prático entre duas contas distintas foi relatado
como aprovado pelo proprietário. O runner API continua sem execução hospedada.
Foi preparada uma entrada manual separada, com Environment Secrets e relatório
sanitizado: [H01_H04_HOSPEDADOS_0220.md](H01_H04_HOSPEDADOS_0220.md). A workflow
comum de build/regressão continua sem acesso às credenciais de contas.

`HomologationHostedAuthTest` é opt-in e só roda na variante de homologação. Precisa
da chave pública válida, do caminho privado `NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE`
e de **autorização explícita posterior** para essas sessões: variável
`NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED=YES`. A presença do arquivo ou da chave
de build, sozinha, não autoriza requisições. Esta rodada não autoriza nem executa
o runner hospedado.
O arquivo, fora do Git, deve conter:

- `accounts`: exatamente dois objetos com `email`, `password` e `expectedId`;
- UUIDs Auth distintos/normalizados, nenhum igual ao proprietário legado;
- `fixtureClassId`: ID de uma turma exclusivamente fictícia criada pela interface
  na conta A e sincronizada no protocolo acadêmico; não gerar por SQL administrativo;
- `allowZeroRowPermissionProbe`: false por padrão; true somente quando autorizado
  o probe de permissão descrito abaixo.

Não enviar esse arquivo ao chat ou artefatos. O runner não imprime conteúdo,
sessões, corpo de erro ou credenciais; identidades privadas têm toString redigido.
Não cria usuários, não usa service_role, não redefine senhas e não executa reset,
fixtures administrativas ou testes PostgreSQL contra o hospedado.

```bash
NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED=YES \
NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE=/caminho/privado/contas-de-teste.json \
./gradlew testHomologacaoUnitTest --tests '*HomologationHostedAuthTest'
```

| Caso | Preparado no runner | O que permanece necessário |
|---|---|---|
| H01 | Login, confirmação/UUID, consulta de identidade, refresh, senha incorreta e logout de ambas as contas comuns. | Chave, arquivo privado e autorização para criar/encerrar essas sessões de ensaio. Entrega/confirmação de e-mail depende do serviço configurado. |
| H02 | A precisa ler a fixture conhecida; B deve conseguir consultar a RPC de estado e não pode ler a mesma linha privada de A, mesmo conhecendo IDs. | Turma fictícia A presente; mudança de conta/isolamento Room no Android e provas de escrita cruzada serão conferidas no roteiro hospedado completo. Ausência de fixture falha, não prova isolamento. |
| H03 | Helper privado recusado; PATCH direto com predicado contraditório (revisão positiva conhecida E revisão<0), impossível de afetar linhas, deve ser negado por permissão. Fixture é conferida antes/depois. | Probe exige flag explícita e sessão A válida. Não há DELETE nem atualização real; demais tabelas/ACLs exigem inventário e ensaios previstos no roteiro. Falta de flag ignora o caso, não aprova. |
| H04 | RPC deve funcionar com a sessão válida; sem sessão, bearer inválido e access JWT da sessão de ensaio após logout devem ser recusados; refresh revogado também. Logout é local à sessão criada pelo teste. | JWT realmente expirado e conta não confirmada não estão disponíveis entre as duas contas confirmadas; permanecem provas adicionais, sem forjar ou criar conta automaticamente. |

Sem autorização/flag/arquivo/variante adequada os casos são condicionais. Nenhum teste ignorado
é aprovação. `SupabaseLiveAuthTest` antigo é bloqueado na variante de homologação
antes de ler arquivo legado, evitando usar a conta do proprietário no projeto de
teste. A workflow de build limpa ambos os caminhos live e nunca dispara H01–H04.

## Verificações locais desta preparação

- `testDebugUnitTest`: 288 casos, **283 aprovados, cinco condicionais, zero falhas**.
- `testHomologacaoUnitTest`: mesma contagem; não somar como 566 testes distintos.
  Oito testes novos de configuração/PKCE incluem bloqueio de origem oficial,
  chave vazia/administrativa/oficial, ID/callback errado, caminho/estado e
  override HTTP local. Os quatro casos hospedados e a sonda live legada não rodaram.
- Compilação debug e instrumentação aprovadas; manifesto de homologação gerado.
- Validador estático confirmou package/provider/callback isolados, sem sharedUserId,
  backup automático ou cleartext. Evidência em
  [ANDROID_HOMOLOGACAO_0220_STATIC.json](evidence/ANDROID_HOMOLOGACAO_0220_STATIC.json).
- Gate Gradle recusou chave ausente, formato administrativo sintético e chave
  pública oficial, sem imprimir entradas. assembleHomologacao com chave vazia
  também foi executado e bloqueou antes da saída APK; nenhum APK de homologação
  foi produzido.
- `HomologationLayoutTest`: **1/1 aprovado no emulador**. AuthForm real dentro da
  identificação de homologação, cena de 320 × 560 dp e LocalDensity/fontScale 1,6;
  ações alcançáveis, alvo de toque de 48 dp e capturas de login/cadastro inspecionadas.
  Esse ensaio de componente não equivale a instalar o APK paralelo nem a configurar
  a fonte do sistema. Os ensaios anteriores com fonte do sistema também permaneceram
  na regressão.
- CI completa **42435902661 aprovada**, código
  `7d00bd0aee80478a5204c64e93ee25385adbb51e`:
  - JVM: 288 casos, **283 aprovados, cinco condicionais, zero falhas**;
  - Android: 424 casos, **413 aprovados, 11 condicionais, zero falhas**;
  - PostgreSQL: **164 cenários aprovados** nas suítes v1/A–E;
  - HTTP local: **70 cenários**, dois emuladores independentes e duas interrupções
    intencionais de processo verificadas; provas anteriores preservadas.
  Os cinco condicionais JVM são H01–H04 e a sonda Auth live legada. Os 11 Android
  são sondas de processo, rede e atualização que dependem de parâmetros/instalações
  específicas; os cenários de rede B–E foram executados separadamente nas provas
  HTTP locais, não inferidos desses casos ignorados na suíte genérica.
  Os testes condicionais não são aprovações; nenhum deles prova o backend hospedado.
  A tarefa de release/atualização estável ficou corretamente ignorada nesta branch.
- Resultados/capturas: artefato
  [resultados-etapa6 — 13600197479](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42435902661/artifacts/13600197479),
  SHA-256 do ZIP informado pelo Actions
  `7ad65baa12abf9e96370c78420f5cb75264e26882648c2eac432e5c4c0f02e7f`.
  Capturas em `build/homologacao-layout.tar`, com arquivos
  `login-320-fonte-1.6.png` e `cadastro-320-fonte-1.6.png`.

Nenhuma homologação real de Auth/RLS/Supabase é inferida dos testes locais.

## Pendências e próxima ação

1. A configuração pública do Actions e o APK estão preparados; verificar que a chave
   foi obtida de noctua-homologacao antes de autenticar. Não enviar seu valor ao chat.
2. Baixar/conferir o APK acima e instalar somente a variante paralela em aparelho de
   teste, mantendo a instalação original intacta.
3. Autorizar/configurar somente o novo callback no projeto de homologação.
4. Fornecer credenciais das duas contas fictícias exclusivamente por arquivo/cofre
   seguro para os ensaios, nunca pelo chat; executar H01–H04 e registrar resultados.
5. Prosseguir para recuperação/sincronização, conflitos e restauração só conforme
   o roteiro [HOMOLOGACAO_SUPABASE_0220.md](HOMOLOGACAO_SUPABASE_0220.md), cujo alvo
   desta etapa é exclusivamente **noctua-homologacao**, não noctua-auth.

Nenhuma instalação, conta, migração hospedada, allowlist, dado real, assinatura
permanente, recurso pago, release ou main foi alterado nesta preparação.

## Acesso de build e CI nesta rodada

- Código de preparação/testes na revisão `7d00bd0aee80`; regressão em
  [42435902661](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42435902661),
  **aprovada**. As execuções 42435656184/42435819454 foram substituídas pelos
  commits seguintes; não representam a revisão final.
- Chave pública não estava no ambiente local. A integração GitHub não permite
  consultar metadados de secrets/variables (HTTP 403); não se afirma que o secret
  remoto está presente ou ausente sem executar o job próprio. Nenhum valor foi lido.
- Disparo direto do arquivo novo homologacao.yml retornou 404, pois ele não está
  registrado no default branch. A entrada suportada preparada usa android.yml
  existente, com homologacao_only=true e ref da branch experimental; não exige merge.
- A tentativa manual [42437875470](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42437875470)
  falhou antes da compilação: setup-android tentou instalar o pacote obsoleto
  `tools`. Corrigido para `packages: platform-tools`, como na regressão existente;
  preparação do SDK e todas as etapas passaram na nova tentativa **42438137774**.
  Essa correção apenas de
  workflow não exige repetir a regressão acadêmica já aprovada.
- A workflow Android tem concorrência por branch: não disparar o build manual
  paralelo enquanto a regressão estiver rodando, para não cancelar sua validação.

## Arquivos alterados nesta preparação

- `.github/workflows/android.yml`
- `.github/workflows/homologacao.yml`
- `app/build.gradle.kts`
- `app/src/androidTest/java/br/com/diariodeclasse/HomologationLayoutTest.kt`
- `app/src/homologacao/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/br/com/diariodeclasse/DiaryApplication.kt`
- `app/src/main/java/br/com/diariodeclasse/auth/AuthEnvironment.kt`
- `app/src/main/java/br/com/diariodeclasse/auth/AuthModels.kt`
- `app/src/main/java/br/com/diariodeclasse/auth/AuthScreen.kt`
- `app/src/main/java/br/com/diariodeclasse/auth/SupabaseAuthClient.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/SupabaseClassSync.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicTransport.kt`
- `app/src/test/java/br/com/diariodeclasse/HomologationConfigTest.kt`
- `app/src/test/java/br/com/diariodeclasse/HomologationHostedAuthTest.kt`
- `app/src/test/java/br/com/diariodeclasse/SupabaseLiveAuthTest.kt`
- `docs/ANDROID_HOMOLOGACAO_0220.md`
- `docs/evidence/ANDROID_HOMOLOGACAO_0220_STATIC.json`
- `docs/evidence/ANDROID_HOMOLOGACAO_0220_APK.json`
- `scripts/ci/validate-android.sh`
- `scripts/validate-homologacao.py`
