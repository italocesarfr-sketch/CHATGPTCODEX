# H13.2 — otimização estrutural da validação integral

13/11/2026, branch `sync/academic-complete-0220`. Base `2273219440cfd1dcb1fd57732da0f38799b163c5`; preserva H13/#204 e o diagnóstico H13.1/#205. Apenas infraestrutura descartável, fixtures fictícias e, depois da admissão focada, CI. Nenhuma carga/migração hospedada, alteração de produção, assinatura, `main`, instalação do proprietário ou interface.

## Implementação e equivalência

O protocolo continua executando o grafo **inteiro**, as projeções, guarda histórica, CAS, auditorias, recibos e transações originais. Não há certificado de validade, índice persistente, nova fila, novo escopo ou atalho por registro.

Migração proposta `supabase/migrations/202611130002_h132_structural_history.sql`, **não aplicada no Supabase**: depois de E4 e H13, substitui somente `noctua_d_get`, `noctua_d_history_guard` e `noctua_d_graph`, todos privados. Uma pré-condição verifica a presença dos validadores anteriores. Não altera schema Room, tabelas, dados, RLS, grants, RPCs, titularidade, defaults de timeout ou regras acadêmicas.

- Getter: a mesma seleção entre mapa JSONB e subconsulta escalar de array, executada em PL/pgSQL para evitar planejamento repetido do caminho de mapa. Mantém SQL NULL distinto de JSON null, erros de cardinalidade em duplicidades e erro original em entradas escalares, inclusive com filtros SQL NULL.
- Guarda histórica: constrói uma vez, dentro da chamada/mesma transação, um índice com `noctua_d_index(p_after)` e o reutiliza nas buscas. Todos os loops, comparações, condições de rejeição, dados antes/depois e mensagens acadêmicas permanecem. Um teste normaliza **somente** as expressões de representação e exige igualdade textual com os predicados originais.
- Índice só é utilizado quando os registros posteriores têm forma compatível e identidade única. Entradas malformadas/duplicadas usam o caminho original. Chaves dinâmicas com números, nulls, booleanos ou estruturas também usam arrays: **a igualdade JSONB de `1` e `1.0` não equivale à igualdade de seus textos**. Chaves compostas somente por strings têm representação textual canônica e podem usar o mapa sem alterar a igualdade.
- Nenhum índice sobrevive à chamada ou é reaproveitado entre transações/dispositivos. As projeções permanecem inalteradas. O grafo E4 materializa, na própria chamada, subconjuntos de remanejamentos e recomposições; três buscas repetidas usam esses subconjuntos, mantendo todos os filtros originais. Isso é pushdown de seleção, não memoização de validade anterior. O teste também exige igualdade textual de todos os predicados do grafo após remover apenas essas expressões de representação.

Duas diferenças foram encontradas no protótipo e corrigidas **antes da integração**: a chave numérica acima e a possibilidade de um plano PL/pgSQL eliminar a leitura de uma entrada escalar quando o comparador é SQL NULL. Testes reproduziram ambas. Elas não foram tratadas como defeitos do produto anterior nem escondidas removendo casos. O candidato corrigido preserva os resultados/SQLSTATEs originais nessas entradas. A primeira série apenas de lookups não atingiu o critério de admissão: 41 registros 37,3 → 43,6 ms e bootstrap com histórico ampliado 6.679 → 6.563 ms (menos de 10%). Ela foi recusada; os critérios não foram relaxados. O perfil justificou a revisão adicional dos subconjuntos do grafo e a construção sob demanda do índice histórico.

## Evidências que sobrevivem a falhas

`benchmark_telemetry.py` grava checkpoints JSON sanitizados via arquivo temporário 0600, fsync e renomeação atômica. Testes cobrem falha de asserção e **saída abrupta real de um processo filho**. Os checkpoints guardam estágio, modo SQL/HTTP, caso, volume/partes, repetição, variante, tempo e perfil de funções. Nunca guardam requisições, payloads, credenciais, tokens ou corpos privados de erro.

Instrumentação acrescentada aos benchmarks H13, delta, locks e ensaio E de timeout, além do novo comparativo H13.2. Journals são artefatos mesmo quando o stdout JSON final fica vazio. O perfil após cancelamento é marcado parcial; em HTTP pode haver atraso de flush das estatísticas. Não inventar `Execution Time` de uma operação cancelada. O último estágio anterior a uma morte do processo não significa confirmação.

O custo de checkpoint fica fora da seção cronometrada de commit SQL/HTTP. O tempo de estágio/parede do harness pode incluir I/O de instrumentação e não é diretamente comparável ao antigo estágio sem journal. A seção de limpeza também é registrada: métricas concluídas não significam que a limpeza/execução completa passou.

## Método reproduzível e critérios de admissão

Reutiliza construtores B–E e o histórico ampliado H13.1. Três repetições alternam antes/depois para 41, 171, 671, 5.171 registros e 5.171 com mais 320 registros históricos virtuais. As duas maiores bases também recebem alteração de **uma nota**, sem substituir o banco. Ambos os lados mantêm o cache H13; muda somente a representação das buscas e dos subconjuntos dos três helpers.

- SQL: EXPLAIN ANALYZE/BUFFERS, perfis por função, parede separada, **statement_timeout 8s** explícito.
- HTTP: PostgREST real em loopback, sessões JWT fictícias, papel autenticado com 8s efetivamente conferidos. O probe chama a RPC original; tempo do corpo é separado da parede HTTP. Não é Supabase Auth hospedado ou latência de rede móvel.
- Mesmo estado/IDs: recibos, revisões, head, conteúdo e hashes recebidos/paginados conferidos. Três reenvios por confirmação não podem criar novos dados/versões. Os hashes do estado inteiro devem permanecer após cancelamento e replay.
- Mesmo conjunto de validadores: compara contagens de chamadas acadêmicas nas execuções SQL completas antes/depois. Um timeout anterior é falha operacional, **não rejeição acadêmica** nem resultado equivalente inventado.
- Diferenciais: getter gerado/adversarial, guarda histórica, bootstrap dos 15 tipos, nota null/zero/limites, atividade/máximo, auditorias imutáveis, transferência da origem para histórico e lote inválido com rollback. Compara resultados, SQLSTATE, hash de mensagem e metadados de escrita dentro de probes sempre revertidos.
- Regressões existentes D/E/decisões são executadas sem remover casos e com o candidato efetivamente instalado durante toda a suíte. Incluem KEEP_LOCAL/KEEP_REMOTE/reconciliação e evidência obsoleta.
- Transporte: reutiliza o teste HTTP H13.1 para duas sessões concorrentes, CAS, edições distintas, resposta descartada/replay, arquivo pendente offline, histórico inválido, conta B e sessão inválida. A recarga desse harness não substitui morte de processo Android.
- Admissão: candidato deve completar todas as confirmações em 8s; mediana das maiores bases deve melhorar pelo menos 10% na amostra. Nas pequenas, não aceitar regressão maior que 10%/5ms. Esses critérios são de investigação, não SLA institucional. Cancelamentos anteriores continuam no relatório. Só após admissão local executar a regressão Android/PostgreSQL completa.

Exemplo, exclusivamente em fixtures PostgreSQL/PostgREST locais existentes:

```sh
export PGHOST=127.0.0.1 PGPORT=5432 PGUSER=postgres PGDATABASE=noctua_phase_e_test
mkdir -p build/h132
python3 -m unittest discover -s scripts/h132 -p test_telemetry.py
# JVM deve ter gerado app/build/academic-phase-d-validation/source-deletion.json
python3 scripts/h132/focused.py
python3 scripts/h132/regressions.py
python3 scripts/h132/benchmark.py --mode sql --repetitions 3 > build/h132/sql.json
# Preparar PostgREST local pelo runner existente, sequencialmente.
export PGDATABASE=noctua_phase_b_test
python3 scripts/h132/benchmark.py --mode http --repetitions 3 > build/h132/http.json
```

Os bancos/host são restritos por allowlist local. Nada disso pode ser apontado a `noctua-auth` ou `noctua-homologacao`. Não dividir uma movimentação para fazê-la caber: cada lote confirmado permanece atômico.

## Estado da execução

Resultados finais, commits, admissão, comparações, artefatos e situação da regressão serão consolidados depois dos ensaios. Neste ponto não há alegação de aprovação completa, implantação hospedada ou capacidade institucional.

## Riscos e contingência

A guarda pode ganhar uma materialização O(n) em memória na chamada, somente se os loops originais precisarem consultar o estado anterior. O grafo mantém ainda duas seleções O(remanejamentos + recomposições), obtidas do mesmo snapshot. Buffers/I/O/temporários e configurações são medidos; isso não equivale a pico de RAM. Acervos maiores, densidade histórica, CPU compartilhada, contenção e transporte externo continuam riscos. Não ampliar timeouts para obter CI verde.

Se a equivalência ou ganho falhar, conservar os helpers funcionais anteriores e o candidato apenas como investigação. Em homologação administrativa futura, inventariar versões e guardar as três definições anteriores antes de qualquer implantação. Reverter somente essas definições é suficiente para remover o candidato, pois não existe migração de dados. Não reaplicar migrações antigas completas nem mexer em guardas de sessão/RLS. O pacote hospedado anteriormente revisado não foi reescrito nesta tarefa; nova implantação depende de revisão/autorização explícita.
