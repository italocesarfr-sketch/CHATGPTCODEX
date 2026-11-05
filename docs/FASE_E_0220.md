# 0.22.0 — Fase E: consolidação experimental

## Estado da branch e evidências

Branch `sync/academic-complete-0220`. Base documental `25c61e7`; código D validado
`9717146b6b5c63ac9aa560f57ac778efed654858` (CI `41527729515`, tentativa 2).
Código E em validação `1abc39f8f90d4123aca453f9f473ce744f897683`.
Versão experimental **0.22.0-dev-e2 / código 45**, package `br.com.diariodeclasse`.

**CI completa em curso:** [41945595743](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41945595743).
Última falha analisada: `41943038581` (`577fa4f`).
A Fase E ainda não está aprovada localmente nesta revisão documental. Não há
homologação hospedada/física nem liberação estável. Resultados condicionais não
são contados como aprovações.

`main` permanece em `fc26644b2e55eef5a1d514af382f18c50fc9ddac`. A pasta `.agents/`
não versionada, presente no início, foi preservada. Nenhum reset destrutivo,
limpeza de dados reais, instalação no aparelho principal ou SQL hospedado.

## Implementação efetiva

### E1 — Transferência incremental e recuperação

Mesma AcademicEngine, captura por gatilhos, estados, outbox/inbox, versões,
recibos, mutex de conta, coordenador e RPCs do protocolo acadêmico. Nenhum arquivo
integral de banco é enviado. As quinze entidades continuam sendo:

`classes`, `terms`, `students`, `activities`, `grades`, `lessons`, `marks`,
`recompositions`, `lesson_records`, `relocations`, `weekly_timetables`,
`class_archives`, `preserved_histories`, `export_rows`, `import_drafts`.

O escopo E (5) tem consentimento experimental explícito, conta fictícia não legada,
build debug e autorização do servidor. Clientes B/C/D/v1 não podem escrever na
mesma conta após a transição. O UUID real do proprietário e seu conflito legado
não são autorizados automaticamente.

O remetente persiste a operação antes do envio; confirma manifesto/partes e só
reconhece o envio após recibo real. Admissão da conta é revalidada entre partes e
antes do commit. Mesmo UUID/pedido é reutilizado após falha de conexão/resposta.

O recebimento persiste partes/digests; nenhuma matrícula, nota ou cursor muda
antes da montagem, validação e aplicação transacional do conjunto completo.
Remanejamentos/auditorias não são divididos. Uma interrupção antes da aplicação
reproduz a última parte durável; depois da aplicação conserva o recibo/digest e
compacta apenas o corpo de estágio confirmado. Tombstones e IDs são preservados.

Limites: **128 registros / 768 KiB por parte; 16.384 registros / 16 MiB por
transação**; uma linha histórica nunca é truncada ou dividida. Até quatro envios
incompletos por conta. Excedentes são bloqueados preservando os dados. A capacidade
é por delta, não por tamanho total de registros já sincronizados. Bootstrap local
acima do limite atômico ainda exige preparação adicional; não se promete escala
ilimitada. Transferências confirmadas permanecem no servidor; retenção/quota
administrativa ainda requer homologação.

### E2 — Decisão explícita e auditável

Prévia por identidade/campo compara base, aparelho e servidor. Notas identificam
atividade/data; frequências identificam data/horário. A revisão de UX acrescenta
número da chamada/turma para distinguir homônimos e nomes explícitos de campos,
incluindo início/fim de bimestre em formato brasileiro. Esse ajuste compilou
localmente; sua captura com fonte ampliada depende da CI em curso. Zero, null, ausência de linha
e tombstone são distintos. A escolha local/remota é explícita e vale por conjunto
consistente; atividades/aulas/matrículas/movimentações e dependências não podem ser
resolvidas parcialmente. Históricos/auditorias imutáveis continuam bloqueando uma
proposta incompatível, mesmo que o usuário escolha o aparelho.

Corpo de decisão imutável conserva escolhas, variantes, revisões, digests,
filas/inbox originais e evidências. Estados PENDING/CONFIRMED/APPLIED/STALE.
Servidor deriva base/remoto de suas próprias versões, revalida cabeça/revisões,
conserva decisões negativas e registra sessão/momento da confirmação. Mudar uma
revisão exige nova conferência. Nenhuma regra genérica de última gravação vence.

Receber uma confirmação não autoriza sobrescrever uma edição local posterior:
contexto/digest é revalidado dentro do commit Room. Falha SQLite faz rollback;
resposta confirmada é conservada para replay. Cancelar depois de perder resposta
consulta/reutiliza o recibo original, sem desfazer auditoria já confirmada.

A interface usa Material 3, tema existente, seleção única, áreas de toque de 48 dp,
conteúdo rolável e cinco registros por página. Impeccable 4.7 disponível em
`.agents/skills/impeccable/` foi utilizado para contexto, endurecimento de UX e
revisão nativa; capturas novas ainda dependem da rodada Android em andamento.

Limites da conferência: 256 transações / 64 MiB por prévia; pedido de decisão
768 KiB; corpo local/evidência de servidor até 16 MiB. Excedentes conservam as
variantes e bloqueiam a operação. Conteúdos estruturados são resumidos; regras de
integridade continuam decidindo se a escolha é admissível.

### E3 — Backup e reconciliação

Backup lógico **formato 12 / esquema lógico 10**, AES-GCM/PBKDF2 e senha permanecem
inalterados. Restauração mantém RESTORE/pausa; inclusive uma conta sem controle
acadêmico prévio recebe a barreira antes da gravação, usando o titular já validado
do workspace. Não há nova atribuição de proprietário.

Prévia reconstrói versões remotas e compara o backup restaurado. Ausência no
backup não apaga registros novos da nuvem. Registros exclusivos e tombstones
conhecidos recebem tratamentos distintos. Só a confirmação explícita validada
retoma o escopo E. Sincronização não substitui backup administrativo independente.

### E4 — Continuidade e desempenho

Mesmo mutex, sinais de escrita/primeiro plano/reconexão, retry limitado e
JobScheduler persistente. Não há sincronizadores paralelos por conta. Indicadores
consideram fila, inbox, conflitos, decisão, pausa e conexão; capacidade excedida
não é apresentada como sucesso nem reprocessada automaticamente como falha
transitória. Pausa e logout conservam os dados.

Buscas repetidas no cliente usam PK/índices de identidades. Outbox, inbox e decisões
grandes são lidas em blocos Unicode na mesma transação Room, evitando a janela
SQLite de uma linha; observadores de UI usam somente cabeçalhos de estado.

O grafo D conserva todos os predicados. Nova indexação JSONB só acelera lookups;
identidades duplicadas continuam proibidas. Replay remoto compara o pedido
completo e reutiliza o recibo confirmado antes de repetir validações custosas.

## Migrações e segurança E5/E6

Room **12→13→14**, somente metadados:

| Esquema | Hash | Alteração |
|---|---|---|
| 12 | `f7a581230efcfacc6e9659189abb0448` | 23 entidades anteriores, preservadas |
| 13 | `465e5636dd99c20945a84f78a1167970` | `sync_academic_parts` |
| 14 | `a7f6fa9b7a6e797b534aa5cef6d23fef` | `sync_academic_decisions` |

Comparação dos schemas confirmou que todas as entidades anteriores permanecem
idênticas. Hashes legados continuam exatos nas sondas; BackupManager valida o
esquema físico atual separadamente do formato lógico. Sem migração destrutiva.

Migrações SQL preparadas, **não implantadas**:

1. `202611030001_phase_e_paging.sql`: escopo 5, manifesto/partes, confirmação e pull.
2. `202611030002_phase_e_decisions.sql`: decisões append-only, CAS e recibos.
3. `202611030003_phase_e_indexed_graph.sql`: mesmos validadores D indexados e replay
   confirmado rápido. Helper privado, sem alteração de regra acadêmica.

RLS nas três tabelas E; authenticated lê somente o próprio titular com sessão
ativa/confirmada. Nenhuma escrita direta ou acesso anônimo. Proprietário sempre
vem da autenticação. RPCs autorizados, auxiliares sem EXECUTE de clientes comuns,
search_path `pg_catalog`, objetos sensíveis qualificados. Catálogo de leitura:
`supabase/checks/phase_e_readonly.sql`; catálogo não comprova RLS por si só.

APK local passou `scripts/validate-auth-apk.py`: sem clientes fictícios, entrada
demonstrativa ou marcadores administrativos detectados. Isso é inspeção estática,
não prova de integração hospedada. Nenhuma dependência nova ou chave privada.

## Medições reproduzíveis

Somente PostgreSQL descartável, 5.171 registros / 41 partes:

| Medição | Antes | Após indexação | Repetição pelo script |
|---|---:|---:|---:|
| Primeiro commit | 17,30 s | 7,18 s | 5,625 s |
| Replay confirmado | 13,74 s | 0,04 s | 0,035 / 0,038 / 0,034 s |

Staging na repetição: 1,663 s. CI `41937383459`: staging 2,119 s, commit 6,390 s,
replays 0,050/0,048/0,048 s. No Android, o método HTTP pushE variou de 3.678 a
6.455 ms no emissor; pullE de 2,32 a 32,49 ms (emissor) e de 4,84 a 135,22 ms
(receptor). Os cenários instrumentados e-lost/replay/recuperação demoraram
7,775/11,333/9,298 s, incluindo startup/Room/UI; não são tempos puros de banco.
 Sequência permaneceu 1, com 5.171 registros e um
único recibo. CPU/cache variam; não são SLA hospedado.

`python3 scripts/benchmark-phase-e.py --baseline` reproduz os corpos originais
somente na fixture; `python3 scripts/benchmark-phase-e.py` usa os índices. O runner
restabelece as funções atuais e limpa somente a fixture ao terminar. Nova comparação
no mesmo ambiente: commit 20,695→5,902 s; três replays
19,612/21,255/20,069→0,037/0,041/0,045 s. Ambas as variantes mantiveram sequência 1,
5.171 registros e um recibo. Nenhuma configuração hospedada foi usada.

O script exige base local descartável
`noctua_phase_e_test`, reutiliza migrações/fixtures/validadores e restaura a fixture
no final. Não executar em ambiente hospedado. CI arquiva
`build/phasee-db-benchmark.json`.

Harness Android registra round-trips HTTP (serialização + servidor + rede) sem
corpos, tokens ou nomes: `*-http-timings.jsonl`. Runner registra cenário Android
completo (startup + HTTP + Room + UI): `android-phase-timings.jsonl`. Não se infere
latência pura de rede ou processamento exclusivo Android por subtração.

## Testes executados e pendências da rodada atual

Retomada em validação: workflow `41945595743`, SHA
`1abc39f8f90d4123aca453f9f473ce744f897683`. Inclui os ajustes anteriores e
publicação visual explícita no dispatcher Main, com regressão de invalidação Room.


- JVM atual: **276 casos, 275 aprovados, um LiveAuth condicional não executado,
  zero falhas**. Build debug e de instrumentação aprovados localmente.
- PostgreSQL após otimização: **27 D + 15 E = 42/42**; depois **16/16 E1** e
  **15/15 decisões** em sequência. Comparação do corpo D confirmou predicados
  idênticos, alterando apenas índice e lookup.
- CI anterior `41760729100`: testes Room E1 **7/7**, reconciliação **10/10**,
  migração/atualização isolada e provas HTTP B/C/D passaram; falhou em `e-lost`.
- CI `41762895876`, código `ff81bfd`: interrupção de admissão após parte durável
  passou, mas `e-lost` voltou a falhar. Código ainda não tinha a otimização SQL.
  A coleta foi corrigida para capturar ComposeTimeoutException (AssertionError).
  Prazo e asserção do cenário foram preservados; causa final depende da nova prova.
- CI `41937383459`, código `d96fce0`: passou `e-lost`, replay, morte real após
  primeira parte e recuperação dos 5.171 registros, alterações inversas e diário
  offline; passou também o teste SQLite acima de 2 MiB (1,98 s). Falhou no cenário
  `e-conflict-observe`: o helper E considerava o dispositivo sincronizado sem
  conferir localVersion/acknowledged. A alteração B ainda não havia sido enviada
  quando o processo do teste terminou; A retomou antes do conflito pretendido.
  B/C/D já exigiam essa barreira. A correção fortalece a mesma condição em E,
  inclui decisões pendentes e confirma por leitura HTTP a nota 8 antes de liberar A.
  Não houve remoção de asserção, timeout maior ou alteração de regra do produto.
- CI `41940192378` (`d9240e1`) passou a criação determinística do conflito. Parou
  ao tentar clicar em `academic-review` antes de o painel combinar os cinco fluxos
  Room. A prova agora espera o botão existir e estar habilitado na árvore semântica
  mesclada, mantendo scroll, visibilidade e clique; não usa sleep nem troca para
  árvore não mesclada para contornar acessibilidade. Falhas de escolha preservam
  screenshot nativo e semântica da fixture para diagnóstico, sem suprimir a falha.
- CI `41943038581` (`577fa4f`) passou a decisão local pelo HTTP, perda de resposta,
  retomada da mesma decisão e observação no outro emulador. Na abertura da segunda
  conferência ocorreu CalledFromWrongThreadException: o stack liga emissão do
  executor Room a ApplyingContinuationInterceptor/TestMonotonicFrameClock e
  remeasure de Text na thread errada. Coletores do SyncPanel agora publicam
  explicitamente em Dispatchers.Main.immediate, mantendo consultas no executor
  Room. Nova regressão faz vinte aberturas/fechamentos com invalidações Room reais,
  conferindo estado, spinner e botão. Nenhum erro é ignorado ou timeout ampliado.
- CI `41945595743` (`1abc39f`) aprovou a regressão de publicação Main, as 23 fases
  HTTP E, duas mortes controladas D/E, ambas as escolhas, reconciliação do backup,
  hashes finais e 41 verificações HTTP de segurança E. Parou depois em fixtures
  legadas de classificação/horários: seus builders particulares só registravam
  migrações até 12. O caminho de produção já incluía 13/14 e a atualização isolada
  passou. Fixtures que reabrem o esquema atual passam a usar DiaryDatabase.open;
  helper/checks dos esquemas congelados e comparações de todas as colunas permanecem
  intactos. A inspeção abrangeu os quatorze builders em doze arquivos; todos usam
  a mesma fábrica de abertura atual do aplicativo, sem manter listas paralelas de
  migrações. Preflight inclui os dois casos e a referência v1 antes da prova longa.
  Compilação e JVM passaram após a correção; a regressão Android integral segue
  obrigatória.
- A próxima CI completa deve comprovar a regressão completa restante;
  não existe aprovação antecipada desses cenários.

Preparadas para a rodada atual: leitura SQLite acima de 2 MiB/reabertura; Room;
23 fases HTTP E e uma morte obrigatória após estágio durável; recuperação de 5.171
registros nos quinze tipos, dois AVDs independentes; nota nos dois sentidos;
diário offline/reconexão; duas decisões (local/remota), perda de resposta/replay;
backup criptografado/restauração explícita, preservando novo aluno remoto (5.172
registros finais); hashes finais iguais; UI compacta com fontes 1,3 e 1,6;
regressão A/B/C/D, leitores independentes XLSX/CSV/TSV, autenticação, backup e reboot.
Esses itens não são declarados aprovados antes dos artefatos efetivos.

## Compatibilidade com o APK original

Original correto: workflow `41228898103`, código
`36f58a239318c42f8293c6d0a4c7fa28747d865b`, artefato `13053517551`.
Inspecionado aapt/apksigner: **br.com.diariodeclasse, 0.21.0 / código 40**.

- APK SHA-256: `18335b1e7a640d733e9975d88d3f51a8cea4f73bd9e3bf536f32e90a40495a73`.
- Certificado permanente: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.
- ZIP SHA-256: `5b430a7d9b9d7f2d36c8f72ae02282d99c778d2da8273eb9e6bc29c6b8645bc9`.

O run `40988203896` contém c39; não é a referência c40. Artefato e certificado
público c40 estão disponíveis, mas a chave privada permanente não está neste
executor. O debug E não atualiza a instalação permanente. Atualização original
c40/assinatura permanente e dispositivos físicos continuam pendentes. CI preserva
referência isolada com chave descartável e regressões das migrações/backup; não
se declara essa referência equivalente à instalação original do proprietário.

## Procedimento futuro de homologação hospedada

1. Obter aprovação administrativa específica; preparar backup administrativo e
   contingência. Usar duas contas comuns autorizadas/fictícias, não o UUID do
   proprietário. Manter o conflito legado intacto.
2. Conferir proteções v1/sessão já implantadas. Revisar/aplicar uma vez, na ordem:
   A `202611010001_academic_protocol.sql`, B `202611020001_phase_b_fence.sql`,
   C `202611020002_phase_c_scope.sql`, D `202611020003_phase_d_scope.sql`,
   E1 `202611030001_phase_e_paging.sql`, E2 `202611030002_phase_e_decisions.sql`,
   E4 `202611030003_phase_e_indexed_graph.sql`. Não reaplicar A–D depois de E.
3. Rodar somente as consultas de catálogo/read-only; depois provar acesso direto
   e RPC com duas sessões Auth comuns reais. Testar IDs conhecidos da outra conta,
   anônimo, sessão revogada, e-mail não confirmado e token expirado. Admin SQL não
   é evidência de isolamento entre usuários.
4. Em instalações separadas com dados fictícios, repetir acervo grande, offline,
   resposta perdida, kills, dois sentidos, conflitos e restauração. Conferir IDs,
   relações, zeros/null, FJ/motivos, auditorias, revisões e hashes. Verificar limites
   de tempo/memória/quota do Free; timings locais não homologam o hospedado.
5. Só depois validar candidato assinado com o certificado permanente sobre o APK
   original c40 em aparelho isolado. Nenhuma instalação automática no principal.
   Homologação física/TalkBack, assinatura e aprovação do proprietário precedem
   qualquer liberação estável. Não criar contas institucionais ou NOcTuA Escola.

## Contingência e limitações

Pausar o teste; conservar bancos, filas, partes, recibos e decisões. Sob autorização
administrativa, revogar somente a allowlist fictícia se necessário. Corrigir de
modo aditivo, sem rebaixar escopo, truncar tabelas, limpar conflitos ou restaurar
backup sobre dados reais para experimentar. Não substituir backup administrativo
pela sincronização. Não remover senha/criptografia dos backups existentes.

Persistem limites atômicos e de conferência, retenção de transferências confirmadas
a definir e validações externas pendentes. Nada foi implantado no Supabase
hospedado, nenhuma conta Auth real foi criada, nenhum dado real foi enviado e
nenhuma release estável foi publicada nesta execução.

## Commits da implementação

- b357843 — Planejar consolidação E sobre a cadeia acadêmica D validada [skip ci]
- 5f86798 — Implementar E1 com transferência paginada persistente, escopo próprio e provas de acervos grandes
- 5bb0226 — Acompanhar Room 13 nas verificações estritas de backup e atualização sem alterar formato lógico
- 9bf4441 — Implementar decisão auditável E2 e reconciliação E3 com CAS e proteção de históricos, em validação
- ac8942a — Fixar principal fictício antes dos jobs de inicialização e preservar diagnóstico de entrada C/D
- 2216ba7 — Provar decisões e backup em Room e dois AVDs, preservar pausa inicial e preparar homologação E
- f119c57 — Conferir consulta de catálogo E sem alterar dados ou código Android [skip ci]
- ff81bfd — fix(sync): revalidate account admission during staged transfers
- dbd24a7 — fix(sync): read durable large payloads within SQLite cursor limits
- cc663c8 — perf(sync): index existing academic graph checks and reuse confirmed receipts
- d96fce0 — test(sync): distinguish database HTTP and Android timings for phase E
- d9240e1 — test(sync): await acknowledged revisions before E conflict scenarios
- 7a8a5e1 — fix(sync): identify records and term fields clearly in assisted review
- d03a50d — test(sync): reproduce baseline and indexed database timings safely
- 577fa4f — test(sync): await actionable review UI and retain native failure evidence

## Arquivos alterados desde a base D documental

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/schemas/br.com.diariodeclasse.data.DiaryDatabase/13.json`
- `app/schemas/br.com.diariodeclasse.data.DiaryDatabase/14.json`
- `app/src/androidTest/java/br/com/diariodeclasse/AuthTestRunner.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/BackupInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/FunctionalFifteenInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/NoctuaEntryTestRule.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseBNetworkHarness.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseELargePayloadRoomTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseENetworkProbeTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseEReconciliationTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseEReviewLayoutTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseERoomTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/UpgradeProbeTest.kt`
- `app/src/main/java/br/com/diariodeclasse/auth/AccountWorkspace.kt`
- `app/src/main/java/br/com/diariodeclasse/backup/BackupManager.kt`
- `app/src/main/java/br/com/diariodeclasse/data/Database.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/SyncPanel.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicDatabase.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicEngine.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicPaging.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicPanel.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicReconciliation.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicRecords.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicResolution.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicReviewDialog.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicRuntime.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicStore.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicTransport.kt`
- `app/src/test/java/br/com/diariodeclasse/AcademicPagingTest.kt`
- `app/src/test/java/br/com/diariodeclasse/AcademicResolutionTest.kt`
- `app/src/test/java/br/com/diariodeclasse/PhaseCStatusTest.kt`
- `docs/FASE_E_0220.md`
- `docs/PLANO_SINCRONIZACAO_0220.md`
- `scripts/benchmark-phase-e.py`
- `scripts/ci/test-phase-b-network.sh`
- `scripts/ci/test-phase-c-network.sh`
- `scripts/ci/test-phase-d-network.sh`
- `scripts/ci/test-phase-e-network.sh`
- `scripts/ci/validate-android.sh`
- `scripts/phase-b-http-fixture.py`
- `scripts/test-academic-postgres.py`
- `scripts/test-phase-b-postgres.py`
- `scripts/test-phase-c-postgres.py`
- `scripts/test-phase-d-postgres.py`
- `scripts/test-phase-e-decisions.py`
- `scripts/test-phase-e-http.py`
- `scripts/test-phase-e-postgres.py`
- `supabase/checks/phase_e_readonly.sql`
- `supabase/migrations/202611030001_phase_e_paging.sql`
- `supabase/migrations/202611030002_phase_e_decisions.sql`
- `supabase/migrations/202611030003_phase_e_indexed_graph.sql`
