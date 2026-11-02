# 0.22.0 — fase B: integração Android

Base `1a366ffd68d3`, mesma branch `sync/academic-complete-0220`.

## Plano de implementação

1. Migração Room 11→12 aditiva, apenas metadados `sync_academic_*`. Gatilhos
   nas três tabelas capturam inclusão/alteração/exclusão na transação acadêmica.
   Pedidos preparados conservam UUID, corpo e contadores até recibo confirmado.
2. Transporte HTTPS para as duas RPCs existentes e negociação de protocolo.
   Nova migração SQL protege v1/v2 sob o mesmo bloqueio por proprietário, compara
   o inventário v1 e impede novos escritores antigos depois de transição explícita.
3. Recebimento por lote inteiro, revisão da fase A, validação do grafo completo e
   regras de identidade da matrícula/ano/número. Não restaurar banco inteiro.
   Lote incompatível permanece em inbox com cursor parado e variantes preservadas.
4. Coordenador único por workspace: alterações locais, foreground, reconexão e
   JobScheduler persistente. Retry transitório com backoff e sem reiniciar app.
5. Controle experimental separado. Não ativar contas legadas com dados reais,
   não apagar conflitos v1, não remover proteção de backup nem publicar release.
6. Evidências: testes Room e migração, testes PostgreSQL de barreira/isolamento,
   transporte Android com PostgREST/PostgreSQL reais em ambiente descartável,
   recuperação depois de limpeza **somente do emulador sintético do CI**, regressão.

O endpoint de produção continua fixo HTTPS/Supabase e chave pública. Um endpoint
HTTP é permitido exclusivamente no debug para o host fixo 10.0.2.2 do emulador,
com JWTs fictícios assinados para o PostgREST descartável. Isso não é validação do
Supabase hospedado nem confirmação de duas sessões Supabase reais.

## Ordem futura de SQL hospedado (não executada automaticamente)

Já instalados: `202610300001_class_sync.sql`, depois
`202610310001_sync_session_guard.sql`. Após revisão/aprovação, aplicar somente os
novos arquivos em ordem: `202611010001_academic_protocol.sql`, seguido da migração
da fase B. Nunca reaplicar v1/guard sobre as funções protegidas mais recentes.

## Implementação

Desenvolvimento identificado como **0.22.0-dev-b / versionCode 41**, sem publicação
de release estável. A configuração da assinatura permanente não foi modificada.

Room **v12**, migração **11→12**, apenas cinco tabelas de metadados:

| Tabela | Uso |
|---|---|
| sync_academic_control | Proprietário, consentimento/transição, pausa, cursor e estado |
| sync_academic_records | Chave tipada, contador local, contador confirmado, revisão/base remota |
| sync_academic_outbox | UUID, corpo imutável, versões enviadas, tentativas, conflito |
| sync_academic_inbox | Lote bloqueado integral, motivo e sequência não aplicada |
| sync_academic_applied | Operação e digest do lote aplicado, para respostas duplicadas |

Nenhuma tabela acadêmica da v11 foi reescrita. O backup permanece formato lógico
12 e envelope criptográfico anterior; metadados de sincronização não são dados
escolares e não são exportados no backup. Uma restauração pausa os dois protocolos,
preserva outbox/conflitos e bloqueia retomada automática até reconciliação futura.

### Fila e transporte

Os gatilhos SQLite de INSERT/UPDATE/DELETE de `classes`, `terms` e `students`
atualizam os contadores dentro da mesma transação da escrita escolar. Um encerramento
entre a escrita e a preparação HTTP não perde a pendência. A preparação lê um estado
consistente, calcula somente o delta e grava o UUID/corpo antes de enviar. Um pedido
preparado nunca é substituído silenciosamente por uma edição posterior.

`SupabaseAcademicSync` usa as RPCs reais `noctua_push_academic` e
`noctua_pull_academic`, HTTPS, a chave pública existente e o bearer da sessão
confirmada da conta do workspace. Não há senha/token no Room ou nos logs. O motor
confere operação, sequência, revisões e recibos antes de reconhecer cada versão
enviada. Edições mais novas continuam pendentes.

O recebimento começa antes do envio em uma instalação recém-ativada. Cada página
é um lote atômico: campos obrigatórios/tipos, chaves, identidade, ano, quatro
bimestres, números de chamada e grafo acadêmico são verificados antes de upserts
pontuais. Não há restauração indiscriminada nem geração de IDs na recuperação.
Uma falha desfaz a transação e guarda o lote na inbox em outra transação; o cursor
não avança. Um recibo local verifica a repetição de uma resposta já aplicada.

### Revisão de mudanças

A fase B integra `AcademicMerge` da fase A ao caminho de recebimento. Uma alteração
remota somente do nome de turma pode atualizar a linha sem substituir os alunos,
notas, frequências ou diários locais. Alterações simultâneas incompatíveis conservam
base/local/remoto; pedidos já preparados não são rebaseados enquanto o resultado
do envio não for conhecido. Lotes de entidades posteriores à B são retidos inteiros.
Matrículas remanejadas/vinculadas exigem a cadeia da fase D e não são enviadas como
cadastros isolados. Nada resolve automaticamente o conflito legado da homologação.

### Execução automática

Um coordenador por workspace reúne alterações locais, retorno da conectividade,
abertura/foreground e consulta a cada minuto enquanto o app está visível. Um mutex
compartilhado também serializa o JobScheduler persistente, usado como fallback.
Falhas transitórias têm retry progressivo; é desnecessário reiniciar o aplicativo
para retomar uma queda de conexão. Logout cancela observadores/callbacks antes de
fechar o Room e mantém o banco acadêmico da conta.

## Barreira v1/v2 e acesso experimental

A migração `202611020001_phase_b_fence.sql` cria duas tabelas com RLS desde o início:
`noctua_sync_protocols` e `noctua_academic_test_accounts`. Os usuários comuns só
podem consultar suas linhas; não podem autorizar a própria conta nem mudar o protocolo.

`noctua_academic_status` consulta protocolo e inventário v1. A transição explícita
`noctua_activate_academic` compara sequência e inventário sob o mesmo lock utilizado
pelos escritores v1. Ela semeia classes/bimestres existentes, preservando IDs e
versões v1, e grava a barreira no mesmo commit. Inventário desatualizado ou dados
acadêmicos v2 de experimentos não reconciliados bloqueiam a transição.

Após o commit, os pontos de entrada v1 rejeitam novos envios/leituras dessa conta.
Seus helpers antigos e o helper v2 sem barreira não são executáveis por anon ou
authenticated. Contas não migradas continuam usando v1. As duas RPCs acadêmicas
exigem conta de teste autorizada, sessão válida e protocolo 2; a escrita é restrita
às três entidades da fase B e preserva ano/identidade/número oficial.

**A migração não autoriza nenhuma conta.** O acesso Android está restrito ao debug,
workspace não legado e consentimento de uso de dados fictícios. A identidade
proprietária legada fixada no Auth permanece inelegível para esse controle. Assim,
esta fase não migra automaticamente a conta do proprietário ou seus dados reais.

## SQL hospedado ainda pendente

Após aprovação para homologação em contas autorizadas e sem dados reais:

1. Conferir as migrações já instaladas `202610300001_class_sync.sql` e
   `202610310001_sync_session_guard.sql`, sem executá-las novamente.
2. Aplicar `202611010001_academic_protocol.sql` uma única vez.
3. Aplicar `202611020001_phase_b_fence.sql` em seguida.
4. Autorizar somente UUIDs de contas de teste previamente aprovadas, pelo SQL Editor
   administrativo, por exemplo (substituir o placeholder, nunca usar os UUIDs fictícios
   dos testes locais no projeto real):

```sql
insert into public.noctua_academic_test_accounts(owner_id, allowed)
values ('UUID_DA_CONTA_DE_TESTE_APROVADA'::uuid, true)
on conflict(owner_id) do update set allowed = excluded.allowed;
```

Não reaplicar **nenhuma migração anterior**, inclusive a fase A, por cima da barreira
B: isso substituiria pontos de entrada protegidos. Usar controle de versões/migrações,
nunca copiar scripts de bootstrap de teste no projeto hospedado. Os scripts Python
de teste exigem PostgreSQL descartável local e nunca devem ser usados no Supabase.

Não foram executadas migrações hospedadas, criadas contas reais nem solicitadas
senhas/tokens. SMTP/domínio pago não são necessários para esse transporte.

## Roteiro de homologação seguinte

Usar duas instalações limpas em emuladores/aparelhos de teste, com conta autorizada
e exclusivamente dados fictícios. Não instalar debug sobre o APK do proprietário,
não apagar seus dados e não restaurar backups sobre eles. Depois dos SQLs revisados,
entrar com a mesma conta, autorizar o teste uma vez, cadastrar turma e alunos,
desconectar/reconectar, recuperar na segunda instalação e editar de volta. Comparar
IDs e conferir que lançamentos locais não abrangidos não são alterados.

Uma segunda conta comum autorizada deve testar consultas diretas e RPCs. JWTs de
fixtures locais não substituem essa prova. Não compartilhar credenciais no chat.

## Limites para C, D e E

- C: atividades, notas, recuperação/recomposição, aulas, P/F/FJ/NR e motivos.
- D: diário, remanejamentos/correções, históricos preservados, arquivamento, horários,
  rascunhos e disposição da exportação. Os dados já existentes continuam locais.
- E: bootstrap maior que 256 linhas/1 MiB, resolução administrativa de conflitos,
  reconciliação após restauração e migração consentida de contas reais. Manter os
  backups e não anunciar recuperação acadêmica completa.
- Atualização assinada sobre a 0.21.0/c40 e homologação Supabase com duas contas
  reais autorizadas continuam requisitos de liberação, não aprovações implícitas.

## Validação

**Validada no ambiente automatizado local**, sem homologação hospedada/física.
Workflow [41382889138](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41382889138),
SHA `78aff91aa9d10ef27a2fd49523b4b9094c4fc0fa`. Os dois jobs de validação
passaram; o job de candidato release foi corretamente ignorado nesta branch.

| Evidência conferida no artefato | Resultado |
|---|---|
| JVM XML | 245 casos: 244 aprovados, 1 condicional ignorado, zero falhas |
| Android XML completo | 371 casos: 363 aprovados, 8 condicionais ignorados, zero falhas |
| Room B, execução focada | 12/12 aprovados |
| PostgreSQL protocolo A | 38/38 aprovados |
| PostgreSQL B, barreira/RLS/concorrência | 18/18 aprovados |
| HTTP/PostgREST local, sondas de segurança | 9/9 aprovadas |
| HTTP Android em dois AVDs independentes | 10/10 fases aprovadas |
| Painel compacto, fontes reais 1,3 e 1,6 | aprovado, capturas conferidas |
| Atualização da referência com chave descartável | aprovada; não é certificado permanente |
| Exportadores XLSX/CSV/TSV e leitores independentes | aprovados no workflow |

O teste JVM ignorado é `SupabaseLiveAuthTest`, sem sessão real disponível. Os oito
casos Android condicionais são os probes de processo/atualização e rede: AuthProcess,
AuthSignedUpgrade, FrequencyProcess, PhaseBNetwork, ReleaseContinuity, StableUpgrade,
SyncProcess e UpgradeProbe. Alguns rodam separadamente no script (processo, referência
e as dez fases HTTP); os de release permanente não rodaram nesta branch. Não contar
as execuções focadas novamente como testes únicos da regressão.

Os dois AVDs comprovaram recuperação de 36 matrículas e quatro bimestres com IDs
exatos, resposta perdida/reenvio idempotente após reinício, alteração offline com
reconexão no mesmo processo, edição inversa de turma/aluno, preservação de nota zero,
FJ/motivo e diário locais, e conflito com variantes/cursor preservados. Papéis SQL e
JWTs assinados são **fixtures locais**, não duas contas Supabase reais.

[Resultados, XML e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41382889138/artifacts/13113899859).
Capturas reais: `phaseb-layout.tar` (fontes ampliadas),
`phaseb-network/primary-captures.tar` e `secondary-captures.tar` (sincronizado,
reconectado, recuperação e conflito). Revisão visual confirmou ações legíveis e
alcançáveis, superfície opaca e escopo parcial explícito.

O APK debug do mesmo SHA existe no artefato `noctua-apk-teste`; não é instalável
sobre o APK permanente do proprietário e não deve ser usado para isso. Não foi
gerada release estável. A atualização assinada original 0.21.0/c40 continua pendente.

### Auditoria final B

A captura está na transação SQLite; o pedido congelado persiste antes de HTTP e só
sai após recibo verificado ou recepção da operação confirmada. O recebimento inteiro,
recibo e cursor são uma única transação. Bloqueios mantêm inbox e cursor anterior.
IDs de pessoa/matrícula são separados; tipos/campos obrigatórios impedem geração de
identidade na desserialização. A revisão não substitui o banco nem toca entidades
fora do escopo. A migração 11→12 adiciona apenas os cinco metadados; backup lógico
12 e criptografia permanecem iguais. Restauração pausa e exige reconciliação futura.

O rótulo Sincronizado exige ausência de contadores pendentes, outbox, inbox e erros,
com consulta concluída; a própria tela explicita quais dados não estão cobertos.
A barreira SQL serializa v1/transição/v2 por conta, não permite escritores antigos
e mantém versões/recibos. O conflito legado impede a transição e não é apagado.
Build release, conta legada e UUID do proprietário não habilitam o teste B.
Nenhum bloqueio crítico adicional identificado nesta revisão; os limites de bootstrap,
movimentações não recuperáveis e reconciliação continuam restrições documentadas.

Commits: `b22ecba49dd0` (integração), `6f41f9e99652` (consulta foreground),
`c0222152a5a0` (runner legado), `8a6c3a401cec` (identidades obrigatórias),
`0f17e4515e78` (versão/atualização) e `78aff91aa9d1` (import da migração).

## Arquivos alterados nesta fase

Lista relativa à entrega A `1a366ffd68d3`; os testes antigos receberam apenas
a inclusão da migração aditiva/expectativas de versão, preservando as verificações.

```text
.github/workflows/android.yml
README.md
app/build.gradle.kts
app/schemas/br.com.diariodeclasse.data.DiaryDatabase/12.json
app/src/androidTest/java/br/com/diariodeclasse/AuthTestRunner.kt
app/src/androidTest/java/br/com/diariodeclasse/BackupInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/ClassLifecycleInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/ClassificationsInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/ExportLayoutInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/FunctionalFifteenInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/HistoryDeletionInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/LessonRecordsInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/PhaseBNetworkHarness.kt
app/src/androidTest/java/br/com/diariodeclasse/PhaseBNetworkProbeTest.kt
app/src/androidTest/java/br/com/diariodeclasse/PhaseBPanelLayoutTest.kt
app/src/androidTest/java/br/com/diariodeclasse/PhaseBRoomTest.kt
app/src/androidTest/java/br/com/diariodeclasse/RecompositionInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/RelocationConversionInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/RelocationInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/ReportsConsolidationInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/SchemaBaselineInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/TimetableInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/UpgradeProbeTest.kt
app/src/debug/AndroidManifest.xml
app/src/debug/res/xml/debug_network_security.xml
app/src/main/java/br/com/diariodeclasse/DiaryApplication.kt
app/src/main/java/br/com/diariodeclasse/MainActivity.kt
app/src/main/java/br/com/diariodeclasse/auth/AccountWorkspace.kt
app/src/main/java/br/com/diariodeclasse/backup/BackupManager.kt
app/src/main/java/br/com/diariodeclasse/data/Database.kt
app/src/main/java/br/com/diariodeclasse/data/DiaryMigrations.kt
app/src/main/java/br/com/diariodeclasse/sync/ClassSyncStore.kt
app/src/main/java/br/com/diariodeclasse/sync/SyncJobs.kt
app/src/main/java/br/com/diariodeclasse/sync/SyncPanel.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicDatabase.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicEngine.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicPanel.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicRuntime.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicStore.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicTransport.kt
app/src/test/java/br/com/diariodeclasse/PhaseBStatusTest.kt
docs/FASE_B_0220.md
docs/PLANO_SINCRONIZACAO_0220.md
docs/SINCRONIZACAO_ACADEMICA_0220.md
scripts/ci/start-phase-b-postgrest.sh
scripts/ci/test-phase-b-network.sh
scripts/ci/validate-android.sh
scripts/phase-b-http-fixture.py
scripts/test-academic-postgres.py
scripts/test-phase-b-http.py
scripts/test-phase-b-postgres.py
scripts/validate-auth-apk.py
supabase/migrations/202611020001_phase_b_fence.sql
```

## Revisão compartilhada durante a fase C

A revisão posterior encontrou dois defeitos conservadores no store/engine comum:
novas tentativas sobre conflito podiam deixar o spinner ativo, e diferenças de
notação numérica JSONB podiam gerar falso conflito. Corrigidos em `36f86f1` e
`f76a18e`, sem apagar variantes ou mudar hashes de recibos. A regressão B foi
reexecutada junto à [validação final C](FASE_C_0220.md), workflow `41466557420` aprovado.
A entrega B continua validada localmente, sem homologação hospedada/assinada implícita.
