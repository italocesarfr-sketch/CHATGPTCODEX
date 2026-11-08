# 0.22.0 — Variante Android paralela de homologação

Branch `sync/academic-complete-0220`. Base de publicação SQL `e71405e`;
implementação Android `69e86e60bce21e07ab5d77af8fe479e1539bdec5`, capturas/teste
nativo `74d8ca4`, entrada manual na workflow existente `7d00bd0`.
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

Nesta rodada local a variável ainda não estava disponível: as classes/testes e o
manifesto foram compilados, mas **não existe APK de homologação válido nem SHA-256
a entregar**. O bloqueio não foi contornado com chave fictícia. Uma futura chave
configurada exige nova compilação e validação do APK, não só alteração do relatório.

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

## Instalação paralela — somente depois de gerar APK válido

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

`HomologationHostedAuthTest` é opt-in e só roda na variante de homologação. Precisa
da chave pública válida e do caminho privado `NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE`.
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
NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE=/caminho/privado/contas-de-teste.json \
./gradlew testHomologacaoUnitTest --tests '*HomologationHostedAuthTest'
```

| Caso | Preparado no runner | O que permanece necessário |
|---|---|---|
| H01 | Login, confirmação/UUID, consulta de identidade, refresh, senha incorreta e logout de ambas as contas comuns. | Chave, arquivo privado e autorização para criar/encerrar essas sessões de ensaio. Entrega/confirmação de e-mail depende do serviço configurado. |
| H02 | A precisa ler a fixture conhecida; B autenticada não pode ler a mesma linha privada de A, mesmo conhecendo IDs. | Turma fictícia A presente; mudança de conta/isolamento Room no Android e provas de escrita cruzada serão conferidas no roteiro hospedado completo. Ausência de fixture falha, não prova isolamento. |
| H03 | Helper privado recusado; PATCH direto com predicado contraditório (revisão positiva conhecida E revisão<0), impossível de afetar linhas, deve ser negado por permissão. Fixture é conferida antes/depois. | Probe exige flag explícita e sessão A válida. Não há DELETE nem atualização real; demais tabelas/ACLs exigem inventário e ensaios previstos no roteiro. Falta de flag ignora o caso, não aprova. |
| H04 | RPC sem sessão, bearer inválido e access JWT da sessão de ensaio após logout devem ser recusados; refresh revogado também. Logout é local à sessão criada pelo teste. | JWT realmente expirado e conta não confirmada não estão disponíveis entre as duas contas confirmadas; permanecem provas adicionais, sem forjar ou criar conta automaticamente. |

Sem flag/arquivo/variante adequada os casos são condicionais. Nenhum teste ignorado
é aprovação. `SupabaseLiveAuthTest` antigo é bloqueado na variante de homologação
antes de ler arquivo legado, evitando usar a conta do proprietário no projeto de
teste. A workflow de build limpa ambos os caminhos live e nunca dispara H01–H04.

## Verificações locais desta preparação

- `testDebugUnitTest`: 288 casos, **283 aprovados, cinco condicionais, zero falhas**.
- `testHomologacaoUnitTest`: mesma contagem; não somar como 566 testes distintos.
  Oito testes novos de configuração/PKCE incluem bloqueio de origem oficial,
  chave vazia/administrativa/oficial, ID/callback errado, porta/caminho/estado e
  override HTTP local. Os quatro casos hospedados e a sonda live legada não rodaram.
- Compilação debug e instrumentação aprovadas; manifesto de homologação gerado.
- Validador estático confirmou package/provider/callback isolados, sem sharedUserId,
  backup automático ou cleartext. Evidência em
  [ANDROID_HOMOLOGACAO_0220_STATIC.json](evidence/ANDROID_HOMOLOGACAO_0220_STATIC.json).
- Gate Gradle recusou chave ausente, formato administrativo sintético e chave
  pública oficial, sem imprimir entradas; nenhum APK de homologação foi produzido.
- `HomologationLayoutTest` compilado: layout real AuthForm dentro da identificação
  de homologação, largura 320 dp/fonte 1,6, controles alcançáveis e capturas preparadas.
- Regressão Android completa em CI após os commits de implementação/teste: resultado
  ainda em consolidação. Renderização/capturas e testes instrumentados não são
  declarados aprovados somente pela compilação.

Estado da CI e demais resultados serão consolidados após a execução. Nenhuma
homologação real de Auth/RLS/Supabase é inferida dos testes locais.

## Pendências e próxima ação

1. Disponibilizar a chave pública da homologação no ambiente de build escolhido.
2. Gerar/validar o APK paralelo e registrar SHA-256; depois instalar em aparelho de
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
  em andamento ao registrar este progresso. As execuções 42435656184/42435819454
  foram substituídas pelos commits seguintes; não representam a revisão final.
- Chave pública não estava no ambiente local. A integração GitHub não permite
  consultar metadados de secrets/variables (HTTP 403); não se afirma que o secret
  remoto está presente ou ausente sem executar o job próprio. Nenhum valor foi lido.
- Disparo direto do arquivo novo homologacao.yml retornou 404, pois ele não está
  registrado no default branch. A entrada suportada preparada usa android.yml
  existente, com homologacao_only=true e ref da branch experimental; não exige merge.
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
- `scripts/ci/validate-android.sh`
- `scripts/validate-homologacao.py`
