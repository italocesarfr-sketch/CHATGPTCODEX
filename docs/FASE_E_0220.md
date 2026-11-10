# 0.22.0 — Fase E: consolidação experimental

> **Atualização registrada em 09/11/2026 (America/Sao_Paulo):** esta página preserva
> as evidências da execução local histórica. O administrador informou implantação
> A–E no projeto separado de homologação; o proprietário relatou dez cenários
> práticos em dois emuladores. São relatos, não execuções automatizadas. Consulte
> a [consolidação atual](CONSOLIDACAO_HOMOLOGACAO_0220.md). Produção continua intocável.

## Estado da branch e evidências

Branch `sync/academic-complete-0220`. Base documental `25c61e7`; código D validado
`9717146b6b5c63ac9aa560f57ac778efed654858` (CI `41527729515`, tentativa 2).
Código E validado `713001a569c5fde39f187db1feb915abdee5392e`.
Versão experimental **0.22.0-dev-e2 / código 45**, package `br.com.diariodeclasse`.

**Consolidação técnica local aprovada:** [CI 41951300992](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41951300992), concluída com sucesso em 05/11/2026 UTC.
Os dois jobs executáveis passaram; o job de candidato com assinatura permanente
foi corretamente ignorado pela restrição da branch. As correções de migração e
sincronização da fonte nativa passaram nesta execução integral.

Aprovação restrita ao ambiente automatizado local/descartável. Não há homologação
hospedada/física nem liberação estável. Condicionais não executados não são contados
como aprovações. Os commits documentais posteriores não alteram o APK validado.

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
localmente e na CI aprovada, inclusive com captura de fonte ampliada. Zero, null, ausência de linha
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
revisão nativa; capturas nativas verificadas na execução aprovada `41951300992`: tela compacta e fontes
1,3/1,6, datas brasileiras, seleção acessível e ações alcançáveis. Conteúdo rola
no diálogo; confirmação/cancelamento permanecem legíveis. TalkBack físico e outros
formatos de dispositivo não foram homologados.

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

Comparação textual do grafo D, normalizando somente declaração/atribuição do
índice e argumento dos lookups: predicados idênticos, SHA-256
`30fbf175ab05e7463bff6febc13f41330d34a5ce00b4cdfca7af6601ec1f2aa9`.

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
Sequência permaneceu 1, com 5.171 registros e um único recibo. CPU/cache variam; não são SLA hospedado.

CI final `41951300992`: staging 2,994 s, commit 8,647 s, replays
0,074/0,072/0,072 s; sequência 1 e único recibo. Round-trips HTTP E do emissor:
pushE 5.180,721–8.855,175 ms, pullE 1,933–46,815 ms; receptor:
pushE 5.082,108–5.215,883 ms, pullE 3,609–59,528 ms. Cenários Android completos
`e-lost`/`e-replay`/`e-recover`: 11,141/23,138/11,958 s. Esses cenários incluem
startup, múltiplas chamadas, Room e UI; replay total não é a latência do recibo SQL.


`python3 scripts/benchmark-phase-e.py --baseline` reproduz os corpos originais
somente na fixture; `python3 scripts/benchmark-phase-e.py` usa os índices. O runner
restabelece as funções atuais e limpa somente a fixture ao terminar. Nova comparação
no mesmo ambiente: commit 20,695→5,902 s; três replays
19,612/21,255/20,069→0,037/0,041/0,045 s. Ambas as variantes mantiveram sequência 1,
5.171 registros e um recibo. Repetição final local no código `713001a`: staging
2,020 s, commit 6,226 s, replays 0,036/0,036/0,039 s, mesma sequência e recibo.
Nenhuma configuração hospedada foi usada.

O script exige base local descartável
`noctua_phase_e_test`, reutiliza migrações/fixtures/validadores e restaura a fixture
no final. Não executar em ambiente hospedado. CI arquiva
`build/phasee-db-benchmark.json`.

Harness Android registra round-trips HTTP (serialização + servidor + rede) sem
corpos, tokens ou nomes: `*-http-timings.jsonl`. Runner registra cenário Android
completo (startup + HTTP + Room + UI): `android-phase-timings.jsonl`. Não se infere
latência pura de rede ou processamento exclusivo Android por subtração.

## Resultados consolidados da CI aprovada

Workflow `41951300992`, código `713001a569c5fde39f187db1feb915abdee5392e`.

| Validação | Executados/aprovados | Condicionais | Falhas |
|---|---:|---:|---:|
| JVM (276 casos) | 275 | 1 | 0 |
| Android integral (423 casos) | 412 | 11 | 0 |
| PostgreSQL v1/A/B/C/D/E1/E2 | 30/38/18/20/27/16/15 (164) | 0 | 0 |
| HTTP Android B/C/D/E em dois AVDs | 10/13/24/23 (70 fases) | 0 | 0 |
| Mortes controladas D/E e replay obrigatório | 2 | 0 | 0 |
| Segurança HTTP B/D/E | 9/32/41 verificações | 0 | 0 |

As execuções focadas repetem parte dos casos da suíte integral; não são somadas
como novos testes únicos. Passaram: preflight de migrações (3), autenticação (19),
protocolo Room (3), B (12), C (10), D (15), E paginado (7), reconciliação E (10),
payload SQLite acima de 2 MiB (1), painel/concorrência Main (3), F/FJ (9),
classificações (10), exclusão de matrícula (14), horários (25), funcionalidades
0.15 (35), relatórios/exportação (27), roster (2), sincronização v1 (24), além dos
smokes e repetições obrigatórias de FJ/Relatórios. Testes de fonte nativa 1,3/1,6
passaram para autenticação, B/C/D/E e os formulários acadêmicos cobertos.

Os onze condicionais da suíte Android são sondas parametrizadas:

- `PhaseBNetworkProbeTest`, `PhaseCNetworkProbeTest`, `PhaseDNetworkProbeTest`,
  `PhaseENetworkProbeTest`: executadas separadamente nas 70 fases HTTP acima.
- `UpgradeProbeTest`: passou antes/depois da atualização da referência v1, em
  instalação descartável, preservando oito tabelas e dados resgatados.
- `AuthProcessProbeTest`: escrita/leitura aprovadas com reboot real do AVD.
- `FrequencyProcessProbeTest` e `SyncProcessProbeTest`: escrita/leitura em
  processos distintos aprovadas, mantendo última marcação e fila persistente.
- `AuthSignedUpgradeProbeTest`, `StableUpgradeProbeTest`,
  `ReleaseContinuityProbeTest`: **não executadas**, dependem do caminho de release
  e assinatura permanente, fora desta branch experimental.

O condicional JVM `SupabaseLiveAuthTest` não foi executado por ausência de
credencial autorizada neste executor. As identidades/JWTs das provas HTTP são
fictícios; PostgreSQL/PostgREST são reais e descartáveis. Nenhuma destas provas
representa Supabase Auth hospedado com duas contas reais.

Reexecutados também neste workspace: D 27/27, E1 16/16 e decisões 15/15; nenhuma
falha. Todas as entidades físicas Room 12 foram comparadas às de 14 e continuam
idênticas. Migrações congeladas e asserções de todas as colunas permanecem.

### Recuperação, conflitos, restauração e integridade

- Dois emuladores independentes: 5.171 registros iniciais nos quinze tipos;
  1.036 matrículas na origem e uma no destino, com IDs preservados. A recuperação
  completa, inclusive movimentos/auditorias/históricos, passou.
- `e-lost` passou: recibo confirmado reutilizado após resposta descartada; mesma
  operação persistente, sem duplicação. Reenvio e recuperação independente passaram.
- Checkpoint D: `Parent written; cursor not committed`. Checkpoint E:
  `First part durable; no academic rows; cursor 0`. Em ambos houve morte real do
  processo, rollback/aplicação integral no replay e preservação do cursor.
- Notas em ambos os sentidos, diário offline/reconexão sem reiniciar e alterações
  independentes passaram. Decisão local (6 versus 8), resposta perdida/reinício e
  observação no outro dispositivo passaram; segunda escolha remota (9 versus 7)
  também passou. Nenhuma variante foi removida sem decisão confirmada.
- Backup cifrado restaurado deixou a sincronização pausada. A conferência explícita
  preservou a nova nota remota (10) e a matrícula criada depois do backup: 5.172
  registros finais. Tentativa de usar backup de outro titular foi rejeitada.
- Hash acadêmico final idêntico nos dois dispositivos:
  `b2ba3c74e7934490780af68f80ef0003b6d3d4006f32ad552ab618a19b6738bd`.
- Leitores independentes aprovaram XLSX/CSV/TSV das projeções de notas, frequência,
  histórico, anual, períodos, planos e arquivos produzidos no Android. Planos
  mantiveram texto exato, Unicode, quebras e fórmulas literais. Backup protegido
  foi decifrado por leitor independente, rejeitando adulteração de titularidade.
- Capturas reais compactas/fontes 1,3 e 1,6 inspecionadas: escolhas, paginação,
  confirmação e cancelamento acessíveis, sem sobreposição; conteúdo longo rola
  dentro da área de evidências. TalkBack/aparelhos físicos ainda pendentes.

### Falhas das rodadas anteriores e respectivas correções

- CI anterior `41760729100`: testes Room E1 **7/7**, reconciliação **10/10**,
  migração/atualização isolada e provas HTTP B/C/D passaram; falhou em `e-lost`.
- CI `41762895876`, código `ff81bfd`: interrupção de admissão após parte durável
  passou, mas `e-lost` voltou a falhar. Código ainda não tinha a otimização SQL.
  A coleta foi corrigida para capturar ComposeTimeoutException (AssertionError).
  Prazo e asserção do cenário foram preservados; a prova posterior confirmou o replay com a otimização, sem remover asserções.
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
- CI `41948624093` (`bbe2e05`) passou o preflight das três migrações e os testes
  Room/painel Main. Parou na espera da fonte nativa do painel C, antes da prova
  HTTP. SettingsProvider pode confirmar font_scale antes da entrega da configuração
  ao processo; criar Activity nesse intervalo pode recriá-la e perder setContent.
  O runner aguarda recursos nativos coincidirem com a escala solicitada antes de
  lançar Activities, usando a fila Main ociosa. Não muda fonte, não injeta Density,
  não amplia o prazo original e conserva todas as asserções. Falhas informam escala
  solicitada, Compose e recursos para evitar diagnóstico por timeout genérico.

A execução final passou a regressão integral, os cenários anteriormente falhos e
as asserções preservadas. As falhas anteriores permanecem documentadas para
rastreabilidade, sem serem tratadas como estado atual.

## Artefatos validados

Todos os artefatos abaixo pertencem ao código `713001a569c5fde39f187db1feb915abdee5392e`:

- [Resultados, XML, planilhas, checkpoints e capturas — 13382642391](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41951300992/artifacts/13382642391).
  ZIP SHA-256: `62f118e94783610eb12fcba50355fa7a3404a96617f1d485087b3b33b100750b`.
  Provas em `build/phasee-network/`, `build/phased-network/`, demais B/C;
  capturas em `layout.tar`, `primary-captures.tar`, `secondary-captures.tar`.
- [APK debug de teste — 13382353077](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41951300992/artifacts/13382353077).
  ZIP SHA-256: `4b63082977bd2f52b13cc91844f72c0f342f82a01201ec56040eb957f6e4d6da`.
  APK SHA-256: `196c759c7ae20c6e7623562f3232c168d81f7453b91a5ec8d8935731e18864fd`.
  Certificado **descartável Android Debug**:
  `5b6b1b83320591af9445126ee25925539fc51f15eccebf125c4a1aacbbb4dcad`.
  AAPT confirmou `br.com.diariodeclasse`, `0.22.0-dev-e2`, c45; apksigner aprovou
  a assinatura. Varredura estática não detectou credencial administrativa,
  clientes fictícios ou entrada demonstrativa no APK acadêmico.

A referência recompilada v1 usou o mesmo certificado descartável do candidato e
passou atualização sem desinstalação. `compatibilidade-baseline.json` compara
outro APK versionado e informa certificado diferente, corretamente: não é prova
contra o c40 permanente. Este APK é **exclusivo de ambiente isolado** e não deve
substituir a instalação do proprietário. Não foi produzida release estável.

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
a definir e validações externas pendentes. Os limites de registros/bytes são
validados; pico de memória e latência em aparelhos físicos/Supabase Free ainda
não foram perfilados. Nada foi implantado no Supabase
hospedado, nenhuma conta Auth real foi criada, nenhum dado real foi enviado e
nenhuma release estável foi publicada nesta execução.

## Próxima ação e estado de continuidade

Código consolidado localmente: `713001a569c5fde39f187db1feb915abdee5392e`.
Apenas documentação foi alterada depois desta CI; reutilizar o artefato validado,
sem recompilar por rotina. Branch experimental preservada, `main` intacta.

A próxima etapa exige aprovação administrativa para o roteiro hospedado acima,
duas sessões Auth comuns autorizadas e instalações separadas com dados fictícios.
Depois: perfil de memória/quotas e homologação física, assinatura permanente e
atualização original c40. A sincronização continua experimental; os limites por
transação/prévia e a política de retenção precisam ser considerados antes de
habilitar acervos reais. Nenhuma autorização de produção é inferida da CI verde.

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
- 1abc39f — fix(sync): confine Room panel publication to the Android UI dispatcher
- bbe2e05 — test(sync): reuse current database factory in frozen migration regressions
- 713001a — test(android): await native font configuration before launching fixtures
- 4f1cacb — docs(sync): record reproduced timings and current native validation [skip ci]

## Arquivos alterados

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/schemas/br.com.diariodeclasse.data.DiaryDatabase/13.json`
- `app/schemas/br.com.diariodeclasse.data.DiaryDatabase/14.json`
- `app/src/androidTest/java/br/com/diariodeclasse/AuthTestRunner.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/BackupInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/ClassLifecycleInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/ClassificationsInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/ExportLayoutInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/FunctionalFifteenInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/HistoryDeletionInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/LessonRecordsInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/NoctuaEntryTestRule.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseBNetworkHarness.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseCPanelLayoutTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseELargePayloadRoomTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseENetworkProbeTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseEReconciliationTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseEReviewLayoutTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseERoomTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/RecompositionInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/RelocationConversionInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/RelocationInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/ReportsConsolidationInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/SchemaBaselineInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/SyncPanelReadinessTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/TimetableInstrumentedTest.kt`
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
