# H13 — diagnóstico e desempenho acadêmico incremental

**13/11/2026 — branch `sync/academic-complete-0220`.** Base: `64d559d`.
Somente PostgreSQL/PostgREST descartáveis e novo AVD da CI. Nenhuma carga,
configuração ou migração no Supabase hospedado; produção e dispositivos do
proprietário permanecem intocados. Não é aprovação institucional de capacidade.

## Gargalo e alteração proposta

O perfil inicial PostgreSQL 17.11 reproduziu o commit de 5.171 registros/41
partes em **7.090 ms** (uma observação, cache/CPU próprios). A hipótese de cópias
JSONB no `noctua_d_get` teve ganho pequeno e não foi incorporada. O perfil apontou
**41.434 chamadas a `noctua_academic_definition`**, 1.256 ms de tempo próprio,
além de custo de planejamento associado aos consumidores SQL. O helper atual
é uma função SQL com `SET search_path` e um literal JSONB grande.

A migração aditiva `202611130001_h13_registry_cache.sql` altera **somente** esse
helper privado para PL/pgSQL, retornando constantes por tipo. Seus quinze
resultados, unknown/null, proprietário, privilégios, imutabilidade e search_path
são preservados. `COLLATE pg_catalog."C"` mantém a comparação exata dos nomes,
inclusive se o chamador utilizar collation não determinística. Não altera RPCs,
predicados/validações do grafo, referências, auditorias, escopos, RLS, CAS,
recibos ou ordem de locks. Não adiciona índices/tabelas nem modifica Room/app.

No perfil candidato inicial, o commit foi **4.213 ms**, com as mesmas 41.434
chamadas ao helper, agora 31 ms próprios, e mesmas 15.527 validações acadêmicas.
Esse ensaio diagnóstico não é a série comparativa nem um SLA.

## Método reproduzível

Reutiliza `benchmark-phase-e.py`, construtores B/D/E, todos os quinze tipos,
41/171/671/5.171 registros, partes de 128 e o protocolo existente. Cada comparação
alterna a ordem antes/depois, cinco repetições por faixa e implementação, mesmas
identidades/payloads e confirmação atômica. **Não divide um lote acadêmico**.
A variante "antes" repõe apenas o helper original em um banco descartável;
"depois" aplica a migração candidata. As demais funções são as mesmas E4.

- SQL: `EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)` fornece tempo de execução; parede
  inclui conexão `psql` e fechamento da transação. Captura perfis por função,
  buffers, spills e igualdade integral dos registros recebidos/paginados.
- HTTP: PostgREST real local, JWT/sessões fictícios, orçamento **8s** efetivamente
  conferido por RPC de diagnóstico. Wrapper de medição **somente da fixture**
  chama a RPC original e mede seu corpo no servidor. Parede inclui cliente,
  codificação/decodificação, resposta, rede e COMMIT final. O residual não é
  denominado "latência de rede pura". Wrappers/ajuste do papel local são removidos
  ou restaurados ao terminar; não fazem parte da migração ou do APK.
- Android: `H13RoomBenchmarkTest`, três bancos Room novos por faixa, fixture E
  existente, codec, partes persistidas, aplicação atômica e replay confirmado.
  Mede codec, último fragmento/aplicação, persistência das partes, CPU do processo
  e amostras PSS/heap. PSS é amostra do processo, **não pico comprovado**. Não
  infere tempo Android subtraindo duração HTTP de uma espera de UI.
- `benchmark-h13-delta.py`: uma nota por operação em acervo já existente de
  5.171 registros, cinco alternâncias antes/depois. Expõe o custo residual de
  validar todo o grafo mesmo numa edição pequena, sem substituir o banco.
- `benchmark-h13-locks.py`: retém um lock real de A; B confirma de forma
  independente. A deve expirar em 8s, não deixar escrita parcial e confirmar a
  **mesma operação** após liberação, com recibo único.

Os dados publicados são tempos, contagens, nomes de funções e hashes agregados.
Nenhum aluno, credencial, token ou UUID de operação aparece nos JSONs de métricas.
Estatísticas incluem mínimo/mediana/máximo; p95 amostral por nearest-rank com
cinco observações equivale ao máximo e **não estima p95 institucional**.

## Resultados locais preliminares — cinco repetições por variante

Tempos em milissegundos; PostgreSQL 17.11, buffers compartilhados 128 MB, trabalho
por operação 4 MB. Séries foram sequenciais em cluster novo, com fsync normal.
A variação inclui cache, planejamento, checkpoint e CPU compartilhada do executor.

| Registros/partes | SQL antes mediana | SQL depois mediana | SQL depois mín.–máx. | HTTP corpo RPC antes/depois mediana |
|---|---:|---:|---:|---:|
| 41 / 1 | 62 | 47 | 41–61 | 41 / 27 |
| 171 / 2 | 203 | 147 | 119–164 | 164 / 111 |
| 671 / 6 | 641 | 474 | 434–492 | 656 / 451 |
| 5.171 / 41 | 6.218 | 4.611 | 4.328–4.640 | 5.983 / 4.382 |

Em 5.171, redução da mediana: **25,8% SQL** e **26,8% no corpo RPC por HTTP**.
HTTP candidato variou de 4.284 a **6.017 ms**, ainda com variação material.
As 40 primeiras confirmações por camada passaram com orçamento de 8s; cada
uma teve três reenvios confirmados, recibo único, recuperação exata e isolamento
negativo com B. Não confundir 120 replays com 120 novos lotes.

Input JSON do maior caso: aproximadamente **1,04 MB**; tabelas/índices NOcTuA
ocuparam cerca de **10,3 MB** na fixture HTTP. Isso não inclui backup administrativo,
toda a retenção futura, WAL, replicas ou overhead do serviço hospedado.

Lock local: B confirmou enquanto A estava retida em 96 ms. A atingiu SQLSTATE
57014 em 8.012 ms; zero registros/versões/recibos parciais e parte preservada.
Após liberação, mesma operação confirmou em 56 ms SQL, replay em 4 ms, um recibo.

A edição de uma única nota no acervo de 5.171 registros (cinco repetições por
variante) teve mediana SQL **4.682 ms antes / 3.832 ms depois**. O candidato
variou 3.670–4.078 ms. Continuaram sendo executadas **5.187 validações** por
operação; todos os outros registros ficaram iguais. Ganho parcial de 18,2%,
mas latência ainda alta para uma alteração pequena em infraestrutura compartilhada.

Sete testes H13 de contrato/ACL/collation/migração/recibos passaram localmente,
e três provas de timeout/rollback/reenvio passaram sem fallback de 20s.
Essas medições foram executadas no workspace antes do commit; resultados da CI,
SHA validado, Android e regressão completa estão consolidados abaixo.
**Estado atual: otimização validada em ambiente automatizado descartável; não aplicada no hospedado.**

## Resultado do job H13 na CI #204

Fonte `9677b022695af4c5579e0598c8c84fccaef3d5cd`.
[Workflow #204](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43450126832)
**aprovado integralmente**: job H13, regressão completa e PostgreSQL v1.
H01–H04, H05, H12 hospedados, variante de distribuição e release ficaram excluídos. PostgreSQL da CI:
**17.12**, diferente do **17.11** local; `shared_buffers=128MB`, `work_mem=4MB`.
Não juntar as séries como se fossem uma única máquina/versão.

[Artefato](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43450126832/artifacts/14053035022)
e [proveniência e métricas preservadas](evidence/H13_20261113/provenance.json).
ZIP SHA-256 `51d761894d48995dee658201999f59e7c1c1bfaa9a7b7482207b8136f56dd1b9`;
foram conferidos nomes dos seis arquivos, hashes, origem e ausência de contas/tokens.

### Commit SQL e HTTP — cinco observações por variante/faixa

Milissegundos; corpo da função para HTTP, `Execution Time` de EXPLAIN para SQL.
Os dois excluem o COMMIT final; tempos de parede completos constam dos JSONs.

| Registros / partes | SQL antes → depois mediana | SQL candidato mín.–máx. | HTTP antes → depois mediana | HTTP candidato mín.–máx. |
|---|---:|---:|---:|---:|
| 41 / 1 | 46 → 36 | 36–38 | 33 → 24 | 23–31 |
| 171 / 2 | 159 → 118 | 116–128 | 140 → 107 | 99–112 |
| 671 / 6 | 598 → 418 | 416–559 | 550 → 397 | 391–409 |
| 5.171 / 41 | 6.908 → 5.877 | 4.116–5.993 | 7.038 → 6.017 | 5.824–6.246 |

No maior caso, ganho de mediana **14,9% SQL / 14,5% HTTP**; inferior aos 26%
locais, com variação material. Todas as confirmações antes/depois ficaram abaixo
de 8s nesta amostra; **a falha antiga de 8,647s não se reproduziu aqui**. Isso
não invalida aquela falha nem comprova a capacidade de um ambiente Free diferente.
O máximo HTTP candidato de **6,246s** deixa apenas **1,754s** até 8s, antes de
considerar outros perfis de dados, CPU/locks/contenção e variação externa.

Por camada: 40 primeiras confirmações e 120 reenvios confirmados, com igualdade
integral, uma sequência/recibo por lote e negativas de conta B. SQL replay grande:
mediana 33 ms; HTTP replay grande: 28 ms no corpo da função. A otimização não
pretende acelerar esse caminho já existente de recibo confirmado.

Para 5.171: estágio mediano SQL 2.267 ms / HTTP 819 ms; recebimento/verificação
paginada do harness SQL 2.479 ms / HTTP 1.096 ms. São transportes e protocolos de
medição distintos; **não subtrair uma série da outra para calcular custo de rede**.
Parede do commit HTTP candidato: mediana 6.020 ms; o residual de cerca de 3 ms
inclui cliente, resposta, transação e loopback, não representa rede móvel/TLS.

Edição incremental de **uma nota** em conta de 5.171: mediana SQL
**4.154 → 3.533 ms**, candidato 3.325–3.644 ms, demais registros preservados.
Continua caro porque o grafo global é validado integralmente.

Lock real na CI: B confirmou em **77 ms** enquanto A estava retida. A cancelou
em **8.038 ms**, rollback integral, parte intacta, mesma operação confirmou depois
em **37 ms SQL**, um recibo e replay de **4 ms**. São identidades sintéticas
com guardas/claims locais; não é benchmark de Supabase Auth.

### Android Room/codec — três observações por faixa, sem rede

**Um teste Android executado, zero falhas/ignorados**, com doze ensaios Room.
Não foi repetido no corpus geral sem opt-in. Valores abaixo são medianas em ms.

| Registros / partes | Serializar | Decodificar | Receber/verificar | Aplicar último fragmento | Replay confirmado |
|---|---:|---:|---:|---:|---:|
| 41 / 1 | 6 | 5 | 103 | 92 | 29 |
| 171 / 2 | 17 | 14 | 259 | 191 | 58 |
| 671 / 6 | 42 | 51 | 722 | 465 | 183 |
| 5.171 / 41 | 264 | 366 | 5.329 | 3.015 | 1.179 |

O recebimento grande variou **5.328–5.384 ms**; inclui coleta de memória e
asserções de parcialidade. Apenas as chamadas de persistência das partes antes
da última somaram mediana **1.815 ms**. Não é tempo SQL remoto nem uma latência
fim a fim do app. CPU de todo o processo durante o recebimento: mediana
**6.055 ms** (threads concorrentes podem superar o tempo de parede).

PSS amostrado no maior caso: **133–150 MiB**, heap Java limitado a **192 MiB**;
heap Java utilizado ao fim: 14–39 MiB. Não mede pico exato. GC/aquecimento e ausência
de uma Activity real impedem extrapolar memória para aparelhos físicos com UI.
Cursor permaneceu zero e tabelas vazias até o último fragmento de cada lote;
IDs/payloads/hashes, chaves estrangeiras, filas e replay confirmados em todos.

### Interpretação de capacidade

Há ganho consistente nesta implementação do helper; não houve eliminação de
validações. No perfil SQL local comparável, cerca de 349 mil buffer hits, seis
blocos lidos e zero spills permaneceram; as 15.527 validações e 48.603 verificações
de forma também permaneceram. O ganho é sobretudo CPU/execução do helper.
O detalhamento exclusivo do planejamento interno não foi medido por um profiler
do otimizador; tempos inclusivos de funções não devem ser somados.

**Não aprovado para uso institucional amplo por capacidade.** A margem não está
comprovada para concorrência sustentada, CPU/quotas Free, bases anuais maiores,
muitos remanejamentos/históricos ou aparelhos físicos. O p95 amostral HTTP de
6,246s sequer atende a meta de margem de 6s sugerida no roteiro anterior; cinco
amostras não sustentam SLA. A fixture grande tem duas turmas e poucos históricos;
mesma contagem com outro perfil de dependências pode ser mais cara.

Próximo passo técnico: medir corpus local com maior diversidade de turmas,
linhagens e históricos, e projetar validação incremental por conjunto dependente
com equivalência demonstrada, sem deixar de validar o grafo por conveniência.
Não executar carga hospedada ou aplicar a migração candidata automaticamente.

## Regressões e segurança

- `test-h13-postgres.py`: igualdade dos quinze contratos e valores inválidos,
  ACL/owner/RLS/search_path, collation, migração idempotente, recibos positivos e
  negativos já confirmados, auditorias e partes interrompidas preservadas.
- Suítes E/D/decisões e HTTP existentes continuam usando os mesmos predicados;
  fixtures passam a aplicar H13 depois de E4. O benchmark pré-E4 continua
  reproduzível com `benchmark-phase-e.py --baseline` e restaura o candidato ao sair.
- O teste de timeout existente deixa de usar fallback diagnóstico de 20s:
  recuperação deve finalizar sob os mesmos 8s; caso contrário é bloqueador de
  capacidade. Não aumenta prazos HTTP Android nem configura timeouts hospedados.
- CI normal permanece obrigatória, com JVM/Android, migrações, backups,
  exportações e dois AVDs A–E. H13 é job separado para não consumir o orçamento
  da regressão. Sem Environment/Secrets, contas ou teste hospedado.

## Regressão consolidada e commits

- **JVM:** 298 casos, **293 aprovados / cinco ignorados / zero falhas**.
  Os cinco condicionais Supabase Auth/H01–H04 não são revalidados por esta CI.
- **Android geral:** 433 casos, **416 aprovados / 17 condicionais ignorados /
  zero falhas/erros**, conferidos nos XMLs. Lista completa de ignorados e contagens
  em [regression.json](evidence/H13_20261113/regression.json). Não contar
  condicionais como aprovados.
- Além da suíte geral, **70 fases HTTP Android** em dois AVDs: B 10, C 13,
  D 24 e E 23, com duas mortes de processo controladas em recebimento (D/E),
  rollback/replay posterior obrigatório. Essas mortes não são testes ignorados.
  Provas de rede são condicionais na suíte geral, mas executadas separadamente;
  não somar todos os focused tests novamente como casos únicos da suíte.
- **H13:** sete testes PostgreSQL próprios; um teste Android focado com doze
  ensaios Room, efetivamente executado. O mesmo teste é ignorado na suíte geral
  sem opt-in, evitando repetir carga por acidente. 40 commits iniciais e 120
  replays **por camada** SQL/HTTP, dez deltas, e prova de contenção/timeout.
- **PostgreSQL acadêmico da regressão:** A 38, B 18, C 20, D 27, E1 16,
  decisões E2/E3 15: **134 testes**, sem falhas. V1 também passou em job separado.
- **Timeouts:** três cenários aprovados, inclusive cancelamento depois de iniciar
  gravações. Rollback e retry ficaram em **8s**, sem fallback ampliado. Graduação
  anterior 41/171/671/5.171 e idempotência/payloads completos passaram novamente.
- Backups cifrados, migrações Room, isolamento, correções, remanejamentos,
  horários, relatórios, XLSX/CSV/TSV e atualizações com assinatura descartável
  passaram na regressão existente. A assinatura permanente e a instalação original
  c40 não foram usadas para instalar esta otimização; não anunciar esse upgrade.
- Hash final igual nos dois AVDs, incluindo 5.172 registros ao fim da reconciliação E:
  `b2ba3c74e7934490780af68f80ef0003b6d3d4006f32ad552ab618a19b6738bd`.

[Artefato da regressão](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43450126832/artifacts/14053727138),
ZIP SHA-256 `6c22be871809a18a62a08c4707f39df9ee3bb6780012a0ff08937ed0a876abb5`.
XMLs/contagens, resumos das fases e digests foram conferidos. As duas cópias ZIP
foram verificadas por SHA-256; somente métricas/contagens foram persistidas no Git.

Também passaram localmente 27 testes D sobre a combinação E4 + helper H13,
além dos sete próprios, 16 E, 15 decisões e três cenários de timeout. Sem mudança
nas asserções acadêmicas ou nos validadores para obter aprovação.

**Commit de implementação e código validado:**
`9677b022695af4c5579e0598c8c84fccaef3d5cd`.
A consolidação posterior modifica somente este documento, referências e
`docs/evidence/H13_20261113/`. Não executa outra CI/carga hospedada.

Arquivos de implementação/teste modificados:

- `supabase/migrations/202611130001_h13_registry_cache.sql` — helper equivalente.
- `scripts/benchmark-h13.py`, `benchmark-h13-delta.py`, `benchmark-h13-locks.py`
  — medições, CAS/replay, snapshots e lock real.
- `scripts/test-h13-postgres.py` — equivalência/ACL/imutabilidade/recibos/collation.
- `scripts/test-phase-e-postgres.py`, `phase-b-http-fixture.py` — H13 nas fixtures.
- `scripts/benchmark-phase-e.py` — preserva baseline original e restaura candidato.
- `scripts/test-phase-e-timeouts.py` — remove fallback de 20s, mantém retry em 8s.
- `app/src/androidTest/.../H13RoomBenchmarkTest.kt`,
  `scripts/ci/benchmark-h13-android.sh` — aplicação Room e métricas sem rede.
- `.github/workflows/h13-performance.yml`, `.github/workflows/android.yml` —
  job descartável sem Secrets; regressão original preservada.

Não há mudança em `app/src/main`, Room 14, formato de backup 12, versão/código do
APK (0.22.0-dev-e2/c45), assinatura, identidade ou cálculo acadêmico. A migração
SQL é aditiva e permanece somente no repositório/fixtures locais.

## Limites e decisão institucional

A otimização reduz planejamento/alocação de definições; **o grafo completo da
conta continua sendo validado**. Não substitui um projeto de validação incremental
com fronteiras comprovadas, nem autoriza ignorar histórico para ganhar tempo.
A edição de uma nota, crescimento das auditorias e concorrência entre dispositivos
da mesma conta podem permanecer caros. Locks consomem o orçamento da instrução.

Preservar bloqueio seguro quando houver: sessão inválida/revogada, conta não
admitida, escopo errado, conflito CAS, grafo/identidade inválidos, alterações em
auditorias imutáveis, manifesto/parte divergente, limites existentes excedidos
(16.384 mudanças, 128 partes, 16 MiB por lote; limites de linha e inbox próprios),
ou timeout. Não reconhecer ACK em falha/resultado incerto. Reconsultar recibo e
reusar operação, mantendo partes e versões; jamais reenviar com identidade nova.

Gate de homologação: não liberar volume que exceda orçamento/margem de segurança
nas condições do destino. O app **não ganha um limitador dinâmico por p95** nesta
mudança; o gate operacional não deve ser confundido com uma função implementada.
Nem o plano Free real, CPU/RAM/concorrência, latências TLS, quotas, retenção ou
SLA foram medidos nesta rodada. Não autorizar carga hospedada com estes resultados.

A migração candidata fica **não aplicada** nos projetos hospedados. A implantação
futura exige revisão/autorização separada, depois de E4. Não modifica o pacote A–E
já publicado ou seu checksum. Contingência: não habilitar candidato em produção;
conservar filas/partes/recibos e voltar o helper apenas por migração administrativa
revisada. Não limpar dados, descartar históricos ou ampliar timeout.

## Comandos e evidências

```text
python3 scripts/test-h13-postgres.py
python3 scripts/benchmark-h13.py --mode sql --repetitions 5
python3 scripts/benchmark-h13-delta.py
python3 scripts/benchmark-h13-locks.py
python3 scripts/benchmark-h13.py --mode http --repetitions 5
python3 scripts/test-phase-e-timeouts.py
```

Todos os scripts exigem host loopback e nome de banco descartável permitido.
O job `.github/workflows/h13-performance.yml` arquiva métricas JSON e resultado
da regressão, sem APK. A instrumentação só aceita opt-in H13 no novo AVD da CI.
A ausência desse opt-in na suíte geral é condicional ignorado, não aprovação;
a prova focada deve executar efetivamente seu único teste e doze ensaios Room.
