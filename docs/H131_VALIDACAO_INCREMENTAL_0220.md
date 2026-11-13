# H13.1 — validação incremental: investigação e barreira de segurança

13/11/2026. Branch `sync/academic-complete-0220`; base `b95940813b654b99b0512bd401daea272226dff2`.
Preserva a otimização H13 e a evidência aprovada da [CI #204](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43450126832), código `9677b02`.

**Decisão: manter o validador integral. A equivalência do atalho estudado não foi demonstrada para estados sem certificação de validade.** Não há migração nova, substituição de função pública, alteração de Room, Android, interface, autenticação, escopos ou RLS. O laboratório não é componente de produção. Não foi executado no Supabase hospedado.

## Dependências reais e alcance da alteração

O inventário verificável está em [H131_DEPENDENCIAS_0220.json](H131_DEPENDENCIAS_0220.json): chaves, referências físicas e unicidades vêm de `supabase/contracts/academic-v2.json`; as obrigações semânticas foram conferidas nas guardas C/D e no grafo indexado E4. Referências físicas não bastam para definir o fecho de validação.

| Tipo | Dependências que determinam o conjunto afetado |
|---|---|
| `classes` | quatro bimestres; compatibilidade de ano/etapa/disciplina/escala/FJ e calendário das movimentações; contexto histórico |
| `terms` | turma e quatro períodos; calendários das origens/destinos; recomposições do período |
| `students` | turma, número da chamada, pessoa, ingresso/saída/situação; cadeia de matrículas e todos os dependentes |
| `activities` | turma, período, máximo; notas dependentes e identidade histórica |
| `grades` | matrícula, atividade, mesma turma e máximo; zero e null distintos; versões/históricos protegidos |
| `lessons` | turma, data e ordinal único no espaço vivo/histórico; marcações dependentes |
| `marks` | aula e matrícula da mesma turma; P/F/FJ/NR e motivo opcional apenas em FJ |
| `recompositions` | matrícula, turma/bimestre e unicidade ao longo da cadeia de remanejamentos |
| `relocations` | origem/destino/pessoa, datas, calendários e compatibilidade; ciclos, recomposições na linhagem, correções imutáveis, aulas/frequências removidas |
| `lesson_records` | turma/data e conteúdo não vazio; o modelo atual não inventa uma FK obrigatória para chamada |
| `preserved_histories` | projeção de nove tipos virtuais, identidades não duplicadas, origem autocontida, vínculo com matrícula viva e imutabilidade antes/depois |
| `weekly_timetables` | ano, turma quando presente, turno/dia/período; slots sem conflito e identidade estável entre revisões, revisão imutável |
| `class_archives` | turma e estado persistido de arquivamento |
| `export_rows` | turma/matrícula, ID de linha, posição única e sequência contígua por turma; alterações precisam ser atômicas |
| `import_drafts` | turma e conteúdo persistido do rascunho; não supõe matrículas já criadas para as linhas |

`preserved_histories` projeta `classes`, `students`, `terms`, `lessons`, `marks`, `activities`, `grades`, `recompositions` e `relocations`. Uma alteração/exclusão exige considerar referências **de entrada e de saída**, versões anteriores e posteriores e as restrições agregadas. Um índice apenas das FKs vivas omite esse espaço virtual, a conectividade histórica e as guardas de auditoria.

Há ainda obrigações globais por titular: sessão confirmada/ativa, allowlist e escopo, lock da conta, revisão conhecida, identidade imutável da operação, recibos e atomicidade. Nenhuma pode ser substituída pelo fecho do grafo.

## Candidato estudado e oráculo

`scripts/h131/focal-grade-lab.sql` fica no schema privado de laboratório `h131_lab`, instalado e removido somente no banco descartável com nome/host permitidos. O predicado focal admite apenas **mudança do valor de uma nota viva já existente**, mantendo chave e demais campos. Revalida shape, CAS, matrícula, atividade, turma, quatro períodos e máximo. Outros tipos, deleções, novas notas e lotes mistos retornam `FULL_REQUIRED`.

A comparação chama o **writer integral original** `noctua_e_guarded_write` dentro de uma subtransação sempre revertida, incluindo versões e recibos. Hashes dos registros e metadados são conferidos antes/depois; as funções públicas devem permanecer idênticas. `FULL_REQUIRED` significa usar o mesmo caminho integral, não comprovação de um segundo algoritmo para aquele tipo. Cancelamento 57014 é inconclusivo para equivalência, nunca rejeição acadêmica nem aprovação artificial.

A execução usa `statement_timeout=8s`. O tempo do **predicado focal** não é comparável ao tempo de **writer integral + rollback** como se fossem duas implementações completas. O primeiro não grava, audita, confirma recibos nem comprova validade do restante da conta. Não há ganho de latência de produção autorizado por esses números.

### Pré-condição faltante e contraexemplo

A tentativa de cadastrar pela RPC C uma matrícula inativa sem data de saída foi corretamente recusada. Não foi encontrada nem alegada vulnerabilidade de escrita comum por esse caminho.

Em um ensaio separado, **uma falha foi explicitamente injetada pelo administrador da fixture local** em uma matrícula não relacionada à nota: situação Transferido com saída null. O predicado focal aceita a nota; o integral rejeita o estado. Isso demonstra que consultar somente os pais da nota **não é equivalente sem a pré-condição de que o estado anterior inteiro seja válido**. Não demonstra que um usuário comum consegue produzir esse estado no aplicativo ou no Supabase.

Também foi testado um lote que corrige a matrícula e a nota conjuntamente: o integral permite o reparo. Logo, um certificado ausente/inválido deve encaminhar ao integral; rejeitar previamente todo estado inválido bloquearia reparos legítimos.

Para qualquer futura implementação, é necessário demonstrar/certificar o estado-base sob a versão exata das regras e do escopo, incluindo todos os escritores anteriores, restore/reconciliação, tombstones, históricos e mudanças de versão. Esse invariante não está certificado no protocolo atual. Somente passar casos válidos conhecidos não constitui prova universal.

## Estratégia segura proposta, ainda não implantada

1. Certificado de validade por titular/head/versão do validador/época de restore, emitido após validação integral sob o mesmo lock da conta. Ausência ou mudança de qualquer elemento exige fallback integral, preservando reparos válidos.
2. Índice reverso que inclua identidades virtuais e dependências semânticas, mantido na mesma transação da gravação e validado contra a projeção integral. A atualização precisa retirar dependências antigas e incluir as novas.
3. Lista de operações comprovadamente locais. Começar somente pela mudança de valor de nota existente; campos de identidade, movimentações, históricos, calendário e lotes interdependentes continuam integrais até prova específica.
4. Validar o fecho antes/depois sob o lock e CAS atuais. Nunca reutilizar uma aprovação obtida antes de outro dispositivo alterar atividade/máximo. Preservar auditorias e ambos os lados dos conflitos.
5. Testes diferenciais gerativos e adversariais, todos os caminhos de escrita/restore e versões suportadas; modo sombra antes de qualquer ativação. Se houver diferença ou certificado insuficiente, o integral continua soberano.

Essa proposta não adiciona tabela/certificado nem muda o protocolo nesta rodada. Alternativa de menor risco: otimizar materialização/indexação e consultas repetidas do **mesmo validador integral**, com equivalência estrutural verificável como na H13. Não ampliar timeout nem fragmentar uma movimentação para contornar custo.

## Ensaios, limites e reprodução

- `scripts/h131/investigate.py --repetitions 3`: casos nulos/zero/positivos/limites, CAS antigo, fallback dos 15 tipos, auditoria imutável, histórico protegido, identidade da movimentação e mudança concorrente do máximo. Compara bases de 5.171 registros, seis turmas vivas/três históricos/movimentações (5.513 registros) e conteúdo histórico ampliado em 320 registros virtuais.
- Um bootstrap que excede 8s é registrado como capacidade bloqueada, com rollback e partes preservadas. O ensaio posterior pode construir **outra fixture** por transações independentes de matrículas completas, cada uma válida. Isso não autoriza dividir/reinterpretar uma transferência acadêmica original nem apagar o resultado do timeout.
- `EXPLAIN (ANALYZE, BUFFERS)` independente mede execução, buffers e arquivos temporários. `work_mem` é configuração, não pico de memória. Pico de RAM não foi medido; não inferir capacidade de serviço a partir da ausência de spill.
- `scripts/h131/network.py`: RPCs E originais, PostgREST real em loopback, sessões JWT sintéticas, conta B, concorrência na mesma revisão, edições distintas, máximo alterado por outro dispositivo, rollback de histórico/lote, partes pendentes, sessão inválida e helper privado. Resposta descartada no cliente é repetida com a mesma operação/recibo. Arquivo pendente 0600 resiste à conexão recusada e recarga pelo harness.
- A recarga desse harness **não é encerramento de processo Android**. Os testes nativos A–E e as mortes controladas de processo da CI #204 continuam evidências anteriores; uma nova execução deve ser identificada pelo seu próprio SHA/run. Não transformar JWTs locais em prova de Supabase Auth hospedado.
- Workflow `h131-investigation.yml`, sem Environment/Secrets, executa somente infraestrutura descartável e artefatos sanitizados. A regressão usual permanece em `android.yml`, sem remover ou enfraquecer testes anteriores.

Exemplo local (PostgreSQL descartável previamente inicializado):

```sh
export PGHOST=127.0.0.1 PGPORT=5432 PGUSER=postgres
export PGDATABASE=noctua_phase_e_test
mkdir -p build/h131
python3 scripts/h131/investigate.py --repetitions 3 > build/h131/local.json
# Sequencial: o bootstrap HTTP reaplica as fixtures; não executar simultaneamente.
export PGDATABASE=noctua_phase_b_test
python3 scripts/phase-b-http-fixture.py
python3 scripts/h131/network.py > build/h131/network.json
```

Não apontar esses runners a qualquer banco hospedado. Eles exigem nomes de banco/loopback e dependem de fixtures administrativas descartáveis.

## Resultados e continuidade

Código do laboratório: `e18a1dcc4a471aec9615ec2883f6bf790933da97`. Ajuste do runner (criação do banco D descartável): `8293374db38b199fc2be7703c556f2b82aa6d7c9`.

[Laboratório aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43510544704), fonte `8293374`, workflow específico execução #2. [Artefato](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43510544704/artifacts/14078980479), ZIP SHA-256 `aeb2f4c61b55c4faceeb57e89122d23317aff734e30cd3de3a6bd67b7ea58378`. A primeira execução `43510320061` foi cancelada por falta desse pré-requisito no runner, sem ser contada como aprovação. A segunda terminou com:

- JVM: **298 casos, 293 aprovados, cinco condicionais ignorados, zero falhas**.
- PostgreSQL: **65 testes aprovados, zero falhas/ignorados**: D 27, E1 16, decisões 15, H13 sete. São reexecuções de suítes existentes, não 65 testes novos.
- Laboratório diferencial: **40 cenários executados conforme a hipótese**, 39 coincidências/fallbacks e **um contraexemplo sintético esperado**. Desses 40, 19 usaram `FULL_REQUIRED`; não são equivalência de um algoritmo incremental para esses tipos. Zero cancelamentos nesse executor. A CI verde comprova que a investigação reproduziu os resultados esperados; **não aprova a substituição**, pois exige justamente detectar a diferença.
- HTTP: cenário real descartável concluído; 171 registros, head 6, sete recibos, igualdade do replay, concorrência/CAS, null/zero, recusa da conta B, sessão inválida e helper privado, rollback e partes preservadas. Não foi Supabase Auth real nem morte de processo Android.

[Regressão completa #205](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43510320045), fonte `e18a1dc`: **falhou; não declarar CI integralmente verde**. O commit posterior `8293374` altera apenas o runner do laboratório e não o aplicativo/protocolo.

- Compilação de debug/instrumentação e JVM: passaram; novamente 298/293/cinco ignorados/zero falhas.
- PostgreSQL acadêmico A–E: **134 aprovados, zero falhas/ignorados** (38 + 18 + 20 + 27 + 16 + 15). Job PostgreSQL v1 também aprovado.
- Job H13: sete regressões H13, séries SQL de 41/171/671/5.171, delta e lock/replay concluíram. **A série HTTP falhou** em `benchmark-h13.py:163`: `Candidate cannot finish under 8s; capacity blocked`. O arquivo `http.json` ficou vazio; tamanho, repetição e variante dessa falha não foram preservados. Não inferir esses detalhes nem anunciar uma série HTTP completa.
- Job de regressão: falhou em `test-phase-e-timeouts.py:148`, `recover:81`, na retomada em 8s depois do timeout controlado durante escrita de **5.171 registros/41 partes**. O teste conferiu rollback integral, head zero e partes preservadas antes dessa tentativa. A nova tentativa também cancelou; não foi confirmada como sincronizada.
- **A suíte Android, as provas de dois AVDs e o benchmark Room desta rodada não chegaram a executar**, por falha prévia. Sua evidência anterior na #204 é preservada como histórica (416 Android aprovados/17 condicionais ignorados e benchmark dedicado), sem atribuí-la à #205.
- Nenhuma asserção foi removida, timeout ampliado ou nova execução disparada para esconder o bloqueio.

Os dois jobs com falha foram diagnosticados por logs sanitizados e artefatos. Os erros acadêmicos/permissões provocados pelos testes negativos anteriores não são a causa desses dois encerramentos. [Proveniência e evidências preservadas](evidence/H131_20261113/provenance.json) incluem hashes dos ZIPs, fontes, métricas, contagens e resumo das falhas. Os arquivos originais da regressão estão nos artefatos [H13 parcial](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43510320045/artifacts/14077609191) e [regressão](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43510320045/artifacts/14078642077).

### Tempos e orçamento

Milissegundos, três amostras por base no laboratório. PostgreSQL CI 17.12, `shared_buffers=128MB`, `work_mem=4MB`. O writer inclui rollback do oráculo; o predicado focal é uma consulta sem gravação. As colunas **não representam um antes/depois de sincronizadores equivalentes**.

| Base | Linhas físicas / projetadas | Writer integral mediana / máximo | Predicado focal mín.–máx. | Margem nominal 8s − máximo integral |
|---|---:|---:|---:|---:|
| Base original | 5.171 / 5.183 | 2.299 / 2.341 | 5,27–5,42 | 5,659s |
| Seis turmas vivas, três históricos e três movimentações | 5.513 / 5.549 | 2.813 / 2.867 | 5,25–5,49 | 5,133s |
| Histórico com mais 320 registros virtuais | 5.171 / 5.503 | 5.186 / 5.214 | 5,16–5,47 | 2,786s |

A margem é aritmética do corpo medido, sem garantia para transporte, concorrência ou outro hardware. Em amostra EXPLAIN separada, os três perfis usaram 58.377 / 79.578 / 3.949 shared-hit blocks, zero shared reads e zero temp-written blocks. Ausência de I/O/spill não implica baixo custo JSONB/CPU; não houve medição de pico de RAM.

No workspace PostgreSQL **17.11**, separadamente, os mesmos três ensaios produziram mediana/máximo integral **3.555/3.622 ms** e **4.298/4.377 ms** nas duas primeiras bases. O histórico ampliado admitiu bootstrap em **7.135 ms**, mas as três edições pequenas cancelaram em 8s, com hashes/metadados preservados. Elas são **inconclusivas para equivalência**, não aprovações. Um ensaio exploratório anterior também cancelou o bootstrap; não apagar essa observação porque uma repetição posterior passou. Não misturar essas séries com a CI 17.12 para inferir ganho.

A série pareada H13 da #205, ainda usando validação integral em ambas as variantes, repetiu cinco amostras: bootstrap SQL 5.171 mediana **5.158 → 4.083 ms** (helper original → cache H13); nota única **3.860 → 3.313 ms**. Isso reafirma o efeito **já existente** da H13, não um ganho H13.1. Replay SQL grande candidato mediana 31 ms. O teste de lock pequeno comprovou cancelamento, partes intactas, outra conta progredindo, retomada da mesma operação em 35 ms e replay em 3,5 ms.

O perfil de uma nota na variante H13 atual ainda tem **5.187 validações**. Uma amostra registra grafo D 2.253 ms inclusivos, `noctua_d_get` 19.640 chamadas/1.259 ms próprios, guarda histórica 889 ms inclusivos, três projeções 206 ms inclusivos e escrita E 19 ms inclusivos. Tempos inclusivos são sobrepostos: não somá-los. A guarda histórica ainda consulta arrays antes/depois; reutilizar índice de identidade nesse caminho é uma alternativa a investigar sem eliminar predicados. **Não foi implantada aqui.**

### Bloqueadores e próxima ação

1. Equivalência do predicado focal sem pré-condição de estado válido: refutada pela injeção administrativa; equivalência restrita a estados válidos continua apenas hipótese. Recibos anteriores não identificam por si só a versão exata das regras nem certificam todos os caminhos legados/administrativos. Não foi demonstrado um bypass comum de usuário.
2. Admissão sob 8s não é robusta: timeout no workspace com histórico extenso e dois bloqueios na #205. Não há autorização de capacidade ampla/institucional.
3. Próxima rodada recomendada: instrumentação persistente de contexto sanitizado antes de um benchmark falhar; depois perfil/otimização estrutural das buscas da guarda histórica integral, mantendo os mesmos predicados. Somente após prova do invariante/certificação e fecho completo retomar o atalho incremental.
4. Nova regressão Android/dois AVDs e provas de tempo precisam ser concluídas numa futura alteração segura; não há regressão acadêmica demonstrada nesses encerramentos, mas a execução completa atual **não foi aprovada**.

**Antes/depois em produção: inalterado.** O predicado experimental não foi promovido. H13.1 não demonstra capacidade institucional ampla; históricos, contenção, cache e CPU compartilhada podem exceder o orçamento mesmo com poucos registros físicos.

Supabase hospedado, produção, contas reais, dados/aplicações do proprietário, assinatura e `main` permanecem intocados. Não há APK novo ou release desta investigação.
