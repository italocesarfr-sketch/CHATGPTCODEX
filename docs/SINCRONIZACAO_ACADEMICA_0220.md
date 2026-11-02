# 0.22.0 — fase A: protocolo acadêmico incremental

**Entrega parcial de desenvolvimento. Não é uma release 0.22.0 instalável.**

Branch `sync/academic-complete-0220`, derivada de `340f6194259b` (código da candidata
0.21.0/c40 em `36f58a239318`). O plano completo está em
[PLANO_SINCRONIZACAO_0220.md](PLANO_SINCRONIZACAO_0220.md).

## Estado do produto

A 0.21.0 continua sincronizando apenas configuração de turmas e seus quatro
bimestres, mediante ativação experimental e participação explícita. O proprietário
relatou sucesso entre celular e emulador para criação e recebimento, falha inicial
de reconexão contornada por reinício e conflito ao receber uma edição no celular
com dados dependentes. Esses relatos são evidências do proprietário; não foram
reexecutados nos seus aparelhos nesta etapa.

A causa estrutural do conflito foi confirmada em `ClassSyncStore.protected`:
qualquer vínculo local bloqueia o agregado turma/bimestres. A proteção, o conflito
existente e a candidata 0.21.0 foram preservados. O novo algoritmo consegue revisar
uma mudança de nome mantendo estudantes, notas, frequências e diário, mas **ainda
não está ligado ao transporte nem à gravação do aplicativo**. Não anunciar essa
correção como já disponível no APK instalado.

## Implementado

- Contrato versionado das **15 entidades físicas** de Room v11 em
  `supabase/contracts/academic-v2.json`: campos, tipos, chaves, FKs e unicidades.
  Não há entidade redundante de média, frequência calculada ou ano letivo separado.
- `AcademicRecords`: conversão por linha com os serializers dos próprios modelos,
  restauração tipada, validação integral pelo `BackupCodec` e cálculo incremental
  apenas das linhas diferentes. Não envia arquivo de banco nem envelope de backup.
- `AcademicMerge`: revisão pura de três versões (base, local, remota), exige revisão
  conhecida, preserva todas as variantes em conflitos e só mescla campos de turma
  independentes quando uma das mudanças é exclusivamente `name`. A proposta inteira
  passa pelo validador acadêmico existente; não escreve no Room.
- Migração aditiva `202611010001_academic_protocol.sql`: armazenamento por registro,
  CAS, lote atômico, recibos idempotentes, versões imutáveis, tombstones e recebimento
  com cursor por lote completo. Mantém intactas as tabelas e RPCs v1.
- Testes SQL com papéis comuns, testes JVM de codec/revisão e testes instrumentados
  de conversores/persistência Room e preservação do conflito v1. CI ampliado para
  testar a compatibilidade entre JSON gerado pelo Kotlin e PostgreSQL independente.

Os 15 tipos estão **mapeados e exercitados no protocolo local**. Isso não significa
15 tipos sincronizados no aplicativo ou no Supabase hospedado.

## Estrutura remota

Todas as chaves incluem `owner_id` derivado exclusivamente de
`noctua_sync_principal()`; nenhuma RPC recebe proprietário como parâmetro.

| Tabela | Função |
|---|---|
| noctua_academic_heads | Serialização por proprietário e sequência de commits |
| noctua_academic_batches | UUID e sequência do lote confirmado |
| noctua_academic_records | Estado atual por tipo/chave/revisão, inclusive tombstones |
| noctua_academic_versions | Versões anteriores e log incremental, sem sobrescrita |
| noctua_academic_receipts | Pedido e resultado imutáveis para reenvio/conflicto |
| noctua_academic_links | FKs físicas com proprietário comum e pais vivos |
| noctua_academic_uniques | Unicidades físicas do Room, com validação diferida |

Registros usam payload JSON tipado, uma linha por entidade Room. Chaves compostas
são objetos, por exemplo `{"lessonId":"...","studentId":"..."}`; `number` e
`term` permanecem números. Campos de identidade não são substituídos por nomes.
Conteúdo aninhado de históricos/auditoria/slots conserva sua estrutura existente.
Slots históricos não ganham uma FK fictícia para uma turma já excluída.

### RPC de envio

`noctua_push_academic(p_operation uuid, p_changes jsonb)` recebe uma lista:

```json
[
  {"kind":"marks", "key":{"lessonId":"aula-id","studentId":"matricula-id"}, "base":1,
   "payload":{"lessonId":"aula-id","studentId":"matricula-id","mark":"FJ","reason":"Atestado médico"}}
]
```

O exemplo pressupõe pais existentes na mesma conta. Os campos do payload devem
ser exatamente os do contrato. `payload: null` representa tombstone;
`grades.payload.value: null` representa nota pendente. Ausência de linha e NR são
estados diferentes. Não inferir presença/falta durante conversão ou recuperação.

Um conflito de revisão rejeita **todo o lote** e conserva pedido e variantes no
recibo negativo. O cliente terá de manter a pendência e obter decisão explícita;
não poderá reenviar outro conteúdo usando o mesmo UUID. Um erro estrutural faz
rollback, sem recibo positivo, incremento de cursor ou alterações parciais.

O servidor verifica tipos/campos/chaves, datas, quatro bimestres, vínculos físicos,
unicidades, turma comum entre frequência/nota e matrícula, limites de notas e
motivos FJ existentes. Revisões de horários não podem ser reescritas; correções
anteriores de remanejamento devem continuar como prefixo imutável da auditoria.
As regras profundas de cadeia de remanejamento e histórico ainda precisam de
validação servidor específica na fase D; o cliente já reutiliza o validador atual
ao construir uma proposta. **Essa é uma barreira de liberação, não uma aprovação
de integridade acadêmica integral no servidor.**

O validador de backup verifica um estado armazenado, não substitui as regras de
uma operação administrativa do `DiaryRepository`. Antes de ligar envio/recebimento,
a fase B também deve proteger ano imutável da turma, identidade/turma imutáveis da
matrícula, IDs já presentes em origens excluídas e números de chamada concorrentes.
`validateStudent` verifica unicidade de número embora o Room não tenha índice único
nesse campo. Não renumerar nem fundir matrículas automaticamente para resolver
esses conflitos. As proteções atuais do repositório continuam intactas no aplicativo.

### RPC de recebimento

`noctua_pull_academic(p_after bigint)` retorna até um lote completo por página,
incluindo operação, sequência e versões das linhas. Nunca divide origem/destino de
um remanejamento entre páginas. O histórico imutável permite reproduzir versões
anteriores; o cursor não pode avançar antes da aplicação transacional no Room.
Essa aplicação persistente é trabalho da fase B em diante.

Limites iniciais: 256 linhas, 1 MiB por pedido e 512 KiB por linha. Dados acima dos
limites devem ser bloqueados/preservados, nunca truncados. Antes de envio inicial
de grandes acervos, implementar estágio por lotes com confirmação de integridade
global. O protocolo atual não deve ser habilitado para acervos arbitrários.

## Segurança implementada e limites da prova

As sete tabelas nascem com RLS. `authenticated` tem apenas SELECT de linhas próprias
com sessão confirmada e ativa; anon/PUBLIC não têm acesso, nem há escrita direta.
FKs incluem proprietário e tombstone, impedindo referência a pai de outra conta
ou excluído. Funções auxiliares não são executáveis pelo cliente. As duas RPCs
usam `SECURITY DEFINER` com `search_path=pg_catalog`, objetos qualificados e guard
de sessão já adotado na 0.21.0.

Os testes usam **dois UUIDs fictícios e papéis comuns** no PostgreSQL local, com
fixtures de Auth. Não equivalem a dois logins JWT no Supabase hospedado. O cliente
de produção continua usando apenas a chave pública; nenhum segredo administrativo
foi adicionado e nenhuma conta foi criada nesta execução.

## Dados, Room, backups e APK

- Room permanece v11; nenhuma migração local foi adicionada e nenhum esquema
  histórico foi modificado.
- Backup lógico permanece formato 12, com envelope criptográfico e proteção de
  titularidade intactos. O novo codec não acessa tokens/senhas nem seleção de tela.
- Nenhum dado de aparelho do proprietário foi lido, enviado, restaurado ou apagado.
- `main` e `sync/academic-foundation-0210` não foram alteradas.
- O Android permanece 0.21.0/c40 nesta fase de biblioteca/protocolo. Não foi gerado
  APK release 0.22.0: ainda não há integração suficiente para um candidato seguro.
- O artefato debug do CI é somente de regressão, não serve para atualizar a versão
  assinada do proprietário. O job de release estável não é executado nesta branch.

## Validação desta etapa

Resultados locais e consolidação do CI:

- 30/30 testes de regressão do PostgreSQL v1.
- 38/38 testes do novo protocolo, incluindo JSON independente produzido pelo Kotlin
  para todos os 15 tipos e isolamento por proprietário nas sete tabelas.
- **242 JVM aprovados**, entre 243 casos: um teste opt-in de autenticação real
  ignorado por ausência de credenciais autorizadas nesta execução. Inclui **24/24**
  testes novos de codec/revisão.
- **350 Android aprovados**, entre 357 casos, com sete sondas condicionais ignoradas
  na suíte geral e nenhuma falha. **3/3 novos testes Room** aprovados também em
  execução focada; **25/25 testes focados de horários**, incluindo o ensaio anual
  corrigido, passaram antes de nova execução na suíte completa.
- Sondas de processo Auth/frequência/fila v1 executadas separadamente em fases de
  gravação/leitura, sem somá-las outra vez aos 350. A atualização debug da referência
  histórica também passou. **Não houve atualização release assinada 0.21→0.22.**
- APK debug e instrumentação compilados; regressão de relatórios, XLSX/CSV/TSV,
  backups, autenticação e fontes Android reais 1,3/1,6 aprovada no emulador API 30.
- Sondas hospedadas v1: 12/12 acessos sem sessão/com JWT inválido recusados, sem
  ler linhas acadêmicas nem gravar dados. **Nenhuma RPC v2 hospedada foi testada.**

Código validado: [`851ac8b53657306bc47e49d1ffd3837e61ea033b`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/851ac8b53657306bc47e49d1ffd3837e61ea033b).
[Execução aprovada `41365065379`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41365065379):
job PostgreSQL v1 e job JVM/protocolo/Android aprovados; job de release corretamente
ignorado para esta branch de fundação. A documentação posterior não altera esse
código validado. O run `41363698843` foi cancelado quando se acrescentou a proteção
de revisão-base; não é a evidência final.

[Artefato de resultados `13107910242`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41365065379/artifacts/13107910242),
baixado e conferido: SHA-256
`be69c0a0d84b9fad6628e73401bf1fe57420b2cd0532f945ebee63910f85ea5d`.
Totais extraídos dos XMLs: JVM `tests=243, skipped=1, failures=0, errors=0`;
Android `tests=357, skipped=7, failures=0, errors=0`. As sete sondas são AuthProcess,
AuthSignedUpgrade, FrequencyProcess, ReleaseContinuity, StableUpgrade, SyncProcess
e UpgradeProbe. Os ensaios assinados permanecem pendentes para um candidato futuro.
Os textos focados e capturas reais de horários também estão no artefato; a captura
anual final mostra os períodos 1, 2 e 3 selecionados e confirmados.

Migração SQL testada: SHA-256
`32e3e7f43e4c8e999c900bf9b4ae8307170cae42228261d0c4d461fcf3755120`.
Contrato tipado: SHA-256
`506fd2d20059ac16eea778a1c67d8302d7ed44f2d2bc312e0caac7967d6904ea`.

A primeira rodada `41363911384` aprovou 242 JVM (um condicional ignorado) e os
38 testes PostgreSQL, mas parou no teste existente de edição anual de horários.
O teste abria a revisão do dia real, esperava os dois períodos da revisão de janeiro
e, desde 01/11/2026, encontrava corretamente a revisão vazia criada por ele mesmo.
A captura `09-alcance-anual` mostrava apenas o 3º período selecionado. A automação
agora seleciona explicitamente a vigência de janeiro e verifica os períodos 1 e 2
antes de adicionar o terceiro; conserva as asserções de três períodos, histórico
integral e dados acadêmicos intactos. Nenhuma regra de horários foi alterada.

## Pendente para as fases B–E

1. Outbox acadêmica persistente, captura atômica de todas as entidades e migração
   aditiva Room, incluindo validação das regras de operação acima. A fila v1
   existente continua limitada a turmas/bimestres.
2. Transporte v2, confirmação estrita por lote, aplicação incremental em Room,
   recuperação em instalação independente e retomada após encerramento do processo.
3. Barreira de protocolo por proprietário antes de transição v1→v2; preservar e
   revisar explicitamente o conflito observado na homologação, sem apagá-lo.
4. Regras servidor completas para remanejamentos, histórico e auditorias, validação
   global de bootstrap e suporte seguro a operações acima dos limites iniciais.
5. Coordenação de conectividade/foreground/retry e indicadores verdadeiros. A falha
   física de reconexão ainda não foi reproduzida nem corrigida nesta fase. Inspeção
   mostra dependência de JobScheduler; isso não identifica sozinho a causa real.
6. Interface de resolução explícita que preserve variantes e registre decisão.
7. Migração hospedada, testes positivos reais e isolamento com **duas contas comuns
   autorizadas**. Não solicitar senhas/tokens no chat nem criar contas sem autorização.
8. Atualização assinada 0.21.0→0.22.0, integridade de backup/restauração e homologação
   física após integração completa. Manter ativação experimental e backups atuais.

Não aplicar essa migração ao projeto hospedado como se fosse ativação da 0.22.0.
Ela prepara a fase A; não resolve a coexistência de escritores v1/v2 nem habilita
upload. Próximo passo técnico: fase B, captura e recuperação transacional de turmas,
bimestres e matrículas, seguida dos demais agregados conforme o plano.

## Reprodução em ambiente descartável

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
PGDATABASE=noctua_sync_test PGHOST=127.0.0.1 python3 scripts/test-sync-postgres.py
PGDATABASE=noctua_academic_test PGHOST=127.0.0.1 \
ROOM_WIRE_PATH=app/build/academic-protocol-validation python3 scripts/test-academic-postgres.py
```

Os scripts SQL de teste exigem nomes de banco locais específicos e usam somente
fixtures. Nunca executar os scripts Python de teste contra o Supabase de uso real.
As migrações versionadas, por sua vez, devem ser aplicadas em ordem, sem reaplicar
a primeira migração v1 sobre o guard de sessão posterior.

## Arquivos desta entrega

- `supabase/contracts/academic-v2.json` — mapeamento tipado das 15 entidades.
- `supabase/migrations/202611010001_academic_protocol.sql` — protocolo, tabelas e RLS.
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicRecords.kt` — codec,
  delta incremental e revisão sem gravação.
- `app/src/test/java/br/com/diariodeclasse/AcademicProtocolTest.kt` — 24 testes JVM.
- `app/src/androidTest/java/br/com/diariodeclasse/AcademicProtocolRoomTest.kt` — três
  testes Room de leitura, reinício, rollback e conflito preservado.
- `app/src/androidTest/java/br/com/diariodeclasse/TimetableUiInstrumentedTest.kt` —
  correção da data de referência do ensaio anual existente.
- `scripts/test-academic-postgres.py` — 38 testes PostgreSQL e conferência Kotlin/SQL.
- `scripts/ci/validate-android.sh` — ensaio focado Room antes da regressão intacta.
- `.github/workflows/android.yml` — branch experimental e serviço PostgreSQL para
  validar o contrato, sem habilitar release nesta branch.
- `README.md`, `docs/PLANO_SINCRONIZACAO_0220.md` e este documento — estado e continuidade.
