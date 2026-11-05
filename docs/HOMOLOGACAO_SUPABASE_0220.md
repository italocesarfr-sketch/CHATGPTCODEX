# 0.22.0 — Pré-homologação do Supabase hospedado

Data da revisão: **05/11/2026, America/Sao_Paulo**. Branch
`sync/academic-complete-0220`; HEAD inicial `e76fa41e350e658f76ac33f7ef0c52a05a9bd5cb`.
Código Android/SQL A–E validado: `713001a569c5fde39f187db1feb915abdee5392e`.

**Estado: roteiro preparado; execução hospedada não autorizada nesta etapa.**
Não houve chamada ao projeto hospedado nesta retomada, migração, mudança de conta,
configuração remota, instalação de APK, release ou alteração de `main`.
As consultas à documentação oficial não são consultas ao projeto do proprietário.

## 1. Evidências e grau de comprovação

| Item | Estado e alcance |
|---|---|
| Código/repositório A–E | Ancestralidade A `851ac8b`, B `78aff91`, C `c2e92f5`, D `9717146` e E `713001a` conferida. Depois de E, apenas documentação até o HEAD inicial. |
| CI E | [41951300992](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41951300992) reconferida: dois jobs aprovados; candidato permanente ignorado pela regra da branch. |
| Ambiente local já comprovado | 275 JVM aprovados/1 condicional; 412 Android aprovados/11 condicionais; 164 PostgreSQL; 70 fases HTTP em dois AVDs e duas mortes controladas. Contagens/evidências em [FASE_E_0220.md](FASE_E_0220.md); não reexecutadas integralmente aqui. |
| Verificações novas locais | Três cenários focados de timeout; pacote A–E aplicado em PostgreSQL 17 descartável sobre v1+guard; reaplicação recusada; inventário somente leitura validado antes/depois e em base sem objetos NOcTuA. |
| Supabase v1/guard | Aplicação e testes físicos relatados pelo proprietário nas etapas anteriores. Evidência histórica de 12 recusas sem sessão/JWT válido registrada na Fase A. Não comprova o catálogo nem o RLS atuais. |
| Supabase A–E | Aplicação não comprovada. Pelo histórico, estes sete scripts ainda não foram aplicados. Confirmar por inventário, não por suposição. |
| Acesso hospedado atual | Nenhuma identidade, segredo ou conexão administrativa de leitura disponível no ambiente. Catálogo, plano, consumo, timeout efetivo, allowlist e políticas hospedadas permanecem sem verificação. |
| Auth/SMTP/retorno | Login físico 0.20 e configuração do retorno relatados anteriormente. SMTP personalizado não operacional segundo o proprietário; configuração atual não reinspecionada. |
| Assinatura/atualização | Original c40 e certificado público identificados. Atualização original c40→E com certificado permanente e teste físico continuam pendentes. |

A documentação histórica [A](SINCRONIZACAO_ACADEMICA_0220.md),
[B](FASE_B_0220.md), [C](FASE_C_0220.md), [D](FASE_D_0220.md) e
[E](FASE_E_0220.md) foi consultada. Limitações antigas descrevem a fase respectiva;
a evidência final E não deve ser confundida com homologação hospedada.

## 2. Inventário somente leitura e bloqueios de entrada

O administrador deve conferir no painel o projeto **noctua-auth**, referência
`mpsgdchpgyxoqowfjoqm`. O aplicativo usa
`https://mpsgdchpgyxoqowfjoqm.supabase.co`, chave publishable pública e bearer da
sessão Auth do workspace; não recebe `owner_id` arbitrário como identidade.

Executar futuramente, no SQL Editor do projeto correto, somente
[`supabase/checks/prehomo_0220_readonly.sql`](../supabase/checks/prehomo_0220_readonly.sql).
É transação READ ONLY; não invoca RPCs nem lê conteúdos acadêmicos/Auth.
Funciona com objetos acadêmicos ausentes. Produz:

- versão PostgreSQL, tamanho do banco e parâmetros não secretos;
- timeout da sessão administrativa, configurações específicas de papel/banco;
- presença/colunas necessárias de `auth.users` e `auth.sessions`;
- presença das 16 tabelas esperadas ao final, RLS, permissões e tamanho com índices;
- políticas, assinaturas/ACLs/owners/search_path e MD5 das definições das funções;
- restrição de escopo, existência do histórico de migrações e capacidade do owner
  da função de consultar `auth.sessions`.

`SHOW statement_timeout` no SQL Editor **não** informa o orçamento efetivo de uma
requisição PostgREST autenticada. Configurações de função, papel, banco, sessão e
API precisam ser consideradas em conjunto. O inventário não certifica a semântica
de uma função só por encontrar trechos de texto ou MD5.

Critérios prévios:

1. Conferir o corpo atual de `noctua_sync_principal()` e os privilégios de seu owner:
   UUID Auth, papel authenticated, expiração, `session_id`, sessão ativa, e-mail
   confirmado e usuário não anônimo. Não exportar linhas de Auth, tokens ou senhas.
2. Confirmar RLS e ausência de política permissiva/anon/DML direto; revisar funções
   SECURITY DEFINER com `search_path=pg_catalog` e referências qualificadas.
3. Distinguir scripts aplicados pelo SQL Editor do histórico CLI. Ausência em
   `schema_migrations` não significa ausência de objetos. Não fazer `db push`,
   `migration repair` ou reaplicação automática para “acertar” o histórico.
4. Se houver qualquer objeto A–E prévio, política desconhecida, schema Auth
   incompatível ou função diferente do baseline revisado: **interromper** e
   preparar plano específico. O pacote abaixo não serve para migração parcial.
5. Confirmar plano/uso em Billing/Usage, estado ativo/pausado, Data API/public,
   versão e limites do gateway; não habilitar novos recursos pagos.

Após aprovação de implantação, repetir o catálogo. Esperado: 16 tabelas NOcTuA com
RLS (4 v1 + 7 A + 2 B + 3 E), SELECT próprio para authenticated, sem DML direto
ou acesso anon. C/D usam os registros tipados existentes; não criam quinze tabelas
acadêmicas remotas independentes. Helpers/unfenced não devem ter EXECUTE de clientes.
Catálogo executado como administrador **não** é prova de isolamento entre usuários.

## 3. Ordem exata e compatibilidade das migrações

| Ordem | Arquivo em `supabase/migrations/` | Dependência/efeito |
|---:|---|---|
| 0 | `202610300001_class_sync.sql` | Baseline v1: quatro tabelas, validação de turma/bimestres, RPCs. Histórico indica instalado; não reaplicar sobre versões posteriores. |
| 1 | `202610310001_sync_session_guard.sql` | Exige v1 e schema Auth compatível; sessão ativa/confirmada. Também redefine RPCs v1. Não reaplicar depois de B. |
| 2 | `202611010001_academic_protocol.sql` | A exige v1+guard. Sete tabelas, contrato tipado, versões, recibos, tombstones e grafo. |
| 3 | `202611020001_phase_b_fence.sql` | B exige A; duas tabelas (allowlist e protocolo), wrappers v1/v2 e helpers privados. Não autoriza contas automaticamente. |
| 4 | `202611020002_phase_c_scope.sql` | C exige B; escopo 3, avaliações/frequência, RPCs e dependências. |
| 5 | `202611020003_phase_d_scope.sql` | D exige C; escopo 4, quinze tipos, cadeias/históricos/auditorias e proteções de revisão. |
| 6 | `202611030001_phase_e_paging.sql` | E1 exige D; escopo 5, manifesto/partes, confirmação atômica e recebimento paginado. |
| 7 | `202611030002_phase_e_decisions.sql` | E2/E3 exige E1; decisões imutáveis, CAS, evidências, reconciliação e quota de estágio. |
| 8 | `202611030003_phase_e_indexed_graph.sql` | E4 exige os anteriores; indexa os mesmos predicados D e reaproveita recibos. Não reduz validadores. |

Não executar scripts de testes/fixtures contra o hospedado: criam Auth fictício,
reaplicam definições históricas e truncam **exclusivamente** bases descartáveis.
Nunca remover guardas, CHECKs, auditorias ou barreiras para corrigir uma ordem errada.

### Pacote transacional para o baseline v1 + guard

Aplicar A e só depois B em transações separadas abre uma janela em que as RPCs da
Fase A ainda não possuem a allowlist/barreira B. Foi preparado um gerador local:

```bash
python3 scripts/prepare-phase-e-deployment.py --output build/homologacao-0220.sql
```

Esse comando **somente grava um arquivo local**, sem conectar a banco/rede.
Mantém a ordem e o conteúdo dos sete scripts, remove apenas seus envelopes
standalone BEGIN/COMMIT e envolve tudo em uma única transação. Inclui hashes dos
fontes, recusa sobrescrever arquivo existente e aborta se o formato dos envelopes
mudar. Não concatenar os arquivos com BEGIN/COMMIT intermediários.

O pacote exige baseline v1+guard e ausência de objetos v2. Usa `lock_timeout=5s`
somente na sessão de implantação, sem aumentar statement_timeout nem mudar papéis.
Qualquer erro desfaz a transação inteira. A notificação de recarga do schema
PostgREST só é entregue no commit. Nenhuma conta é habilitada por esse pacote.

**Gerar/revisar não autoriza executar.** Depois de inventário, backup e aprovação
administrativa específica, o administrador poderá executar o pacote conferido em
uma única sessão. SQL Editor deve interromper em erro; conexão CLI deve usar
`ON_ERROR_STOP=1`. Não enviar partes selecionadas do pacote separadamente.
Se houver timeout/erro ou resultado de commit incerto, conferir o catálogo antes
de qualquer nova tentativa; não fazer retry cego.

Validação local do pacote: v1+guard→A–E em banco novo descartável, 16 tabelas RLS;
segunda aplicação recusada pelo preflight, sem mudança; catálogo aprovado antes e
depois. Falha injetada ao final, antes do commit, desfez todo A–E: quatro tabelas
v1 e definições do principal/RPC v1 preservadas. Evidência:
[PREHOMOLOGACAO_DEPLOY_0220.json](evidence/PREHOMOLOGACAO_DEPLOY_0220.json).
Não houve simulação de autorização hospedada. Manifesto de hashes ao final.

## 4. Timeout: camadas diferentes

| Camada | O que controla | Evidência e pendência |
|---|---|---|
| PostgreSQL `statement_timeout` | Cancela uma instrução no servidor, incluindo espera por locks; pode desfazer o trabalho dessa instrução/transação. | Documentação Supabase informa padrão authenticated 8 s e anon 3 s; efetivo do projeto não consultado. SECURITY DEFINER não concede orçamento administrativo automaticamente. |
| Função RPC sob a sessão | `noctua_commit_academic_e` monta o conjunto, valida CAS/grafo/históricos e grava atomicamente; `resolve` pode chamar o commit. | Inventariar `proconfig` e orçamentos efetivos. Em sucesso local, EXPLAIN ANALYZE mediu execução do servidor separadamente. |
| PostgREST/API/gateway | Encaminhamento, pool, limites/cancelamentos HTTP e da plataforma. | Configuração hospedada não verificada. A referência Supabase descreve limite configurável de 60 s para consultas Dashboard/Client; isso não substitui o orçamento de papel de 8 s. |
| HTTP Android | Cliente atual: conexão 15 s, espera por leitura 20 s; redirects desativados. Não há prazo global de chamada nem write-timeout explícito neste transporte. | Valores conferidos em `AcademicTransport.kt`; não são timeout global da sincronização nem garantia de 20 s no servidor. |
| Transferência completa | Manifesto, 41 chamadas de estágio, commit, recebimento de páginas, gravação Room e UI. | Pode ultrapassar 8/20 s sem exceder o orçamento de uma instrução ou uma espera por leitura. Não comparar duração total do cenário diretamente ao timeout de uma RPC. |

Fontes: [Timeouts Supabase](https://supabase.com/docs/guides/database/postgres/timeouts)
e [código do transporte](../app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicTransport.kt).
Consulta documental em 05/11/2026; configurações reais prevalecem sobre padrões.
O readTimeout é espera de leitura do socket, não cronômetro total de toda a
requisição. Upload lento/bloqueado e resposta com progresso precisam ser medidos
separadamente; não há prova de deadline total Android hospedado nesta etapa.

### Risco do lote 5.171 / 41 partes

A CI E registrou 8,647 s no wrapper de confirmação; o benchmark mede parede do
processo `psql`, incluindo conexão e chamada, **não** somente tempo interno da
função. É sinal de margem insuficiente, não prova de timeout hospedado.
O Postgres/CPU/RAM da CI e deste executor não representam o Free hospedado.

O commit do lote é uma instrução; paginar o envio não divide sua validação/gravação
atômica. O grafo D também revisa relações do acervo: reduzir linhas enviadas não
elimina necessariamente o custo do grafo. Não aumentar timeouts globais, reduzir
validadores, quebrar remanejamentos/históricos ou mudar o conteúdo sob o mesmo UUID.

### Prova focada nova, somente local

Script: [`scripts/test-phase-e-timeouts.py`](../scripts/test-phase-e-timeouts.py).
Evidência: [`PREHOMOLOGACAO_TIMEOUT_0220.json`](evidence/PREHOMOLOGACAO_TIMEOUT_0220.json).

```bash
PGHOST=/workspace/pg-local PGPORT=55432 PGUSER=agent \
PGDATABASE=noctua_phase_e_test \
PSQL_BIN=/workspace/pg-local/root/usr/lib/postgresql/17/bin/psql \
python3 scripts/test-phase-e-timeouts.py
```

Usa fixtures existentes, papéis/claims fictícios e SET explícito de 8 s na mesma
sessão de cada chamada. SET ROLE isoladamente não carrega defaults de login como
o PostgREST pode configurar por requisição. Não altera defaults globais/de papel.
EXPLAIN ANALYZE **executa** a RPC e foi usado apenas na base descartável; não rodar
esse ensaio no hospedado nem como suposta consulta de leitura.

| Caso aprovado | Parede `psql` | Execução reportada pelo PostgreSQL |
|---|---:|---:|
| 171 registros / 2 partes, orçamento 8 s | 0,246 s | 234,278 ms |
| 5.171 registros / 41 partes, orçamento 8 s | 6,103 s | 6.094,698 ms |
| Replay do lote grande confirmado | 0,041 s | 33,459 ms |
| Cancelamento no meio da gravação, orçamento 8 s | 8,007 s | Sem EXPLAIN final, SQLSTATE 57014 |
| Retry do mesmo lote após cancelamento, orçamento 8 s | 6,750 s | 6.742,744 ms |

Cancelamento determinístico por lock controlado em trigger **exclusivo da fixture**,
após início das escritas; sem sleeps nem substituição dos validadores. Sequência de
teste comprovou atingir esse ponto. Resultado: zero linhas/versões/recibos/lotes e
sequência acadêmica zero; 41 partes preservadas. O mesmo UUID concluiu e o replay
manteve recibo/versões únicos. Hook/lock removidos e dados acadêmicos de ensaio reinicializados no banco
descartável ao final. Três cenários
aprovados; não são testes PostgREST, Android nem Auth hospedado.

O script prevê diagnóstico local de 20 s se o lote natural não couber em 8 s,
registrando isso como insuficiência, sem mudar configuração permanente. Esse caminho
não foi necessário nesta rodada e não comprova capacidade Free.

### Critério de desempenho hospedado e proposta se falhar

Após autorização, começar pequeno (turma/quatro bimestres/36 matrículas), depois
171 registros com os quinze tipos, e só então 671 e 5.171/41 partes. Conferir IDs,
revisões, cursores e consistência em cada patamar; repetir sob carga controlada
com uma conta por vez e depois duas contas/instalações. Medir latência de cada RPC,
tempo total, timeouts, memória/CPU, espera por locks, tamanho e consumo de saída.

- Identificar erro: 57014 é cancelamento SQL; 504/queda HTTP sozinhos não provam
  rollback. Se a resposta se perder, o servidor pode ter confirmado: consultar o
  recibo/repetir o **mesmo** UUID/corpo e validar a revisão antes de reconhecer envio.
- Simular timeout de banco em laboratório separado; no hospedado observar os
  limites reais, sem instalar triggers de falha nem provocar locks em dados reais.
- Exigir conclusão repetível com folga mensurável no orçamento efetivo, sem falhas
  ou repetições permanentes. Como critério de ensaio, buscar commit p95 abaixo de
  75% do orçamento em ao menos dez execuções controladas; é meta do NOcTuA, não SLA
  Supabase. Se não cumprir, bloquear o patamar e preservar as partes/pendências.
- Se 8 s for insuficiente, priorizar profiling do grafo/serializações e índices
  equivalentes, preservando todos os predicados. Agrupar deltas independentes em
  transações menores só após prova de integridade e CAS; nunca cortar movimentação
  ou histórico indivisível. Não há repartição automática do bootstrap no limite.
- Mudança de protocolo exigirá proposta versionada, testes e revisão antes de
  implementação. Ajuste estritamente limitado de orçamento de RPC/papel, se ainda
  necessário, depende de diagnóstico e autorização específica; não é adotado aqui.

## 5. Backup administrativo e ambiente de ensaio

Antes de qualquer DDL hospedado, o administrador deve produzir backup independente
em máquina própria/controlada: estrutura, dados necessários à recuperação,
funções/ACLs/roles pertinentes, histórico de migrações e registro das configurações
Auth/API. Registrar versão do cliente PostgreSQL compatível, data, escopo e hashes.

- Usar conexão TLS obtida em **Connect**, com credenciais mantidas localmente pelo
  administrador (prompt/passfile/service de permissões restritas). Nunca colar URL
  com senha, chaves, tokens ou dump no chat, repositório ou artefatos públicos.
- Exemplo local do administrador, com serviço libpq já configurado com segurança:
  `pg_dump --dbname='service=noctua_backup' --format=custom --file=noctua-pre-0220.dump`.
  Não é comando a executar neste ambiente nem prova de que a permissão de dump
  completo esteja disponível. Conferir erros e manifesto com `pg_restore --list`.
- Um dump só de `public` não cobre Auth/configuração. Ferramentas gerenciadas podem
  excluir schemas gerenciados; revisar cobertura e limitações antes de afirmar
  recuperabilidade completa. Storage binário não está no dump do banco; nenhum
  recurso Storage novo é necessário para esta sincronização.
- Guardar cópia cifrada fora do projeto, com acesso restrito; verificar checksum e
  inventário. Ensaiar procedimento com estrutura e dados fictícios em banco
  descartável. Teste de restauração de um backup com dados reais exige autorização
  e ambiente privado específicos; não será usado nos experimentos Codex.
- Backup lógico do aplicativo continua formato 12/esquema lógico 10, cifrado,
  protegido por titularidade e senha. Não substitui backup administrativo; metadados
  de fila não são uma recuperação automática de histórico remoto pelo backup.

[Backups Supabase](https://supabase.com/docs/guides/platform/backups) recomenda
exportação independente para Free; backup diário/PITR não deve ser presumido.
Procedimento oficial: [backup/restore CLI](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore).
Não redefinir senhas existentes apenas para seguir um exemplo da documentação.

Opções de isolamento, a escolher antes da autorização:

1. Mesmo projeto, **duas contas Auth exclusivamente fictícias**, allowlist por UUID,
   dois aparelhos de teste/AVDs e nenhum workspace legado. DDL afeta funções globais:
   precisa de janela administrativa aprovada e proteção dos usuários v1 existentes.
2. Projeto de ensaio separado, se houver vaga gratuita. Não clonar dados reais.
   Exige configuração Auth/SQL própria e futuro build de teste com endpoint/chave
   pública daquele projeto: o APK validado fixa a URL atual e não troca de projeto
   por menu. Não criar projeto/build adicional por rotina ou com custo implícito.

Nenhum isolamento dispensa RLS. O conflito legado do proprietário permanece intacto.

## 6. Contas, ativação e configurações pendentes

O administrador deve verificar, sem alterar nesta etapa:

- Email provider, confirmação obrigatória, anônimos desabilitados, cadastro e senha
  mínima de oito caracteres, conforme configuração relatada.
- Retorno Android `br.com.diariodeclasse://auth/callback` e estado PKCE produzido
  pelo aplicativo. Preservar checagem de scheme/host/path/state/code, janela de
  validade e verifier. Não usar localhost como retorno Android nem liberar URLs
  genéricas para fazer testes passarem. Conferir fluxo real no aparelho de ensaio.
- Duas identidades Auth comuns, confirmadas, com UUIDs diferentes e endereços de
  teste controlados/autorizados. Não reutilizar a conta/UUID do proprietário com
  acervo real nem criar as contas dos cinco professores-piloto nesta preparação.
- Conta Auth comum não é membro/admin da organização Supabase. Não conceder acesso
  ao painel só para contornar entrega de e-mail.

Sem SMTP próprio, o serviço padrão documenta destinatários pré-autorizados e limite
atual de dois e-mails/hora, sujeito a mudança. Duas caixas quaisquer não estão
automaticamente habilitadas. Após autorização, selecionar contas de teste já
confirmadas ou criação administrativa explícita de contas comuns de ensaio; isso
valida sessões/RLS, **não** entrega/confirmação de e-mail. Cadastro por e-mail e
recuperação completos permanecem pendentes se não houver entrega autorizada.
Não desabilitar confirmação global. [SMTP oficial](https://supabase.com/docs/guides/auth/auth-smtp).

Depois de implantação aprovada, autorizar somente os dois UUIDs fictícios em
`noctua_academic_test_accounts` com `allowed=true`, em transação revisada e com
conferência de duas linhas afetadas. Não usar autorizações por domínio, nome,
owner fornecido pelo cliente ou todos os registros; excluir o UUID configurado em
`AuthConfig.LEGACY_OWNER_ID`. Não há allowlist automática nas migrações.

No Android, `academicTestEligible` exige DEBUG, workspace não legado e UUID distinto
do proprietário. Cada instalação faz a transição explícita B→C→D→E pela interface,
com estados/filas consistentes e consentimento de dados fictícios. Para E, ação
**Testar recuperação de acervos maiores**; confirmar a ampliação. Não manipular
Room/cursor/protocolo por SQL para saltar etapas. Usar contas vazias até chegar ao
escopo E antes de criar o acervo grande; clientes antigos ficam bloqueados somente
nas contas que efetivamente transitaram. Não rebaixar escopo depois.

## 7. Matriz de homologação hospedada — ainda não executada

Usar duas instalações independentes A1/A2 da mesma conta fictícia A e sessão comum
B separada. Senhas/tokens/links ficam somente nos aparelhos/cofre autorizado;
evidências públicas contêm identificadores de caso, contagens, hashes e estados
redigidos, nunca nomes reais ou tokens. API deve usar chave pública + JWT comum,
sem service_role ou privilégios administrativos.

| ID | Ensaio | Aprovação/evidência obrigatória |
|---|---|---|
| H01 | Auth real A/B | Login confirmado, UUID esperado, refresh, senha incorreta recusada; troca de conta sem conteúdo cruzado. E-mail/recuperação registrados separadamente segundo SMTP. |
| H02 | RLS direto e RPC | A cria fixture; B conhece os IDs e não lê/altera/exclui dados A por API ou RPC. B acessa seus próprios dados para provar validade da sessão; RLS pode responder lista vazia, não só erro. |
| H03 | ACL/auxiliares | DML direto negado; helpers, unfenced e anon indisponíveis. SELECT próprio autorizado; nenhuma política pública permissiva. Catálogo e chamadas reais comuns devem concordar. |
| H04 | Sessões inválidas | Sem JWT, expirado, usuário não confirmado e sessão revogada negados; JWT ainda assinado mas sessão removida não autoriza RPC. Não revogar sessões do proprietário. |
| H05 | Bootstrap crescente | 36 matrículas e depois 171/671/5.171 registros, 41 partes no maior; todos os quinze tipos, IDs, zeros/null, P/F/FJ/NR/motivos, vínculos, auditorias e quatro bimestres preservados em A2. |
| H06 | Paginação/processo | Interromper após parte durável e antes da aplicação integral: nenhum cursor/registro parcial. Reiniciar, recuperar com IDs iguais, sem duplicação. |
| H07 | Offline/reconexão | Editar em A1 sem rede; fila persiste; reconectar sem reiniciar; A2 recebe. Repetir inverso A2→A1, foreground e reinício. |
| H08 | Resposta perdida/timeout | Servidor confirma, cliente perde resposta; mesmo UUID/corpo retorna recibo, sem dupla revisão. Timeout confirmado de banco conserva atomicidade; queda HTTP tem resultado incerto até conferir recibo. Medir cada camada. |
| H09 | Concorrência/decisão | Alterações independentes convergem; mesma nota gera conflito. Mostrar local/remoto; escolher, auditar e revalidar. Nova edição durante prévia mantém bloqueio e variantes. Repetir escolha local e remota. |
| H10 | Grafo indivisível | Remanejamento origem/destino, pessoa, datas e auditorias recebidos juntos. Incompatibilidade retém inbox e cursor; ciclos/identidades cruzadas/exclusões inválidas negados. |
| H11 | Exportação/histórico | Trocar posições sem violar unicidade; preservar ocultações, notas, recomposição, conteúdo do diário, horários e arquivamentos. Conferir exportações por leitor independente. |
| H12 | Restauração | Backup cifrado fictício; mudar remoto e acrescentar matrícula; restaurar backup antigo em A2. Deve pausar; reconciliação explícita preserva novidade remota, fila/versões e titularidade. Backup de B negado em A. |
| H13 | Capacidade/continuidade | Repetir cargas e dois dispositivos; observar quotas, memória/CPU/locks, estado verdadeiro e backoff. Não mostrar sincronizado com fila/inbox/conflito/decisão pendentes. |

Por fim, comparar snapshots canônicos e hashes entre A1/A2; conferir contagens,
revisões, recibos e cursores após replay, além da igualdade visual. Registrar SHA
do APK, fonte SQL, plano/recursos, timeout efetivo, datas e resultados de cada caso.

Scripts `test-*-postgres.py`, `phase-b-http-fixture.py` e rede do CI continuam
**locais**; não alterar PGHOST/URL para apontá-los ao projeto hospedado. A automação
hospedada precisa de adaptador específico: alvo aprovado, sessões comuns obtidas
no ambiente seguro, allowlist exata A/B, dados fictícios e proteção contra executar
reset/fixtures administrativas. Esse adaptador não está implementado nesta rodada;
a matriz é executável inicialmente pelo app nos aparelhos de ensaio.

## 8. Infraestrutura, limites e custos

Plano contratado/consumo atuais **não consultados**. Referência pública Free em
05/11/2026: 500 MB de banco, CPU compartilhada/500 MB RAM, 5 GB de egress, 1 GB
Storage, 50 mil MAU; até dois projetos ativos, pausa após uma semana inativo.
Sem backup automático/PITR incluído. Confirmar no painel antes de planejar ensaio.
Fonte: [Supabase Pricing](https://supabase.com/pricing).

O NOcTuA usa Auth + PostgreSQL/PostgREST por HTTPS. Não exige domínio, SMTP próprio,
Realtime, Edge Functions, bucket Storage, servidor ou add-on pago para provar a
sincronização. SMTP afeta os fluxos de e-mail, não concede nem substitui sessão.
Se limites gratuitos impedirem a prova, pausar e apresentar alternativas; não
contratar, ampliar compute ou remover proteções automaticamente.

Limites atuais do protocolo: 16.384 registros/16 MiB por transação, partes de
128 registros/768 KiB; uma linha/histórico não é fragmentada. A conferência limita
256 transações/64 MiB, pedido de decisão 768 KiB e evidência 16 MiB. Quatro estágios
inconclusos por titular. Excedentes bloqueiam/preservam dados; não são capacidade
ilimitada. Um bootstrap maior que o limite atômico requer proposta adicional.

O armazenamento contém estado atual, versões, recibos/pedidos, partes e evidências
com possíveis cópias de payload/índices; o JSON de uma fixture não mede tamanho do
banco. Recebimento lê histórico por sequência, consumindo egress novamente em
instalação limpa/restauração. Inventariar `pg_total_relation_size`, consumo no painel
e crescimento após ciclos de edição/replay. Não inferir quantos professores cabem
em 500 MB a partir de 5.171 registros. Retenção/compactação de auditorias e estágios
confirmados ainda exige política; não apagar versões para caber no Free.

Pico de memória do backend e aparelhos físicos não foi medido. Indexação JSONB
reduz buscas repetidas, mas preserva custo de montar/validar o grafo. CPU compartilhada,
cache, locks e rede podem ampliar os tempos; execução local não é SLA Free. Não
assumir limites de conexões/requests, gateway ou SMTP com base só na quota de RAM.

## 9. Matriz de riscos e critérios de interrupção

| Risco | Grau | Controle e condição de parada |
|---|---|---|
| Estado remoto desconhecido/reaplicação | Crítico | Inventário primeiro; qualquer objeto parcial/divergência bloqueia o pacote padrão. |
| Janela A sem barreira B | Alto | Pacote transacional único; não executar arquivos isolados em sequência com commits intermediários. |
| Timeout do commit/locks | Alto | Medir sessão/API/RPC; orçamento local 8 s; preservar partes/UUID. Timeouts repetidos ou sem folga bloqueiam carga. |
| Conta/owner errado ou privilégio excessivo | Crítico | Duas contas comuns exatas, RLS/API reais; qualquer acesso cruzado interrompe homologação. |
| Backup insuficiente | Crítico | Dump/manifesto/checksum independente e escopo conferido; sem isso não autorizar DDL. |
| Crescimento/memória/egress Free | Alto | Medir uso e retenção; recursos indisponíveis/estouro de quota interrompem progressão, sem truncar históricos. |
| SMTP/destinatários sem autorização | Alto | Separar teste de sessão/RLS de entrega; não abrir confirmação/organização para contornar. |
| Atualização assinada/legado | Crítico | Certificado original, backup e ensaio isolado; debug jamais substitui instalação principal. |
| Confiança em simulação/condicionais | Alto | Evidência hospedada por caso e conta comum; não transportar aprovação local automaticamente. |

Aprovar somente o patamar realmente executado, com zero corrupção/acesso cruzado,
IDs/grafo/auditorias preservados, reenvios sem duplicação e retomada demonstrada.
Falha de segurança/integridade interrompe todos os ensaios; falha de capacidade
bloqueia o patamar, mantém os dados e exige diagnóstico. Não há promoção automática.

## 10. Contingência

1. Antes do commit do pacote, qualquer erro gera rollback; conferir conexão,
   resultado e catálogo para distinguir rollback de resposta perdida.
2. Após commit, se smoke/API/RLS falhar, pausar somente os workspaces fictícios,
   preservar filas, partes, recibos, inbox e decisões, e coletar metadados redigidos.
3. Sob autorização administrativa específica, retirar `allowed` somente dos UUIDs
   de teste. Não revogar globalmente sessões/RPCs, não mexer no usuário real, não
   rebaixar protocolo e não reinstalar SQL v1/guard/A sobre escopo E.
4. Manter objetos/dados para investigação e corrigir aditivamente. Restore remoto
   de backup é operação sensível separada, não reação automática: precisa plano de
   janela, reconciliação de novos commits e autorização; não restaurar no projeto
   real para experimentar nem substituir backups do aplicativo.
5. Manter aplicativo do proprietário intacto e uso offline; não resolver o conflito
   legado, apagar Room, remover criptografia ou forçar cadastro em sincronização.

## 11. Requisitos da futura atualização 0.21.0/c40

Original identificado na [execução 41228898103](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41228898103),
artefato `13053517551`:

- package `br.com.diariodeclasse`, versão 0.21.0/c40;
- APK SHA-256 `18335b1e7a640d733e9975d88d3f51a8cea4f73bd9e3bf536f32e90a40495a73`;
- certificado permanente SHA-256 `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.

E validada: 0.22.0-dev-e2/c45, Room 14; cadeia incremental 11→12→13→14 mantém
as entidades anteriores. Backup lógico formato 12/esquema 10 e criptografia
preservados. CI demonstrou atualização de referência v1 com chave descartável e
migrações congeladas; não é prova de atualização original c40 com chave permanente.

O APK E atual é Android Debug, certificado diferente. O escopo E é DEBUG-only;
um futuro release precisa de decisão explícita de política de acesso/homologação,
sem abrir dados reais para habilitar o teste. Não assinar/recompilar por rotina.

Requisitos futuros: confirmar no ambiente protegido GitHub `release` a disponibilidade
legítima da chave já existente e variáveis de assinatura da workflow, sem revelar
valores; conferir certificado com apksigner, package e código maior que 40; testar
atualização `-r` sem desinstalação em aparelho isolado com dados fictícios, snapshot,
backup, isolamento de conta e conflito legado preservados. Não usar downgrade,
limpeza, chave nova ou reinstalação para obter instalação. A chave privada não está
neste executor; seu armazenamento/disponibilidade atuais não foram verificados.

## 12. Responsabilidades e autorizações

**Proprietário/administrador no Supabase, em ordem:**

- [ ] Confirmar projeto, plano/Usage/estado ativo e reunir inventário somente leitura;
      não aplicar migrações ainda.
- [ ] Conferir Auth/retorno/SMTP e escolher duas contas comuns exclusivamente de
      teste; criar/confirmar somente após autorização específica, sem conta real.
- [ ] Preparar backup administrativo privado, cobertura, hashes e contingência;
      escolher isolamento e janela de implantação.
- [ ] Revisar hashes/pacote A–E e autorizar explicitamente sua execução, orçamento
      e escopo. Aplicar apenas depois dessas condições; não reaplicar baseline.
- [ ] Depois da implantação aprovada, conferir catálogo, permitir só os dois UUIDs
      fictícios e executar H01–H13 progressivamente em instalações separadas.

**Codex pode continuar automaticamente, sem mudar hospedado:** conferir commits,
gerar/validar o pacote local, analisar inventário redigido sem dados pessoais,
reproduzir diagnóstico de desempenho em bases descartáveis, preparar adaptador
seguro e evidências, revisar documentação e compatibilidade pública do APK.

**Exigem autorização e acesso seguros adicionais:** qualquer DDL/configuração/ACL
hospedada, criação/confirmação/revogação de conta, allowlist, teste RPC que grave
fixtures, novas sessões para integração, alteração de timeout, backup/restauração
administrativo, build com novo endpoint e testes assinados/instalação. Não pedir
senhas/tokens/chaves pelo chat; acesso deve existir em canal/ambiente protegido.
Homologação institucional, liberação estável e `main` continuam fora desta etapa.

## 13. Manifesto dos SQLs auditados

Os hashes abaixo fixam os arquivos revisados; gerar pacote com esses conteúdos.
Mudanças posteriores exigem nova revisão, sem inferir validação do código 713001a.

```text
fec4f2382d7d4cf589d84df57e407c86b8977422786d077b28b9c60ad7076ff7  supabase/migrations/202610300001_class_sync.sql
b79d74a844853d00ba91e256ebc1c122085db720dd88f6cd1d7a214437c1f526  supabase/migrations/202610310001_sync_session_guard.sql
32e3e7f43e4c8e999c900bf9b4ae8307170cae42228261d0c4d461fcf3755120  supabase/migrations/202611010001_academic_protocol.sql
680bbf805f30b4c15ebb3b8cda1ff286183083e2fe7437e4c8f625ffa4c4031a  supabase/migrations/202611020001_phase_b_fence.sql
8d6df95a1c1eb57e8603790fa6f93c8867f00ab3f1e4cf3e4d811c09a1b34d06  supabase/migrations/202611020002_phase_c_scope.sql
7e51af0f29843f02c93a34b1be8c005f96e5d27c70045c2b28fc40d9ba07f3df  supabase/migrations/202611020003_phase_d_scope.sql
0eb5a4bad62743a28c1cd42e28e70711fd197a1b317804cbcd64e7beb1eb9813  supabase/migrations/202611030001_phase_e_paging.sql
f7785853b4438e72aea24a17b8b91812c4a250efd0a5e6ab8d61c8c3e44b54bf  supabase/migrations/202611030002_phase_e_decisions.sql
9ecd918240458a9367f3acba286a79445999b1b7058ac0e48fe3eab8ccc6f918  supabase/migrations/202611030003_phase_e_indexed_graph.sql
```

Pacote A–E preparado nesta revisão: SHA-256
`7fd28b6dbce5f72724abc1a2644041f19f373fc7675eb37a24bd17fa9a46659b`.
Arquivo gerado é local/revisável, não uma migração implantada nem uma autorização.
