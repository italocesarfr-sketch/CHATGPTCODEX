# Fase 3.4 — autenticação, onboarding e provisionamento comercial

Branch dedicada `sync/academic-commercial-onboarding-034`, base
`sync/academic-commercial-final-033` / `2b999cbe9868e580002f67772de217e1f1763fae`.
Preparação iniciada em 21/11/2026; retomada em 22/11/2026, America/Sao_Paulo.
Sem operações hospedadas, contas existentes, dados reais, main, assinatura ou APK oficial.

## Confirmação por código: implementada, hospedagem pendente

O cadastro original usa PKCE S256. O callback exige esquema/host/caminho exatos,
verificador/estado locais, prazo, ausência de fragmento/parâmetros extras. Essa
proteção deliberadamente impede trocar o código do link em outro aparelho.
Um link antigo pode confirmar o e-mail no servidor e falhar no retorno local;
isso não autoriza contornar o verificador. O novo caminho exibe uma confirmação
por código no aplicativo, sem exigir que a caixa de e-mail esteja no mesmo dispositivo.

Usa somente mecanismos oficiais: signup com senha/PKCE, POST `/auth/v1/verify`
com email/token/type=email, GET `/auth/v1/user` para identidade confirmada pelo
servidor e resend type=signup com novo challenge PKCE. Não usa `/otp` com criação
automática de usuários, TokenHash no campo de código, JWT decodificado como identidade,
service_role ou confirmação local falsa.

Após validar o código, **não se admite a sessão OTP no workspace**: ela é encerrada
com logout scope=local e o professor entra com senha. O e-mail confirmado precisa
corresponder ao alvo pendente. Logout durante verificação invalida a operação;
uma resposta tardia não pode recuperar sessão ou trocar de conta. Uma sessão anterior
armazenada, inclusive expirada/offline aguardando revalidação, bloqueia o início
do fluxo OTP e permanece intacta; a confirmação não a encerra ou substitui. Cleanup da
sessão temporária é best-effort, sem persistir seus tokens em disco/logs; falha de
rede pode deixar uma sessão sem uso no serviço até expiração/revogação normal.

A tela mantém os campos/botões Compose e tema existentes, código efêmero sem senha
persistida, loading, erro, reenvio explícito e navegação de volta. A tentativa de
login não confirmado oferece a confirmação por código, inclusive em outra instalação.
O alvo e os timestamps persistem no cofre Keystore (não o código). Metadados locais
reservam cooldown de reenvio de 60s antes da rede, espera entre verificações e pausa
após cinco tentativas. São controles de UX, **não substitutos** dos limites/expiração
obrigatórios do Supabase. Não foi alterado limite algum no servidor.

Aceita 6–10 dígitos ASCII, intervalo do provedor, sem supor que todo projeto usa
seis. O servidor decide validade, uso único e expiração; o app limita a tentativa
local a uma hora e permite solicitar outro código. Erros expõem mensagens genéricas,
nunca respostas privadas. HTTP mantém HTTPS, sem redirects, timeouts e limite de
resposta de 256 KiB. O factory de conexão é test seam para TLS loopback, não uma
URL configurável por Intent ou propriedade de usuário.

AuthSecrets ganhou apenas o metadado opcional signup, com defaults para ler
arquivos antigos. AES-GCM, alias, AAD, arquivos de titularidade, sessões existentes,
Room 14 e backup acadêmico permanecem iguais. **Downgrade para leitor antigo durante
uma confirmação pendente não é homologado**: leitores antigos estritos não conhecem
o novo campo. Concluir/cancelar a confirmação o remove; não limpar arquivos para
resolver erro. Nenhum downgrade/instalação no proprietário foi executado.

### Configuração futura dos e-mails, sem aplicação nesta fase

1. Confirmar no painel o projeto **noctua-homologacao / sscexzxtqdcsqdlcytum**.
2. Revisar/copiar o template atual antes de alterar. Somente após autorização,
   Authentication → Email Templates → Confirm signup: usar o arquivo preparado
   `supabase/templates/confirm_signup_otp.html`, com `{{ .Token }}`.
3. Não alterar Reset password, confirmação obrigatória, CAPTCHA, limites de
   tentativas ou OTP expiry para fazer o teste passar. A recuperação permanece PKCE.
4. Conferir a configuração real de tamanho/prazo do OTP, envio de e-mail/SMTP e
   limites Free. Não presumir entrega a qualquer destinatário externo.
5. Preservar o callback homologação `br.com.diariodeclasse.homologacao://auth/callback`
   e suas validações para os fluxos de link. Produção não recebe alterações.

O template foi preparado, não enviado ao Supabase. O novo APK só poderá provar a
entrega OTP real após configuração autorizada e teste com conta fictícia inédita.
Referências oficiais consultadas: [Email templates](https://supabase.com/docs/guides/auth/auth-email-templates),
[verificação OTP Kotlin](https://supabase.com/docs/reference/kotlin/auth-verifyotp),
[resend](https://supabase.com/docs/reference/javascript/auth-resend),
[rate limits](https://supabase.com/docs/guides/auth/rate-limits),
[código oficial GoTrue](https://github.com/supabase/auth/blob/master/internal/api/verify.go)
e [limites configuráveis OTP](https://github.com/supabase/auth/blob/master/internal/conf/configuration.go).

## Launcher — análise e prova Android

Manifest declara MAIN/LAUNCHER, MainActivity exportada, singleTop, ícones normal e
round e callback separado. Não se presume defeito só porque instruções anteriores
usaram ADB. O ensaio novo tocará o ícone real no launcher de AVD próprio, com o
Application normal e rede bloqueada antes de instalar. Não usa instrumentação,
monkey ou componente hardcoded para produzir a abertura. Validação pendente de CI;
nenhuma correção de manifest se justifica antes da reprodução.

A CI 45067806104 parou na automação da gaveta: a captura mostrou o launcher AOSP
clássico na Home, antes de qualquer abertura do app. O executor procurava somente
um subconjunto dos nomes do botão e usou um gesto incompatível com esse launcher.
A correção `54e3da2` reconhece `all_apps_handle` e variações semânticas; o fallback
é a tecla pública Android ALL_APPS, jamais o componente do app. O toque que abre
o NOcTuA continua sendo no ícone encontrado pela árvore real de acessibilidade.
São preservados XML/captura do launcher vazio para diagnóstico. Isso não comprova
um defeito no Manifest do produto e nenhuma alteração nele foi feita.

## Provisionamento institucional mínimo — migração preparada

A autorização é separada em cinco responsabilidades:

1. Auth cadastra usuário/senha; não concede licença nem vínculos institucionais.
2. Confirmação do e-mail prova posse da caixa; não comprova vínculo de trabalho.
3. Administração confiável pré-autoriza o e-mail exato em roster protegido, com
   referência institucional e prazo de primeiro resgate; não há regra por domínio.
4. Servidor deriva UUID e e-mail de Auth confirmado, cria direito comercial e um
   vínculo de proveniência imutável. Nunca aceita papel/instituição por user_metadata.
5. O inicializador E existente conclui o protocolo transacional/idempotente. App
   não tem menu institucional nem configuração A–E.

Arquivo preparado: `supabase/migrations/202611220001_institutional_provisioning.sql`.
Exige a migração comercial original 202611170001, mesma role proprietária da função,
corpo original revisado e A–E sem H13/PT409. Acrescenta roster administrativo e
claims de proveniência, ambos com RLS/deny-all e sem ACL de leitura/escrita dos
clientes. Helper privado sem EXECUTE comum; assinatura/ACL da única RPC existente
`noctua_ensure_academic_e()` não muda. Seu corpo permanece byte a byte igual salvo
a chamada explícita ao provisionador privado.

Não é um sistema multi-institucional completo. institution_reference é uma
referência opaca de proveniência; não concede acesso aos alunos/notas de outros
professores, papéis de gestor ou compartilhamento. Não há portal, convites por
link, gestão financeira, domínio institucional ou serviço administrativo no APK.

Rollout e roster ativo explícito são necessários. Contas manuais já existentes,
inclusive desabilitadas, não são reassociadas/reativadas por roster. Qualquer inscrição
de testes, dado v1, estado v2 parcial ou identidade legada continua bloqueando a
admissão automática. A autorização tentada e o head vazio são desfeitos se o guard
original recusar elegibilidade. O mesmo lock de v1 protege a corrida com escritores
antigos. Concessão, claim e E compartilham a transação; resposta perdida repete o
mesmo resultado. Claims confirmados não podem ser alterados/excluídos por operações
normais; não se reciclam e-mails/UUIDs para recuperar contas.

O prazo do roster é **prazo de primeiro resgate**, não licença/subscrição com
expiração automática. Revogar novas admissões não apaga dados/direitos existentes:
a administração deve desabilitar o direito comercial específico pelo mecanismo
existente, mediante autorização própria. Alterar associação de conta existente
exige outro procedimento auditado; não é feito implicitamente nesta entrega.

A migração não insere roster ou direitos e **não foi aplicada em ambiente hospedado**.
Localmente, 18 casos PostgreSQL passaram: e-mail exato, não autorizado, expirado,
rollout off, sessão inválida, privilégios, v1/parcial, replay, concorrência,
rollback, preservação manual, imutabilidade e fraude de metadata/identidade.
O procedimento preparado de implantação e contingência está ao final; sua execução hospedada continua bloqueada por autorização.

## Auditoria dos quatro tipos acadêmicos

| Tipo | Quando deve existir | O que não produz o tipo |
| --- | --- | --- |
| class_archives | `DiaryRepository.archiveClass(id,true)` grava marcador de arquivamento; reativar remove o marcador | Apenas editar/visualizar turma |
| preserved_histories | Exclusão validada de turma cujo histórico continua necessário a remanejamentos/vínculos preservados | Toda exclusão comum, simples arquivamento ou exportação |
| export_rows | Personalização persistida de posições, espaços ou ocultações da planilha | Gerar arquivo XLSX/CSV com disposição padrão calculada |
| import_drafts | Importação/colar lista ainda em conferência; confirmar ou descartar remove os rascunhos | Cadastro direto de aluno ou importação já concluída |

Todos já pertencem ao contrato de quinze tipos e aos triggers/outbox. O acervo
local 3.3 recuperou um arquivo de turma, um histórico, três linhas de disposição e
um rascunho. A ausência no inventário hospedado relatado não prova defeito: não há
registro acadêmico remoto de tipo que não tenha sido materializado/persistido.
Novos ensaios usam os comandos reais do repositório, conferem outbox e provam que
exportação de arquivos não cria linhas acadêmicas nem envia arquivos locais.
Nenhuma correção de sincronização foi comprovada como necessária neste checkpoint.

## Recuperação e evidências desta retomada — checkpoint de 22/11/2026

A branch 3.4 e as alterações não commitadas estavam disponíveis. Foram preservados
os cinco arquivos de Auth, os testes OTP/Compose, a migração institucional e os
preparativos de auditoria/CI. HEAD inicial ainda era a base 2b999cb; não houve reset,
limpeza ou reconstrução das fases anteriores. A retomada acrescentou proteção contra
substituição de sessão armazenada que aguarda revalidação, regressão de reutilização
do código e conferência de logout temporário scope=local no transporte TLS.

Commits por responsabilidade:

- `32dbd36`: Auth/OTP, UI e template preparado.
- `d4c89f7`: provisionamento institucional privado e 18 testes PostgreSQL.
- `5838b71`: captura das quatro entidades auxiliares por operações reais do repository.
- `ab21cf7`: launcher pelo ícone e ampliação da CI comercial já existente.
- `01acb32`: prepara fixture vazio exclusivo do job antes da bateria de migração; nenhum CHECK foi relaxado.
- `54e3da2`: reconhece a gaveta do launcher AOSP clássico na prova real por ícone.

Resultados efetivamente concluídos no workspace:

| Verificação | Resultado |
| --- | --- |
| JVM `testDebugUnitTest` | 337 casos: 332 aprovados, 5 condicionais/ignorados, 0 falhas/erros |
| OTP isolado, incluído no total JVM | 11 de controller/policy + 4 TLS loopback, aprovados |
| Compilação | assembleDebug e assembleDebugAndroidTest aprovados, configuração comercial padrão desligada |
| Provisionamento PostgreSQL 17.11 descartável | 18/18 aprovados; última rodada 5,387 s |
| Transporte PostgREST 12.2.3 local | 43 verificações aprovadas, 171 registros/15 tipos, sessão expirada/revogada/inválida e acesso cruzado |
| Preparação segura de homologação | 8/8 verificações estáticas aprovadas |
| YAML/Python/shell/diff | três workflows parseados; py_compile, bash -n e git diff --check aprovados |

A primeira tentativa manual HTTP foi feita antes do cache de esquema PostgREST
ficar pronto e recebeu 503. O log confirmou a inicialização do cache logo depois;
a rodada após readiness passou integralmente. Não foi falha acadêmica e não houve
alteração de timeout/retry do produto. A primeira reconstrução SQL do fixture sobre
um banco descartável ainda populado também foi interrompida por CHECK de escopo;
o banco exclusivo desta tarefa foi recriado vazio, sem qualquer banco do usuário.

A primeira [CI 45067489560](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45067489560)
parou antes do Android: a bateria anterior deixou seu fixture em E e a nova bateria
de migração tentou reaplicar A–D nele. O artefato confirmou a falha no CHECK de escopo,
sem executar a migração institucional. A correção 01acb32 recria apenas o banco do
serviço PostgreSQL efêmero do próprio job, com guardas CI/loopback/porta/nome; preserva
as evidências da bateria anterior e testa a migração sobre fixture vazio.

CI completa em andamento no código `54e3da222e5d78a67c4d530249866d959b3c5130`:
[execução 45068761680](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45068761680).
Os resultados Android/launcher ainda não estão aprovados neste checkpoint. Não
confundir TLS mock/OTP simulado com cadastro/verificação real em GoTrue. As cinco
condicionais JVM não contam como aprovadas. Nenhuma autenticação hospedada foi feita.

### Preparação operacional do provisionamento (não executada)

- Inspeção preparada: `supabase/checks/onboarding_034_readonly.sql`, somente
  catálogo/configuração. Antes da migração devem existir as duas tabelas comerciais
  originais e nenhuma das duas novas. O inicializador precisa ter o corpo original
  e a role do SQL Editor precisa ser sua proprietária; a migração aborta ante drift,
  H13/PT409, objeto parcial ou role diferente. Não reaplicar migrações anteriores.
- Backup administrativo verificável do projeto separado, inventário/RLS aprovado e
  autorização explícita são portões anteriores à execução. O arquivo novo contém
  uma transação única e lock_timeout local de 5s, sem ampliar statement_timeout.
- Após autorização futura, executar **só** 202611220001 inteiro, uma sessão. Erro ou
  resultado incerto: não repetir; inspecionar catálogo para distinguir rollback de
  commit sem resposta. Não fazer DROP/reparos automáticos para passar o preflight.
- Pós-aplicação: esperado total de vinte tabelas acadêmicas/controle com RLS
  (dezesseis A–E/v1 + duas comerciais + duas de provisionamento). As novas tabelas
  são deny-all e os novos helpers não têm EXECUTE autenticado/anônimo. A RPC antiga
  mantém a ACL. Não conceder own_rows/SELECT nas tabelas administrativas.
- A migração não preenche o roster. Mediante **outra** autorização, administrador
  pode inserir uma linha de e-mail fictício inédito, referência institucional opaca,
  enabled=true e prazo futuro de resgate. Modelo para revisão: `INSERT INTO
  public.noctua_commercial_roster(email,institution_reference,enabled,expires_at)
  VALUES ('<e-mail fictício canônico aprovado>','<referência administrativa>',true,
  now()+interval '7 days');`. Confirmar uma linha, nenhum direito prévio, ausência
  de allowlist/v1/v2 parcial; não usar UPSERT, domínio, wildcard ou dados reais.
- O rollout global existente também precisa estar autorizado/ativo. Não alterá-lo
  incidentalmente: seus efeitos sobre todos os direitos atuais exigem revisão
  separada. A homologação existente não é reconfigurada nesta tarefa.
- O Android do ensaio descartável passa a ser provisionado por esse roster, sem
  INSERT manual de entitlement; a prova final exige exatamente um claim, vínculo
  correto e inicialização E. B permanece sem direito. Isso não é uma conta hospedada.
- Contingência administrativa futura: desabilitar o roster bloqueia novas admissões;
  para direito já concedido, desabilitar o entitlement específico preservando dados,
  claim, filas e titularidade. Essas alterações exigem autorização própria. Não
  remover auditorias nem restaurar banco sobre dados ativos como rollback casual.

## Arquivos e comandos de validação

Auth: `AuthModels.kt`, `AuthVault.kt`, `AuthController.kt`, `SupabaseAuthClient.kt`
e `AuthScreen.kt`. Testes: `SignupOtpTest.kt`, `SignupOtpHttpsTest.kt`,
`SignupConfirmationUiTest.kt` e `AcademicAuxiliaryCaptureTest.kt`. Preparação do
servidor: nova migração institucional, check somente leitura e template OTP.
Integração CI: workflows `commercial-auto-local.yml`, `android.yml`, `homologacao.yml`,
helpers `test-commercial-android.sh`/`test-launcher-icon.py` e os scripts de fixture,
evidência e teste de provisionamento. Nenhuma entidade/schema Room, DAO, regra
acadêmica ou RPC A–E foi reescrita. A migração comercial original continua com
SHA-256 `1d5c48c0b55a24edbae948407a8775030490338c8a3b0c6a64b2a98e5bf051e2`.
Nova migração preparada (6.729 bytes):
`934bc08a8448cea5b6e73c4cef488472940838195420236a3aeaae283ea341aa`.

Comandos locais (SDK/JDK instalados no workspace; serviços somente loopback):

```sh
./gradlew --offline --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest
PGHOST=127.0.0.1 PGPORT=55431 PGDATABASE=noctua_phase_e_test NOCTUA_COMMERCIAL_DISPOSABLE=YES python3 scripts/test-commercial-provisioning.py
PGHOST=127.0.0.1 PGPORT=55431 PGDATABASE=noctua_phase_e_test NOCTUA_COMMERCIAL_DISPOSABLE=YES NOCTUA_ONBOARDING_LOCAL=YES python3 scripts/test-commercial-http.py --prepare
PGHOST=127.0.0.1 PGPORT=55431 PGDATABASE=noctua_phase_e_test NOCTUA_COMMERCIAL_DISPOSABLE=YES python3 scripts/test-commercial-http.py
python3 scripts/test-commercial-homologation-preparation.py
bash -n scripts/ci/test-commercial-android.sh
```

São fixtures SQL locais independentes de GoTrue hospedado. As cinco condicionais
JVM são H01–H04 e SupabaseLiveAuthTest, deliberadamente sem autorização/credenciais
nesta tarefa; não foram executadas nem aprovadas novamente.

## Validação manual mínima futura (não executada)

1. Após autorização e revisão do template em **homologação**, usar uma conta
   fictícia inédita: cadastrar, consultar o e-mail em outro aparelho, tentar um
   código inválido, confirmar com o código correto e entrar com senha. Não enviar
   códigos, links ou senhas pelo chat. Conferir que a confirmação, sozinha, não abre
   workspace. Expiração/uso único/entrega precisam ser comprovados no GoTrue real.
2. Após autorização administrativa separada para a nova migração e **uma** linha
   de roster, entrar com essa conta num AVD novo. Verificar inicialização E sem menus
   e recuperação no segundo AVD novo. Não alterar contas comerciais já homologadas;
   os oito cenários acadêmicos manuais das fases anteriores não precisam ser refeitos.

Os testes locais não autorizam produção ou concessão comercial. Migração, template,
roster/rollout e uso de conta hospedada continuam pendentes de autorização própria.
