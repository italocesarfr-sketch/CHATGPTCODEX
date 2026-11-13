# H12 — backup antigo e reconciliação explícita

Preparação em **13/11/2026 (America/Sao_Paulo)**, branch
`sync/academic-complete-0220`, a partir de `6cc7bef`.
Preparação LOCAL aprovada em #202. **Após autorização expressa independente,
H12 HOSTED foi executado uma única vez e aprovado em #203, somente para os
nove tipos presentes na conta fictícia A de homologação.** Evidência abaixo.

## Implementação e escopo

Reutiliza `AccountBackup` (AES-GCM/PBKDF2), `BackupManager`, Room 14,
`AcademicReconciliation`, a engine E, recibos, inbox/outbox, revisões e cursores
existentes. Nenhum arquivo de produção, migração SQL, regra de remanejamento,
formato 12 de backup, criptografia, assinatura ou versão foi alterado.

`H12RestoreProbeTest` usa o mesmo startup controlado de H05, com argumento e
consentimento **H12 independentes**, sem iniciar jobs normais antes da revisão.
O inventário paginado limitado foi extraído de H05 para `RecoveryInventory`;
ambas as provas usam esse leitor. O runner Python H05 foi parametrizado com
`--case H12`, preservando os padrões anteriores e evitando outra infraestrutura
para Secrets, ADB, limpeza e relatórios.

### Prova descartável LOCAL

1. Novo AVD da CI, sem sessão/Room anteriores. Auth estritamente fictício e
   PostgreSQL 17/PostgREST local, com políticas e protocolo A–E reais.
2. Recupera o fixture existente de 171 registros, 15 tipos e 37 matrículas.
3. Cria backup cifrado verdadeiro pelo gerenciador existente. Tenta abri-lo
   com a identidade B: deve recusar e manter o banco inalterado.
4. Depois do backup, o peer HTTP descartável atualiza uma nota, um diário e
   acrescenta uma matrícula. Não utiliza o Supabase para produzir esses dados.
5. Restaura o backup antigo somente no workspace descartável. Exige `RESTORE`,
   sync inativa, cursor preservado e cópia de segurança validada. Duas chamadas
   da engine não podem escolher uma versão nem alterar a nuvem por conta própria.
6. Obtém prévia de versões antigas/remotas, incluindo registro ausente do backup
   e alterações de campos. Confirma explicitamente `KEEP_REMOTE`. A proposta
   precisa ter **zero alterações acadêmicas remotas**.
7. Perde intencionalmente a resposta de uma decisão aceita no servidor local;
   fecha/reabre Room e retoma pelo mesmo recibo, sem uma segunda escrita da decisão.
8. Exige igualdade de IDs/payloads/hashes por tipo, bases e revisões, cursor e
   recibos dos lotes, FKs, auditoria local `APPLIED`, filas vazias, ausência de
   duplicação e inventário acadêmico remoto exatamente igual ao anterior à restauração.

A prova adicional inclui os dez testes Room existentes de
`PhaseEReconciliationTest` (rollback, revalidação local/remota, migração 13→14,
backup em banco independente, escolhas explícitas e resposta perdida), além de
três testes de guardas H12. É reabertura do Room, **não** reboot/morte forçada
real do processo. Essa distinção não é substituída por uma afirmação de resiliência total.

### Executor HOSTED — autorização própria obrigatória

Apenas `br.com.diariodeclasse.homologacao`, endpoint fixo
`https://sscexzxtqdcsqdlcytum.supabase.co`, conta fictícia A do Environment
`noctua-homologacao-h01-h04` e um novo AVD da CI. Reutiliza os dois Secrets
nominais existentes; não usa `secrets: inherit`. B fornece apenas o identificador
privado usado na rejeição de titularidade do backup; não abre sessão de B.

Não cria registros nem alterações acadêmicas para fabricar um backup antigo.
Lê páginas já confirmadas do histórico remoto e procura uma revisão anterior,
com grafo válido, fixtureClassId conhecida, ao menos um registro posterior e
uma diferença de payload. Em Room temporário novo, produz um backup cifrado
representando essa revisão e o restaura no workspace descartável recuperado.
O relatório informa `historical_backup_reconstructed=true`: é uma reconstrução
fiel de revisão comprometida no servidor, **não** um arquivo antigo fornecido
pelo proprietário. Se não existir base segura, bloqueia com
`H12_NO_SAFE_OLD_BASELINE`, sem aprovação nem criação de dados na nuvem.

A escolha é exclusivamente **manter nuvem**, após revisão explícita, com
`hasChanges=false`. Ainda assim, `resolveE` **grava uma auditoria/recibo de decisão
no servidor**. Por isso, a autorização de #203 abrangeu essa escrita de
metadados; a autorização somente leitura do H05 não basta. O guard H12 permite
somente a requisição congelada e sua operação exata, uma única vez. Bloqueia
staging, push, tombstones, promoção de escopos, `KEEP_LOCAL`, cancelamento e
qualquer decisão com mudanças acadêmicas. Revalidação de versões continua no
servidor. O head acadêmico e todas as versões devem permanecer iguais.

Sem injeção de falhas hospedadas, carga, contas novas, SQL ou alteração de RLS.
Se outro dispositivo editar a conta durante o ensaio, o teste não aprova.

## Limites e evidências

Limites herdados de H05: 1.000 registros vivos/por lote, 4.096 versões e 8 MiB
por inventário, 128 páginas/inventário, 256 lotes, até cinco minutos e 512
leituras no transporte. Usa volume pequeno; não executa 5.171 registros.

Artefato único: `build/h12/result.json`, estritamente validado antes de publicar.
Só contém contagens, hashes agregados, tempos, códigos/estágios fixos e flags.
Sem e-mails, UUIDs, nomes, notas individuais, tokens, respostas privadas,
bancos, backups, screenshots/logcat ou APKs. Entrada temporária 0600 e diretório
0700 fora do checkout; transferência ao AVD por stdin e remoção ao final.
Nenhum dispositivo preexistente pode ser selecionado: exige runner GitHub,
um único emulator-5554 e nome AVD exclusivo do run/attempt.

O conjunto **H05 hospedado #201** continha **24 registros / nove tipos**.
Os seis tipos ausentes eram `recompositions`, `import_drafts`, `export_rows`,
`class_archives`, `preserved_histories` e `weekly_timetables`.
A cobertura LOCAL dos 15 tipos **não aprova esses seis no ambiente hospedado**.
O futuro H12 informará as contagens presentes/zero reais; não os semeará.

## Validações desta preparação

- Python offline: 16 testes do executor Auth + 15 H05 + 14 H12 = **45 aprovados**.
- JVM debug reexecutada: **298 casos, 293 aprovados, cinco condicionais ignorados,
  zero falhas/erros**. Os condicionais reais de Auth não são aprovados por esta rodada.
- Compilação Kotlin das instrumentações debug e homologacao: aprovadas;
  a compilação homologacao não empacotou APK sem chave nem fez requisições.
- `actionlint` 1.7.7 (sem ShellCheck/pyflakes), py_compile e `git diff --check`: aprovados.
- [CI LOCAL #202](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43438302556):
  **aprovada**, no código `510c14c5ea1cfbb3508f0ada6b735994aa0eed1f`.
  **14 testes Android aprovados, zero falhas/ignorados**: uma prova real HTTP/Room,
  três guardas H12 e os dez testes Room de reconciliação E existentes.
- No mesmo job: **15 testes PostgreSQL de decisões/restauração aprovados**,
  e **nove verificações HTTP/segurança** existentes concluídas pelo bootstrap local.
  Auth/JWT desses cenários são fictícios, não Supabase Auth hospedado.
- Os 45 testes Python passaram também no job. Os jobs hospedados, H05, regressão
  Android geral e release ficaram excluídos; não são contabilizados como aprovações.
- [H12 HOSTED #203](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43441087893):
  **um teste Android aprovado, zero falhas/ignorados**, após autorização própria.
  Fonte `90318e7d0f0ce6e093f7b1ab904cbb6b9b5cdf8f`, sem mudança de código
  depois da prova LOCAL #202. Os demais jobs ficaram excluídos.

A regressão Android integral não foi repetida: nenhuma implementação acadêmica
foi alterada e a prova foi focada em backup/reconciliação e nas guardas compartilhadas.
O ramo HOSTED de reconstrução histórica/conta Auth real foi executado em #203,
com dados fictícios e sem injeção de falhas/carga. A interface visual
de revisão não foi automatizada nesta prova; os mesmos métodos reais de prévia e
confirmação receberam a escolha explicitamente autorizada pelo runner.

### Evidência LOCAL persistida

[Relatório sanitizado](evidence/H12_LOCAL_20261113.json), extraído do
[artefato #202](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43438302556/artifacts/14045910728).
SHA-256 do ZIP de 1.905 bytes:
`ac770e5c820348912152f3e4a3f27116b893aa0eb096e0ce16e2ae0f28e6a9f4`.
O ZIP continha somente `result.json`; sua origem SHA e o esquema foram conferidos,
sem e-mails/UUIDs ou campos desconhecidos.

- Backup antigo: **171 registros / 15 tipos**. Após a alteração no peer local:
  **172 registros / 15 tipos**, com uma matrícula remota adicional preservada.
- Tipos finais: turmas 2; bimestres 8; matrículas 38; aulas 3; atividades 2;
  marcações 71; notas 36; recomposição 1; remanejamento 1; rascunho 1; linhas
  de exportação 3; arquivamento 1; diários 2; histórico preservado 1; revisões
  de horário 2. IDs/relações/payloads e hashes iguais em todos os tipos.
- Restauração persistiu pausa; nenhuma escolha automática. Nota/diário posteriores
  e matrícula ausente no backup foram recuperados; remanejamento, auditorias,
  históricos, frequências/FJ e horários mantiveram os payloads íntegros.
- **Cursor 3 / três recibos**. Outbox, inbox, partes pendentes e fila v1 vazias;
  revisões/bases/ACKs corretos. Uma decisão `APPLIED`, uma resposta perdida e
  **uma única escrita de auditoria** no servidor descartável.
- Banco reaberto e engine executada novamente, sem duplicações. A versão acadêmica
  remota depois da restauração/reconciliação permaneceu idêntica à anterior à
  restauração; os três campos/registros do peer foram alterados **antes** dela.
- Total da prova: **7.645 ms**. Recuperação inicial: **831 ms**; backup cifrado:
  **1.954 ms**; restauração/verificação da pausa: **2.216 ms**; revisão: **630 ms**;
  reabertura/replay e conferências: **862 ms**. Leituras status/pull: 39 chamadas,
  **340 ms** acumulados (exclui peer/decisão; não é benchmark do Supabase).
- Hash lógico do backup:
  `62f371fdfecbb1e8b0016dc528a7410c6a1bc3d9e071442bf7010878ae2cabdd`.
  Hash final local/remoto:
  `f1f5724d258beaa74d390f724b276861e9bfb8c70822851e187154993d18de64`.
- `hosted_requests=false`. Supabase hospedado, produção e dispositivos do
  proprietário não foram acessados por esta prova.

### Evidência HOSTED #203 — 13/11/2026

[Relatório sanitizado](evidence/H12_HOSTED_20261113.json), extraído do
[artefato h12-hospedado-sanitizado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43441087893/artifacts/14045774076).
Resultado `PASSED_HOSTED_PRESENT_TYPES`. SHA-256 do ZIP de 1.681 bytes:
`094013c80dc31fdab3b25987541cc23fc8d95248086974575d835762d1d19dae`.
O ZIP continha somente `result.json`; digest, origem SHA, esquema fechado e
marcador de um teste executado foram verificados antes de persistir o relatório.
Sem e-mails, UUIDs, dados individuais ou respostas privadas.

- Projeto exclusivo `sscexzxtqdcsqdlcytum`; conta fictícia A, Auth real e
  consentimentos existentes. Nenhuma nova conta, alteração de allowlist ou SQL.
- Instalação limpa confirmada no AVD novo da CI. Um backup criptografado foi
  reconstruído de um grafo histórico remoto válido com **22 registros**.
  `historical_backup_reconstructed=true`: não foi utilizado um arquivo antigo
  do proprietário nem foram criadas alterações acadêmicas remotas para o teste.
- Ao restaurar somente nesse AVD, a sincronização permaneceu em **RESTORE**,
  desativada, sem decisão automática. Houve cópia de segurança validada e
  rejeição do backup sob a identidade B, sem abrir sessão de B.
- A prévia comparou versões divergentes e registros ausentes do backup. A
  confirmação explícita **KEEP_REMOTE** tinha `hasChanges=false` e **nenhuma
  mudança acadêmica proposta**. Os **dois registros posteriores ausentes do
  backup** permaneceram preservados na reconciliação.
- Resultado final: **24 registros / nove tipos**, mesmos IDs/payloads/hashes,
  revisões, bases e relacionamentos do servidor. Quantidades abaixo.

| Tipo acadêmico | Preservados |
|---|---:|
| classes | 2 |
| terms | 8 |
| students (matrículas) | 3 |
| lessons | 2 |
| activities | 1 |
| marks (incluindo estados/motivos presentes) | 4 |
| grades | 2 |
| relocations (incluindo payload histórico/auditorias presentes) | 1 |
| lesson_records | 1 |

**Seis tipos ausentes, todos com contagem zero:** `recompositions`,
`import_drafts`, `export_rows`, `class_archives`, `preserved_histories` e
`weekly_timetables`. Não foram criados nem classificados como homologados.
A igualdade do remanejamento presente não prova conteúdo da entidade
`preserved_histories`, ausente neste conjunto.

- **Uma única escrita de auditoria/recibo RESTORE/KEEP_REMOTE**, aceita pelo
  servidor; decisão local `APPLIED`, variantes/revisões congeladas verificadas.
  Confirmação por RPC e recibo da própria operação; não houve consulta SQL
  administrativa nem inspeção direta da tabela privada de auditorias.
- **Cursor 23 e 23 recibos acadêmicos**, sem avanço do head acadêmico pela
  decisão. Outbox, inbox, partes pendentes e fila v1 vazias. Zero tentativas
  de push/staging/promoção bloqueadas; nenhuma alteração acadêmica enviada.
- Inventário acadêmico remoto antes/depois idêntico. Workspace Room reaberto,
  engine executada novamente, revisão/recibos conferidos e sem duplicações.
  Nenhuma resposta foi perdida propositalmente neste ensaio hospedado.
- Total da prova Android: **21.517 ms**. Login: **1.466 ms**; inventário inicial:
  **2.728 ms**; recuperação inicial: **3.187 ms**; backup cifrado: **2.798 ms**;
  restauração/verificação de pausa: **4.409 ms**; revisão: **2.167 ms**; decisão:
  **411 ms**; reabertura/conferências: **2.220 ms**. As 156 leituras status/pull
  acumularam **11.695 ms** de transporte (exclui login/decisão).
- Hash lógico do backup histórico:
  `5fe88d774d810777c21ebab3b1e2a53833734e3eea3979a6ef9e6f4903aff4d2`.
- Hash final local/remoto, também igual ao inventário remoto pré-restauração:
  `e60421c6f9163c9cc0c46ffe660f53c642293d16a18bdb16ecbd8fdee7c15169`.
- Sessão do ensaio encerrada e entrada privada removida. Produção, dados reais,
  emuladores existentes, RLS, contas, migrações e regras acadêmicas intocados.

Limites: um cenário positivo, uma conta e um novo AVD; sem ensaio hospedado de
carga, concorrência deliberada, processo morto/reboot ou variante KEEP_LOCAL.
Não aprova todos os 15 tipos, todos os formatos antigos de backup, dispositivo
físico, acervos reais ou release de produção. As evidências LOCAL continuam
separadas e não substituem essas pendências. Não houve repetição automática.

### Commits e comandos

- `510c14c5ea1cfbb3508f0ada6b735994aa0eed1f`: executor, consentimento independente,
  guardas, teste real e documentação inicial; validado no run #202.
- `90318e7d0f0ce6e093f7b1ab904cbb6b9b5cdf8f`: consolidação LOCAL; também foi a
  fonte exata da execução HOSTED #203. O código permanece o validado em #202.
- A consolidação HOSTED posterior adiciona apenas documentação e o JSON sanitizado.

Comandos locais executados (sem credenciais hospedadas):

```text
python3 scripts/test-hosted-auth-checks.py
python3 scripts/test-h05-recovery.py
python3 scripts/test-h12-recovery.py
python3 -m py_compile scripts/h05-recovery.py scripts/test-h12-recovery.py
actionlint -shellcheck= -pyflakes= .github/workflows/android.yml .github/workflows/h12-restore.yml .github/workflows/h05-recovery.yml
./gradlew --offline --no-daemon :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --rerun
./gradlew --offline --no-daemon -PnoctuaH05HomologacaoInstrumentation=true :app:compileHomologacaoAndroidTestKotlin
git diff --check
```

Na CI LOCAL #202: `test-phase-e-decisions.py` com PostgreSQL descartável,
`start-phase-b-postgrest.sh`, fixture HTTP pequeno `h05-local-fixture.py` e
`h05-recovery.py --case H12 execute` no AVD novo. Nenhum teste de carga.

## Procedimento mínimo

Não exige novos Secrets, migrações nem manipulação dos emuladores do proprietário.

```bash
# Pré-requisitos de formato somente, sem Supabase/AVD:
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
 --ref sync/academic-complete-0220 -f h12_only=true -f h12_mode=PREFLIGHT

# Simulação completa descartável, sem Secrets/Environment ou Supabase:
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
 --ref sync/academic-complete-0220 -f h12_only=true -f h12_mode=LOCAL
```

A execução #203 já utilizou `h12_only=true`, `h12_mode=HOSTED` e
`authorize_hosted_h12=true`, com demais modalidades/autorização desativadas.
Não há nova execução pendente deste cenário. **Qualquer repetição exige nova
autorização específica; a autorização de #203 não é permanente.** Procedimento
para uma futura solicitação:

1. Autorizar explicitamente login A, leituras status/pull E, restauração somente
   no novo AVD e **uma auditoria RESTORE/KEEP_REMOTE sem alterações acadêmicas**,
   consulta do recibo e logout da sessão criada, exclusivamente em homologação.
2. Disparar `android.yml` na branch experimental com `h12_only=true`,
   `h12_mode=HOSTED`, `authorize_hosted_h12=true`; aprovar o Environment, se solicitado.
3. Não editar os dados fictícios dessa conta durante o ensaio. Conferir resultado
   sanitizado e tipos realmente presentes. Não repetir automaticamente uma falha.

Sem autorização dessa auditoria, é possível apenas pré-verificar e ler a prévia;
não declarar H12 integralmente aprovado. Não instalar esta instrumentação sobre
qualquer aplicativo existente nem anunciar release estável.
