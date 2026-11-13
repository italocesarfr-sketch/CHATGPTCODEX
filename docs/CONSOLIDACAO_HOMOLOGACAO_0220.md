# 0.22.0 — Consolidação da homologação e automação restante

> **Atualização em 10/11/2026:** o proprietário confirmou isolamento prático com
> **duas contas distintas**, troca no mesmo emulador, sincronização separada e
> retorno ao workspace original. Isso atualiza os relatos manuais H01/H02/H05,
> sem aprovar automaticamente o runner hospedado. Ver
> [preparação privada H01–H04](H01_H04_HOSPEDADOS_0220.md). Nenhuma chamada hospedada
> foi feita nesta atualização; a autorização específica ainda é necessária.

Registro: **09/11/2026, America/Sao_Paulo**. Branch exclusiva
`sync/academic-complete-0220`. Base inspecionada `34780d1d23ba8234287550304578ca529a92b90b`;
automação de volume `ad9a90abd95f8d95e03a7124f8d24563f899828d`;
consolidação inicial `63ea459`; pré-condições do runner hospedado
`e007db311b25ed2e88a4446e8af49cf80626a856`; diagnóstico de crash/repetições
`1f66134e1f05b5554673f7d0947758409f658d6c`; reinício verificável do AVD
`41fd98964959d16ad2bd7a217277097d1f632b19`.

**Escopo:** provas seguras locais/descartáveis e documentação. Nenhuma requisição
foi feita ao Supabase hospedado nesta rodada. Nenhum aplicativo do proprietário
foi acessado, instalado, removido, limpo ou reinstalado. Nenhum novo candidato
de homologação/release,
DDL hospedado, usuário, allowlist, plano, chave permanente ou `main` foi alterado.
O último APK paralelo publicado continua o artefato da execução **42438137774**,
package `br.com.diariodeclasse.homologacao`, versão `0.22.0-dev-e2-homologacao`/c45.
O hash das instalações do proprietário não foi reconferido; os APKs DEBUG gerados
na regressão são somente para os AVDs descartáveis da CI, não para distribuição.

## 1. Fontes e datas: não confundir tipos de evidência

1. **Código e CI históricos conferidos:** fases A–E preservadas; última regressão
   completa anterior **42435902661**, fonte `7d00bd0aee80478a5204c64e93ee25385adbb51e`,
   aprovada. Build paralelo **42438137774**, fonte `e8fa20406e63`, aprovado.
   Não existem commits posteriores à base inicial antes desta rodada. Working tree
   inicial continha somente `.agents/` não rastreado, preservado.
2. **Relato administrativo hospedado, não leitura nova do Codex:** projeto separado
   **noctua-homologacao**, ref `sscexzxtqdcsqdlcytum`; v1, guard e A–E aplicados;
   16 tabelas/RLS/políticas, ausência relatada de DML direto/SELECT anônimo;
   duas contas comuns confirmadas e allowlisted. Isso não prova RLS via JWT comum.
3. **Relato funcional do proprietário:** dez cenários aprovados em dois emuladores
   independentes, com dados fictícios. Não foram fornecidas datas de execução,
   snapshots, IDs de casos automatizados ou métricas de volume. O dia acima é a
   data do registro, não uma data inventada para a execução manual.
4. **Novas execuções locais:** PostgreSQL 17.11 em cluster descartável próprio,
   porta 55433; fixtures sintéticas e sessões SQL fictícias. JVM sem credenciais
   live. Resultados abaixo não certificam o serviço hospedado nem seu SLA.

Os documentos antigos conservam suas datas e resultados históricos. Foram
acrescentadas notas e alinhadas referências operacionais de projeto/callback, sem converter “não executado naquela rodada”
em falha atual ou orientação para implantar novamente. **As instruções antigas
que mencionam noctua-auth não autorizam nenhuma ação nesse projeto de produção.**

## 2. Relatos do proprietário preservados

Fonte estruturada: [HOMOLOGACAO_RELATOS_20261109.json](evidence/HOMOLOGACAO_RELATOS_20261109.json).

- Turmas e alunos, atividades e notas sincronizados nos dois sentidos.
- Chamadas/FJ sincronizadas; diário sincronizado e editado bidirecionalmente.
- Remanejamento com histórico preservado **no cenário ensaiado**.
- Edição offline, reconexão e sincronização automática.
- Conflito simultâneo de notas detectado/preservado; escolhas local e nuvem realizadas.
- Alteração offline persistiu após fechar/reabrir o aplicativo e sincronizou depois.

Não se infere daí teste com duas **contas distintas**, cobertura dos quinze tipos,
recuperação de 5.171 registros em instalação limpa ou restauração de backup antigo.
Também não se chama esse relato de execução automatizada, prova de aparelho físico
ou aprovação de atualização com certificado permanente.

**Decisão de produto mantida integralmente:** disciplinas incompatíveis continuam
bloqueadas; lançamentos na origem na data efetiva ou posteriormente continuam
impedindo o remanejamento. Nenhum código/SQL/validador dessas regras foi alterado.
As regressões existentes `RelocationRulesTest`, `RelocationConversionTest` e
respectivos instrumentados permanecem na suíte.

## 3. Matriz H01–H13

“Aprovado local” refere-se somente à camada indicada, nunca ao Supabase Auth real.
“Manual confirmado” refere-se somente ao cenário relatado. Uma linha pode ter
cobertura local aprovada e, simultaneamente, obrigações hospedadas pendentes.

| Caso | CI local/descartável e cobertura reutilizada | Homologação hospedada relatada | Pendente de comprovação / falha identificada |
|---|---|---|---|
| **H01 — Auth A/B** | Fluxos/AuthVault/AccountWorkspaces e 19 testes Auth instrumentados na regressão. Cliente Auth real preparado em `HomologationHostedAuthTest.h01`, mas condicional. | **Confirmado manualmente em 10/11:** acesso/troca entre duas contas distintas. Refresh, senha incorreta e identidade pela API do runner ainda não executados. | **Pendente:** H01 automatizado real com A/B e identidade esperada; troca de conta prática já relatada. Fixtures locais não validam Auth hospedado. Nenhuma falha funcional nova demonstrada. |
| **H02 — isolamento RLS/RPC** | PostgreSQL A/B/D/E e HTTP de duas identidades fictícias: leitura/escrita cruzada recusadas; workspace/backup por titular. `h02` preparado com fixture A conhecida, leitura A positiva e RPC B válida antes da negativa cruzada. | **Confirmado manualmente em 10/11:** contas distintas, troca no mesmo emulador, sincronização separada e retorno ao workspace original. Não é prova de acesso direto REST/RPC. | **Pendente:** duas sessões Auth comuns reais para provas REST/RPC cruzadas; isolamento prático no app foi relatado como aprovado. O runner não cobre sozinho escrita/exclusão cruzada efetiva. |
| **H03 — permissões** | ACL/helpers/unfenced/DML/anon cobertos em PostgreSQL e HTTP locais. `h03` opt-in usa helper e PATCH sem possibilidade de afetar linhas. | Inventário 16/16 é relato administrativo; não é execução pela API como usuário comum. | **Pendente:** confirmar ACL pela API comum, catálogo e demais tabelas. Não executar DML hospedado automaticamente. |
| **H04 — sessões inválidas** | JWT inválido/expirado, sessão removida, usuário não confirmado e anon recusados nos ensaios SQL/HTTP com Auth fictício. `h04` preparado, exigindo RPC positiva antes de revogar sua própria sessão de ensaio. | Não relatado para as contas fictícias hospedadas. | **Pendente:** ausência/invalidade/revogação reais. JWT efetivamente expirado/conta não confirmada continuam fora do runner atual; não criar usuário nem forjar aprovação. |
| **H05 — recuperação completa** | B: 36 matrículas/41 registros em segundo AVD; D: 171 registros/15 tipos; E: 5.171/41 partes, IDs/payloads/zero/null/FJ/auditorias exatos no segundo AVD. Nova graduação SQL válida 41/171/671/5.171, não só grafo inválido de 671. | Turmas/alunos/notas/frequência/diário/remanejamento confirmados; retorno ao workspace original após trocar de conta também relatado em 10/11. | **Pendente:** recuperação hospedada limpa e inventário completo dos quinze tipos, contagens/hashes; volumes hospedados não informados. |
| **H06 — paginação/processo** | D/E: morte real de processo em checkpoint, sem cursor/aplicação parcial; replay obrigatório em processo novo; reboot real comprovado e sessão/dados preservados. Sete testes Room E e payload SQLite >2 MiB. | Fechar/reabrir com alteração offline relatado; não equivale ao checkpoint de recebimento. | Interrupção forçada **somente no descartável**, não repetir nos dois emuladores do proprietário. Recebimento hospedado real ainda precisa de conferência normal, sem injeção de falha. |
| **H07 — offline/reconexão** | B/C/D/E HTTP: fila persistente, reconexão sem reiniciar, edições reversas; sondas de processo/reboot executadas separadamente. | **Confirmado manualmente:** offline/reconexão automática e persistência após fechar/reabrir. | Não extrapolar para limites de background de aparelhos físicos não testados; nenhuma falha nova demonstrada. |
| **H08 — resposta perdida/timeout** | `e-lost`/replay e decisão com resposta descartada: mesmo UUID/recibo, sem duplicação. Novos ensaios CI de orçamento 8 s e rollback no meio da escrita, reenvio com 41 partes preservadas. | Não relatado; não provocar falhas no hospedado. | Timeout/API/rede Free efetivos e margem permanecem sem medição. Repetir falhas intencionais **só localmente**. |
| **H09 — conflito/decisão** | SQL decisões 15; Room reconciliação 10; E HTTP local/nuvem, revisão alterada, perda de resposta, variantes/auditoria preservadas. | **Confirmado manualmente:** conflito simultâneo de notas; escolha local; escolha nuvem. | Revalidação/auditoria e mudanças durante a prévia têm prova local; não anunciar inspeção dessas tabelas hospedadas. Conflito legado real intocado. |
| **H10 — remanejamento/grafo** | D: 27 SQL, 15 Room, 24 fases HTTP; cadeia indivisível, identidade/datas, ciclo, histórico, auditoria imutável e rollback. Comparação completa de payloads na graduação. | **Confirmado manualmente no cenário testado:** movimentação e histórico. | Casos negativos completos têm evidência local; não inferir ensaio hospedado de todas as variantes. Duas regras de produto continuam intactas. |
| **H11 — exportação/histórico** | D: troca/reordenação sem quebrar posições únicas; ocultações e histórico. Regressão de relatórios/exportação e leitores independentes XLSX/CSV/TSV/planos de aula no CI. | Diário e histórico de remanejamento relatados; exportações, revisões de grade, arquivamentos e rascunhos não relatados explicitamente. | Pendente conferência dos demais tipos recuperados hospedados. Não houve mudança de exportador. |
| **H12 — backup/reconciliação** | `e-backup-before`→`e-after-backup-remote`→`e-restore-review`: backup cifrado, pausa RESTORE, decisão explícita, nota remota 10/matrícula posterior preservadas, 5.172 registros finais; backup estrangeiro recusado. | Não relatado. | **Pendente hospedado prioritário:** instalação extra isolada e backup fictício antigo. Nunca restaurar sobre os acervos existentes do proprietário. |
| **H13 — capacidade/continuidade** | Estado sincronizado exige fila/inbox/partes vazias, revisões reconhecidas e decisões finalizadas. Graduação, hashes finais iguais entre dois AVDs, métricas SQL e HTTP separadas. | Continuidade funcional/offline relatada; sem volume/latência/quota/memória. | **Falha de capacidade no ensaio de 8 s:** 5.171 causou SQLSTATE 57014 na CI final; rollback íntegro. Memória/compute/quota/p95 Free não comprovados. Bloquear progressão a esse patamar até medir margem. Não há SLA nem aprovação de capacidade. |

Referências executáveis principais:

- `app/src/androidTest/java/br/com/diariodeclasse/Phase{B,C,D,E}NetworkProbeTest.kt`;
- `PhaseBRoomTest`, `PhaseCRoomTest`, `PhaseDRoomTest`, `PhaseERoomTest`,
  `PhaseEReconciliationTest`, `PhaseELargePayloadRoomTest` e testes de autenticação;
- `scripts/ci/test-phase-{b,c,d,e}-network.sh` e `scripts/ci/validate-android.sh`;
- `scripts/test-{academic,phase-b,phase-c,phase-d,phase-e}-postgres.py`,
  `scripts/test-phase-e-decisions.py`, `scripts/test-phase-{b,d,e}-http.py`.

Não foram duplicados esses cenários. Em particular, `e-lost`, recuperação da
instalação secundária, reconciliação cifrada, isolamento e checkpoints já possuíam
prova real de Room + transporte HTTP + PostgreSQL/PostgREST em dois AVDs da CI.

## 4. Automação nova e proteções

**Código do aplicativo oficial:** nenhuma alteração em `app/src/main`, manifesto,
Gradle do app, entidades Room, schemas, backup, exportadores ou migrações SQL.
As mudanças desta rodada são testes, scripts de prova, workflows e documentação.
Não foi necessário corrigir defeito funcional do produto. A auditoria corrigiu
uma lacuna de evidência no runner de teste: respostas negativas isoladas poderiam
ser confundidas com uma RPC indisponível mesmo para sessões válidas.

1. `scripts/benchmark-phase-e.py --graduated`: reutiliza as fixtures B/D/E para
   36 matrículas (41 registros), 171/671/5.171 registros. Além dos três replays,
   confere payloads paginados completos, chaves/revisões, um recibo e um lote,
   invisibilidade antes do commit, sequência, ausência de acesso B e hash canônico.
   Informa partes/páginas, tempo `psql`, armazenamento físico com índices e marca
   memória/HTTP/Android como **não medidos**. Opções anteriores continuam disponíveis.
2. `.github/workflows/android.yml`: agora executa essa graduação e o script de
   timeout já existente, arquivando `build/homologacao-volumes.json` e
   `build/homologacao-timeouts.json`. Não muda timeouts globais nem protocolo.
3. `HomologationHostedAuthTest`: exige, além de variante correta/arquivo privado,
   `NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED=YES`. Somente configurar uma chave de
   build não autoriza sessões. As duas workflows limpam essa variável e os caminhos
   live; nenhum teste de integração hospedada pode ser disparado por este CI.
   H02 agora exige RPC de estado aceita para B; H04 exige sucesso da mesma RPC
   antes dos casos sem JWT/inválido/revogado. Isso impede falsa aprovação quando
   o endpoint estiver sempre negado. São pré-condições preparadas/compiladas,
   ainda não executadas contra o hospedado.
4. Scripts locais exigem nomes de bancos descartáveis/PGHOST local; não transformar
   essas fixtures em scripts hospedados. O runner B–E só opera em emuladores próprios
   da CI. Nenhum `adb`, `pm clear`, uninstall ou restore foi executado contra os
   emuladores do proprietário.

## 5. Comandos e resultados desta rodada

Executados no cluster novo `/workspace/homologacao-consolidacao-pg`, socket local
`/workspace/pg-local`, porta **55433**, usuário de laboratório. Nenhum segredo
remoto foi utilizado. Os comandos Python abaixo usaram:

```bash
PGHOST=/workspace/pg-local PGPORT=55433 PGUSER=agent \
PGDATABASE=noctua_phase_e_test \
PSQL_BIN=/workspace/pg-local/root/usr/lib/postgresql/17/bin/psql \
python3 scripts/benchmark-phase-e.py --graduated
# Mesmo ambiente descartável:
python3 scripts/test-phase-e-timeouts.py
python3 scripts/test-phase-e-postgres.py
python3 scripts/test-phase-e-decisions.py
# test-phase-d-postgres.py: PGDATABASE=noctua_phase_d_test
```

A atribuição de ambiente deve ser repetida/exportada na sessão local para cada
comando; o exemplo não instrui executar os demais sem o guard local.

```bash
JAVA_HOME=/workspace/jdk17 ANDROID_HOME=/workspace/android-sdk \
ANDROID_USER_HOME=/workspace/.android GRADLE_USER_HOME=/workspace/.gradle \
NOCTUA_AUTH_LIVE_FILE='' NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE='' \
NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED='' \
./gradlew --offline --no-daemon testDebugUnitTest testHomologacaoUnitTest
```

Verificações adicionais executadas sem acessar dispositivo:

```bash
python3 scripts/test-reboot-disposable-avd.py
python3 -m py_compile scripts/reboot-disposable-avd.py scripts/test-reboot-disposable-avd.py
bash -n scripts/ci/validate-auth.sh
```

A prova nativa de reboot foi chamada pela rotina já existente
`scripts/ci/validate-auth.sh process`, exclusivamente no único AVD da CI; a
regressão completa usa `scripts/ci/validate-android.sh`. Não executar esses scripts
nos emuladores do proprietário nem exportar flags CI para contornar seus guardas.

Primeira tentativa Gradle foi bloqueada pela sandbox ao abrir o socket local do
daemon (`SocketException: Operation not permitted`), antes dos testes. Repetição
com acesso de rede do executor autorizado concluiu; não foi falha do aplicativo.

| Execução local nova | Aprovados | Condicionais | Falhas finais |
|---|---:|---:|---:|
| JVM debug, 288 casos | 283 | 5 | 0 |
| JVM homologacao, mesmos 288 casos | 283 | 5 | 0 |
| PostgreSQL D | 27 | 0 | 0 |
| PostgreSQL E paginação | 16 | 0 | 0 |
| PostgreSQL E decisões | 15 | 0 | 0 |
| Graduação SQL, quatro patamares | 4 | 0 | 0 |
| Timeout/rollback/retry SQL | 3 | 0 | 0 |

Não somar as duas variantes JVM como 566 casos únicos. Os cinco condicionais são
H01–H04 e a sonda live histórica; **não executados, não aprovados**. Os 58 SQL
focados repetem casos da regressão; os quatro patamares/três cenários adicionais
são provas de integração, não novos métodos JUnit.

### Medições novas — não são SLA

| Patamar | Registros / partes / tipos | Commit parede psql | Replays confirmados parede psql |
|---|---|---:|---:|
| 36 matrículas | 41 / 1 / 3 | 0,074 s | 0,013–0,015 s |
| D completo | 171 / 2 / 15 | 0,197 s | 0,011–0,014 s |
| E intermediário válido | 671 / 6 / 15 | 0,758 s | 0,014–0,016 s |
| E grande | 5.171 / 41 / 15 | 7,647 s | 0,039–0,047 s |

Em outra execução com orçamento explícito de 8 s, PostgreSQL EXPLAIN ANALYZE
mediu **7.611,832 ms** para o lote grande; retry após rollback **7.799,814 ms**.
Cancelamento determinístico no meio da escrita: SQLSTATE 57014, zero linhas,
versões, recibos e sequência; **41 partes preservadas**, reenvio e replay únicos.
Esses 8 s são orçamento da instrução SQL; não são timeout da API nem duração total
Android. O `readTimeout=20s` Android não amplia o orçamento PostgreSQL.
O timeout efetivo de noctua-homologacao não foi consultado nesta rodada; 8 s é
o orçamento local baseado no padrão documentado, não leitura nova da configuração.

O patamar grande não demonstra a meta de p95 <75% do orçamento (6 s se efetivo 8 s).
Uma execução que termina abaixo de 8 s não prova folga nem p95.
Na CI 42732848997, o commit natural foi 4.939,178 ms no PostgreSQL; porém o
primeiro retry após cancelamento não coube em 8 s e o diagnóstico local usou
20 s (concluiu em 4.926,814 ms). Isso **não** aprova retry no orçamento hospedado
de 8 s considerado no ensaio, mesmo com os três cenários de integridade concluídos. Não se ocultou essa
variação nem se alterou timeout de papel, função ou projeto. Não aumentar limites
hospedados automaticamente. Partes paginadas não dividem o commit indivisível.

Evidências novas sem payloads/credenciais: `docs/evidence/HOMOLOGACAO_LOCAL_20261109.json`.
Os hashes dos payloads recuperados nos quatro patamares coincidiram entre este
executor e as etapas SQL aprovadas da CI 42732848997; isso não aprova o Android
daquela execução que falhou. Capturas, relatórios e provas HTTP completas
continuam nos artefatos da CI.

### CI desta rodada

- Execução **42731594028** substituída após a correção das pré-condições positivas
  do runner; cancelamento não é aprovação nem regressão inferida.
- [42732848997](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42732848997),
  código `e007db311b25ed2e88a4446e8af49cf80626a856`: **falhou no Android**.
  JVM, PostgreSQL A–E, leitores de exportação/backup, graduação e timeout passaram.
  As três migrações focadas e a atualização de referência também passaram.
  O processo encerrou durante `AuthIsolationInstrumentedTest`, antes da suíte
  completa e das provas HTTP B–E. O artefato não contém stack trace de crash;
  não há base para atribuir causa ao produto nem chamar de simples timeout.
- Acrescentados diagnóstico `logcat -b crash`, eventos de encerramento e
  `dumpsys activity exit-info` antes da destruição do AVD; instrumentação raw para
  identificar o método; duas repetições obrigatórias dos oito testes de isolamento.
  Não existe retry que converta uma falha em sucesso; o primeiro crash/assertion
  continua encerrando o job. Nenhuma asserção removida ou timeout aumentado.
- Nova execução [42734103527](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42734103527),
  código `1f66134e1f05b5554673f7d0947758409f658d6c`: **cancelada no limite de 50 min**.
  A suíte Android **terminou aprovada**: XML com 424 casos únicos, **413 aprovados,
  11 condicionais, zero falhas/erros**. O console exibiu 435 ao finalizar; usa-se
  o XML, não esse contador de progresso, para contar os casos. Focada Auth 19/19,
  duas repetições obrigatórias 8/8 e 8/8, fontes 1,3/1,6, migrações e leitores
  de exportação passaram. O crash inicial **não se reproduziu** nessas execuções
  nem na suíte integral; sua causa histórica continua indeterminada, sem stack.
  Capturas de login/identificação de homologação e vinculação com fonte ampliada
  foram inspecionadas nos artefatos.
- O bloqueio posterior ocorreu entre `auth-process-write` (1/1 aprovado às
  22h43 de 09/11) e a leitura após reboot do AVD. A rotina anterior tinha comandos
  ADB sem limite efetivo; o coletor de diagnóstico também ficou em `waiting for
  device`. Não houve `auth-process-read`. **Correção do registro de progresso:** os artefatos
  completos confirmam que B–E HTTP já tinham passado (10/13/24/23 fases), antes da
  suíte genérica; a inspeção inicial apenas do trecho final do log não os mostrou.
  Isso é **execução incompleta**, não teste ignorado ou recuperação aprovada.
- `41fd989` limita chamadas ADB, recupera uma conexão local do host uma única vez
  e exige `boot_id` novo + `sys.boot_completed=1` antes da leitura protegida.
  Apenas um AVD QEMU da CI é aceito; outro dispositivo bloqueia o procedimento.
  Não limpa dados, reinstala apps ou repete a solicitação de reboot. Mesmo se a
  resposta ADB se perder, o teste só prossegue com prova independente do novo boot;
  depois a sonda original compara sessão, seleção, FJ e snapshot acadêmico.
  Diagnósticos também têm limite rígido para não bloquear a coleta até o timeout
  global. Sete testes **offline do harness**, sem ADB real, passaram; não são
  aprovação de reboot nativo.
- Nova execução [42740837445](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42740837445),
  código `41fd98964959d16ad2bd7a217277097d1f632b19`: **APROVADA**.
  O novo kernel/boot concluído foram comprovados após **26,937 s de conferência**
  (medidos depois da chamada ADB de solicitação), com recuperação
  única da conexão ADB do host, sem limpeza de userdata; escrita e leitura protegida
  **1/1 + 1/1** passaram. As 70 fases HTTP B–E e os dois checkpoints com morte real
  também passaram. Não há reboot/recuperação pendente na validação local final.
  O crash isolado da execução 42732848997 não se reproduziu nas duas execuções
  posteriores (focadas, repetições obrigatórias e suíte integral); a causa histórica
  sem stack permanece indeterminada, não foi atribuída artificialmente ao timeout
  nem declarada corrigida no produto.


### Resultado consolidado da CI final

| Prova | Aprovados | Condicionais | Falhas funcionais |
|---|---:|---:|---:|
| JVM, 288 casos | 283 | 5 | 0 |
| Android, 424 casos únicos | 413 | 11 | 0 |
| PostgreSQL v1/A/B/C/D/E1/E2 | 30/38/18/20/27/16/15 = **164** | 0 | 0 |
| HTTP Android em dois AVDs B/C/D/E | 10/13/24/23 = **70 fases** | 0 | 0 |
| Checkpoints de morte real/rollback/replay D/E | 2 | 0 | 0 |
| Segurança HTTP B/D/E | 9/32/41 verificações | 0 | 0 |
| Harness reboot, testes offline (não nativos) | 7 | 0 | 0 |
| Graduação/timeout SQL | 4 patamares + 3 cenários de integridade | 0 | 0 de integridade |

**Capacidade é resultado separado:** o lote natural de 5.171/41 partes **não coube
em 8 s** nesta CI. O teste de rollback/retry passou porque validou a atomicidade,
a preservação do estágio e o mesmo recibo no orçamento diagnóstico local de 20 s.
Isso não aprova o desempenho em 8 s. PostgreSQL reportou **13.014,198 ms** no retry
do lote natural e **8.249,907 ms** no retry após o bloqueio controlado. Nenhum
orçamento global/de papel/função hospedado foi aumentado. O benchmark sem orçamento
explícito mediu parede psql 0,101/0,283/0,898/**8,123 s** para 41/171/671/5.171;
os três replays grandes foram 0,071 s cada. Não são latências Android ou SLA Free.

A recuperação/reconciliação entre os AVDs terminou com 5.172 registros nos quinze
tipos, mesmo snapshot final e fingerprint
`b2ba3c74e7934490780af68f80ef0003b6d3d4006f32ad552ab618a19b6738bd`.
`e-lost`, decisões local/nuvem, backup cifrado antigo, matrícula remota posterior,
remanejamento/auditoria e reordenação permanecem aprovados no laboratório.

Os cinco condicionais JVM são H01–H04 hospedados e a sonda live legada: **não
executados**. Dos onze Android, as sondas parametrizadas B–E/atualização v1/processo
foram executadas separadamente e aprovadas; as três sondas com assinatura permanente
não foram executadas nesta branch. Não somar repetições/focadas como casos únicos.
Nenhuma prova usa sessões reais do Supabase hospedado.

- [CI aprovada 42740837445](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42740837445).
- [Resultados/capturas — artefato 13730028278](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42740837445/artifacts/13730028278).
- SHA-256 do ZIP baixado/conferido:
  `746d6418bc2bd35abe57d94a910b4fa0d1a60f3c3a8cd2140dfa6c47845eaea0`.
- Evidências centrais: `build/auth-reboot.json`, `auth-process-{write,read}.txt`,
  `auth-isolation-repeat-{1,2}.txt`, `homologacao-{volumes,timeouts}.json`,
  `phase{b,c,d,e}-network/`, XML JVM/Android e capturas com fontes ampliadas.
- O artefato `noctua-apk-teste` é DEBUG com ID oficial para CI descartável;
  **não é novo APK paralelo nem candidato para instalar no aparelho do proprietário**.
  Build paralelo e atualização estável foram corretamente ignorados.

## 6. Somente três ensaios manuais prioritários, após autorização

1. **Segurança com duas contas distintas — H01–H04.** Autorizar explicitamente as
   sessões de ensaio, disponibilizando credenciais apenas por arquivo/cofre privado,
   fora do Git/chat/artefatos. Codex poderá executar o runner já preparado, fixo em
   `sscexzxtqdcsqdlcytum`, e consolidar resultados. O proprietário confere a troca
   de conta/isolamento da interface em ambiente de teste. H03 só faz PATCH com
   predicado contraditório e flag específica; não modifica linhas. Nenhuma criação
   de usuário, allowlist, SQL ou revogação global de sessões. Casos não cobertos
   (expiração real/não confirmado/escrita cruzada efetiva) continuam explicitamente
   pendentes, não inventar sessões nem executar testes destrutivos hospedados.
2. **Recuperação hospedada em instalação adicional isolada — H05/H11 e observação
   de H13.** Criar um AVD descartável novo; usar o APK paralelo existente e a mesma
   conta fictícia, sem limpar/reinstalar nenhum dos dois emuladores atuais. Conferir
   inventário/IDs e amostras zero/null/FJ/diário/histórico/exportação. Começar pelo
   acervo fictício já existente, sem gerar cargas automaticamente. Registrar volume,
   duração e pendências reais; não testar 5.171/41 partes até haver plano autorizado
   e margem comprovada. Escalonamento e métricas hospedadas seguem pendentes.
3. **Backup fictício antigo e reconciliação — H12.** Somente no novo AVD descartável,
   guardar backup cifrado fictício, fazer pequena alteração/novo registro pela
   interface da outra instalação mediante autorização e restaurar a cópia antiga.
   Confirmar pausa, conferência explícita e preservação da novidade remota. Nunca
   restaurar sobre o acervo real nem tratar ausência no backup como exclusão.

Não repetir manualmente falhas injetadas, perda de resposta, corrupção de grafo ou
morte forçada: já são automatizadas exclusivamente no laboratório descartável.
Não solicitar senhas, links, códigos, tokens, service_role ou chaves privadas no chat.

## 7. Decisão e bloqueadores

**Pode avançar para os ensaios hospedados controlados acima**, após autorização
específica. A CI local final foi aprovada e o reboot/data-read foi comprovado. Os relatos confirmam a evolução
funcional e não justificam refazer A–E. Não foi identificado defeito funcional novo
nos testes JVM/SQL locais; o crash Android inicial não se reproduziu, mas sua causa
continua indeterminada e foi mantida como ressalva histórica. A automação de reboot
concluiu a prova nativa sem apagar dados.

**Não está aprovada para promoção estável/produção.** Permanecem bloqueadores dessa
liberação: evidência real H01–H04 com duas contas, recuperação hospedada completa,
reconciliação hospedada, capacidade/timeout Free e futura atualização assinada com
preservação dos dados reais. Nenhum desses bloqueadores autoriza alterar produção.
A falha observada de capacidade em 8 s para 5.171 impede considerar esse patamar
homologado. Antes de ensaio hospedado grande, perfilar/otimizar preservando os
validadores ou propor divisão de deltas realmente independentes com prova de grafo;
nunca fragmentar remanejamentos/históricos nem aumentar limites automaticamente.
Backup administrativo independente/contingência e disponibilidade da assinatura
permanente continuam exigindo processo próprio; sincronização não substitui backup.

Não alterar as duas regras de remanejamento. Não remover proteção de backups,
resolver o conflito legado ou ativar sincronização de dados reais automaticamente.
Nenhum próximo passo inclui NOcTuA Escola, release, merge ou migração hospedada.

## 8. Commits e arquivos desta execução

- `ad9a90abd95f8d95e03a7124f8d24563f899828d`: graduação SQL, timeout na CI e gate explícito hospedado.
- `63ea459`: matriz H01–H13, relatos do proprietário e resultados locais iniciais.
- `e007db311b25ed2e88a4446e8af49cf80626a856`: pré-condições positivas de RPC em H02/H04.
- `1f66134e1f05b5554673f7d0947758409f658d6c`: diagnóstico de crash e repetições obrigatórias de isolamento.
- `a21ff20`: preservação da execução Android falha, risco do retry de 8 s e continuidade.
- `41fd98964959d16ad2bd7a217277097d1f632b19`: ADB com limite, prova de boot novo e testes offline do harness.
- Commits documentais finais: consultar o histórico desta página; não alteram o código validado.

Arquivos (nenhum pertence ao código funcional de produção ou às migrações):

- `.github/workflows/android.yml`: automação/artefatos locais e bloqueio de credenciais live.
- `.github/workflows/homologacao.yml`: gate hospedado limpo no build.
- `scripts/benchmark-phase-e.py`: graduação e conferência integral paginada.
- `scripts/ci/validate-auth.sh`: diagnósticos e estabilidade do isolamento.
- `scripts/reboot-disposable-avd.py`: reinício verificável do único AVD descartável.
- `scripts/test-reboot-disposable-avd.py`: sete casos offline da automação (não provas nativas).
- `app/src/test/java/br/com/diariodeclasse/HomologationHostedAuthTest.kt`: consentimento e provas positivas antes das negativas.
- `docs/CONSOLIDACAO_HOMOLOGACAO_0220.md`: este relatório/matriz/roteiro.
- `docs/FASE_E_0220.md`: nota temporal e link para o estado atual.
- `docs/HOMOLOGACAO_SUPABASE_0220.md`: alvo atual, callback e distinção do checklist histórico.
- `docs/ANDROID_HOMOLOGACAO_0220.md`: estado atual e autorização do runner.
- `docs/evidence/HOMOLOGACAO_RELATOS_20261109.json`: dez relatos, não resultados automatizados.
- `docs/evidence/HOMOLOGACAO_LOCAL_20261109.json`: contagens, hashes e medições sem payloads/credenciais.


### Preparação específica H12 — 13/11/2026

[Executor, limites e autorização independente](H12_RESTAURACAO_RECONCILIACAO_0220.md).
Reutiliza a prova E com volume pequeno e AVD novo. H12 hospedado continua pendente;
a decisão de manter nuvem grava auditoria e exige autorização distinta do H05.
Os seis tipos ausentes no H05 #201 não ganham cobertura hospedada por testes locais.
