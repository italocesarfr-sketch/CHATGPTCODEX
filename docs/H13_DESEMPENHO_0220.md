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
SHA validado, Android e testes de regressão serão consolidados após o workflow.
**Estado atual: candidato local, CI/Android ainda pendentes.**

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
